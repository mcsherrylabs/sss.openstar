package sss.asado.nodebuilder


import java.util.logging.{Level, Logger}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions._
import sss.ancillary.{DynConfig, _}
import sss.asado.{InitWithActorRefs, MessageKeys}
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.BalanceLedger
import sss.asado.block._
import sss.asado.contract.CoinbaseValidator
import sss.asado.identityledger.{IdentityLedger, IdentityService}
import sss.asado.ledger.Ledgers
import sss.asado.message.{MessageDownloadActor, MessagePaywall, MessageQueryHandlerActor}
import sss.asado.network.NetworkController.{BindControllerSettings, ConnectTo, StartNetwork}
import sss.asado.network._
import sss.asado.state._
import sss.asado.account.PublicKeyAccount
import sss.db.Db

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
trait ConfigNameBuilder {
  val configName : String
}

trait PhraseBuilder {
  val phrase : Option[String]
}

trait ConfigBuilder extends Configure {
  self : ConfigNameBuilder =>
  lazy val conf: Config = config(configName)
}

trait BindControllerSettingsBuilder {
  self : ConfigBuilder =>
  lazy val bindSettings: BindControllerSettings = DynConfig[BindControllerSettings](conf.getConfig("bind"))
}

trait NodeConfigBuilder {
  self : ConfigNameBuilder with
    ConfigBuilder with
    ConfigNameBuilder with
    BindControllerSettingsBuilder =>

  lazy val nodeConfig: NodeConfig = NodeConfigImpl(conf)

  trait NodeConfig {
     val conf: Config
     val settings: BindControllerSettings
     val uPnp: Option[UPnP]
     val dbConfig: Config
     val blockChainSettings: BlockChainSettings
     val production: Boolean
     val peersList: Set[NodeId]
     val quorum: Int
     val connectedPeers = Agent[Set[Connection]](Set.empty[Connection])
     val connectedClients = Agent[Set[Connection]](Set.empty[Connection])
  }

  case class NodeConfigImpl(conf: Config) extends NodeConfig with Configure {

    lazy val settings: BindControllerSettings = bindSettings
    lazy val uPnp = DynConfig.opt[UPnPSettings](s"${configName}.upnp") map (new UPnP(_))
    lazy val dbConfig = conf.getConfig("database")
    lazy val blockChainSettings: BlockChainSettings = DynConfig[BlockChainSettings](conf.getConfig("blockchain"))
    lazy val production: Boolean = conf.getBoolean("production")
    lazy val peersList: Set[NodeId] = conf.getStringList("peers").toSet.map(NetworkController.toNodeId)
    lazy val quorum = NetworkController.quorum(peersList.size)
  }
}

trait HomeDomainBuilder {

  self : NodeConfigBuilder =>

  lazy val homeDomain: HomeDomain = {
    val conf = DynConfig[HomeDomainConfig](nodeConfig.conf.getConfig("homeDomain"))
    new HomeDomain {
      override val identity: String = conf.identity
      override val dns: String = conf.dns
      override val tcpPort: Int = conf.tcpPort
      override val httpPort: Int = conf.httpPort
    }
  }
}

trait DbBuilder {

  self : NodeConfigBuilder =>
    lazy implicit val db = Db(nodeConfig.dbConfig)

}

trait ActorSystemBuilder {
  lazy implicit val actorSystem: ActorSystem = ActorSystem("asado-network-node")
}

trait EventListenerBuilder {
  self: ActorSystemBuilder =>
  lazy val eventListener: ActorRef = actorSystem.actorOf(Props(classOf[EventListener]))
}

trait LedgersBuilder {

  self : BlockChainBuilder with NodeConfigBuilder with IdentityServiceBuilder with BalanceLedgerBuilder with DbBuilder =>

  lazy val ledgers : Ledgers = {

      val identityLedger = new IdentityLedger(MessageKeys.IdentityLedger, identityService)

      new Ledgers(Map(
        MessageKeys.BalanceLedger -> balanceLedger,
        MessageKeys.IdentityLedger -> identityLedger
      ))
    }
}

