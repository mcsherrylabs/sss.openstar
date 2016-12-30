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

  def persist(details: Seq[String]) = {
    Memento("", Option("scheduled"))
  }
  def retrieve: Seq[Seq[String]] = {
    (storageFolder.listFiles().filter(_.isFile)).map(_.getName).map { scheduled : String =>
      Memento(scheduled).read.get.split(",").toSeq
    }
  }
}

