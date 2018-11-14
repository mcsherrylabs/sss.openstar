package sss.ui.nobu

import java.io.File

import com.vaadin.ui.ComboBox

/**
  * Created by alan on 11/15/16.
  */
class UserDirectory(keyFolder: String) {

  private def getKeyNames(keyFolder: String): Array[String] = {
    val folder = new File(keyFolder)
    folder.listFiles().filter(_.isFile).map(_.getName)
  }

  def listUsers: Seq[String] = getKeyNames(keyFolder).map(_.split("\\.").head)

  def loadCombo(combo:ComboBox): Unit = {
    val users = listUsers
    combo.removeAllItems()
    if(!users.isEmpty) {
      combo.addItems(users: _*)
      combo.select(combo.getItemIds.iterator().next)
    }
  }
}
