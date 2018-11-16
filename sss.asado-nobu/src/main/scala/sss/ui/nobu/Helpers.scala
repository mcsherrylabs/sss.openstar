package sss.ui.nobu

import com.vaadin.navigator.Navigator
import com.vaadin.ui.{AbstractLayout, UI}
import sss.ancillary.Logging

import scala.util.Try

object PushHelper extends Logging {

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
trait Helpers {

  self: AbstractLayout =>

  implicit lazy val ui: UI = getUI()
  protected lazy val navigator: Navigator = ui.getNavigator()

  def push(f: => Unit): Unit = PushHelper.push(f)

}
