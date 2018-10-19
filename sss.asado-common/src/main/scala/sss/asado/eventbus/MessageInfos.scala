package sss.asado.eventbus


trait MessageInfos {

  self: MessageInfoComposite[_] =>

  def find(code: Byte): Option[MessageInfoComposite[_]]

  def toMessageComposite: MessageInfoComposite[_] = this

  def ++(others: MessageInfos): MessageInfos =
    toMessageComposite :+
      others.toMessageComposite

}
