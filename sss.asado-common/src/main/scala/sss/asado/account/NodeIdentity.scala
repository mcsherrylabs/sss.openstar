package sss.asado.account

import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions.{MessageToSign, PublicKey, SharedSecret, Signature}

import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 3/21/16.
  */

trait NodeIdentity {
  val id: String
  val tag: String
  val publicKey: PublicKey
  def sign(msg: MessageToSign): Signature
  def verify(sig: Signature, msg: Array[Byte]): Boolean
  def createSharedSecret(publicKey: PublicKey): SharedSecret
}

object NodeIdentity {

  val nodeIdKey = "nodeId"
  val tagKey = "tag"

  def keyExists(identity: String, tag: String): Boolean = {
    KeyPersister.keyExists(identity, tag)
  }

  def deleteKey(identity: String, tag: String) = KeyPersister.deleteKey(identity, tag)

  def unlockNodeIdentityFromConsole(nodeConfig: Config): NodeIdentity = {
    unlockNodeIdentityFromConsole(nodeConfig.getString(nodeIdKey), nodeConfig.getString(tagKey))
  }

  def unlockNodeIdentityFromConsole(identity: String, tag: String): NodeIdentity = {
    println("Unlock key phrase:")
    val phrase = Option(System.console()) match {
      case None =>
        println("WARNING No system console found, the password may echo to the console")
        StdIn.readLine
      case Some(standardIn) =>
        val chars = standardIn.readPassword
        new String(chars)
    }

    Try(NodeIdentity(identity, tag, phrase)) match {
      case Success(nodeIdentity) => nodeIdentity
      case Failure(e) => unlockNodeIdentityFromConsole(identity, tag)
    }
  }

  def apply(nodeConfig: Config, phrase: String): NodeIdentity =
    apply(nodeConfig.getString(nodeIdKey), nodeConfig.getString(tagKey), phrase)

  def apply(nodeId: String, tagOfNodeKey: String, phrase: String): NodeIdentity = {
    val nodeKey = new KeyPersister(nodeId, true, phrase, tagOfNodeKey).account
    new NodeIdentity {
      override def verify(sig: Signature, msg: Array[Byte]): Boolean = nodeKey.verify(sig, msg)
      override def sign(msg: MessageToSign): Signature = nodeKey.sign(msg)
      override def createSharedSecret(publicKey: PublicKey): SharedSecret = nodeKey.getSharedSecret(publicKey)
      override val publicKey: PublicKey = nodeKey.publicKey
      override val id: String = nodeId
      override val tag: String = tagOfNodeKey

    }
  }

}

