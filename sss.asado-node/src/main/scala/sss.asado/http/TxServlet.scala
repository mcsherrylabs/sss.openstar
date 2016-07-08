package sss.asado.http

import akka.actor.{ActorRef, ActorSystem}
import akka.agent.Agent
import org.scalatra.{Ok, ScalatraServlet}
import sss.asado.block.{Block, BlockChain, BlockChainTxConfirms}
import sss.asado.network.{Connection, NodeId}
import sss.db.{Db, Where}


/**
  * Created by alan on 5/7/16.
  */
class TxServlet(msgRouter: ActorRef,
                     nc: ActorRef,
                     peerList: Agent[Set[Connection]],
                     system: ActorSystem,
                     ncRef: ActorRef,
                     bc: BlockChain with BlockChainTxConfirms,
                     implicit val db: Db) extends ScalatraServlet {


  lazy val utxosTable = db.table("utxo")
  lazy val blocks = db.table("blockchain")

  post("/tx") {
    Ok()
  }
}


