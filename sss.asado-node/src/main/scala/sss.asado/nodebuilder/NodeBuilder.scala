package sss.asado.nodebuilder

import java.util.logging.{Level, Logger}

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions._
import sss.ancillary.{DynConfig, _}
import sss.asado.account.{NodeIdentity, NodeIdentityManager, PublicKeyAccount}
import sss.asado.balanceledger.BalanceLedger
import sss.asado.block._
import sss.asado.chains.{Chain, GlobalChainIdMask}
import sss.asado.contract.CoinbaseValidator
import sss.asado.crypto.SeedBytes
import sss.asado.identityledger.{IdentityLedger, IdentityService}
import sss.asado.ledger.Ledgers
import sss.asado.message.{MessageDownloadActor, MessagePaywall, MessageQueryHandlerActor}
import sss.asado.network.MessageEventBus.MessageInfo
import sss.asado.network.NetworkInterface.BindControllerSettings
import sss.asado.network._
import sss.asado.quorumledger.{QuorumLedger, QuorumService}
import sss.asado.state._
import sss.asado.{InitWithActorRefs, MessageKeys}
import sss.db.Db
import sss.db.datasource.DataSource

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/9/16.
  */
trait PhraseBuilder {
  val phrase: Option[String]
}

trait ConfigBuilder extends Configure {
  val configName: String
  lazy val conf: Config = config(configName)
}

trait NodeConfigBuilder {

  self: ConfigBuilder =>

  lazy val bindSettings: BindControllerSettings =
    DynConfig[BindControllerSettings](conf.getConfig("bind"))

  lazy val nodeConfig: NodeConfig = NodeConfigImpl(conf)

  trait NodeConfig {
    val conf: Config
    val settings: BindControllerSettings
    val uPnp: Option[UPnP]
    val blockChainSettings: BlockChainSettings
    val production: Boolean
    val peersList: Set[NodeId]
    //val quorum: Int
    /*val connectedPeers = Agent[Set[Connection]](Set.empty[Connection])
    val connectedClients = Agent[Set[Connection]](Set.empty[Connection])*/
  }

  case class NodeConfigImpl(conf: Config) extends NodeConfig with Configure {

    lazy val settings: BindControllerSettings = bindSettings
    lazy val uPnp = DynConfig.opt[UPnPSettings](s"${configName}.upnp") map (new UPnP(
      _))
    lazy val blockChainSettings: BlockChainSettings =
      DynConfig[BlockChainSettings](conf.getConfig("blockchain"))
    lazy val production: Boolean = conf.getBoolean("production")
    lazy val peersList: Set[NodeId] = conf
      .getStringList("peers")
      .asScala
      .toSet
      .map(toNodeId)
    //lazy val quorum = quorum(peersList.size)
  }
}

trait HomeDomainBuilder {

  self: NodeConfigBuilder =>

  lazy val homeDomain: HomeDomain = {
    val conf =
      DynConfig[HomeDomainConfig](nodeConfig.conf.getConfig("homeDomain"))
    new HomeDomain {
      override val identity: String = conf.identity
      override val dns: String = conf.dns
      override val tcpPort: Int = conf.tcpPort
      override val httpPort: Int = conf.httpPort
    }
  }

}

trait DbBuilder {

  self: ConfigBuilder =>

  lazy implicit val db = Db(conf.getConfig("database"))(
    DataSource(conf.getConfig("database.datasource")))

}

trait ActorSystemBuilder {
  lazy implicit val actorSystem: ActorSystem = ActorSystem("asado-network-node")
}

trait QuorumMonitorBuilder {
    self: ChainBuilder with
      MessageEventBusBuilder =>

    lazy val quorumMonitor: QuorumMonitor = new QuorumMonitor(messageEventBus, chain)
}


trait ChainBuilder {

  self: BlockChainBuilder
    with IdentityServiceBuilder
    with BalanceLedgerBuilder
    with DbBuilder =>

  val globalChainId: GlobalChainIdMask = 1
  val quorumService = new QuorumService(globalChainId.toString)


  lazy val chain: Chain = new Chain {
    override val id: GlobalChainIdMask = globalChainId

    override implicit val ledgers: Ledgers = new Ledgers({
      val identityLedger =
        new IdentityLedger(MessageKeys.IdentityLedger, identityService)

      val quorumLedger: QuorumLedger = new QuorumLedger(
        MessageKeys.QuorumLedger,
        quorumService,
        identityService.accounts)

      Map(
        MessageKeys.BalanceLedger -> balanceLedger,
        MessageKeys.IdentityLedger -> identityLedger,
        MessageKeys.QuorumLedger -> quorumLedger
      )}
    )
    override def quorumMembers: Seq[UniqueNodeIdentifier] = quorumService.members()
  }


}

