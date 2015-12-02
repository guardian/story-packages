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
  editions: Seq[String],
  email: String,
  avatarUrl: Option[String],
  lowFrequency: Int,
  highFrequency: Int,
  standardFrequency: Int,
  sentryPublicDSN: String,
  mediaBaseUrl: String,
  apiBaseUrl: String,
  switches: JsValue,
  collectionCap: Int
)

object DefaultsController extends Controller with PanDomainAuthActions {
  private val DynamicGroups = Seq(
    "standard",
    "big",
    "very big",
    "huge"
  )

  private val DynamicPackage = Seq(
    "standard",
    "snap"
  )

  private val DynamicMpu = Seq(
    "standard",
    "big"
  )


  def configuration = APIAuthAction { request =>
    Cached(60) {
      Ok(Json.toJson(Defaults(
        Play.isDev,
        Configuration.environment.stage,
        Seq("uk", "us", "au"),
        request.user.email,
        request.user.avatarUrl,
        60,
        1,
        5,
        Configuration.sentry.publicDSN,
        Configuration.media.baseUrl.get,
        Configuration.media.apiUrl.get,
        SwitchManager.getSwitchesAsJson(),
        Configuration.facia.collectionCap
      )))
    }
  }
}
