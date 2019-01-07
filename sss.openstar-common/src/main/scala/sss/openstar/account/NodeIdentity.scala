package sss.openstar.account

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions.{MessageToSign, PublicKey, SharedSecret, Signature}
import sss.ancillary.Logging
import sss.openstar.crypto.SeedBytes

import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/**
  * Node identity binds a string identifier to a pair of public private keys.
  * The id is the main identifier, but the tag signifies a different pair of keys for
  * the same 'id'.
  */
trait NodeIdentity {

  val id: String
  val idBytes: Array[Byte]
  val tag: String
  val tagBytes: Array[Byte]

  val publicKey: PublicKey
  def sign(msg: MessageToSign): Signature
  def verify(sig: Signature, msg: Array[Byte]): Boolean
  def createSharedSecret(publicKey: PublicKey): SharedSecret
}

class NodeIdentityManager(seedBytes: SeedBytes) extends Logging {

  val nodeIdKey = "nodeId"
  val tagKey = "tag"

  implicit val keyGenerator: () => (Array[Byte], Array[Byte]) = () => {
    PrivateKeyAccount(seedBytes).tuple
  }

  def keyExists(identity: String, tag: String): Boolean = {
    KeyPersister.keyExists(identity, tag)
  }

  def deleteKey(identity: String, tag: String) = KeyPersister.deleteKey(identity, tag)

  def unlockNodeIdentityFromConsole(nodeIdTag: NodeIdTag): NodeIdentity = {

    println("Unlock key phrase:")
    val phrase = Option(System.console()) match {
      case None =>
        println("WARNING No system console found, the password may echo to the console")
        StdIn.readLine
      case Some(standardIn) =>
        val chars = standardIn.readPassword
        new String(chars)
    }

    Try(apply(nodeIdTag, phrase)) match {
      case Success(nodeIdentity) => nodeIdentity
      case Failure(e) => unlockNodeIdentityFromConsole(nodeIdTag)
    }
  }

  def apply(nodeId: String, tag: String, phrase: String): NodeIdentity =
    apply(NodeIdTag(nodeId, tag), phrase)

  def get(nodeId: String,
          tagOfNodeKey: String,
          phrase: String): Option[NodeIdentity] = {
    for {
      kys <- KeyPersister.get(nodeId, tagOfNodeKey, phrase)
      nodeKey = PrivateKeyAccount(kys)
    } yield apply(nodeKey, NodeIdTag(nodeId, tagOfNodeKey))
  }

  def apply(nodeKey: PrivateKeyAccount,
            nodeIdTag: NodeIdTag): NodeIdentity = {

    new NodeIdentity {
      override def verify(sig: Signature, msg: Array[Byte]): Boolean = nodeKey.verify(sig, msg)
      override def sign(msg: MessageToSign): Signature = nodeKey.sign(msg)
      override def createSharedSecret(publicKey: PublicKey): SharedSecret = nodeKey.getSharedSecret(publicKey)
      override val publicKey: PublicKey = nodeKey.publicKey
      override val id: String = nodeIdTag.nodeId
      override val tag: String = nodeIdTag.tag
      override val idBytes: Array[Byte] = id.getBytes(StandardCharsets.UTF_8)
      override val tagBytes: Array[Byte] = tag.getBytes(StandardCharsets.UTF_8)
    }
  }
  def apply(
             nodeIdTag: NodeIdTag,
             phrase: String
            ): NodeIdentity = {
    val nodeKey = PrivateKeyAccount(KeyPersister(nodeIdTag.nodeId, nodeIdTag.tag, phrase, keyGenerator ))
    apply(nodeKey,nodeIdTag)
  }

}

