package sss

import akka.actor.ActorRef
import sss.openstar.chains.Chains.GlobalChainIdMask

package object openstar {

  trait OpenstarEvent

  type UniqueNodeIdentifier = String

  case object QueryStatus extends OpenstarEvent
  case class Status(any: Any) extends OpenstarEvent

  trait QueryStatusSupport {
    protected val ref: ActorRef
    def queryStatus = ref ! QueryStatus
  }
}
