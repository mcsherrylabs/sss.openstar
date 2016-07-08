package sss.asado.nodebuilder

import akka.actor.{ActorRef, Props}
import akka.routing.RoundRobinPool
import sss.asado.block.{BlockChainActor, BlockChainDownloaderActor, BlockChainSynchronizationActor, TxForwarderActor, TxWriter}

/**
  * Created by alan on 6/16/16.
  */
trait BlockChainActorsBuilder {

  self : NodeConfigBuilder with
    ActorSystemBuilder with
    MessageRouterActorBuilder with
    NodeIdentityBuilder with
    NetworkContollerBuilder with
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
    actorSystem.actorOf(RoundRobinPool(5).props(Props(classOf[TxWriter], blockChainSynchronizationActor)), "txRouter")

}


trait BlockChainDownloaderBuilder {

  self : ActorSystemBuilder with
    MessageRouterActorBuilder with
    NodeIdentityBuilder with
    NetworkContollerBuilder with
    BlockChainBuilder with
    DbBuilder with
    LedgersBuilder =>

  lazy val blockChainDownloaderActor: ActorRef = buildChainDownloader

  def buildChainDownloader =
    actorSystem.actorOf(Props(classOf[BlockChainDownloaderActor], nodeIdentity, ncRef,
      messageRouterActor, bc, db, ledgers))

}

trait TxForwarderActorBuilder {

  self : NodeConfigBuilder with
    ActorSystemBuilder with
    MessageRouterActorBuilder with
    NetworkContollerBuilder =>

  lazy val txForwarderActor : ActorRef = buildTxForwarder

  def buildTxForwarder =
    actorSystem.actorOf(Props(classOf[TxForwarderActor],
      messageRouterActor,
      nodeConfig.nodeConfig.getInt("clientRefCacheSize")))

}