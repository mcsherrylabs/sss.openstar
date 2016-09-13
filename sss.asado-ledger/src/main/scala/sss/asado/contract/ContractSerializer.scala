package sss.asado.contract

import sss.asado.contract.SaleOrReturnSecretEnc.HashedSecret
import sss.asado.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object ContractSerializer {

  private[contract] val SinglePrivateKeyCode = 1.toByte
  private[contract] val PrivateKeySigCode = 2.toByte
  private[contract] val NullEncumberCode = 3.toByte
  private[contract] val NullDecumberCode = 4.toByte
  private[contract] val CoinBaseDecumbranceCode = 5.toByte
  private[contract] val SingleIdentityEncCode = 6.toByte
  private[contract] val SingleIdentityDecCode = 7.toByte
  private[contract] val SaleOrReturnSecretEncCode = 8.toByte
  private[contract] val SaleSecretDecCode = 9.toByte
  private[contract] val ReturnSecretDecCode = 10.toByte


  implicit class ContractFromBytes(bytes: Array[Byte]) {

    def toEncumbrance: Encumbrance = {
      bytes.head match {
        case SinglePrivateKeyCode => SinglePrivateKeyToFromBytes.fromBytes(bytes)
        case NullEncumberCode => NullEncumbrance
        case SingleIdentityEncCode => SingleIdentityEncToFromBytes.fromBytes(bytes)
        case SaleOrReturnSecretEncCode => SaleOrReturnSecretEncToFromBytes.fromBytes(bytes)
        case x => throw new Error(s"No such contract known to system $x")
      }
    }

    def toDecumbrance: Decumbrance = {
      bytes.head match {
        case PrivateKeySigCode => PrivateKeySig
        case NullDecumberCode => NullDecumbrance
        case CoinBaseDecumbranceCode => CoinbaseDecumbranceToFromBytes.fromBytes(bytes)
        case SingleIdentityDecCode => SingleIdentityDec
        case SaleSecretDecCode => SaleSecretDec
        case ReturnSecretDecCode => ReturnSecretDec
        case x => throw new Error(s"No such contract known to system $x")
      }
    }
  }

  implicit class ContractToBytes(contract: Contract) {
    def toBytes: Array[Byte] = {
      contract match {
        case a:SinglePrivateKey => SinglePrivateKeyToFromBytes.toBytes(a)
        case PrivateKeySig => Array(PrivateKeySigCode)
        case NullEncumbrance => Array(NullEncumberCode)
        case NullDecumbrance => Array(NullDecumberCode)
        case a:CoinbaseDecumbrance => CoinbaseDecumbranceToFromBytes.toBytes(a)
        case a:SingleIdentityEnc => SingleIdentityEncToFromBytes.toBytes(a)
        case SingleIdentityDec => Array(SingleIdentityDecCode)
        case a:SaleOrReturnSecretEnc => SaleOrReturnSecretEncToFromBytes.toBytes(a)
        case SaleSecretDec =>  Array(SaleSecretDecCode)
        case ReturnSecretDec => Array(ReturnSecretDecCode)
      }
    }
  }

  object SingleIdentityEncToFromBytes extends Serializer[SingleIdentityEnc] {
    override def toBytes(t: SingleIdentityEnc): Array[Byte] = {
      (ByteSerializer(SingleIdentityEncCode) ++
        StringSerializer(t.identity)  ++
        LongSerializer(t.minBlockHeight)).toBytes
    }

    override def fromBytes(b: Array[Byte]): SingleIdentityEnc = {
      val extracted = b.extract(ByteDeSerialize, StringDeSerialize, LongDeSerialize)
      val headerByte = extracted(0)[Byte]
      require(headerByte == SingleIdentityEncCode, s"Wrong header byte, expecting $SingleIdentityEncCode, got $headerByte")
      SingleIdentityEnc(extracted(1)[String], extracted(2)[Long])
    }
  }

  object CoinbaseDecumbranceToFromBytes extends Serializer[CoinbaseDecumbrance] {
    override def toBytes(t: CoinbaseDecumbrance): Array[Byte] = {
      (ByteSerializer(CoinBaseDecumbranceCode) ++ LongSerializer(t.blockHeight)).toBytes
    }

    override def fromBytes(b: Array[Byte]): CoinbaseDecumbrance = {
      val extracted = b.extract(ByteDeSerialize, LongDeSerialize)
      val headerByte = extracted(0)[Byte]
      require(headerByte == CoinBaseDecumbranceCode, s"Wrong header byte, expecting $CoinBaseDecumbranceCode, got $headerByte")
      CoinbaseDecumbrance(extracted(1)[Long])
    }
  }

  object SinglePrivateKeyToFromBytes extends Serializer[SinglePrivateKey] {

    override def toBytes(t: SinglePrivateKey): Array[Byte] = {
      (ByteSerializer(SinglePrivateKeyCode) ++
      LongSerializer(t.minBlockHeight) ++
      ByteArraySerializer(t.pKey)).toBytes
    }

    override def fromBytes(b: Array[Byte]): SinglePrivateKey = {
      val extracted = b.extract(ByteDeSerialize, LongDeSerialize, ByteArrayDeSerialize)
      val headerByte = extracted(0)[Byte]
      require(headerByte == SinglePrivateKeyCode, s"Wrong header byte, expecting $SinglePrivateKeyCode, got $headerByte")
      SinglePrivateKey(extracted(2)[Array[Byte]], extracted(1)[Long])
    }
  }

  object SaleOrReturnSecretEncToFromBytes extends Serializer[SaleOrReturnSecretEnc] {

    override def toBytes(t: SaleOrReturnSecretEnc): Array[Byte] = {
      (ByteSerializer(SaleOrReturnSecretEncCode) ++
        StringSerializer(t.returnIdentity) ++
        StringSerializer(t.claimant) ++
        ByteArraySerializer(t.hashOfSecret.bytes) ++
        LongSerializer(t.returnBlockHeight)).toBytes
    }

    override def fromBytes(b: Array[Byte]): SaleOrReturnSecretEnc = {
      val extracted = b.extract(ByteDeSerialize,
        StringDeSerialize, StringDeSerialize,
        ByteArrayDeSerialize, LongDeSerialize)

      val headerByte = extracted(0)[Byte]
      require(headerByte == SaleOrReturnSecretEncCode, s"Wrong header byte, expecting $SaleOrReturnSecretEncCode, got $headerByte")
      SaleOrReturnSecretEnc(extracted(1)[String],
        extracted(2)[String],
        HashedSecret(extracted(3)[Array[Byte]]),
        extracted(4)[Long]
        )
    }
  }
}