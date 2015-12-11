package controllers

import java.net.URLDecoder

import auth.PanDomainAuthActions
import conf.Configuration
import model.{StoryPackage, StoryPackageSearchResult}
import play.api.libs.json.Json
import play.api.mvc.{Controller, Result}
import services.Database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

object StoryPackagesController extends Controller with PanDomainAuthActions {
  private def serializeSuccess(result: StoryPackage): Future[Result] = {
    Future.successful(Ok(Json.toJson(result)))}
  private def serializeSuccess(result: StoryPackageSearchResult): Future[Result] = {
    Future.successful(Ok(Json.toJson(result)))}

  def create() = APIAuthAction.async { request =>
    request.body.asJson.flatMap(_.asOpt[StoryPackage]).map {
      case story: StoryPackage =>
        Database.createStoryPackage(story)
          .flatMap(serializeSuccess)
          .recover {
            case NonFatal(e) => InternalServerError(e.getMessage)
          }
      case _ => Future.successful(BadRequest)
    }.getOrElse(Future.successful(NotAcceptable))}

  def search(term: String) = APIAuthAction.async { request =>
    println(request.queryString)
    Database.searchPackages(
      URLDecoder.decode(term, "UTF-8"),
      isHidden = request.queryString.getOrElse("isHidden", "false").equals("true")
    )
      .flatMap(serializeSuccess)
      .recover {
        case NonFatal(e) => InternalServerError(e.getMessage)
      }
  }

  def latest() = APIAuthAction.async { request =>
    Database.latestPackages(Configuration.storage.maxLatestSize, Configuration.storage.maxLatestDays)
      .flatMap(serializeSuccess)
      .recover {
        case NonFatal(e) => InternalServerError(e.getMessage)
      }
  }
}
