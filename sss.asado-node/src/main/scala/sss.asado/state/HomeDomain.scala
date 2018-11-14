package sss.asado.state

import java.net.InetSocketAddress

import sss.asado.network.NodeId

/**
  * Created by alan on 7/2/16.
  */

trait HomeDomainConfig {
  val identity: String
  val httpPort: Int
  val tcpPort: Int
  val fallbackIp: String
}

trait HomeDomain extends HomeDomainConfig {
  lazy val http: String = s"http://${fallbackIp}:${httpPort}"
  lazy val nodeId = NodeId(identity, new InetSocketAddress(fallbackIp, tcpPort.toInt))
  override def toString: String = s"${nodeId} ${fallbackIp} http:${httpPort} tcp:${tcpPort}"
}
