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
  val dns: String
}

trait HomeDomain extends HomeDomainConfig {
  lazy val http: String = s"http://${dns}:${httpPort}"
  lazy val nodeId = NodeId(identity, new InetSocketAddress(dns, tcpPort.toInt))
  override def toString: String = s"${nodeId} ${dns} http:${httpPort} tcp:${tcpPort}"
}
