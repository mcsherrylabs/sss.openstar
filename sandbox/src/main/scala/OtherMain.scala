import java.util.Date


import testpackager.ToBytes

class Serailse(val bytes: Array[Byte])

trait CodeType {
  val i: Int
  type T
}

object OtherMain {

  val m : Map[Int, CodeType] = Map()

  object Serailse {

    def apply[T <% ToBytes](t: T): Serailse = {
      new Serailse(t.toBytes)
    }
  }


  def main(args: Array[String]): Unit = {
    Serailse("")
    Serailse(4)
    Serailse(new Date())

  }
}