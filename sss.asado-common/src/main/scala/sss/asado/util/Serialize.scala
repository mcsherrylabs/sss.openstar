package sss.asado.util

import java.nio.charset.StandardCharsets

import com.google.common.primitives.{Ints, Longs}

import scala.annotation.tailrec

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  *
  * Think I could use implicits F bound parameters here,
  * but I don't have the time and it's simpler to read this way.
  *
  */
object Serialize {

  trait Serializer[T] {

    def toBytes(t: T): Array[Byte]
    def fromBytes(b: Array[Byte]): T

  }

  trait ToBytes {
    def toBytes: Array[Byte]
    def ++(byteable: ToBytes): ToBytes = ByteArrayRawSerializer(toBytes ++ byteable.toBytes)
    def ++(bytes: Array[Byte]): Array[Byte] = toBytes ++ bytes
  }

  trait DeSerialized[T] {
    val payload: T
    def apply[K](): K = payload.asInstanceOf[K]
  }

  case class BooleanDeSerialized(payload: Boolean) extends DeSerialized[Boolean]
  case class StringDeSerialized(payload: String) extends DeSerialized[String]
  case class IntDeSerialized(payload: Int) extends DeSerialized[Int]
  case class LongDeSerialized(payload: Long) extends DeSerialized[Long]
  case class ByteDeSerialized(payload: Byte) extends DeSerialized[Byte]
  case class ByteArrayDeSerialized(payload: Array[Byte]) extends DeSerialized[Array[Byte]]
  case class ByteArrayRawDeSerialized(payload: Array[Byte]) extends DeSerialized[Array[Byte]]
  case class SequenceDeSerialized(payload: Seq[Array[Byte]]) extends DeSerialized[Seq[Array[Byte]]]

  trait DeSerializeTarget {
    def extract(bs: Array[Byte]): (DeSerialized[_], Array[Byte])
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
    override def toBytes: Array[Byte] = if(payload) Array(1.toByte) else Array(0.toByte)
  }

  case class ByteSerializer(payload: Byte) extends ToBytes {
    override def toBytes: Array[Byte] = Array(payload)
  }

  case class ByteArrayRawSerializer(payload: Array[Byte]) extends ToBytes {
    override def toBytes: Array[Byte] = payload
  }

  case class ByteArraySerializer(payload: Array[Byte]) extends ToBytes {
    override def toBytes: Array[Byte] = Ints.toByteArray(payload.length) ++ payload
  }

  object BooleanDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (BooleanDeSerialized, Array[Byte]) = {
      val (boolByte, rest) = bs.splitAt(1)
      val result = if(boolByte.head == 1.toByte) true else false
      (BooleanDeSerialized(result), rest)
    }
  }

  object StringDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (StringDeSerialized, Array[Byte]) = {
      val (lenStringBytes, rest) = bs.splitAt(4)
      val lenString = Ints.fromByteArray(lenStringBytes)
      val (strBytes, returnBytes) = rest.splitAt(lenString)
      (StringDeSerialized(new String(strBytes, StandardCharsets.UTF_8)), returnBytes)
    }
  }

  object LongDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (LongDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(8)
      val longVal = Longs.fromByteArray(bytes)
      (LongDeSerialized(longVal), rest)
    }
  }

  object IntDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (IntDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(4)
      val intVal = Ints.fromByteArray(bytes)
      (IntDeSerialized(intVal), rest)
    }
  }

  object ByteDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (ByteDeSerialized, Array[Byte]) = {
      (ByteDeSerialized(bs.head), bs.tail)
    }
  }

  object ByteArrayDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (ByteArrayDeSerialized, Array[Byte]) = {
      val (bytes, rest) = bs.splitAt(4)
      val len = Ints.fromByteArray(bytes)
      val(ary, returnBs) = rest.splitAt(len)
      (ByteArrayDeSerialized(ary), returnBs)
    }
  }

  object ByteArrayRawDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (ByteArrayRawDeSerialized, Array[Byte]) = {
      (ByteArrayRawDeSerialized(bs), Array())
    }
  }

  object SequenceDeSerialize extends DeSerializeTarget {
    override def extract(bs: Array[Byte]): (SequenceDeSerialized, Array[Byte]) = {
      val (seqDeserialized, rest) = SeqSerializer.fromBytesWithRemainder(bs)
      (SequenceDeSerialized(seqDeserialized), rest)
    }
  }

  implicit class SerializeHelper(bytes: Array[Byte]) {

    @tailrec
    private def extract(bs: Array[Byte], acc: List[DeSerialized[Any]], targets: List[DeSerializeTarget]): List[DeSerialized[Any]] = {
      targets match {
        case Nil => acc
        case target :: remainingTargets =>
          val (targetWithValue : DeSerialized[Any], rest) = target.extract(bs)
          extract(rest, acc :+ targetWithValue, remainingTargets)
      }
    }

    def extract(targets: DeSerializeTarget*): List[DeSerialized[_]]= extract(bytes, List(), targets.toList)
  }
}
