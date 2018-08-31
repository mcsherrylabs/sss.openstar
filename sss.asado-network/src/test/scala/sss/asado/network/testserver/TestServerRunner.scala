package sss.asado.network.testserver

import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask
import sss.asado.network._
import sss.asado.network.testserver.TestServer.{End, TestRun}

import scala.language.postfixOps
import NetworkTests._
import akka.actor.{ActorSystem, PoisonPill}
import sss.ancillary.LogFactory.log

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Success, Try}

object TestServerRunner {

  implicit val timeout = Timeout(1 minute)
  val scenarioTimeout: Duration = 1 minute

  def main(args: Array[String]): Unit = {
    import TestActorSystem.actorSystem
    test(actorSystem)
    actorSystem.terminate()
    log.info("All stop ok.")
  }

  def test(implicit actorSystem: ActorSystem) {
    import sss.ancillary.LogFactory.log

    log.info("Init the logger")


    //actorSystem.eventStream.setLogLevel(Logging.DebugLevel)

    val server1 = new TestServer("local1", 8888)
    val server2 = new TestServer("local2", 9999)

    implicit val servers = (server1, server2)

    report(
      runListOfPairs(NetworkTests.allPairsOfTests)
    )

    Await.ready(server1.startFuture.map (_.stop()), scenarioTimeout)
    log.info("Server1 down...")
    Await.ready(server2.startFuture.map (_.stop()), scenarioTimeout)
    log.info("Server2 down...")

    def runListOfPairs(all: List[(TestRun, TestRun)])(
        implicit servers: (TestServer, TestServer))
      : List[Try[(TestRun, TestRun)]] = {
      all map {
        case (s1, s2) =>
          val start1 = servers._1.startActor ? s1
          val start2 = servers._2.startActor ? s2
          Try(Await.result(for {
                         r1 <- start1
                         r2 <- start2
                       } yield
                         (r1.asInstanceOf[TestRun], r2.asInstanceOf[TestRun]),
            scenarioTimeout))
      }
    }

    def report(results: List[Try[(TestRun, TestRun)]])(
        implicit servers: (TestServer, TestServer)) = {

      val all = for {
        resultTry <- results
      } yield {
        log.info("*****************************************************************")
        resultTry match {
          case Success(result) =>
            result match {
              case r@(End(msg1), End(msg2)) =>
                log.info(s"Success server1: $msg1")
                log.info(s"Success server2: $msg2")
              case (End(x), y) =>
                log.info(s"Success server1: $x")
                log.info(s"ERROR   server2! $y")
              case (x, End(y)) =>
                log.info(s"ERROR   server1: $x")
                log.info(s"Success server2: $y")
              case (x, y) =>
                log.info(s"ERROR   server1: $x")
                log.info(s"ERROR   server2: $y")
            }
          case Failure(e: TimeoutException) =>
            log.info(s"A scenario didn't finish within $scenarioTimeout")
          case Failure (e) =>
            log.info(s"Bad -> $e")
        }
      }
      log.info("*****************************************************************")
    }

  }

}
