package controllers

import akka.actor.ActorSystem
import story_packages.auth.PanDomainAuthActions
import frontsapi.model._
import story_packages.metrics.FaciaToolMetrics
import story_packages.model.NoCache
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import story_packages.services._
import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient
import story_packages.updates._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class FaciaToolController(val config: ApplicationConfiguration, frontsApi: FrontsApi, updateActions: UpdateActions,
                          database: Database, updatesStream: UpdatesStream, val wsClient: WSClient) extends Controller with PanDomainAuthActions {

  override lazy val actorSystem = ActorSystem()

  def getCollection(collectionId: String) = APIAuthAction.async { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    frontsApi.amazonClient.collection(collectionId).map { configJson =>
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
        updateActions.updateCollectionList(update.update.id, update.update, identity).flatMap { maybeCollectionJson =>
          val updatedCollections = maybeCollectionJson.map(update.update.id -> _).toMap

          if (updatedCollections.nonEmpty) {
            database.touchPackage(update.update.id, identity).map(storyPackage => {
              updatesStream.putStreamUpdate(AuditUpdate(update, identity.email, updatedCollections, storyPackage))
              Ok(Json.toJson(updatedCollections)).as("application/json")
            })
            .recover {
              case NonFatal(e) => touchFailure(e, update.update.id)
            }
          } else
            Future.successful(NotFound)
        }
      case remove: Remove =>
        updateActions.updateCollectionFilter(remove.remove.id, remove.remove, identity).flatMap { maybeCollectionJson =>
          val updatedCollections = maybeCollectionJson.map(remove.remove.id -> _).toMap
          database.touchPackage(remove.remove.id, identity).map(storyPackage => {
            updatesStream.putStreamUpdate(AuditUpdate(remove, identity.email, updatedCollections, storyPackage))
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
