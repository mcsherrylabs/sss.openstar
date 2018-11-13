package sss.asado.message

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.message.MessageDownloadActor.{CheckForMessages, ForceCheckForMessages, NewInBoxMessage}
import sss.asado.{AsadoEvent, MessageKeys, Send}
import sss.asado.network.MessageEventBus
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.state.HomeDomain
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by alan on 6/8/16.
  */


object MessageDownloadActor {

  case object CheckForMessages
  case object ForceCheckForMessages
  case class NewInBoxMessage(msg: Message) extends AsadoEvent

  def apply(who: String,
            homeDomain: HomeDomain)(implicit actorSystem: ActorSystem,
                                    db: Db,
                                    messageRouter: MessageEventBus,
                                    send: Send,
                                    chainId: GlobalChainIdMask): ActorRef = {
    actorSystem.actorOf(
      Props(classOf[MessageDownloadActor],
        who,
        homeDomain,
        db,
        messageRouter,
        send,
        chainId)
    )
  }
}
class MessageDownloadActor(who: String,
                           homeDomain: HomeDomain,
                           )(implicit db: Db,
                             messageRouter: MessageEventBus,
                             send: Send,
                             chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging {

  messageRouter.subscribe(MessageKeys.MessageMsg)
  messageRouter.subscribe(MessageKeys.EndMessageQuery)
  messageRouter.subscribe(MessageKeys.EndMessagePage)

  log.info("MessageDownload actor has started...")

  private val inBox = MessageInBox(who)

  private var isQuiet = true

  def createQuery: MessageQuery = MessageQuery(who, inBox.maxInIndex, 25)

  override def receive: Receive = {

    case ForceCheckForMessages =>
      isQuiet = true
      self ! CheckForMessages

    case CheckForMessages =>
      if (isQuiet) {
        send(
            MessageKeys.MessageQuery,
            createQuery,
          homeDomain.nodeId.id)

        isQuiet = false
      }

    case IncomingMessage(_, _, _, EndMessagePage(`who`)) =>
      isQuiet = true
      self ! CheckForMessages

    case IncomingMessage(_, _, _, EndMessageQuery(`who`)) =>
      isQuiet = true
      context.system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS),
                                            self,
                                            CheckForMessages)


    case IncomingMessage(_, _, _, msg: Message) if(msg.to == who) =>
      inBox.addNew(msg)
      messageRouter publish(NewInBoxMessage(msg))
  }
}
