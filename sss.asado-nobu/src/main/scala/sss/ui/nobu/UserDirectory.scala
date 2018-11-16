package sss.ui.nobu

import java.io.File

import com.vaadin.data.provider.ListDataProvider
import com.vaadin.ui.ComboBox
import scala.collection.JavaConverters._

/**
  * Created by alan on 11/15/16.
  */
class UserDirectory(keyFolder: String) {

  private def getKeyNames(keyFolder: String): Array[String] = {
    val folder = new File(keyFolder)
    folder.listFiles().filter(_.isFile).map(_.getName)
  }

  def listUsers: Seq[String] = getKeyNames(keyFolder).map(_.split("\\.").head)

  def loadCombo(combo:ComboBox[String]): Unit = {
    val users = listUsers
    combo.setDataProvider(new ListDataProvider[String](users.asJava))

  }
}
