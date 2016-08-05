package sss.asado

import scala.util.{Failure, Success, Try}
import sss.ancillary.Logging

import scala.reflect.ClassTag
/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */
package object contract {

  trait Contract
  trait Decumbrance extends Contract

  trait Encumbrance extends Contract {
    def decumber(params: Seq[Array[Byte]], context: LedgerContext, decumberence: Decumbrance): Boolean
  }

  case object NullDecumbrance extends Decumbrance
  case object NullEncumbrance extends Encumbrance {

    override def toString: String = "NullEncumbrance!"

    override def decumber(params: Seq[Array[Byte]], context: LedgerContext, decumberence: Decumbrance): Boolean = true
  }

  import scala.language.dynamics

  object LedgerContext {
    val blockHeightKey = "blockHeight"
    val identityServiceKey = "identityService"
    def apply(wrapped: Map[String, Any]) = new LedgerContext(wrapped)
  }

  class LedgerContext(wrapped: Map[String, Any]) extends Logging with Dynamic {

    def selectDynamic[T : ClassTag](name: String) = apply[T](name)

    def apply[T: ClassTag](name: String): Option[T]= {
      wrapped.get(name) map { found =>
        Try[T](found.asInstanceOf[T]) match {
          case Failure(e) =>
            log.error(s"Ledger context failed to cast $name", e)
            throw new Error("Serious ledger configuration error")
          case Success(t) => t
        }
      }
    }
  }
}
