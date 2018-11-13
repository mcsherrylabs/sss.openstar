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
  val PagedCloseBlock: Byte = 43
  val CloseBlock: Byte = 44
  val Synced: Byte = 45
  val BlockSig: Byte = 46
  val BlockNewSig: Byte = 47
  val NonQuorumBlockNewSig: Byte = 48
  val NonQuorumCloseBlock: Byte = 49
  val RejectedPagedTx: Byte = 50

  val NotSynced: Byte = 52

  val MessageQuery: Byte = 60
  val MessageMsg: Byte = 61
  val MessageAddressed: Byte = 62
  val EndMessagePage: Byte = 63
  val EndMessageQuery: Byte = 64
  val MessageResponse: Byte = 65


  private val localMessages: MessageInfos =  {
        MessageInfoComposite[MessageResponse](MessageResponse, classOf[MessageResponse], _.toMessageResponse) +:
        MessageInfoComposite[EndMessageQuery](EndMessageQuery, classOf[EndMessageQuery], _.toEndMessageQuery) +:
        MessageInfoComposite[EndMessagePage](EndMessagePage, classOf[EndMessagePage], _.toEndMessagePage) :+
        MessageInfoComposite[AddressedMessage](MessageAddressed, classOf[AddressedMessage],_.toMessageAddressed) :+
        MessageInfoComposite[Message](MessageMsg, classOf[Message] , _.toMessage) :+
        MessageInfoComposite[MessageQuery](MessageQuery, classOf[MessageQuery] , _.toMessageQuery) :+
        MessageInfoComposite[GetTxPage](NotSynced, classOf[GetTxPage] , _.toGetTxPage) :+
        MessageInfoComposite[BlockSignature](BlockNewSig, classOf[BlockSignature], _.toBlockSignature) :+
        MessageInfoComposite[BlockSignature](NonQuorumBlockNewSig, classOf[BlockSignature], _.toBlockSignature) :+
        MessageInfoComposite[BlockSignature](BlockSig, classOf[BlockSignature], _.toBlockSignature) :+
        MessageInfoComposite[GetTxPage](Synced, classOf[GetTxPage], _.toGetTxPage) :+
        MessageInfoComposite[DistributeClose](PagedCloseBlock, classOf[DistributeClose], _.toDistributeClose) :+
        MessageInfoComposite[DistributeClose](CloseBlock, classOf[DistributeClose], _.toDistributeClose) :+
        MessageInfoComposite[DistributeClose](NonQuorumCloseBlock, classOf[DistributeClose], _.toDistributeClose) :+
        MessageInfoComposite[GetTxPage](EndPageTx, classOf[GetTxPage], _.toGetTxPage) :+
        MessageInfoComposite[GetTxPage](GetPageTx, classOf[GetTxPage], _.toGetTxPage) :+
        MessageInfoComposite[BlockChainTxId](RejectedPagedTx, classOf[BlockChainTxId], _.toBlockChainTxId) :+
        MessageInfoComposite[BlockChainTx](PagedTx, classOf[BlockChainTx], _.toBlockChainTx) :+
        MessageInfoComposite[VoteLeader](VoteLeader, classOf[VoteLeader], _.toVoteLeader) :+
        MessageInfoComposite[FindLeader](FindLeader, classOf[FindLeader], _.toFindLeader) :+
        MessageInfoComposite[Leader](Leader, classOf[Leader], _.toLeader) :+
        MessageInfoComposite[Capabilities](Capabilities, classOf[Capabilities], _.toCapabilities) :+
        MessageInfoComposite[Synchronized](Synchronized, classOf[Synchronized], _.toSynchronized) :+
        MessageInfoComposite[PureEvent](QueryCapabilities, classOf[PureEvent], PureEvent(QueryCapabilities,_))


  }

  val messages = localMessages ++ publishedMsgs

}
