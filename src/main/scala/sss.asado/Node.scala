package sss.asado

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import akka.util.Timeout
import sss.ancillary.Configure
import sss.asado.console.{ConsoleActor, InfoActor, NoRead}
import sss.asado.ledger.Ledger
import sss.asado.network.MessageRouter.Register
import sss.asado.network._
import sss.asado.storage.DBStorage

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
case class MyBindControllerSettings(ownNonce: Int, porty: Int, addr: Option[String],override val bindAddress: String) extends BindControllerSettings {

  override implicit val timeout: Timeout = 5 seconds
  override val applicationName: String = "asado"
  override val appVersion: ApplicationVersion = ApplicationVersion(1,0,0)
  override val nodeNonce: Int = ownNonce
  override val declaredAddress: Option[String] = addr
  override val port : Int = porty
}

object Node extends Configure {

  val ownNonce: Int = Random.nextInt()

  def main(args: Array[String]) {

    println("Asado node starting up ...")

    val nodeConfig = config(args(0))
    val porty = nodeConfig.getInt("port")
    val addr = Option(nodeConfig.getString("declaredAddress"))
    val bindAddr = nodeConfig.getString("bindAddress")
    val dbConfig = s"${args(0)}.database"
    val settings: BindControllerSettings = MyBindControllerSettings(ownNonce, porty, addr, bindAddr)

    implicit val actorSystem = ActorSystem("asado-network-node")
    val peerList = Agent[Seq[InetSocketAddress]](Nil)

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter], peerList))

    val uPnpSettings = new UPnPSettings {
      override val upnpGatewayTimeout: Option[Int] = Some(5)
      override val upnpDiscoverTimeout: Option[Int] = Some(5)
    }

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, settings, Some(new UPnP(uPnpSettings))))

    val ledger = new Ledger(new DBStorage(dbConfig))
    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, peerList, ledger))

    val infoRef = actorSystem.actorOf(Props(classOf[InfoActor], messageRouter, ledger ))

    infoRef ! Register(1)
    infoRef ! Register(2)

    val peers: scala.collection.mutable.Seq[String] = nodeConfig.getStringList("peers")
    peers.foreach(p => ref ! NoRead(s"connect $p"))

    ref ! "init"
  }

}
