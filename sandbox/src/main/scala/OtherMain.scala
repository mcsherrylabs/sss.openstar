import java.util.Date


import testpackager.ToBytes

class Serailse(val bytes: Array[Byte])

trait CodeType {
  val i: Int
  type T
}

object OtherMain {

  def doIt(s: Serailse)(str: String)(implicit int: Int) = ???

  implicit def f(b:Byte, a: Any): Serailse = ???

  implicit val i: Int = 9

  doIt(1.toByte, "")( "")

  type SENDIT = (Byte, Any, Seq[String]) => Unit

  object Impl extends  SENDIT {
    override def apply(v1: Byte, v2: Any, v3: Seq[String]): Unit = ()
    def apply(v1: Byte, v2: Any, v3: String): Unit = apply(v1,v2,Seq(v3))
    //def apply(s: String): String = s
  }

  Impl(1.toByte, "", "")

  def doit (i: SENDIT): Unit = {
    //i(1.toByte, "", "")
    i(1.toByte, "", Seq(""))
    //val s: String = i("")
  }


}