trait MessageRouterActorBuilder {
  self: ActorSystemBuilder =>
  lazy val messageRouterActor: ActorRef = actorSystem.actorOf(Props(classOf[MessageRouter]))
}

trait NodeIdentityBuilder {

  self : NodeConfigBuilder with PhraseBuilder =>
  lazy val nodeIdentity: NodeIdentity = {
    phrase match {
      case None => NodeIdentity.unlockNodeIdentityFromConsole(nodeConfig.conf)
      case Some(secret) => NodeIdentity(nodeConfig.conf, secret)
    }
  }
}

trait BalanceLedgerBuilder {
  self : NodeConfigBuilder with DbBuilder with BlockChainBuilder with IdentityServiceBuilder  =>

  def publicKeyOfFirstSigner(height: Long): Option[PublicKey] = bc.signatures(height, 1).map(_.publicKey).headOption

  lazy val balanceLedger: BalanceLedger = BalanceLedger(
    new CoinbaseValidator(publicKeyOfFirstSigner,
      nodeConfig.blockChainSettings.inflationRatePerBlock, nodeConfig.blockChainSettings.spendDelayBlocks),
    identityService)
}


trait IdentityServiceBuilder {
  self : NodeConfigBuilder with DbBuilder =>

  lazy val identityService: IdentityService = IdentityService()
}

import sss.asado.util.ByteArrayVarcharOps._
case class BootstrapIdentity(nodeId: String, pKeyStr: String) {
  private lazy val pKey: PublicKey = pKeyStr.toByteArray
  private lazy val pKeyAccount = PublicKeyAccount(pKey)
  def verify(sig: Signature, msg: Array[Byte]): Boolean = pKeyAccount.verify(sig, msg)
}

trait BootstrapIdentitiesBuilder {

  self : NodeConfigBuilder =>

  lazy val bootstrapIdentities: List[BootstrapIdentity] = buildBootstrapIdentities
  def buildBootstrapIdentities: List[BootstrapIdentity] = {
    nodeConfig.conf.getStringList("bootstrap").toList.map { str =>
          val strAry = str.split(":::")
          BootstrapIdentity(strAry.head, strAry.tail.head)
        }
      }
}

trait LeaderActorBuilder {

  self: ActorSystemBuilder with
    NodeConfigBuilder with
    LedgersBuilder with
    DbBuilder with
    BlockChainBuilder with
    NodeIdentityBuilder with
    MessageRouterActorBuilder with
    IdentityServiceBuilder with
    NetworkContollerBuilder with
    StateMachineActorBuilder =>

  lazy val leaderActor: ActorRef = buildLeaderActor

  def buildLeaderActor = {
    actorSystem.actorOf(Props(classOf[LeaderActor],
      nodeIdentity.id,
      nodeConfig.quorum,
      nodeConfig.connectedPeers,
      messageRouterActor,
      ncRef,
      stateMachineActor,
      bc,
      db, ledgers))
  }
}

trait MessageQueryHandlerActorBuilder {
  self: DbBuilder with
    MessageRouterActorBuilder with
    ActorSystemBuilder with
    NodeIdentityBuilder with
    BlockChainBuilder with
    ConfigBuilder with
    IdentityServiceBuilder =>

  lazy val minNumBlocksInWhichToClaim = conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")

  lazy val messagePaywall = new MessagePaywall(
    minNumBlocksInWhichToClaim,
    chargePerMessage,
    currentBlockHeight _, nodeIdentity, identityService)

  lazy val messageServiceActor =
    actorSystem.actorOf(Props(classOf[MessageQueryHandlerActor], messageRouterActor, messagePaywall, db))

}

trait MessageDownloadServiceBuilder  {
  self: DbBuilder with
    MessageRouterActorBuilder with
    ActorSystemBuilder with
    NodeIdentityBuilder with
    HomeDomainBuilder with
   NetworkContollerBuilder =>

  lazy val messageDownloaderActor = actorSystem.actorOf(Props(classOf[MessageDownloadActor],
    nodeIdentity.id, homeDomain, messageRouterActor,ncRef,db))
}


trait BlockChainBuilder {
  self: DbBuilder =>

  lazy val bc : BlockChain
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

