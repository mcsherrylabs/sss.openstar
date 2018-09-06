package sss.asado.chains

import sss.asado.network.UniqueNodeIdentifier
import sss.asado.peers.PeerManager

import concurrent.ExecutionContext.Implicits.global

class ChainManager(
                    thisNodeId: UniqueNodeIdentifier,
                    chain: Chain,
                    peerManager: PeerManager
                  ) {


  def start(): Unit = {
    if(chain.quorum.exists(_ == thisNodeId)) {

    }

  }

}
