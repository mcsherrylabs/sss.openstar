package sss.asado


import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.account.PublicKeyAccount
import sss.asado.identityledger.serialize._
import sss.asado.util.ByteArrayComparisonOps._
import sss.asado.util.Serialize.ToBytes
import sss.asado.util.hash.SecureCryptographicHash

/**
  * Created by alan on 5/31/16.
  */
package object identityledger {

  abstract class IdentityLedgerTx(toCheck: String*) {
    val txId: Array[Byte] = SecureCryptographicHash.hash(this.toBytes)
    require(toCheck.forall(IdentityService.validateIdentity(_)),
      s"Unsupported character used, simple lowercase alpha numerics and _ only. ($toCheck)")
    private[identityledger] val uniqueMessage = System.nanoTime()
  }

  private[identityledger] val ClaimCode = 1.toByte
  private[identityledger] val LinkCode = 2.toByte
  private[identityledger] val UnLinkByKeyCode = 3.toByte
  private[identityledger] val UnLinkCode = 4.toByte
  private[identityledger] val RescueCode = 5.toByte
  private[identityledger] val LinkRescuerCode = 6.toByte
  private[identityledger] val UnLinkRescuerCode = 7.toByte

  case class Claim(identity: String, pKey: PublicKey) extends IdentityLedgerTx(identity) {
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: Claim =>
          that.identity == identity &&
          that.uniqueMessage == uniqueMessage &&
            (that.pKey isSame pKey)
        case _ => false
      }
    }

    override def hashCode(): Int = uniqueMessage.hashCode
  }

  case class Link(identity: String, pKey: PublicKey, tag: String) extends IdentityLedgerTx(identity) {
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: Link =>
          that.identity == identity &&
            that.tag == tag &&
            that.uniqueMessage == uniqueMessage &&
            (that.pKey isSame pKey)
        case _ => false
      }
    }

    override def hashCode(): Int = uniqueMessage.hashCode
  }

  case class Rescue(rescuer: String, identity: String, pKey: PublicKey, tag: String)
    extends IdentityLedgerTx(identity, rescuer) {

    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: Rescue =>
          that.identity == identity &&
          that.rescuer == rescuer &&
            that.tag == tag &&
            that.uniqueMessage == uniqueMessage &&
            (that.pKey isSame pKey)
        case _ => false
      }
    }

    override def hashCode(): Int = uniqueMessage.hashCode
  }

  case class LinkRescuer(rescuer: String, identity: String) extends IdentityLedgerTx(rescuer, identity)
  case class UnLinkRescuer(rescuer: String, identity: String) extends IdentityLedgerTx(rescuer, identity)

  case class UnLinkByKey(identity: String, pKey: PublicKey) extends IdentityLedgerTx(identity) {
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: UnLinkByKey =>
          that.identity == identity &&
            that.uniqueMessage == uniqueMessage &&
            (that.pKey isSame pKey)
        case _ => false
      }
    }

    override def hashCode(): Int = uniqueMessage.hashCode
  }
  case class UnLink(identity: String, tag: String) extends IdentityLedgerTx(identity, tag)

  case class TaggedIdentity(identity: String , tag:String)
  case class TaggedPublicKeyAccount(account: PublicKeyAccount, tag:String)

  implicit class IdentityLedgerTxFromBytes(bytes: Array[Byte]) {
    def toIdentityLedgerTx: IdentityLedgerTx = bytes.head match {
      case ClaimCode => bytes.toClaim
      case LinkCode => bytes.toLink
      case UnLinkByKeyCode => bytes.toUnLinkByKey
      case UnLinkCode => bytes.toUnLink
      case RescueCode => bytes.toRescue
      case LinkRescuerCode => bytes.toLinkRescuer
      case UnLinkRescuerCode => bytes.toUnLinkRescuer
    }
  }
  implicit class IdentityLedgerTxToBytes(idLedgerMsg: IdentityLedgerTx) {
    def toBytes: Array[Byte] = idLedgerMsg match {
      case msg: Claim => msg.toBytes
      case msg: Link => msg.toBytes
      case msg: UnLink => msg.toBytes
      case msg: UnLinkByKey => msg.toBytes
      case msg: LinkRescuer => msg.toBytes
      case msg: Rescue => msg.toBytes
      case msg: UnLinkRescuer => msg.toBytes
    }
  }

  implicit class ClaimToBytes(claim: Claim) extends ToBytes {
    override def toBytes: Array[Byte] = ClaimSerializer.toBytes(claim)
  }

  implicit class ClaimFromBytes(bytes: Array[Byte])  {
    def toClaim: Claim = ClaimSerializer.fromBytes(bytes)
  }

  implicit class LinkToBytes(claim: Link) extends ToBytes {
    override def toBytes: Array[Byte] = LinkSerializer.toBytes(claim)
  }

  implicit class LinkFromBytes(bytes: Array[Byte])  {
    def toLink: Link = LinkSerializer.fromBytes(bytes)
  }

  implicit class UnLinkByKeyToBytes(claim: UnLinkByKey) extends ToBytes {
    override def toBytes: Array[Byte] = UnLinkByKeySerializer.toBytes(claim)
  }

  implicit class UnLinkByKeyFromBytes(bytes: Array[Byte])  {
    def toUnLinkByKey: UnLinkByKey = UnLinkByKeySerializer.fromBytes(bytes)
  }

  implicit class UnLinkToBytes(claim: UnLink) extends ToBytes {
    override def toBytes: Array[Byte] = UnLinkSerializer.toBytes(claim)
  }

  implicit class UnLinkFromBytes(bytes: Array[Byte])  {
    def toUnLink: UnLink = UnLinkSerializer.fromBytes(bytes)
  }

  implicit class LinkRescuerFromBytes(bytes: Array[Byte])  {
    def toLinkRescuer: LinkRescuer = LinkRescuerSerializer.fromBytes(bytes)
  }

  implicit class LinkRescuerToBytes(rescuer: LinkRescuer) extends ToBytes {
    override def toBytes: Array[Byte] = LinkRescuerSerializer.toBytes(rescuer)
  }

  implicit class UnLinkRescuerFromBytes(bytes: Array[Byte])  {
    def toUnLinkRescuer: UnLinkRescuer = UnLinkRescuerSerializer.fromBytes(bytes)
  }

  implicit class UnLinkRescuerToBytes(rescuer: UnLinkRescuer) extends ToBytes {
    override def toBytes: Array[Byte] = UnLinkRescuerSerializer.toBytes(rescuer)
  }

  implicit class RescueFromBytes(bytes: Array[Byte])  {
    def toRescue: Rescue = RescueSerializer.fromBytes(bytes)
  }

  implicit class RescueToBytes(rescue: Rescue) extends ToBytes {
    override def toBytes: Array[Byte] = RescueSerializer.toBytes(rescue)
  }
}


