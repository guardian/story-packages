package controllers

import java.net.URLDecoder

import auth.PanDomainAuthActions
import conf.Configuration
import metrics.FaciaToolMetrics
import model.{Cached, StoryPackage, StoryPackageSearchResult}
import permissions.APIKeyAuthAction
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.mvc._
import services.Database
import switchboard.SwitchManager
import updates.{Reindex, UpdatesStream}
import play.api.Play.current

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

  def capiLatest() = APIAuthAction.async { request =>

    val hidden = isHidden(request)

    FaciaToolMetrics.ProxyCount.increment()

    val contentApiHost = if (hidden)
      Configuration.contentApi.contentApiDraftHost
    else
      Configuration.contentApi.contentApiLiveHost

    val url = s"$contentApiHost/packages?${Configuration.contentApi.key.map(key => s"api-key=$key").getOrElse("")}"

    Logger.info(s"Proxying latest packages API query to: $url")

    WS.url(url).get().map { response =>
      Cached(60) {
        Ok(response.body).as("application/javascript")
      }
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
