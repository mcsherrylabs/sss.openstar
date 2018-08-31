package sss.asado.nodebuilder

import com.google.common.primitives.Longs
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.identityledger.IdentityService
import sss.asado.network.{IdentityVerification, NetworkInterface}
import sss.asado.util.Results._

trait NetworkInterfaceBuilder {

  self : NodeIdentityBuilder with
    IdentityServiceBuilder with
    DbBuilder with
    BootstrapIdentitiesBuilder with
    Logging with
    NodeConfigBuilder =>


  lazy val networkInterface : NetworkInterface = buildNetworkInterface
  def buildNetworkInterface =
    new NetworkInterface(nodeConfig.settings, nodeConfig.uPnp)

  lazy val idVerifier = createIdVerifier(nodeIdentity,
    identityService, bootstrapIdentities)

  protected def createIdVerifier(nodeIdentity: NodeIdentity,
                                 identityService: IdentityService,
                                 bootstrapIdentities: List[BootstrapIdentity]
                                ): IdentityVerification = {

    import sss.asado.util.ByteArrayEncodedStrOps._

    new IdentityVerification {

      override def verify(sig: Array[Byte],
                          msg: Array[Byte],
                          nodeId: String,
                          tag: String): OkResult = {

        val ac = identityService.accountOpt(nodeId, tag)
        val pk = ac.map(_.publicKey.toBase64Str)
        val sigStr = sig.toBase64Str
        val msgStr = Longs.fromByteArray(msg)
        log.info(s"Verifing $nodeId, $tag ")
        log.debug(s"msg $msgStr pk $pk")
        log.debug(s"sig $sigStr ")

        lazy val nodeTagStr = s"$nodeId, $tag"
        ac orErrMsg(s"Could not find $nodeTagStr in identity ledger") andThen {
          ac.exists(_.verify(sig, msg)) orErrMsg s"Could not verify signature of $nodeTagStr"
        } ifNotOk {
          //try the bootstrap
          log.info(s"$nodeId, $tag not in Identity ledger, trying bootstrap...")
          bootstrapIdentities.find(_.nodeId == nodeId)
            .orErrMsg(s"NodeId $nodeId not in bootstrap identities")
            .ifOk { bi =>
              bi.verify(sig, msg)
                .orErrMsg(s"Could not verify sig $sig from NodeId $nodeId")
            }
        }
      }

      override def sign(msg: Array[Byte]): Array[Byte] = nodeIdentity.sign(msg)

      override val nodeId: String = nodeIdentity.id
      override val tag: String = nodeIdentity.tag

    }
  }
}
