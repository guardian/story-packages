package controllers

import auth.PanDomainAuthActions
import model.StoryPackage
import play.api.libs.json.Json
import play.api.mvc.{Controller, Result}
import services.Database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

object StoryPackagesController extends Controller with PanDomainAuthActions {
  def create() = APIAuthAction.async { request =>
    request.body.asJson.flatMap(_.asOpt[StoryPackage]).map {
      case story: StoryPackage =>
        Database.createStoryPackage(story)
          .flatMap(handleCreateSuccess)
          .recover {
            case NonFatal(e) => InternalServerError(e.getMessage)
          }
      case _ => Future.successful(BadRequest)
    }.getOrElse(Future.successful(NotAcceptable))}

  private def handleCreateSuccess(story: StoryPackage): Future[Result] = {
    Future.successful(Ok(Json.toJson(story)))
  }
}
