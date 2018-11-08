package sss.ui.nobu


import akka.actor.{Actor, ActorRef}
import akka.actor.Actor.Receive
import com.vaadin.ui.Notification
import sss.asado.account.{NodeIdentity, NodeIdentityManager}
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.contract.SingleIdentityEnc
import sss.asado.identityledger.IdentityService
import sss.asado.state.HomeDomain
import sss.asado.wallet.WalletPersistence
import sss.asado.wallet.WalletPersistence.Lodgement
import sss.db.Db
import sss.ui.nobu.CreateIdentity.{ClaimIdentity, Fund, NewClaimedIdentity}
import sss.ui.nobu.NobuNodeBridge.Notify
import us.monoid.web.Resty

import scala.util.{Failure, Success, Try}
import sss.asado.ledger._
import sss.asado.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr

object CreateIdentity {

  case class Fund(claim: String, sndr: ActorRef)
  case class NewClaimedIdentity(nodeIdentity: NodeIdentity)
  case class ClaimIdentity(claim: String, claimTag: String, phrase: String, sndr: ActorRef)

}

class CreateIdentity(implicit nodeIdentityManager: NodeIdentityManager,
                     homeDomain: HomeDomain,
                     identityService: IdentityService,
                     db:Db) {

  def createIdentity: Receive = {

    case ClaimIdentity(claimStr: String, claimTag: String, phrase: String, sendr: ActorRef) =>
      claim(claimStr, claimTag, phrase, sendr)

    case Fund(claim: String, sendr: ActorRef) =>
      fund(claim, sendr)
  }

  def fund(claim: String, sender: ActorRef) = {
    //localhost:8070/claim/debit?to=cavan1&amount=100
    Try(new Resty().text(s"${homeDomain.http}/claim/debit?to=${claim}&amount=100")) match {
      case Success(s) => sender ! Notify(s.toString)
      case Failure(e) => sender ! Notify(e.toString)
    }

  }

  def claim(claim: String, claimTag: String, phrase: String, sender: ActorRef) = {

      Try {
        if (identityService.accounts(claim).nonEmpty) throw new Error(s"Identity already claimed!")
        else {
          val nId = nodeIdentityManager.get(claim, claimTag, phrase).getOrElse  {
            sender ! Notify(s"Generating new key for $claim, this may take significant time ...")
            nodeIdentityManager(claim, claimTag, phrase)
          }

          val publicKey = nId.publicKey.toBase64Str

          Try(new Resty().text(
            s"${homeDomain.http}/console/command?1=claim&2=${claim}&3=${publicKey}")) match {

            case Success(tr) if (tr.toString == "ok") =>
              sender ! Notify(s"Thank you $claim, your claim has been successful")
              sender ! NewClaimedIdentity(nId)
            case Success(_) =>
              sender ! Notify(s"There was a problem with your $claim")
            case Failure(e) => sender ! Notify(s"$e")
          }

        }

      } match {
        case Failure(e) => sender ! Notify(s"${e.getMessage}", Notification.Type.ERROR_MESSAGE)
        case Success(_) =>
          sender ! Notify(s"Thank you $claim, your registration has been successfully submitted")
      }
    }

}