trait DecoderBuilder {
  val decoder: Byte => Option[MessageInfo] = ???
}

trait MessageEventBusBuilder {
  self: ActorSystemBuilder with
    DecoderBuilder =>

  lazy val messageEventBus: MessageEventBus = new MessageEventBus(decoder)

}

trait SeedBytesBuilder {
  lazy val seedBytes = new SeedBytes {}
}

trait NodeIdentityBuilder {

  self: ConfigBuilder with PhraseBuilder with SeedBytesBuilder =>

  lazy val nodeIdentityManager = new NodeIdentityManager(seedBytes)
  lazy val nodeIdentity: NodeIdentity = {
    phrase match {
      case None         => nodeIdentityManager.unlockNodeIdentityFromConsole(conf)
      case Some(secret) => nodeIdentityManager(conf, secret)
    }
  }
}

trait BalanceLedgerBuilder {
  self: NodeConfigBuilder
    with DbBuilder
    with BlockChainBuilder
    with IdentityServiceBuilder =>

  def publicKeyOfFirstSigner(height: Long): Option[PublicKey] =
    bc.signatures(height, 1).map(_.publicKey).headOption

  lazy val balanceLedger: BalanceLedger = BalanceLedger(
    new CoinbaseValidator(publicKeyOfFirstSigner,
                          nodeConfig.blockChainSettings.inflationRatePerBlock,
                          nodeConfig.blockChainSettings.spendDelayBlocks),
    identityService
  )
}

trait IdentityServiceBuilder {
  self: DbBuilder =>

  lazy val identityService: IdentityService = IdentityService()
}


import sss.asado.util.ByteArrayEncodedStrOps._

case class BootstrapIdentity(nodeId: String, pKeyStr: String) {
  private lazy val pKey: PublicKey = pKeyStr.toByteArray
  private lazy val pKeyAccount = PublicKeyAccount(pKey)
  def verify(sig: Signature, msg: Array[Byte]): Boolean =
    pKeyAccount.verify(sig, msg)
}


trait BootstrapIdentitiesBuilder {

  self: ConfigBuilder =>

  lazy val bootstrapIdentities: List[BootstrapIdentity] =
    buildBootstrapIdentities

  def buildBootstrapIdentities: List[BootstrapIdentity] = {
    conf.getStringList("bootstrap").asScala.toList.map { str =>
      val strAry = str.split(":::")
      BootstrapIdentity(strAry.head, strAry.tail.head)
    }
  }
}

trait LeaderActorBuilder {

  self: ActorSystemBuilder
    with NodeConfigBuilder
    with ChainBuilder
    with DbBuilder
    with BlockChainBuilder
    with NodeIdentityBuilder
    with MessageEventBusBuilder
    with IdentityServiceBuilder
    with NetworkControllerBuilder
    with StateMachineActorBuilder =>

  lazy val leaderActor: ActorRef = buildLeaderActor

  def buildLeaderActor = {
    actorSystem.actorOf(
      Props(classOf[LeaderActor],
            nodeIdentity.id,
            Set(),
            messageEventBus,
            ncRef,
            stateMachineActor,
            bc))
  }
}

trait MessageQueryHandlerActorBuilder {
  self: DbBuilder
    with MessageEventBusBuilder
    with ActorSystemBuilder
    with NodeIdentityBuilder
    with BlockChainBuilder
    with ConfigBuilder
    with IdentityServiceBuilder =>

  lazy val minNumBlocksInWhichToClaim =
    conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")

  lazy val messagePaywall = new MessagePaywall(minNumBlocksInWhichToClaim,
                                               chargePerMessage,
                                               currentBlockHeight _,
                                               nodeIdentity,
                                               identityService)

  lazy val messageServiceActor =
    actorSystem.actorOf(
      Props(classOf[MessageQueryHandlerActor],
            messageEventBus,
            messagePaywall,
            db))

}

trait MessageDownloadServiceBuilder {
  self: DbBuilder
    with MessageEventBusBuilder
    with ActorSystemBuilder
    with NodeIdentityBuilder
    with HomeDomainBuilder
    with NetworkControllerBuilder =>

  lazy val messageDownloaderActor = actorSystem.actorOf(
    Props(classOf[MessageDownloadActor],
          nodeIdentity.id,
          homeDomain,
          messageEventBus,
          ncRef,
          db))
}

trait BlockChainBuilder {
  self: DbBuilder =>

