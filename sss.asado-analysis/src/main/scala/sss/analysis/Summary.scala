package sss.analysis


import akka.agent.Agent
import com.vaadin.ui._
import sss.analysis.DashBoard.Status
import sss.ui.StatisticsChart
import sss.ui.reactor.UIReactor

/**
  * Created by alan on 10/27/16.
  */
class Summary(uiReactor: UIReactor, status: Agent[Status]) extends VerticalLayout {


  def update: Unit = {
    val s = status.get()
    val blockAnalysis = s.lastAnalysis
    setBalance(blockAnalysis.balance)
    setBlockCount(blockAnalysis.analysisHeight)
    setIdentitiesCount(s.numIds)
    setTxCount(blockAnalysis.txCount)
    setChainHeight(s.chainHeight)
    setConnected(s.whoConnectedTo)
  }

  private def makeLhsLabel(name: String, row: Int) = {
    val lbl = new Label(name)
    grid.addComponent(lbl, 0, row)
    grid.setComponentAlignment(lbl, Alignment.MIDDLE_RIGHT)
    lbl
  }

  private def makeRhsValue(name: String, row: Int) = {
    val lbl = new Button(name)
    lbl.addClickListener(uiReactor)

    grid.addComponent(lbl, 1, row)
    grid.setComponentAlignment(lbl, Alignment.MIDDLE_LEFT)
    lbl
  }

  val panel = new Panel("Asado Statistics")

  val grid = new GridLayout(2, 8)

  grid.setSpacing(true)
  grid.setMargin(true)
  setMargin(true)
  setSpacing(true)

  //grid.setWidth(400, Sizeable.Unit.PIXELS)
  //grid.setHeight(200, Sizeable.Unit.PIXELS)

  val blocksBtnLbl = makeLhsLabel("Analysed Blocks", 0)
  val balanceBtnLbl = makeLhsLabel("Ledger Balance", 1)
  val identitiesBtnLbl = makeLhsLabel("Identities", 2)
  val txsBtnLbl = makeLhsLabel("Txs", 3)
  val connectedLbl = makeLhsLabel("Connected", 4)
  val chainHeightLbl = makeLhsLabel("Chain Height", 5)

  val numBlocksLbl = makeRhsValue("10", 0)

  val balanceLbl = makeRhsValue("0", 1)
  balanceLbl.setEnabled(false)

  val identitiesLbl = makeRhsValue("0", 2)
  val txsLbl = makeRhsValue("0", 3)
  txsLbl.setEnabled(false)

  val connectedRhs = makeRhsValue("Not connected", 4)
  connectedRhs.setEnabled(false)

  val chainHeightRhs = makeRhsValue("Unknown", 5)
  chainHeightRhs.setEnabled(false)

  setCaption("Asado Statistics")
  panel.setContent(grid)
  addComponents(panel)

  private def setBlockCount(count: Long) = numBlocksLbl.setCaption(count.toString)
  private def setTxCount(count: Long) = txsLbl.setCaption(count.toString)
  private def setIdentitiesCount(count: Long) = identitiesLbl.setCaption(count.toString)
  private def setBalance(bal: Long) = balanceLbl.setCaption(bal.toString)
  def setConnected(info: String) = connectedRhs.setCaption(info)
  def setChainHeight(height: Long) = chainHeightRhs.setCaption(height.toString)
}
