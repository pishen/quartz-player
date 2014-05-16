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

object Application extends Controller {
  implicit val timeout = Timeout(5000)
  implicit val ec = concurrent.ExecutionContext.Implicits.global

  //actors
  val handler = Akka.system.actorOf(Props[JobHandler])

  //create folder
  def createDir(dir: String) = {
    val f = new File(dir)
    if(!f.exists()) f.mkdir()
  }
  val jobsDir = "jobs"
  createDir(jobsDir)

  //files
  val jobsJson = "jobs.json"
  if (!new File(jobsJson).exists()) Resource.fromWriter(new FileWriter(jobsJson)).write("{\"jobs\":[]}")

  //load from jobs.json
  //TODO catch exception and log here
  (Json.parse(Resource.fromFile(jobsJson).string) \ "jobs").as[Seq[JsObject]].foreach(json => {
    val id = (json \ "id").as[String]
    val email = (json \ "email").as[String]
    val cmd = (json \ "cmd").as[String]
    val cron = (json \ "cron").as[String]
    val errorOnly = (json \ "errorOnly").as[Boolean]

    handler ! AddJob(JobConfig(id, email, cmd, cron, errorOnly), false)    
  })

  def index = Action.async {
    (handler ? ListJobs).mapTo[Elem].map(jobs => Ok(views.html.index(jobs)))
  }

  //handle form submit
  val jobConfigForm = Form(mapping(
    "id" -> nonEmptyText,
    "email" -> email,
    "cmd" -> nonEmptyText,
    "cron" -> nonEmptyText,
    "error-only" -> boolean)(JobConfig.apply)(JobConfig.unapply))

  def add = Action { implicit request =>
    handler ! AddJob(jobConfigForm.bindFromRequest.get, true)
    Redirect("/")
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