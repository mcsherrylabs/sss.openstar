package sss.asado.network

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorRef
import sss.asado.UniqueNodeIdentifier
import sss.asado.network.NetworkControllerActor._

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}

/**
  * An reference to the working network interface
  * Use to manage the network.
  *
  * @param networkController
  * @param stopFuture
  */
class NetworkRef private[network] (networkController: ActorRef,
                                   connectionsRef: AtomicReference[Set[Connection]],
                                   stopFuture: Promise[Unit]) {

  def send(msg: SerializedMessage, nIds: Set[UniqueNodeIdentifier]): Unit = {
    require(nIds.nonEmpty, s"Programmer error sending SerializedMessage to zero recipients! ($msg)")
    nIds foreach (nId => networkController ! SendToNodeId(msg, nId))
  }

  def send(msg: SerializedMessage, nId: UniqueNodeIdentifier): Unit =
    networkController ! SendToNodeId(msg, nId)

  def connect(nId: NodeId,
              reconnectionStrategy: ReconnectionStrategy =
                NoReconnectionStrategy): Unit = {

    networkController ! ConnectTo(nId: NodeId, reconnectionStrategy)
  }

  def disconnect(nodeId: UniqueNodeIdentifier): Unit = {
    networkController ! Disconnect(nodeId)
  }

  def blacklist(id: String, duration: Duration) = {
    networkController ! BlackList(id, duration)
  }

  def blacklist(inetAddress: InetAddress, duration: Duration) = {
    networkController ! BlackListAddr(inetAddress, duration)
  }

  def unBlacklist(id: UniqueNodeIdentifier) = {
    networkController ! UnBlackList(id)
  }

  def unBlacklist(inetAddress: InetAddress) = {
    networkController ! UnBlackListAddr(inetAddress)
  }

  def stop(): Future[Unit] = {

    if (!stopFuture.isCompleted) {
      networkController ! ShutdownNetwork
    }

    stopFuture.future
  }

  def connections(): Set[Connection] = {
    connectionsRef.get()
  }
}
