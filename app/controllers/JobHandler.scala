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

class JobHandler extends Actor {
  implicit val timeout = Application.timeout
  implicit val ec = Application.ec
  //create folder
  val jobsDir = "jobs"
  Seq("mkdir", jobsDir).!

  def getJobDir(id: String) = jobsDir + "/" + id

  val quartz = context.actorOf(Props[QuartzActor])

  var jobMap = Map.empty[String, (ActorRef, Cancellable)]

  def receive = {
    case add: AddJob => {
      if (!jobMap.contains(add.id)) {
        Seq("mkdir", getJobDir(add.id)).!

        val job = context.actorOf(Props(classOf[Job], add.id, add.email, add.cmd, add.cron, getJobDir(add.id)))

        val f = (quartz ? AddCronSchedule(job, add.cron, RunJob, true))
        Await.result(f, timeout.duration) match {
          case AddCronScheduleSuccess(cancellable) => {
            jobMap += (add.id -> (job, cancellable))
          }
          case _ => //failed
        }
      }
    }
    case RemoveJob(id) => {
      if (jobMap.contains(id)) {
        val (job, cancellable) = jobMap(id)
        quartz ! us.theatr.akka.quartz.RemoveJob(cancellable)
        job ! PoisonPill
        jobMap -= id
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
}

case class AddJob(id: String, email: String, cmd: String, cron: String)
case class RemoveJob(id: String)
case class GetJob(id: String)
case object ListJobs