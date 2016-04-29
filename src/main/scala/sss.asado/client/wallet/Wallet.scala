package sss.asado.client.wallet

import akka.actor.{Actor, ActorLogging, Props}
import contract.{Decumbrance, NullDecumbrance}
import ledger._
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.ancillary.Logging
import sss.asado.account.{ClientKey, PrivateKeyAccount}
import sss.asado.client.wallet.WalletPersistence.WalletEntry
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.NetworkMessage
import sss.asado.util.ByteArrayVarcharOps._
import sss.asado.{ClientContext, MessageKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 4/27/16.
  */

case class NewTx(promise: Promise[String], stx: SignedTx)

object Wallet {
  def apply(context:ClientContext, pka: PrivateKeyAccount = ClientKey()) : Wallet = new WalletImpl(pka,
    new WalletPersistence(context.db), context)
}

private class WalletImpl(pka: PrivateKeyAccount,
                         walletPersist: WalletPersistence,
                         context:ClientContext) extends Wallet with Logging {

  import context._
  private var promises: Map[String, Promise[String]] = Map()

  private object WalletActor extends Actor with ActorLogging {

    import block._

    val minConfirms = nodeConfig.getInt("minConfirms")

    messageRouter ! Register(MessageKeys.SignedTxAck)
    messageRouter ! Register(MessageKeys.SignedTxNack)
    messageRouter ! Register(MessageKeys.AckConfirmTx)
    messageRouter ! Register(MessageKeys.NackConfirmTx)

    def createTx(txIndex: TxIndex, amunt: Int): SignedTx = {
      val txOutput = TxOutput(amunt, SinglePrivateKey(pka.publicKey))
      val txInput = TxInput(txIndex, amunt, PrivateKeySig)
      val tx = StandardTx(Seq(txInput), Seq(txOutput))
      val sig = tx.sign(pka)
      SignedTx(tx, Seq(sig))
    }

    override def receive: Receive = {

      case NewTx(promise, stx) =>
        promises += stx.txId.toVarChar -> promise
        var continueFlag = true
        val txId = stx.tx.txId
        Try(walletPersist.inTransaction {
          stx.tx.ins foreach { in =>
            walletPersist.find(in.txIndex) match {
              case Some(entry) if !entry.isUnspent =>
                log.error(s"Cannot continue with this tx, one of the tx inputs is not unspent ${in.txIndex}")
                continueFlag = false
              case None =>
                log.error(s"Cannot continue with this tx, one of the tx inputs is unknown ${in.txIndex}")
                continueFlag = false
              case Some(entry) => walletPersist.spendingTx(entry, txId)
            }
          }
          if (continueFlag) {
            log.info(s"All the inputs were ok ")
            var index = 0
            stx.tx.outs foreach { out =>
              walletPersist.addOrUpdate(TxIndex(txId, index), out.amount)
              index += 1
            }
            log.info(s"All the outputs were recored, sending now. ")
            networkController ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, stx.toBytes))
          }
        }) match {
          case Success(_) =>
          case Failure(e) => log.error(e, "Failed to process new tx ")
        }


      case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
        val txMsg = bytes.toTxMessage
        promises(txMsg.txId.toVarChar).failure(new Exception(txMsg.msg))
        promises -= txMsg.txId.toVarChar

      case NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
        val confirmed = bytes.toBlockChainIdTx

        val numconfirms = walletPersist.confirm(confirmed.blockTxId.txId, minConfirms )
        if(numconfirms >= minConfirms)  {
          promises(confirmed.blockTxId.txId.toVarChar).success(s"$numconfirms")
          promises -= confirmed.blockTxId.txId.toVarChar
          log.info(s"Tx ${confirmed.blockTxId} confirmed.")
        }
        else log.info(s"A Tx ${confirmed.blockTxId} confirmation.")

      case x => log.info(s"No idea $x")
    }
  }
  private val ref = actorSystem.actorOf(Props(WalletActor))

  override def accept(txIndex: TxIndex, amount: Int): Unit = {
    walletPersist.find(txIndex) match {
      case None =>
        val stx = createTx(txIndex, amount, amount, NullDecumbrance)
        walletPersist.addOrUpdate(txIndex, amount)
        walletPersist.markUnspent(txIndex)
        val p = Promise[String]()
        ref ! NewTx(p, stx)
        p.future.map (_ => log.info(s"accepted $txIndex"))
        Await.result(p.future, 1 minute)
      case Some(e) if e.isKnown && e.amount == amount => walletPersist.markUnspent(txIndex)
      case Some(e) if e.isUnspent && e.amount == amount =>
      case Some(e) if e.isSpent => throw new IllegalArgumentException("This index has been spent?!")
      case Some(e) if e.amount != amount =>
        throw new IllegalArgumentException(s"Attempting to change amount from ${e.amount} to $amount")
    }

  }

  override def close(waitDuration : Duration = Duration.Inf): Future[_] = {
    Await.ready(Future.sequence(promises map (_._2.future)), waitDuration)
  }

  override def balance: Int = {
    def addEm(acc : Int, e: WalletEntry): Int = {
      log.info(s"Entry $e")
      acc + e.balance
    }
    walletPersist.findUnSpent.foldLeft(0)(addEm)
  }

  override def send(amount: Int, publicKey: PublicKey): Future[String] = {
    walletPersist.findUnSpent.headOption match {
      case Some(found) =>
        val stx = createTx(found.txIndx, found.amount, amount)
        val p = Promise[String]()
        ref ! NewTx(p, stx)
        p.future
      case None => walletPersist.findUnSpent.headOption match {
        case None => Future.failed(new IllegalArgumentException("No unspent! (or pending)"))
        case Some(mightBeInProcess) =>
          val stx = createTx(mightBeInProcess.txIndx, mightBeInProcess.amount, amount)
          val p = Promise[String]()
          ref ! NewTx(p, stx)
          p.future
      }

    }
  }

  private def createTx(txIndex: TxIndex, totalIn: Int, amuntToSpend: Int, decumbrance: Decumbrance = PrivateKeySig): SignedTx = {
    val txOutput = TxOutput(amuntToSpend, SinglePrivateKey(pka.publicKey))
    val change = totalIn - amuntToSpend
    val outs = if(change > 0) {
      Seq(txOutput, TxOutput(change, SinglePrivateKey(pka.publicKey)))
    } else Seq(txOutput)
    val txInput = TxInput(txIndex, totalIn, decumbrance)
    val tx = StandardTx(Seq(txInput), outs)
    val sig = tx.sign(pka)
    SignedTx(tx, Seq(sig))
  }
}

trait Wallet {
  implicit def toTxIndexFromStrings(txIdAndIndexAsStrs: (String,String)): TxIndex =
    TxIndex(txIdAndIndexAsStrs._1.toByteArray, txIdAndIndexAsStrs._2.toInt)

  def accept(txIndex: TxIndex, amount: Int)
  def balance: Int
  def send(amount: Int, publicKey: PublicKey): Future[String]
  def close(waitDuration : Duration = Duration.Inf): Future[_]
}




