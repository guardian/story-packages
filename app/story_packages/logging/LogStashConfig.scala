package story_packages.logging

import conf.ApplicationConfiguration
import story_packages.services.Logging

import scala.util.control.NonFatal

class LogStashConfig(config: ApplicationConfiguration) extends Logging {
  Logger.info("Starting log stash")
  try LogStash.init(config)
  catch {
    case NonFatal(e) => Logger.error("could not configure log stream", e)
  }
}
