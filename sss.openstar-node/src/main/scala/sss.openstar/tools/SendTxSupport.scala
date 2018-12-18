package sss.openstar.tools

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.chains.LeaderElectionActor.{LeaderFound, LeaderLost}
import sss.openstar.chains.TxWriterActor.{InternalTxResult, _}
import sss.openstar.ledger.LedgerItem
import sss.openstar.network.MessageEventBus
import sss.openstar.tools.SendTxSupport.{SendTx, TxTracker}
import sss.openstar.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr

import scala.language.postfixOps
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Future, Promise}
import scala.util.Random

object SendTxSupport {

  private case class TxTracker(ledgerItem: LedgerItem, p: Promise[InternalTxResult], timeout: Duration)

  trait SendTx extends ((LedgerItem, Duration) => Future[InternalTxResult]) {
    def apply(le: LedgerItem): Future[InternalTxResult] = apply(le, 15 seconds)
  }


  def apply(implicit actorSystem: ActorSystem,
            chainId: GlobalChainIdMask,
            messageEventBus: MessageEventBus): SendTx = {
    new SendTxSupport()
  }
}

class SendTxSupport(implicit actorSystem: ActorSystem,
                    chainId: GlobalChainIdMask,
                    messageEventBus: MessageEventBus) extends SendTx {

  private val ref = actorSystem.actorOf(Props(SendTxActor), s"SendTxActor_$chainId")

  override def apply(le: LedgerItem, timeout: Duration): Future[InternalTxResult] = {
    val p = Promise[InternalTxResult]()
    ref ! TxTracker(le,p, timeout)
    p.future
  }

  private object SendTxActor extends Actor with ActorLogging {

    messageEventBus.subscribe(classOf[LeaderFound])
    messageEventBus.subscribe(classOf[LeaderLost])

    private def waitForLeader: Receive = {
      case TxTracker(le,p, _) => p.failure(new RuntimeException("No leader found, cannot process txs"))

      case f: LeaderFound if(f.chainId == chainId) =>
        log.info(s"SendTxSupport started with leader ${f}")
        context become onLeaderFound
    }

    override def receive = onLeaderFound

    def onLeaderFound: Receive = {
      case TxTracker(le,p, timeout) =>

        val trackerRef = context.actorOf(Props.create(classOf[TxTrackerActor], p, timeout),
          s"TxTrackerActor_${chainId}_${le.txId.toBase64Str}_RND${Random.nextLong()}")

        messageEventBus.publish(InternalLedgerItem(chainId, le, Some(trackerRef)))

      case l: LeaderLost if (l.chainId == chainId) => context become waitForLeader
    }
  }

}

private class TxTrackerActor(result: Promise[InternalTxResult], timeout: Duration) extends Actor with ActorLogging {

  context.setReceiveTimeout(timeout)

  override def unhandled(message: Any): Unit =
    log.error("Tx Tracker received unexpected message {}", message)

  override def receive: Receive = {

    case ReceiveTimeout =>
      result.failure(new RuntimeException(s"TxTrackerActor received no response in $timeout"))
      context stop self

    case txResult: InternalAck =>
      log.debug("Got ack, waiting for commit or Nack")

    case txResult: InternalTxResult =>
      result.success(txResult)
      context stop self
  }

}
