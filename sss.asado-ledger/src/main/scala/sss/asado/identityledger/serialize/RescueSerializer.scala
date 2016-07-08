package sss.asado.identityledger.serialize

import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.identityledger._
import sss.asado.util.Serialize._


/**
  * Created by alan on 6/1/16.
  */
object RescueSerializer extends Serializer[Rescue] {
  def toBytes(rescuer: Rescue): Array[Byte] = {
    (ByteSerializer(RescueCode) ++
      LongSerializer(rescuer.uniqueMessage) ++
      StringSerializer(rescuer.rescuer) ++
      StringSerializer(rescuer.identity) ++
      ByteArraySerializer(rescuer.pKey) ++
      StringSerializer(rescuer.tag)
      ).toBytes
  }

  def fromBytes(bytes: Array[Byte]): Rescue = {
    val extracted = bytes.extract(ByteDeSerialize,
      LongDeSerialize, StringDeSerialize,
      StringDeSerialize, ByteArrayDeSerialize, StringDeSerialize)

    require(extracted(0)[Byte] == RescueCode, s"Wrong leading byte for Rescue ${bytes.head} instead of $RescueCode")

    new Rescue(extracted(2)[String], extracted(3)[String], extracted(4)[Array[Byte]], extracted(5)[String]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }
}
