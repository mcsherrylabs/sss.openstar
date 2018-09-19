import java.util.Date

package object testpackager {

  val someCrap: Int = 5

  trait ToBytes {
    def toBytes: Array[Byte]
  }

  implicit class DateToBytes(val i: Date) extends ToBytes {
    override def toBytes: Array[Byte] = Array()
  }

  implicit class IntToBytes(val i: Int) extends ToBytes {
    override def toBytes: Array[Byte] = Array(i.toByte)
  }

  implicit class StringToBytes(val i: String) extends ToBytes {
    override def toBytes: Array[Byte] = i.getBytes
  }
}

