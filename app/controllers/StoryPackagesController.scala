package controllers

import java.net.URLDecoder

import auth.PanDomainAuthActions
import com.gu.pandomainauth.action.UserRequest
import conf.Configuration
import model.{StoryPackage, StoryPackageSearchResult}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Controller, Result}
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

  private def isHidden(request: UserRequest[AnyContent]): Boolean = {
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

  def reindex() = APIAuthAction.async { request =>
    def scheduleJob(jobId: String): Future[Result] = {
      Reindex.scheduleJob(job = jobId, isHidden = isHidden(request))
        .map(result => Ok(Json.toJson(result)))
        .recover {
          case NonFatal(e) => InternalServerError(e.getMessage)
        }
    }

    if (SwitchManager.getStatus("story-packages-disable-reindex-endpoint")) {
      Future.successful(Forbidden("Reindex endpoint disabled by a switch"))
    } else {
      request.queryString.getOrElse("job", Nil) match {
        case Seq(jobId) if !jobId.isEmpty => scheduleJob(jobId)
        case _ => Future.successful(BadRequest("Missing or invalid job ID"))
      }
    }
    // TODO authentication
    // TODO POST, not GET

  }
}
