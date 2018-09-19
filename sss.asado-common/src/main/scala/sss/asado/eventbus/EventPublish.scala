package sss.asado.eventbus

import sss.asado.AsadoEvent

import scala.reflect.ClassTag


object PureEvent {
  def apply(code: Byte, many: Array[Byte]): PureEvent = {
    require(many.isEmpty, s"A pure event has no body of bytes associated with it. (payload size ${many.size}")
    PureEvent(code)
  }
}

case class PureEvent(code: Byte)

trait EventPublish {
  def publish[T <: AsadoEvent: ClassTag](event: T): Unit
}