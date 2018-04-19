package story_packages.updates

import net.logstash.logback.marker.Markers
import play.api.Logger
import play.api.libs.json.Json
import conf.ApplicationConfiguration

import scala.collection.JavaConverters._

class AuditingUpdates(config: ApplicationConfiguration) {

  def putAudit(audit: AuditUpdate): Unit = {
    lazy val updatePayload = serializeUpdateMessage(audit)
    lazy val shortMessagePayload = serializeShortMessage(audit)
    audit.storyPackage.id.foreach { packageId =>
      Logger.logger.info(createMarkers(audit, shortMessagePayload, updatePayload, packageId), "Story Packages Audit")
    }
  }

  private def createMarkers(audit: AuditUpdate, shortMessage: Option[String], message: Option[String], packageId: String) =
    Markers.appendEntries((
      Map(
        "operation" -> audit.update.getClass.getSimpleName,
        "userEmail" -> audit.email,
        "date" -> audit.dateTime.toString,
        "resourceId" -> packageId
      )
        ++ shortMessage.map("shortMessage" -> _)
        ++ message.map("message" -> _)
      ).asJava
    )

  private def serializeShortMessage(audit: AuditUpdate): Option[String] = {
    audit.update match {
      case update: CreatePackage => Some(Json.toJson(Json.obj(
        "isHidden" -> update.isHidden,
        "name" -> update.name,
        "email" -> audit.email
      )).toString)
      case update: DeletePackage => Some(Json.toJson(Json.obj(
        "name" -> update.name,
        "isHidden" -> update.isHidden,
        "email" -> audit.email
      )).toString)
      case update: UpdateName => Some(Json.toJson(Json.obj(
        "name" -> update.name
      )).toString)
      case _ => None
    }
  }

  private def serializeUpdateMessage(streamUpdate: AuditUpdate): Option[String] = {
    Some(Json.toJson(streamUpdate.update).toString())
  }
}
