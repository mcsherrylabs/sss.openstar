package sss.openstar.tools

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.openstar.MessageKeys
import sss.openstar.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.openstar.chains.TxWriterActor.InternalTxResult
import sss.openstar.identityledger.Claim
import sss.openstar.ledger.{LedgerItem, SignedTxEntry}
import sss.openstar.network.MessageEventBus
import sss.openstar.nodebuilder.BootstrapIdentity
import sss.openstar.tools.SendTxSupport.SendTx
import akka.pattern.pipe
import sss.openstar.account.NodeIdentity

import scala.concurrent.{ExecutionContext, Future}

object TestnetConfiguration {

  def apply(bootstrapIdentities: List[BootstrapIdentity])(implicit actorSystem: ActorSystem,
                                                          nodeIdentity: NodeIdentity,
            sendTx: SendTx,
            messageEventBus: MessageEventBus
           ): ActorRef = {
    actorSystem.actorOf(
      Props(classOf[TestnetConfiguration],
        bootstrapIdentities,
        nodeIdentity,
        sendTx,
        messageEventBus)
      , "TestnetConfiguration"
    )
  }
}

class TestnetConfiguration(bootstrapIdentities: List[BootstrapIdentity])(implicit nodeIdentity: NodeIdentity,
                           sendTx: SendTx,
                           messageEventBus: MessageEventBus) extends Actor with ActorLogging {

  messageEventBus.subscribe(classOf[BlockChainReady])

  implicit val ec: ExecutionContext = context.dispatcher


  private def claim(nodeId: String, pKey: PublicKey): Future[InternalTxResult] = {
    val tx = Claim(nodeId, pKey)
    val ste = SignedTxEntry(tx.toBytes, Seq())
    val le = LedgerItem(MessageKeys.IdentityLedger, tx.txId, ste.toBytes)
    sendTx(le)
  }


  var once = false

  override def receive: Receive = {

    case Failure(e) =>
      log.warning(e.toString)

    case r : InternalTxResult =>
      log.info(r.toString)

    case _ :BlockChainReady =>

      if(nodeIdentity.id == "bob" && !once) {
        once = true
        claim(nodeIdentity.id, nodeIdentity.publicKey) pipeTo self
        bootstrapIdentities
          .map(bootId => claim(bootId.nodeId, bootId.pKey))
          .map(_ pipeTo self)
      }
  }
}
