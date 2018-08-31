package sss.asado.network.testserver

import java.net.InetSocketAddress

import akka.util.ByteString
import sss.ancillary.Logging
import sss.asado.network.ConnectionHandler._
import sss.asado.network.{Handshake, NetworkInterface, NodeId}

import scala.util.{Failure, Random, Success}

object SimpleTestHandshake {

  def apply(netInf: NetworkInterface, nodeId: String, nonce: Long = Random.nextLong())(
      remote: InetSocketAddress
      ): HandshakeStep = {
    new SimpleTestHandshake(netInf, nodeId, nonce, remote).initialStep
  }
}

class SimpleTestHandshake(netInf: NetworkInterface,
                          nodeId: String,
                          myNonce: Long,
                          remote: InetSocketAddress)
    extends Logging {

  private lazy val myChallengeHandshake =
    ByteString(createHandshake(myNonce).bytes)

  def initialStep: HandshakeStep = {
    BytesToSend(myChallengeHandshake, WaitForBytes(processReturnedHandshake))
  }

  @volatile
  private var hisHandshakeHasBeenSigned: Option[NodeId] = None
  @volatile
  private var ourHandshakeHasBeenCorrectlySignedByHim: Boolean = false

  def processReturnedHandshake(data: ByteString): HandshakeStep = {
    val result = Handshake.parse(data.toArray) match {
      case Success(shake) =>
        if (shake.fromNonce == myNonce) {

          ourHandshakeHasBeenCorrectlySignedByHim = true

          if (hisHandshakeHasBeenSigned.isDefined) {
            ConnectionEstablished(hisHandshakeHasBeenSigned.get)
          } else
            // must wait for his shake to arrive so we can sign it
            WaitForBytes(processReturnedHandshake)

        } else {

          hisHandshakeHasBeenSigned = Option(NodeId(shake.nodeId, remote))

          val nextStep =
            if (ourHandshakeHasBeenCorrectlySignedByHim) {
              ConnectionEstablished(hisHandshakeHasBeenSigned.get)
            } else
              WaitForBytes(processReturnedHandshake)

          BytesToSend(data, nextStep)
        }

      case Failure(t) =>
        log.info(s"Error parsing a handshake: $t")
        RejectConnection
    }

    result
  }

  private def createHandshake(nonce: Long,
                              mySig: ByteString = ByteString()): Handshake = {
    handshakeTemplate.copy(fromNonce = nonce,
                           sig = mySig,
                           time = System.currentTimeMillis() / 1000)
  }

  private lazy val handshakeTemplate = {

    Handshake(
      netInf.appName,
      netInf.appVersion,
      nodeId,
      "testTag",
      0,
      ByteString(),
      0 // it's a template, no point in using real time.
    )
  }

}
