package sss.asado.util

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import com.google.common.primitives.{Ints, Longs}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  *
  *
  */
object Serialize {
  import TupleOps._

  trait Serializer[T] {

    def toBytes(t: T): Array[Byte]
    def fromBytes(b: Array[Byte]): T

  }

  trait ToBytes {
    def toBytes: Array[Byte]
    def ++(byteable: ToBytes): ToBytes =
      ByteArrayRawSerializer(toBytes ++ byteable.toBytes)
    def ++(bytes: Array[Byte]): Array[Byte] = toBytes ++ bytes
  }

  trait DeSerialized[T] {
    val payload: T
  }

  case class BooleanDeSerialized(payload: Boolean) extends DeSerialized[Boolean]
  case class StringDeSerialized(payload: String) extends DeSerialized[String]
  case class IntDeSerialized(payload: Int) extends DeSerialized[Int]
  case class LongDeSerialized(payload: Long) extends DeSerialized[Long]
  case class ByteDeSerialized(payload: Byte) extends DeSerialized[Byte]
  case class ByteStringDeSerialized(payload: ByteString)
      extends DeSerialized[ByteString]
  case class ByteArrayDeSerialized(payload: Array[Byte])
      extends DeSerialized[Array[Byte]]
  case class ByteArrayRawDeSerialized(payload: Array[Byte])
      extends DeSerialized[Array[Byte]]
  case class SequenceDeSerialized(payload: Seq[Array[Byte]])
      extends DeSerialized[Seq[Array[Byte]]]

  trait DeSerializeTarget {
    type t
    def extract(bs: Array[Byte]): (DeSerialized[t], Array[Byte])
  }

  case class StringSerializer(payload: String) extends ToBytes {
    override def toBytes: Array[Byte] = {
      val asBytes = payload.getBytes(StandardCharsets.UTF_8)
      val len = asBytes.length
      Ints.toByteArray(len) ++ asBytes
    }
  }

  case class IntSerializer(payload: Int) extends ToBytes {
    override def toBytes: Array[Byte] = Ints.toByteArray(payload)
  }

  case class SequenceSerializer(payload: Seq[Array[Byte]]) extends ToBytes {
    override def toBytes: Array[Byte] = SeqSerializer.toBytes(payload)
  }

  case class LongSerializer(payload: Long) extends ToBytes {
    override def toBytes: Array[Byte] = Longs.toByteArray(payload)
  }

  case class BooleanSerializer(payload: Boolean) extends ToBytes {
    override def toBytes: Array[Byte] =
      if (payload) Array(1.toByte) else Array(0.toByte)
  }

  case class ByteSerializer(payload: Byte) extends ToBytes {
    override def toBytes: Array[Byte] = Array(payload)
  }

  case class ByteArrayRawSerializer(payload: Array[Byte]) extends ToBytes {
    override def toBytes: Array[Byte] = payload
  }

  case class ByteArraySerializer(payload: Array[Byte]) extends ToBytes {
    override def toBytes: Array[Byte] =
      Ints.toByteArray(payload.length) ++ payload
  }

  case class ByteStringSerializer(payload: ByteString) extends ToBytes {
    override def toBytes: Array[Byte] = {
      assert(payload.toArray.length == payload.length, "WHAT?")
      Ints.toByteArray(payload.length) ++ payload
    }
  }

  object BooleanDeSerialize extends DeSerializeTarget {
    type t = Boolean
    override def extract(
        bs: Array[Byte]): (BooleanDeSerialized, Array[Byte]) = {
      val (boolByte, rest) = bs.splitAt(1)
      val result = if (boolByte.head == 1.toByte) true else false
      (BooleanDeSerialized(result), rest)
    }
  }

  case class StringDeSerialize[T](m: String => T) extends DeSerializeTarget {
    type t = T
    override def extract(bs: Array[Byte]): (DeSerialized[T], Array[Byte]) = {
      val (strDes, rest) = StringDeSerialize.extract(bs)
      (new DeSerialized[T] { val payload = m(strDes.payload)}, rest)
    }
  }

