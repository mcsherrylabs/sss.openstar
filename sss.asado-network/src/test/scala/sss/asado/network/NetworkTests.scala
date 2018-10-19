package sss.asado.network

import java.net.InetSocketAddress

import sss.asado.network.TestActorSystem.TestMessage

import scala.language.postfixOps

import sss.asado.network.testserver.TestServer
import sss.asado.network.testserver.TestServer._

import scala.concurrent.duration._

object NetworkTests {

  def allPairsOfTests(implicit servers: (TestServer, TestServer)) = {

    val waitForMessageAndDisconnect = Run { (server, nc) =>

      server.msgBus.subscribe(classOf[TestMessage])( server.startActor)
      server.msgBus.subscribe(classOf[Connection])( server.startActor)
      WaitFor[Connection] { c =>
        WaitFor[TestMessage]({ t =>
          nc.disconnect(c.nodeId)
          server.msgBus.unsubscribe(classOf[TestMessage])( server.startActor)
          server.msgBus.unsubscribe(classOf[Connection])( server.startActor)
          End("Waited for connection, received TestMessage, disconnected")
        })
      }
    }

    val sendMessageAndReconnectStrategy = Run { (server, nc) =>

      server.msgBus.subscribe(classOf[Connection])( server.startActor)
      server.msgBus.subscribe(classOf[ConnectionLost])( server.startActor)
      server.msgBus.subscribe(classOf[ConnectionHandshakeTimeout])(
                              server.startActor)
      nc.connect(servers._1.nodeId, reconnectionStrategy(1))
      WaitFor[Connection] { c: Connection =>
        val nm = SerializedMessage(1.toByte, 1.toByte, Array())
        nc.send(nm, Set(c.nodeId))
        WaitFor[ConnectionLost] { lost =>
          WaitFor[Connection] { c =>
            nc.disconnect(c.nodeId)
            WaitFor[ConnectionLost] { lost =>
              server.msgBus.unsubscribe(classOf[Connection])( server.startActor)
              server.msgBus.unsubscribe(classOf[ConnectionLost])( server.startActor)
              server.msgBus.unsubscribe(classOf[ConnectionHandshakeTimeout])(server.startActor)
              End("Established connection with retry, sent message, lost connection, got connection re established")
            }
          }
        }
      }
    }

    val sentToNonExistentServer = Run { (server, nc) =>

      server.msgBus.subscribe(classOf[ConnectionFailed])(server.startActor)
      nc.connect(
        NodeId("nosuch", new InetSocketAddress("127.0.0.1", 1111)))
      WaitFor[ConnectionFailed] { failed =>
        server.msgBus.unsubscribe(classOf[ConnectionFailed])(server.startActor)
        End(
          s"Successfully failed connect to ${failed.remote} because there's nothing there.")
      }
    }

    val blacklisted = Run { (server, nc) =>

      server.msgBus.subscribe(classOf[Connection])(server.startActor)
      server.msgBus.subscribe(classOf[ConnectionLost])(server.startActor)
      nc.connect(servers._2.nodeId, reconnectionStrategy(1))
      WaitFor[Connection] { c =>
        WaitFor[ConnectionLost] { lost =>
          WaitFor[Connection] { c =>
            nc.disconnect(servers._2.nodeId.id)
            server.msgBus.unsubscribe(classOf[ConnectionLost])( server.startActor)
            server.msgBus.unsubscribe(classOf[Connection])(server.startActor)
            End("Connection lost while blacklisted, but regained post blacklist expiry")
          }
        }
      }
    }

    val blacklister = Run { (server, nc) =>


      server.msgBus.subscribe(classOf[Connection])(server.startActor)
      nc.blacklist(servers._1.nodeId.id, 2 seconds)
      nc.connect(servers._1.nodeId, reconnectionStrategy(1,1))

        WaitForNothing(2 seconds, Run {
          (server, nc) =>
            nc.connect(servers._1.nodeId)
            WaitFor[Connection] { c =>
              nc.disconnect(servers._1.nodeId.id)
              server.msgBus.unsubscribe(classOf[Connection])(server.startActor)
              End("Couldn't connect while blacklisted, could on expiry.")
            }
        })
    }

    val unblacklistee = Run { (server, nc) =>
      server.msgBus.subscribe(classOf[Connection])(server.startActor)
      server.msgBus.subscribe(classOf[ConnectionLost])(server.startActor)
      WaitFor[Connection] { c =>
        nc.disconnect(c.nodeId)
        WaitFor[ConnectionLost] { lost =>
          server.msgBus.unsubscribe(classOf[Connection])(server.startActor)
          server.msgBus.unsubscribe(classOf[ConnectionLost])(server.startActor)
          End("Connection made post blacklisting.")
        }

      }
    }

    val unblacklister = Run { (server, nc) =>

      server.msgBus.subscribe(classOf[Connection])(server.startActor)
      server.msgBus.subscribe(classOf[ConnectionLost])(server.startActor)
      nc.blacklist(servers._1.nodeId.id, 2 seconds)
      nc.unBlacklist(servers._1.nodeId.id)
      nc.connect(servers._1.nodeId)
      WaitFor[Connection] { c =>
        nc.disconnect(c.nodeId)
        WaitFor[ConnectionLost] { lost =>
          server.msgBus.unsubscribe(classOf[Connection])(server.startActor)
          server.msgBus.unsubscribe(classOf[ConnectionLost])(server.startActor)
          End("Connected because unblacklisted")
        }
      }
    }

    List(
      (unblacklistee, unblacklister),
      (unblacklistee, unblacklister),
      (unblacklistee, unblacklister),
      (waitForMessageAndDisconnect, sendMessageAndReconnectStrategy),
      (End("No need for server1 "), sentToNonExistentServer),
      (blacklisted, blacklister)
    )
  }

}
