package sss.asado.state

/**
  * Created by alan on 4/1/16.
  */

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import sss.asado.StopSystemAfterAll
import sss.asado.state.AsadoState._
import sss.asado.state.AsadoStateProtocol._



class AsadoStateMachineSpec extends TestKit(ActorSystem("test-system"))
  with MustMatchers
  with WordSpecLike
  with StopSystemAfterAll
  with ImplicitSender {

  class TestAsadoStateMachineActor() extends AsadoStateMachine {

    def localRec : Receive = {
      case  m@FindTheLeader => println(s"$m")
      case  m@SyncWithLeader => println(s"$m")
      case  m@AcceptTransactions=> println(s"$m")
      case  m@StopAcceptingTransactions=> println(s"$m")
    }
    override def receive = localRec orElse super.receive

  }

  "Asado initially " must {
    "be in Connecting " in {
      val asadoMachine = system.actorOf(Props(new TestAsadoStateMachineActor))

      asadoMachine  ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _ => fail("must initialise to ConnectingState")
      }
    }
  }

  "Asado in Connecting " must {
    "transition to QuorumState" in {
      val asadoMachine = system.actorOf(Props( new TestAsadoStateMachineActor))

      asadoMachine ! QuorumGained
      asadoMachine  ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, QuorumState) => true
        case _ => fail("must transition to QuorumState")
      }
    }
  }


  "Asado in  QuorumState" must {
    "return to ConectingState if Quorum Lost" in {
      val asadoMachine = system.actorOf(Props( new TestAsadoStateMachineActor))

      asadoMachine ! QuorumGained
      asadoMachine ! QuorumLost
      asadoMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _ => fail("must transition to Connection")
      }
    }
  }

  "Asado in QuorumState" must {
    "transition to Ordered state when leader elected " in {
      val asadoMachine = system.actorOf(Props( new TestAsadoStateMachineActor))

      asadoMachine ! QuorumGained
      asadoMachine ! LeaderFound("some leader")
      asadoMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, OrderedState) => true
        case _ => fail("must transition to Ordered")
      }
    }
  }

  "Asado in OrderedState " must {
    "transition to Ready when synced " in {
      val asadoMachine = system.actorOf(Props( new TestAsadoStateMachineActor))

      asadoMachine ! QuorumGained
      asadoMachine ! LeaderFound("")
      asadoMachine ! Synced
      asadoMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ReadyState) => true
        case _ => fail("must transition to Ready")
      }
    }
  }

  "Asado in OrderedState " must {
    "transition to Connecting " in {
      val asadoMachine = system.actorOf(Props( new TestAsadoStateMachineActor))

      asadoMachine ! QuorumGained
      asadoMachine ! LeaderFound("")
      asadoMachine ! QuorumLost
      asadoMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _ => fail("must transition to Connecting")
      }
    }
  }
  "Asado in ReadyState " must {
    "transition to Connection when Quorum lost " in {
      val asadoMachine = system.actorOf(Props( new TestAsadoStateMachineActor))

      asadoMachine ! QuorumGained
      asadoMachine ! LeaderFound("")
      asadoMachine ! Synced
      asadoMachine ! QuorumLost

      asadoMachine ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_, ConnectingState) => true
        case _ => fail("must transition to Connecting")
      }
    }
  }
}

