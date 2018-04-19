package story_packages.updates

import com.gu.facia.client.models.{CollectionJson, TrailMetaData}
import julienrf.variants.Variants
import story_packages.model.StoryPackage
import org.joda.time.DateTime
import play.api.libs.json._

sealed trait UpdateMessage

/* Config updates */
case class DeletePackage(id: String, isHidden: Boolean, name: String) extends UpdateMessage
case class UpdateName(id: String, name: String) extends UpdateMessage
case class CreatePackage(id: String, isHidden: Boolean, name: String) extends UpdateMessage

/* Collection updates */
case class UpdateList(
  id: String,
  item: String,
  position: Option[String],
  after: Option[Boolean],
  itemMeta: Option[TrailMetaData]
) extends UpdateMessage

object UpdateList {
  implicit val format: Format[UpdateList] = Json.format[UpdateList]
}

case class Update(update: UpdateList) extends UpdateMessage
case class Remove(remove: UpdateList) extends UpdateMessage

/* Macro - Watch out, this needs to be after the case classes */
object UpdateMessage {
  implicit val format: Format[UpdateMessage] = Variants.format[UpdateMessage]((__ \ "type").format[String])
}

/* Kinesis messages */
case class AuditUpdate(
  update: UpdateMessage,
  email: String,
  collections: Map[String, CollectionJson],
  storyPackage: StoryPackage
) {
  val dateTime: DateTime = new DateTime()
}
object AuditUpdate {
  implicit val streamUpdateFormat: Format[AuditUpdate] = Json.format[AuditUpdate]
}
