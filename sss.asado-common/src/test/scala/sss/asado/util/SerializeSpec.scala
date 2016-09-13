package sss.asado.util


import org.scalatest.{FlatSpec, Matchers}
import sss.asado.crypto.SeedBytes
import sss.asado.util.Serialize.{ByteArraySerializer, _}
/**
  * Created by alan on 2/11/16.
  */
class SerializeSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  def fromBytes(bytes: Array[Byte]): TestSeriliazer = {
    val extracted = bytes.extract(ByteDeSerialize,
      LongDeSerialize,
      StringDeSerialize,
      IntDeSerialize,
      SequenceDeSerialize,
      ByteArrayDeSerialize,
      BooleanDeSerialize,
      ByteArrayRawDeSerialize)


    val recursiveSeq = extracted(4)[Seq[Array[Byte]]] map fromBytes
    TestSeriliazer(extracted(0)[Byte],recursiveSeq,
      extracted(1)[Long],
      extracted(2)[String],
      extracted(3)[Int],
      extracted(5)[Array[Byte]],
      extracted(6)[Boolean],
      extracted(7)[Array[Byte]])
  }
  case class TestSeriliazer(byteHeader: Byte,
                            tricky: Seq[TestSeriliazer],
                            longVal: Long,
                            someString: String,
                            intVal: Int,
                            byteArray: Array[Byte],
                            isTrue: Boolean,
                            byteArrayNoHeader: Array[Byte]
                          ) extends ByteArrayComparisonOps {
    def toBytes: Array[Byte] = {
      (ByteSerializer(byteHeader) ++
        LongSerializer(longVal) ++
        StringSerializer(someString) ++
        IntSerializer(intVal) ++
        SequenceSerializer(tricky.map(_.toBytes)) ++
        ByteArraySerializer(byteArray) ++
        BooleanSerializer(isTrue) ++
        ByteArrayRawSerializer(byteArrayNoHeader)).toBytes
    }

    def checkFields(that: TestSeriliazer): Boolean = {
      byteHeader == that.byteHeader &&
      tricky == that.tricky &&
      longVal == that.longVal &&
      someString == that.someString &&
      intVal == that.intVal &&
      byteArray.isSame(that.byteArray) &&
      byteArrayNoHeader.isSame(that.byteArrayNoHeader) &&
      isTrue == that.isTrue
    }

    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: TestSeriliazer => checkFields(that)
        case _ => false
      }
    }

  }

  case class SimpleTestSerilizer(byteHeader: Byte,
                           longVal: Long,
                           someString: String,
                           intVal: Int,
                                 byteArray: Array[Byte],
                                 byteArrayNoHeader: Array[Byte]
                          )

  val bHeader = 1.toByte
  val bHeader2 = 2.toByte
  val bHeader3 = 3.toByte
  val bHeader4 = 4.toByte
  val longVal: Long = Long.MaxValue
  val intVal : Int = Int.MaxValue
  val someString = "Hello cruel world"
  val byteArray = SeedBytes(45)
  val byteArrayNoHeader = SeedBytes(440)
  val test = TestSeriliazer(bHeader, Seq(), longVal, someString, intVal, byteArray, true, byteArrayNoHeader)
  val test2 = TestSeriliazer(bHeader2, Seq(test), longVal, someString, intVal, byteArray, false, byteArrayNoHeader)
  val test3 = TestSeriliazer(bHeader3, Seq(test, test2), longVal, someString, intVal, byteArray, true, byteArrayNoHeader)
  val test4 = SimpleTestSerilizer(bHeader4, longVal, someString, intVal, byteArray, byteArrayNoHeader)

  "The serializer " should " make it easy to serialize common types " in {

    val bytes = ByteSerializer(test4.byteHeader) ++
      LongSerializer(test4.longVal) ++
      StringSerializer(test4.someString) ++
      IntSerializer(test4.intVal) ++
      ByteArraySerializer(test4.byteArray) ++
      ByteArrayRawSerializer(test4.byteArrayNoHeader)


    val deserialised = bytes.toBytes.extract(ByteDeSerialize,
      LongDeSerialize,
      StringDeSerialize,
      IntDeSerialize,
      ByteArrayDeSerialize,
      ByteArrayRawDeSerialize)

    assert(deserialised.size == 6)
    assert(deserialised(0)[Byte] == bHeader4)
    assert(deserialised(1)[Long] == longVal)
    assert(deserialised(2)[String] == someString)
    assert(deserialised(3)[Int] == intVal)
    assert(byteArray isSame deserialised(4).payload.asInstanceOf[Array[Byte]])
    assert(byteArrayNoHeader isSame deserialised(5)().asInstanceOf[Array[Byte]])

  }

  it should "also handle sequences " in {
    val testAsBytes = test.toBytes
    val backAgain = fromBytes(testAsBytes)

    assert(backAgain === test)

  }

  it should "also handle recursive types " in {
    val testAsBytes = test3.toBytes
    val backAgain = fromBytes(testAsBytes)

    assert(backAgain === test3)

  }

  it should "also handle empty strings" in {
    case class Striny(test:String)
    val bytes = (StringSerializer("") ++
      StringSerializer("Not empty") ++
      StringSerializer("")++
      StringSerializer("")).toBytes

    val extracted = bytes.extract(StringDeSerialize, StringDeSerialize, StringDeSerialize, StringDeSerialize)
    assert(extracted(0)[String]== "")
    assert(extracted(1)[String]== "Not empty")
    assert(extracted(2)[String]== "")
    assert(extracted(3)[String]== "")
  }
}
