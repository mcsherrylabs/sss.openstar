package sss.openstar.tools

import sss.openstar.balanceledger.{TxIndex, TxOutput}
import sss.openstar.contract.SingleIdentityEnc
import sss.openstar.ledger._
import sss.openstar.nodebuilder._
import sss.openstar.wallet.WalletPersistence
import sss.openstar.wallet.WalletPersistence.Lodgement
/**
  * Created by alan on 6/7/16.
  */
object AddIdentityTxToWallet {

  class LoadDb(val configName: String) extends
    DbBuilder with
    NodeConfigBuilder with
    ConfigBuilder

  def main(args: Array[String]) {

    if(args.length == 6) {

      val dbLoader = new LoadDb(args(0))
      import dbLoader.db

      val identity = args(1)
      val walletPersistence = new WalletPersistence(identity, db)
      val txId = args(2).asTxId
      val index = args(3).toInt
      val amount = args(4).toInt
      val inBlock = args(5).toLong

      val txIndx = TxIndex(txId, index)
      val txOutput = TxOutput(amount, SingleIdentityEnc(identity, 0))
      walletPersistence.track(Lodgement(txIndx, txOutput, inBlock))

    } else println("Provide the node config string, the " +
      "identity to give to, the txId as hex, the tx index, " +
      "the amount and the block height the value is good from.")
  }
}
