package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{AttributeKeys, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.io.StdIn

object GreeterWebSocketService extends App {
  implicit val system = ActorSystem()

  import system.dispatcher

  //#websocket-handler
  // The Greeter WebSocket Service expects a "name" per message and
  // returns a greeting message for that name
  val greeterWebSocketService =
  Flow[Message]
    .mapConcat {
      // we match but don't actually consume the text message here,
      // rather we simply stream it back as the tail of the response
      // this means we might start sending the response even before the
      // end of the incoming message has been received
      case tm: TextMessage =>
        val st = tm.textStream
        val p: Future[String] = st.runFold("")(_ ++ _)
        p.foreach { is =>
          val rez = new String(is.toArray)
          println(s"get $rez")
        }

        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }
  //#websocket-handler

  //#websocket-request-handling
  val requestHandler: HttpRequest => HttpResponse = {
    case req@HttpRequest(GET, Uri.Path("/greeter"), _, _, _) =>
      req.attribute(AttributeKeys.webSocketUpgrade) match {
        case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
        case None => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }
  //#websocket-request-handling

  val bindingFuture =
    Http().newServerAt("localhost", 8080).bindSync(requestHandler)

  println(s"GreeterWebSocketService online at ws://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
