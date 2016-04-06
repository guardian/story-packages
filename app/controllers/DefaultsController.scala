package controllers

import auth.PanDomainAuthActions
import conf.Configuration
import model.Cached
import play.api.Play
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import switchboard.SwitchManager


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

object DefaultsController extends Controller with PanDomainAuthActions {
  def configuration = APIAuthAction { request =>
    Cached(60) {
      Ok(Json.toJson(Defaults(
        Play.isDev,
        Configuration.environment.stage,
        request.user.email,
        request.user.avatarUrl,
        Configuration.sentry.publicDSN,
        Configuration.media.baseUrl.get,
        Configuration.media.apiUrl.get,
        SwitchManager.getSwitchesAsJson(),
        Configuration.facia.includedCollectionCap,
        Configuration.facia.linkingCollectionCap
      )))
    }
  }
}
