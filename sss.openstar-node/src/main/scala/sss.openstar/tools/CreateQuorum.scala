package sss.openstar.tools

import sss.openstar.quorumledger.QuorumService
import sss.openstar.nodebuilder.{ConfigBuilder, DbBuilder, RequireConfig}


/**
  * Created by alan on 6/7/16.
  */
object CreateQuorum {

  def main(args: Array[String]) {

    println("This will create a new Quorum table and make the given id the owner")
    println("All changes to the quorum must be signed by the new owner")
    println("The owning id must exist in the identity ledger, but this is not checked here.")

    if(args.length == 3) {

      val cake = new ConfigBuilder
        with DbBuilder {

        override val configName: String = args(0)
        val chainId = args(1).toByte
        val identity = args(2)

        val qs = QuorumService.create(chainId, identity)
        println(s"Members: ${qs.candidates()}" )
      }

    } else {
      println("")
      println("Provide (1) the config root to use, (2) " +
        "the chainId of the new chain from and (3) the owning identity.")
    }
  }
}
