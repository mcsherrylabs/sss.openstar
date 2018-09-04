package sss.asado

import java.nio.charset.StandardCharsets

import com.google.common.primitives.Ints
import sss.asado.account.NodeIdentity
import sss.asado.block.BlockId
import sss.asado.util.ByteArrayComparisonOps._
import sss.asado.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr
import sss.asado.util.hash.SecureCryptographicHash
import sss.asado.util.{ByteArrayEncodedStrOps, SeqSerializer}

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

  case class LedgerItem(ledgerId: Byte, txId : TxId, txEntryBytes: Array[Byte]) {
    def toBytes: Array[Byte] =  ledgerId +: (txId ++ txEntryBytes)
    lazy val txIdHexStr : String = txId.toBase64Str

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

  trait Ledger {
    @throws[LedgerException]
    def apply(ledgerItem: LedgerItem, blockheight: Long)
    def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId, ledgerId: Byte): Option[LedgerItem] = None
  }

  case class LedgerException(ledgerId: Byte, msg: String) extends Exception(msg)

  class Ledgers(ledgers: Map[Byte, Ledger]) {
    def apply(ledgerItem: LedgerItem, blockHeight: Long) = ledgers(ledgerItem.ledgerId)(ledgerItem, blockHeight)
    def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId): Iterable[LedgerItem] = {
      val opts = ledgers map {case (k,v) => v.coinbase(nodeIdentity, blockId, k)}
      opts collect { case Some(le) => le}
    }
  }

}
