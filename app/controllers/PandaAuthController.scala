package controllers

import com.gu.pandomainauth.PanDomainAuthSettingsRefresher
import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient
import play.api.mvc.{BaseController, ControllerComponents}
import story_packages.auth.PanDomainAuthActions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PandaAuthController(
  val config: ApplicationConfiguration,
  val wsClient: WSClient,
  val controllerComponents: ControllerComponents,
  val panDomainSettings: PanDomainAuthSettingsRefresher) extends BaseController with PanDomainAuthActions {

  def oauthCallback = Action.async { implicit request =>
    processGoogleCallback()
  }

  def logout = Action.async { implicit request =>
    Future(processLogout)
  }

  def authError(message: String) = Action.async { implicit request =>
    Future(Forbidden(views.html.auth.login(Some(message))))
  }

  def user() = AuthAction { implicit request =>
    Ok(request.user.toJson).as(JSON)
  }

  def status = AuthAction { request =>
    val user = request.user
    Ok(views.html.auth.status(user.toJson))
  }
}
