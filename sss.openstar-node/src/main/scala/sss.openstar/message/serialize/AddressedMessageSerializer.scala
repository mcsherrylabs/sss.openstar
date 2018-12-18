package sss.openstar.message.serialize

import sss.openstar.ledger._
import sss.openstar.message._
import sss.openstar.util.Serialize._
/**
  * Created by alan on 6/8/16.
  */
object AddressedMessageSerializer extends Serializer[AddressedMessage] {
  def toBytes(o: AddressedMessage): Array[Byte] =
    (StringSerializer(o.from) ++
      ByteArraySerializer(o.ledgerItem.toBytes) ++
      ByteArraySerializer(o.msgPayload.toBytes)
      ).toBytes

  def fromBytes(bs: Array[Byte]): AddressedMessage = {
    AddressedMessage.tupled(
      bs.extract(
        StringDeSerialize,
        ByteArrayDeSerialize(_.toLedgerItem),
        ByteArrayDeSerialize(_.toMessagePayload)
      )
    )

  }

}
