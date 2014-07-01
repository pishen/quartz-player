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
import play.api.libs.openid.OpenID
import scala.concurrent.Future


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

  def login = Action.async { implicit request =>
    OpenID.redirectURL("https://www.google.com/accounts/o8/id",
      routes.Application.openIDCallback.absoluteURL())
      .map(url => Ok(views.html.login(url)))
  }

  def openIDCallback = Action.async { implicit request =>
    OpenID.verifiedId.map(info => {
      Redirect("/").withSession("user" -> info.id)
    })
  }

  val loginRedirect = Future { Redirect("/login") }

  def index = Action.async { request =>
    request.session.get("user") match {
      case None => loginRedirect
      case Some(user) => (handler ? GetJobs).mapTo[Elem].map(jobs => Ok(views.html.index(user, jobs)))
    }
  }

  //handle form submit
  val jobConfigForm = Form(mapping(
    "id" -> nonEmptyText,
    "email" -> email,
    "cmd" -> nonEmptyText,
    "cron" -> nonEmptyText,
    "error-only" -> boolean)(JobConfig.apply)(JobConfig.unapply))

  def add = Action { implicit request =>
    if (request.session.get("user").nonEmpty) {
      handler ! AddJob(jobConfigForm.bindFromRequest.get)
      Redirect("/")
    } else {
      Unauthorized("Who are you?")
    }

  }

  def update = Action { implicit request =>
    if (request.session.get("user").nonEmpty) {
      val jobConf = jobConfigForm.bindFromRequest.get
      handler ! UpdateJob(jobConf)
      Redirect("/job", Map("id" -> Seq(jobConf.id)))
    } else {
      Unauthorized("Who are you?")
    }
  }

  //
  def job = Action.async { request =>
    request.session.get("user") match {
      case None => loginRedirect
      case Some(user) => {
        val id = request.getQueryString("id").get
        for {
          job <- (handler ? GetJob(id)).mapTo[ActorRef]
          detail <- (job ? GetJobDetail).mapTo[Elem]
          executions <- (job ? GetExecutions).mapTo[Elem]
        } yield {
          Ok(views.html.job(user, id, detail, executions))
        }
      }
    }
  }

  def remove = Action(parse.tolerantJson) { request =>
    if (request.session.get("user").nonEmpty) {
      val json = request.body
      val id = (json \ "jobId").as[String]
      handler ! RemoveJob(id)
      Ok("removed")
    } else {
      Unauthorized("Who are you?")
    }
  }

  //
  def exec = Action.async { request =>
    request.session.get("user") match {
      case None => loginRedirect
      case Some(user) => {
        val jobId = request.getQueryString("id").get
        val execId = request.getQueryString("exec").get
        for {
          job <- (handler ? GetJob(jobId)).mapTo[ActorRef]
          exec <- (job ? GetExec(execId)).mapTo[ActorRef]
          output <- (exec ? GetOutput).mapTo[String]
        } yield {
          Ok(views.html.exec(user, jobId, execId, output))
        }
      }
    }
  }

  //adhoc
  def adhoc = Action.async { request =>
    request.session.get("user") match {
      case None => loginRedirect
      case Some(user) => {
        for {
          cmd <- (adhocActor ? GetAdhocCmd).mapTo[String]
          state <- (adhocActor ? GetState).mapTo[String]
          output <- (adhocActor ? GetOutput).mapTo[String]
        } yield {
          val (stateElem, running) = state match {
            case "running" => (<strong style="color:#006600">Running</strong>, true)
            case "error" => (<strong style="color:red">Error</strong>, false)
            case "finished" => (<span style="color:#006600">Finished</span>, false)
            case _ => (<span></span>, false)
          }
          Ok(views.html.adhoc(user, cmd, stateElem, output, running))
        }
      }
    }

  }

  val runAdhocForm = Form(mapping("cmd" -> nonEmptyText)(RunAdhoc.apply)(RunAdhoc.unapply))
  def runAdhoc = Action { implicit request =>
    if (request.session.get("user").nonEmpty) {
      adhocActor ! runAdhocForm.bindFromRequest.get
      Redirect("/adhoc")
    } else {
      Unauthorized("Who are you?")
    }
  }

  def killAdhoc = Action { request =>
    if (request.session.get("user").nonEmpty) {
      adhocActor ! KillAdhoc
      Redirect("/adhoc")
    } else {
      Unauthorized("Who are you?")
    }
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