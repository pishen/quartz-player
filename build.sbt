import play.Project._

name := "akka-quartz-test"

scalaVersion := "2.10.4"

libraryDependencies += "us.theatr" %% "akka-quartz" % "0.2.0"

libraryDependencies += "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2"

libraryDependencies += "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2"

resolvers += "theatr.us" at "http://repo.theatr.us"

playScalaSettings
