package story_packages.switchboard

import akka.actor.Scheduler
import com.amazonaws.auth.AWSCredentialsProvider
import story_packages.services.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class SwitchboardConfiguration (
  bucket: String,
  objectKey: String,
  credentials: AWSCredentialsProvider,
  endpoint: String
)

class Lifecycle(conf: SwitchboardConfiguration, scheduler: Scheduler) extends Logging {
  lazy val client: S3client = new S3client(conf)

  Logger.info("Starting switchboard cache")
  scheduler.scheduleWithFixedDelay(initialDelay = 1.seconds, delay = 1.minute) { () => refreshSwitches() }

  def refreshSwitches() {
    Logger.info("Refreshing switches from switchboard")
    client.getSwitches() foreach { response => SwitchManager.updateSwitches(response) }
  }
}
