package sss.ui.nobu

import com.vaadin.navigator.{View, ViewChangeListener}
import com.vaadin.ui._
import sss.asado.{AsadoEvent, Status}
import sss.asado.network.{ConnectionLost, MessageEventBus}



import scala.collection.JavaConverters._

object WaitKeyGenerationView {

  case class Update(uiId: Option[String], msg: String) extends AsadoEvent

  val name = "waitKeyGenerationView"
}

class WaitKeyGenerationView(implicit messageEventBus: MessageEventBus
                    ) extends VerticalLayout with View {

  val btn = new Button("Generating new key, this may take some time ....")
  btn.setEnabled(false)
  val bar = new ProgressBar()
  bar.setIndeterminate(true)

  setSizeFull()
  setDefaultComponentAlignment(Alignment.MIDDLE_CENTER)
  addComponents(btn, bar)


  override def enter(event: ViewChangeListener.ViewChangeEvent): Unit = {}

}
