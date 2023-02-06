package controllers

import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient
import play.api.mvc.ControllerComponents

class StatusController(config: ApplicationConfiguration, components: ControllerComponents, wsClient: WSClient) extends StoryPackagesBaseController(config, components, wsClient) {
  def healthStatus = Action { request =>
    Ok("Ok.")
  }
}
