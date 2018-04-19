package story_packages.logging

import play.api.Logger
import conf.ApplicationConfiguration

import scala.util.control.NonFatal

class LogStashConfig(config: ApplicationConfiguration) {
  Logger.info("Starting log stash")
  try LogStash.init(config)
  catch {
    case NonFatal(e) => Logger.error("could not configure log stream", e)
  }
}
