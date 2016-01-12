package controllers

import java.net.URLDecoder

import auth.PanDomainAuthActions
import com.gu.facia.client.models.CollectionJson
import com.gu.pandomainauth.action.UserRequest
import conf.Configuration
import model.{StoryPackage, StoryPackageSearchResult}
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Controller, Result}
import services.Database
import updates.{DeletePackage, UpdateMessage, UpdatesStream, StreamUpdate}

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
    for {
      storyPackage <- Database.getPackage(id)
      isHidden <- storyPackage.isHidden
    } {
      UpdatesStream.putStreamDelete(id, isHidden)
    }

      Database.removePackage(id).map(response => {
        val isHidden = response.getAttributes().get("isHidden").getBOOL()
        UpdatesStream.putStreamDelete(id, isHidden)
        Ok
      })
  }
}
