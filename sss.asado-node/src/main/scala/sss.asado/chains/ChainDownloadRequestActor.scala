package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.block._
import org.joda.time.DateTime
import sss.asado.{MessageKeys, Send}
import sss.asado.account.NodeIdentity
import sss.asado.actor.{AsadoEventPublishingActor, SystemPanic}
import sss.asado.common.block._
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.ChainSynchronizer.NotSynchronized
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.ledger.Ledgers
import sss.asado.network.MessageEventBus.{EventSubscriptions, IncomingMessage}
import sss.asado.network._
import sss.asado.peers.PeerManager.PeerConnection
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * This actor's job is to bring the local blockchain up to date with
  * the leaders blockchain and keep it there.
  *
  * When it's asked to SynchroniseWith with 'who' it pages down the
  * tx's and the block sig's and closes the blocks (commiting the tx's to
  * the ledger in the process) until it gets 'Synced'
  *
  * It raises the 'Synced' event to the stateMachine.
  *
  * Then it continues to download tx's more slowly through the 'ConfimTx' message
  * but continues to close blocks through the 'CloseBlock' message
  *
  * @param nodeIdentity
  * @param send
  * @param messageEventBus
  * @param stateMachine
  * @param bc
  * @param db
  * @param ledgers
  */
object ChainDownloadRequestActor {


  def createStartSyncer(
                         nodeIdentity: NodeIdentity,
                         send: Send,
                         messageEventBus: EventSubscriptions,
                         bc: BlockChain with BlockChainSignatures,
                         db: Db, ledgers: Ledgers, chainId: GlobalChainIdMask)(context: ActorContext, peerConnection: PeerConnection): Unit= {

    ChainDownloadRequestActor(props(peerConnection,
      nodeIdentity,
      bc)(db, chainId, send, messageEventBus, ledgers)
    )(context)
  }

  case class CheckedProps(p: Props) extends AnyVal

  def props(peerConnection: PeerConnection,
            nodeIdentity: NodeIdentity,
            bc: BlockChain with BlockChainSignatures)(
      implicit db: Db,
      chainId: GlobalChainIdMask,
      send: Send,
      messageEventBus: EventSubscriptions,
      ledgers: Ledgers
      ): CheckedProps =

    CheckedProps(
      Props(classOf[ChainDownloadRequestActor],
            peerConnection,
            nodeIdentity,
            bc,
            db,
            chainId,
            send,
            messageEventBus,
            ledgers
            )
    )

  def apply(props: CheckedProps)(
      implicit context: ActorContext): ActorRef = {
    context.actorOf(props.p)
  }

}

