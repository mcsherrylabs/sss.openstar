package sss.ui.nobu

import akka.actor.ActorRef
import com.vaadin.server.FontAwesome
import com.vaadin.ui.{Layout, Notification}
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.balanceledger._
import sss.asado.contract.SingleIdentityEnc
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger._
import sss.asado.message.MessageEcryption.EncryptedMessage
import sss.asado.message.{Message, MessageInBox, MessagePayloadDecoder, SavedAddressedMessage}
import sss.asado.state.HomeDomain
import sss.asado.wallet.WalletPersistence
import sss.asado.wallet.WalletPersistence.Lodgement
import sss.db.Db
import sss.ui.design.MessageDesign
import sss.ui.nobu.NobuMainLayout.ShowWrite
import sss.ui.nobu.NobuNodeBridge.MessageToArchive
import us.monoid.web.Resty

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */
object MessageComponent extends Logging {
 val dtFmt = DateTimeFormat.forPattern("dd MMM yyyy HH:mm")

  def toDetails(savedMsg: SavedAddressedMessage)
           (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery): MsgDetails = {

    val bount = savedMsg.addrMsg.ledgerItem.txEntryBytes.toSignedTxEntry.txEntryBytes.toTx.outs(1).amount
    val enc = MessagePayloadDecoder.decode(savedMsg.addrMsg.msgPayload).asInstanceOf[EncryptedMessage]

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

    val tx = msg.tx.toSignedTxEntry.txEntryBytes.toTx
    val bount = tx.outs(1).amount

    val enc = MessagePayloadDecoder.decode(msg.msgPayload).asInstanceOf[EncryptedMessage]

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
                       showWrite: ShowWrite,
                       protected val msgDetails: MsgDetails
                       ) extends MessageDesign {

  forwardMsgBtn.addClickListener(_ => {
      showWrite(None, msgDetails.text)
    }
  )

  replyMsgBtn.addClickListener(_ => {
    showWrite(Option(msgDetails.fromTo), msgDetails.text)
  })

  messageText.setValue(msgDetails.text)
  fromLabel.setValue(msgDetails.fromTo)
  deliveredAt.setValue(msgDetails.createdAt.toString(dtFmt))

}



class NewMessageComponent(parentLayout: Layout, showWrite: ShowWrite, inBox: MessageInBox, msg:Message)
                         (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery) extends
  MessageComponent(parentLayout,
    showWrite,
    toDetails(msg)) {

  deleteMsgBtn.addClickListener(_ => {
      inBox.archive(msg.index)
      parentLayout.removeComponent(NewMessageComponent.this)
  })

}

class DeleteMessageComponent(parentLayout: Layout, showWrite: ShowWrite, inBox: MessageInBox, msg:Message)
                            (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery)
  extends MessageComponent(parentLayout, showWrite,
    toDetails(msg)) {

  deleteMsgBtn.setIcon(FontAwesome.TRASH_O)

  deleteMsgBtn.addClickListener(_ => {
    inBox.delete(msg.index)
    parentLayout.removeComponent(DeleteMessageComponent.this)
  })
}

class SentMessageComponent(parentLayout: Layout, showWrite: ShowWrite, inBox: MessageInBox, msg:SavedAddressedMessage)
                          (implicit nodeIdentity: NodeIdentity, identityServiceQuery: IdentityServiceQuery)
  extends MessageComponent(parentLayout, showWrite, toDetails(msg)) {

  deleteMsgBtn.setIcon(FontAwesome.TRASH_O)

  deleteMsgBtn.addClickListener(_ => {
    inBox.deleteSent(msg.index)
    parentLayout.removeComponent(SentMessageComponent.this)
  })
}