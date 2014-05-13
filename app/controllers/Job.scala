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

  def outputFile = execDir + "/output"
  def stateFile = execDir + "/exit-value"

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
    case History => {
      val history = new File(jobDir).listFiles()
        .filter(_.isDirectory()).map(h => <li>{ h.getName() }</li>)
      sender ! <ul>{ history }</ul>
    }
    case RunJob => {
      if (!running) {
        //start new execution
        execDir = jobDir + "/" + new SimpleDateFormat("MMdd-HHmmss").format(new Date())
        new File(execDir).mkdir()
        val script = jobDir + "/script"
        val newProcess = Seq("sh", script) run ProcessLogger(line => Resource.fromFile(outputFile).write(line + "\n"))
        process = newProcess
        future {
          blocking {
            val state = newProcess.exitValue
            self ! Finish(state)
          }
        }
        running = true
      }
    }
    case Finish(state) => {
      //change state to sleeping
      running = false
      //log exit value
      Resource.fromWriter(new FileWriter(stateFile)).write(state.toString)
      //TODO check if it's killed, success, or error, and decide whether to send email
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
case object History
case object RunJob
case class Finish(res: Int)
case object KillJob
