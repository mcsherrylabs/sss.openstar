package sss.asado.network

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.util.Timeout

import scala.concurrent.duration._




/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object MessageRouter {

  case class RegisterRef(msgCode: Byte, ref: ActorRef)
  case class Register(msgCode: Byte)
  case class UnRegister(msgCode: Byte)

}

class MessageRouter extends Actor with ActorLogging {

  import MessageRouter._

  import scala.language.postfixOps
  implicit val timeout = Timeout(2 seconds)

  private def manageRegister(registeredParties: Map[Byte, Set[ActorRef]]): Receive = {

    case RegisterRef(msgCode, ref) => registerRef(ref, msgCode, registeredParties)

    case Register(msgCode) => registerRef(sender(), msgCode, registeredParties)


    case UnRegister(msgCode) =>
      log.debug(s"Removing msgCode $msgCode registrant ")
      val registrant = sender()
      context unwatch registrant
      val newRegistrantList = registeredParties(msgCode).filterNot(_ == registrant)
      context.become(manageRegister(registeredParties + (msgCode -> newRegistrantList)))


    case e @ NetworkMessage(msgCode, bytes) =>
      log.debug(s"We got a message, code is $msgCode")
      registeredParties(msgCode).foreach(_ forward e)


    case Terminated(registrant) =>
      val newRegistrantMap = registeredParties map {
        case (msgCode, newRegistrantList) => msgCode -> newRegistrantList.filterNot(_ == registrant)
      }
      context.become(manageRegister(newRegistrantMap))

    case x   => log.warning(s"We got rubbish -> $x")
  }

  private def registerRef(newRegistrant: ActorRef, msgCode: Byte, registeredParties: Map[Byte, Set[ActorRef]]): Unit = {
    context watch newRegistrant
    val currentRegistered = registeredParties(msgCode)
    context.become(manageRegister(registeredParties + (msgCode -> (currentRegistered + newRegistrant))))
  }

  final def receive = manageRegister(Map().withDefaultValue(Set()))

}
