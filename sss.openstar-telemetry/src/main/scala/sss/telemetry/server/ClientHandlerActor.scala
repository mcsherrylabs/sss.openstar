package sss.telemetry.server

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import sss.telemetry.server.Route.GetWebsocketFlow
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.Future

class ClientHandlerActor(implicit as: ActorSystem,
                         am: ActorMaterializer) extends Actor {

  import as.dispatcher
  val (down, publisher) = Source
    .actorRef[ByteString](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)
    .run()

  override def receive = {
    case GetWebsocketFlow =>

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        val textMsgFlow = b.add(Flow[Message]
          .mapAsync(1) {
            case tm: TextMessage => tm.toStrict(3.seconds).map(_.text)
            case bm: BinaryMessage.Strict =>
              Future(new String(bm.data.toArray, StandardCharsets.UTF_8))
            case bm: BinaryMessage =>
              // consume the stream
              bm.toStrict(3.seconds)
              bm.dataStream.runWith(Sink.ignore)
              Future.failed(new Exception("yuck"))
          })

        val pubSrc = b.add(Source.fromPublisher(publisher).map(BinaryMessage(_)))

        textMsgFlow ~> Sink.foreach[String](self ! _)
        FlowShape(textMsgFlow.in, pubSrc.out)
      })

      sender ! flow

    case s: String =>
      println(s"client actor received $s")
      down ! ByteString("Hello " + s + "!".getBytes(StandardCharsets.UTF_8))

    // passes any int down the websocket
    case n: Int =>
      println(s"client actor received $n")
      down ! ByteString(n.toString.getBytes(StandardCharsets.UTF_8))
  }
}
