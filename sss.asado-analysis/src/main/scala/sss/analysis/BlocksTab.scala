package sss.analysis

import java.util.concurrent.atomic.AtomicLong

import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import org.joda.time.format.DateTimeFormat
import sss.analysis.Analysis.InOut
import sss.analysis.AnalysisMessages.Message
import sss.asado.nodebuilder.ClientNode

import scala.util.{Failure, Success, Try}


/**
  * Created by alan on 10/27/16.
  */
class BlocksTab(clientNode: ClientNode) extends VerticalLayout {

  import clientNode.db
  private val panel = new Panel("Asado Block")
  private val maxRows = 8
  private val grid = new GridLayout(2, maxRows)

  val dateFormat =  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")
  private val currentBlockHeight = new AtomicLong(0)
  grid.setSpacing(true)
  grid.setMargin(true)
  setMargin(true)
  setSpacing(true)
  panel.setContent(grid)
  val prev = new Button("Previous")
  val next = new Button("Next")
  val goBtn = new Button("Go!")
  val goToBlock = new TextField()

  val numInBlock = new Label("")
  val numInBlockLbl = new Label("Number Txs")
  val balanceLbl = new Label("Balance of ledger")
  val balance = new Label("")
  val coinbaseLbl = new Label("Coinbase Total")
  val coinbase = new Label("")
  val txOutsLbl = new Label("TxOut Total")
  val txOuts = new Label("")

  coinbaseLbl.setWidth("250px")
  coinbase.setWidth("1200px")
  grid.addComponent(prev, 0, 0)

  val l = new HorizontalLayout()
  l.setWidth("600px")
  l.addComponent(next)
  l.addComponent(goToBlock)
  l.addComponent(goBtn)

  grid.addComponent(l, 1, 0)
  grid.addComponent(numInBlockLbl, 0, 1)
  grid.addComponent(numInBlock, 1, 1)
  grid.addComponent(txOutsLbl, 0, 2)
  grid.addComponent(txOuts, 1, 2)

  grid.addComponent(balanceLbl, 0, 3)
  grid.addComponent(balance, 1, 3)
  grid.addComponent(coinbaseLbl, 0, 4)
  grid.addComponent(coinbase, 1, 4)

  grid.setColumnExpandRatio(0, 0)
  grid.setColumnExpandRatio(1, 1)

  next.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      val next = currentBlockHeight.get() + 1
      update(next)
    }
  })

  prev.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = update(currentBlockHeight.get() - 1)
  })

  goBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      Try(goToBlock.getValue.toLong) match {
        case Success(bh) => {
          if(bh < 2 || bh > clientNode.bc.lastBlockHeader.height) Notification.show(s"Could not go to block number ${goToBlock.getValue}!")
          else update(bh)
        }
        case Failure(_) => Notification.show(s"Could not go to block number ${goToBlock.getValue}!")
      }
    }
  })
  addComponent(panel)

  // num txs
  // outs after finished

  // value at end
  def update(blockHeight:Long) = {

    def println(msg: String) = {
      grid.insertRow(grid.getRows)
      val tf = new TextField()
      tf.setWidth("100%")
      tf.setValue(msg)
      tf.setReadOnly(true)
      grid.addComponent(tf, 1, grid.getRows - 1)
    }

    while(grid.getRows > maxRows) grid.removeRow(grid.getRows - 1)
    currentBlockHeight.set(blockHeight)

    clientNode.bc.blockHeaderOpt(blockHeight) match {
      case None => println(s"No block header for $blockHeight!")
      case Some(header) =>

        panel.setCaption(s"Asado Block $blockHeight")
        numInBlock.setValue(header.numTxs.toString)
        if(Analysis.isAnalysed(blockHeight)) {
          val analysis = Analysis.load(blockHeight)
          balance.setValue(analysis.balance.toString)
          coinbase.setValue(analysis.coinbaseTotal.toString)
          txOuts.setValue(analysis.txOuts.size.toString)
          new AnalysisMessages(blockHeight).apply.foreach(m => println(format(m)))
        } else println("No analysis exists for this block")

    }

    clientNode.bc.blockHeaderOpt(blockHeight + 1) match {
      case None => next.setEnabled(false)
      case Some(_) => next.setEnabled(true)
    }

    clientNode.bc.blockHeaderOpt(blockHeight - 1) match {
      case None => prev.setEnabled(false)
      case Some(_) => prev.setEnabled(true)
    }
  }

  private def format(msg: Message): String = {
    s"${dateFormat.print(msg.at)} ${msg.msgType} ${msg.blockHeight} ${msg.msg}"
  }
}
