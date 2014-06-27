package controllers

import akka.actor.Props
import akka.pattern.ask
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.Play.current
import play.api.libs.concurrent.Akka
import akka.util.Timeout
import akka.actor.ActorRef
import scala.xml.Elem
import sys.process._
import play.api.libs.json.JsValue
import scalax.io.Resource
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import java.io.File
import java.io.FileWriter
import play.api.data.Form
import play.api.data.Forms._
import play.api.Logger
import scala.concurrent.duration.DurationInt

object Application extends Controller {
  implicit val timeout = Timeout(5.seconds)
  implicit val ec = concurrent.ExecutionContext.Implicits.global

  //create folder
  def createDir(dir: String) = {
    val f = new File(dir)
    if (!f.exists()) f.mkdir()
  }
  val jobsDir = "jobs"
  createDir(jobsDir)

  //files
  val jobsJson = "jobs.json"
  if (!new File(jobsJson).exists()) Resource.fromWriter(new FileWriter(jobsJson)).write("{\"jobs\":[]}")

  //actors
  val handler = Akka.system.actorOf(Props[JobHandler], "handler")
  val adhocActor = Akka.system.actorOf(Props[Adhoc], "adhoc")

  def index = Action.async {
    (handler ? GetJobs).mapTo[Elem].map(jobs => Ok(views.html.index(jobs)))
  }

  //handle form submit
  val jobConfigForm = Form(mapping(
    "id" -> nonEmptyText,
    "email" -> email,
    "cmd" -> nonEmptyText,
    "cron" -> nonEmptyText,
    "error-only" -> boolean)(JobConfig.apply)(JobConfig.unapply))

  def add = Action { implicit request =>
    handler ! AddJob(jobConfigForm.bindFromRequest.get)
    Redirect("/")
  }
  
  def update = Action { implicit request => 
    val jobConf = jobConfigForm.bindFromRequest.get
    handler ! UpdateJob(jobConf)
    Redirect("/job", Map("id" -> Seq(jobConf.id)))
  }

  //
  def job = Action.async { request =>
    val id = request.getQueryString("id").get
    for {
      job <- (handler ? GetJob(id)).mapTo[ActorRef]
      detail <- (job ? GetJobDetail).mapTo[Elem]
      executions <- (job ? GetExecutions).mapTo[Elem]
    } yield {
      Ok(views.html.job(id, detail, executions))
    }
  }

  def remove = Action(parse.tolerantJson) { request =>
    val json = request.body
    val id = (json \ "jobId").as[String]
    handler ! RemoveJob(id)
    Ok("removed")
  }

  //
  def exec = Action.async { request =>
    val jobId = request.getQueryString("id").get
    val execId = request.getQueryString("exec").get
    for {
      job <- (handler ? GetJob(jobId)).mapTo[ActorRef]
      exec <- (job ? GetExec(execId)).mapTo[ActorRef]
      output <- (exec ? GetOutput).mapTo[String]
    } yield {
      Ok(views.html.exec(jobId, execId, output))
    }
  }

  //adhoc
  def adhoc = Action.async { request =>
    for {
      cmd <- (adhocActor ? GetAdhocCmd).mapTo[String]
      state <- (adhocActor ? GetState).mapTo[String]
      output <- (adhocActor ? GetOutput).mapTo[String]
    } yield {
      state match {
        case "running" =>
          Ok(views.html.adhoc(cmd, <strong style="color:#006600">Running</strong>, output, true))
        case "error" =>
          Ok(views.html.adhoc(cmd, <strong style="color:red">Error</strong>, output, false))
        case "finished" =>
          Ok(views.html.adhoc(cmd, <span style="color:#006600">Finished</span>, output, false))
        case _ =>
          Ok(views.html.adhoc(cmd, <span></span>, output, false))
      }
    }
  }

  val runAdhocForm = Form(mapping("cmd" -> nonEmptyText)(RunAdhoc.apply)(RunAdhoc.unapply))
  def runAdhoc = Action { implicit request =>
    adhocActor ! runAdhocForm.bindFromRequest.get
    Redirect("/adhoc")
  }

  def killAdhoc = Action {
    adhocActor ! KillAdhoc
    Redirect("/adhoc")
  }

  //
  def state = Action { request =>
    ???
  }

  def detail = Action { request =>
    ???
  }

  def output = Action { request =>
    ???
  }

}