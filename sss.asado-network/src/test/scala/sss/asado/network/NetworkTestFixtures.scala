package sss.asado

import java.net.InetSocketAddress

import sss.asado.network.NetworkInterface.BindControllerSettings
import sss.asado.network.{IdentityVerification, NetworkInterface}
import sss.asado.util.Results.{OkResult, ok}

trait NetworkTestFixtures {

  val appVer111 = "1.1.1"
  val appVer234 = "2.3.4"
  val appVer235 = "2.3.5"
  val appVer334 = "3.3.4"


  val remote: InetSocketAddress =
    new InetSocketAddress("localhost", 9876)

  val handshakeVerifier = new IdentityVerification {
    override val nodeId: String = "nodeId"
    override val tag: String = "tag"
    override def sign(msg: Array[Byte]): Array[Byte] = msg
    override def verify(sig: Array[Byte],
                        msg: Array[Byte],
                        nodeId: String,
                        tag: String): OkResult = ok()
  }

  val settings = new BindControllerSettings {
    override val applicationName: String = "applicationName"
    override val declaredAddressOpt: Option[String] = None
    override val appVersion: String = appVer234
  }

  val networkInterface = new NetworkInterface(settings, None)
}
