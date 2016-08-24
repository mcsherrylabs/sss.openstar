package sss.analysis

import akka.actor.{Actor, ActorRef, Props}
import sss.asado.actor.AsadoEventSubscribedActor

import scala.concurrent.ExecutionContext.Implicits.global
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{ReadyStateEvent, StateMachineInitialised}

import scala.concurrent.duration._


/**
  * Created by alan on 8/17/16.
  */
object MainUI {

  def main(args: Array[String]): Unit = {

    new ClientNode {
      override val phrase: Option[String] = Some("password")
      override val configName: String = "analysis"

      override lazy val eventListener: ActorRef =
        actorSystem.actorOf(Props(classOf[OrchestratingActor], this))

    }.initStateMachine
  }
}




class OrchestratingActor(clientNode: ClientNode) extends Actor with AsadoEventSubscribedActor {
  import clientNode._

  private case object ConnectHome


    override def receive: Receive = {
      case StateMachineInitialised =>
        startNetwork
        context.system.scheduler.scheduleOnce(
          FiniteDuration(5, SECONDS),
          self, ConnectHome)

      case ConnectHome => connectHome

      case ReadyStateEvent =>
        log.info("Ready Nothing to do")



    }


  }