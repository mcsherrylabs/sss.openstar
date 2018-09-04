package sss.asado.peers

import sss.asado.{AsadoEvent, Identity}
import sss.asado.chains.GlobalChainIdMask
import sss.asado.network.Connection
import sss.asado.network.MessageEventBus.HasNodeId

import scala.concurrent.Future

class PeerManager {

  trait Query
  case class ChainQuery(chainId: GlobalChainIdMask) extends Query
  case class IdQuery(ids: Set[Identity]) extends Query

  def query(q: Query) = {

  }

  case class PeerQueryMatch(id:Identity, chainId: GlobalChainIdMask) extends AsadoEvent {
    def matches(chainId: GlobalChainIdMask): Boolean = ???
    def matches(id:Identity): Boolean = ???
  }
  // register for connections
  // on connection get the supported chains
  // add to database
  // check against the filters if match publish PeerQueryMatch(id:Identity)
  // get connection
  // if the connection fails try another one.
  // incoming connections get added also.



}
