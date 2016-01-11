package updates

import com.gu.facia.client.models.{CollectionConfigJson, CollectionJson, FrontJson, TrailMetaData}
import julienrf.variants.Variants
import model.StoryPackage
import org.joda.time.DateTime
import play.api.libs.json._
import services.ConfigAgent

sealed trait UpdateMessage {
  def affectedFronts: Set[String]
}

/* Config updates */
case class DeletePackage(id: String) extends UpdateMessage {
  def affectedFronts = Set(id)
}

/* Collection updates */
case class UpdateList(
  id: String,
  item: String,
  position: Option[String],
  after: Option[Boolean],
  itemMeta: Option[TrailMetaData],
  live: Boolean,
  draft: Boolean
) extends UpdateMessage {
  def affectedFronts: Set[String] = ConfigAgent.getConfigsUsingCollectionId(id).toSet[String]
}
object UpdateList {
  implicit val format: Format[UpdateList] = Json.format[UpdateList]
}

case class Update(update: UpdateList) extends UpdateMessage {
  def affectedFronts = update.affectedFronts
}
case class Remove(remove: UpdateList) extends UpdateMessage {
  def affectedFronts = remove.affectedFronts
}

case class UpdateAndRemove(update: UpdateList, remove: UpdateList) extends UpdateMessage {
  def affectedFronts = update.affectedFronts ++ remove.affectedFronts
}

case class DiscardUpdate(id: String) extends UpdateMessage {
  def affectedFronts = Set(id)
}
case class PublishUpdate(id: String) extends UpdateMessage {
  def affectedFronts = Set(id)
}

/* Macro - Watch out, this needs to be after the case classes */
object UpdateMessage {
  implicit val format: Format[UpdateMessage] = Variants.format[UpdateMessage]((__ \ "type").format[String])
}

/* Kinesis messages */
case class StreamUpdate(
  update: UpdateMessage,
  email: String,
  collections: Map[String, CollectionJson],
  storyPackage: StoryPackage,
  delete: Boolean = false
) {
  val fronts: Set[String] = update.affectedFronts
  val dateTime: DateTime = new DateTime()
}
object StreamUpdate {
  implicit val streamUpdateFormat: Format[StreamUpdate] = Json.format[StreamUpdate]
}
