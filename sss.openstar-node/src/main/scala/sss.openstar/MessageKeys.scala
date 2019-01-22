package sss.openstar


import sss.ancillary.Logging
import sss.openstar.block._
import sss.openstar.block.signature.BlockSignatures.BlockSignature

import sss.openstar.common.block._
import sss.openstar.eventbus.{MessageInfoComposite, MessageInfos, PureEvent}
import sss.openstar.message._
import sss.openstar.peers._
import sss.openstar.peers.PeerManager._




object MessageKeys extends PublishedMessageKeys with Logging {

  val FindLeader: Byte = 30
  val Leader: Byte = 31
  val VoteLeader: Byte = 32

  val Capabilities: Byte = 33
  val QueryCapabilities: Byte = 34
  val Synchronized: Byte = 35
  val PeerPage: Byte = 36
  val PeerPageResponse: Byte = 37 // TODO unused? If so remove.
  val SeqPeerPageResponse: Byte = 38


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


  private val localMessages: MessageInfos =
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
    MessageInfoComposite[PureEvent](QueryCapabilities, classOf[PureEvent], PureEvent(QueryCapabilities,_)) :+
    MessageInfoComposite[PeerPage](PeerPage, classOf[PeerPage], _.toPeerPage) :+
    MessageInfoComposite[SeqPeerPageResponse](SeqPeerPageResponse, classOf[SeqPeerPageResponse], _.toSeqPeerPageResponse) :+
    MessageInfoComposite[PeerPageResponse](PeerPageResponse, classOf[PeerPageResponse], _.toPeerPageResponse)

  val messages = localMessages ++ publishedMsgs

}
