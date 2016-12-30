package sss.ui.nobu

import com.vaadin.server.VaadinSession
import org.joda.time.DateTime
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.ui.nobu.NobuNodeBridge.MessageToSend

/**
  * Created by alan on 12/30/16.
  */
object Scheduler {

  val once = "Once"
  val daily = "Daily"
  val weekly = "Weekly"
  val monthly = "Monthly"

  case class ScheduleDetails(fields: Array[String]) {

    def isDue(dateTime: DateTime): Boolean = ???
    val from: String = ""
    val to: String = ""
    val account: PublicKey = Array()
    val text: String = ""
    val amount: Int = 0
  }

  def toDetails(fields: Seq[String]): ScheduleDetails = ???

  def serialiseDetails(from: String, schedule: String, msg: MessageToSend): Seq[String] = {
    Seq()
  }

}
