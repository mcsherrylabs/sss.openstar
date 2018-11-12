package sss.ui.nobu


import akka.actor.ActorRef
import akka.actor.Actor.Receive
import com.vaadin.ui.Notification
import sss.ancillary.Logging
import sss.asado.account.{NodeIdentity, NodeIdentityManager}

import sss.asado.identityledger.IdentityService
import sss.asado.state.HomeDomain

import sss.db.Db
import sss.ui.nobu.CreateIdentity.{ClaimIdentity, Fund, Funded, NewClaimedIdentity}
import sss.ui.nobu.NobuNodeBridge.{Fail}
import us.monoid.web.Resty

import scala.util.{Failure, Success, Try}
import sss.asado.network.MessageEventBus
import sss.asado.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr
import sss.ui.nobu.WaitKeyGenerationView.Update

object CreateIdentity {

  case class Fund(uiId: Option[String], nodeIdentity: NodeIdentity, sndr: ActorRef)
  case class Funded(uiId: Option[String], nodeIdentity: NodeIdentity, amount: Long)
  case class NewClaimedIdentity(nodeIdentity: NodeIdentity)
  case class ClaimIdentity(uiId: Option[String], claim: String, claimTag: String, phrase: String, sndr: ActorRef)

}

class CreateIdentity(implicit nodeIdentityManager: NodeIdentityManager,
                     homeDomain: HomeDomain,
                     identityService: IdentityService,
                     messageEventBus: MessageEventBus,
                     db:Db) extends Logging {

  def createIdentity: Receive = {

    case ClaimIdentity(uiId: Option[String], claimStr: String, claimTag: String, phrase: String, sendr: ActorRef) =>
      claim(uiId, claimStr, claimTag, phrase, sendr)

    case Fund(uiId: Option[String], nodeIdentity: NodeIdentity, sendr: ActorRef) =>
      fund(uiId,nodeIdentity, sendr)
  }

  def fund(uiId: Option[String], nodeIdentity: NodeIdentity, sender: ActorRef) = {
    //localhost:8070/claim/debit?to=cavan1&amount=100
    val amount = 100
    Try(new Resty().text(s"${homeDomain.http}/claim/debit?to=${nodeIdentity.id}&amount=$amount")) match {
      case Success(s) =>
        sender ! Funded(uiId, nodeIdentity, amount)

      case Failure(e) =>
        log.warn("Failed to fund {}", nodeIdentity.id)
        log.warn("Failed to fund exception {}", e)
        sender ! Fail(s"Failed to fund ${nodeIdentity.id}")
    }

  }

  def claim(uiId: Option[String], claim: String, claimTag: String, phrase: String, sender: ActorRef) = {

    if (identityService.accounts(claim).nonEmpty) {
      sender ! Fail(s"Identity $claim already claimed!")
    } else {
      Try {
        nodeIdentityManager.get(claim, claimTag, phrase).getOrElse {
          nodeIdentityManager(claim, claimTag, phrase)
        }
      }

    } match {
      case Failure(e) =>
        sender ! Fail(s"Could not generate or retrieve identity key file.")

      case Success(nId) =>
        val publicKey = nId.publicKey.toBase64Str

        Try(new Resty().text(
          s"${homeDomain.http}/console/command?1=claim&2=${claim}&3=${publicKey}")) match {

          case Success(tr) if (tr.toString.contains("ok")) =>
            sender ! NewClaimedIdentity(nId)
          case Success(s) =>
            log.info(s.toString)
            sender ! Fail(s"There was a problem with your $claim, try again.")
          case Failure(e) =>
            log.info(e.toString)
            sender ! Fail(s"There was a problem with your $claim, try again.")

        }
    }
  }

}