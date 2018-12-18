package sss.openstar.eventbus

import java.nio.charset.StandardCharsets

import sss.openstar.OpenstarEvent
import sss.openstar.util.Serialize.ToBytes

import scala.reflect.ClassTag


object PureEvent {
  def apply(code: Byte, many: Array[Byte]): PureEvent = {
    require(many.isEmpty, s"A pure event has no body of bytes associated with it. (payload size ${many.size}")
    PureEvent(code)
  }
}

case class StringMessage(value: String) extends ToBytes {
  override def toBytes: Array[Byte] = value.getBytes(StandardCharsets.UTF_8)
}

case class PureEvent(code: Byte)

trait EventPublish {
  def publish[T <: OpenstarEvent: ClassTag](event: T): Unit
}