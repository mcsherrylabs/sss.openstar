package sss.openstar.peers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.util.ByteString
import sss.openstar.network.MessageEventBus.IncomingMessage
import sss.openstar.network.{MessageEventBus, NodeId, SerializedMessage}
import sss.openstar.peers.DiscoveryActor.Discover
import sss.openstar.{MessageKeys, OpenstarEvent, Send, UniqueNodeIdentifier}

import scala.util.Random

object DiscoveryActor {

  def apply(props:Props)(implicit actorSystem: ActorSystem): ActorRef = {
    actorSystem actorOf(props, "DiscoveryActor")
  }

  def props(discovery: Discovery)
           (implicit events: MessageEventBus,
            send: Send): Props = Props(classOf[DiscoveryActor], discovery, events, send)

  case class Discover(seeds: Set[UniqueNodeIdentifier]) extends OpenstarEvent
}

class DiscoveryActor(
                     discovery: Discovery)
                    (implicit events: MessageEventBus,
                     send: Send) extends Actor with ActorLogging {

  import SerializedMessage.noChain

  private val start = 0
  private val pageSize = 1000
  private var hashCache: Map[(Int,Int), ByteString] = Map()

  events subscribe MessageKeys.SeqPeerPageResponse
  events subscribe MessageKeys.PeerPage
  events subscribe classOf[Discover]

    override def receive: Receive = {

      case Discover(seeds) if (seeds.nonEmpty) =>

        val conn = seeds.toSeq(Random.nextInt(seeds.size))

        val finger = hashCache.getOrElse((start, pageSize), {
          val (_, localFingerPrint) = discovery.query(start, pageSize)
          hashCache += (start, pageSize) -> localFingerPrint
          localFingerPrint
        })

        val msg = PeerPage(start, pageSize, finger)

        send(MessageKeys.PeerPage, msg, conn)

      case Discover(_) => // no connections
        log.warning("No seed nodes, no discovery.")

      case IncomingMessage(_, MessageKeys.PeerPage, fromNode, PeerPage(start, end, fingerPrint)) =>
        val (discoveredNodes, localFingerPrint) = discovery.query(start, end)

        if(fingerPrint != localFingerPrint) {

          val asResponses = discoveredNodes map (dn =>
            PeerPageResponse(
              dn.nodeId.id,
              dn.nodeId.inetSocketAddress,
              Capabilities(dn.capabilities)
            ))

          if(asResponses.nonEmpty) {
            send(MessageKeys.SeqPeerPageResponse, SeqPeerPageResponse(asResponses), fromNode)
          }
          send(MessageKeys.PeerPage, PeerPage(start, end, localFingerPrint), fromNode)
        }


      case IncomingMessage(_, MessageKeys.SeqPeerPageResponse, fromNode, SeqPeerPageResponse(seq)) =>
        seq foreach (self ! _)


      case PeerPageResponse(nodeId, socketAddr, capabilities) =>

        if(hashCache.nonEmpty) hashCache = Map.empty

        discovery
          .persist(
            NodeId(nodeId, socketAddr),
            capabilities.supportedChains
          ) recover {
          case e =>
            log.debug("Couldn't process {} {}, possible dup", nodeId, socketAddr)
            log.debug(e.toString)
        }

    }
}
