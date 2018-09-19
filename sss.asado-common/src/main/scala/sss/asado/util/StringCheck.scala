package sss.asado.util

import scala.language.implicitConversions

object StringCheck {

  private val simpleTagChars = (('a' to 'z') ++ ('0' to '9')).toSet

  implicit def toSimpleTag(s: Any): SimpleTag = SimpleTag(s.toString)

  case class SimpleTag(s: String) {
    require(isSimpleTag(s), s"simple tag can only have lower case letters and numbers -> $s")

    override def toString: String = s
  }

  def isSimpleTag(s: String): Boolean = {
    s forall (simpleTagChars contains _)
  }

}
