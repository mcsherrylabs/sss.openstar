package sss.asado.message.serialize

import sss.asado.message.AddressedMessage
import sss.asado.util.Serialize._
import sss.asado.ledger._
/**
  * Created by alan on 6/8/16.
  */
object AddressedMessageSerializer extends Serializer[AddressedMessage] {
  def toBytes(o: AddressedMessage): Array[Byte] =
    (ByteArraySerializer(o.ledgerItem.toBytes) ++
      ByteArraySerializer(o.msg)
      ).toBytes

  def fromBytes(bs: Array[Byte]): AddressedMessage = {
    val extracted = bs.extract(ByteArrayDeSerialize, ByteArrayDeSerialize)
    AddressedMessage(extracted(0)[Array[Byte]].toLedgerItem, extracted(1)[Array[Byte]])
  }

}
