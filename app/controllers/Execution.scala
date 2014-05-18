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
import akka.actor.ActorRef
import play.api.Logger
import java.io.File

class Execution(execId: String, conf: JobConfig) extends Actor {
  implicit val ec = Application.ec

  val execDir = conf.jobDir + "/" + execId
  val outputFile = execDir + "/output"
  val exitValFile = execDir + "/exit-value"

  val concatName = conf.id + "/" + execId

  //create folder
  Application.createDir(execDir)

  var running = false
  var process: Process = null

  override def postStop() {
    killIfRunning()
  }

  def receive = {
    case StartExec => {
      if (!running) {
        Logger.info("Fire " + concatName)
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
    case KillExec => killIfRunning()
    case Finish(exitVal) => {
      if (running) {
        //change state to sleeping
        running = false
        finishExec(exitVal)
      }
    }
    case GetState => {
      if (running) {
        sender ! <strong style="color:#006600">Running</strong>
      } else {
        if (new File(exitValFile).exists()) {
          if (Resource.fromFile(exitValFile).lines().head.toInt != 0) {
            sender ! <strong style="color:red">Error</strong>
          } else {
            sender ! <span style="color:#006600">Finished</span>
          }
        } else {
          sender ! <span>Initializing</span>
        }
      }
    }
    case GetOutput => {
      sender ! Resource.fromFile(outputFile).lines().mkString("\n")
    }
    case Clean => {
      killIfRunning()
      Seq("rm", "-r", execDir).!
      context.stop(self)
    }
    case AddWaitingExec(waiting) => {
      ???
    }
  }

  def killIfRunning() = {
    if (running) {
      process.destroy
      Logger.info(concatName + " killed")
      running = false
      finishExec(-1)
    }
  }

  def finishExec(exitVal: Int) = {
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

case class AddWaitingExec(waiting: ActorRef)
