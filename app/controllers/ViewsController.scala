package controllers

import story_packages.auth.PanDomainAuthActions
import com.gu.pandomainauth.action.UserRequest
import story_packages.model.Cached
import play.api.mvc.{AnyContent, Controller}
import story_packages.services.AssetsManager
import conf.ApplicationConfiguration
import play.api.Mode
import play.api.libs.ws.WSClient

class ViewsController(val config: ApplicationConfiguration, assetsManager: AssetsManager, val wsClient: WSClient) extends Controller with PanDomainAuthActions {
  def priorities() = AuthAction { request =>
    val identity = request.user
    Cached(60) {
      Ok(views.html.priority(Option(identity), config.facia.stage, config.environment.mode == Mode.Dev))
    }
  }

  def collectionEditor() = AuthAction { request =>
    val identity = request.user
    Cached(60) {
      Ok(views.html.admin_main(Option(identity), config.facia.stage, overrideIsDev(request), assetsManager.pathForPackages))
    }
  }

  private def overrideIsDev(request: UserRequest[AnyContent]): Boolean = {
    request.queryString.getOrElse("isDev", Seq("false")).contains("true")
  }
}
