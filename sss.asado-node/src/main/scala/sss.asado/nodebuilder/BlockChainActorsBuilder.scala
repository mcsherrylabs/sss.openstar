package sss.asado.nodebuilder

import akka.actor.{ActorRef, Props}
import akka.routing.RoundRobinPool
import sss.asado.MessageKeys._
import sss.asado.block.{BlockChainActor, BlockChainDownloaderActor, BlockChainSynchronizationActor, ClientBlockChainDownloaderActor, SimpleTxPageActor, TxForwarderActor, TxWriter}


/**
  * Created by alan on 6/16/16.
  */
trait BlockChainActorsBuilder {

  self : NodeConfigBuilder with
    RequireActorSystem with
    MessageEventBusBuilder with
    NodeIdentityBuilder with
    NetworkControllerBuilder with
    BlockChainBuilder with
    RequireDb with
    ChainBuilder with
    WalletBuilder with
    StateMachineActorBuilder =>


  lazy val blockChainSynchronizationActor :  ActorRef = buildBlockChainSyncActor
  lazy val blockChainActor :  ActorRef = buildBlockChainActor
  lazy val txRouter: ActorRef = buildTxRouter

  def buildBlockChainSyncActor =
    actorSystem.actorOf(Props(classOf[BlockChainSynchronizationActor],
      nodeConfig.blockChainSettings.maxTxPerBlock,
      nodeConfig.blockChainSettings.maxSignatures,
      nodeConfig.peersList,
      stateMachineActor,
      bc,
      messageEventBus,
      db))


  def buildBlockChainActor =
    actorSystem.actorOf(Props(classOf[BlockChainActor],
      nodeIdentity,
      nodeConfig.blockChainSettings,
      bc,
      txRouter,
      blockChainSynchronizationActor,
      wallet,
      db,
      chain))

  def buildTxRouter: ActorRef =
    actorSystem.actorOf(RoundRobinPool(nodeConfig.blockChainSettings.numTxWriters).props(
      Props(classOf[TxWriter], blockChainSynchronizationActor)), "txRouter")

}


trait BlockChainDownloaderBuilder {

  self : RequireActorSystem with
    MessageEventBusBuilder with
    StateMachineActorBuilder with
    NodeIdentityBuilder with
    NetworkControllerBuilder with
    BlockChainBuilder with
    RequireDb with
    ChainBuilder =>

  lazy val blockChainDownloaderActor: ActorRef = buildChainDownloader

  def buildChainDownloader =
    actorSystem.actorOf(Props(classOf[BlockChainDownloaderActor], nodeIdentity, net,
      messageEventBus, stateMachineActor, bc, db, chain.ledgers))

}

trait ClientBlockChainDownloaderBuilder {

  self : RequireActorSystem with
    NodeConfigBuilder with
    MessageEventBusBuilder with
    StateMachineActorBuilder with
    NodeIdentityBuilder with
    NetworkControllerBuilder with
    BlockChainBuilder with
    RequireDb with
    ChainBuilder =>

  lazy val blockChainDownloaderActor: ActorRef = buildClientChainDownloader

  def buildClientChainDownloader =
    actorSystem.actorOf(Props(classOf[ClientBlockChainDownloaderActor], net,
      messageEventBus,
      stateMachineActor,
      nodeConfig.blockChainSettings.numBlocksCached,
      nodeConfig.blockChainSettings.maxBlockOpenSecs,
      bc,
      db,
      chain.ledgers
    ))

}

trait TxForwarderActorBuilder {

  self : NodeConfigBuilder with
    RequireActorSystem with
    MessageEventBusBuilder with
    StateMachineActorBuilder with
    NetworkControllerBuilder =>

  lazy val txForwarderActor : ActorRef = buildTxForwarder

  def buildTxForwarder =
    actorSystem.actorOf(Props(classOf[TxForwarderActor],
      messageEventBus,
      nodeConfig.conf.getInt("clientRefCacheSize")))

}

trait SimpleTxPageActorBuilder {

  self : NodeConfigBuilder with
    RequireActorSystem with
    MessageEventBusBuilder with
    BlockChainBuilder with RequireDb  =>

  lazy val simpleTxPageActor: ActorRef = buildSimpleTxPageActor

  def buildSimpleTxPageActor =
    actorSystem.actorOf(Props(classOf[SimpleTxPageActor],
      nodeConfig.blockChainSettings.maxSignatures,
      bc, db))

  def initSimplePageTxActor = messageEventBus.subscribe(SimpleGetPageTx)( simpleTxPageActor)
}