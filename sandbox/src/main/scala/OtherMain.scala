import java.util.Date


import testpackager.ToBytes

class Serailse(val bytes: Array[Byte])

trait CodeType {
  val i: Int
  type T
}

object OtherMain {

  object Base {
    def unapply(arg: Base): Option[String] = Option(arg.s)
  }
  class Base(val s: String = "")

  case class Derivde(override val s: String) extends Base(s)

  def doit(a: Any) = a match {
    case Derivde("sdfdf") => println("empty")
    case Base(s) => println(s)
  }

  def main(args: Array[String]): Unit = {
    doit(Derivde("asdas"))
    //doit(Derivde("sdfdf"))

  }
}