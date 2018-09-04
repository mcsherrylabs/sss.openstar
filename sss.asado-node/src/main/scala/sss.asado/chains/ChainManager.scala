package sss.asado.chains

import sss.asado.peers.PeerManager
import concurrent.ExecutionContext.Implicits.global

class ChainManager(
                    chains: Chains,
                    peerManager: PeerManager
                  ) {

  /*chains() foreach { chain =>
    peerManager.find(chain.id) map { connection =>

    }
  }*/


}
