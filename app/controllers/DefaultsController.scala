package controllers

import story_packages.auth.PanDomainAuthActions
import story_packages.model.Cached
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import conf.ApplicationConfiguration
import play.api.Mode.Dev
import play.api.libs.ws.WSClient
import story_packages.switchboard.SwitchManager

object Defaults {
  implicit val jsonFormat = Json.writes[Defaults]
}

case class Defaults(
  dev: Boolean,
  env: String,
  email: String,
  avatarUrl: Option[String],
  sentryPublicDSN: String,
  mediaBaseUrl: String,
  apiBaseUrl: String,
  switches: JsValue,
  includedCap: Int,
  linkingCap: Int
)

class DefaultsController(config: ApplicationConfiguration, components: ControllerComponents, wsClient: WSClient) extends StoryPackagesBaseController(config, components, wsClient) with PanDomainAuthActions {
  def configuration = APIAuthAction { request =>
    Cached(60) {
      Ok(Json.toJson(Defaults(
        config.environment.mode == Dev,
        config.environment.stage,
        request.user.email,
        request.user.avatarUrl,
        config.sentry.publicDSN,
        config.media.baseUrl.get,
        config.media.apiUrl.get,
        SwitchManager.getSwitchesAsJson(),
        config.facia.includedCollectionCap,
        config.facia.linkingCollectionCap
      )))
    }
  }
}
