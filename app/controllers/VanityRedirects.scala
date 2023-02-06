package controllers

import java.net.URLEncoder
import story_packages.auth.PanDomainAuthActions
import story_packages.model.NoCache
import play.api.mvc.{Action, ControllerComponents}
import play.mvc.Controller
import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient

class VanityRedirects(config: ApplicationConfiguration, components: ControllerComponents, wsClient: WSClient) extends StoryPackagesBaseController(config, components, wsClient) with PanDomainAuthActions {

  def storyPackage(id: String) = AuthAction { request => {
    NoCache(Redirect(s"/editorial?layout=latest,content:$id,packages", 301))}
  }

  def addTrail(id: String) = AuthAction { request => {
    NoCache(Redirect(s"/editorial?layout=latest,content,packages:create&q=${URLEncoder.encode(id, "utf-8")}", 301))
  }}

  def untrail(path: String) = Action { request =>
    NoCache(Redirect("/" + path, 301))}
}
