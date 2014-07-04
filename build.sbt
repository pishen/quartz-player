name := "quartz-player"

version := "dev"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  ws,
  "org.quartz-scheduler"          %  "quartz"        % "2.2.1",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3-1")

lazy val root = (project in file(".")).enablePlugins(PlayScala)
