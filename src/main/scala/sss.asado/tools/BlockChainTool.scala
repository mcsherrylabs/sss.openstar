package sss.asado.tools


import javax.xml.bind.DatatypeConverter

import contract.NullEncumbrance
import ledger.{GenisesTx, TxIndex, TxOutput}
import sss.ancillary.Configure
import sss.asado.account.PrivateKeyAccount
import sss.asado.block.BlockChain
import sss.asado.ledger.{Ledger, UTXOLedger}
import sss.asado.storage.{TxDBStorage, UTXODBStorage}
import sss.db.{Db, Where}


/**
  * Created by alan on 3/21/16.
  */
object BlockChainTool extends Configure {

  private def p(a: Any) = println(a)

  implicit var db: Db = _

  lazy val utxos = new UTXOLedger(new UTXODBStorage())
  lazy val ledger = new Ledger(1, TxDBStorage(1), utxos)
  lazy val utxosTable = db.table("utxo")
  lazy val blockHeaderTable = db.table(BlockChain.tableName)

  def main(args: Array[String]) {

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"
    db = Db(dbConfig)

    args.tail.toList match {
      case Nil => p("Choose from init, genesis, createkey...")
      case "init" :: tail => init(tail)
      case "block0" :: tail => block0(tail)
      case "genesis" :: tail => genesis(tail)
      case "createkey" :: tail => createKey(tail)
      case "dumpdb" :: tail => dumpDb(tail)
      case x => p(s"$x not handled")

    }
  }

  def dumpDb(args: List[String]): Unit = {
    println(s"Total utxos ${utxosTable.count}, printing ..")
    var count = 0
    utxosTable.filter(Where("indx > -1 LIMIT ?", 100)) map { r =>
      val tmp = TxIndex(DatatypeConverter.parseHexBinary(r[String]("txid")), r[Int]("indx"))
      utxos.entry(tmp) match {
        case None =>
        case Some(entry) => println(s"$tmp ${entry.amount}")
      }
    }
    println(s"Total blocks ${blockHeaderTable.count}, printing ..")
    blockHeaderTable.map( r =>
    println(r)
    )

  }
  def createKey(args: List[String]): Unit = {
    val acc = PrivateKeyAccount()
    p(s"Private Key ${acc.privateKey}")
    p(s"Public Key ${acc.publicKey}")
    p(s"Address ${acc.address}")
  }

  def genesis(args: List[String]): Unit = {
    val amount = args match {
      case Nil => p("Using default figure for the initial purse..."); 1000000
      case amount :: rest => amount.toInt
    }

    val gx = GenisesTx(outs = Seq(TxOutput(amount, NullEncumbrance)))
    p("TxId Genesis is ... ")
    val str = DatatypeConverter.printHexBinary(gx.txId)
    p(str)
    val txDbId = ledger.genesis(gx).get
    TxDBStorage.confirm(gx.txId, txDbId.height, txDbId.id)
    utxos.entry(TxIndex(gx.txId, 0)) map (println)
  }

  def init(args: List[String]): Unit = {
    val bc = new BlockChain()
    //p(s"Last block was ${bc.lastBlock}")
    bc.genesisBlock()

    p(s"${bc.lastBlock} <- now")
    genesis(args)
  }

  def block0(args: List[String]): Unit = {
    val bc = new BlockChain()
    //p(s"Last block was ${bc.lastBlock}")
    bc.genesisBlock()
    p(s"${bc.lastBlock} <- now")
  }
}
