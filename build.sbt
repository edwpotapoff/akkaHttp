
val PekkoVersion = "1.1.2"
val PekkoHttpVersion = "1.1.0"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "3.3.3"
    )),
    name := "akkaHttp",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
      "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion
    )
  )
