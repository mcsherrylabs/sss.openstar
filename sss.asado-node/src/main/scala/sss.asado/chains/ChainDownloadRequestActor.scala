package sss.asado.chains

import akka.actor.{
  Actor,
  ActorContext,
  ActorLogging,
  ActorRef,
  ActorSystem,
  PoisonPill,
  Props
}
import sss.asado.block._
import org.joda.time.DateTime
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.account.{NodeIdentity, PublicKeyAccount}
import sss.asado.actor.{AsadoEventPublishingActor, SystemPanic}
import sss.asado.common.block._
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.eventbus.EventPublish
import sss.asado.ledger.{LedgerResult, Ledgers}
import sss.asado.network.MessageEventBus.{EventSubscriptions, IncomingMessage}
import sss.asado.network._
import sss.asado.peers.PeerManager.PeerConnection
import sss.db.Db

import scala.annotation.tailrec
import scala.collection.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object ChainDownloadRequestActor {

  def createStartSyncer(
      nodeIdentity: NodeIdentity,
      send: Send,
      messageEventBus: EventSubscriptions,
      bc: BlockChain with BlockChainSignaturesAccessor,
      db: Db,
      ledgers: Ledgers,
      chainId: GlobalChainIdMask)(context: ActorContext): ActorRef = {

    ChainDownloadRequestActor(
      props(nodeIdentity, bc)(db, chainId, send, messageEventBus, ledgers))(
      context)
  }

  case class CheckedProps(p: Props, name: String)

  def props(nodeIdentity: NodeIdentity,
            bc: BlockChain with BlockChainSignaturesAccessor)(
      implicit db: Db,
      chainId: GlobalChainIdMask,
      send: Send,
      messageEventBus: EventSubscriptions,
      ledgers: Ledgers
  ): CheckedProps =
    CheckedProps(
      Props(classOf[ChainDownloadRequestActor],
            nodeIdentity,
            bc,
            db,
            chainId,
            send,
            messageEventBus,
            ledgers),
      s"ChainDownloadRequestActor_${chainId}"
    )

  def apply(props: CheckedProps)(implicit context: ActorContext): ActorRef = {
    context.actorOf(props.p.withDispatcher("blocking-dispatcher"), props.name)
  }

}

