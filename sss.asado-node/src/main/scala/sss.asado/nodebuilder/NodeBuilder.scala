package sss.asado.nodebuilder

import java.util.logging.{Level, Logger}

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions._
import sss.ancillary.{DynConfig, _}
import sss.asado.MessageKeys.messages
import sss.asado.account.{NodeIdentity, NodeIdentityManager, PublicKeyAccount}
import sss.asado.balanceledger.BalanceLedger
import sss.asado.block._
import sss.asado.chains.BlockCloseDistributorActor.ProcessCoinBaseHook
import sss.asado.chains.Chains.{Chain, GlobalChainIdMask}
import sss.asado.chains.{GenerateCoinBaseTxs, QuorumMonitor, TxWriterActor}
import sss.asado.contract.CoinbaseValidator
import sss.asado.crypto.SeedBytes
import sss.asado.eventbus.{MessageInfo, PureEvent}
import sss.asado.identityledger.{IdentityLedger, IdentityService}
import sss.asado.ledger.Ledgers
import sss.asado.message.{MessageDownloadActor, MessagePaywall, MessageQueryHandlerActor}
import sss.asado.network.NetworkInterface.BindControllerSettings
import sss.asado.network.{MessageEventBus, _}
import sss.asado.peers.{PeerManager, PeerQuery}
import sss.asado.peers.PeerManager.{Capabilities, Query}
import sss.asado.quorumledger.{QuorumLedger, QuorumService}
import sss.asado.state._
import sss.asado.util.Serialize.ToBytes
import sss.asado.util.StringCheck.SimpleTag
import sss.asado.{InitWithActorRefs, MessageKeys, PublishedMessageKeys, UniqueNodeIdentifier}
import sss.db.Db
import sss.db.datasource.DataSource

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/9/16.
  */
trait RequirePhrase {
  val phrase: Option[String]
}

trait RequireGlobalChainId {
  implicit val globalChainId: GlobalChainIdMask = 1.toByte
}

trait RequireConfig {
  val conf: Config
}

trait ConfigBuilder extends RequireConfig with Configure {
  val configName: String
  lazy val conf: Config = config(configName)
}

trait BlockChainSettings {
  val inflationRatePerBlock: Int
  val maxTxPerBlock: Int
  val maxBlockOpenSecs: Int
  val maxSignatures: Int
  val spendDelayBlocks: Int
  val numTxWriters: Int
  val numBlocksCached: Int
}

trait RequireNodeConfig {
  val bindSettings: BindControllerSettings
  val nodeConfig: NodeConfig

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

}
trait NodeConfigBuilder extends RequireNodeConfig {

  self: RequireConfig =>

  lazy val bindSettings: BindControllerSettings =
    DynConfig[BindControllerSettings](conf.getConfig("bind"))

  lazy val nodeConfig: NodeConfig = NodeConfigImpl(conf)


  case class NodeConfigImpl(conf: Config) extends NodeConfig with Configure {

    lazy val settings: BindControllerSettings = bindSettings

    lazy val uPnp = if(conf.hasPath("upnp"))
      Some(new UPnP(DynConfig[UPnPSettings](conf.getConfig("upnp")))) else None

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

trait RequireDb {
  implicit val db: Db
}

trait DbBuilder extends RequireDb {

  self: RequireConfig =>

  override lazy implicit val db: Db = {
    Db(conf.getConfig("database"))(
      DataSource(conf.getConfig("database.datasource")))
  }

}


trait RequireActorSystem {
  lazy implicit val actorSystem: ActorSystem = ActorSystem("asado-network-node")
}

trait RequireQuorumMonitor {
  val quorumMonitor: QuorumMonitor
}

trait QuorumMonitorBuilder extends RequireQuorumMonitor {
    self: ChainBuilder with
      RequireActorSystem with
      RequireNodeIdentity with
      RequirePeerManager with
      MessageEventBusBuilder =>

    lazy val quorumMonitor: QuorumMonitor = QuorumMonitor(
      messageEventBus,
      chain.id,
      nodeIdentity.id,
      chain.quorumCandidates(),
      peerManager
    )
}

trait RequireCoinbaseGenerator {
  val processCoinBaseHook: ProcessCoinBaseHook
}

trait CoinbaseGeneratorBuilder
  extends RequireCoinbaseGenerator {

  self: MessageEventBusBuilder with
    RequireDb with
    ChainBuilder with
    RequireGlobalChainId with
    RequireNodeIdentity with
    WalletBuilder with
    RequireNetSend =>

  import chain.ledgers

  lazy override val processCoinBaseHook: ProcessCoinBaseHook =
    new GenerateCoinBaseTxs(
      messageEventBus,
      nodeIdentity,
      ledgers,
      sendTo,
      wallet
    )
}

trait ShutdownHookBuilder {

  def shutdown(): Unit

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      shutdown()
    }
  })
}

