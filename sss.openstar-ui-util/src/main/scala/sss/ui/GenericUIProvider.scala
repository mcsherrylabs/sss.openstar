package sss.ui

import com.vaadin.server.{UIClassSelectionEvent, UICreateEvent, UIProvider}
import com.vaadin.ui.UI

object GenericUIProvider {

  def apply(cls: Class[_ <: UI], createUI: UICreateEvent => UI): UIProvider = {
    new UIProvider {

      override def getUIClass(event: UIClassSelectionEvent): Class[_ <: UI] = cls

      override def createInstance(event: UICreateEvent): UI = {
        createUI(event)
      }
    }
  }
}