private class ChainDownloadRequestActor(nodeIdentity: NodeIdentity,
                                        bc: BlockChain)(
    implicit db: Db,
    chainId: GlobalChainIdMask,
    send: Send,
    messageEventBus: EventSubscriptions with EventPublish,
    ledgers: Ledgers,
) extends Actor
    with ActorLogging
    with AsadoEventPublishingActor
    with SystemPanic {

  //private case class CommitBlock(blockId: BlockId, retryCount: Int = 0)

  messageEventBus.subscribe(MessageKeys.NotSynced)
  messageEventBus.subscribe(MessageKeys.Synced)
  messageEventBus.subscribe(MessageKeys.PagedTx)
  messageEventBus.subscribe(MessageKeys.EndPageTx)
  messageEventBus.subscribe(MessageKeys.PagedCloseBlock)
  messageEventBus.subscribe(MessageKeys.RejectedPagedTx)

  /*private var lastWrittenBlockId: BlockId = getLastCommitted()
  private var txCache: SortedSet[BlockChainTx] = SortedSet[BlockChainTx]()

  @tailrec
  private def commitWhileInOrder(): Unit = {
    (lastWrittenBlockId, txCache.headOption) match {
      case (BlockId(h1, index1), Some(BlockChainTx(h2, BlockTx(index2, le))))
          if h1 == h2 &&
            index1 + 1 == index2 =>
        val blockLedger = BlockChainLedger(h1)
        blockLedger.apply(le) match {
          case Failure(e) => {
            //we cannot apply a Tx that has been committed else where meaning we
            //have a leftover journalled tx that has not been committed - remove it an rewrite.
            log.info("Couldn't apply tx {}", e)

            if (Block(h1).getLastRecorded == index2) {
              log.info("Attempting to commit previously recorded tx h:{} i:{} ",
                       h1,
                       index2)

              BlockChainLedger(h1).commit(BlockId(h1, index2)) match {
                case Success(events) =>
                  events foreach (messageEventBus.publish(_))
                case Failure(e) =>
                  log.error("Could not commit recorded tx {}",
                            BlockId(h1, index2))
                  systemPanic(e)
              }
            }
          }
          case Success((_, events)) =>
            events foreach (messageEventBus.publish(_))
        }
        txCache = txCache.tail
        lastWrittenBlockId = BlockId(h1, index2)
        commitWhileInOrder()
      case _ =>
    }
  }*/



  private def withPeer(syncingWithNode: UniqueNodeIdentifier): Receive = withoutPeer orElse {

    case IncomingMessage(`chainId`,
                          MessageKeys.RejectedPagedTx,
                          `syncingWithNode`,
                          bTxId: BlockChainTxId) =>
      val blockLedger = BlockChainLedger(bTxId.height)
      blockLedger.rejected(bTxId) match {
        case Success(_) =>
        case Failure(e) => systemPanic(e)
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.PagedTx,
                         `syncingWithNode`,
                         bTx@ BlockChainTx(height, BlockTx(index, le))) =>
      val blockLedger = BlockChainLedger(bTx.height)
      blockLedger.apply(le) match {
        case Failure(e) => {
          //we cannot apply a Tx that has been committed else where meaning we
          //have a leftover journalled tx that has not been committed - remove it an rewrite.
          log.info("Couldn't apply tx {}", e)

          if (Block(height).getLastRecorded == index) {
            log.info("Attempting to commit previously recorded tx h:{} i:{} ",
              height,
              index)

            blockLedger.commit(BlockId(height, index)) match {
              case Success(events) =>
                events foreach (messageEventBus.publish(_))
              case Failure(e) =>
                log.error("Could not commit recorded tx {}",
                  BlockId(height, index))
                systemPanic(e)
            }
          }
        }
        case Success((_, events)) =>
          events foreach (messageEventBus.publish(_))
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.EndPageTx,
                         `syncingWithNode`,
                         getTxPage: GetTxPage) =>
      send(MessageKeys.GetPageTx, getTxPage, syncingWithNode)

    case IncomingMessage(`chainId`,
                         MessageKeys.PagedCloseBlock,
                         `syncingWithNode`,
                         DistributeClose(blockSigs, blockId)) =>
      //TODO CHECK THE SIGNATURES! Against the coinbase tx and the identity ldeger.
      val sigs =
        BlockSignatures.QuorumSigs(blockId.blockHeight).write(blockSigs)

      commit(blockId) match {
        case Failure(e) =>
          log.error(
            e,
            s"Could not commit this block ${blockId} ")
          systemPanic(e)


        case Success(events) =>
          bc.closeBlock(blockId) match {

            case Failure(e) =>
              log.error(e, s"Ledger cannot sync close block $blockId.")
              systemPanic()

            case Success(blockHeader) =>
              val first = blockSigs.head

              assert(
                PublicKeyAccount(first.publicKey)
                  .verify(first.signature, blockHeader.hash),
                s"Our block header h ${blockHeader.height} num ${blockHeader.numTxs} did not match the sig"
              )

              //lastWrittenBlockId = getLastCommitted()
              publish(BlockClosedEvent(blockHeader.height))
              events foreach messageEventBus.publish

              log.info(
                s"Synching - committed block height ${blockHeader.height}, num txs  ${blockHeader.numTxs}")

              assert(blockHeader.height == blockId.blockHeight,
                     s"How can ${blockHeader} differ from ${blockId}")

              send(MessageKeys.GetPageTx,
                   GetTxPage(blockHeader.height + 1, 1),
                   syncingWithNode)

          }
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.NotSynced,
                         `syncingWithNode`,
                         getTxPage: GetTxPage) =>
      finish(NotSynchronized(chainId),
             s"Downloader is synced to tx page $getTxPage but not fully synced")

    case IncomingMessage(`chainId`,
                         MessageKeys.Synced,
                         `syncingWithNode`,
                         p @ GetTxPage(h, i, pageSize)) =>
      finish(Synchronized(chainId, h, i),
             s"Downloader is fully synced to tx page $p")

  }

  private def withoutPeer: Receive = stopOnAllStop orElse {

    case PeerConnection(nodeId, _) =>
      context become withPeer(nodeId)
      val getTxs = createGetTxPage()
      send(MessageKeys.GetPageTx, getTxs, nodeId)
  }

  override def receive: Receive = withoutPeer

  private def commit(blockId: BlockId): LedgerResult = {
    BlockChainLedger(blockId.blockHeight).commit(blockId)
  }

  private def journal(bTx: BlockChainTx): BlockChainTx = {
    BlockChainLedger(bTx.height).journal(bTx.blockTx)
  }

  private def getLastCommitted(): BlockId = {
    val lb = bc.lastBlockHeader
    val blockStorage = Block(lb.height + 1)
    val indexOfLastRow = blockStorage.getLastCommitted
    BlockId(lb.height + 1, indexOfLastRow)
  }

  private def createGetTxPage() = {
    val BlockId(height, index) = getLastCommitted()
    GetTxPage(height, index + 1, 50)
  }

  private def finish(result: Any, msg: String) = {
    context become withoutPeer
    log.info(msg)
    context.parent ! result

  }

  private def newBlockSignature(
      blockHeader: BlockHeader): Option[BlockSignature] = {
    BlockSignatures
      .QuorumSigs(blockHeader.height)
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

}
