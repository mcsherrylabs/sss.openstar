package sss.openstar.telemetry

import akka.actor.{Actor, Cancellable}
import sss.openstar.block.BlockChainLedger.NewBlockId
import sss.openstar.network.{Connection, ConnectionLost, MessageEventBus}
import sss.openstar.telemetry.Client.{Report, ReportInterval}

import scala.concurrent.duration._

class ReportActor(client: Client, initialSleepSeconds: Long = 10)
                 (implicit events: MessageEventBus) extends Actor {

  events subscribe classOf[ReportInterval]
  events subscribe classOf[NewBlockId]
  events subscribe classOf[Connection]
  events subscribe classOf[ConnectionLost]

  private var interval = initialSleepSeconds

  private case object ReportTrigger

  import context.dispatcher

  private def resetTrigger(c:Option[Cancellable]): Option[Cancellable] = {
    c foreach(_.cancel())
    Option(
      context.system.scheduler.scheduleOnce(
        interval.seconds, self, ReportTrigger
      )
    )
  }

  private var cancellable = resetTrigger(None)

  private var connectionCount: Int = 0

  override def receive: Receive = {

    case ReportInterval(newSleep) =>

      interval = newSleep
      cancellable = resetTrigger(cancellable)

    case Report =>
      client.report(Report(...counters))

      // TODO add other counters.
  }
}