  lazy val bc: BlockChain
    with BlockChainSignatures
    with BlockChainGenesis
    with BlockChainTxConfirms = new BlockChainImpl()

  def currentBlockHeight: Long = bc.lastBlockHeader.height + 1
}

trait StateMachineActorBuilder {
  val stateMachineActor: ActorRef
  def initStateMachine = {}
}

trait CoreStateMachineActorBuilder extends StateMachineActorBuilder {

  self: ActorSystemBuilder
    with DbBuilder
    with NodeConfigBuilder
    with NodeIdentityBuilder
    with BlockChainBuilder
    with BlockChainActorsBuilder
    with LeaderActorBuilder
    with BlockChainDownloaderBuilder
    with TxForwarderActorBuilder
    with NetworkControllerBuilder
    with MessageEventBusBuilder =>

  lazy val stateMachineActor: ActorRef = buildCoreStateMachine

  override def initStateMachine = {

    stateMachineActor ! InitWithActorRefs(leaderActor,
                                          txRouter,
                                          blockChainActor,
                                          txForwarderActor)

    blockChainDownloaderActor
  }

  def buildCoreStateMachine: ActorRef = {
    actorSystem.actorOf(
      Props(classOf[AsadoCoreStateMachineActor],
            nodeIdentity.id,
            nodeConfig.blockChainSettings,
            bc,
            messageEventBus,
            ncRef,
            db))
  }

}

trait ClientStateMachineActorBuilder extends StateMachineActorBuilder {

  self: ActorSystemBuilder
    with DbBuilder
    with NodeConfigBuilder
    with NodeIdentityBuilder
    with BlockChainBuilder
    with MessageDownloadServiceBuilder
    with ClientBlockChainDownloaderBuilder
    with TxForwarderActorBuilder
    with MessageEventBusBuilder =>

  lazy val stateMachineActor: ActorRef = buildClientStateMachine

  override def initStateMachine = {
    stateMachineActor ! InitWithActorRefs(messageDownloaderActor,
                                          blockChainDownloaderActor,
                                          txForwarderActor)
  }

  def buildClientStateMachine: ActorRef = {
    actorSystem.actorOf(
      Props(classOf[AsadoClientStateMachineActor],
            nodeIdentity.id,
            nodeConfig.blockChainSettings,
            bc,
            db))
  }
}

trait HandshakeGeneratorBuilder {

  self : NetworkInterfaceBuilder =>

  lazy val initialStepGenerator: InitialHandshakeStepGenerator =
    ValidateHandshake(
      networkInterface,
      idVerifier
    )
}

trait NetworkControllerBuilder {

  self: ActorSystemBuilder
    with DbBuilder
    with NodeConfigBuilder
    with MessageEventBusBuilder
    with NetworkInterfaceBuilder
    with HandshakeGeneratorBuilder =>

  val netController =
    new NetworkController(initialStepGenerator, networkInterface, messageEventBus)

  lazy val ncRef = netController.waitStart()
}

trait MinimumNode
    extends Logging
    with ConfigBuilder
    with ActorSystemBuilder
    with DbBuilder
    with NodeConfigBuilder
    with PhraseBuilder
    with SeedBytesBuilder
    with NodeIdentityBuilder
    with IdentityServiceBuilder
    with BootstrapIdentitiesBuilder
    with DecoderBuilder
    with MessageEventBusBuilder
    with BlockChainBuilder
    with StateMachineActorBuilder
    with NetworkInterfaceBuilder
    with HandshakeGeneratorBuilder
    with NetworkControllerBuilder
    with BalanceLedgerBuilder
    with ChainBuilder
    with WalletPersistenceBuilder
    with WalletBuilder
    with IntegratedWalletBuilder
    with HttpServerBuilder
    with SimpleTxPageActorBuilder {

  def shutdown: Unit = {
    httpServer.stop
    actorSystem.terminate
  }

  Logger.getLogger("hsqldb.db").setLevel(Level.OFF)
}

trait CoreNode
    extends MinimumNode
    with BlockChainDownloaderBuilder
    with TxForwarderActorBuilder
    with CoreStateMachineActorBuilder
    with LeaderActorBuilder
    with BlockChainActorsBuilder {}

trait ServicesNode
    extends CoreNode
    with MessageQueryHandlerActorBuilder
    with ClaimServletBuilder {}

trait ClientNode
    extends MinimumNode
    with ClientBlockChainDownloaderBuilder
    with TxForwarderActorBuilder
    with ClientStateMachineActorBuilder
    with MessageDownloadServiceBuilder
    with HomeDomainBuilder {

  def connectHome = ncRef.connect(homeDomain.nodeId)

}
