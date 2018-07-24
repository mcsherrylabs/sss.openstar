package sss.asado.contract

import java.util.Date

import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.ancillary.Logging
import sss.asado.account.PublicKeyAccount
import sss.asado.balanceledger.Tx
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.db._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */
case class CoinbaseValidator(pKeyOfFirstSigner: (Long) => Option[PublicKey],
                             rewardPerBlockAmount: Int,
                             numBlocksInTheFuture: Int)(implicit db: Db) extends Logging {

  private lazy val tableName = s"block_coinbase"

  private val block_height_str = "block_height"
  private val txId_str = "txId"
  private val when_str = "when_dt"

  private lazy val createTableSql =
    s"""CREATE TABLE IF NOT EXISTS $tableName
        |($block_height_str BIGINT,
        |$txId_str VARCHAR(100),
        |$when_str BIGINT,
        |PRIMARY KEY($block_height_str));
        |""".stripMargin

  private lazy val table = {
    db.executeSql(createTableSql)
    db.table(tableName)
  }

  private def write(blockHeight: Long, txIdAsStr: String): Unit = {
    table.insert(blockHeight, txIdAsStr, new Date().getTime)
  }

  private def getTxIdForBlockHeight(blockHeight: Long): Option[String] =
    table.find(
      where(block_height_str -> blockHeight))
      .map (r => r[String](txId_str))

  def validate(currentBlockHeight: Long, params: Seq[Seq[Array[Byte]]], tx:Tx) {
    require(tx.ins.size == 1, s"Only one input per coinbase tx allowed (not ${tx.ins.size})")
    val in = tx.ins(0)
    in.sig match {
      case CoinbaseDecumbrance(blockHeight) =>
        getTxIdForBlockHeight(currentBlockHeight) match {
          case Some(alreadyDone) =>
            log.debug(s"A coinbase Tx ${alreadyDone}=? ${tx.txId.toBase64Str} already exists for block height $blockHeight")

          case None =>
            //TODO Strategy for block rewards
            //require(in.amount == rewardPerBlockAmount, s"${in.amount} is not the amount allowed for a block ($rewardPerBlockAmount)")
            require(params.nonEmpty && params.head.nonEmpty, "The tx sig must be provided - not enough params.")
            val sigOfTx = params(0)(0)
            val pKey = pKeyOfFirstSigner(blockHeight)

            require(pKey.isDefined, s"Cannot find the public key used to sign block $blockHeight")

            tx.outs.foreach { out => out.encumbrance match {
                case SinglePrivateKey(publicKey, minBlockHeight) =>
                  require(blockHeight == currentBlockHeight - 1, "Must claim coinbase reward in the next block")
                  require(minBlockHeight >= blockHeight + numBlocksInTheFuture,
                    s"The earliest spend block must be ${numBlocksInTheFuture} blocks in the future." +
                      s"(min - ${minBlockHeight} cur -  $blockHeight)")
                case SingleIdentityEnc(identity, minBlockHeight) =>
                  require(blockHeight == currentBlockHeight - 1, "Must claim coinbase reward in the next block")
                  require(minBlockHeight >= blockHeight + numBlocksInTheFuture,
                    s"The earliest spend block must be ${numBlocksInTheFuture} blocks in the future." +
                      s"(min - ${minBlockHeight} cur -  $blockHeight)")

                case _ => require(false, "The reward coins must be locked using SinglePrivateKey encumbrance ")
              }
            }
            require(PublicKeyAccount(pKey.get).verify(sigOfTx, tx.txId),
              "The signature provided did not match the tx and key.")

            log.debug(s"Writing Coinbase Tx ${tx.txId.toBase64Str} for height $currentBlockHeight")
            write(currentBlockHeight, tx.txId.toBase64Str)
          }

      case _ => require(false, "Must use CoinbaseDecumbrance to decumber coinbase tx.")
    }
  }
}

case class CoinbaseDecumbrance(blockHeight: Long) extends Decumbrance
