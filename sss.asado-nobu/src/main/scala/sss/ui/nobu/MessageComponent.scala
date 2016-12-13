package sss.ui.nobu

import akka.actor.ActorRef
import com.vaadin.server.FontAwesome
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui.{Button, Layout}
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger._
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger._
import sss.asado.message.MessageEcryption.EncryptedMessage
import sss.asado.message.{Message, MessageEcryption, MessagePayloadDecoder, SavedAddressedMessage}
import sss.ui.design.MessageDesign
import sss.ui.nobu.NobuNodeBridge.{ClaimBounty, MessageToArchive, MessageToDelete, SentMessageToDelete}
import sss.ui.reactor.UIReactor

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */
object MessageComponent extends Logging {
 val dtFmt = DateTimeFormat.forPattern("dd MMM yyyy HH:mm")

  val msgDecoders = (MessagePayloadDecoder.decode orElse PayloadDecoder.decode)
  def toDetails(savedMsg: SavedAddressedMessage)
           (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery): MsgDetails = {

    val bount = savedMsg.addrMsg.ledgerItem.txEntryBytes.toSignedTxEntry.txEntryBytes.toTx.outs(1).amount

    msgDecoders(savedMsg.addrMsg.msgPayload) match {
      case enc: EncryptedMessage =>
        Try(identityServiceQuery.account(savedMsg.to)) match {
          case Failure(e) => throw e
          case Success(recipient) =>
            val msgText = enc.decrypt(nodeIdentity, recipient.publicKey)
            new MsgDetails {
              override val secret: Array[Byte] = msgText.secret
              override val text: String = msgText.text
              override val fromTo: String = savedMsg.to
              override val bounty: Int = bount
              override val createdAt: LocalDateTime = savedMsg.savedAt
              override val canClaim: Boolean = false
            }
        }
      case IdentityClaimMessagePayload(claim, tag, publicKey) =>
        new MsgDetails {
          override val secret: Array[Byte] = Array()
          override val text: String = claim
          override val fromTo: String = savedMsg.to
          override val bounty: Int = 0
          override val createdAt: LocalDateTime = savedMsg.savedAt
          override val canClaim: Boolean = false
        }
    }
  }


  def toDetails(msg: Message)
           (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery): MsgDetails = {
    //val le = msg.tx.toLedgerItem

    //require(le.ledgerId == MessageKeys.BalanceLedger, s"Message download expecting a balance ledger entry ${MessageKeys.BalanceLedger}, but got ${le.ledgerId}")

    //val sTx = le.txEntryBytes.toSignedTxEntry
    //val tx = sTx.txEntryBytes.toTx
    val tx = msg.tx.toSignedTxEntry.txEntryBytes.toTx
    val bount = tx.outs(1).amount
    msgDecoders(msg.msgPayload) match {

      case enc: EncryptedMessage =>
        Try(identityServiceQuery.account(msg.from)) match {
          case Failure(e) => throw e
          case Success(sender) =>
            val msgText = enc.decrypt(nodeIdentity, sender.publicKey)
            new MsgDetails {
              override val secret: Array[Byte] = msgText.secret
              override val text: String = msgText.text
              override val fromTo: String = msg.from
              override val bounty: Int = bount
              override val createdAt: LocalDateTime = msg.createdAt
              override val canClaim: Boolean = true
            }
        }

      case IdentityClaimMessagePayload(claim, tag, publicKey) =>
        new MsgDetails {
          override val secret: Array[Byte] = Array()
          override val text: String = claim
          override val fromTo: String = msg.from
          override val bounty: Int = 0
          override val createdAt: LocalDateTime = msg.createdAt
          override val canClaim: Boolean = false
        }
    }


  }

}

import sss.ui.nobu.MessageComponent._

trait MsgDetails {
  val text: String
  val bounty: Int
  val fromTo: String
  val createdAt: LocalDateTime
  val canClaim: Boolean
  val secret: Array[Byte]
}

class MessageComponent(parentLayout: Layout,
                       mainActorRef: ActorRef,
                       protected val msgDetails: MsgDetails
                       ) extends MessageDesign {



  forwardMsgBtn.addClickListener(new Button.ClickListener {
    def buttonClick(event: Button.ClickEvent): Unit = {
      mainActorRef ! ShowWrite(to = "", text = msgDetails.text)
    }
  })

  replyMsgBtn.addClickListener(new Button.ClickListener {
    def buttonClick(event: Button.ClickEvent): Unit = {
      mainActorRef ! ShowWrite(to = msgDetails.fromTo, text = msgDetails.text)
    }
  })

  messageText.setValue(msgDetails.text)
  fromLabel.setValue(msgDetails.fromTo)
  deliveredAt.setValue(msgDetails.createdAt.toString(dtFmt))

}

class NewMessageComponent(parentLayout: Layout, mainActorRef: ActorRef, msg:Message)
                         (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery) extends
  MessageComponent(parentLayout,
    mainActorRef,
    toDetails(msg)) {

  if(msgDetails.canClaim) mainActorRef ! ClaimBounty(msg.tx.toSignedTxEntry, msgDetails.secret)

  deleteMsgBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      mainActorRef ! MessageToArchive(msg.index)
      parentLayout.removeComponent(NewMessageComponent.this)
    }
  })

}

class DeleteMessageComponent(parentLayout: Layout,mainActorRef: ActorRef, msg:Message)
                            (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery)
  extends MessageComponent(parentLayout, mainActorRef,
    toDetails(msg)) {

  deleteMsgBtn.setIcon(FontAwesome.TRASH_O)


  deleteMsgBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      mainActorRef ! MessageToDelete(msg.index)
      parentLayout.removeComponent(DeleteMessageComponent.this)
    }
  })
}

class SentMessageComponent(parentLayout: Layout, mainActorRef: ActorRef, msg:SavedAddressedMessage)
                          (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery)
  extends MessageComponent(parentLayout,  mainActorRef, toDetails(msg)) {

  deleteMsgBtn.setIcon(FontAwesome.TRASH_O)

  deleteMsgBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      mainActorRef ! SentMessageToDelete(msg.index)
      parentLayout.removeComponent(SentMessageComponent.this)
    }
  })
}