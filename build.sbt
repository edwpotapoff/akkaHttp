
lazy val akkaHttpVersion = "10.5.1"
lazy val akkaVersion = "2.7.0"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.13.16"
    )),
    name := "akkaHttp",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
      "io.netty" % "netty-codec-http2" % "4.2.0.Final",
      "io.netty" % "netty-pkitesting" % "4.2.0.Final",
      "io.netty" % "netty-all" % "4.2.0.Final",
      "org.bouncycastle" % "bcprov-jdk15on" % "1.70" % "runtime",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.70" % "runtime",
      "com.typesafe.akka" %% "akka-stream" % akkaVersion
    )
  )
