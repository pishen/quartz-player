package controllers

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import scala.Array.canBuildFrom

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

class Job(conf: JobConfig) extends Actor {
  implicit val ec = Application.ec

  //create folder
  Application.createDir(conf.jobDir)

  var executions = new File(conf.jobDir).listFiles().filter(_.isDirectory())
    .map(execDir => {
      val execId = execDir.getName()
      execId -> context.actorOf(Props(classOf[Execution], execId, conf))
    }).toMap

  def receive = {
    case GetJobDetail => {
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
    case GetExecutions => {
      val lis = executions.keys.toSeq.sorted.reverse.map(exec => {
        <li><a href={ "exec?id=" + conf.id + "&exec=" + exec }>{ exec }</a></li>
      })
      sender ! <ul>{ lis }</ul>
    }
    case StartNewExec => {
      //start new execution
      val execId = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(new Date())
      val newExec = context.actorOf(Props(classOf[Execution], execId, conf))
      executions += (execId -> newExec)
      newExec ! StartExec
      //clean old execs
      if (executions.size > 10) {
        val oldest = executions.keys.min
        val oldestExec = executions(oldest)
        executions -= oldest
        oldestExec ! Clean
      }
    }
    case GetExec(id) => {
      executions.get(id) match {
        case None => //
        case Some(exec) => sender ! exec
      }
    }
  }
}

case class JobConfig(id: String, email: String, cmd: String, cron: String, errorOnly: Boolean) {
  //TODO validate config if needed
  require(id.replaceAll("[\\w-]", "") == "")

  def jobDir = Application.jobsDir + "/" + id

  def toJsObject = Json.obj("id" -> id, "email" -> email, "cmd" -> cmd, "cron" -> cron, "errorOnly" -> errorOnly)
}

case object StartNewExec
case object GetJobDetail
case object GetExecutions
case class GetExec(id: String)
