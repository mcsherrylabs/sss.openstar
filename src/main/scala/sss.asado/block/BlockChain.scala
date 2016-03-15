package sss.asado.block

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/15/16.
  */
/*class BlockChainActor(configBase: String , msgCodes: Seq[Byte], messageRouter: ActorRef) extends Actor {

  msgCodes foreach (messageRouter ! Register(_))

  override def receive: Receive = ???
}

class Block(height: Int)(implicit db: Db) extends Storage[] {
  val t = db.table(s"block_${height}")

  def apply(stx: SignedTx): Unit = {

  }

  private lazy val hashPrevBlock: Array[Byte] = ???
  private lazy val txMerkleRoot: Array[Byte] = ???

  def close: Unit = {
    // write new block row
    db.table("blockchain").insert(height, hashPrevBlock, txMerkleRoot, new Date)
  }
}

object Block {
  def genesis: Block = ???
  def apply(height: Int)(implicit db: Db): Block = {
    new Block(height)
  }
}

class BlockChain(configBase: String) {

  implicit val db = Db(s"$configBase.database")
  val currentBlock = {
    db.table("blockchain").find(Where(" id > 0 ORDER BY height DESC LIMIT 1")) match {
      case None => Block.genesis
      case Some(row) => Block(row[Int]("height") + 1)
    }
  }

}*/