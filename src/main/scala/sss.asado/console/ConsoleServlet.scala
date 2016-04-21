package sss.asado.console

import akka.actor.{ActorRef, ActorSystem}
import akka.agent.Agent
import sss.asado.block.Block
import sss.asado.ledger.Ledger
import sss.asado.network.Connection
import sss.db.{Db, Where}
import sss.ui.console.util.{Cmd, ConsoleServlet => BaseConsoleServlet}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */

trait Event { val category: String }
case class Feedback(msg: String) extends Event { val category = "sss.ui.feedback"}


class ConsoleServlet(args: Array[String], msgRouter: ActorRef,
                     nc: ActorRef,
                     peerList: Agent[Set[Connection]],
                     system: ActorSystem,
                     implicit val db: Db) extends BaseConsoleServlet {

  val remote = system.actorSelection("akka.tcp://default@127.0.0.1:2577/user/uiReactorBroadcastEndpoint")
  remote ! Feedback("Hello World!")

  lazy val utxos = Ledger()
  lazy val utxosTable = db.table("utxo")
  lazy val blocks = db.table("blockchain")

  val cmds: Map[String, Cmd] = Map (
    "peers" -> new Cmd {
      override def apply(params: Seq[String]): Seq[String] = peerList.get.map(_.nodeId.toString).toSeq
    },
    "block" -> new Cmd {
      override def apply(params: Seq[String]): Seq[String] = {
        Block(params.head.toLong).entries.map(_.toString)
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
