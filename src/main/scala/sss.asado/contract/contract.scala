

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */
package object contract {

  trait Contract
  trait Decumbrance extends Contract

  trait Encumbrance extends Contract {
    def decumber(params: Seq[Array[Byte]], decumberence: Decumbrance): Boolean
  }

  case object NullDecumbrance extends Decumbrance
  case object NullEncumbrance extends Encumbrance {
    override def decumber(params: Seq[Array[Byte]], decumberence: Decumbrance): Boolean = decumberence match {
      case NullDecumbrance => true
      case _ => false
    }
  }

}
