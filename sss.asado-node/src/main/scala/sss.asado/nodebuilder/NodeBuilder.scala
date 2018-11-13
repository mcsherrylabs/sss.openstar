package sss.asado.nodebuilder

import java.util.logging.{Level, Logger}

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions._
import sss.ancillary.{DynConfig, _}
import sss.asado.account.{NodeIdentity, NodeIdentityManager, PublicKeyAccount}
import sss.asado.balanceledger.BalanceLedger
import sss.asado.block._
import sss.asado.chains.Chains.{Chain, GlobalChainIdMask}
import sss.asado.chains._
import sss.asado.contract.CoinbaseValidator
import sss.asado.crypto.SeedBytes
import sss.asado.eventbus.{MessageInfo, PureEvent}
import sss.asado.identityledger.{IdentityLedger, IdentityService}
import sss.asado.ledger.Ledgers
import sss.asado.message.{EndMessageQuery, MessageDownloadActor, MessagePaywall, MessageQuery, MessageQueryHandlerActor}
import sss.asado.network.NetworkInterface.BindControllerSettings
import sss.asado.network.{MessageEventBus, _}
import sss.asado.peers.{PeerManager, PeerQuery}
import sss.asado.peers.PeerManager.{Capabilities, Query}
import sss.asado.quorumledger.{QuorumLedger, QuorumService}
import sss.asado.state._
import sss.asado._
import sss.asado.chains.ChainSynchronizer.StartSyncer
import sss.asado.tools.{DownloadSeedNodes, SendTxSupport}
import sss.asado.tools.SendTxSupport.SendTx
import sss.asado.wallet.UtxoTracker
import sss.db.Db
import sss.db.datasource.DataSource

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
  * This is area is used to create 'components' that can be wired up for test or different applications
  *
  * A component should be created only if it is reusable across applications or tests. The final wiring
  * can be done in the 'main' function, not everything has to be buildable.
  *
  * There are a few options for making a component available.
  *
  * 1. Declare as a requirement. Declare the name of the val and the type in a trait . e.g.
  *
  * trait RequireMyThing { val myThing: MyThing }
  *
  * This 'RequireMyThing' trait is now stackable and usable across applications and tests.
  * If myThing is a simple instanciation it can read
  *
  * trait RequireMyThing { val myThing: MyThing = new MyThing() }
  *
  * If it is more complicated and needs construction use a Builder
  *
  * 2.
  *
  * trait MyThingBuilder extends RequireMyThing with RequireOtherThing { val myThing: MyThing = new MyThing(otherThing) }
  *
  * Note the MyThingBuilder depends only on other 'Require'ments. This means in tests or elsewhere one can use the Builder
  * but the dependencies can be wired up as test dependencies.
  *
  * 3.
  * Just add a Builder (no 'Require' trait) , in this case extending this trait means instantly depending on the components
  * used to build it.
  *
  * So a component could have
  *
  * a. no building trait, to be instanciated in the 'main' function
  * b. a Require trait
  * c. a RequireTrait and extending Builder
  * d. a Builder only
  *
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
  override lazy val conf: Config = config(configName)
}

trait RequireNodeConfig {
  val bindSettings: BindControllerSettings
  val nodeConfig: NodeConfig

  trait NodeConfig {
    val conf: Config
    val settings: BindControllerSettings
    val uPnp: Option[UPnP]
    val blockChainSettings: BlockChainSettings
    val peersList: Set[NodeId]
    val dnsSeedUrl: String
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

    lazy val peersList: Set[NodeId] = conf
      .getStringList("peers")
      .asScala
      .toSet
      .map(toNodeId)

    lazy val dnsSeedUrl: String = conf.getString("dnsSeedUrl")
  }
}

trait HomeDomainBuilder {

  self: NodeConfigBuilder =>

  implicit lazy val homeDomain: HomeDomain = {
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

trait ShutdownHookBuilder {

  def shutdown(): Unit

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      shutdown()
    }
  })
}

trait RequireChain {
  val chain: Chain
}

trait ChainBuilder extends RequireChain {

  self: BlockChainBuilder
    with IdentityServiceBuilder
    with BalanceLedgerBuilder
    with MessageEventBusBuilder
    with RequireNodeConfig
    with RequireGlobalChainId
    with RequireDb =>

  private lazy val quorumService = new QuorumService(globalChainId)

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

  MessageEventBusBuilder =>

  lazy val seedNodesFromDns: Set[NodeId] = DownloadSeedNodes.download(nodeConfig.dnsSeedUrl)

  lazy val peerManager: PeerManager = new PeerManager(net,
    nodeConfig.peersList ++ seedNodesFromDns,
    Capabilities(chain.id), messageEventBus)
}

trait RequireMessageEventBus {
  implicit val messageEventBus: MessageEventBus
}

trait MessageEventBusBuilder extends RequireMessageEventBus {
  self: RequireActorSystem with
    RequireDecoder =>

