package controllers

import auth.PanDomainAuthActions
import com.gu.facia.client.models.FrontJson
import config.UpdateManager
import play.api.mvc.Controller
import updates.{CreateFront, StreamUpdateWithCollections, UpdateFront, UpdatesStream}
import util.Requests._

object FrontController extends Controller with PanDomainAuthActions {
  def create = APIAuthAction { request =>
    request.body.read[CreateFront] match {
      case Some(createFrontRequest) =>
        val identity = request.user
        val newCollectionId = UpdateManager.createFront(createFrontRequest, identity)
        UpdatesStream.putStreamUpdate(StreamUpdateWithCollections(createFrontRequest, identity.email))
        Ok

      case None => BadRequest
    }
  }

  def update(frontId: String) = APIAuthAction { request =>
    request.body.read[FrontJson] match {
      case Some(front) =>
        val identity = request.user
        UpdateManager.updateFront(frontId, front, identity)
        UpdatesStream.putStreamUpdate(StreamUpdateWithCollections(UpdateFront(frontId, front), identity.email))
        Ok

      case None => BadRequest
    }
  }
}



