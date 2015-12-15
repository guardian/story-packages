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

object FaciaToolController extends Controller with PanDomainAuthActions {

  override lazy val actorSystem = ActorSystem()

  def priorities() = AuthAction { request =>
    Logger.info("Doing priorities..." + request)
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
    FaciaToolMetrics.ApiUsageCount.increment()
      request.body.asJson.flatMap (_.asOpt[UpdateMessage]).map {
        case update: Update => {
          val identity = request.user

          val futureCollectionJson = UpdateActions.updateCollectionList(update.update.id, update.update, identity)
          futureCollectionJson.map { maybeCollectionJson =>
            val updatedCollections = maybeCollectionJson.map(update.update.id -> _).toMap

            if (updatedCollections.nonEmpty) {
              UpdatesStream.putStreamUpdate(StreamUpdate(update, identity.email))
              Database.touchPackage(update.update.id, identity.email)
              Ok(Json.toJson(updatedCollections)).as("application/json")
            } else
              NotFound
          }
        }
        case remove: Remove => {
          val identity = request.user
          UpdateActions.updateCollectionFilter(remove.remove.id, remove.remove, identity).map { maybeCollectionJson =>
            val updatedCollections = maybeCollectionJson.map(remove.remove.id -> _).toMap
            UpdatesStream.putStreamUpdate(StreamUpdate(remove, identity.email))
            Database.touchPackage(remove.remove.id, identity.email)
            Ok(Json.toJson(updatedCollections)).as("application/json")
          }
        }
        case updateAndRemove: UpdateAndRemove =>  {
          val identity = request.user
          val futureUpdatedCollections =
            Future.sequence(
              List(UpdateActions.updateCollectionList(updateAndRemove.update.id, updateAndRemove.update, identity).map(_.map(updateAndRemove.update.id -> _)),
                UpdateActions.updateCollectionFilter(updateAndRemove.remove.id, updateAndRemove.remove, identity).map(_.map(updateAndRemove.remove.id -> _))
              )).map(_.flatten.toMap)

          futureUpdatedCollections.map { updatedCollections =>
            UpdatesStream.putStreamUpdate(StreamUpdate(updateAndRemove, identity.email))
            Database.touchPackage(updateAndRemove.update.id, identity.email)
            Database.touchPackage(updateAndRemove.remove.id, identity.email)
            Ok(Json.toJson(updatedCollections)).as("application/json")
          }
        }
        case _ => Future.successful(NotAcceptable)
      } getOrElse Future.successful(NotFound)
  }
}
