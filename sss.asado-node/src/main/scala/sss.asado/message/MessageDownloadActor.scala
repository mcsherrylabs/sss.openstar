package sss.asado.message

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.{MessageKeys, Send}
import sss.asado.network.MessageEventBus
import sss.asado.state.HomeDomain
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by alan on 6/8/16.
  */
case object CheckForMessages
case object ForceCheckForMessages

object MessageDownloadActor {
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

  messageRouter.subscribe(classOf[Message])
  messageRouter.subscribe(classOf[EndMessagePage])
  messageRouter.subscribe(classOf[EndMessageQuery])

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

    case EndMessagePage(`who`) =>
      isQuiet = true
      self ! CheckForMessages

    case EndMessageQuery(`who`) =>
      isQuiet = true
      context.system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS),
                                            self,
                                            CheckForMessages)


    case msg: Message if(msg.to == who) => inBox.addNew(msg)

  }
}
