package sss.asado.account


/**
  * Created by alan on 6/3/16.
  */

object TestClientKey {
  def apply(clientIdentity: String = "clientKey",
            phrase: String = "testpassword",
            tag: String = "testtag"): PrivateKeyAccount = {
    KeyPersister.deleteKey(clientIdentity, tag)
    new KeyPersister(clientIdentity, true, phrase, tag).account
  }
}
