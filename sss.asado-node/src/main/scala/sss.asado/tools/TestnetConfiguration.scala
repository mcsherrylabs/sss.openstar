package sss.asado.tools

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.asado.chains.TxWriterActor.InternalTxResult
import sss.asado.identityledger.Claim
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.network.MessageEventBus
import sss.asado.nodebuilder.BootstrapIdentity
import sss.asado.tools.SendTxSupport.SendTx
import akka.pattern.pipe

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


  override def receive: Receive = {

    case Failure(e) =>
      log.warning(e.toString)

    case r : InternalTxResult =>
      log.info(r.toString)

    case _ :BlockChainReady =>

      if(nodeIdentity.id == "bob") {
        claim(nodeIdentity.id, nodeIdentity.publicKey) pipeTo self
        bootstrapIdentities
          .map(bootId => claim(bootId.nodeId, bootId.pKey))
          .map(_ pipeTo self)
      }
  }
}
