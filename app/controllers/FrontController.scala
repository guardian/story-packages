package controllers

import auth.PanDomainAuthActions
import com.gu.facia.client.models.{CollectionConfigJson, FrontJson}
import config.UpdateManager
import play.api.libs.json.Json
import play.api.mvc.Controller
import util.Requests._

object CreateFront {
  implicit val jsonFormat = Json.format[CreateFront].filter(_.id.matches("""^[a-z0-9\/\-+]*$"""))
}

case class CreateFront(
  id: String,
  navSection: Option[String],
  webTitle: Option[String],
  title: Option[String],
  imageUrl: Option[String],
  imageWidth: Option[Int],
  imageHeight: Option[Int],
  isImageDisplayed: Option[Boolean],
  description: Option[String],
  onPageDescription: Option[String],
  priority: Option[String],
  isHidden: Option[Boolean],
  initialCollection: CollectionConfigJson,
  group: Option[String]
)

object FrontController extends Controller with PanDomainAuthActions {
  def create = APIAuthAction { request =>
    request.body.read[CreateFront] match {
      case Some(createFrontRequest) =>
        val identity = request.user
        val newCollectionId = UpdateManager.createFront(createFrontRequest, identity)
        Ok

      case None => BadRequest
    }
  }

  def update(frontId: String) = APIAuthAction { request =>
    request.body.read[FrontJson] match {
      case Some(front) =>
        val identity = request.user
        UpdateManager.updateFront(frontId, front, identity)
        Ok

      case None => BadRequest
    }
  }
}



