package sss.openstar.account

import sss.openstar.DummySeedBytes


/**
  * Created by alan on 6/3/16.
  */

object TestClientKey {
  def apply(clientIdentity: String = "clientKey",
            phrase: String = "testpassword",
            tag: String = "testtag"): PrivateKeyAccount = {
    KeyPersister.deleteKey(clientIdentity, tag)
    PrivateKeyAccount(KeyPersister(clientIdentity, tag, phrase, () => {
      PrivateKeyAccount(DummySeedBytes).tuple
    }))
  }
}
