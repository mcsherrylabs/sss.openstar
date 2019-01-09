package sss.telemetry.server

import java.nio.charset.StandardCharsets

import sss.openstar.common.telemetry._
import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.util.ByteString
import sss.telemetry.server.Route.GetWebsocketFlow
import akka.stream.scaladsl.GraphDSL.Implicits._


import scala.concurrent.duration._
import scala.concurrent.Future

class ClientHandlerActor(logReport: Report => Unit)(implicit as: ActorSystem,
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
              logReport(bm.data.toReport)
              Future("9999")
            case bm: BinaryMessage =>
              // consume the stream
              bm.toStrict(3.seconds)
                .map (_.data.toReport)
                .map(logReport)
                  .map(_ => "999")
          })

        val pubSrc = b.add(Source.fromPublisher(publisher).map(BinaryMessage(_)))
        textMsgFlow ~> Sink.foreach[String](self ! _)
        FlowShape(textMsgFlow.in, pubSrc.out)
      })

      sender ! flow

    /*
    case x =>
      println(s"client actor received $s")
      // passes x down to the websocket! Super cool.
      down ! ByteString("Hello " + s + "!".getBytes(StandardCharsets.UTF_8))
    */
  }
}
