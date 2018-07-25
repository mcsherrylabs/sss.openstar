package sss.asado.account

import sss.asado.DummySeedBytes


/**
  * Created by alan on 6/3/16.
  */

object TestClientKey {
  def apply(clientIdentity: String = "clientKey",
            phrase: String = "testpassword",
            tag: String = "testtag"): PrivateKeyAccount = {
    KeyPersister.deleteKey(clientIdentity, tag)
    PrivateKeyAccount(KeyPersister(clientIdentity, tag, phrase, () => {
      val pk = PrivateKeyAccount(DummySeedBytes)
      (pk.privateKey, pk.publicKey)
    }))
  }
}
