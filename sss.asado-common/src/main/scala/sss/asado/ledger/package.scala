package sss.asado

import java.nio.charset.StandardCharsets

import com.google.common.primitives.Ints
import sss.asado.account.NodeIdentity
import sss.asado.block.BlockId
import sss.asado.util.ByteArrayComparisonOps._
import sss.asado.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr
import sss.asado.util.Serialize._
import sss.asado.util.hash.SecureCryptographicHash
import sss.asado.util.{ByteArrayEncodedStrOps, SeqSerializer}

/**
  * Created by alan on 5/24/16.
  */
package object ledger {

  type LedgerId = Byte

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
      LedgerItem.tupled(
        bytes.extract(
          ByteDeSerialize,
          ByteArrayDeSerialize,
          ByteArrayDeSerialize
        )
      )
    }
  }

  case class LedgerItem(ledgerId: LedgerId, txId : TxId, txEntryBytes: Array[Byte]) {
    def toBytes: Array[Byte] =
      ByteSerializer(ledgerId) ++
        ByteArraySerializer(txId) ++
        ByteArraySerializer(txEntryBytes).toBytes

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

    val id: LedgerId

    @throws[LedgerException]
    def apply(ledgerItem: LedgerItem, blockheight: Long)
    def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId, ledgerId: LedgerId): Option[LedgerItem] = None
  }

  case class LedgerException(ledgerId: LedgerId, msg: String) extends Exception(msg)

  class Ledgers(ledgers: Seq[Ledger]) {

    //Note the order of val initialisation is important.
    val ordered: Seq[Ledger] = {
      ledgers.sortWith(_.id < _.id)
    }

    val byId: Map[LedgerId, Ledger] = {
      (ordered map(l => l.id -> l))
        .toMap
    }

    assert(byId.size == ordered.size, "Check the ledgers parameter for duplicate ids...")

    def apply(ledgerItem: LedgerItem, blockHeight: Long) = byId(ledgerItem.ledgerId)(ledgerItem, blockHeight)
    def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId): Iterable[LedgerItem] = {
      val opts = ledgers map {l => l.coinbase(nodeIdentity, blockId, l.id)}
      opts collect { case Some(le) => le}
    }
  }

}
