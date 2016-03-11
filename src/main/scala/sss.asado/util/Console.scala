package sss.asado.util

import java.text.SimpleDateFormat

import org.joda.time.LocalDate

import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
trait Console {

  val dfStr = "dd/MM/yyyy"
  val df = new SimpleDateFormat(dfStr)

  def readLocalDate(prompt: String): LocalDate = {
    Try(new LocalDate(df.parse(StdIn.readLine()))) match {
      case Failure(e) =>
        print(s"$prompt: ($dfStr) "); readLocalDate(prompt)
      case Success(d) => d
    }
  }

  import scala.reflect.runtime.universe._

  def read[T: TypeTag](prompt: String = ""): T = {

    print(s"$prompt> ")

    typeOf[T] match {
      case x if x == typeOf[String] => StdIn.readLine().asInstanceOf[T]
      case x if x == typeOf[Boolean] => StdIn.readBoolean().asInstanceOf[T]
      case x if x == typeOf[Int] => StdIn.readInt().asInstanceOf[T]
      case x if x == typeOf[Double] => StdIn.readDouble().asInstanceOf[T]
      case x if x == typeOf[BigDecimal] => BigDecimal(StdIn.readDouble()).asInstanceOf[T]
      case x if x == typeOf[LocalDate] => readLocalDate(prompt).asInstanceOf[T]
    }

  }
}

