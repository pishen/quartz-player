import play.Project._

name := "akka-quartz-test"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "us.theatr"                     %% "akka-quartz"   % "0.2.0",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2")

playScalaSettings