trait ChainBuilder {

  self: BlockChainBuilder
    with IdentityServiceBuilder
    with BalanceLedgerBuilder
    with MessageEventBusBuilder
    with RequireNodeConfig
    with RequireGlobalChainId
    with RequireDb =>

  lazy val quorumService = new QuorumService(globalChainId)

  lazy val chain: Chain = new Chain {
    implicit override val id: GlobalChainIdMask = globalChainId


    override implicit val ledgers: Ledgers = new Ledgers({
      val identityLedger =
        new IdentityLedger(MessageKeys.IdentityLedger, identityService)

      val quorumLedger: QuorumLedger = new QuorumLedger(
        globalChainId,
        MessageKeys.QuorumLedger,
        quorumService,
        messageEventBus,
        identityService.accounts)

      Map(
        MessageKeys.BalanceLedger -> balanceLedger,
        MessageKeys.IdentityLedger -> identityLedger,
        MessageKeys.QuorumLedger -> quorumLedger
      )}
    )
    override def quorumCandidates(): Set[UniqueNodeIdentifier] = quorumService.candidates()
  }


}

trait Encoder {
  implicit def apply(msgCode: Byte)(implicit chainId: GlobalChainIdMask): SerializedMessage
  implicit def apply[T <% ToBytes](msgCode: Byte, t: T)(implicit chainId: GlobalChainIdMask): SerializedMessage
}

trait RequireEncoder {
  val encode: Encoder
}

trait EncoderBuilder extends RequireEncoder {

  lazy val encode = new Encoder {
    override def apply(msgCode: Byte)(implicit chainId: GlobalChainIdMask): SerializedMessage = {
      SerializedMessage(chainId, msgCode, Array())
    } ensuring (messages.find(msgCode).get.clazz == classOf[PureEvent],
      s"No bytes were provided but code $msgCode does not map to a PureEvent class")

    override def apply[T <% ToBytes](msgCode: Byte, t: T)
                                    (implicit chainId: GlobalChainIdMask): SerializedMessage = {
      SerializedMessage(msgCode, t)
    } ensuring(messages.find(msgCode).get.clazz.isAssignableFrom(t.getClass),
      s"The class to encode doesn't match the msgCode type ${t.getClass} $msgCode ")
  }
}

trait RequireDecoder {
  val decoder: Byte => Option[MessageInfo]
}

trait DecoderBuilder extends RequireDecoder {
  lazy val m = MessageKeys.messages
  lazy val decoder: Byte => Option[MessageInfo] = m.find
}

trait RequirePeerQuery {
  val peerQuery: PeerQuery
}

trait PeerQueryBuilder extends RequirePeerQuery {
  self: RequirePeerManager =>

  lazy override val peerQuery: PeerQuery = peerManager

}

trait RequirePeerManager {
  val peerManager: PeerManager
}

trait PeerManagerBuilder extends RequirePeerManager {
  self: NetworkControllerBuilder with
  RequireActorSystem with
  ChainBuilder with
  RequireNodeConfig with
  RequireEncoder with
  MessageEventBusBuilder =>

  lazy val peerManager: PeerManager = new PeerManager(net,
    nodeConfig.peersList,
    Capabilities(chain.id), messageEventBus, encode)
}

trait MessageEventBusBuilder {
  self: RequireActorSystem with
    RequireDecoder =>

  lazy val messageEventBus: MessageEventBus = new MessageEventBus(decoder)

}

