package sss.asado.network

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.{Await, Future, Promise}

class NetworkController(
    initialStepGenerator: InitialHandshakeStepGenerator,
    networkInterface: NetworkInterface,
    messageEventBus: MessageEventBus
) {

  def waitStart()(implicit actorSystem: ActorSystem, atMost: Duration = 10 seconds ): NetworkRef = {
    Await.result(start(), atMost)
  }

  def start()(implicit actorSystem: ActorSystem): Future[NetworkRef] = {

    val startPromise = Promise[NetworkRef]()

    startActor(networkInterface,
               initialStepGenerator,
               startPromise,
               messageEventBus)

    startPromise.future
  }

  private def startActor(
      networkInterface: NetworkInterface,
      initialStep: InitialHandshakeStepGenerator,
      startPromise: Promise[NetworkRef],
      messageEventBus: MessageEventBus
  )(implicit actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(
      Props(classOf[NetworkControllerActor],
            networkInterface,
            initialStep,
            startPromise,
            messageEventBus))
}
