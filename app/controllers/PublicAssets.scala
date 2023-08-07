package controllers

import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient
import story_packages.model.NoCache
import play.api.mvc.{AnyContent, ControllerComponents}

import scala.concurrent.ExecutionContext.Implicits.global

class PublicAssets(assets: Assets,
                   config: ApplicationConfiguration,
                   components: ControllerComponents,
                   wsClient: WSClient)
  extends StoryPackagesBaseController(config, components, wsClient) {

  def at(file: String): NoCache[AnyContent] = NoCache {
    if (file.startsWith("story-packages/bundles")) {
      assets.at("/public", file)
    } else {
      assets.at("/public/src", file)
    }
  }

}
