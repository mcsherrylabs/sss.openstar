package sss.asado

import akka.actor.ActorSystem
import com.typesafe.config.Config
import sss.ancillary.{Configure, Logging}
import sss.asado.account.{NodeIdentity, PrivateKeyAccount, PublicKeyAccount}
import sss.asado.balanceledger.{BalanceLedger, StandardTx, TxIndex, TxInput, TxOutput}
import sss.asado.block.BlockChain
import sss.asado.identityledger.Claim
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.ledger._
import sss.asado.network.{IncomingSerializedMessage, NetSend, SerializedMessage}
import sss.asado.nodebuilder._
import sss.asado.network.TestMessageEventBusOps._

import scala.util.Random


object TestUtils {

  implicit val actorSystem: ActorSystem = ActorSystem("AsadoTests")


  def addEmptyBlock(balanceLedger: BalanceLedger,
                  nodeIdentity: NodeIdentity,
                  bc: BlockChain): Unit = {

    val bHeader = bc.lastBlockHeader

    val b = bc.block(bHeader.height + 1)

    bc.closeBlock(bHeader)
    bc.sign(nodeIdentity, bHeader)

  }

  def addOneBlock(balanceLedger: BalanceLedger,
                  nodeIdentity: NodeIdentity,
                  bc: BlockChain, size: Int = 10): Unit = {

    val bHeader = bc.lastBlockHeader

    val b = bc.block(bHeader.height + 1)

    def writeBlock(height: Long, size: Int): Unit = {

      def gen(size: Int): Unit = {

        /*val ins = Seq(input)
        val outs = Seq(TxOutput(input.amount - 1, NullEncumbrance), TxOutput(1, NullEncumbrance))
        val tx = StandardTx(ins, outs)
        val stx = SignedTxEntry(tx.toBytes, Seq(Seq(nodeIdentity.sign(tx.txId))))

        val outItem = LedgerItem(MessageKeys.BalanceLedger, stx.txId, stx.toBytes)
        b.write(outItem)

        val outIn = TxInput(TxIndex(outItem.txId, 0), input.amount - 1, PrivateKeySig)
        if(size != 0) {
          gen(outIn, size - 1)
        }*/

        def createRandomClaimString() = s"${System.currentTimeMillis()}"

        val key = PrivateKeyAccount(DummySeedBytes).publicKey
        val claim = Claim(createRandomClaimString() , key)
        val ste = SignedTxEntry(claim.toBytes)
        val le = LedgerItem(MessageKeys.IdentityLedger, claim.txId, ste.toBytes)
        b.write(le)
        if(size != 0) gen(size - 1)

      }

      //val start = balanceLedger.coinbase(nodeIdentity, BlockId(height, 0), MessageKeys.BalanceLedger).get
      //val cbTx = start.txEntryBytes.toSignedTxEntry
      ///val startIn = TxInput(TxIndex(cbTx.txId, 0), 100, PrivateKeySig)
      //b.write(start)

      gen(size - 1)
      bc.closeBlock(bHeader)

    }

    writeBlock(b.height, size)
  }
}

trait BaseTestSystem extends MessageEventBusBuilder
  with DecoderBuilder
  with RequireGlobalChainId
  with RequireSeedBytes
  with DbBuilder
  with RequireConfig
  with Configure
  with Logging
  with RequireActorSystem
  with NodeIdentityBuilder
  with RequireNetSend
  with RequirePhrase {

  override val phrase = Option("password")

  lazy override val seedBytes = DummySeedBytes

}

trait TestSystem1 extends BaseTestSystem {
  lazy override implicit val actorSystem: ActorSystem = ActorSystem("AsadoTests1")

  val nodeId: UniqueNodeIdentifier = "testSystem1"
  lazy override val conf: Config = config(nodeId.toString)

  def sendF: NetSend = (msg, node) => {
    msg match {

      case SerializedMessage(chainId, msgCode, data) =>
        testSystem2.messageEventBus.simulateNetworkMessage(IncomingSerializedMessage(nodeId,
          SerializedMessage(chainId, msgCode, data)))

    }
  }

  implicit override val send: Send = Send(sendF)

  val testSystem2: BaseTestSystem
}

trait TestSystem2 extends BaseTestSystem {
  lazy override implicit val actorSystem: ActorSystem = ActorSystem("AsadoTests2")

  val nodeId: UniqueNodeIdentifier = "testSystem2"
  lazy override val conf: Config = config(nodeId.toString)

  def sendF: NetSend = (msg, node) => {
    msg match {

      case SerializedMessage(chainId, msgCode, data) =>
        testSystem1.messageEventBus.simulateNetworkMessage(IncomingSerializedMessage(nodeId,
          SerializedMessage(chainId, msgCode, data)))

    }
  }

  implicit override val send: Send = Send(sendF)

  val testSystem1: BaseTestSystem


}

