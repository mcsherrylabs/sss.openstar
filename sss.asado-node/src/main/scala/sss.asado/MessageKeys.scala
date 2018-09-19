package sss.asado


import sss.ancillary.Logging
import sss.asado.block._
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block._
import sss.asado.eventbus.{MessageInfoComposite, MessageInfos, PureEvent}
import sss.asado.message._
import sss.asado.message.serialize.MsgResponseSerializer
import sss.asado.message.{EndMessageQuery => EndMessageQueryObj}
import sss.asado.message.{EndMessagePage => EndMessagePageObj}
import sss.asado.network.SerializedMessage
import sss.asado.peers.PeerManager._
import sss.asado.util.Serialize.ToBytes

import scala.reflect.ClassTag
import scala.util._

/**
  * Created by alan on 3/18/16.
  */


object MessageKeys extends PublishedMessageKeys with Logging {

  val FindLeader: Byte = 30
  val Leader: Byte = 31
  val VoteLeader: Byte = 32

  val Capabilities: Byte = 33
  val QueryCapabilities: Byte = 34
  val Synchronized: Byte = 35


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
  val NotSynced: Byte = 52

  val MessageQuery: Byte = 60
  val MessageMsg: Byte = 61
  val MessageAddressed: Byte = 62
  val EndMessagePage: Byte = 63
  val EndMessageQuery: Byte = 64
  val MessageResponse: Byte = 65

//  val messages: MessageInfos = MessageInfoComposite[MessageResponse](MessageResponse, classOf[MessageResponse], _.toMessageResponse, MsgResponseSerializer.toBytes) +:
//    MessageInfoComposite[EndMessageQueryObj.type](EndMessageQuery, EndMessageQueryObj.getClass.asInstanceOf[Class[EndMessageQueryObj.type]], _ => EndMessageQueryObj, _ => Array())

  private val localMessages: MessageInfos =  {
        MessageInfoComposite[MessageResponse](MessageResponse, classOf[MessageResponse], _.toMessageResponse) +:
        MessageInfoComposite[EndMessageQueryObj.type](EndMessageQuery, EndMessageQueryObj.getClass.asInstanceOf[Class[EndMessageQueryObj.type]], _ => EndMessageQueryObj) +:
        MessageInfoComposite[EndMessagePageObj.type](EndMessagePage, EndMessagePageObj.getClass.asInstanceOf[Class[EndMessagePageObj.type]], _ => EndMessagePageObj) :+
        MessageInfoComposite[AddressedMessage](MessageAddressed, classOf[AddressedMessage],_.toMessageAddressed) :+
        MessageInfoComposite[Message](MessageMsg, classOf[Message] , _.toMessage) :+
        MessageInfoComposite[MessageQuery](MessageQuery, classOf[MessageQuery] , _.toMessageQuery) :+
        MessageInfoComposite[DistributeClose](SimpleCloseBlock, classOf[DistributeClose], _.toDistributeClose) :+
        MessageInfoComposite[PureEvent](SimpleEndPageTx, classOf[PureEvent], PureEvent(SimpleEndPageTx, _)) :+
        MessageInfoComposite[BlockChainTx](SimplePagedTx, classOf[BlockChainTx], _.toBlockChainTx ) :+
        MessageInfoComposite[GetTxPage](SimpleGetPageTxEnd, classOf[GetTxPage] , _.toGetTxPage) :+
        MessageInfoComposite[GetTxPage](NotSynced, classOf[GetTxPage] , _.toGetTxPage) :+
        MessageInfoComposite[GetTxPage](SimpleGetPageTx, classOf[GetTxPage] , _.toGetTxPage) :+
        MessageInfoComposite[BlockSignature](BlockNewSig, classOf[BlockSignature], _.toBlockSignature) :+
        MessageInfoComposite[BlockSignature](BlockSig, classOf[BlockSignature], _.toBlockSignature) :+
        MessageInfoComposite[GetTxPage](Synced, classOf[GetTxPage], _.toGetTxPage) :+
        MessageInfoComposite[DistributeClose](CloseBlock, classOf[DistributeClose], _.toDistributeClose) :+
        MessageInfoComposite[GetTxPage](EndPageTx, classOf[GetTxPage], _.toGetTxPage) :+
        MessageInfoComposite[GetTxPage](GetPageTx, classOf[GetTxPage], _.toGetTxPage) :+
        MessageInfoComposite[BlockChainTx](PagedTx, classOf[BlockChainTx], _.toBlockChainTx) :+
        MessageInfoComposite[VoteLeader](VoteLeader, classOf[VoteLeader], _.toVoteLeader) :+
        MessageInfoComposite[FindLeader](FindLeader, classOf[FindLeader], _.toFindLeader) :+
        MessageInfoComposite[PureEvent](Leader, classOf[PureEvent], PureEvent(Leader, _)) :+
        MessageInfoComposite[Capabilities](Capabilities, classOf[Capabilities], _.toCapabilities) :+
        MessageInfoComposite[Synchronized](Synchronized, classOf[Synchronized], _.toSynchronized) :+
        MessageInfoComposite[PureEvent](QueryCapabilities, classOf[PureEvent], PureEvent(QueryCapabilities,_))


  }

  val messages = localMessages ++ publishedMsgs


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

object TestIt {
  def main(args: Array[String]): Unit = {





    val f = MessageKeys.messages.find(MessageKeys.EndMessageQuery)
    val s = f.get.fromBytes(Array())
    s match {
      case EndMessageQueryObj => println("got it")
      case _: Int => println(":(")
    }

  }
}