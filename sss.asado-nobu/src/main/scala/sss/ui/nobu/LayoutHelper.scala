package sss.ui.nobu

import com.vaadin.navigator.Navigator
import com.vaadin.server.VaadinSession
import com.vaadin.ui.{AbstractLayout, Notification, UI}
import sss.ancillary.Logging

import scala.util.Try

object PushHelper extends PushHelper

trait PushHelper extends Logging {

  def push(f: => Unit)(implicit ui: UI): Unit = {
    Try(
      ui.access(() =>
        Try(
          f
        ).recover {
          case e =>
            log.error(e.toString)
        })
    ). recover {
      case e =>
        log.error(e.toString)
    }
  }

}

trait LayoutHelper {

  self: AbstractLayout =>

  protected implicit val ui: UI

  implicit lazy val sessId: String = {
    val s = ui.getSession.getSession.getId
    require(Option(s).isDefined, "Session id is null")
    s
  }

  protected lazy val navigator: Navigator = ui.getNavigator()

  def push(f: => Unit): Unit = PushHelper.push(f)

}

trait Notifications {
  def show(msg: String): Unit = Notification.show(msg, Notification.Type.HUMANIZED_MESSAGE)
  def showWarn(msg: String): Unit = Notification.show(msg, Notification.Type.WARNING_MESSAGE)
}