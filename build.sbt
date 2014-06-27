name := "play-with-quartz"

version := "dev"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.quartz-scheduler"          %  "quartz"        % "2.2.1",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2")

lazy val root = (project in file(".")).enablePlugins(PlayScala)