  object StringDeSerialize extends DeSerializeTarget {
    type t = String
    override def extract(bs: Array[Byte]): (StringDeSerialized, Array[Byte]) = {
      val (lenStringBytes, rest) = bs.splitAt(4)
      val lenString = Ints.fromByteArray(lenStringBytes)
      val (strBytes, returnBytes) = rest.splitAt(lenString)
      (StringDeSerialized(new String(strBytes, StandardCharsets.UTF_8)),
       returnBytes)
    }
  }

  object LongDeSerialize extends DeSerializeTarget {
    type t = Long
    override def extract(bs: Array[Byte]): (LongDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(8)
      val longVal = Longs.fromByteArray(bytes)
      (LongDeSerialized(longVal), rest)
    }
  }

  case class LongDeSerialize[T](m: Long => T) extends DeSerializeTarget {
    type t = T
    override def extract(bs: Array[Byte]): (DeSerialized[T], Array[Byte]) = {
      val (longVal, rest) = LongDeSerialize.extract(bs)
      val result = new DeSerialized[T] { val payload = m(longVal.payload)}
      (result, rest)
    }
  }

  object IntDeSerialize extends DeSerializeTarget {
    type t = Int
    override def extract(bs: Array[Byte]): (IntDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(4)
      val intVal = Ints.fromByteArray(bytes)
      (IntDeSerialized(intVal), rest)
    }
  }

  object ByteDeSerialize extends DeSerializeTarget {
    type t = Byte
    override def extract(bs: Array[Byte]): (ByteDeSerialized, Array[Byte]) = {
      (ByteDeSerialized(bs.head), bs.tail)
    }
  }

  object ByteStringDeSerialize extends DeSerializeTarget {
    type t = ByteString
    override def extract(
        bs: Array[Byte]): (ByteStringDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(4)
      val len = Ints.fromByteArray(bytes)
      val (ary, returnBs) = rest.splitAt(len)
      (ByteStringDeSerialized(ByteString(ary)), returnBs)
    }
  }

  case class ByteArrayDeSerialize[T](mapper: Array[Byte] => T)
      extends DeSerializeTarget {
    type t = T
    override def extract(bs: Array[Byte]): (DeSerialized[T], Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(4)
      val len = Ints.fromByteArray(bytes)
      val (ary, returnBs) = rest.splitAt(len)
      (new DeSerialized[T] { val payload = (mapper(ary)) }, returnBs)
    }
  }

  object ByteArrayDeSerialize extends DeSerializeTarget {
    type t = Array[Byte]
    override def extract(
        bs: Array[Byte]): (ByteArrayDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(4)
      val len = Ints.fromByteArray(bytes)
      val (ary, returnBs) = rest.splitAt(len)
      (ByteArrayDeSerialized(ary), returnBs)
    }
  }

  case class ByteArrayRawDeSerialize[T](mapper: Array[Byte] => T)
      extends DeSerializeTarget {
    type t = T

    override def extract(bs: Array[Byte]): (DeSerialized[T], Array[Byte]) = {
      (new DeSerialized[T] { val payload = (mapper(bs)) }, Array())
    }
  }

  object ByteArrayRawDeSerialize extends DeSerializeTarget {
    type t = Array[Byte]

    override def extract(
        bs: Array[Byte]): (ByteArrayRawDeSerialized, Array[Byte]) = {
      (ByteArrayRawDeSerialized(bs), Array())
    }
  }

  object SequenceDeSerialize extends DeSerializeTarget {
    type t = Seq[Array[Byte]]
    override def extract(
        bs: Array[Byte]): (SequenceDeSerialized, Array[Byte]) = {
      val (seqDeserialized, rest) = SeqSerializer.fromBytesWithRemainder(bs)
      (SequenceDeSerialized(seqDeserialized), rest)
    }
  }

