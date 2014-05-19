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
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import scala.xml.Elem
import scala.concurrent.Future

class Job(conf: JobConfig) extends Actor {
  implicit val timeout = Timeout(5000)
  implicit val ec = Application.ec

  //create folder
  Application.createDir(conf.jobDir)

  var executions = new File(conf.jobDir).listFiles().filter(_.isDirectory())
    .map(execDir => {
      val execId = execDir.getName()
      execId -> context.actorOf(Props(classOf[Execution], execId, conf), execId)
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
      val execElems = executions.toSeq.sortBy(_._1).reverse.map {
        case (execId, actor) =>
          (actor ? GetState).mapTo[Elem].map(state => {
            <li><a href={ "exec?id=" + conf.id + "&exec=" + execId }>{ execId }</a> { state }</li>
          })
      }
      Future.sequence(execElems).map(elems => <ul>{ elems }</ul>) pipeTo sender
    }
    case StartNewExec(date) => {
      //start new execution
      val execId = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(date)
      val newExec = context.actorOf(Props(classOf[Execution], execId, conf), execId)
      executions += (execId -> newExec)
      newExec ! StartExec
      //clean old execs
      if (executions.size > 50) {
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

case class StartNewExec(date: Date)
case object GetJobDetail
case object GetExecutions
case class GetExec(id: String)
