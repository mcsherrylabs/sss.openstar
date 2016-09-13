package sss.ui.nobu


import com.vaadin.ui.Button.ClickEvent
import com.vaadin.ui.{Button, Notification}
import sss.ancillary.Logging
import sss.asado.identityledger.IdentityServiceQuery
import sss.ui.design.WriteDesign
import sss.ui.nobu.NobuNodeBridge.{MessageToSend, ShowInBox}
import sss.ui.reactor.UIReactor

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/15/16.
  */


class WriteLayout(uiReactor: UIReactor, to: String, text: String)
                 (implicit identityQuery: IdentityServiceQuery) extends WriteDesign with Logging {

  import NobuUI.CRLF

  toField.setValue(to)

  if(text.length > 0) messageText.setValue(CRLF + CRLF + text)


  sendButton.addClickListener(new Button.ClickListener{
    override def buttonClick(event: ClickEvent): Unit = {
      Option(toField.getValue) match {
        case None => Notification.show("'To' cannot be empty", Notification.Type.WARNING_MESSAGE)
        case Some(to) if to.length == 0 =>
          Notification.show("'To' cannot be empty", Notification.Type.WARNING_MESSAGE)
        case Some(to) =>
          Try(identityQuery.account(to)) match {
            case Failure(e) =>
              log.debug(s"Failed to lookup id $to", e)
              Notification.show(s"No account exists for $to")
            case Success(ac) =>
              Option(messageText.getValue) match {
                case None => Notification.show("Cannot send an empty message", Notification.Type.WARNING_MESSAGE)
                case Some(text) if text.length == 0 =>
                  Notification.show("Cannot send an empty message", Notification.Type.WARNING_MESSAGE)
                case Some(text) =>
                  uiReactor.broadcast(MessageToSend(to, ac, text))
                  uiReactor.broadcast(ShowInBox)
              }
          }
      }
    }
  })
}
