package sss.openstar.network.testserver

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.ask
import akka.util.Timeout
import sss.openstar.OpenstarEvent
import sss.openstar.network.NetworkInterface.BindControllerSettings
import sss.openstar.network.{MessageEventBus, _}
import sss.openstar.network.testserver.TestServer._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

object TestServer {

  trait TestRun
  trait TestRunEnd extends TestRun
  case object Stop extends TestRunEnd
  case class End(msg: String) extends TestRunEnd
  case object NoOp extends TestRun
  case class DelayExceeded(howLong: Duration) extends TestRunEnd {
    override def toString: String = s"Failed to receive event in $howLong ... failing."
  }

  case object Drained extends OpenstarEvent

  case class UnExpectedMessage(x: Any) extends TestRunEnd

  case class TimerTriggered(howLong: Duration)
  case class Run(f: (TestServer, NetworkRef) => TestRun) extends TestRun
  case class WaitForNothing(duration: FiniteDuration, next: TestRun)
      extends TestRun
  case class WaitFor[T](t: T => TestRun, duration: FiniteDuration = 5 second)
      extends TestRun

  case class Delay[T <: TestRun](sender: ActorRef, t: T)

}

class TestServer(id: String, newPort: Int)(
    implicit val actorSystem: ActorSystem) {

  import TestServerRunner.scenarioTimeout
  import TestServerRunner.timeout

  val testServer = this
  import akka.pattern.pipe

  val msgBus = new MessageEventBus(TestActorSystem.decoder, Seq.empty)
  val settings = new BindControllerSettings {
    override val applicationName: String = "TestServer"
    override val declaredAddressOpt: Option[String] = None
    override val bindAddress: String = "127.0.0.1"
    override val appVersion: String = "9.9.9"
    override val port: Int = newPort
  }

  private var cancellable: Option[Cancellable] = None

  val networkInterface =
    new NetworkInterface(settings, None)

  val nodeId =
    NodeId(id, new InetSocketAddress("127.0.0.1", settings.port))

  def handshakeGenerator = SimpleTestHandshake(networkInterface, nodeId.id, Random.nextLong()) _


  val netController =
    new NetworkController(handshakeGenerator, networkInterface, msgBus)

  lazy val startActor = startActorPair._1
  lazy val startFuture = startActorPair._2

  private lazy val startActorPair = {
    val ref = actorSystem.actorOf(Props(InternalActor))
    val future = netController.start()
    val nr = Await.result(future, scenarioTimeout)
    (ref ? nr) map (println)
    (ref, future)
  }

  object InternalActor extends Actor {

    msgBus.subscribe(Drained.getClass.asInstanceOf[Class[Drained.type]])

    override def receive: Receive = {
      case Stop =>
        context stop self

      case tr: TestRun =>
        context.system.scheduler
          .scheduleOnce(.5 second, self, Delay(sender(), tr))

      case ref: NetworkRef =>
        //println("Started TestServer")
        context become ready(ref)
        val who = sender()
        who ! "Started TestServer"
    }

    def waitForNothing(asker: ActorRef,
                       netRef: NetworkRef,
                       runner: TestRun): Receive = {

      case TimerTriggered(_) =>
        process(asker, netRef, runner)

      case x =>
        cancellable map (_.cancel())
        process(asker, netRef, UnExpectedMessage(x))

    }

    def process(asker: ActorRef, netRef: NetworkRef, runner: TestRun): Unit = {
      runner match {
        case Run(f) =>
          process(asker, netRef, f(testServer, netRef))

        case WaitForNothing(d, r) =>
          context become waitForNothing(asker, netRef, r)
          cancellable = Option(
            context.system.scheduler.scheduleOnce(d, self, TimerTriggered(d))
          )

        case e: UnExpectedMessage =>
          context become ready(netRef)
          context become drain(netRef, asker, e)
          msgBus.publish(Drained)

        case WaitFor(t, d) =>
          cancellable = Option(
            context.system.scheduler.scheduleOnce(d, self, TimerTriggered(d))
          )
          context.become(running(asker, netRef, t))

        case e: End =>
          cancellable map (_.cancel())
          context become drain(netRef, asker, e)
          msgBus.publish(Drained)


        case Stop =>
          netRef.stop() map { _ =>
            context stop self
            context become drain(netRef, asker, Stop)
            msgBus.publish(Drained)
          }

        case r @ DelayExceeded(d) =>
          context become drain(netRef, asker, r)
          msgBus.publish(Drained)

        case NoOp =>
      }
    }

    def drain(netRef: NetworkRef, asker: ActorRef, e: TestRunEnd): Receive = {

      case Drained =>
        context become ready(netRef)
        asker ! e

      case x =>
        println(s"Draining $x")

    }

    def ready(netRef: NetworkRef): Receive = {

      case r: TestRun =>
        process(sender(), netRef, r)

      case Delay(s, r) =>
        process(s, netRef, r)
    }

    def running[T](asker: ActorRef,
                   netRef: NetworkRef,
                   t: T => TestRun): Receive = ready(netRef) orElse {

      case TimerTriggered(d) =>
        process(asker, netRef, DelayExceeded(d))

      case p: T =>
        cancellable map (_.cancel())
        Try(t(p)) match {
          case Success(r) => process(asker, netRef, r)
          case Failure(f) => process(asker, netRef, UnExpectedMessage(f))
        }

      case x =>
        println(s"Testserver unexpectedly received $x")
    }
  }
}
