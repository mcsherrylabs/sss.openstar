package sss.ui.nobu


import akka.actor.ActorRef
import com.vaadin.server.VaadinSession
import com.vaadin.ui.Button.ClickEvent
import com.vaadin.ui.{Button, Notification}
import sss.ancillary.Logging
import sss.asado.Identity
import sss.asado.identityledger.IdentityServiceQuery
import sss.ui.design.WriteDesign
import sss.ui.nobu.NobuNodeBridge.{MessageToSend, ShowInBox}
import sss.ui.reactor.UIReactor

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/15/16.
  */


class WriteLayout(mainNobuRef: ActorRef, to: String, text: String, userDir: UserDirectory)
                 (implicit identityQuery: IdentityServiceQuery) extends WriteDesign with Logging {


  import NobuUI.CRLF

  scheduleCombo.setNullSelectionAllowed(false)
  scheduleCombo.setValue(Scheduler.once)
  toCombo.setNullSelectionAllowed(false)
  userDir.loadCombo(toCombo)

  toCombo.setValue(to)

  if (text.length > 0) messageText.setValue(CRLF + CRLF + text)


  sendButton.addClickListener(new Button.ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      Option(amountField.getValue) match {
        case None => Notification.show("'Amount' cannot be empty", Notification.Type.WARNING_MESSAGE)
        case Some(amountStr) => Try(Integer.parseInt(amountStr)) match {
          case Failure(e) => Notification.show("'Amount' must be a number", Notification.Type.WARNING_MESSAGE)
          case Success(amount) =>

            Option(toCombo.getValue.toString) match {
              case None => Notification.show("'To' cannot be empty", Notification.Type.WARNING_MESSAGE)
              case Some(to) if to.length == 0 =>
                Notification.show("'To' cannot be empty", Notification.Type.WARNING_MESSAGE)
              case Some(to) =>
                Try(identityQuery.account(Identity(to))) match {
                  case Failure(e) =>
                    log.debug(s"Failed to lookup id $to", e)
                    Notification.show(s"No account exists for $to")
                  case Success(ac) =>
                    Option(messageText.getValue) match {
                      case None => Notification.show("Cannot send an empty message", Notification.Type.WARNING_MESSAGE)
                      case Some(text) if text.length == 0 =>
                        Notification.show("Cannot send an empty message", Notification.Type.WARNING_MESSAGE)
                      case Some(text) =>
                        sendButton.setEnabled(false)
                        mainNobuRef ! ShowInBox
                        val mts = MessageToSend(Identity(to), ac, text, amount)
                        mainNobuRef ! mts
                        Option(scheduleCombo.getValue.toString) match {
                          case None | Some(Scheduler.once) =>
                          case Some(schedule) =>
                            val from = getSession().getAttribute(UnlockClaimView.identityAttr).toString
                            val serialised = Scheduler.serialiseDetails(from, schedule, mts)
                            SchedulerPersistence().persist(serialised)


                        }
                    }
                }
            }
        }
      }
    }
  })
}
