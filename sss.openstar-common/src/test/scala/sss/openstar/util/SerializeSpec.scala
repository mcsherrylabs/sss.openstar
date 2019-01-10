package sss.openstar.util

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.util.Serialize._

/**
  * Created by alan on 2/11/16.
  */
class SerializeSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  case class TestSerializerSimple(byteHeader: Byte,
                                  longVal: Long,
                                  someString: String) {

    def toBytes: Array[Byte] = {
      (ByteSerializer(byteHeader) ++
        LongSerializer(longVal) ++
        StringSerializer(someString)).toBytes

    }

  }

  def simpleFromBytes(bytes: Array[Byte]): TestSerializerSimple = {
    TestSerializerSimple.tupled(
      bytes.extract(ByteDeSerialize, LongDeSerialize, StringDeSerialize))
  }

  def fromBytes(bytes: Array[Byte]): TestSeriliazer = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize,
                                  IntDeSerialize,
                                  SequenceDeSerialize,
                                  ByteArrayDeSerialize,
                                  BooleanDeSerialize,
                                  ByteArrayRawDeSerialize)

    val s = extracted._5
    val recursiveSeq = s map fromBytes

    TestSeriliazer(extracted._1,
                   recursiveSeq,
                   extracted._2,
                   extracted._3,
                   extracted._4,
                   extracted._6,
                   extracted._7,
                   extracted._8)
  }

  case class TestSeriliazer(byteHeader: Byte,
                            tricky: Seq[TestSeriliazer],
                            longVal: Long,
                            someString: String,
                            intVal: Int,
                            byteArray: Array[Byte],
                            isTrue: Boolean,
                            byteArrayNoHeader: Array[Byte])
      extends ByteArrayComparisonOps {
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
        case _                    => false
      }
    }

  }

  case class SimpleTestSerilizer(byteHeader: Byte,
                                 longVal: Long,
                                 someString: String,
                                 intVal: Int,
                                 byteArray: Array[Byte],
                                 byteArrayNoHeader: Array[Byte])

  val bHeader = 1.toByte
  val bHeader2 = 2.toByte
  val bHeader3 = 3.toByte
  val bHeader4 = 4.toByte
  val longVal: Long = Long.MaxValue
  val intVal: Int = Int.MaxValue
  val someString = "Hello cruel world"
  val byteArray = DummySeedBytes.randomSeed(45)
  val byteArrayNoHeader = DummySeedBytes.randomSeed(440)
  val test = TestSeriliazer(bHeader,
                            Seq(),
                            longVal,
                            someString,
                            intVal,
                            byteArray,
                            true,
                            byteArrayNoHeader)
  val test2 = TestSeriliazer(bHeader2,
                             Seq(test),
                             longVal,
                             someString,
                             intVal,
                             byteArray,
                             false,
                             byteArrayNoHeader)
  val test3 = TestSeriliazer(bHeader3,
                             Seq(test, test2),
                             longVal,
                             someString,
                             intVal,
                             byteArray,
                             true,
                             byteArrayNoHeader)
  val test4 = SimpleTestSerilizer(bHeader4,
                                  longVal,
                                  someString,
                                  intVal,
                                  byteArray,
                                  byteArrayNoHeader)

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

    assert((deserialised._1) == bHeader4)
    assert((deserialised._2) == longVal)
    assert((deserialised._3) == someString)
    assert((deserialised._4) == intVal)
    assert(byteArray isSame deserialised._5)
    assert(byteArrayNoHeader isSame deserialised._6)

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
    case class Striny(test: String)
    val bytes = (StringSerializer("") ++
      StringSerializer("Not empty") ++
      StringSerializer("") ++
      StringSerializer("")).toBytes

    val extracted = bytes.extract(StringDeSerialize,
                                  StringDeSerialize,
                                  StringDeSerialize,
                                  StringDeSerialize)
    assert(extracted._1 === "")
    assert(extracted._2 === "Not empty")
    assert(extracted._3 === "")
    assert(extracted._4 === "")
  }

  case class One(a: Option[Int], b: Option[One])

  case class OneSerializer(o: One) extends ToBytes {
    override def toBytes: Array[Byte] = {
      (OptionSerializer[Int](o.a, IntSerializer) ++ OptionSerializer[One](o.b, OneSerializer)).toBytes
    }
  }

  it should "serialize branches (optional values)" in {

    val sut = One(Some(3), Some(One(None, None)))
    val asBytes = OneSerializer(sut).toBytes

    def deSer(bs: Array[Byte]): One = {
      One.tupled(bs.extract(
        OptionDeSerialize(IntDeSerialize),
        OptionDeSerialize(ByteArrayRawDeSerialize(deSer))
        )
      )
    }

    val inflated = deSer(asBytes)

    assert(inflated === sut)
  }

  case class OneOr(a: Either[Int, String], b: Either[OneOr, Long])

  case class OneOrSerializer(o: OneOr) extends ToBytes {
    override def toBytes: Array[Byte] = {
      (EitherSerializer[Int, String](o.a, IntSerializer, StringSerializer) ++
        EitherSerializer[OneOr, Long](o.b, OneOrSerializer, LongSerializer)).toBytes
    }
  }

  it should "serialize branches (either values)" in {

    val sut = OneOr(Right("stringy"), Left(OneOr(Left(2), Right(Long.MaxValue))))

    val asBytes = OneOrSerializer(sut).toBytes

    def deSer(bs: Array[Byte]): OneOr = {
      OneOr.tupled(bs.extract(
        EitherDeSerialize(IntDeSerialize, StringDeSerialize),
        EitherDeSerialize(ByteArrayRawDeSerialize(deSer), LongDeSerialize)
      )
      )
    }

    val inflated = deSer(asBytes)

    assert(inflated === sut)
  }


}
