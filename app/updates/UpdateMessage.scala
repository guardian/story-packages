package updates

import com.gu.facia.client.models.{CollectionJson, TrailMetaData}
import julienrf.variants.Variants
import model.StoryPackage
import org.joda.time.DateTime
import play.api.libs.json._

sealed trait UpdateMessage

/* Config updates */
case class DeletePackage(id: String) extends UpdateMessage
case class UpdateName(id: String, name: String) extends UpdateMessage

/* Collection updates */
case class UpdateList(
  id: String,
  item: String,
  position: Option[String],
  after: Option[Boolean],
  itemMeta: Option[TrailMetaData],
  live: Boolean,
  draft: Boolean
) extends UpdateMessage

object UpdateList {
  implicit val format: Format[UpdateList] = Json.format[UpdateList]
}

case class Update(update: UpdateList) extends UpdateMessage
case class Remove(remove: UpdateList) extends UpdateMessage

case class DiscardUpdate(id: String) extends UpdateMessage
case class PublishUpdate(id: String) extends UpdateMessage

/* Macro - Watch out, this needs to be after the case classes */
object UpdateMessage {
  implicit val format: Format[UpdateMessage] = Variants.format[UpdateMessage]((__ \ "type").format[String])
}

/* Kinesis messages */
case class StreamUpdate(
  update: UpdateMessage,
  email: String,
  collections: Map[String, CollectionJson],
  storyPackage: StoryPackage
) {
  val dateTime: DateTime = new DateTime()
}
object StreamUpdate {
  implicit val streamUpdateFormat: Format[StreamUpdate] = Json.format[StreamUpdate]
}
