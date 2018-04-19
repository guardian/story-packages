package story_packages.switchboard

import akka.actor.Scheduler
import com.amazonaws.auth.AWSCredentialsProvider
import play.api.{GlobalSettings, Logger}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class SwitchboardConfiguration (
  bucket: String,
  objectKey: String,
  credentials: AWSCredentialsProvider,
  endpoint: String
)

class Lifecycle(conf: SwitchboardConfiguration, scheduler: Scheduler) extends GlobalSettings {
  lazy val client: S3client = new S3client(conf)

  Logger.info("Starting switchboard cache")
  scheduler.schedule(initialDelay = 1.seconds, interval = 1.minute) { refreshSwitches() }

  def refreshSwitches() {
    Logger.info("Refreshing switches from switchboard")
    client.getSwitches() foreach { response => SwitchManager.updateSwitches(response) }
  }
}
