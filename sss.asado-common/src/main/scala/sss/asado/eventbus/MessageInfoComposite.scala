package sss.asado.eventbus

case class MessageInfoComposite[K](
                               msgCode: Byte,
                               clazz: Class[K],
                               t: Array[Byte] => K,
                               next: Option[MessageInfoComposite[_]] = None
                             )
  extends MessageInfo
  with MessageInfos {

  override type T = K
  override def fromBytes(bytes: Array[Byte]): T = t(bytes)

  def find(code: Byte): Option[MessageInfoComposite[_]] = {
    if(code == msgCode) Option(this)
    else next flatMap (_.find(code))
  }

  def +:(other: MessageInfoComposite[_] ): MessageInfoComposite[_] = prepend(other)
  def :+(other: MessageInfoComposite[_]): MessageInfoComposite[_] = append(other)

  private def tailCopy(nxt: Option[MessageInfoComposite[_]],
                       newEnd: MessageInfoComposite[_])
  : Option[MessageInfoComposite[_]] = {

    if(nxt.isDefined) Some(this.copy(next = tailCopy(nxt, newEnd)))
    else Some(this.copy(next = Option(newEnd)))
  }

  private def accCodes(other: Option[MessageInfoComposite[_]], acc: Seq[Byte]): Seq[Byte] = {
    other match {
      case None => acc
      case Some(msgInfo) => accCodes(msgInfo.next, msgInfo.msgCode +: acc)
    }
  }

  private def append(other: MessageInfoComposite[_]): MessageInfoComposite[_] = {
    val clashes = for {
      code1 <- accCodes(Option(this), Seq())
      code2 <- accCodes(Option(other), Seq())
      if(code1 == code2)
    } yield(code1)

    require(clashes.isEmpty, s"Duplicate msgCodes not allowed $clashes")

    if(next.isDefined) this.copy(next = Option(next.get.append(other)))
    else this.copy(next = Option(other))
  }

  private def prepend(other: MessageInfoComposite[_]): MessageInfoComposite[_] = {
    other.append(this)
  }
}
