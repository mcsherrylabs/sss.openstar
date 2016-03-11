package sss.asado.account

import scala.collection.mutable

class PublicKeyAccount(val publicKey: mutable.WrappedArray[Byte]) extends Account(Account.fromPubkey(publicKey))
