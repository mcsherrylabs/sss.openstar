package sss

package object asado {

  case class Identity(val value: String) extends AnyVal

  case class IdentityTag(val value: String) extends AnyVal

  trait AsadoEvent

}
