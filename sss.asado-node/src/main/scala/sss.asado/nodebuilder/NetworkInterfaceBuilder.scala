package sss.asado.nodebuilder

import com.google.common.primitives.Longs
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.identityledger.IdentityService
import sss.asado.network.{IdentityVerification, NetworkInterface}

trait NetworkInterfaceBuilder {

  self : NodeIdentityBuilder with
    IdentityServiceBuilder with
    DbBuilder with
    BootstrapIdentitiesBuilder with
    Logging with
    NodeConfigBuilder =>


  lazy val networkInterface : NetworkInterface = buildNetworkInterface
  def buildNetworkInterface =
    new NetworkInterface(createIdVerifier(nodeIdentity,
      identityService, bootstrapIdentities), nodeConfig.settings, nodeConfig.uPnp)


  protected def createIdVerifier(nodeIdentity: NodeIdentity,
                                 identityService: IdentityService,
                                 bootstrapIdentities: List[BootstrapIdentity]
                                ): IdentityVerification = {

    import sss.asado.util.ByteArrayVarcharOps._

    new IdentityVerification {
      override def verify(sig: Array[Byte], msg: Array[Byte], nodeId: String, tag: String): Boolean = {
        val ac = identityService.accountOpt(nodeId, tag)
        val pk = ac.map(_.publicKey.toVarChar)
        val sigStr = sig.toVarChar
        val msgStr = Longs.fromByteArray(msg)
        log.info(s"Attempting to verify $nodeId, $tag ")
        log.info(s"msg $msgStr pk $pk")
        log.info(s"sig $sigStr ")

        if(ac.exists(_.verify(sig, msg))) {
          log.info(s"$nodeId, $tag verified using identity ledger")
          true
        }else {
          //try the bootstrap
          val res = bootstrapIdentities.exists(_.verify(sig, msg))
          log.info(s"Using bootstrap identities for $nodeId - result is $res")
          res
        }
      }

      override def sign(msg: Array[Byte]): Array[Byte] = nodeIdentity.sign(msg)

      override val nodeId: String = nodeIdentity.id
      override val tag: String = nodeIdentity.tag

    }
  }
}
