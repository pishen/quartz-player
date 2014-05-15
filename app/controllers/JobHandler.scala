package controllers

import akka.actor.Actor
import java.io.File
import akka.actor.ActorRef
import akka.actor.Props
import us.theatr.akka.quartz.QuartzActor
import sys.process._
import akka.pattern.ask
import us.theatr.akka.quartz.AddCronSchedule
import akka.util.Timeout
import us.theatr.akka.quartz.AddCronScheduleSuccess
import akka.actor.Cancellable
import scala.concurrent.Await
import akka.actor.PoisonPill
import play.api.libs.json.JsValue
import us.theatr.akka.quartz.AddCronScheduleFailure
import play.api.libs.json.Json
import scalax.io.Resource
import java.io.FileWriter

class JobHandler extends Actor {
  implicit val timeout = Timeout(1000)
  implicit val ec = Application.ec

  def jobDir(id: String) = Application.jobsDir + "/" + id

  val quartz = context.actorOf(Props[QuartzActor])

  var jobMap = Map.empty[String, (ActorRef, Cancellable, JobConfig)]

  def receive = {
    case AddJob(conf, rewrite) => {
      if (!jobMap.contains(conf.id)) {
        Seq("mkdir", jobDir(conf.id)).!

        val job = context.actorOf(Props(classOf[Job], conf))

        val f = (quartz ? AddCronSchedule(job, conf.cron, RunJob, true))
        Await.result(f, timeout.duration) match {
          case AddCronScheduleSuccess(cancellable) => {
            jobMap += (conf.id -> (job, cancellable, conf))
            //rewrite jobs.json
            if (rewrite) rewriteJson()
          }
          case AddCronScheduleFailure => job ! PoisonPill
          case _ => //
        }
      }
    }
    case RemoveJob(id) => {
      if (jobMap.contains(id)) {
        val (job, cancellable, conf) = jobMap(id)
        quartz ! us.theatr.akka.quartz.RemoveJob(cancellable)
        job ! PoisonPill
        jobMap -= id
        //rewrite jobs.json
        rewriteJson()
      }
    }
    case GetJob(id) => {
      if (jobMap.contains(id)) {
        sender ! jobMap(id)._1
      }
    }
    case ListJobs => {
      val list = jobMap.keys.map(id => <li><a href={ "job?id=" + id }>{ id }</a></li>)
      sender ! <ul id="jobs">{ list }</ul>
    }
  }

  private def rewriteJson() = Resource.fromWriter(new FileWriter(Application.jobsJson)).write {
    Json.prettyPrint(Json.obj("jobs" -> jobMap.values.map(_._3.toJsObject)))
  }
}

case class AddJob(conf: JobConfig, rewrite: Boolean)
case class RemoveJob(id: String)
case class GetJob(id: String)
case object ListJobs