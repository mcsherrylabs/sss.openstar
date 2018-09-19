package sss.asado.message

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.network.{MessageEventBus, SerializedMessage, NetworkRef}
import sss.asado.state.HomeDomain
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by alan on 6/8/16.
  */
case object CheckForMessages
case object ForceCheckForMessages

class MessageDownloadActor(who: String,
                           homeDomain: HomeDomain,
                           messageRouter: MessageEventBus,
                           ncRef: NetworkRef)(implicit db: Db)
    extends Actor
    with ActorLogging {

  messageRouter.subscribe(MessageKeys.MessageMsg)
  messageRouter.subscribe(MessageKeys.EndMessagePage)
  messageRouter.subscribe(MessageKeys.EndMessageQuery)

  log.info("MessageDownload actor has started...")

  private val inBox = MessageInBox(who)

  private var isQuiet = true

  def createQuery: MessageQuery = MessageQuery(inBox.maxInIndex, 25)

  override def receive: Receive = {

    case ForceCheckForMessages =>
      isQuiet = true
      self ! CheckForMessages

    case CheckForMessages =>
      if (isQuiet) {
        ncRef.send(
          SerializedMessage(0.toByte,
            MessageKeys.MessageQuery,
            createQuery.toBytes),
          homeDomain.nodeId.id)

        isQuiet = false
      }

    case SerializedMessage(_, MessageKeys.EndMessagePage, bytes) =>
      isQuiet = true
      self ! CheckForMessages

    case SerializedMessage(_, MessageKeys.EndMessageQuery, bytes) =>
      isQuiet = true
      context.system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS),
                                            self,
                                            CheckForMessages)

    case SerializedMessage(_, MessageKeys.MessageMsg, bytes) =>
      decode(MessageKeys.MessageMsg, bytes.toMessage) { msg: Message =>
        inBox.addNew(msg)
      }

  }
}
