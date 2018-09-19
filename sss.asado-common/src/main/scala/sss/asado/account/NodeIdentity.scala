package sss.asado.account

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions.{MessageToSign, PublicKey, SharedSecret, Signature}
import sss.asado.crypto.SeedBytes

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

class NodeIdentityManager(seedBytes: SeedBytes) {

  val nodeIdKey = "nodeId"
  val tagKey = "tag"

  implicit val keyGenerator: () => (Array[Byte], Array[Byte]) = () => {
    PrivateKeyAccount(seedBytes).tuple
  }

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

    Try(apply(identity, tag, phrase)) match {
      case Success(nodeIdentity) => nodeIdentity
      case Failure(e) => unlockNodeIdentityFromConsole(identity, tag)
    }
  }

  def apply(nodeConfig: Config, phrase: String): NodeIdentity =
    apply(nodeConfig.getString(nodeIdKey), nodeConfig.getString(tagKey), phrase)

  def get(nodeId: String,
          tagOfNodeKey: String,
          phrase: String): Option[NodeIdentity] = {
    for {
      kys <- KeyPersister.get(nodeId, tagOfNodeKey, phrase)
      nodeKey = PrivateKeyAccount(kys)
    } yield (apply(nodeKey, nodeId, tagOfNodeKey))
  }

  def apply(nodeKey: PrivateKeyAccount,
            nodeId: String,
            tagOfNodeKey: String): NodeIdentity = {
    new NodeIdentity {
      override def verify(sig: Signature, msg: Array[Byte]): Boolean = nodeKey.verify(sig, msg)
      override def sign(msg: MessageToSign): Signature = nodeKey.sign(msg)
      override def createSharedSecret(publicKey: PublicKey): SharedSecret = nodeKey.getSharedSecret(publicKey)
      override val publicKey: PublicKey = nodeKey.publicKey
      override val id: String = nodeId
      override val tag: String = tagOfNodeKey
      override val idBytes: Array[Byte] = id.getBytes(StandardCharsets.UTF_8)
      override val tagBytes: Array[Byte] = tag.getBytes(StandardCharsets.UTF_8)
    }
  }
  def apply(
             nodeId: String,
             tagOfNodeKey: String,
             phrase: String
            ): NodeIdentity = {
    val nodeKey = PrivateKeyAccount(KeyPersister(nodeId, tagOfNodeKey, phrase, keyGenerator ))
    apply(nodeKey,nodeId,tagOfNodeKey)
  }

}

