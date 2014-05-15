package controllers

import akka.actor.Actor
import scalax.io.Resource
import java.io.FileWriter
import sys.process._
import java.io.File
import concurrent.{ future, blocking }
import java.text.SimpleDateFormat
import java.util.Date

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
        </ul>
      sender ! detail
    }
    case History => {
      val history = new File(jobDir).listFiles().map(h => <li>{ h.getName() }</li>)
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
      }else{
        //TODO log a failed exec
        
      }
    }
    case Finish(exitVal) => {
      //change state to sleeping
      running = false
      //log exit value
      Resource.fromWriter(new FileWriter(exitValFile)).write(exitVal.toString)
      //TODO check if it's killed, success, or error, and decide whether to send email
      //result email to check the exec result
      //error email to send when exitVal != 0
      val output = Resource.fromFile(outputFile).lines().mkString("\n")
      val content = "The result of your command is:\n\n" + output
      (Seq("echo", content) #| Seq("mail", "-s", "From Akka Quartz", conf.email)).!
    }
    case KillJob => process.destroy
  }
}

case class JobConfig(id: String, email: String, cmd: String, cron: String){
  //TODO validate config if needed
  require(id.replaceAll("\\w", "") == "")
}

case object JobState
case object JobDetail
case object History
case object RunJob
case class Finish(exitVal: Int)
case object KillJob
