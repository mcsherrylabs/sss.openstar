package sss.openstar.state

/**
  * Created by alan on 4/1/16.
  *
import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import sss.openstar.block.IsSynced
import org.scalatest._
import sss.openstar.StopSystemAfterAll
import sss.openstar.network.NetworkControllerActor.{QuorumGained, QuorumLost}
import sss.openstar.state.OpenstarState._
import sss.openstar.state.OpenstarStateProtocol._

class OpenstarStateMachineSpec
    extends TestKit(ActorSystem("test-system"))
    with MustMatchers
    with WordSpecLike
    with StopSystemAfterAll
    with ImplicitSender {

  class TestOpenstarStateMachineActor() extends OpenstarStateMachine {

    def localRec: Receive = {
      case m @ FindTheLeader             => println(s"$m")
      case m @ SplitRemoteLocalLeader    => println(s"$m")
      case m @ AcceptTransactions        => println(s"$m")
      case m @ StopAcceptingTransactions => println(s"$m")
    }
    override def receive = localRec orElse super.receive

  }

  "Openstar initially " must {
    "be in Connecting " in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _                                => fail("must initialise to ConnectingState")
      }
    }
  }

  "Openstar in Connecting " must {
    "transition to QuorumState" in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! QuorumGained
      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, QuorumState) => true
        case _                            => fail("must transition to QuorumState")
      }
    }
  }

  "Openstar in  QuorumState" must {
    "return to ConectingState if Quorum Lost" in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! QuorumGained
      openstarMachine ! QuorumLost
      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _                                => fail("must transition to Connection")
      }
    }
  }

  "Openstar in QuorumState" must {
    "transition to Ordered state when leader elected " in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! QuorumGained
      openstarMachine ! LeaderFound("some leader")
      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, OrderedState) => true
        case _                             => fail("must transition to Ordered")
      }
    }
  }

  "Openstar in OrderedState " must {
    "transition to Ready when synced " in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! QuorumGained
      openstarMachine ! LeaderFound("")
      openstarMachine ! IsSynced
      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ReadyState) => true
        case _                           => fail("must transition to Ready")
      }
    }
  }

  "Openstar in OrderedState " must {
    "transition to Connecting " in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! QuorumGained
      openstarMachine ! LeaderFound("")
      openstarMachine ! QuorumLost
      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _                                => fail("must transition to Connecting")
      }
    }
  }
  "Openstar in ReadyState " must {
    "transition to Connection when Quorum lost " in {
      val openstarMachine = system.actorOf(Props(new TestOpenstarStateMachineActor))

      openstarMachine ! QuorumGained
      openstarMachine ! LeaderFound("")
      openstarMachine ! IsSynced
      openstarMachine ! QuorumLost

      openstarMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _                                => fail("must transition to Connecting")
      }
    }
  }
}
*/