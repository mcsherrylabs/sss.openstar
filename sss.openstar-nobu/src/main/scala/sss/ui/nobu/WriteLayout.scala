package sss.ui.nobu


import com.vaadin.ui.UI
import sss.ancillary.Logging
import sss.openstar.account.PublicKeyAccount
import sss.openstar.identityledger.IdentityServiceQuery
import sss.openstar.network.MessageEventBus
import sss.ui.design.WriteDesign
import sss.ui.nobu.BlockingWorkers.BlockingTask
import sss.ui.nobu.SendMessage.MessageToSend

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/15/16.
  */


class WriteLayout(showInBox: => Unit, from: String, to: Option[String], text: String, userDir: UserDirectory)
                 (implicit identityQuery: IdentityServiceQuery,
                  val ui: UI,
                  messageEventBus: MessageEventBus)
  extends WriteDesign
    with LayoutHelper
    with Logging
    with Notifications
{

  import NobuUI.CRLF

  scheduleCombo setVisible false

  toCombo.setItems(identityQuery.list().asJava)
  toCombo.setEmptySelectionAllowed(true)

  to map (toCombo.setValue(_))


  if (text.length > 0) messageText.setValue(CRLF + text)

  sendButton.addClickListener( _ =>

      Option(amountField.getValue) match {
        case None => showWarn("'Amount' cannot be empty")
        case Some(amountStr) =>
          parseAmount(amountStr) foreach { amount =>
              Option(toCombo.getValue) match {
                case None => showWarn("'To' cannot be empty")
                case Some(to) if to.length == 0 =>
                  showWarn("'To' cannot be empty")
                case Some(to) =>
                  identityQuery.accountOpt(to) match {
                    case None =>
                      show(s"No account exists for $to")
                    case Some(ac) =>
                      sendMessage(amount, to, ac)
                  }
              }
        }
      }
  )

  private def parseAmount(amountStr: String): Option[Int] = {
    Try(Integer.parseInt(amountStr)) match {
      case Failure(e) =>
        showWarn("'Amount' must be a number > than 0")
        None
      case Success(amount) if amount < 1 =>
        showWarn("'Amount' must be 1 or more")
        None
      case Success(amount) =>
        Some(amount)
    }
  }

  private def sendMessage(amount: Int, to: String, ac: PublicKeyAccount): Unit = {
    Option(messageText.getValue) match {
      case None => showWarn("Cannot send an empty message")
      case Some(text) if text.trim.length == 0 =>
        showWarn("Cannot send an empty message")
      case Some(text) =>
        sendButton setEnabled false
        messageEventBus publish BlockingTask(MessageToSend(from, to, ac, text, amount))
        showInBox
    }
  }
}
