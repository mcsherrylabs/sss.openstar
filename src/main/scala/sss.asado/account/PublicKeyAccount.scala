package sss.asado.account

class PublicKeyAccount(val publicKey: Array[Byte]) extends Account(Account.fromPubkey(publicKey))
