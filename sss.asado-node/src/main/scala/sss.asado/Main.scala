package sss.asado

import akka.actor.ActorRef
import sss.asado.nodebuilder.{CoreNode, ServicesNode}
import sss.asado.state.AsadoStateProtocol.{AcceptTransactions, FindTheLeader}

import scala.language.postfixOps


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */

case class InitWithActorRefs(refs: ActorRef*)

  object CoreMain {
    def main(withArgs: Array[String]): Unit = {

      val core = new CoreNode {
        override val configName: String = withArgs(0)
      }
      core.initStateMachine
      core.startNetwork
      core.startHttpServer

    }
  }

  object ServicesMain {
    def main(withArgs: Array[String]): Unit = {

      val core = new ServicesNode {
        override val configName: String = withArgs(0)
      }
      core.initStateMachine
      core.messageServiceActor
      core.addClaimServlet
      core.startNetwork
      core.startHttpServer

    }
  }



