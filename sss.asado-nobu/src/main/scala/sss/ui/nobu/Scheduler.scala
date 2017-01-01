package sss.ui.nobu


import org.joda.time.DateTime
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.ui.nobu.NobuNodeBridge.MessageToSend
import sss.asado.util.ByteArrayEncodedStrOps._

/**
  * Created by alan on 12/30/16.
  */
object Scheduler {

  val once = "Once"
  val daily = "Daily"
  val weekly = "Weekly"
  val monthly = "Monthly"

  trait ScheduleDetails {
    def isDue(dateTime: DateTime): Boolean
    val from: String
    val to: String
    val account: PublicKey
    val text: String
    val amount: Int

  }

  def toDetails(fields: Seq[String]): ScheduleDetails = {
    val ffrom = fields(0)
    val schedule = fields(1)
    val msg_to = fields(2)
    val msg_amount = fields(3)
    val msg_account_publicKey = fields(4)
    val msg_text = fields(5)

    new ScheduleDetails {
      override val text: String = msg_text
      override val account: PublicKey = msg_account_publicKey.toByteArray
      override val from: String = ffrom
      override val amount: Int = msg_amount.toInt
      override val to: String = msg_to

      override def isDue(dateTime: DateTime): Boolean = {
        schedule match {
          case `daily` => true
          case `weekly` => dateTime.getDayOfWeek == 1
          case `monthly` => dateTime.getDayOfMonth == 1
        }
      }
    }

  }

  def serialiseDetails(from: String, schedule: String, msg: MessageToSend): Seq[String] = {
    Seq(from, schedule, msg.to, msg.amount.toString, msg.account.publicKey.toBase64Str, msg.text)
  }

}
