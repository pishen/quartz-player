package controllers

import akka.util.Timeout
import akka.actor.Props
import akka.pattern.ask
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.Play.current
import play.api.libs.concurrent.Akka
import us.theatr.akka.quartz._

object Application extends Controller {
  implicit val timeout = Timeout(5000)
  //actors
  val quartz = Akka.system.actorOf(Props[QuartzActor])
  val job = Akka.system.actorOf(Props[Job])

  def index = Action {
    Ok(views.html.index())
  }
  
  def add = Action { request =>
    println("add")
    //val jobName = request.getQueryString("job-name").get
    val email = request.getQueryString("email").get
    val cmd = request.getQueryString("cmd").get
    val cron = request.getQueryString("cron").get
    quartz ? AddCronSchedule(job, cron, Run(cmd, email), true)
    Ok("added")
  }
  
  def cancel = Action {request =>
    
    
    Ok("canceled")
  }

}