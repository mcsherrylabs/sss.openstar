package sss.asado


/**
  * Created by alan on 3/18/16.
  */
object MessageKeys {

  val SignedTx: Byte = 100
  val SignedTxAck: Byte = 101
  val SignedTxNack: Byte = 102
  val SeqSignedTx: Byte = 102
  val ConfirmTx: Byte = 104
  val AckConfirmTx: Byte = 105
  val NackConfirmTx: Byte = 106
  val ReConfirmTx: Byte = 107


  val MalformedMessage: Byte = 20

  val FindLeader: Byte = 30
  val Leader: Byte = 31
  val VoteLeader: Byte = 32

  val GetTxPage: Byte = 40
  val PagedTx: Byte = 41
  val EndPageTx: Byte = 42
  val CloseBlock: Byte = 43
  val Synced: Byte = 44



}
