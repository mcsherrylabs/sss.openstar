package sss.asado.eventbus

trait MessageInfo {
  type T
  val msgCode: Byte
  val clazz: Class[T]
  def fromBytes(bytes: Array[Byte]): T
}