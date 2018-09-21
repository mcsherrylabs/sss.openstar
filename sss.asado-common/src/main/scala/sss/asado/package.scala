package sss

import akka.actor.ActorRef
import sss.asado.chains.Chains.GlobalChainIdMask

package object asado {

  trait AsadoEvent

  type UniqueNodeIdentifier = String

  case object QueryStatus extends AsadoEvent
  case class Status(any: Any) extends AsadoEvent

  trait QueryStatusSupport {
    protected val ref: ActorRef
    def queryStatus = ref ! QueryStatus
  }
}
