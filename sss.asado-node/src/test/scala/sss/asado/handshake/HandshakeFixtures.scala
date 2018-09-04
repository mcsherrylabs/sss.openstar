package sss.asado.handshake

import java.net.InetSocketAddress

import sss.asado.network.NetworkInterface
import sss.asado.network.NetworkInterface.BindControllerSettings
import sss.asado.{Identity, IdentityTag}
import sss.asado.util.Results.{OkResult, ok}

trait HandshakeFixtures {



  val appVer111 = "1.1.1"
  val appVer234 = "2.3.4"
  val appVer235 = "2.3.5"
  val appVer334 = "3.3.4"

  val remote: InetSocketAddress =
    InetSocketAddress.createUnresolved("somehost.com", 9876)

  val settings = new BindControllerSettings {
    override val applicationName: String = "applicationName"
    override val declaredAddressOpt: Option[String] = None
    override val appVersion: String = appVer234
  }

  val networkInterface = new NetworkInterface(settings, None)
  val handshakeVerifier = new IdentityVerification {
    override val nodeId: Identity = Identity("nodeId")
    override val tag: IdentityTag = IdentityTag("tag")
    override def sign(msg: Array[Byte]): Array[Byte] = msg
    override def verify(sig: Array[Byte],
                        msg: Array[Byte],
                        nodeId: String,
                        tag: String): OkResult = ok()
  }

}
