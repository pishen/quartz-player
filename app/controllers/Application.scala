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

object Application extends Controller {
  implicit val timeout = Timeout(5000)
  implicit val ec = concurrent.ExecutionContext.Implicits.global
  //actors
  val handler = Akka.system.actorOf(Props[JobHandler])

  def index = Action.async {
    (handler ? ListJobs).mapTo[Elem].map(jobs => Ok(views.html.index(jobs)))
  }

  def add = Action(parse.tolerantJson) { request =>
    val json = request.body
    val id = (json \ "jobId").as[String]
    val email = (json \ "email").as[String]
    val cmd = (json \ "cmd").as[String]
    val cron = (json \ "cron").as[String]

    handler ! AddJob(id, email, cmd, cron)
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