package sss.asado.nodebuilder

import akka.actor.{ActorRef, Props}
import akka.routing.RoundRobinPool
import sss.asado.MessageKeys._
import sss.asado.block.{BlockChainActor, BlockChainDownloaderActor, BlockChainSynchronizationActor, ClientBlockChainDownloaderActor, SimpleTxPageActor, TxForwarderActor, TxWriter}
import sss.asado.network.MessageRouter.RegisterRef

/**
  * Created by alan on 6/16/16.
  */
trait BlockChainActorsBuilder {

  self : NodeConfigBuilder with
    ActorSystemBuilder with
    MessageRouterActorBuilder with
    NodeIdentityBuilder with
    NetworkControllerBuilder with
    BlockChainBuilder with
    DbBuilder with
    LedgersBuilder with
    WalletBuilder with
    StateMachineActorBuilder =>


  lazy val blockChainSynchronizationActor :  ActorRef = buildBlockChainSyncActor
  lazy val blockChainActor :  ActorRef = buildBlockChainActor
  lazy val txRouter: ActorRef = buildTxRouter

  def buildBlockChainSyncActor =
    actorSystem.actorOf(Props(classOf[BlockChainSynchronizationActor],
      nodeConfig.quorum,
      nodeConfig.blockChainSettings.maxTxPerBlock,
      nodeConfig.blockChainSettings.maxSignatures,
      nodeConfig.peersList,
      stateMachineActor,
      bc,
      messageRouterActor,
      db))


  def buildBlockChainActor =
    actorSystem.actorOf(Props(classOf[BlockChainActor],
      nodeIdentity,
      nodeConfig.blockChainSettings, bc,

      txRouter,
      blockChainSynchronizationActor,
      wallet,
      db,
      ledgers))

  def buildTxRouter: ActorRef =
    actorSystem.actorOf(RoundRobinPool(nodeConfig.blockChainSettings.numTxWriters).props(
      Props(classOf[TxWriter], blockChainSynchronizationActor)), "txRouter")

}


trait BlockChainDownloaderBuilder {

  self : ActorSystemBuilder with
    MessageRouterActorBuilder with
    StateMachineActorBuilder with
    NodeIdentityBuilder with
    NetworkControllerBuilder with
    BlockChainBuilder with
    DbBuilder with
    LedgersBuilder =>

  lazy val blockChainDownloaderActor: ActorRef = buildChainDownloader

  def buildChainDownloader =
    actorSystem.actorOf(Props(classOf[BlockChainDownloaderActor], nodeIdentity, ncRef,
      messageRouterActor, stateMachineActor, bc, db, ledgers))

}

trait ClientBlockChainDownloaderBuilder {

  self : ActorSystemBuilder with
    NodeConfigBuilder with
    MessageRouterActorBuilder with
    StateMachineActorBuilder with
    NodeIdentityBuilder with
    NetworkControllerBuilder with
    BlockChainBuilder with
    DbBuilder with
    LedgersBuilder =>

  lazy val blockChainDownloaderActor: ActorRef = buildClientChainDownloader

  def buildClientChainDownloader =
    actorSystem.actorOf(Props(classOf[ClientBlockChainDownloaderActor], ncRef,
      messageRouterActor, stateMachineActor, nodeConfig.blockChainSettings.numBlocksCached,  nodeConfig.blockChainSettings.maxBlockOpenSecs, bc, db, ledgers))

}

trait TxForwarderActorBuilder {

  self : NodeConfigBuilder with
    ActorSystemBuilder with
    MessageRouterActorBuilder with
    StateMachineActorBuilder with
    NetworkControllerBuilder =>

  lazy val txForwarderActor : ActorRef = buildTxForwarder

  def buildTxForwarder =
    actorSystem.actorOf(Props(classOf[TxForwarderActor],
      messageRouterActor,
      nodeConfig.conf.getInt("clientRefCacheSize")))

}

trait SimpleTxPageActorBuilder {

  self : NodeConfigBuilder with
    ActorSystemBuilder with
    MessageRouterActorBuilder with
    BlockChainBuilder with DbBuilder  =>

  lazy val simpleTxPageActor: ActorRef = buildSimpleTxPageActor

  def buildSimpleTxPageActor =
    actorSystem.actorOf(Props(classOf[SimpleTxPageActor],
      nodeConfig.blockChainSettings.maxSignatures,
      bc, db))

  def initSimplePageTxActor = messageRouterActor ! RegisterRef(SimpleGetPageTx, simpleTxPageActor)
}