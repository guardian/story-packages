package controllers

import story_packages.auth.PanDomainAuthActions
import play.api.mvc.ControllerComponents
import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PandaAuthController(config: ApplicationConfiguration, components: ControllerComponents, wsClient: WSClient)
  extends StoryPackagesBaseController(config, components, wsClient)
    with PanDomainAuthActions {

  def oauthCallback = Action.async { implicit request =>
    processOAuthCallback()
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
