package sss.openstar.peers.serialize

import java.net.{InetSocketAddress}

import sss.openstar.peers._
import sss.openstar.util.Serialize._

object InetSocketAddressSerializer extends Serializer[InetSocketAddress] {

  override def toBytes(p: InetSocketAddress): Array[Byte] = {

      ByteArraySerializer(p.getAddress.toBytes) ++
      IntSerializer(p.getPort)
        .toBytes
  }

  override def fromBytes(bs: Array[Byte]): InetSocketAddress = {
    val extracted = bs.extract(
      ByteArrayDeSerialize(_.toInetAddress),
      IntDeSerialize
    )
    new InetSocketAddress(extracted._1, extracted._2)

  }
}

