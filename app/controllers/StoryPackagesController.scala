package controllers

import java.net.{URLDecoder, URLEncoder}
import story_packages.auth.PanDomainAuthActions
import com.gu.facia.client.models.CollectionJson
import story_packages.metrics.FaciaToolMetrics
import story_packages.model.{Cached, StoryPackage}
import story_packages.permissions.APIKeyAuthAction
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._
import story_packages.services.{Database, FrontsApi}
import conf.ApplicationConfiguration
import story_packages.switchboard.SwitchManager
import story_packages.updates._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class StoryPackagesController(config: ApplicationConfiguration, components: ControllerComponents, database: Database, updatesStream: UpdatesStream,
                              frontsApi: FrontsApi, reindexJob: Reindex, wsClient: WSClient) extends StoryPackagesBaseController(config, components, wsClient) with PanDomainAuthActions {

  private def serializeSuccess(result: StoryPackage): Future[Result] = {
    Future.successful(Ok(Json.toJson(result)))}

  private def isHidden(request: Request[AnyContent]): Boolean = {
    request.queryString.getOrElse("isHidden", Seq("false")).contains("true")
  }

  def create() = APIAuthAction.async { request =>
    request.body.asJson.flatMap(_.asOpt[StoryPackage]).map {
      case story: StoryPackage =>
        database.createStoryPackage(story, request.user)
          .flatMap{storyPackage => {
            updatesStream.putStreamCreate(storyPackage, request.user.email)
            serializeSuccess(storyPackage)
          }}
          .recover {
            case NonFatal(e) => InternalServerError(e.getMessage)
          }
      case _ => Future.successful(BadRequest)
    }.getOrElse(Future.successful(NotAcceptable))}

  def capiLatest() = APIAuthAction.async { request =>
    val hidden = isHidden(request)
    FaciaToolMetrics.ProxyCount.increment()

    val contentApiHost = if (hidden)
      config.contentApi.packagesDraftHost
    else
      config.contentApi.packagesLiveHost

    val pageSize = config.latest.pageSize
    val url = s"$contentApiHost/packages?order-by=newest&page-size=$pageSize&${config.contentApi.key.map(key => s"api-key=$key").getOrElse("")}"

    Logger.info(s"Proxying latest packages API query to: $url")
    wsClient.url(url).get().map { response =>
      Cached(60) {
        Ok(response.body).as("application/javascript")
      }
    }
  }

  def capiSearch(term: String) = APIAuthAction.async { request =>
    val hidden = isHidden(request)
    val encodedTerm = URLEncoder.encode(URLDecoder.decode(term, "utf-8"), "utf-8")
    FaciaToolMetrics.ProxyCount.increment()

    val contentApiHost = if (hidden)
      config.contentApi.packagesDraftHost
    else
      config.contentApi.packagesLiveHost

    val url = s"$contentApiHost/packages?order-by=newest&q=$encodedTerm${config.contentApi.key.map(key => s"&api-key=$key").getOrElse("")}"

    Logger.info(s"Proxying search query to: $url")
    wsClient.url(url).get().flatMap { response =>
      val json: JsValue = Json.parse(response.body)
      val packageIds = (json \ "response" \ "results" \\ "packageId").map(_.as[String])
      for {
        packages <- Future.sequence(packageIds.map(id => database.getPackage(id)))
      } yield {
        Cached(60) {
          Ok(Json.toJson(packages)).as("application/javascript")
        }
      }
    }
  }

  def getPackage(id: String) = APIAuthAction.async { request =>
    database.getPackage(id)
      .flatMap(serializeSuccess)
      .recover {
        case NonFatal(e) => NotFound
      }
  }

  def deletePackage(id: String) = APIAuthAction.async { request =>
    database.removePackage(id).map(storyPackage => {
      val isHidden = storyPackage.isHidden.getOrElse(false)
      val deleteMessage = DeletePackage(id, isHidden, storyPackage.name.getOrElse("-unknown-"))
      val streamUpdate = AuditUpdate(deleteMessage, request.user.email, Map(), storyPackage)
      updatesStream.putStreamDelete(streamUpdate, id, isHidden)
      Ok
    })
  }

  def editPackage(id: String) = APIAuthAction.async { request =>
    val name = (request.body.asJson.get \ "name").as[String]
    database.touchPackage(id, request.user, Some(name)).map(storyPackage => {
      for {
        packageId <- storyPackage.id
        displayName <- storyPackage.name
      } yield {
        frontsApi.amazonClient.collection(packageId).map {
          case Some(coll) =>
            val collections: Map[String, CollectionJson] = Map((packageId, coll))
            val updateMessage = UpdateName(packageId, displayName)
            val streamUpdate = AuditUpdate(updateMessage, request.user.email, collections, storyPackage)
            updatesStream.putStreamUpdate(streamUpdate)
          case None =>
            Logger.info(s"Ignore sending update of empty story package $packageId")
        }
      }
      Ok(Json.toJson(storyPackage))
    })
  }

  def reindex(isHidden: Boolean) = new APIKeyAuthAction(config).async { request =>
    if (SwitchManager.getStatus("story-packages-disable-reindex-endpoint")) {
      Future.successful(Forbidden("Reindex endpoint disabled by a switch"))
    } else {
      reindexJob.scheduleJob(isHidden).map{
        case Some(job) => Created(s"Reindex scheduled at ${job.startTime}")
        case None => Forbidden("Reindex already running")
      }
    }
  }

  def reindexProgress(isHidden: Boolean) = (new APIKeyAuthAction(config)) { request =>
    reindexJob.getJobProgress(isHidden) match {
      case Some(progress) => Ok(Json.toJson(progress))
      case None => NotFound("Reindex never run")
    }
  }
}
