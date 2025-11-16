name := "agent-ai"

version := "1.0"

scalaVersion := s"3.3.7"

lazy val pekkoVersion = "1.0.2"
lazy val pekkoHttpVersion = "1.0.1"
lazy val circeVersion = "0.14.6"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

// Disable forking for run task to allow stdin input
run / fork := false

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion,
  "ch.megard" %% "pekko-http-cors" % "1.0.0",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)
