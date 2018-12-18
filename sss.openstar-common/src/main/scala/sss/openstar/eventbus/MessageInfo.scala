package sss.openstar.eventbus

trait MessageInfo {
  type T
  val msgCode: Byte
  val clazz: Class[T]
  def fromBytes(bytes: Array[Byte]): T
  def toBytes(t: T)(implicit f: T => Array[Byte]): Array[Byte] = f(t)
}