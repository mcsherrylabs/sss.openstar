package sss.asado

import sss.ancillary.Logging
import sss.asado.network.NetworkMessage
import sss.asado.block._

import scala.util._

/**
  * Created by alan on 3/18/16.
  */
object MessageKeys extends PublishedMessageKeys with Logging {

  val FindLeader: Byte = 30
  val Leader: Byte = 31
  val VoteLeader: Byte = 32

  val GetPageTx: Byte = 40
  val PagedTx: Byte = 41
  val EndPageTx: Byte = 42
  val CloseBlock: Byte = 43
  val Synced: Byte = 44
  val BlockSig: Byte = 45
  val BlockNewSig: Byte = 46
  val SimpleGetPageTx: Byte = 47
  val SimpleGetPageTxEnd: Byte = 48
  val SimplePagedTx: Byte = 49
  val SimpleEndPageTx: Byte = 50
  val SimpleCloseBlock: Byte = 51

  val MessageQuery: Byte = 60
  val MessageMsg: Byte = 61
  val MessageAddressed: Byte = 62
  val EndMessagePage: Byte = 63
  val EndMessageQuery: Byte = 64
  val MessageResponse: Byte = 65

  val f: NetworkMessage = encodeMessageKeys(this)

  val encodeMessageKeys = new PartialFunction[AnyRef, NetworkMessage] {
    override def isDefinedAt(x: AnyRef): Boolean = ???

    override def apply(v1: AnyRef): NetworkMessage = ???
  }
  val decodeMessageKeys = new PartialFunction[NetworkMessage, AnyRef] {

    override def isDefinedAt(x: NetworkMessage): Boolean = {
      true
    }

    override def apply(netMsg: NetworkMessage): AnyRef = netMsg match {
      case NetworkMessage(2, bytes) => bytes.toTxMessage
    }
  }

  def decode[T](msgCode: Byte, f: => T)(t: T => Unit): Unit = {
    Try {
      f
    } match {
      case Failure(e) =>
        log.error(s"Unable to decode a request of type $msgCode", e)
      case Success(s) =>
        Try(t(s)) match {
          case Failure(e) =>
            log.error(s"Problem handling message of type $msgCode", e)
          case _ =>
        }
    }
  }

}
