package story_packages.switchboard

import play.api.libs.json.{JsValue, Json}
import story_packages.services.Logging

object SwitchManager extends Logging {

  var switches: Map[String, Boolean] = Map()

  def updateSwitches(newSwitches: Map[String, Boolean] ): Unit = {
    switches = newSwitches
  }

  def getStatus(switchName:String): Boolean = {
    switches.get(switchName) match {
        case Some(value) => value
        case None => {
          Logger.info(s"No status found matching switch $switchName")
          false
        }
    }
  }

  def getSwitchesAsJson(): JsValue = Json.toJson(switches)
}
