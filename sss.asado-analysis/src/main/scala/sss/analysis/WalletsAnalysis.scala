package sss.analysis

import sss.analysis.Analysis.InOut
import sss.asado.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc, SinglePrivateKey}

/**
  * Created by alan on 11/14/16.
  */
class WalletsAnalysis(inOuts: Seq[InOut]) {

  lazy val sortedById: Map[String, Seq[InOut]] = {

    def discriminator(inOut: InOut): String = {
      inOut.txOut.encumbrance match {
        case enc: SinglePrivateKey => "privateKey"
        case enc: SingleIdentityEnc => enc.identity
        case enc: SaleOrReturnSecretEnc => enc.claimant
      }
    }
    inOuts.groupBy(discriminator)

  }
  def apply(identity: String): WalletAnalysis = {
    val inOutsForId = sortedById.getOrElse(identity, Seq())
    new WalletAnalysis(identity, inOutsForId)
  }
}

class WalletAnalysis(val identity: String, val inOuts: Seq[InOut]) {
  lazy val balance: Long = inOuts.foldLeft(0)((acc, inOut) => acc + inOut.txOut.amount)
}
