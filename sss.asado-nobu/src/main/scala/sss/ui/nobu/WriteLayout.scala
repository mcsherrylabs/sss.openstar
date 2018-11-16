package sss.ui.nobu


import akka.actor.ActorRef
import com.vaadin.server.VaadinSession
import com.vaadin.ui.Button.ClickEvent
import com.vaadin.ui.{Button, Notification}
import sss.ancillary.Logging
import sss.asado.identityledger.IdentityServiceQuery
import sss.ui.design.WriteDesign
import sss.ui.nobu.NobuNodeBridge.{MessageToSend, ShowInBox}


import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/15/16.
  */


class WriteLayout(mainNobuRef: ActorRef, from: String, to: String, text: String, userDir: UserDirectory)
                 (implicit identityQuery: IdentityServiceQuery,
                  blockingWorkers: BlockingWorkers) extends WriteDesign with Logging {


  import NobuUI.CRLF

  toCombo.setEmptySelectionAllowed(false)
  userDir.loadCombo(toCombo)

  toCombo.setValue(to)

  if (text.length > 0) messageText.setValue(CRLF + CRLF + text)


  sendButton.addClickListener( event =>
      Option(amountField.getValue) match {
        case None => Notification.show("'Amount' cannot be empty", Notification.Type.WARNING_MESSAGE)
        case Some(amountStr) => Try(Integer.parseInt(amountStr)) match {
          case Failure(e) =>
            Notification.show("'Amount' must be a number > than 0", Notification.Type.WARNING_MESSAGE)
          case Success(amount) if amount < 1 =>
            Notification.show("'Amount' must be 1 or more", Notification.Type.WARNING_MESSAGE)
          case Success(amount) =>

            Option(toCombo.getValue.toString) match {
              case None => Notification.show("'To' cannot be empty", Notification.Type.WARNING_MESSAGE)
              case Some(to) if to.length == 0 =>
                Notification.show("'To' cannot be empty", Notification.Type.WARNING_MESSAGE)
              case Some(to) =>
                identityQuery.accountOpt(to) match {
                  case None =>
                    Notification.show(s"No account exists for $to")
                  case Some(ac) =>
                    Option(messageText.getValue) match {
                      case None => Notification.show("Cannot send an empty message", Notification.Type.WARNING_MESSAGE)
                      case Some(text) if text.length == 0 =>
                        Notification.show("Cannot send an empty message", Notification.Type.WARNING_MESSAGE)
                      case Some(text) =>
                        sendButton.setEnabled(false)
                        mainNobuRef ! ShowInBox
                        blockingWorkers.submit(MessageToSend(from, to, ac, text, amount, mainNobuRef))

                    }
                }
            }
        }
      }

  )
}
