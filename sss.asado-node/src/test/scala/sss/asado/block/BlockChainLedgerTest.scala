package sss.asado.block


import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.NodeIdentity
import sss.asado.DummySeedBytes
import sss.asado.ledger._
import sss.db.Db

import scala.util.{Failure, Try}

/**
  * Created by alan on 2/15/16.
  */

object TestLedger extends Ledger {

  override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {

  }
  override def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId, ledgerId: Byte): Option[LedgerItem] = ???
}
class BlockChainLedgerTest extends FlatSpec with Matchers {


  implicit val db: Db = Db()
  implicit val ledgers = new Ledgers(Map(99.toByte -> TestLedger))

  def resetUtxo = db.executeSql("TRUNCATE TABLE utxo")

  resetUtxo

  val ledger = BlockChainLedger(1)


  def expectIllegalArgument(result: => Any, msg:String = "Something wrong"): Unit = {
    Try(result) match {
      case Failure(e: IllegalArgumentException) =>
      case x => fail(msg)
    }
  }


  def resetUTXOBlockAndCreateTx(height: Long): LedgerItem  = {
    resetUtxo
    Block(height).truncate
    LedgerItem(99, DummySeedBytes(32), DummySeedBytes(12))
  }

  it should "allow journaling of a tx " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    assert(blkChnTx.blockTx.index === 34)
    assert(blkChnTx.blockTx.ledgerItem === stx)
    assert(blkChnTx.height === 2)
  }

  it should "allow repeated journaling of the same tx " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    ledger.journal(BlockTx(34, stx))
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    assert(blkChnTx.blockTx.index === 34)
    assert(blkChnTx.blockTx.ledgerItem === stx)
    assert(blkChnTx.height === 2)
  }

  it should "allow a tx to be committed by BlockId (once)" in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    ledger.commit(BlockId(2, 1))
    intercept[Exception] {ledger(stx) }
  }

  it should "allow a tx to be committed (only once) " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    ledger.commit
    intercept[Exception] {ledger(stx) }
  }


  it should " prevent commit non existent block " in {

    val ledger = BlockChainLedger(2)
    val stx = resetUTXOBlockAndCreateTx(2)

    val nonExistentBlockOrNumTxs = 999
    expectIllegalArgument( ledger.commit(BlockId(nonExistentBlockOrNumTxs, 5)))

    val blkChnTx = ledger.journal(BlockTx(34, stx))
    expectIllegalArgument( ledger.commit(BlockId(2, nonExistentBlockOrNumTxs)))
  }

}
