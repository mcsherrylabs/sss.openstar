package sss.asado.handshake

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.NetworkTestFixtures
import sss.asado.network.ConnectionHandler.{BytesToSend, ConnectionEstablished, WaitForBytes}
import sss.asado.network.{ApplicationVersion, NodeId}
import sss.asado.util.Results._

class ValidationHandshakeSpec
    extends FlatSpec
    with Matchers
    with HandshakeFixtures {

  "ValidateHandshake " should " get through happy path " in {

    val remoteNodeId = "remoteNodeId"
    val sut = new ValidateHandshake(remote, networkInterface, handshakeVerifier)
    val BytesToSend(hs, WaitForBytes(handler)) = sut.initialStep()
    val WaitForBytes(handler2) = handler(hs)
    val shake = Handshake.parse(hs.toArray).get
    val sentNonce =
      shake.copy(fromNonce = shake.fromNonce + 1, nodeId = remoteNodeId)
    val BytesToSend(_,
                    ConnectionEstablished(NodeId(`remoteNodeId`, `remote`))) =
      handler2(ByteString(sentNonce.bytes))

  }

  it should "  succeed even if handshakes returned in reverse " in {

    val remoteNodeId = "remoteNodeId"
    val sut = new ValidateHandshake(remote, networkInterface, handshakeVerifier)
    val BytesToSend(hs, WaitForBytes(handler)) = sut.initialStep()
    val shake = Handshake.parse(hs.toArray).get
    val sentNonce =
      shake.copy(fromNonce = shake.fromNonce + 1, nodeId = remoteNodeId)
    val BytesToSend(_, WaitForBytes(handler2)) =
      handler(ByteString(sentNonce.bytes))
    val ConnectionEstablished(NodeId(`remoteNodeId`, `remote`)) = handler2(hs)

  }

  "ValidationHandshake" should " check application version " in {
    val sut = new ValidateHandshake(remote, networkInterface, handshakeVerifier)
    assert(
      sut.isApplicationVersionCompatible(ApplicationVersion(appVer234)).isOk)
    assert(
      !sut.isApplicationVersionCompatible(ApplicationVersion(appVer334)).isOk)
    assert(
      sut.isApplicationVersionCompatible(ApplicationVersion(appVer235)).isOk)
    assert(
      !sut.isApplicationVersionCompatible(ApplicationVersion(appVer111)).isOk)

  }

}
