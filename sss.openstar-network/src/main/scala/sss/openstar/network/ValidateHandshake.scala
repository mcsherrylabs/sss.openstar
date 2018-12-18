package sss.openstar.network

import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.Base64

import akka.util.ByteString
import com.google.common.primitives.Longs
import sss.ancillary.Logging
import sss.openstar.util.Results._
import ConnectionHandler._

import scala.util.{Failure, Success}

trait IdentityVerification {
  val nodeId: String
  val tag: String
  def sign(msg: Array[Byte]): Array[Byte]
  def verify(sig: Array[Byte],
             msg: Array[Byte],
             nodeId: String,
             tag: String): OkResult
}

object ValidateHandshake {
  def apply(netInf: NetworkInterface, handshakeVerifier: IdentityVerification)(
      remote: InetSocketAddress): HandshakeStep = {
    new ValidateHandshake(remote, netInf, handshakeVerifier).initialStep()
  }
}
class ValidateHandshake(remote: InetSocketAddress,
                        netInf: NetworkInterface,
                        handshakeVerifier: IdentityVerification)
    extends Logging {

  private lazy val myNonce = new SecureRandom().nextLong()
  private lazy val myChallengeHandshake =
    ByteString(createHandshake(myNonce).bytes)

  def initialStep(): HandshakeStep = {
    BytesToSend(myChallengeHandshake, WaitForBytes(processReturnedHandshake))
  }

  @volatile
  private var hisHandshakeHasBeenSigned: Option[NodeId] = None
  @volatile
  private var ourHandshakeHasBeenCorrectlySignedByHim: Boolean = false

  def processReturnedHandshake(data: ByteString): HandshakeStep = {
    Handshake.parse(data.toArray) match {
      case Success(shake) =>
        if (shake.fromNonce == myNonce) {
          // this is my nonce returned, check he signed it correctly.
          val handshakeGood: OkResult = verifyHandshake(shake)
          if (handshakeGood.isOk) {

            logDelay(shake)
            ourHandshakeHasBeenCorrectlySignedByHim = true

            if (hisHandshakeHasBeenSigned.isDefined)
              ConnectionEstablished(hisHandshakeHasBeenSigned.get)
            else
              // must wait for his shake to arrive so we can sign it
              WaitForBytes(processReturnedHandshake)

          } else {
            handshakeGood.errors.foreach(log.warn(_))
            log.info(s"Got a bad handshake from ${remote}, closing.")
            RejectConnection
          }

        } else {
          val signedHandshakeBytes = signHisHandshake(shake)
          hisHandshakeHasBeenSigned = Option(NodeId(shake.nodeId, remote))

          val nextStep =
            if (ourHandshakeHasBeenCorrectlySignedByHim)
              ConnectionEstablished(hisHandshakeHasBeenSigned.get)
            else
              WaitForBytes(processReturnedHandshake)

          BytesToSend(signedHandshakeBytes, nextStep)
        }

      case Failure(e) =>
        log.info(s"Error parsing a handshake: $e")
        RejectConnection
    }
  }

  private def logDelay(shake: Handshake) = log.whenDebugEnabled {
    val delay = (System.currentTimeMillis() / 1000) - shake.time
    log.debug(
      s"Got a Handshake from ${shake.nodeId} (${remote}), delay in s is $delay")
  }

  private def verifyHandshake(shake: Handshake) = {

    handshakeVerifier.verify(shake.sig.toArray,
                             Longs.toByteArray(shake.fromNonce),
                             shake.nodeId,
                             shake.tag) andThen
      isApplicationVersionCompatible(shake.applicationVersion)

  }

  private def logShakeSig(signedShake: Handshake) = {
    log.whenDebugEnabled {
      val sigStr = Base64.getEncoder.encodeToString(signedShake.sig.toArray)
      log.debug(
        s"Signing ${signedShake.fromNonce} ${signedShake.nodeId}, ${signedShake.tag}, ${sigStr}")
    }
  }

  private[network] def isApplicationVersionCompatible(
      thatAppVer: ApplicationVersion): OkResult = {
    (thatAppVer.firstDigit == netInf.appVersion.firstDigit)
      .orErrMsg(
        s"First digits not matching ${thatAppVer.firstDigit} != ${netInf.appVersion.firstDigit}") andThen
      (thatAppVer.secondDigit == netInf.appVersion.secondDigit)
        .orErrMsg(
          s"Second digits not matching ${thatAppVer.secondDigit} != ${netInf.appVersion.secondDigit}")
  }

  private def createHandshake(nonce: Long,
                              mySig: ByteString = ByteString()): Handshake = {
    handshakeTemplate.copy(fromNonce = nonce,
                           sig = mySig,
                           time = System.currentTimeMillis() / 1000)
  }

  private def signHisHandshake(shake: Handshake): ByteString = {
    val mySig =
      handshakeVerifier.sign(Longs.toByteArray(shake.fromNonce))
    val signedShake =
      createHandshake(shake.fromNonce, ByteString(mySig))
    logShakeSig(signedShake)
    ByteString(signedShake.bytes)
  }

  private lazy val handshakeTemplate = Handshake(
    netInf.appName,
    netInf.appVersion,
    handshakeVerifier.nodeId,
    handshakeVerifier.tag,
    new SecureRandom().nextLong,
    ByteString(),
    0 // it's a template, no point in using real time.
  )

}