  implicit class SerializeHelper(val bytes: Array[Byte]) extends AnyVal {

    def extract(target: DeSerializeTarget): target.t = {
      extract(bytes, target)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget): target.t = {
      target.extract(bytes)._1.payload
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget): (target.t, target2.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload, extract(targetPlusRemainder._2, target2))
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget): (target.t, target2.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload, extract(targetPlusRemainder._2, target2))
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget): (target.t, target2.t, target3.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3)
    }

    private def extract(
        bytes: Array[Byte],
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget): (target.t, target2.t, target3.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
    ): (target.t, target2.t, target3.t, target4.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
    ): (target.t, target2.t, target3.t, target4.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
    ): (target.t, target2.t, target3.t, target4.t, target5.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
    ): (target.t, target2.t, target3.t, target4.t, target5.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
    ): (target.t, target2.t, target3.t, target4.t, target5.t, target6.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
    ): (target.t, target2.t, target3.t, target4.t, target5.t, target6.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
        target7: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
        target7: DeSerializeTarget,
        target8: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
        target7: DeSerializeTarget,
        target8: DeSerializeTarget,
        target9: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
        target7: DeSerializeTarget,
        target8: DeSerializeTarget,
        target9: DeSerializeTarget,
        target10: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t,
        target10.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t,
        target10.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
        target7: DeSerializeTarget,
        target8: DeSerializeTarget,
        target9: DeSerializeTarget,
        target10: DeSerializeTarget,
        target11: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t,
        target10.t,
        target11.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t,
        target10.t,
        target11.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11)
    }

    def extract(
        target: DeSerializeTarget,
        target2: DeSerializeTarget,
        target3: DeSerializeTarget,
        target4: DeSerializeTarget,
        target5: DeSerializeTarget,
        target6: DeSerializeTarget,
        target7: DeSerializeTarget,
        target8: DeSerializeTarget,
        target9: DeSerializeTarget,
        target10: DeSerializeTarget,
        target11: DeSerializeTarget,
        target12: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t,
        target10.t,
        target11.t,
        target12.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
    ): (target.t,
        target2.t,
        target3.t,
        target4.t,
        target5.t,
        target6.t,
        target7.t,
        target8.t,
        target9.t,
        target10.t,
        target11.t,
        target12.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12)
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget,
                target3: DeSerializeTarget,
                target4: DeSerializeTarget,
                target5: DeSerializeTarget,
                target6: DeSerializeTarget,
                target7: DeSerializeTarget,
                target8: DeSerializeTarget,
                target9: DeSerializeTarget,
                target10: DeSerializeTarget,
                target11: DeSerializeTarget,
                target12: DeSerializeTarget,
                target13: DeSerializeTarget): (target.t,
                                               target2.t,
                                               target3.t,
                                               target4.t,
                                               target5.t,
                                               target6.t,
                                               target7.t,
                                               target8.t,
                                               target9.t,
                                               target10.t,
                                               target11.t,
                                               target12.t,
                                               target13.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
                        target13: DeSerializeTarget): (target.t,
                                                       target2.t,
                                                       target3.t,
                                                       target4.t,
                                                       target5.t,
                                                       target6.t,
                                                       target7.t,
                                                       target8.t,
                                                       target9.t,
                                                       target10.t,
                                                       target11.t,
                                                       target12.t,
                                                       target13.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13)
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget,
                target3: DeSerializeTarget,
                target4: DeSerializeTarget,
                target5: DeSerializeTarget,
                target6: DeSerializeTarget,
                target7: DeSerializeTarget,
                target8: DeSerializeTarget,
                target9: DeSerializeTarget,
                target10: DeSerializeTarget,
                target11: DeSerializeTarget,
                target12: DeSerializeTarget,
                target13: DeSerializeTarget,
                target14: DeSerializeTarget): (target.t,
                                               target2.t,
                                               target3.t,
                                               target4.t,
                                               target5.t,
                                               target6.t,
                                               target7.t,
                                               target8.t,
                                               target9.t,
                                               target10.t,
                                               target11.t,
                                               target12.t,
                                               target13.t,
                                               target14.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13,
                                                  target14)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
                        target13: DeSerializeTarget,
                        target14: DeSerializeTarget): (target.t,
                                                       target2.t,
                                                       target3.t,
                                                       target4.t,
                                                       target5.t,
                                                       target6.t,
                                                       target7.t,
                                                       target8.t,
                                                       target9.t,
                                                       target10.t,
                                                       target11.t,
                                                       target12.t,
                                                       target13.t,
                                                       target14.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13,
                                                  target14)
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget,
                target3: DeSerializeTarget,
                target4: DeSerializeTarget,
                target5: DeSerializeTarget,
                target6: DeSerializeTarget,
                target7: DeSerializeTarget,
                target8: DeSerializeTarget,
                target9: DeSerializeTarget,
                target10: DeSerializeTarget,
                target11: DeSerializeTarget,
                target12: DeSerializeTarget,
                target13: DeSerializeTarget,
                target14: DeSerializeTarget,
                target15: DeSerializeTarget): (target.t,
                                               target2.t,
                                               target3.t,
                                               target4.t,
                                               target5.t,
                                               target6.t,
                                               target7.t,
                                               target8.t,
                                               target9.t,
                                               target10.t,
                                               target11.t,
                                               target12.t,
                                               target13.t,
                                               target14.t,
                                               target15.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13,
                                                  target14,
                                                  target15)
    }
    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
                        target13: DeSerializeTarget,
                        target14: DeSerializeTarget,
                        target15: DeSerializeTarget): (target.t,
                                                       target2.t,
                                                       target3.t,
                                                       target4.t,
                                                       target5.t,
                                                       target6.t,
                                                       target7.t,
                                                       target8.t,
                                                       target9.t,
                                                       target10.t,
                                                       target11.t,
                                                       target12.t,
                                                       target13.t,
                                                       target14.t,
                                                       target15.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13,
                                                  target14,
                                                  target15)
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget,
                target3: DeSerializeTarget,
                target4: DeSerializeTarget,
                target5: DeSerializeTarget,
                target6: DeSerializeTarget,
                target7: DeSerializeTarget,
                target8: DeSerializeTarget,
                target9: DeSerializeTarget,
                target10: DeSerializeTarget,
                target11: DeSerializeTarget,
                target12: DeSerializeTarget,
                target13: DeSerializeTarget,
                target14: DeSerializeTarget,
                target15: DeSerializeTarget,
                target16: DeSerializeTarget): (target.t,
                                               target2.t,
                                               target3.t,
                                               target4.t,
                                               target5.t,
                                               target6.t,
                                               target7.t,
                                               target8.t,
                                               target9.t,
                                               target10.t,
                                               target11.t,
                                               target12.t,
                                               target13.t,
                                               target14.t,
                                               target15.t,
                                               target16.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13,
                                                  target14,
                                                  target15,
                                                  target16)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
                        target13: DeSerializeTarget,
                        target14: DeSerializeTarget,
                        target15: DeSerializeTarget,
                        target16: DeSerializeTarget): (target.t,
                                                       target2.t,
                                                       target3.t,
                                                       target4.t,
                                                       target5.t,
                                                       target6.t,
                                                       target7.t,
                                                       target8.t,
                                                       target9.t,
                                                       target10.t,
                                                       target11.t,
                                                       target12.t,
                                                       target13.t,
                                                       target14.t,
                                                       target15.t,
                                                       target16.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(targetPlusRemainder._2,
                                                  target2,
                                                  target3,
                                                  target4,
                                                  target5,
                                                  target6,
                                                  target7,
                                                  target8,
                                                  target9,
                                                  target10,
                                                  target11,
                                                  target12,
                                                  target13,
                                                  target14,
                                                  target15,
                                                  target16)
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
                        target13: DeSerializeTarget,
                        target14: DeSerializeTarget,
                        target15: DeSerializeTarget,
                        target16: DeSerializeTarget,
                        target17: DeSerializeTarget): (target.t,
                                                       target2.t,
                                                       target3.t,
                                                       target4.t,
                                                       target5.t,
                                                       target6.t,
                                                       target7.t,
                                                       target8.t,
                                                       target9.t,
                                                       target10.t,
                                                       target11.t,
                                                       target12.t,
                                                       target13.t,
                                                       target14.t,
                                                       target15.t,
                                                       target16.t,
                                                       target17.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(
        targetPlusRemainder._2,
        target2,
        target3,
        target4,
        target5,
        target6,
        target7,
        target8,
        target9,
        target10,
        target11,
        target12,
        target13,
        target14,
        target15,
        target16,
        target17
      )
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget,
                target3: DeSerializeTarget,
                target4: DeSerializeTarget,
                target5: DeSerializeTarget,
                target6: DeSerializeTarget,
                target7: DeSerializeTarget,
                target8: DeSerializeTarget,
                target9: DeSerializeTarget,
                target10: DeSerializeTarget,
                target11: DeSerializeTarget,
                target12: DeSerializeTarget,
                target13: DeSerializeTarget,
                target14: DeSerializeTarget,
                target15: DeSerializeTarget,
                target16: DeSerializeTarget,
                target17: DeSerializeTarget): (target.t,
                                               target2.t,
                                               target3.t,
                                               target4.t,
                                               target5.t,
                                               target6.t,
                                               target7.t,
                                               target8.t,
                                               target9.t,
                                               target10.t,
                                               target11.t,
                                               target12.t,
                                               target13.t,
                                               target14.t,
                                               target15.t,
                                               target16.t,
                                               target17.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(
        targetPlusRemainder._2,
        target2,
        target3,
        target4,
        target5,
        target6,
        target7,
        target8,
        target9,
        target10,
        target11,
        target12,
        target13,
        target14,
        target15,
        target16,
        target17
      )
    }

    def extract(target: DeSerializeTarget,
                target2: DeSerializeTarget,
                target3: DeSerializeTarget,
                target4: DeSerializeTarget,
                target5: DeSerializeTarget,
                target6: DeSerializeTarget,
                target7: DeSerializeTarget,
                target8: DeSerializeTarget,
                target9: DeSerializeTarget,
                target10: DeSerializeTarget,
                target11: DeSerializeTarget,
                target12: DeSerializeTarget,
                target13: DeSerializeTarget,
                target14: DeSerializeTarget,
                target15: DeSerializeTarget,
                target16: DeSerializeTarget,
                target17: DeSerializeTarget,
                target18: DeSerializeTarget): (target.t,
                                               target2.t,
                                               target3.t,
                                               target4.t,
                                               target5.t,
                                               target6.t,
                                               target7.t,
                                               target8.t,
                                               target9.t,
                                               target10.t,
                                               target11.t,
                                               target12.t,
                                               target13.t,
                                               target14.t,
                                               target15.t,
                                               target16.t,
                                               target17.t,
                                               target18.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(
        targetPlusRemainder._2,
        target2,
        target3,
        target4,
        target5,
        target6,
        target7,
        target8,
        target9,
        target10,
        target11,
        target12,
        target13,
        target14,
        target15,
        target16,
        target17,
        target18
      )
    }

    private def extract(bytes: Array[Byte],
                        target: DeSerializeTarget,
                        target2: DeSerializeTarget,
                        target3: DeSerializeTarget,
                        target4: DeSerializeTarget,
                        target5: DeSerializeTarget,
                        target6: DeSerializeTarget,
                        target7: DeSerializeTarget,
                        target8: DeSerializeTarget,
                        target9: DeSerializeTarget,
                        target10: DeSerializeTarget,
                        target11: DeSerializeTarget,
                        target12: DeSerializeTarget,
                        target13: DeSerializeTarget,
                        target14: DeSerializeTarget,
                        target15: DeSerializeTarget,
                        target16: DeSerializeTarget,
                        target17: DeSerializeTarget,
                        target18: DeSerializeTarget): (target.t,
                                                       target2.t,
                                                       target3.t,
                                                       target4.t,
                                                       target5.t,
                                                       target6.t,
                                                       target7.t,
                                                       target8.t,
                                                       target9.t,
                                                       target10.t,
                                                       target11.t,
                                                       target12.t,
                                                       target13.t,
                                                       target14.t,
                                                       target15.t,
                                                       target16.t,
                                                       target17.t,
                                                       target18.t) = {
      val targetPlusRemainder = target.extract(bytes)
      (targetPlusRemainder._1.payload) +: extract(
        targetPlusRemainder._2,
        target2,
        target3,
        target4,
        target5,
        target6,
        target7,
        target8,
        target9,
        target10,
        target11,
        target12,
        target13,
        target14,
        target15,
        target16,
        target17,
        target18
      )
    }
  }
}
