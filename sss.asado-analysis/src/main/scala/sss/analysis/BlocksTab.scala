package sss.analysis

import java.util.concurrent.atomic.AtomicLong

import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.analysis.Analysis.InOut
import sss.asado.MessageKeys
import sss.asado.balanceledger.TxIndex
import sss.asado.block.Block
import sss.asado.balanceledger._
import sss.asado.identityledger._
import sss.asado.ledger._
import sss.asado.identityledger.Claim
import sss.asado.nodebuilder.ClientNode
import sss.asado.util.ByteArrayEncodedStrOps._

/**
  * Created by alan on 10/27/16.
  */
class BlocksTab(clientNode: ClientNode) extends VerticalLayout {

  val panel = new Panel("Asado Block")
  val maxRows = 8
  val grid = new GridLayout(2, maxRows)

  val currentBlockHeight = new AtomicLong(0)
  grid.setSpacing(true)
  grid.setMargin(true)
  setMargin(true)
  setSpacing(true)
  panel.setContent(grid)
  val prev = new Button("Previous")
  val next = new Button("Next")
  val numInBlock = new Label("")
  val numInBlockLbl = new Label("Number Txs")
  val balanceLbl = new Label("Balance of ledger")
  val balance = new Label("")

  grid.addComponent(prev, 0, 0)
  grid.addComponent(next, 1, 0)
  grid.addComponent(numInBlockLbl, 0, 1)
  grid.addComponent(numInBlock, 1, 1)
  grid.addComponent(balanceLbl, 0, 2)
  grid.addComponent(balance, 1, 2)


  next.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = update(currentBlockHeight.get() + 1)
  })

  prev.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = update(currentBlockHeight.get() - 1)
  })

  addComponent(panel)

  // num txs
  // outs after finished

  // value at end
  def update(blockHeight:Long) = {

    def println(msg: String) = {
      grid.insertRow(grid.getRows)
      grid.addComponent(new TextField(msg), 1, grid.getRows)
    }

    clientNode.bc.blockHeaderOpt(blockHeight) match {
      case None => Notification.show(s"No block header for $blockHeight!")
      case Some(header) =>
        while(grid.getRows > maxRows) grid.removeRow(grid.getRows)
        panel.setCaption(s"Asado Block $blockHeight")
        numInBlock.setValue(header.numTxs.toString)
        val analysis = Analysis(blockHeight - 1)
        val block = clientNode.bc.block(blockHeight)
        val allEntries = block.entries
        val result: Seq[InOut] = allEntries.foldLeft(analysis.txOuts)((acc, le) => {

          val e = le.ledgerItem.txEntryBytes.toSignedTxEntry
          if (!(e.txId sameElements le.ledgerItem.txId)) {
            println(s"${le.index} ${le.ledgerItem.ledgerId}" +
              s"${le.ledgerItem.txIdHexStr}=${e.txId.toBase64Str} Tx Entry has different txId to LedgerItem!")
          }

          le.ledgerItem.ledgerId match {
            case MessageKeys.IdentityLedger =>
              val msg = e.txEntryBytes.toIdentityLedgerMessage
              if(!(msg.txId sameElements le.ledgerItem.txId)) println("Id ledger txId mismatch")
              msg match {
                case Claim(id, pKey) =>
                case x =>
              }
              acc

            case MessageKeys.BalanceLedger =>

              val tx = e.txEntryBytes.toTx
              // are the tx ins in the list of txouts? yes? remove.
              //var newCoinbases: Seq[InOut] = Seq()

              tx.ins.foreach { in =>
                if(Analysis.isCoinBase(in)) {
                  println(s"Coinbase tx is ${tx.outs.head.amount}")
                  println(s"Coinbase tx has more than one output, ${tx.outs.size}")

                } else {
                  assert(acc.find(_.txIndex == in.txIndex).isDefined, s"TxIndex from nowhere ${in.txIndex}")
                }
              }

              val newOuts = acc.filterNot(index => tx.ins.find(_.txIndex == index.txIndex).isDefined)
              // add the tx outs to the list
              val plusNewOuts = tx.outs.indices.map { i =>
                //assert(tx.outs(i).amount > 0, "Why txOut is 0?")<-because the server charge can be 0
                val newIndx = TxIndex(tx.txId, i)
                InOut(newIndx,tx.outs(i))
              }

              plusNewOuts ++ newOuts
            case x =>
              println(s"Another type of ledger? $x")
              acc
          }

        })
        currentBlockHeight.set(blockHeight)
    }
  }

}
