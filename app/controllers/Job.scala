package controllers

import akka.actor.Actor
import scalax.io.Resource
import java.io.FileWriter
import sys.process._
import java.io.File
import concurrent.{ future, blocking }
import java.text.SimpleDateFormat
import java.util.Date

class Job(id: String,
  email: String,
  cmd: String,
  cron: String,
  jobDir: String) extends Actor {

  implicit val ec = Application.ec

  var running = false
  var execDir = ""
  var process: Process = null

  Resource.fromWriter(new FileWriter(jobDir + "/script")).write(cmd)

  def outputFile = new File(execDir + "/output")
  def resultFile = new File(execDir + "/result")

  def receive = {
    case JobState => sender ! (if (running) "running" else "sleeping")
    case JobDetail => {
      val detail =
        <ul>
          <li>id: { id }</li>
          <li>email: { email }</li>
          <li>cmd: { cmd }</li>
          <li>cron: { cron }</li>
        </ul>
      sender ! detail
    }
    case LastOutput => sender ! Resource.fromFile(outputFile).lines().toSeq
    case RunJob => {
      if (!running) {
        //start new execution
        execDir = jobDir + "/" + new SimpleDateFormat("MMdd-HHmmss").format(new Date())
        new File(execDir).mkdir()
        val newProcess = "sh script" run ProcessLogger(outputFile)
        process = newProcess
        future {
          blocking {
            val res = newProcess.exitValue
            self ! Finish(res)
          }
        }
        running = true
      }
    }
    case Finish(res) => {
      //change state to sleeping
      running = false
      //log result into file
      Resource.fromWriter(new FileWriter(resultFile)).write(res.toString)
      //send email
      val output = Resource.fromFile(outputFile).lines().mkString("\n")
      val content = "The result of your command is:\n\n" + output
      (Seq("echo", content) #| Seq("mail", "-s", "From Akka Quartz", email)).!
    }
    case KillJob => process.destroy
  }
}

case object JobState
case object JobDetail
case object LastOutput
case object RunJob
case class Finish(res: Int)
case object KillJob
