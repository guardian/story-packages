package tools

import com.gu.facia.client.models.CollectionJson
import com.gu.pandomainauth.model.User
import frontsapi.model.CollectionJsonFunctions
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{JsValue, _}
import services.{FrontsApi, S3FrontsApi}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

trait FaciaApiRead {
  def getCollectionJson(id: String): Future[Option[CollectionJson]]
}

trait FaciaApiWrite {
  def putCollectionJson(id: String, collectionJson: CollectionJson): CollectionJson
  def archive(id: String, collectionJson: CollectionJson, update: JsValue, identity: User): Unit
}

object FaciaApiIO extends FaciaApiRead with FaciaApiWrite {

  def getCollectionJson(id: String): Future[Option[CollectionJson]] = FrontsApi.amazonClient.collection(id)

  def putCollectionJson(id: String, collectionJson: CollectionJson): CollectionJson = {
    Try(S3FrontsApi.putCollectionJson(id, Json.prettyPrint(Json.toJson(collectionJson))))
    collectionJson
  }

  def archive(id: String, collectionJson: CollectionJson, update: JsValue, identity: User): Unit = {
    Json.toJson(collectionJson).transform[JsObject](Reads.JsObjectReads) match {
      case JsSuccess(result, _) =>
        S3FrontsApi.archive(id, Json.prettyPrint(result + ("diff", update)), identity)
      case JsError(errors)  => Logger.warn(s"Could not archive $id: $errors")}}

}
