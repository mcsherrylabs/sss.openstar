package sss.asado
import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import sss.asado.network.NetworkController.{SendToNetwork, ConnectTo}
import sss.asado.network._
import sss.asado.util.Console

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/21/16.
  */

case class MyBindControllerSettings(ownNonce: Int, porty: Int) extends BindControllerSettings {

  override implicit val timeout: Timeout = 5 seconds
  override val applicationName: String = "asado"
  override val appVersion: ApplicationVersion = ApplicationVersion(1,0,0)
  override val nodeNonce: Int = ownNonce
  override val declaredAddress: Option[String] = None
  override val port : Int = porty
}

object TestApp extends Console {

  val ownNonce: Int = Random.nextInt()

  def main(args: Array[String]) {

    println("It begins...")

    val settings: BindControllerSettings = MyBindControllerSettings(ownNonce,  args(0).toInt)

    val actorSystem = ActorSystem("asado-network-node")
    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))
    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, settings, None))

    args(1) match {
      case "server" => println("Should be listening on a port...")
      case "client" => {
        ncRef ! ConnectTo(InetSocketAddress.createUnresolved("127.0.0.1", 8084))
        Thread.sleep(2000)
        val ary = Array[Byte](1,2,3)
        ncRef ! SendToNetwork(NetworkMessage(45.toByte, ary))
          println("End sleep.")
      }
    }
  }

}
