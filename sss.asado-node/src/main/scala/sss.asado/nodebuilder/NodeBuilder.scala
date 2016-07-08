package sss.asado.nodebuilder

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions._
import sss.ancillary.{DynConfig, _}
import sss.asado.{InitWithActorRefs, MessageKeys}
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{BalanceLedger, BalanceLedgerQuery}
import sss.asado.block._
import sss.asado.contract.CoinbaseValidator
import sss.asado.identityledger.{IdentityLedger, IdentityService, IdentityServiceQuery}
import sss.asado.ledger.Ledgers
import sss.asado.message.{MessageDownloadActor, MessagePaywall, MessageQueryHandlerActor}
import sss.asado.network.NetworkController.{BindControllerSettings, ConnectTo, StartNetwork}
import sss.asado.network._
import sss.asado.state._
import sss.asado.account.PublicKeyAccount
import sss.asado.wallet.{Wallet, WalletPersistence}
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

trait NodeConfigBuilder {
  self : ConfigNameBuilder =>
  lazy val nodeConfig: NodeConfig = NodeConfigImpl(configName)

  trait NodeConfig {
     val nodeConfig: Config
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

  case class NodeConfigImpl(configName: String) extends NodeConfig with Configure {
    lazy val nodeConfig: Config = config(configName)
    lazy val settings: BindControllerSettings = DynConfig[BindControllerSettings](nodeConfig.getConfig("bind"))
    lazy val uPnp = DynConfig.opt[UPnPSettings](s"${configName}.upnp") map (new UPnP(_))
    lazy val dbConfig = nodeConfig.getConfig("database")
    lazy val blockChainSettings: BlockChainSettings = DynConfig[BlockChainSettings](s"${configName}.blockchain")
    lazy val production: Boolean = nodeConfig.getBoolean("production")
    lazy val peersList: Set[NodeId] = nodeConfig.getStringList("peers").toSet.map(NetworkController.toNodeId)
    lazy val quorum = NetworkController.quorum(peersList.size)
  }
}

trait HomeDomainBuilder {

  self : NodeConfigBuilder =>

  lazy val homeDomain: HomeDomain = {
    val conf = DynConfig[HomeDomainConfig](nodeConfig.nodeConfig.getConfig("homeDomain"))
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

  self : NodeConfigBuilder =>
  lazy val nodeIdentity: NodeIdentity = {
    if(nodeConfig.production) NodeIdentity.unlockNodeIdentityFromConsole(nodeConfig.nodeConfig)
    else NodeIdentity(nodeConfig.nodeConfig, "password")
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
    nodeConfig.nodeConfig.getStringList("bootstrap").toList.map { str =>
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
    IdentityServiceBuilder =>

  lazy val messagePaywall = new MessagePaywall(1,1,currentBlockHeight _, nodeIdentity, identityService)

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
      db))
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
    ActorSystemBuilder with
    DbBuilder with
    NodeConfigBuilder with
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
    ClientStateMachineActorBuilder with
    MessageDownloadServiceBuilder with
    HomeDomainBuilder {

  def connectHome = ncRef ! ConnectTo(homeDomain.nodeId)

}






