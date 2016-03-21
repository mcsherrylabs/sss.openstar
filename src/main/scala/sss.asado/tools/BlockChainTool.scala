package sss.asado.tools


import javax.xml.bind.DatatypeConverter

import ledger.{GenisesTx, TxOutput}
import sss.ancillary.Configure
import sss.asado.block.BlockChain
import sss.asado.contract.SinglePrivateKey
import sss.asado.ledger.UTXOLedger
import sss.asado.storage.UTXODBStorage
import sss.asado.util.RootKey
import sss.db.Db


/**
  * Created by alan on 3/21/16.
  */
object BlockChainTool extends Configure {

  private def p(a: Any) = println(a)

  implicit var db: Db = _

  lazy val utxos = new UTXOLedger(new UTXODBStorage())

  def main(args: Array[String]) {

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"
    db = Db(dbConfig)

    sys addShutdownHook( db shutdown)

    args.tail.toList match {
      case Nil => p("Choose from init, genesis, rootkey...")
      case "init" :: tail => init(tail)
      case "genesis" :: tail => genesis(tail)

    }
  }

  def genesis(args: List[String]): Unit = {
    args match {
      case Nil => p("Give a figure for the initial purse...")
      case amount :: rest => {
        val gx = GenisesTx(outs = Seq(TxOutput(amount.toInt, SinglePrivateKey(RootKey.pubKey))))
        p("TxId Genesis is ... ")
        val str = DatatypeConverter.printHexBinary(gx.txId)
        p(str)
        utxos.genesis(gx)
      }
    }
  }
  def init(args: List[String]): Unit = {
    val bc = new BlockChain()
    p(s"Last block was ${bc.lastBlock}")
    bc.genesisBlock()
    p(s"${bc.lastBlock} <- now")
  }
}
