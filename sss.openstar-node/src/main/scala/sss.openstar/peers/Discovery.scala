package sss.openstar.peers

import java.net.InetSocketAddress

import akka.util.ByteString
import sss.db.{Db, Row, Where, where}
import sss.openstar.{OpenstarEvent, UniqueNodeIdentifier}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.network.NodeId
import sss.openstar.peers.Discovery.{DiscoveredNode, Hash}

import scala.util.Try


object Discovery {

  type Hash = Array[Byte] => Array[Byte]
  case class DiscoveredNode(nodeId: NodeId, capabilities: GlobalChainIdMask) extends OpenstarEvent {
    lazy val hash: ByteString = nodeId.hash ++ ByteString(capabilities)
  }
}

class Discovery(hasher: Hash)(implicit db: Db) {

  private val discoveryTableName = "discovery"
  private val nIdCol = "nid_col"
  private val addrCol = "addr_col"
  private val portCol = "port_col"
  private val capCol = "cap_col"
  private val idCol = "id"

  private val createTableSql =
    s"""CREATE TABLE IF NOT EXISTS $discoveryTableName
       |($idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
       |$nIdCol VARCHAR(100),
       |$addrCol BINARY(16),
       |$portCol INT NOT NULL,
       |$capCol BINARY(1) NOT NULL,
       |PRIMARY KEY(id), UNIQUE($addrCol));
       |""".stripMargin

  private val indx = s"CREATE INDEX IF NOT EXISTS ${discoveryTableName}_indx ON $discoveryTableName ($addrCol, $capCol);"

  lazy val discoveryTable = {
    db.executeSqls(Seq(createTableSql, indx))
    db.table(discoveryTableName)
  }

  def find(nodesToIgnore: Set[UniqueNodeIdentifier], numConns: Int, caps: GlobalChainIdMask): Seq[DiscoveredNode] = {
    viewToDiscoveredNodes(
      where(capCol -> caps) and where(nIdCol) notIn nodesToIgnore limit numConns
    )
  }

  def lookup(names: Set[UniqueNodeIdentifier]): Seq[DiscoveredNode] = {
    viewToDiscoveredNodes(where(nIdCol) in names)
  }

  private def viewToDiscoveredNodes(filter: Where): Seq[DiscoveredNode] = {
    discoveryTable
      .map(
        rowToDiscoveredNode, filter
      )
  }

  private def rowToDiscoveredNode(r:Row): DiscoveredNode = {
    val inet = r[Array[Byte]](addrCol).toInetAddress
    val sock = new InetSocketAddress(inet, r[Int](portCol))
    DiscoveredNode(NodeId(r[String](nIdCol), sock), r[Byte](capCol))
  }

  private[peers] def purge(supportedChains: GlobalChainIdMask): Int = {
    discoveryTable delete where(capCol -> supportedChains)
  }

  private[peers] def insert(nodeId:NodeId, supportedChains: GlobalChainIdMask): Try[DiscoveredNode] = {

    Try(discoveryTable insert
      Map(
        nIdCol -> nodeId.id,
        addrCol -> nodeId.inetSocketAddress.getAddress.toBytes,
        portCol -> nodeId.inetSocketAddress.getPort,
        capCol -> supportedChains)
    ) map rowToDiscoveredNode
  }

  def query(start: Int, pageSize: Int): (Seq[DiscoveredNode], ByteString) = {
    val nodes = viewToDiscoveredNodes (where(s"$idCol > ?", start) limit pageSize)
    val hashed = nodes.foldLeft(Array.emptyByteArray)((acc, e) => hasher(acc ++ e.hash))
    (nodes, ByteString(hashed))
  }

}
