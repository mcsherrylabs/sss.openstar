package sss.asado.identityledger.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.crypto.SeedBytes
import sss.asado.identityledger._
import sss.asado.util.ByteArrayComparisonOps

/**
  * Created by alan on 6/7/16.
  */
class SerializeSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  "A Claim " should " serialize and deserialize " in {
    val claim = Claim("idenairy", SeedBytes(56))
    val asBytes = claim.toBytes
    val hydrated = asBytes.toClaim
    assert(claim.identity == hydrated.identity)
    assert(claim.uniqueMessage == hydrated.uniqueMessage)
    assert(claim.pKey isSame hydrated.pKey)
    assert(claim == hydrated)
    assert(claim.hashCode == hydrated.hashCode)
  }

  "A Link Rescuer " should " serialize and deserialize " in {
    val test = LinkRescuer("idenairy", "asdalsdjalsdjalid")
    val asBytes = test.toBytes
    val hydrated = asBytes.toLinkRescuer
    assert(test.identity == hydrated.identity)
    assert(test.uniqueMessage == hydrated.uniqueMessage)
    assert(test.rescuer == hydrated.rescuer)
    assert(test == hydrated)
    assert(test.hashCode == hydrated.hashCode)
  }


  "A Link  " should " serialize and deserialize " in {
    val test = Link("idenairy", SeedBytes(56), "asdalsdjalsdjalid")
    val asBytes = test.toBytes
    val hydrated = asBytes.toLink
    assert(test.identity == hydrated.identity)
    assert(test.uniqueMessage == hydrated.uniqueMessage)
    assert(test.tag == hydrated.tag)
    assert(test == hydrated)
    assert(test.hashCode == hydrated.hashCode)
  }


  "A Rescue  " should " serialize and deserialize " in {
    val test = Rescue("rescuerasdasd", "idenairy", SeedBytes(56), "asdalsdjalsdjalid")
    val asBytes = test.toBytes
    val hydrated = asBytes.toRescue
    assert(test.identity == hydrated.identity)
    assert(test.uniqueMessage == hydrated.uniqueMessage)
    assert(test.tag == hydrated.tag)
    assert(test.rescuer == hydrated.rescuer)
    assert(test == hydrated)
    assert(test.hashCode == hydrated.hashCode)
  }

  "An Unlink by Key " should " serialize and deserialize " in {
    val test = UnLinkByKey("rescuerasdasd", SeedBytes(56))
    val asBytes = test.toBytes
    val hydrated = asBytes.toUnLinkByKey
    assert(test.identity == hydrated.identity)
    assert(test.pKey isSame hydrated.pKey)
    assert(test.uniqueMessage == hydrated.uniqueMessage)
    assert(test == hydrated)
    assert(test.hashCode == hydrated.hashCode)
  }

  "An UnLinkRescuer " should " serialize and deserialize " in {
    val test = UnLinkRescuer("rescuerasdasd", "asdfadsasdasdadsasd")
    val asBytes = test.toBytes
    val hydrated = asBytes.toUnLinkRescuer
    assert(test.identity == hydrated.identity)
    assert(test.rescuer == hydrated.rescuer)
    assert(test.uniqueMessage == hydrated.uniqueMessage)
    assert(test == hydrated)
    assert(test.hashCode == hydrated.hashCode)
  }

  "An UnLink " should " serialize and deserialize " in {
    val test = UnLink("rescuerasdasd", "asdfadsasdasdadsasd")
    val asBytes = test.toBytes
    val hydrated = asBytes.toUnLink
    assert(test.identity == hydrated.identity)
    assert(test.tag == hydrated.tag)
    assert(test.uniqueMessage == hydrated.uniqueMessage)
    assert(test == hydrated)
    assert(test.hashCode == hydrated.hashCode)
  }
}