private class ChainDownloadRequestActor(peerConnection: PeerConnection,
                                        nodeIdentity: NodeIdentity,
                                        bc: BlockChain)(
    implicit db: Db,
    chainId: GlobalChainIdMask,
    send: Send,
    messageEventBus: EventSubscriptions,
    ledgers: Ledgers,
    )
    extends Actor
    with ActorLogging
    with AsadoEventPublishingActor
    with SystemPanic {

  private case class CommitBlock(blockId: BlockId, retryCount: Int = 0)

  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(MessageKeys.NotSynced)
  messageEventBus.subscribe(MessageKeys.Synced)
  messageEventBus.subscribe(MessageKeys.PagedTx)
  messageEventBus.subscribe(MessageKeys.EndPageTx)
  messageEventBus.subscribe(MessageKeys.CloseBlock)
  //messageEventBus.subscribe(MessageKeys.BlockSig)

  private val syncingWithNode = peerConnection.nodeId

  private case object StartSync

  self ! StartSync

  log.info(s"BlockChainDownloader actor has started... $self")

  override def receive: Receive = stopOnAllStop orElse {

    case StartSync =>
      val getTxs = createGetTxPage()
      send(MessageKeys.GetPageTx, getTxs,
           syncingWithNode)

    case IncomingMessage(`chainId`,
                         MessageKeys.PagedTx,
                         `syncingWithNode`,
                         bTx: BlockChainTx) =>
      Try(
        //TODO don't journal, use a cache of ordered txs
        journal(bTx)
      ) match {
        case Failure(e) =>
          log.error(e, s"Ledger cannot sync PagedTx")
          log.error(s"Failed to journal blockTx $bTx")
          systemPanic()

        case Success(txDbId) => log.debug(s"CONFIRMED Up to $txDbId")
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.EndPageTx,
                         `syncingWithNode`,
                         getTxPage: GetTxPage) =>
      send(MessageKeys.GetPageTx, getTxPage,
           syncingWithNode)

    /*case IncomingMessage(`chainId`,
                         MessageKeys.BlockSig,
                         `syncingWithNode`,
                         blkSig: BlockSignature) =>
      BlockSignatures(blkSig.height).write(blkSig)*/

    case IncomingMessage(`chainId`,
                         MessageKeys.CloseBlock,
                         `syncingWithNode`,
                         DistributeClose(blockSigs, blockId)) =>
      //TODO CHECK THE SIGNATURES! Against the coinbase tx and the identity ldeger.
      BlockSignatures(blockId.blockHeight).write(blockSigs)
      self ! CommitBlock(blockId)

    case CommitBlock(blockId, reTryCount) =>
      Try(commit(blockId)) match {
        case Failure(e) if NonFatal(e) =>
          retryLater(e, blockId, reTryCount)

        case Success(_) =>
          Try(close(blockId)) match {

            case Failure(e) =>
              log.error(e, s"Ledger cannot sync close block $blockId.")
              systemPanic()

            case Success(blockHeader) =>
              publish(BlockClosedEvent(blockHeader.height))

              log.info(
                s"Synching - committed block height ${blockHeader.height}, num txs  ${blockHeader.numTxs}")

              assert(blockHeader.height == blockId.blockHeight,
                     s"How can ${blockHeader} differ from ${blockId}")

              newBlockSignature(blockHeader) foreach { newSig =>
                send(MessageKeys.BlockNewSig, newSig,
                     syncingWithNode)
              }

              send(
                MessageKeys.GetPageTx,
                                  GetTxPage(blockHeader.height + 1, 0),
                syncingWithNode)

          }
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.NotSynced,
                         `syncingWithNode`,
                         getTxPage: GetTxPage) =>
      finish(NotSynchronized(chainId, syncingWithNode),
             s"Downloader is synced to tx page $getTxPage but not fully synced")

    case ConnectionLost(`syncingWithNode`) =>
      finish("", s"Connection to $syncingWithNode lost")
      finish(NotSynchronized(chainId, syncingWithNode),
             s"Connection to $syncingWithNode lost")

    case IncomingMessage(`chainId`,
                         MessageKeys.Synced,
                         `syncingWithNode`,
                         p @ GetTxPage(h, i, pageSize)) =>
      finish(Synchronized(chainId, h, i),
             s"Downloader is fully synced to tx page $p")

  }

  private def close(blockId: BlockId): BlockHeader = {
    bc.closeBlock(bc.blockHeader(blockId.blockHeight - 1))
  }

  private def commit(blockId: BlockId): Unit = {
    BlockChainLedger(blockId.blockHeight).commit(blockId)
  }

  private def journal(bTx: BlockChainTx): BlockChainTx = {
    BlockChainLedger(bTx.height).journal(bTx.blockTx)
  }

  private def createGetTxPage() = {
    val lb = bc.lastBlockHeader
    val blockStorage = Block(lb.height + 1)
    val indexOfLastRow = blockStorage.maxMonotonicCommittedIndex
    val startAtNextIndex = indexOfLastRow + 1
    GetTxPage(lb.height + 1, startAtNextIndex, 50)
  }

  private def finish[T >: NotSynchronized with Synchronized](result: T,
                                                             msg: String) = {
    log.info(msg)
    messageEventBus unsubscribe self
    context.parent ! result
    context stop self
  }

  private def newBlockSignature(
      blockHeader: BlockHeader): Option[BlockSignature] = {
    BlockSignatures(blockHeader.height)
      .indexOfBlockSignature(nodeIdentity.id) map { i =>
      val sig = nodeIdentity.sign(blockHeader.hash)
      BlockSignature(0,
                     new DateTime(),
                     blockHeader.height,
                     nodeIdentity.id,
                     nodeIdentity.publicKey,
                     sig)

    }
  }

  private def retryLater(e: Throwable, blockId: BlockId, reTryCount: Int) = {

    log.error(
      e,
      s"Could not commit this block ${blockId}, retry count is $reTryCount")

    val retryDelaySeconds = if (reTryCount > 60) 60 else reTryCount + 1

    context.system.scheduler.scheduleOnce(retryDelaySeconds seconds,
                                          self,
                                          CommitBlock(blockId, reTryCount + 1))
  }
}
