package sss.ui.nobu

import akka.actor.ActorRef
import com.vaadin.server.FontAwesome
import com.vaadin.ui.{Button, Component, Layout}
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger._
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger._
import sss.asado.message.{Message, MessageEcryption, SavedAddressedMessage}
import sss.ui.design.MessageDesign
import sss.ui.nobu.NobuNodeBridge.{ClaimBounty, MessageToArchive, MessageToDelete, SentMessageToDelete}
import sss.ui.reactor.UIReactor

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */
object MessageComponent {
 val dtFmt = DateTimeFormat.forPattern("dd MMM yyyy HH:mm")

  def toDetails(savedMsg: SavedAddressedMessage)
           (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery): MsgDetails = {

    val bount = savedMsg.addrMsg.ledgerItem.txEntryBytes.toSignedTxEntry.txEntryBytes.toTx.outs(1).amount

    val enc = MessageEcryption.encryptedMessage(savedMsg.addrMsg.msg)
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

  }

  def toDetails(msg: Message)
           (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery): MsgDetails = {
    val le = msg.tx.toLedgerItem

    require(le.ledgerId == MessageKeys.BalanceLedger, s"Message download expecting a balance ledger entry ${MessageKeys.BalanceLedger}, but got ${le.ledgerId}")

    val sTx = le.txEntryBytes.toSignedTxEntry
    val tx = sTx.txEntryBytes.toTx
    val bount = tx.outs(1).amount
    val enc = MessageEcryption.encryptedMessage(msg.msg)
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
  }

}

import MessageComponent._

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
                       uiReactor: UIReactor,
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

class NewMessageComponent(parentLayout: Layout, mainActorRef: ActorRef, uiReactor: UIReactor, msg:Message)
                         (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery) extends
  MessageComponent(parentLayout,
    mainActorRef,
    uiReactor,
    toDetails(msg)) {

  if(msgDetails.canClaim) uiReactor.broadcast(ClaimBounty(msg.tx.toLedgerItem, msgDetails.secret))

  deleteMsgBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      uiReactor.broadcast(MessageToArchive(msg.index))
      parentLayout.removeComponent(NewMessageComponent.this)
    }
  })

}

class DeleteMessageComponent(parentLayout: Layout,mainActorRef: ActorRef,  uiReactor: UIReactor,msg:Message)
                            (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery)
  extends MessageComponent(parentLayout, mainActorRef, uiReactor,
    toDetails(msg)) {

  deleteMsgBtn.setIcon(FontAwesome.TRASH_O)


  deleteMsgBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      uiReactor.broadcast(MessageToDelete(msg.index))
      parentLayout.removeComponent(DeleteMessageComponent.this)
    }
  })
}

class SentMessageComponent(parentLayout: Layout, mainActorRef: ActorRef, uiReactor: UIReactor,msg:SavedAddressedMessage)
                          (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery)
  extends MessageComponent(parentLayout,  mainActorRef, uiReactor, toDetails(msg)) {

  deleteMsgBtn.setIcon(FontAwesome.TRASH_O)

  deleteMsgBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      uiReactor.broadcast(SentMessageToDelete(msg.index))
      parentLayout.removeComponent(SentMessageComponent.this)
    }
  })
}