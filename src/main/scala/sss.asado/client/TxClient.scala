package sss.asado.client

import sss.asado.account.ClientKey
import sss.asado.client.wallet.Wallet
import sss.asado.{BaseClient, ClientContext}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success, Try}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object TxClient extends BaseClient {

  override protected def run(context:ClientContext): Unit = {

    val (pka, wallet) = context.args match {
      case Array(clientName) =>
        val pka = ClientKey(clientName)
        (pka, Wallet(context, pka))
      case Array(clientName, unspentTxIdVarChar, unspentIndex, amount) =>
        val pka = ClientKey(clientName)
        val w = Wallet(context, pka)
        import w.toTxIndexFromStrings
        w.accept((unspentTxIdVarChar, unspentIndex), amount.toInt)
        (pka, w)
    }

    @tailrec
    def spendAllInOnes(balance: Int): Unit = {
      log.info(s"Balance is $balance")
      if(balance > 0) {
        val f = wallet.send(1, pka.publicKey)
        Try(Await.result(f, 1 minute)) match {
          case Failure(e) => log.info("Timeout spending money", e)
          case Success(s) => log.info(s"successfully spent $s")
        }
        spendAllInOnes(balance - 1)
      }
    }

    def spendAll: Unit = {
      if(wallet.balance > 0) {
        spendAllInOnes(wallet.balance)
        Thread.sleep(5000)
        spendAll
      } else Thread.sleep(5000)
    }

    log.info(s"Wallet balance ${wallet.balance}")
    try {
      spendAll
    } finally {wallet.close(5 seconds)}

  }
}



