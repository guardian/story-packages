package controllers

import auth.PanDomainAuthActions
import com.gu.pandomainauth.action.UserRequest
import conf.ApplicationConfiguration
import model.Cached
import play.api.mvc.{AnyContent, Controller}
import services.AssetsManager

class ViewsController(val config: ApplicationConfiguration, assetsManager: AssetsManager, isDev: Boolean) extends Controller with PanDomainAuthActions {
  def priorities() = AuthAction { request =>
    val identity = request.user
    Cached(60) {
      Ok(views.html.priority(Option(identity), config.facia.stage, isDev))
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
