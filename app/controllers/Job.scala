package controllers

import akka.actor.Actor
import javax.mail._
import javax.mail.internet._
import javax.activation._
import scalax.io.Resource
import java.io.FileWriter
import sys.process._

class Job extends Actor {
  
  
  def receive = {
    case Run(cmd, email) => {
      Resource.fromWriter(new FileWriter("script")).write(cmd)
      val res = "sh script".!!
      val content = "The result of your command is:\n\n" + res
      (Seq("echo", content) #| Seq("mail", "-s", "akka-cron-test", email)).!
    }
  }
}

case class Run(cmd: String, email: String)