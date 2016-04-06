package controllers

import akka.actor.ActorSystem
import auth.PanDomainAuthActions
import frontsapi.model._
import metrics.FaciaToolMetrics
import model.{Cached, NoCache}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import services._
import updates._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

object FaciaToolController extends Controller with PanDomainAuthActions {

  override lazy val actorSystem = ActorSystem()

  def priorities() = AuthAction { request =>
    val identity = request.user
    Cached(60) { Ok(views.html.priority(Option(identity))) }
  }

  def collectionEditor() = AuthAction { request =>
    val identity = request.user
    Cached(60) { Ok(views.html.admin_main(Option(identity))) }
  }

  def getCollection(collectionId: String) = APIAuthAction.async { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    FrontsApi.amazonClient.collection(collectionId).map { configJson =>
      NoCache {
        Ok(Json.toJson(configJson)).as("application/json")}}}

  def collectionEdits(): Action[AnyContent] = APIAuthAction.async { implicit request =>
    def touchFailure(e: Throwable, id: String) = {
      Logger.error(s"Non fatal exception when touching story package $id")
      InternalServerError(s"Unable to update package $id")
    }
    FaciaToolMetrics.ApiUsageCount.increment()
    val identity = request.user

    request.body.asJson.flatMap (_.asOpt[UpdateMessage]).map {
      case update: Update =>
        UpdateActions.updateCollectionList(update.update.id, update.update, identity).flatMap { maybeCollectionJson =>
          val updatedCollections = maybeCollectionJson.map(update.update.id -> _).toMap

          if (updatedCollections.nonEmpty) {
            Database.touchPackage(update.update.id, identity).map(storyPackage => {
              UpdatesStream.putStreamUpdate(StreamUpdate(update, identity.email, updatedCollections, storyPackage))
              Ok(Json.toJson(updatedCollections)).as("application/json")
            })
            .recover {
              case NonFatal(e) => touchFailure(e, update.update.id)
            }
          } else
            Future.successful(NotFound)
        }
      case remove: Remove =>
        UpdateActions.updateCollectionFilter(remove.remove.id, remove.remove, identity).flatMap { maybeCollectionJson =>
          val updatedCollections = maybeCollectionJson.map(remove.remove.id -> _).toMap
          Database.touchPackage(remove.remove.id, identity).map(storyPackage => {
            UpdatesStream.putStreamUpdate(StreamUpdate(remove, identity.email, updatedCollections, storyPackage))
            Ok(Json.toJson(updatedCollections)).as("application/json")
          })
          .recover {
            case NonFatal(e) => touchFailure(e, remove.remove.id)
          }
        }
      case _ => Future.successful(NotAcceptable)
    } getOrElse Future.successful(NotFound)
  }
}
