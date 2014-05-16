package controllers

import akka.actor.Actor
import scalax.io.Resource
import java.io.FileWriter
import sys.process._
import java.io.File
import concurrent.{ future, blocking }
import java.text.SimpleDateFormat
import java.util.Date
import play.api.libs.json.Json

class Job(conf: JobConfig) extends Actor {
  implicit val ec = Application.ec

  val jobDir = Application.jobsDir + "/" + conf.id

  var running = false
  var lastExec = ""
  var process: Process = null

  def execDir = jobDir + "/" + lastExec
  def outputFile = execDir + "/output"
  def exitValFile = execDir + "/exit-value"

  def receive = {
    case JobState => sender ! (if (running) "running" else "sleeping")
    case JobDetail => {
      val detail =
        <ul>
          <li>id: { conf.id }</li>
          <li>email: { conf.email }</li>
          <li>cmd: { conf.cmd }</li>
          <li>cron: { conf.cron }</li>
          <li>errorOnly: { conf.errorOnly }</li>
        </ul>
      sender ! detail
    }
    case History => {
      val history = new File(jobDir).listFiles().map(_.getName()).sorted.reverse
        .map(name => <li>{ name }</li>)
      sender ! <ul>{ history }</ul>
    }
    case RunJob => {
      if (!running) {
        //start new execution
        lastExec = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(new Date())
        new File(execDir).mkdir()

        val newProcess = Seq("sh", "-c", conf.cmd)
          .run(ProcessLogger(line => Resource.fromFile(outputFile).write(line + "\n")))
        process = newProcess
        future {
          blocking {
            val exitVal = newProcess.exitValue
            self ! Finish(exitVal)
          }
        }
        running = true
      } else {
        //TODO log a failed exec

      }
    }
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
    case KillJob => process.destroy
  }

  def sendMail(subject: String, content: String) = {
    (Seq("echo", content) #| Seq("mail", "-s", subject, conf.email)).!
  }
}

case class JobConfig(id: String, email: String, cmd: String, cron: String, errorOnly: Boolean) {
  //TODO validate config if needed
  require(id.replaceAll("[\\w-]", "") == "")

  def toJsObject = Json.obj("id" -> id, "email" -> email, "cmd" -> cmd, "cron" -> cron, "errorOnly" -> errorOnly)
}

case object JobState
case object JobDetail
case object History
case object RunJob
case class Finish(exitVal: Int)
case object KillJob
