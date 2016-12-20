package sss.ui.nobu

import org.joda.time.DateTime

/**
  * Created by alan on 12/20/16.
  */
case class ScheduledMessage(fields: Array[String]) {

  def isDue(dateTime: DateTime): Boolean = ???
}
