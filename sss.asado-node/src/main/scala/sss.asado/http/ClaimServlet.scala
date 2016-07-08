package sss.asado.http


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import org.scalatra.{BadRequest, Ok, ScalatraServlet}
import sss.asado.balanceledger.TxIndex
import sss.asado.block._
import sss.asado.identityledger.Claim
import sss.asado.ledger._
import sss.asado.network.NetworkMessage
import sss.asado.util.ByteArrayVarcharOps._
import sss.asado.wallet.IntegratedWallet
import sss.asado.wallet.IntegratedWallet.{Payment, TxFailure, TxSuccess}
import sss.asado.{MessageKeys, PublishedMessageKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 5/7/16.
  */
class ClaimServlet(actorSystem:ActorSystem,
                   messageRouter: ActorRef,
                   integratedWallet: IntegratedWallet) extends ScalatraServlet {

  import ClaimServlet._

  private val claimsActor = actorSystem.actorOf(Props(classOf[ClaimsResultsActor], messageRouter, integratedWallet))

  get("/") {
    params.get("claim") match {
      case None => BadRequest("Param 'claim' not found")
      case Some(claiming) => params.get("pKey") match {
        case None => BadRequest("Param 'pKey' not found")
        case Some(pKeyStr) =>
            val claim = Claim(claiming, pKeyStr.toByteArray)
            val ste = SignedTxEntry(claim.toBytes)
            val le = LedgerItem(MessageKeys.IdentityLedger , claim.txId, ste.toBytes)
            val p = Promise[String]()
            claimsActor ! Claiming(claiming, le.txIdHexStr, NetworkMessage(PublishedMessageKeys.SignedTx, le.toBytes), p)
            p.future.map { Ok(_) }
            Await.result(p.future, Duration(1, MINUTES))

      }
    }
  }
}

object ClaimServlet {

  case class Claiming(identity: String, txIdHex: String, netMsg: NetworkMessage, p: Promise[String])
  case class ClaimTracker(sendr: ActorRef, claiming: Claiming)
}

class ClaimsResultsActor(messageRouter: ActorRef, integratedWallet: IntegratedWallet) extends Actor with ActorLogging {

  import ClaimServlet._
  var inFlightClaims: Map[String, ClaimTracker] = Map()

  val kickStartingAmount = 100

  private def working: Receive = {

    case c@Claiming(identity, txIdHex, netMsg, promise) =>
      inFlightClaims += (txIdHex-> ClaimTracker(sender(), c))
      messageRouter ! netMsg

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val bcTxId = bytes.toBlockChainIdTx
      val hexId = bcTxId.blockTxId.txId.toVarChar
      val trackerOpt = inFlightClaims.get(hexId)
      Try {
        trackerOpt map { claimTracker =>
          integratedWallet.payAsync(self, Payment(claimTracker.claiming.identity, kickStartingAmount, Option(hexId), 1, 0))
        }
      } match {
        case Failure(e) =>
          log.info(e.getMessage)
          trackerOpt.map(_.claiming.p.success(s"fail:${e.getMessage}") )
        case Success(_) =>
      }

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      val txMsg = bytes.toTxMessage
      val hexId = txMsg.txId.toVarChar
      val claimTracker = inFlightClaims(hexId)
      inFlightClaims -= hexId
      claimTracker.claiming.p.success(s"fail:${txMsg.msg}")

    case TxSuccess(blockChainTxId, txIndex, txTracking) =>
      inFlightClaims.get(txTracking.get) map { claimTracker =>
        val indx = TxIndex(blockChainTxId.blockTxId.txId, 0)
        claimTracker.claiming.p.success(s"ok:${txIndex.toString}:$kickStartingAmount:${blockChainTxId.height}")
      }
    case TxFailure(txMsg, txTracking) =>
      inFlightClaims.get(txTracking.get) map { claimTracker =>
        claimTracker.claiming.p.success(s"okNoMoney:${txMsg.msg}")
      }
  }

  override def postStop = log.info("ClaimsResultsActor has stopped")
  override def receive: Receive = working

}
