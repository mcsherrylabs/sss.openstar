package sss.asado.account

import sss.asado.{DummySeedBytes, Identity, IdentityTag}


/**
  * Created by alan on 6/3/16.
  */

object TestClientKey {
  def apply(clientIdentity: String = "clientKey",
            phrase: String = "testpassword",
            tag: String = "testtag"): PrivateKeyAccount = {
    KeyPersister.deleteKey(clientIdentity, tag)
    PrivateKeyAccount(KeyPersister(Identity(clientIdentity), IdentityTag(tag), phrase, () => {
      PrivateKeyAccount(DummySeedBytes).tuple
    }))
  }
}
