package txflood

import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.common.block.BlockChainTxId
import sss.asado.contract.SingleIdentityEnc
import sss.asado.nodebuilder.ClientNode
import sss.asado.wallet.IntegratedWallet.{Payment, TxFailure, TxSuccess}
import sss.asado.wallet.WalletPersistence.Lodgement

import scala.concurrent.duration._

/**
  * Created by alan on 8/5/16.
  */
object Main  {

  implicit val timeout = Duration(10, SECONDS)

  def main(args: Array[String]) {
    val identity = args(0)
    val amount = args(1).toInt
    val batchSize =  args(2).toInt
    val sleepMs =  args(3).toLong
    val iterations =  args(4).toInt

    val client = new ClientNode {
      override val phrase: Option[String] = Option("password")
      override val configName: String = "node"
      lazy override val nodeIdentity: NodeIdentity = {
        val idStr = s"testsender${bindSettings.port}"
        val defTag = "defaultTag"

        NodeIdentity(idStr, defTag, new String(phrase.get))
      }
    }


    import client._

    initStateMachine
    Thread.sleep(1000)
    startNetwork
    Thread.sleep(1000)
    connectHome

    val payment = Payment(identity, amount)

    val bal = integratedWallet.balance
    log.info(s"About to start balance is $bal")
    if(bal > 0) {
      for (j <- 0 until iterations) {
        for (i <- 0 until batchSize) {
          integratedWallet.pay(payment) match {
            case TxSuccess(blockChainTxId: BlockChainTxId, txIndex: TxIndex, txIdentifier: Option[String]) =>
              val txOutput = TxOutput(amount, SingleIdentityEnc(identity, 0))
              integratedWallet.credit(Lodgement(txIndex, txOutput, blockChainTxId.height))
            case TxFailure(txMessage, txIdentifier) =>
              log.error(s"PROBLEM: ${txMessage}")
          }
        }
        Thread.sleep(sleepMs)
      }
    }
    log.info(s"Shutdown please....")
  }


}
