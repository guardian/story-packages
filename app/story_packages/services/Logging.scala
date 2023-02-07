package story_packages.services

import play.api.{Logger => BaseLogger}

trait Logging {
  val Logger = BaseLogger(getClass)
}
