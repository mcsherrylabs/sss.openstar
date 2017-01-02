package sss.ui.nobu

import java.io.File

import sss.ancillary.{Configure, Memento}

/**
  * Created by alan on 12/30/16.
  */
object SchedulerPersistence extends Configure {

  def apply(): SchedulerPersistence = new SchedulerPersistence(new File(config.getString("scheduler.folder")))
}

class SchedulerPersistence(storageFolder: File) {

  private val delim = "=&="

  def delete(details: Seq[String]) = {
    Memento(details.take(3).mkString("."), Option("schedules")).clear
  }

  def persist(details: Seq[String]) = {
    Memento(details.take(3).mkString("."), Option("schedules")).write(details.mkString(delim))
  }

  def retrieve: Seq[Seq[String]] = {
    val resOpt = Option(storageFolder.listFiles()).map(_.filter(_.isFile)).map { filesInFolder =>
      filesInFolder.map(_.getName).map { scheduleFile : String =>
        Memento(scheduleFile, Option("schedules")).read.get.split(delim).toSeq
      }
    }
    resOpt match {
      case None => Seq()
      case Some(x) => x
    }
  }
}

