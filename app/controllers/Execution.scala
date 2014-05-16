package controllers

import java.io.FileWriter

import scala.concurrent.blocking
import scala.concurrent.future
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringSeqToProcess

import akka.actor.Actor
import akka.actor.actorRef2Scala
import scalax.io.Resource

class Execution(execId: String, conf: JobConfig) extends Actor {
  implicit val ec = Application.ec

  val execDir = conf.jobDir + "/" + execId
  val outputFile = execDir + "/output"
  val exitValFile = execDir + "/exit-value"

  //create folder
  Application.createDir(execDir)

  var running = false
  var process: Process = null

  def receive = {
    case StartExec => {
      if (!running) {
        val logger = ProcessLogger(line => Resource.fromFile(outputFile).write(line + "\n"))
        val newProcess = Seq("sh", "-c", conf.cmd) run logger
        process = newProcess
        future {
          blocking {
            val exitVal = newProcess.exitValue
            self ! Finish(exitVal)
          }
        }
        running = true
      }
    }
    case KillExec => process.destroy
    case Finish(exitVal) => {
      //change state to sleeping
      running = false
      //log exit value
      Resource.fromWriter(new FileWriter(exitValFile)).write(exitVal.toString)

      //send email
      val output = Resource.fromFile(outputFile).lines().mkString("\n")
      val content = "The output is included below:\n\n" + output
      if (exitVal != 0) {
        //error
        sendMail("[KKQuartz] ERROR in job " + conf.id, content)
      } else if (!conf.errorOnly) {
        //result
        sendMail("[KKQuartz] Finished job: " + conf.id, content)
      }
    }
    case GetState => {

    }
    case GetOutput => {

    }
    case Clean => {
      Seq("rm", "-r", execDir).!
      context.stop(self)
    }
  }
  
  def sendMail(subject: String, content: String) = {
    (Seq("echo", content) #| Seq("mail", "-s", subject, conf.email)).!
  }
}

case object StartExec
case object KillExec
case class Finish(exitVal: Int)
case object GetState
case object GetOutput
case object Clean
