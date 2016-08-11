package sss.asado

/**
  * Created by alan on 5/24/16.
  */
trait PublishedMessageKeys {

  val SignedTx: Byte = 100
  val SignedTxAck: Byte = 101
  val SignedTxNack: Byte = 102
  val SeqSignedTx: Byte = 103
  val ConfirmTx: Byte = 104
  val AckConfirmTx: Byte = 105
  val NackConfirmTx: Byte = 106
  val TempNack: Byte = 107

  val MalformedMessage: Byte = 20
  val GenericErrorMessage: Byte = 21

  val BalanceLedger: Byte = 70
  val IdentityLedger: Byte = 71
}

object PublishedMessageKeys extends PublishedMessageKeys