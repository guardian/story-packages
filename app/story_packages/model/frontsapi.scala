package frontsapi.model

import com.gu.facia.client.models._
import com.gu.pandomainauth.model.User
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import story_packages.services.FrontsApi
import conf.ApplicationConfiguration
import story_packages.tools.FaciaApiIO
import story_packages.updates.UpdateList

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


trait UpdateActionsTrait {
  def faciaApiIO: FaciaApiIO
  def frontsApi: FrontsApi
  def config: ApplicationConfiguration

  implicit val updateListWrite = Json.writes[UpdateList]

  def insertIntoLive(update: UpdateList, identity: User, collectionJson: CollectionJson): CollectionJson = {
    val live = updateList(update, identity, collectionJson.live)
    collectionJson.copy(live=live)}

  def deleteFromLive(update: UpdateList, collectionJson: CollectionJson): CollectionJson =
    collectionJson.copy(live=collectionJson.live.filterNot(_.id == update.item))

  def putCollectionJson(id: String, collectionJson: CollectionJson): CollectionJson =
    faciaApiIO.putCollectionJson(id, collectionJson)

  def updateIdentity(collectionJson: CollectionJson, identity: User): CollectionJson =
    collectionJson.copy(lastUpdated = DateTime.now, updatedBy = getUserName(identity), updatedEmail = identity.email)

  //Archiving
  def archiveUpdateBlock(collectionId: String, collectionJson: CollectionJson, updateJson: JsValue, identity: User): CollectionJson = {
    archiveBlock(collectionId, collectionJson, Json.obj("action" -> "update", "update" -> updateJson), identity)
  }

  def archiveDeleteBlock(collectionId: String, collectionJson: CollectionJson, updateJson: JsValue, identity: User): CollectionJson = {
    archiveBlock(collectionId, collectionJson, Json.obj("action" -> "delete", "update" -> updateJson), identity)
  }

  private def archiveBlock(id: String, collectionJson: CollectionJson, action: String, identity: User): CollectionJson =
    archiveBlock(id, collectionJson, Json.obj("action" -> action), identity)

  private def archiveBlock(id: String, collectionJson: CollectionJson, updateJson: JsValue, identity: User): CollectionJson =
    Try(faciaApiIO.archive(id, collectionJson, updateJson, identity)) match {
      case Failure(t: Throwable) => {
        Logger.warn(t.toString)
        collectionJson
      }
      case Success(_) => collectionJson
    }

  def updateCollectionList(id: String, update: UpdateList, identity: User): Future[Option[CollectionJson]] = {
    lazy val updateJson = Json.toJson(update)
    frontsApi.amazonClient.collection(id).map { maybeCollectionJson =>
      maybeCollectionJson
        .map(insertIntoLive(update, identity, _))
        .map(pruneBlock)
        .map(sortByGroupAndCap)
        .map(updateIdentity(_, identity))
        .map(putCollectionJson(id, _))
        .map(archiveUpdateBlock(id, _, updateJson, identity))
        .orElse(Option(createCollectionJson(identity, update)))
        .map(putCollectionJson(id, _))}}

  def updateCollectionFilter(id: String, update: UpdateList, identity: User): Future[Option[CollectionJson]] = {
    lazy val updateJson = Json.toJson(update)
    frontsApi.amazonClient.collection(id).map { maybeCollectionJson =>
      maybeCollectionJson
        .map(deleteFromLive(update, _))
        .map(pruneBlock)
        .map(sortByGroupAndCap)
        .map(archiveDeleteBlock(id, _, updateJson, identity))
        .map(updateIdentity(_, identity))
        .map(putCollectionJson(id, _))}}

  private def updateList(update: UpdateList, identity: User, blocks: List[Trail]): List[Trail] = {
    val trail: Trail = blocks
      .find(_.id == update.item)
      .map { currentTrail =>
        val newMeta = for (updateMeta <- update.itemMeta) yield updateMeta
        currentTrail.copy(meta = newMeta)
      }
      .getOrElse(Trail(update.item, DateTime.now.getMillis, Some(getUserName(identity)), update.itemMeta))

    val listWithoutItem = blocks.filterNot(_.id == update.item)

    val splitList: (List[Trail], List[Trail]) = {
      //Different index logic if item is being place at itself in list
      //(Eg for metadata update, or group change, index must come from list without item removed)
      if (update.position.exists(_ == update.item)) {
        val index = blocks.indexWhere(_.id == update.item)
        listWithoutItem.splitAt(index)
      }
      else {
        val index = update.after.filter {_ == true}
          .map {_ => listWithoutItem.indexWhere(t => update.position.exists(_ == t.id)) + 1}
          .getOrElse { listWithoutItem.indexWhere(t => update.position.exists(_ == t.id)) }
        listWithoutItem.splitAt(index)
      }
    }

    splitList._1 ::: (trail +: splitList._2)
  }

  def createCollectionJson(identity: User, update: UpdateList): CollectionJson = {
    val userName = getUserName(identity)
    CollectionJson(
      live = List(Trail(
        id = update.item,
        frontPublicationDate = DateTime.now.getMillis,
        publishedBy = Some(userName),
        meta = update.itemMeta)
      ),
      draft = None,
      treats = None,
      lastUpdated = DateTime.now,
      updatedBy = userName,
      updatedEmail = identity.email,
      displayName = None,
      href = None,
      previously = None,
      targetedTerritory = None
    )
  }

  private def pruneBlock(collectionJson: CollectionJson): CollectionJson =
    collectionJson.copy(
      live = collectionJson.live
        .map(pruneGroupOfZero)
        .map(pruneMetaDataIfEmpty),
      draft = None,
      previously = None
    )

  private def pruneGroupOfZero(trail: Trail): Trail =
    trail.copy(meta = trail.meta.map(
      metaData => metaData.copy(json = metaData.json.filter{
        case ("group", JsString("0")) => false
        case _ => true})))

  private def pruneMetaDataIfEmpty(trail: Trail): Trail =
    trail.copy(meta = trail.meta.filter(_.json.nonEmpty))

  private def removeGroupsFromTrail(trail: Trail): Trail =
    trail.copy(meta = trail.meta.map(metaData => metaData.copy(json = metaData.json - "group")))

  private def getUserName(identity: User): String = s"${identity.firstName} ${identity.lastName}"

  private def sortByGroupAndCap(collectionJson: CollectionJson) =
    collectionJson.copy(live = sortTrailsByGroupAndCap(collectionJson.live))

  private def sortTrailsByGroupAndCap(trails: List[Trail]): List[Trail] = {
    val trailGroups = trails.groupBy(_.meta.flatMap(_.group).map(_.toInt).getOrElse(0))
    trailGroups.keys.toList.sorted(Ordering.Int.reverse).flatMap(groupId => {
      trailGroups.getOrElse(groupId, Nil).take(groupId match {
        case 0 => config.facia.linkingCollectionCap
        case 1 => config.facia.includedCollectionCap
        case _ => 0
      })
    })
  }
}

class UpdateActions(val faciaApiIO: FaciaApiIO, val frontsApi: FrontsApi, val config: ApplicationConfiguration) extends UpdateActionsTrait
