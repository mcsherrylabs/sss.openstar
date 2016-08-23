package sss.asado.console

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.agent.Agent
import sss.asado.block.Block
import sss.asado.block.signature.BlockSignatures
import sss.asado.identityledger.IdentityService
import sss.asado.network.NetworkController.ConnectTo
import sss.asado.network.{Connection, NodeId}
import sss.asado.wallet.Wallet
import sss.db.{Db, Where}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.ui.console.util.{Cmd, ConsoleServlet => BaseConsoleServlet}
/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
class ConsoleServlet(peerList: Agent[Set[Connection]],
                     ncRef: ActorRef,
                     identityService: IdentityService,
                     wallet: Wallet,
                     implicit val db: Db) extends BaseConsoleServlet {


  lazy val utxosTable = db.table("utxo")
  lazy val blocks = db.table("blockchain")

  val cmds: Map[String, Cmd] = Map (
    "peers" -> new Cmd {
      override def apply(params: Seq[String]): Seq[String] = peerList.get.map(_.nodeId.toString).toSeq
    },
    "signatures" -> new Cmd {
      override def help: String = s"signatures <blockheight> <num_sigs>"
      override def apply(params: Seq[String]): Seq[String] = {
        val sigs = BlockSignatures(params.head.toLong).signatures(params(1).toInt).map(_.toString)
        Seq(s"Num sigs is ${sigs.size}") ++ sigs
      }
    },
    "balance" -> new Cmd {
      override def help = s"the balance of the node wallet at a given block height"
      override def apply(params: Seq[String]): Seq[String] = {
        Seq(s"Balance: ${wallet.balance(params.head.toLong)}")
      }
    },
    "block" -> new Cmd {
      override def help = s"block <block height> <start index> <end index>"
      override def apply(params: Seq[String]): Seq[String] = {
        Block(params.head.toLong).entries.map(_.toString).slice(params(1).toInt, params(2).toInt) :+ "...End"
      }
    },
    "claim" -> new Cmd {
      override def help: String = s"Claim an identity with public key "
      override def apply(params: Seq[String]): Seq[String] = {
        val claim = params(1)
        val pKey = params(2).toByteArray
        identityService.claim(claim, pKey)
        Seq(s"Seems ok ... $claim")
      }
    },
    "connectpeer" -> new Cmd {
      override def help: String = s"nodeId ip port"
      override def apply(params: Seq[String]): Seq[String] = {
        val socketAddr = new InetSocketAddress(params(1), params(2).toInt)
        val n = NodeId(params(0), socketAddr)
        ncRef ! ConnectTo(n)
        Seq(s"$n")
      }
    },
    "id" -> new Cmd {
      override def apply(params: Seq[String]): Seq[String] = {
        identityService.accounts(params.head).map(_.toString)
      }
    },
    "blockheader" -> new Cmd {
      override def apply(params: Seq[String]): Seq[String] = {
        blocks.filter(Where("height = ?", params.head.toLong)).map(_.toString)
      }
    },
    "utxo" -> new Cmd {
    override def apply(params: Seq[String]): Seq[String] = {
      val startPage = params.head.toLong
      val pageSize = params.tail.head.toInt
      val result = utxosTable.page(startPage, pageSize, Seq()).map(_.toString)
      if(result.isEmpty) Seq("No utxos found")
      else result
    }
  }
  )

}
