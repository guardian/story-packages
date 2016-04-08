package updates

import java.nio.ByteBuffer

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordRequest, PutRecordResult}
import com.gu.auditing.model.v1.{App, Notification}
import com.gu.thrift.serializer.{GzipType, ThriftSerializer}
import conf.ApplicationConfiguration
import play.api.Logger
import play.api.libs.json._

class AuditingUpdates(config: ApplicationConfiguration) {
  val partitionKey: String = "story-packages-updates"

  object KinesisLoggingAsyncHandler extends AsyncHandler[PutRecordRequest, PutRecordResult] {
    def onError(exception: Exception) {
      Logger.error(s"Kinesis PutRecord request error: ${exception.getMessage}}")
    }
    def onSuccess(request: PutRecordRequest, result: PutRecordResult) {
      Logger.info(s"Put diff to stream:${request.getStreamName} Seq:${result.getSequenceNumber}")
    }
  }

  val client: AmazonKinesisAsyncClient = {
    val c = new AmazonKinesisAsyncClient(config.aws.mandatoryCredentials)
    c.setRegion(Region.getRegion(Regions.EU_WEST_1))
    c
  }

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {
    val updateName = streamUpdate.update.getClass.getSimpleName
    lazy val updatePayload = serializeUpdateMessage(streamUpdate)
    lazy val shortMessagePayload = serializeShortMessage(streamUpdate)
    lazy val expiryDate = computeExpiryDate(streamUpdate)

    streamUpdate.storyPackage.id.foreach(packageId => putAuditingNotification(
      Notification(
        app = App.StoryPackages,
        operation = updateName,
        userEmail = streamUpdate.email,
        date = streamUpdate.dateTime.toString,
        resourceId = Some(packageId),
        message = updatePayload,
        shortMessage = shortMessagePayload,
        expiryDate = expiryDate
      )))
  }

  private def serializeShortMessage(streamUpdate: StreamUpdate): Option[String] = {
    streamUpdate.update match {
      case update: CreatePackage => Some(Json.toJson(Json.obj(
        "isHidden" -> update.isHidden,
        "name" -> update.name,
        "email" -> streamUpdate.email
      )).toString)
      case update: DeletePackage => Some(Json.toJson(Json.obj(
        "name" -> update.name,
        "isHidden" -> update.isHidden,
        "email" -> streamUpdate.email
      )).toString)
      case update: UpdateName => Some(Json.toJson(Json.obj(
        "name" -> update.name
      )).toString)
      case _ => None
    }
  }

  private def serializeUpdateMessage(streamUpdate: StreamUpdate): Option[String] = {
    Some(Json.toJson(streamUpdate.update).toString())
  }

  private def computeExpiryDate(streamUpdate: StreamUpdate): Option[String] = {
    streamUpdate.update match {
      case _: DeletePackage => None
      case _: UpdateName => None
      case _: CreatePackage => None
      case _ => Some(streamUpdate.dateTime.plusMonths(1).toString)
    }
  }

  private def putAuditingNotification(notification: Notification): Unit = {

    val streamName = config.auditing.stream
    val bytes = ThriftSerializer.serializeToBytes(notification, Some(GzipType), None)
    if (bytes.length > config.auditing.maxDataSize) {
      Logger.error(s"$streamName - NOT sending because size (${bytes.length} bytes) is larger than max kinesis size(${config.auditing.maxDataSize})")
    } else {
      Logger.info(s"$streamName - sending auditing thrift update with size of ${bytes.length} bytes")
      client.putRecordAsync(
        new PutRecordRequest()
          .withData(ByteBuffer.wrap(bytes))
          .withStreamName(streamName)
          .withPartitionKey(partitionKey),
        KinesisLoggingAsyncHandler
      )
    }
  }
}
