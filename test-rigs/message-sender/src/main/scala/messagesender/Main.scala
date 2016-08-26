package messagesender

import akka.actor.{ActorRef, Props}
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.contract.SingleIdentityEnc
import sss.asado.ledger._
import sss.asado.network.NetworkController.BindControllerSettings
import sss.asado.nodebuilder.{BindControllerSettingsBuilder, ClientNode, ConfigBuilder, ConfigNameBuilder}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.wallet.WalletPersistence.Lodgement
import us.monoid.web.Resty

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 7/11/16.
  */
object Main {

  def main(args: Array[String]) {
    val prefix = args(0)
    val port = args(1).toInt
    val startIndex = args(2).toInt
    val endIndex = args(3).toInt
     new MessageSenderClient(new EditPortBindControllerSettings(port), prefix,
      new CircularSeq(startIndex, endIndex, port))

  }


}

class CircularSeq(min:Int, max:Int, skip: Int) {

  private var pos = min

  require(max > min)
  require(max - min >= 1)
  require(max >= skip &&  min <= skip)

  def next: Int = {
    if(pos == skip) pos += 1
    if(pos > max) {
      pos = min
      next
    } else {
      pos += 1
      pos - 1
    }
  }
}
class EditPortBindControllerSettings(override val port: Int) extends BindControllerSettingsBuilder with
  ConfigNameBuilder with
            ConfigBuilder with BindControllerSettings {
  override val configName: String = "node"
  override val applicationName: String = bindSettings.applicationName
  override val bindAddress = "127.0.0.1"
  override val declaredAddressOpt: Option[String] = Some("127.0.0.1")
  override val connectionRetryIntervalSecs: Int = bindSettings.connectionRetryIntervalSecs
  override val appVersion: String = bindSettings.appVersion
  override val maxNumConnections = bindSettings.maxNumConnections
  override val connectionTimeout = bindSettings.connectionTimeout
  override val localOnly = bindSettings.localOnly

}

class MessageSenderClient(val newBndSettings: BindControllerSettings,
                         prefix: String, circSeq: CircularSeq) extends ClientNode {
  override val configName: String = "node"
  override val phrase: Option[String] = Some("password")
  override lazy val bindSettings: BindControllerSettings = newBndSettings

  lazy override val nodeIdentity: NodeIdentity = {
    val idStr = s"${prefix}${bindSettings.port}"
    val defTag = "defaultTag"

    NodeIdentity(idStr, defTag, new String(phrase.get))
  }

  def claim(claim: String, claimTag: String, phrase:String) = {

    val http = homeDomain.http
    val nId = NodeIdentity(claim, claimTag, phrase)
    val publicKey = nId.publicKey.toBase64Str

    Try(new Resty().text(s"$http/claim?claim=$claim&tag=$claimTag&pKey=$publicKey")) match {
      case Failure(e) =>
        //NodeIdentity.deleteKey(claim, claimTag)
        log.error(s"Failed to claim $claim $e")
        throw e

      case Success(resultText) => resultText.toString match {
        case msg if msg.startsWith("ok:") =>
          val asAry = msg.substring(3).split(":")
          val txIndx = TxIndex(asAry(0).asTxId, asAry(1).toInt)
          val txOutput = TxOutput(asAry(2).toInt, SingleIdentityEnc(nId.id, 0))
          val inBlock = asAry(3).toLong
          walletPersistence.track(Lodgement(txIndx, txOutput, inBlock))
        case errMsg =>
          //NodeIdentity.deleteKey(claim, claimTag)
          //log.error(s"Failed to claim $claim: $errMsg")
          throw new Error(errMsg)
      }
    }
  }

  actorSystem.actorOf(Props(classOf[OrchestratingActor], this, prefix, circSeq))

  initStateMachine


}