  self : ActorSystemBuilder with
    DbBuilder with
    NodeConfigBuilder with
    NodeIdentityBuilder with
    BlockChainBuilder with
    BlockChainActorsBuilder with
    LeaderActorBuilder with
    BlockChainDownloaderBuilder with
    TxForwarderActorBuilder with
    MessageRouterActorBuilder =>

  lazy val stateMachineActor: ActorRef = buildCoreStateMachine

  override def initStateMachine = {

    stateMachineActor ! InitWithActorRefs(
      blockChainDownloaderActor,
      leaderActor,
      messageRouterActor,
      txRouter,
      blockChainActor,
      txForwarderActor)
  }


  def buildCoreStateMachine: ActorRef = {
    actorSystem.actorOf(Props(classOf[AsadoCoreStateMachineActor],
      nodeIdentity.id,
      nodeConfig.connectedPeers,
      nodeConfig.blockChainSettings,
      bc,
      nodeConfig.quorum,
      db))
  }

}

trait ClientStateMachineActorBuilder extends StateMachineActorBuilder {

  self : ActorSystemBuilder with
    DbBuilder with
    NodeConfigBuilder with
    NodeIdentityBuilder with
    BlockChainBuilder with
    MessageDownloadServiceBuilder with
    BlockChainDownloaderBuilder with
    EventListenerBuilder with
    MessageRouterActorBuilder =>

  lazy val stateMachineActor: ActorRef = buildClientStateMachine

  override  def initStateMachine = {
    stateMachineActor ! InitWithActorRefs(messageDownloaderActor,
      blockChainDownloaderActor,
      messageRouterActor)
  }

  def buildClientStateMachine : ActorRef = {
    actorSystem.actorOf(Props(classOf[AsadoClientStateMachineActor],
      nodeIdentity.id,
      nodeConfig.connectedPeers,
      nodeConfig.blockChainSettings,
      bc,
      nodeConfig.quorum,
      db, eventListener))
  }
}


trait NetworkContollerBuilder {

  self : ActorSystemBuilder with
    DbBuilder with
    NodeConfigBuilder with
    MessageRouterActorBuilder with
    NetworkInterfaceBuilder with
    StateMachineActorBuilder  =>

  lazy val ncRef: ActorRef =  buildNetworkController

  def buildNetworkController =
    actorSystem.actorOf(Props(classOf[NetworkController],
      messageRouterActor, networkInterface, nodeConfig.peersList,
      nodeConfig.connectedPeers,
      nodeConfig.connectedClients,
      stateMachineActor))

  def startNetwork = ncRef ! StartNetwork
}


trait MinimumNode extends Logging with
    ConfigNameBuilder with
    ConfigBuilder with
    BindControllerSettingsBuilder with
    ActorSystemBuilder with
    DbBuilder with
    NodeConfigBuilder with
    PhraseBuilder with
    NodeIdentityBuilder with
    IdentityServiceBuilder with
    BootstrapIdentitiesBuilder with
    MessageRouterActorBuilder with
    BlockChainBuilder with
    StateMachineActorBuilder with
    NetworkInterfaceBuilder with
    NetworkContollerBuilder with
    BalanceLedgerBuilder with
    LedgersBuilder with
    WalletPersistenceBuilder with
    WalletBuilder with
    IntegratedWalletBuilder with
    HttpServerBuilder  {

  def shutdown: Unit = {
    httpServer.stop
    actorSystem.terminate
  }

  Logger.getLogger("hsqldb.db").setLevel(Level.OFF)
}


trait CoreNode extends MinimumNode with
    BlockChainDownloaderBuilder with
    TxForwarderActorBuilder with
    CoreStateMachineActorBuilder with
    LeaderActorBuilder with
    BlockChainActorsBuilder {


}

trait ServicesNode extends CoreNode with
    MessageQueryHandlerActorBuilder with
    ClaimServletBuilder {
}


trait ClientNode extends MinimumNode with
    BlockChainDownloaderBuilder with
    EventListenerBuilder with
    ClientStateMachineActorBuilder with
    MessageDownloadServiceBuilder with
    HomeDomainBuilder {

  def connectHome = ncRef ! ConnectTo(homeDomain.nodeId)

}