  implicit lazy val messageEventBus: MessageEventBus = new MessageEventBus(decoder,
    Seq(classOf[ConnectionFailed],
      classOf[EndMessageQuery],
      classOf[MessageQuery]
    )
  )

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

  implicit lazy val nodeIdentityManager = new NodeIdentityManager(seedBytes)
  implicit lazy val nodeIdentity: NodeIdentity = {
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
    bc.quorumSigs(height).signatures(1).map(_.publicKey).headOption

  lazy val balanceLedger: BalanceLedger = BalanceLedger(
    new CoinbaseValidator(publicKeyOfFirstSigner,
                          nodeConfig.blockChainSettings.inflationRatePerBlock,
                          nodeConfig.blockChainSettings.spendDelayBlocks),
    identityService
  )
}

trait IdentityServiceBuilder {
  self: RequireDb =>

  implicit lazy val identityService: IdentityService = IdentityService()
}


import sss.asado.util.ByteArrayEncodedStrOps._

case class BootstrapIdentity(nodeId: String, pKeyStr: String) {
  lazy val pKey: PublicKey = pKeyStr.toByteArray
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
    with IdentityServiceBuilder
    with RequireNetSend
    with RequireGlobalChainId =>

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
        messagePaywall,
        db,
        messageEventBus,
        send,
        globalChainId).withDispatcher("blocking-dispatcher"))

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
  val bc: BlockChain with BlockChainSignaturesAccessor
    with BlockChainGenesis

  def currentBlockHeight: Long
}

trait BlockChainBuilder extends RequireBlockChain {

  self: RequireDb with RequireGlobalChainId =>

  lazy val bc: BlockChain
    with BlockChainSignaturesAccessor
    with BlockChainGenesis = new BlockChainImpl()

  def currentBlockHeight(): Long = bc.lastBlockHeader.height + 1
}


trait HandshakeGeneratorBuilder {

  self : NetworkInterfaceBuilder =>

  lazy val initialStepGenerator: InitialHandshakeStepGenerator =
    ValidateHandshake(
      networkInterface,
      idVerifier
    )
}

trait RequireNetSend {
  implicit val send: Send
}

trait SendTxBuilder {

  self : MessageEventBusBuilder
    with RequireActorSystem
    with RequireGlobalChainId =>

  implicit val sendTx: SendTx = SendTxSupport(actorSystem, globalChainId, messageEventBus)
}

trait NetSendBuilder extends RequireNetSend {

  self :NetworkControllerBuilder =>

  implicit override lazy val send: Send = Send(net)
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

trait ChainSynchronizerBuilder {

  self: RequireActorSystem
    with RequireGlobalChainId
    with RequireDb
    with RequireNodeIdentity
    with RequireChain
    with RequireNetSend
    with RequireBlockChain
    with MessageEventBusBuilder =>

  lazy val startSyncer: StartSyncer = ChainDownloadRequestActor.createStartSyncer(nodeIdentity,
    send,
    messageEventBus,
    bc, db, chain.ledgers, chain.id)


  lazy val synchronization =
    ChainSynchronizer(chain.quorumCandidates(),
      nodeIdentity.id,
      startSyncer,
      () => bc.getLatestCommittedBlockId(),
      () => bc.getLatestRecordedBlockId(),
    )
}

trait PartialNode extends Logging
    with ConfigBuilder
    with RequireActorSystem
    with DbBuilder
    with RequireGlobalChainId
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
    with NetSendBuilder
    with BalanceLedgerBuilder
    with PeerManagerBuilder
    with ClaimServletBuilder
    with HttpServerBuilder
    with SendTxBuilder
    with UnsubscribedMessageHandlerBuilder
    with WalletBuilder
    with ShutdownHookBuilder
    with PublicKeyTrackerBuilder
    with ChainBuilder
    with ChainSynchronizerBuilder
    with MessageQueryHandlerActorBuilder {


  def shutdown: Unit = {
    httpServer.stop
    actorSystem.terminate
  }

  Logger.getLogger("hsqldb.db").setLevel(Level.OFF)

  lazy val init = {

    LeaderElectionActor(nodeIdentity.id, bc)

    ChainDownloadResponseActor(nodeConfig.blockChainSettings.maxSignatures, bc)

    import chain.ledgers

    TxWriterActor(TxWriterActor.props(nodeConfig.blockChainSettings, nodeIdentity.id, bc, nodeIdentity))

    TxDistributeeActor(TxDistributeeActor.props(bc, nodeIdentity))

    QuorumFollowersSyncedMonitor(nodeIdentity.id, net.disconnect)

    synchronization // Init this to allow it to register for events before QuorumMontor starts.

    QuorumMonitor(messageEventBus, globalChainId, nodeIdentity.id, chain.quorumCandidates(), peerManager)

    TxForwarderActor(1000)

    SouthboundTxDistributorActor(
      SouthboundTxDistributorActor.props(nodeIdentity, () => chain.quorumCandidates(), bc, net.disconnect)
    )

    UtxoTracker(buildWalletIndexTracker(nodeIdentity.id))

    messageServiceActor
  }
}


