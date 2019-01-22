package sss.openstar.peers.serialize

import java.net.InetAddress

import sss.openstar.util.Serialize._

object InetAddressSerializer extends Serializer[InetAddress] {

  private val ip4Prefix: Array[Byte] = Array.fill[Byte](10)(0) ++ Array.fill[Byte](2)(-1)

  override def toBytes(p: InetAddress): Array[Byte] = {
    ByteArrayRawSerializer((ip4Prefix ++ p.getAddress).takeRight(16))
      .toBytes
  }

  override def fromBytes(bs: Array[Byte]): InetAddress = {
    bs.extract(
      ByteArrayRawDeSerialize(bs => InetAddress.getByAddress(bs))
    )
  }
}