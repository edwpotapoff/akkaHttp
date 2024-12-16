package com.example

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future

object WebSocketClientFlow extends App {
  println("start")
  //  implicit val system = ActorSystem()
  //
  //  //#half-closed-WebSocket-finite-working-example
  //
  //  // using emit "one" and "two" and then keep the connection open
  //  val flow: Flow[Message, Message, Promise[Option[Message]]] =
  //    Flow.fromSinkAndSourceMat(
  //      Sink.foreach[Message](println),
  //      Source(List(TextMessage("one"), TextMessage("two")))
  //        .concatMat(Source.maybe[Message])(Keep.right))(Keep.right)
  //
  //  val (upgradeResponse, promise) =
  //    Http().singleWebSocketRequest(
  //      WebSocketRequest("ws://localhost:8080/greeter"),
  //      flow)
  //
  //  // at some later time we want to disconnect
  //  promise.success(None)

  implicit val system = ActorSystem()

  import system.dispatcher

  val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case tm: TextMessage =>
        val st = tm.textStream
        val p: Future[String] = st.runFold("")(_ ++ _)
        p.foreach { is =>
          val rez = new String(is.toArray)
          println(s"get $rez")
        }
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)

    }

  //  val incoming =
  //    Flow[Message]
  //      .mapConcat {
  //        // we match but don't actually consume the text message here,
  //        // rather we simply stream it back as the tail of the response
  //        // this means we might start sending the response even before the
  //        // end of the incoming message has been received
  //        case tm: TextMessage =>
  //          val st = tm.textStream
  //          val p: Future[String] = st.runFold("")(_ ++ _)
  //          p.foreach { is =>
  //            val rez = new String(is.toArray)
  //            println(s"get $rez")
  //          }
  //
  //          Nil
  //        case bm: BinaryMessage =>
  //          // ignore binary messages but drain content to avoid the stream being clogged
  //          bm.dataStream.runWith(Sink.ignore)
  //          Nil
  //      }

  val outgoing = Source.single(TextMessage("hello world!"))

  val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:8080/greeter"))

  val (upgradeResponse, closed) =
    outgoing
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()

  val connected = upgradeResponse.flatMap { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Future.successful(Done)
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  connected.onComplete(println)
  closed.foreach(_ => system.terminate())
}