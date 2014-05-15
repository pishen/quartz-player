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

object Application extends Controller {
  implicit val timeout = Timeout(5000)
  implicit val ec = concurrent.ExecutionContext.Implicits.global
  
  //actors
  val handler = Akka.system.actorOf(Props[JobHandler])
  
  //create folder
  val jobsDir = "jobs"
  Seq("mkdir", jobsDir).!
  
  //files
  val jobsJson = "jobs.json"
  if(! new File(jobsJson).exists()) Resource.fromWriter(new FileWriter(jobsJson)).write("{\"jobs\":[]}")
  
  //load current jobs
  private def addJob(json: JsValue, rewrite: Boolean) = {
    val id = (json \ "id").as[String]
    val email = (json \ "email").as[String]
    val cmd = (json \ "cmd").as[String]
    val cron = (json \ "cron").as[String]

    handler ! AddJob(JobConfig(id, email, cmd, cron), rewrite)
  }
  //load from jobs.json
  (Json.parse(Resource.fromFile(jobsJson).string) \ "jobs").as[Seq[JsObject]].map(json => addJob(json, false))

  def index = Action.async {
    (handler ? ListJobs).mapTo[Elem].map(jobs => Ok(views.html.index(jobs)))
  }

  def add = Action(parse.tolerantJson) { request =>
    val json = request.body
    addJob(json, true)
    Ok("added")
  }

  def job = Action.async { request =>
    val id = request.getQueryString("id").get
    (handler ? GetJob(id)).mapTo[ActorRef].flatMap(job => {
      for{
        detail <- (job ? JobDetail).mapTo[Elem]
        history <- (job ? History).mapTo[Elem]
      } yield {
        Ok(views.html.job(id, detail, history))
      }
    })
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