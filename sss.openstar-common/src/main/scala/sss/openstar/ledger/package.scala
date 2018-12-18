package sss.openstar

import java.nio.charset.StandardCharsets

import com.google.common.primitives.Ints
import sss.openstar.account.NodeIdentity
import sss.openstar.common.block.BlockId
import sss.openstar.util.ByteArrayComparisonOps._
import sss.openstar.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr
import sss.openstar.util.Serialize.ToBytes
import sss.openstar.util.hash.SecureCryptographicHash
import sss.openstar.util.{ByteArrayEncodedStrOps, SeqSerializer}


import scala.util.Try


/**
  * Created by alan on 5/24/16.
  */
package object ledger {

  type TxId = Array[Byte]
  val TxIdLen = 32
  val CoinbaseTxId: TxId = "COINBASECOINBASECOINBASECOINBASE".getBytes(StandardCharsets.UTF_8)

  implicit class TxIdFromString(txId: String) {
    def asTxId: TxId = {
      val bytes = ByteArrayEncodedStrOps.Base64StrToByteArray(txId).toByteArray
      assert(bytes.length == TxIdLen, s"Bytes decoded not right len for txId, ${bytes.length} should be $TxIdLen")
      bytes
    }
  }

  implicit class ToLedgerItem(bytes : Array[Byte]) {
    def toLedgerItem: LedgerItem = {
      val ledgerId = bytes.head
      val (ledgerTxId, ledgerBytes) = bytes.tail.splitAt(TxIdLen)
      LedgerItem(ledgerId, ledgerTxId, ledgerBytes)
    }
  }

  case class SeqLedgerItem(val value: Seq[LedgerItem]) extends AnyVal

  implicit class SeqLedgerItemToBytes(v: SeqLedgerItem) extends ToBytes {
    override def toBytes: Array[Byte] = SeqSerializer.toBytes(v.value map (_.toBytes))
  }

  implicit class SeqLedgerItemFromBytes(bytes: Array[Byte]) {
    def toSeqLedgerItem: SeqLedgerItem =
      SeqLedgerItem(
        SeqSerializer.fromBytes(bytes) map (_.toLedgerItem)
      )
  }

  case class LedgerItem(ledgerId: Byte, txId : TxId, txEntryBytes: Array[Byte]) extends OpenstarEvent with ToBytes {

    require(txId.size == TxIdLen, s"LedgerItem cannot create a TxId with len ${txId.size}, must be $TxIdLen")

    override def toBytes: Array[Byte] =  ledgerId +: (txId ++ txEntryBytes)

    lazy val txIdHexStr : String = txId.toBase64Str

    override def toString: String = {
      s"LedgerItem(ledgerId:$ledgerId, txId: $txIdHexStr txEntryBytes:[${txEntryBytes.toBase64Str.take(5)}...])"
    }

    override def equals(obj: scala.Any): Boolean = obj match {
      case le: LedgerItem =>
        le.ledgerId == ledgerId &&
          le.txId.isSame(txId) &&
          le.txEntryBytes.isSame(txEntryBytes)

      case _ => false
    }

    override def hashCode(): Int = 17 * txId.hash

  }

  case class SignedTxEntry(txEntryBytes: Array[Byte], signatures: Seq[Seq[Array[Byte]]] = Seq()) {
    def toBytes: Array[Byte] = {
      val lenAsBytes = Ints.toByteArray(txEntryBytes.length)
      val seqsAsBytes = SeqSerializer.toBytes(signatures.map(SeqSerializer.toBytes(_))) ++ txEntryBytes
      lenAsBytes ++ txEntryBytes ++ seqsAsBytes
    }


    override def equals(obj: scala.Any): Boolean = obj match {
      case ste: SignedTxEntry =>
        ste.txEntryBytes.isSame(txEntryBytes) &&
          (ste.signatures.flatten isSame signatures.flatten)

      case _ => false
    }

    override def hashCode(): Int = (17 + signatures.flatten.hash) * txId.hash

    lazy val txId: TxId = {
      SecureCryptographicHash.hash(txEntryBytes)
    }

  }

  implicit class ToTxEntryWithSignatures(bytes : Array[Byte]) {
    def toSignedTxEntry: SignedTxEntry = {
      val (lenAsBytes, rest) = bytes.splitAt(4)
      val len = Ints.fromByteArray(lenAsBytes)
      val (txEntry, seqsAsBytes) = rest.splitAt(len)
      val seqOfSeq = SeqSerializer.fromBytes(seqsAsBytes)
      val allDeserialized = seqOfSeq map SeqSerializer.fromBytes
      SignedTxEntry(txEntry, allDeserialized)
    }
  }

  //When you apply a LedgerItem to a ledger you get a result
  //a Failure or a list of events to be fired after the item is committed
  type LedgerResult = Try[Seq[OpenstarEvent]]
  //type Hash = Array[Byte]

  trait Ledger {
    //def fingerprint(): Hash
    def apply(ledgerItem: LedgerItem, blockId: BlockId): LedgerResult
    def coinbase(nodeIdentity: NodeIdentity, forBlockHeight: Long, ledgerId: Byte): Option[LedgerItem] = None
  }

  case class LedgerException(ledgerId: Byte, msg: String) extends Exception(msg)

  class Ledgers(ledgers: Map[Byte, Ledger]) {

    //def fingerprint: Hash = MerkleTree(ledgers.values.map(fingerprint)
    def apply(ledgerItem: LedgerItem, blockId: BlockId):LedgerResult =
      ledgers(ledgerItem.ledgerId)(ledgerItem, blockId)

    def coinbase(nodeIdentity: NodeIdentity, forBlockHeight: Long): Iterable[LedgerItem] = {
      val opts = ledgers map {case (k,v) => v.coinbase(nodeIdentity, forBlockHeight, k)}
      opts collect { case Some(le) => le}
    }
  }

}
