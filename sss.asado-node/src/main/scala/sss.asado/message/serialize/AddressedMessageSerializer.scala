package sss.asado.message.serialize

import sss.asado.ledger._
import sss.asado.message._
import sss.asado.util.Serialize._
/**
  * Created by alan on 6/8/16.
  */
object AddressedMessageSerializer extends Serializer[AddressedMessage] {
  def toBytes(o: AddressedMessage): Array[Byte] =
    (ByteArraySerializer(o.ledgerItem.toBytes) ++
      ByteArraySerializer(o.msgPayload.toBytes)
      ).toBytes

  def fromBytes(bs: Array[Byte]): AddressedMessage = {
    AddressedMessage.tupled(
      bs.extract(
        ByteArrayDeSerialize(_.toLedgerItem),
        ByteArrayDeSerialize(_.toMessagePayload)
      )
    )

  }

}
