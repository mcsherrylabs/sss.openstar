package sss.openstar.tools

import us.monoid.web.Resty


/**
  * Created by alan on 6/7/16.
  */
object DebitWalletToIdentity {

  def main(args: Array[String]) {

    if(args.length == 3) {
      val ledgerUrl = args(0)
      val identity = args(1)
      val amount = args(2)

      val result = new Resty().text(s"$ledgerUrl/debit?to=$identity&amount=$amount")
      println(result)

    } else println("Provide the url to debit from and the identity to claim and the amount.")
  }
}
