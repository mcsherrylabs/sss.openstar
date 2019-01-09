package sss.openstar.telemetry

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import sss.openstar.block.BlockChainLedger.NewBlockId
import sss.openstar.common.block.BlockId
import sss.openstar.common.telemetry._
import sss.openstar.network.{Connection, ConnectionLost, MessageEventBus}
import sss.openstar.telemetry.Client.ClientResponse
import sss.openstar.util.Serialize._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object ReportActor {
  def props(client: Client, nodeName: String, initialSleepSeconds: Long = 10)
           (implicit events: MessageEventBus): Props = Props(classOf[ReportActor], client, nodeName, initialSleepSeconds, events)

  def apply(props: Props)(implicit actorSystem: ActorSystem): ActorRef = actorSystem.actorOf(props, "ReportActor")

}
class ReportActor(client: Client, nodeName: String, initialSleepSeconds: Long)
                 (implicit events: MessageEventBus) extends Actor with ActorLogging {

  events subscribe classOf[ClientResponse]
  events subscribe classOf[NewBlockId]
  events subscribe classOf[Connection]
  events subscribe classOf[ConnectionLost]


  private var interval = initialSleepSeconds

  private case object ReportTrigger

  private var latestBlockId: Option[BlockId] = None

  private var numConnections = 0

  private var currentConnections: Set[String] = Set.empty

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

    case ClientResponse(rawBs) => Try {
      val newReportSleepInterval = rawBs.toArray.extract(LongDeSerialize)
      interval = newReportSleepInterval
      cancellable = resetTrigger(cancellable)
    } recover {
      case e => log.warning("Could not decode client telemetry response {}", e)
    }

    case ReportTrigger =>
      val r = Report(nodeName, latestBlockId, numConnections, currentConnections.toSeq)
      client.report(r.toByteString)
      cancellable = resetTrigger(cancellable)

    case NewBlockId(chId, blockId) =>
      latestBlockId = Option(blockId)

    case Connection(nodeName) =>
      numConnections += 1
      currentConnections += nodeName

    case ConnectionLost(nodeName) =>
      numConnections -= 1
      currentConnections -= nodeName
  }
}

