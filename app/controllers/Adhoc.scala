package controllers

import java.io.File
import java.io.FileWriter

import scala.concurrent.Future
import scala.concurrent.blocking
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringSeqToProcess

import akka.actor.Actor
import akka.actor.actorRef2Scala
import play.api.Logger
import scalax.io.Resource

class Adhoc extends Actor {
  implicit val ec = Application.ec

  val execDir = "/tmp/kkquartz-adhoc"
  val outputFile = execDir + "/output"
  val exitValFile = execDir + "/exit-value"

  //create folder
  Application.createDir(execDir)

  var command = ""
  var running = false
  var process: Process = null

  override def postStop() {
    killIfRunning()
  }

  def receive = {
    case RunAdhoc(cmd) => {
      if (!running) {
        Logger.info("Adhoc " + cmd)
        //clean old files
        new File(execDir).listFiles().foreach(_.delete())
        
        command = cmd
        val logger = ProcessLogger(line => Resource.fromFile(outputFile).write(line + "\n"))
        val newProcess = Seq("sh", "-c", cmd) run logger
        process = newProcess
        Future {
          blocking {
            val exitVal = newProcess.exitValue
            self ! Finish(exitVal)
          }
        }
        running = true
      }
    }
    case KillAdhoc => killIfRunning()
    case Finish(exitVal) => {
      if (running) {
        //change state to sleeping
        running = false
        finishExec(exitVal)
      }
    }
    case GetAdhocCmd => sender ! command
    case GetState => {
      if (running) {
        sender ! "running"
      } else {
        if (new File(exitValFile).exists()) {
          val exitVal = Resource.fromFile(exitValFile).lines().head.toInt
          if (exitVal != 0) {
            sender ! "error"
          } else {
            sender ! "finished"
          }
        } else {
          sender ! "none"
        }
      }
    }
    case GetOutput => {
      sender ! Resource.fromFile(outputFile).lines().mkString("\n")
    }
  }

  def killIfRunning() = {
    if (running) {
      process.destroy
      Logger.info("Adhoc " + command + " killed")
      running = false
      finishExec(-1)
    }
  }

  def finishExec(exitVal: Int) = {
    //log exit value
    Resource.fromWriter(new FileWriter(exitValFile)).write(exitVal.toString)
  }
}

case class RunAdhoc(cmd: String)
case object KillAdhoc
case object GetAdhocCmd
