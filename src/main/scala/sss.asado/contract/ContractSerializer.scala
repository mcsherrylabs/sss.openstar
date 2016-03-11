package sss.asado.contract

import com.google.common.primitives.Ints
import contract.{NullDecumbrance, Decumbrance, NullEncumbrance, Encumbrance}


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object ContractSerializer {


  trait ToFromBytes[A] {
    def from(aryBytes: Array[Byte]): A
    def to(a: A): Array[Byte]
    protected def extractId(bytes: Array[Byte]): (Int, Array[Byte]) = {
      val (contractIdBytes, contractBytes) = bytes.splitAt(4)
      (Ints.fromByteArray(contractIdBytes),contractBytes)
    }
  }

  object ToFromBytes {
    def apply[A:ToFromBytes]: ToFromBytes[A] = implicitly
  }

  def fromBytes[A:ToFromBytes](bytes: Array[Byte]) = ToFromBytes[A].from(bytes)
  def toBytes[A:ToFromBytes](a: A): Array[Byte] = ToFromBytes[A].to(a)

  implicit object EncumbranceFromBytes extends ToFromBytes[Encumbrance] {
    def from(bytes: Array[Byte]) = {
      extractId(bytes) match {
        case (2, contractBytes) => SinglePrivateKeyToFromBytes.from(bytes)
        case(3, bytes) => NullEncumbrance
        case x => throw new Error(s"No such encumbrance known to system $x")
      }
    }
    def to(a: Encumbrance): Array[Byte] = {
      a match {
        case spk @ SinglePrivateKey(_) => SinglePrivateKeyToFromBytes.to(spk)
        case nll @ NullEncumbrance => UselessEncumbranceToFromBytes.to(nll)
      }
    }
  }

  implicit object DecumbranceFromBytes extends ToFromBytes[Decumbrance] {
    def from(bytes: Array[Byte]) = {
      extractId(bytes) match {
        case (1, bytes) => PrivateKeySig
        case(4, bytes) => NullDecumbrance
        case x => throw new Error(s"No such decumbrance known to system $x")
      }
    }
    def to(a: Decumbrance): Array[Byte] = {
      a match {
        case pSig @ PrivateKeySig => PrivateKeySigFromBytes.to(pSig)
        case nll @ NullDecumbrance => UselessDecumbranceToFromBytes.to(nll)
      }
    }
  }


  implicit object UselessDecumbranceToFromBytes extends ToFromBytes[NullDecumbrance.type] {
    def from(x: Array[Byte]) =  extractId(x) match {
      case (4, bytes) => NullDecumbrance
      case (unknownId, bytes) => throw new Error(s"Contract type $unknownId does not match NullDecumbrance!")
    }
    def to(a: NullDecumbrance.type): Array[Byte] = Ints.toByteArray(4)
  }

  implicit object UselessEncumbranceToFromBytes extends ToFromBytes[NullEncumbrance.type] {
    def from(x: Array[Byte]) = extractId(x) match {
      case (3, bytes) => NullEncumbrance
      case (unknownId, bytes) => throw new Error(s"Contract type $unknownId does not match NullEncumbrance!")
    }
    def to(a: NullEncumbrance.type): Array[Byte] = Ints.toByteArray(3)
  }

  implicit object PrivateKeySigFromBytes extends ToFromBytes[PrivateKeySig.type] {
    def from(x: Array[Byte]) =  extractId(x) match {
      case (1, bytes) => PrivateKeySig
      case (unknownId, bytes) => throw new Error(s"Contract type $unknownId does not match PrivateKeySig!")
    }
    def to(a: PrivateKeySig.type): Array[Byte] = Ints.toByteArray(1)
  }

  implicit object SinglePrivateKeyToFromBytes extends ToFromBytes[SinglePrivateKey] {
    def from(x: Array[Byte]) = extractId(x) match {
      case (2, bytes) => SinglePrivateKey(bytes)
      case (unknownId, bytes) => throw new Error(s"Contract type $unknownId does not match SinglePrivateKey!")
    }
    def to(a: SinglePrivateKey): Array[Byte] = Ints.toByteArray(2) ++ a.pKey
  }
}