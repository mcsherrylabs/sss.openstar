package sss.asado.network

import sss.asado.util.Serialize._

case class ApplicationVersion(firstDigit: Int,
                              secondDigit: Int,
                              thirdDigit: Int) {
  lazy val bytes: Array[Byte] =
    ApplicationVersionSeriliser.toBytes(this)
}

object ApplicationVersion {

  def apply(ver: String): ApplicationVersion = {
    val digits = ver.split("\\.")
    require(digits.length == 3,
            "The Application Version must be of the form x.x.x")
    ApplicationVersion(digits(0).toInt, digits(1).toInt, digits(2).toInt)
  }

  def parse(bytes: Array[Byte]): ApplicationVersion =
    ApplicationVersionSeriliser.fromBytes(bytes)

}

object ApplicationVersionSeriliser extends Serializer[ApplicationVersion] {

  override def toBytes(t: ApplicationVersion): Array[Byte] =
    IntSerializer(t.firstDigit) ++
      IntSerializer(t.secondDigit) ++
      IntSerializer(t.thirdDigit).toBytes

  override def fromBytes(b: Array[Byte]): ApplicationVersion = {
    val extracted = b.extract(IntDeSerialize, IntDeSerialize, IntDeSerialize)
    ApplicationVersion(extracted._1, extracted._2, extracted._3)
  }
}
