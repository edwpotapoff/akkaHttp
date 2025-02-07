package loger

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.util.ByteString
import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object Proxy extends App {

  var bindingFuture: Future[Http.ServerBinding] = null
  val transactions = scala.collection.mutable.Buffer[String]()

  implicit var system: ActorSystem = null
  implicit var executionContext: ExecutionContext = null


  system = ActorSystem("proxy")
  executionContext = system.dispatcher

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(method, uri, headers, entity, protocol) =>

      val hf = headers.filter(_.name() != "Timeout-Access")
      println(s"$method $uri $protocol ")
      hf.foreach { h =>
        println(s"${h.name()}: ${h.value()}")
      }

      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        try {
          val str = body.utf8String
          println(str)

          val ct = entity.contentType.value
          println(ct)

        }
        catch {
          case e: Throwable =>
            e.printStackTrace()
        }
      }

      HttpResponse(entity = "Ok")

  }

  val port = 1234
  bindingFuture = Http().newServerAt("localhost", port).bindSync(requestHandler)
  println(s"Proxy online at http://localhost:$port/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  exit()


  def exit(): Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete {
        _ =>
          system.terminate()
          System.exit(0)
      } // and shutdown when done
  }
}