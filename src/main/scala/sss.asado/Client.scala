package sss.asado

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import sss.asado.console.{ConsoleActor, InfoActor}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.BindControllerSettings
import sss.asado.network._

import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object Client extends BaseClient {


  override protected def run(settings: BindControllerSettings,
                             actorSystem: ActorSystem,
                             peerList: Agent[Set[Connection]],
                             messageRouter: ActorRef,
                             ncRef: ActorRef,
                             nodeConfig: Config,
                             args: Array[String]
                            ): Unit = {

    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, peerList))

    val infoRef = actorSystem.actorOf(Props(classOf[InfoActor], messageRouter))

    infoRef ! Register(MessageKeys.SignedTxAck)
    infoRef ! Register(MessageKeys.SignedTxNack)


    ref ! "init"
  }


}
