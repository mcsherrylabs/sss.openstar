package sss.ui.nobu

import akka.actor.{ActorSystem, Props}
import sss.ancillary._
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.network.MessageRouter.RegisterRef
import sss.asado.nodebuilder._
import sss.ui.reactor.{ReactorActorSystem, Register, UIReactor}


/**
  * Created by alan on 6/14/16.
  */

object NobuNode {

  trait NodeConfigName {
    val configName: String = "node"
  }
  case object NodeBootstrap extends ConfigNameBuilder with
    NodeConfigBuilder with
    ConfigBuilder with
    BindControllerSettingsBuilder with
    HomeDomainBuilder with NodeConfigName

  case class NodeBootstrapWallet(nodeId: NodeIdentity) extends ConfigNameBuilder with
    BindControllerSettingsBuilder with
    ConfigBuilder with
    NodeConfigBuilder with
    HomeDomainBuilder with
    NodeIdentityBuilder with
    DbBuilder with
    WalletPersistenceBuilder with
    NodeConfigName {
    lazy override val nodeIdentity: NodeIdentity = nodeId
  }

  def apply(uiReactor: UIReactor,nodeId: NodeIdentity) = new NobuNode(NodeBootstrap.configName,
    ReactorActorSystem.actorSystem,
    nodeId, Main.server,
    uiReactor)
}

class NobuNode(override val configName: String,
               anActorSystem: ActorSystem,
               nId: NodeIdentity,
               aHttpServer: ServerLauncher,
               uiReactor: UIReactor
               ) extends ClientNode {

  require(Option(aHttpServer).isDefined, "The httpServer has not been initialised yet")
  require(Option(anActorSystem).isDefined, "The Actor System has not been initialised yet")

  lazy override val actorSystem: ActorSystem = anActorSystem
  lazy override val nodeIdentity: NodeIdentity = nId
  lazy override val httpServer: ServerLauncher = aHttpServer

  initStateMachine
  configureServlets
  startNetwork
  connectHome

  val ref = uiReactor.actorOf(Props(classOf[NobuNodeBridge], this, homeDomain, balanceLedger, identityService, 1, 2))
  ref ! Register(NobuNodeBridge.NobuCategory)


  messageRouterActor ! RegisterRef( MessageKeys.SignedTxAck, ref)
  messageRouterActor ! RegisterRef( MessageKeys.AckConfirmTx, ref)
  messageRouterActor ! RegisterRef( MessageKeys.SignedTxNack, ref)
  messageRouterActor ! RegisterRef( MessageKeys.MessageResponse, ref)
}
