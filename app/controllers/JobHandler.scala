package controllers

import java.io.FileWriter
import java.util.Properties

import scala.collection.JavaConverters.mapAsJavaMapConverter

import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import scalax.io.Resource

class JobHandler extends Actor {
  implicit val timeout = Timeout(5000)
  implicit val ec = Application.ec

  //quartz
  val props = new Properties()
  props.setProperty("org.quartz.scheduler.instanceName", "JobHandler")
  props.setProperty("org.quartz.threadPool.threadCount", "1")
  props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
  val scheduler = new StdSchedulerFactory(props).getScheduler
  scheduler.start()
  Logger.info("Quartz scheduler started")

  override def postStop() {
    scheduler.shutdown()
    Logger.info("Quartz scheduler shutdown")
  }

  //load jobs from jobs.json
  (Json.parse(Resource.fromFile(Application.jobsJson).string) \ "jobs").as[Seq[JsObject]].foreach(json => {
    val id = (json \ "id").as[String]
    val email = (json \ "email").as[String]
    val cmd = (json \ "cmd").as[String]
    val cron = (json \ "cron").as[String]
    val errorOnly = (json \ "errorOnly").as[Boolean]

    self ! AddJob(JobConfig(id, email, cmd, cron, errorOnly), false)
  })

  var jobMap = Map.empty[String, (ActorRef, JobKey, JobConfig)]

  def receive = {
    case AddJob(conf, rewrite) => {
      if (!jobMap.contains(conf.id)) {
        val job = context.actorOf(Props(classOf[Job], conf), conf.id)

        try {
          //quartz
          val quartzJobDetail = JobBuilder
            .newJob(classOf[QuartzJob])
            .usingJobData(new JobDataMap(Map("actor" -> job).asJava))
            .build()
          val quartzTrigger = TriggerBuilder
            .newTrigger()
            .withSchedule(CronScheduleBuilder.cronSchedule(conf.cron))
            .forJob(quartzJobDetail)
            .build()
          scheduler.scheduleJob(quartzJobDetail, quartzTrigger)

          jobMap += (conf.id -> (job, quartzJobDetail.getKey(), conf))
          //rewrite jobs.json
          if (rewrite) rewriteJson()
          
          Logger.info("Job " + conf.id + " added.")
        } catch {
          case e: Throwable =>
            job ! PoisonPill
            Logger.error("Failed to add job " + conf.id, e)
        }
      }
    }
    case RemoveJob(id) => {
      if (jobMap.contains(id)) {
        val (job, quartzJobKey, conf) = jobMap(id)
        scheduler.deleteJob(quartzJobKey)
        job ! PoisonPill
        jobMap -= id
        //rewrite jobs.json
        rewriteJson()
        Logger.info("Job " + id + " removed.")
      }
    }
    case GetJob(id) => {
      if (jobMap.contains(id)) {
        sender ! jobMap(id)._1
      }
    }
    case GetJobs => {
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
case object GetJobs

class QuartzJob extends org.quartz.Job {
  def execute(context: JobExecutionContext) {
    context.getJobDetail
      .getJobDataMap()
      .get("actor")
      .asInstanceOf[ActorRef] ! StartNewExec(context.getScheduledFireTime())
  }
}