trait RequireSeedBytes {
  lazy val seedBytes = new SeedBytes {}
}

trait RequireNodeIdentity {
  val nodeIdentityManager: NodeIdentityManager
  val nodeIdentity: NodeIdentity
}

trait NodeIdentityBuilder extends RequireNodeIdentity {

  self: RequireConfig with RequirePhrase with RequireSeedBytes =>

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
    with RequireDb
    with RequireGlobalChainId
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
  self: RequireDb =>

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

  self: RequireConfig =>

  lazy val bootstrapIdentities: List[BootstrapIdentity] =
    buildBootstrapIdentities

  def buildBootstrapIdentities: List[BootstrapIdentity] = {
    conf.getStringList("bootstrap").asScala.toList.map { str =>
      val strAry = str.split(":::")
      BootstrapIdentity(strAry.head, strAry.tail.head)
    }
  }
}


trait MessageQueryHandlerActorBuilder {
  self: RequireDb
    with MessageEventBusBuilder
    with RequireActorSystem
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
  self: RequireDb
    with MessageEventBusBuilder
    with RequireActorSystem
    with NodeIdentityBuilder
    with HomeDomainBuilder
    with NetworkControllerBuilder =>

  lazy val messageDownloaderActor = actorSystem.actorOf(
    Props(classOf[MessageDownloadActor],
          nodeIdentity.id,
          homeDomain,
          messageEventBus,
          net,
          db))
}

trait RequireBlockChain {
  val bc: BlockChain with BlockChainSignatures
    with BlockChainGenesis
    with BlockChainTxConfirms

  def currentBlockHeight: Long
}

trait BlockChainBuilder extends RequireBlockChain {

  self: RequireDb with RequireGlobalChainId =>

  lazy val bc: BlockChain
    with BlockChainSignatures
    with BlockChainGenesis
    with BlockChainTxConfirms = new BlockChainImpl()

  def currentBlockHeight: Long = bc.lastBlockHeader.height + 1
}


trait HandshakeGeneratorBuilder {

  self : NetworkInterfaceBuilder =>

  lazy val initialStepGenerator: InitialHandshakeStepGenerator =
    ValidateHandshake(
      networkInterface,
      idVerifier
    )
}

trait RequireNetSend extends RequireEncoder {

  def s[T <% ToBytes](msgCode: Byte, t:T, m: Seq[UniqueNodeIdentifier])(implicit cId: GlobalChainIdMask): Unit = {
    sendToMany(encode(msgCode, t), m)
  }

  val sendToMany: NetSendToMany
  val sendTo: NetSendTo = (s, n) => sendToMany(s, Set(n))
}

trait NetSendBuilder extends RequireNetSend {

  self :NetworkControllerBuilder =>

  lazy val sendToMany: NetSendToMany = net.send
}

trait NetworkControllerBuilder {

  self: RequireActorSystem
    with RequireDb
    with NodeConfigBuilder
    with MessageEventBusBuilder
    with NetworkInterfaceBuilder
    with HandshakeGeneratorBuilder =>

  lazy val netController =
    new NetworkController(initialStepGenerator, networkInterface, messageEventBus)

  lazy val net = netController.waitStart()
}

trait MinimumNode
    extends Logging
    with RequireGlobalChainId
    with ConfigBuilder
    with RequireActorSystem
    with DbBuilder
    with NodeConfigBuilder
    with RequirePhrase
    with RequireSeedBytes
    with NodeIdentityBuilder
    with IdentityServiceBuilder
    with BootstrapIdentitiesBuilder
    with DecoderBuilder
    with MessageEventBusBuilder
    with BlockChainBuilder
    with NetworkInterfaceBuilder
    with HandshakeGeneratorBuilder
    with NetworkControllerBuilder
    with BalanceLedgerBuilder
    with ChainBuilder
    with WalletPersistenceBuilder
    with WalletBuilder
    with IntegratedWalletBuilder
    with HttpServerBuilder {

  def shutdown: Unit = {
    httpServer.stop
    actorSystem.terminate
  }

  Logger.getLogger("hsqldb.db").setLevel(Level.OFF)
}


