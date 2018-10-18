import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem, Props}
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths

import scala.reflect.ClassTag


case class Min[T](r: T, n: String = "", next: Option[Min[_]] = None) {
   def findEnd(): Min[_] = next.flatMap(_.next).getOrElse(this)
}


class Example() extends Actor {
  override def receive: Receive = {

    case Min(s: String, _, _) =>
      println(s + " string")

    case Min(i: Int, _, _) =>
      println((i+i) + " int")

    case Min(Min(i: Int,_, _), _, _) =>
      println((i+i) + " int embedded")
  }
}

object Main {

  def createMin(): Min[_] = Min("asdasd")


  def main(args: Array[String]): Unit = {
    println("Hello world")
//
//    val maxWaitInterval: Long = 30 * 1000
//
//    lazy val stream: Stream[Long] = {
//      (10l) #:: (10l) #:: stream.zip(stream.tail).map { n =>
//        val fib = n._1 + n._2
//        if (fib > maxWaitInterval) maxWaitInterval
//        else fib
//      }
//    }
//
//    stream.take(100).foreach(println)
//    val actorSystem = ActorSystem()
//
//    val ref = actorSystem.actorOf(Props(classOf[Example]))
//    ref ! createMin()
//    ref ! Min(4)
//    ref ! Min(Min(3))
//
//    val m = Min(1, "", Option(Min(2, "", Option(Min(3, "", Option(Min(4, "", None)))))))
//
//    println(m)
//    println(m.findEnd())
//
//
//    val source: Source[Int, NotUsed] = Source(1 to 100)
//

  }
}