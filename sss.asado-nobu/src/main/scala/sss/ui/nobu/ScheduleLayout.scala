package sss.ui.nobu

import akka.actor.ActorRef
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.ancillary.Logging
import sss.asado.identityledger.IdentityServiceQuery
import sss.ui.nobu.Scheduler.ScheduleDetails


/**
  * Created by alan on 6/15/16.
  */


class ScheduleLayout(mainNobuRef: ActorRef, userDir: UserDirectory, loggedInUser: String)
                    extends VerticalLayout with Logging {



  val schedulerPersistence = SchedulerPersistence()

  paint()

  def paint() {
    removeAllComponents()
    val allSchedulesForUser = schedulerPersistence.retrieve.map(Scheduler.toDetails).filter(_.from == loggedInUser)

    def createDeleteBtn(schedule: ScheduleDetails): Button = {
      val btn = new Button("Delete")
      btn.addClickListener(new ClickListener {
        override def buttonClick(event: ClickEvent) = {
          schedulerPersistence.delete(Scheduler.serialiseDetails(schedule))
          paint()
        }
      })
      btn
    }

    val allComponents = allSchedulesForUser.map { schedule =>
      val layout = new HorizontalLayout
      layout.setWidth("80%")
      layout.setMargin(true)
      layout.setSpacing(true)
      val all = Seq(new Label(schedule.from), new Label(schedule.amount.toString), new Label(schedule.to), new Label(schedule.schedule))
      layout.addComponents(all: _*)
      layout.addComponent(createDeleteBtn(schedule))
      layout
    }

    if (allComponents.isEmpty) addComponent(new Label(s"No schedules exist for user $loggedInUser"))
    else addComponents(allComponents: _*)
  }

}
