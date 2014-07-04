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
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.libs.ws.WS

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

  //authentication
  object AjaxAction extends AuthenticatedBuilder(
    request => request.session.get("user"),
    _ => Unauthorized("Please login"))

  object UserAction extends AuthenticatedBuilder[String](
    request => request.session.get("user"),
    _ => Redirect("/login"))

  def authInfo(email: String) = <div>Logged in as: { email } <a href="/logout">Logout</a></div>

  def login = Action { request =>
    if (request.session.get("user").isEmpty) {
      Ok(views.html.login(<div>Please log in</div>))
    } else {
      Redirect("/")
    }
  }

  case class UserInfo(email: String, password: String)
  val userForm = Form(mapping(
    "email" -> email,
    "password" -> nonEmptyText)(UserInfo.apply)(UserInfo.unapply))
  val authUrl = Resource.fromFile("auth-url").lines().head

  def loginPost = Action.async { implicit request =>
    val userInfo = userForm.bindFromRequest.get
    WS.url(authUrl).post(Map("acc" -> Seq(userInfo.email), "pwd" -> Seq(userInfo.password))).map {
      response =>
        println(response.body)
        if ((response.json \ "sid").asOpt[String].nonEmpty) {
          Redirect("/").withSession("user" -> userInfo.email)
        } else {
          Unauthorized("Email or password is wrong.")
        }
    }
  }
  
  def logout = Action {
    Redirect("/login").withNewSession
  }
  ////

  def index = UserAction.async { request =>
    (handler ? GetJobs).mapTo[Elem].map(jobs => Ok(views.html.index(authInfo(request.user), jobs)))
  }

  //handle form submit
  val jobConfigForm = Form(mapping(
    "id" -> nonEmptyText,
    "email" -> email,
    "cmd" -> nonEmptyText,
    "cron" -> nonEmptyText,
    "error-only" -> boolean)(JobConfig.apply)(JobConfig.unapply))

  def add = AjaxAction { implicit request =>
    handler ! AddJob(jobConfigForm.bindFromRequest.get)
    Redirect("/")
  }

  def update = AjaxAction { implicit request =>
    val jobConf = jobConfigForm.bindFromRequest.get
    handler ! UpdateJob(jobConf)
    Redirect("/job", Map("id" -> Seq(jobConf.id)))
  }

  //
  def job = UserAction.async { request =>
    val id = request.getQueryString("id").get
    for {
      job <- (handler ? GetJob(id)).mapTo[ActorRef]
      detail <- (job ? GetJobDetail).mapTo[Elem]
      executions <- (job ? GetExecutions).mapTo[Elem]
    } yield {
      Ok(views.html.job(authInfo(request.user), id, detail, executions))
    }
  }

  def remove = AjaxAction(parse.tolerantJson) { request =>
    val json = request.body
    val id = (json \ "jobId").as[String]
    handler ! RemoveJob(id)
    Ok("removed")
  }

  //
  def exec = UserAction.async { request =>
    val jobId = request.getQueryString("id").get
    val execId = request.getQueryString("exec").get
    for {
      job <- (handler ? GetJob(jobId)).mapTo[ActorRef]
      exec <- (job ? GetExec(execId)).mapTo[ActorRef]
      output <- (exec ? GetOutput).mapTo[String]
    } yield {
      Ok(views.html.exec(authInfo(request.user), jobId, execId, output))
    }
  }

  //adhoc
  def adhoc = UserAction.async { request =>
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
      Ok(views.html.adhoc(authInfo(request.user), cmd, stateElem, output, running))
    }
  }

  val runAdhocForm = Form(mapping("cmd" -> nonEmptyText)(RunAdhoc.apply)(RunAdhoc.unapply))
  def runAdhoc = AjaxAction { implicit request =>
    adhocActor ! runAdhocForm.bindFromRequest.get
    Redirect("/adhoc")
  }

  def killAdhoc = AjaxAction { request =>
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