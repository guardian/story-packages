package controllers

import java.net.URLDecoder

import auth.PanDomainAuthActions
import conf.Configuration
import model.{StoryPackage, StoryPackageSearchResult}
import permissions.APIKeyAuthAction
import play.api.libs.json.Json
import play.api.mvc._
import services.Database
import switchboard.SwitchManager
import updates.{Reindex, UpdatesStream}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

object StoryPackagesController extends Controller with PanDomainAuthActions {
  private def serializeSuccess(result: StoryPackage): Future[Result] = {
    Future.successful(Ok(Json.toJson(result)))}
  private def serializeSuccess(result: StoryPackageSearchResult): Future[Result] = {
    Future.successful(Ok(Json.toJson(result)))}

  private def isHidden(request: Request[AnyContent]): Boolean = {
    request.queryString.getOrElse("isHidden", Seq("false")).contains("true")
  }

  def create() = APIAuthAction.async { request =>
    request.body.asJson.flatMap(_.asOpt[StoryPackage]).map {
      case story: StoryPackage =>
        Database.createStoryPackage(story, request.user)
          .flatMap(serializeSuccess)
          .recover {
            case NonFatal(e) => InternalServerError(e.getMessage)
          }
      case _ => Future.successful(BadRequest)
    }.getOrElse(Future.successful(NotAcceptable))}

  def search(term: String) = APIAuthAction.async { request =>
    Database.searchPackages(
      URLDecoder.decode(term, "UTF-8"),
      isHidden = isHidden(request)
    )
      .flatMap(serializeSuccess)
      .recover {
        case NonFatal(e) => InternalServerError(e.getMessage)
      }
  }

  def latest() = APIAuthAction.async { request =>
    Database.latestPackages(
      Configuration.storage.maxLatestDays,
      isHidden = isHidden(request)
    )
      .flatMap(serializeSuccess)
      .recover {
        case NonFatal(e) => InternalServerError(e.getMessage)
      }
  }

  def getPackage(id: String) = APIAuthAction.async { request =>
    Database.getPackage(id)
      .flatMap(serializeSuccess)
      .recover {
        case NonFatal(e) => NotFound
      }
  }

  def deletePackage(id: String) = APIAuthAction.async { request =>
    Database.removePackage(id).map(storyPackage => {
      UpdatesStream.putStreamDelete(id, storyPackage.isHidden.getOrElse(false))
      Ok
    })
  }

  def reindex(isHidden: Boolean) = APIKeyAuthAction.async { request =>
    if (SwitchManager.getStatus("story-packages-disable-reindex-endpoint")) {
      Future.successful(Forbidden("Reindex endpoint disabled by a switch"))
    } else {
      Reindex.scheduleJob(isHidden).map{
        case Some(job) => Created(s"Reindex scheduled at ${job.startTime}")
        case None => Forbidden("Reindex already running")
      }
    }
  }

  def reindexProgress(isHidden: Boolean) = APIKeyAuthAction { request =>
    Reindex.getJobProgress(isHidden) match {
      case Some(progress) => Ok(Json.toJson(progress))
      case None => NotFound("Reindex never run")
    }
  }
}
