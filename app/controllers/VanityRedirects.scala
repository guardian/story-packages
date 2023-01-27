package controllers

import com.gu.pandomainauth.PanDomainAuthSettingsRefresher
import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient
import play.api.mvc.{BaseController, ControllerComponents}
import story_packages.auth.PanDomainAuthActions
import story_packages.model.NoCache

import java.net.URLEncoder

class VanityRedirects(
  val config: ApplicationConfiguration,
  val wsClient: WSClient,
  val controllerComponents: ControllerComponents,
  val panDomainSettings: PanDomainAuthSettingsRefresher
) extends BaseController with PanDomainAuthActions {

  def storyPackage(id: String) = AuthAction {
    NoCache(Redirect(s"/editorial?layout=latest,content:$id,packages", 301))
  }

  def addTrail(id: String) = AuthAction {
    NoCache(Redirect(s"/editorial?layout=latest,content,packages:create&q=${URLEncoder.encode(id, "utf-8")}", 301))
  }

  def untrail(path: String) = Action {
    NoCache(Redirect("/" + path, 301))
  }
}
