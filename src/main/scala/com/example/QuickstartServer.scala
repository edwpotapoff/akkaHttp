package com.example

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.HttpMethods.{GET, POST}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object QuickstartServer extends App with UserRoutes {
  implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")

  val userRegistryActor: ActorRef = system.actorOf(UserRegistryActor.props, "userRegistryActor")
  lazy val routes: Route = userRoutes

  implicit val executionContext: ExecutionContext = system.dispatcher

  val currOpenConn = new AtomicInteger(0)
  val countRequests = new AtomicInteger(0)
  val maxConn = new AtomicInteger(0)
  var start: Long = 0

  import scala.util.matching.Regex

  val str = "([0-9a-zA-Z- ]+): ([0-9a-zA-Z-#()/. ]+)"
  val keyValPattern: Regex = str.r

  val input: String =
    """background-color: #A03300;
      |background-image: url(img/header100.png);
      |background-position: top center;
      |background-repeat: repeat-x;
      |background-size: 2160px 108px;
      |margin: 0;
      |height: 108px;
      |width: 100%;""".stripMargin

  for (patternMatch <- keyValPattern.findAllMatchIn(input))
    println(s"key: ${patternMatch.group(1)} value: ${patternMatch.group(2)}")

  def printA(a: Any) = a match {
    case () => println("Unit")
      println(a)

    case _ => println(a)
  }

  printA(())
  printA("что-то")

  def a(): Unit = {
    println("ничего")
  }

  printA(a())

  /*val password: Array[Char] = "123456".toCharArray // do not store passwords in code, read them from somewhere safe!

  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystore: InputStream = new FileInputStream("c:/temp/keystore.jks") // getClass.getClassLoader.getResourceAsStream("server.p12")

  require(keystore != null, "Keystore required!")
  ks.load(keystore, password)

  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)

  val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  tmf.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

  val https = ConnectionContext.httpsServer(sslContext)
   */


  def parse(query: String) = {
    val params: Array[String] = query.split("&")
    params.map { p =>
      val pk = p.split("=")
      s"key = ${pk(0)} value = ${pk(1)}"
    }.mkString("\n")
  }

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
        //        val st = tm.textStream
        //        val p: Future[String] = st.runFold("")(_ ++ _)
        //        p.foreach { is =>
        //          val rez = new String(is.toArray)
        //          println(s"get $rez")
        //        }

        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        BinaryMessage(Source.single(ByteString("Hello ")) ++ bm.dataStream ++ Source.single(ByteString("!"))) :: Nil
      //bm.dataStream.runWith(Sink.ignore)
      //Nil
    }
  //#websocket-handler
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      Future(HttpResponse(entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        "<html><body>Hello world!</body></html>"
      )))

    case HttpRequest(GET, Uri.Path("/users"), _, _, _) =>
      val tc = countRequests.incrementAndGet()
      Future(HttpResponse(entity = "{users:[]}"))

    case HttpRequest(GET, Uri.Path("/users/anna"), _, _, _) =>
      val tc = countRequests.incrementAndGet()
      Future(HttpResponse(entity = "{\"age\":30,\"countryOfResidence\":\"Rus\",\"name\":\"anna\"}"))

    case HttpRequest(GET, Uri.Path("/users/rom"), _, _, _) =>
      val tc = countRequests.incrementAndGet()
      //     if( tc%2 == 1 )
      //        Thread.sleep(50)
      Future(HttpResponse(entity = "{\"age\":30,\"countryOfResidence\":\"Rus\",\"name\":\"rom\"}"))

    case HttpRequest(GET, Uri.Path("/books"), _, _, _) =>
      val tc = countRequests.incrementAndGet()
      //     if( tc%2 == 1 )
      //        Thread.sleep(50)
      Future(HttpResponse(entity =
        """{
          |  "books": [
          |    {
          |      "title": "The Great Gatsby",
          |      "author": "F. Scott Fitzgerald",
          |      "year": 1925,
          |      "genre": "novel"
          |    },
          |    {
          |      "title": "The Catcher in the Rye",
          |      "author": "J.D. Salinger",
          |      "year": 1951,
          |      "genre": "novel"
          |    },
          |    {
          |      "title": "1984",
          |      "author": "George Orwell",
          |      "year": 1949,
          |      "genre": "novel"
          |    }
          |  ]
          |}
          |""".stripMargin))

    case HttpRequest(GET, r@Uri.Path("/page"), _, _, _) =>
      countRequests.incrementAndGet()
      r.queryString() match {
        case Some(query) =>
          Future(HttpResponse(entity = parse(query)))
        case None =>
          Future(HttpResponse(404, entity = "Unknown resource!"))
      }

    case HttpRequest(POST, Uri.Path("/new"), attributes, entity, protocol) =>
      countRequests.incrementAndGet()
      val p: Future[ByteString] = entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      p.map { query =>
        HttpResponse(entity = parse(new String(query.toArray)))
      }
    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
      Future(sys.error("BOOM!"))
    case req@HttpRequest(GET, Uri.Path("/greeter"), _, _, _) =>
      countRequests.incrementAndGet()
      req.attribute(AttributeKeys.webSocketUpgrade) match {
        case Some(upgrade) => Future(upgrade.handleMessages(greeterWebSocketService))
        case None => Future(HttpResponse(400, entity = "Not a valid websocket request!"))
      }


    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      Future(HttpResponse(404, entity = "Unknown resource!"))
  }

  def serverSource = Http().newServerAt("localhost", 8080) /*.enableHttps(https)*/ .connectionSource()

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection: IncomingConnection =>

      val tc = currOpenConn.incrementAndGet()
      if (maxConn.get() < tc) {
        maxConn.set(tc)
        if (tc == 1)
          start = System.nanoTime()
      }

      connection.handleWith(
        Flow[HttpRequest].mapAsync(1)(requestHandler)
          .watchTermination()((_, connClosedFuture) => {
            connClosedFuture.onComplete { _ =>
              val tc = currOpenConn.decrementAndGet()
              if (tc == 0) {
                val finish = System.nanoTime()
                val c = countRequests.get()
                val tho = c / ((finish / 1000000D - start / 1000000D) / 1000)
                println(s"count requests $c, connections ${maxConn.get()}, speed $tho RPS")
                maxConn.set(0)
                countRequests.set(0)
              }
            }
          })
      )
    }).run()

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")


  StdIn.readLine() // let it run until user presses return
  StdIn.readLine() // let it run until user presses return

  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete { _ =>
      system.terminate()
    } // and shutdown when done


}
