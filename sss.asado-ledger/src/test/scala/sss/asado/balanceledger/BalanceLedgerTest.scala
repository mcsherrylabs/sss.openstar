package sss.asado.balanceledger

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.{NodeIdentityManager, PrivateKeyAccount}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block.BlockId
import sss.asado.contract._
import sss.asado.identityledger.IdentityService
import sss.asado.ledger._
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */
class BalanceLedgerTest extends FlatSpec with Matchers {

  val nodeIdentityManager = new NodeIdentityManager(DummySeedBytes)
  nodeIdentityManager.deleteKey("testKey", "tag")
  val nodeIdentity = nodeIdentityManager("testKey", "tag", "phrase1233333333")

  implicit val chainId: GlobalChainIdMask = 1.toByte

  def pKeyOfFirstSigner(blockHeight:Long) = Option(nodeIdentity.publicKey)

  implicit val db: Db = Db()

  val cbv = CoinbaseValidator(pKeyOfFirstSigner, 100, 0)
  val identityService = IdentityService()
  val ledger = BalanceLedger(cbv, identityService)

  //FIXME SHAMEFULLY the state of validOut is used throughout this test.
  var validOut: TxIndex = _
  var cbTx: SignedTxEntry = _

  "A coinbase tx " should " honour the 'num blocks in future' setting " in {
    val numBlockInFuture = 10
    val cbv = CoinbaseValidator(pKeyOfFirstSigner, 100, numBlockInFuture)
    val ledger = BalanceLedger(cbv, identityService)
    val le = ledger.coinbase(nodeIdentity, 50, 1.toByte).get

    val initBal = ledger.balance
    assert(le.txEntryBytes.toSignedTxEntry.txEntryBytes.toTx.outs.head.encumbrance.asInstanceOf[SinglePrivateKey].minBlockHeight == 60)

    // can only ledger into same block
    ledger(le, BlockId(50, 1)).get

    assert(ledger(le, BlockId(51, 1)).isFailure)

    assert(ledger.balance - initBal == 100)
  }

  "A Ledger " should " allow coinbase tx " in {
    val le = ledger.coinbase(nodeIdentity, 2, 1.toByte).get
    cbTx = le.txEntryBytes.toSignedTxEntry
    val initBal = ledger.balance
    ledger(le, BlockId(2, 1))
    assert(ledger.balance - initBal == 100)
  }

  it should "only allow 1 coinbase per block " in {

    val le = ledger.coinbase(nodeIdentity, 2, 1.toByte).get
    assert(ledger(le, BlockId(2, 1)).isFailure)
  }

  it should " prevent txs with bad balances " in {

    val ins = Seq(TxInput(TxIndex(cbTx.txId, 0), 1000,  NullDecumbrance))
    val outs = Seq(TxOutput(1, NullEncumbrance), TxOutput(1, NullEncumbrance))
    intercept[IllegalArgumentException] {
      val le = ledger.apply(
        SignedTxEntry(StandardTx(ins, outs).toBytes, Seq()),
        BlockId(0, 1))
    }
  }

  it should " prevent txs with no inputs " in {

    val ins: Seq[TxInput] = Seq()
    val outs: Seq[TxOutput] = Seq()
    intercept[IllegalArgumentException] {
      val le = ledger.apply(
        SignedTxEntry(StandardTx(ins, outs).toBytes, Seq()),
        BlockId(0, 1))
    }
  }

  it should " not allow out balance to be greater than in " in {

    val ins = Seq(TxInput(TxIndex(cbTx.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(11, NullEncumbrance))
    intercept[IllegalArgumentException] {
      val le = ledger.apply(
        SignedTxEntry(StandardTx(ins, outs).toBytes, Seq(Seq())),
        BlockId(0, 1))
    }
  }

  it should " prevent double spend and honour min block height " in {

    val ins = Seq(TxInput(TxIndex(cbTx.txId, 0), 100,  PrivateKeySig))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val tx = StandardTx(ins, outs)
    val stx = SignedTxEntry(tx.toBytes, Seq(Seq(nodeIdentity.sign(tx.txId))))
    validOut = TxIndex(stx.txId, 0)

    intercept[IllegalArgumentException] {
      // minimum block height not exceeded
      ledger.apply(SignedTxEntry(StandardTx(ins, outs).toBytes), BlockId(3, 1))
    }

    val le = ledger.apply(stx, BlockId(4, 2))

    intercept[IllegalArgumentException] {
      // cannot double spend.
      ledger.apply(SignedTxEntry(StandardTx(ins, outs).toBytes), BlockId(4, 1))
    }
  }

  it should " prevent spend from invalid tx in" in {

    val ins = Seq(TxInput(TxIndex(DummySeedBytes.randomSeed(3), 2), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    intercept[IllegalArgumentException] {
      ledger.apply(SignedTxEntry(StandardTx(ins, outs).toBytes), BlockId(0, 1))
    }
  }

  it should " allow spending from a tx out that was also a tx " in {

    val ins = Seq(TxInput(validOut, 99, NullDecumbrance))
    val outs = Seq(TxOutput(98, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val stx = SignedTxEntry(StandardTx(ins, outs).toBytes, Seq(Seq()))
    ledger(stx, BlockId(0, 2))
    val nextIns = Seq(TxInput(TxIndex(stx.txId, 0), 98, NullDecumbrance))
    val nextOuts = Seq(TxOutput(1, NullEncumbrance),TxOutput(97, NullEncumbrance))
    val nextTx = SignedTxEntry(StandardTx(nextIns, nextOuts).toBytes,  Seq(Seq()))
    ledger(nextTx, BlockId(0, 2))

    validOut = TxIndex(nextTx.txId, 1)

    intercept[IllegalArgumentException] {
      ledger(stx, BlockId(0, 2))
    }

    intercept[IllegalArgumentException] {
      ledger(nextTx, BlockId(0, 2))
    }
  }

  it should " handle different encumbrances on different inputs " in {

    lazy val pkPair1 = PrivateKeyAccount(DummySeedBytes.randomSeed(32))
    lazy val pkPair2 = PrivateKeyAccount(DummySeedBytes.randomSeed(32))

    val ins = Seq(TxInput(validOut, 97, NullDecumbrance))

    val outs = Seq(TxOutput(1, SinglePrivateKey(pkPair1.publicKey)), TxOutput(96, SinglePrivateKey(pkPair2.publicKey)))
    val stx = SignedTxEntry(StandardTx(ins, outs).toBytes,  Seq(Seq()))
    ledger(stx, BlockId(0, 2))
    val nextIns = Seq(TxInput(TxIndex(stx.txId, 0), 1, PrivateKeySig), TxInput(TxIndex(stx.txId, 1), 96, PrivateKeySig))
    val nextOuts = Seq(TxOutput(97, NullEncumbrance))
    val nextTx = StandardTx(nextIns, nextOuts)

    val nextSignedTx = SignedTxEntry(nextTx.toBytes, Seq(Seq(pkPair1.sign(nextTx.txId)), Seq(pkPair2.sign(nextTx.txId))))
    ledger(nextSignedTx, BlockId(0, 2))

  }
}
