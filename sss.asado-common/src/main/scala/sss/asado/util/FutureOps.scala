package sss.asado.util

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object FutureOps {

  implicit class AwaitResult[T](val f: Future[T]) extends AnyVal {
    def await(d: Duration = 10 seconds): T = {
      Await.result(f, d)
    }
  }
}
