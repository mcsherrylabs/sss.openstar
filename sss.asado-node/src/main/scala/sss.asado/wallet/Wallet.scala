package sss.asado.wallet

import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger._
import sss.asado.contract._
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger._
import sss.asado.wallet.WalletPersistence.Lodgement


/**
  * This wallet will attempt to double spend depending on when the unspent are marked spent!
  *
  */
object Wallet {
  case class UnSpent(txIndex: TxIndex, out: TxOutput)
}

class Wallet(identity: NodeIdentity,
             balanceLedger: BalanceLedgerQuery,
             identityServiceQuery: IdentityServiceQuery,
             walletPersistence: WalletPersistence,
             currentBlockHeight: () => Long) extends Logging {

  import Wallet._

  def credit(lodgement: Lodgement): Unit = {
    walletPersistence.track(lodgement)
  }

  def markSpent(spentIns: Seq[TxInput]): Unit = {
    spentIns.foreach { in =>
      walletPersistence.markSpent(in.txIndex)
    }
  }

  def update(txId: TxId,
             debits: Seq[TxInput],
             creditsOrderedByIndex: Seq[TxOutput],
             inBlock: Long = currentBlockHeight()): Unit = walletPersistence.tx {
    markSpent(debits)
    creditsOrderedByIndex.indices foreach { i =>
      credit(Lodgement(TxIndex(txId, i), creditsOrderedByIndex(i), inBlock))
    }
  }

  def prependOutputs(tx: Tx, txOutput: TxOutput*): Tx = {
    val newTxOuts = txOutput ++ tx.outs
    StandardTx(tx.ins, newTxOuts)
  }

  def appendOutputs(tx: Tx, txOutput: TxOutput*): Tx = {
    val newTxOuts = tx.outs ++ txOutput
    StandardTx(tx.ins, newTxOuts)
  }

  def sign(tx: Tx, secret: Array[Byte] = Array()): SignedTxEntry = {
    val sigs = tx.ins.map { in =>
      in.sig match {
        case PrivateKeySig => PrivateKeySig.createUnlockingSignature(identity.sign(tx.txId))
        case NullDecumbrance => Seq()
        case SingleIdentityDec => SingleIdentityDec.createUnlockingSignature(tx.txId, identity.tag, identity.sign)
        case SaleSecretDec => SaleSecretDec.createUnlockingSignature(tx.txId, identity.tag, identity.sign, secret)
        case ReturnSecretDec => ReturnSecretDec.createUnlockingSignature(tx.txId, identity.tag, identity.sign)
      }
    }
    SignedTxEntry(tx.toBytes, sigs)
  }

  def createTx(amountToSpend: Int): Tx = {
    val unSpent = findUnSpent(currentBlockHeight())

    def fund(acc: Seq[TxInput], outs: Seq[UnSpent], fundedTo: Int, target: Int): (Seq[TxInput], Seq[TxOutput]) = {
      if(fundedTo == target) (acc, Seq())
      else {
        outs.headOption match {
          case None => throw new IllegalArgumentException("Not enough credit")
          case Some(unspent) =>
            if(target - fundedTo >= unspent.out.amount) {
              val txIn = toInput(unspent)
              fund(acc :+ txIn, outs.tail, fundedTo + unspent.out.amount, target)
            } else {
              val txIn = toInput(unspent)
              val change = unspent.out.amount - (target - fundedTo)
              (acc :+ txIn, Seq(TxOutput(change, encumberToIdentity())))
            }
        }
      }
    }

    val (newIns, change) = fund(Seq(), unSpent, 0, amountToSpend)
    StandardTx(newIns, change)

  }


  private[wallet] def toInput(unSpent : UnSpent): TxInput = {
    TxInput(unSpent.txIndex, unSpent.out.amount, createDecumbrance(unSpent.out.encumbrance))
  }

  private[wallet] def createDecumbrance(enc:Encumbrance): Decumbrance = {
    enc match {
      case SinglePrivateKey(pKey, minBlockHeight) => PrivateKeySig
      case SaleOrReturnSecretEnc(returnIdentity,
                        claimant,
                        hashOfSecret,
                        returnBlockHeight) => {
        if(returnIdentity == identity.id) ReturnSecretDec
        else if(claimant == identity.id) SaleSecretDec
        else throw new IllegalArgumentException("This encumbrance is nothing to do with our identity.")
      }

      case SingleIdentityEnc(id, blockHeight) => SingleIdentityDec

      case NullEncumbrance => NullDecumbrance

    }
  }

  def encumberToIdentity(atBlockHeight: Long = 0,
                                         someIdentity: String = identity.id): Encumbrance =
    SingleIdentityEnc(someIdentity, atBlockHeight)


  private[wallet] def unSpentOpt(lodgement: Lodgement, atBlockHeight: Long): Option[UnSpent] = {

    balanceLedger.entry(lodgement.txIndex) match {
      case Some(txOut) => txOut.encumbrance match {
        case SinglePrivateKey(pKey, minBlockHeight) =>
          identityServiceQuery.identify(pKey).flatMap { acc =>
            if (acc.identity == identity.id && minBlockHeight <= atBlockHeight) Option(UnSpent(lodgement.txIndex, txOut))
            else None
          }

        case SingleIdentityEnc(id, blockHeight) =>
          if (id == identity.id && blockHeight <= atBlockHeight) Option(UnSpent(lodgement.txIndex, txOut))
          else None

        case SaleOrReturnSecretEnc(returnIdentity,
        claimant,
        hashOfSecret,
        returnBlockHeight) if returnIdentity == identity.id &&
          returnBlockHeight <= atBlockHeight =>
          Option(UnSpent(lodgement.txIndex, txOut))

        case SaleOrReturnSecretEnc(returnIdentity,
        claimant,
        hashOfSecret,
        returnBlockHeight) if returnIdentity != identity.id =>
          throw new Error(s"Why is there a Sale Enc with $returnIdentity? for is ${identity.id}")

        case NullEncumbrance => Option(UnSpent(lodgement.txIndex, txOut))
      }

      case None =>
        if (lodgement.inBlock == atBlockHeight) {
          // the lodgement is in the current block, but as this block has not been closed
          // it will not be in the balanceledger.
          Option(UnSpent(lodgement.txIndex, lodgement.txOutput))
        } else None
    }
  }

  def balance(atBlockHeight: Long = currentBlockHeight()): Int = findUnSpent(atBlockHeight).foldLeft(0)((acc, e) => acc + e.out.amount)

  private[wallet] def findUnSpent(atBlockHeight: Long): Seq[UnSpent] =  {
    val allWalletEntries = walletPersistence.listUnSpent
    log.info(s"There are ${allWalletEntries.size} unspent wallet entries.")
    allWalletEntries.foldLeft(Seq[UnSpent]()) ((acc: Seq[UnSpent], lodgement: Lodgement) =>
      unSpentOpt(lodgement, atBlockHeight) match {
        case Some(u) => acc :+ u
        case None => acc
      }
    )

  }

}
