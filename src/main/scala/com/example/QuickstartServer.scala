package com.example

//#quick-start-server
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Flow, Sink }

import java.util.concurrent.atomic.AtomicInteger
import scala.io.StdIn

object QuickstartServer extends App with UserRoutes {
  implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")

  val userRegistryActor: ActorRef = system.actorOf(UserRegistryActor.props, "userRegistryActor")
  lazy val routes: Route = userRoutes
//  Http().bindAndHandle(routes, "localhost", 8080)
//  println(s"Server online at http://localhost:8080/")
//  Await.result(system.whenTerminated, Duration.Inf)


  implicit val executionContext: ExecutionContext = system.dispatcher

  val currOpenConn = new AtomicInteger(0)
  val countRequests = new AtomicInteger(0)
  val maxConn = new AtomicInteger(0)
  var start, finish: Long = 0

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        "<html><body>Hello world!</body></html>"
      ))

    case HttpRequest(GET, Uri.Path("/users"), _, _, _) =>
      val tc = countRequests.incrementAndGet()

      HttpResponse(entity = "{users:[]}")

    case HttpRequest(GET, Uri.Path("/users/anna"), _, _, _) =>
      val tc = countRequests.incrementAndGet()

      HttpResponse(entity = "{\"age\":30,\"countryOfResidence\":\"Rus\",\"name\":\"anna\"}")

    case HttpRequest(GET, Uri.Path("/users/rom"), _, _, _) =>
      val tc = countRequests.incrementAndGet()

      HttpResponse(entity = "{\"age\":30,\"countryOfResidence\":\"Rus\",\"name\":\"rom\"}")

    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
      sys.error("BOOM!")

    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  def serverSource = Http().newServerAt("localhost", 8080).connectionSource()

  val react = Flow[IncomingConnection].watchTermination()((_, termination) => termination.onComplete(_ => currOpenConn.decrementAndGet()))

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection =>

      val tc = currOpenConn.incrementAndGet()
      if (maxConn.get() < tc) {
        maxConn.set(tc)
        if (tc == 1)
          start = System.currentTimeMillis()
      }

      connection.handleWith(
        Flow[HttpRequest].map(requestHandler)
          .watchTermination()((_, connClosedFuture) => {
            connClosedFuture.onComplete { _ =>
              val tc = currOpenConn.decrementAndGet()
              if (tc == 0)
                finish = System.currentTimeMillis()
            }
          })
      )
    }).run()

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete { _ =>
      val c = countRequests.get()
      val tho = c / ((finish - start).toDouble / 1000)
      println(s"count requests $c, connections ${maxConn.get()}, speed $tho requests in second")
      system.terminate()
    } // and shutdown when done


}
