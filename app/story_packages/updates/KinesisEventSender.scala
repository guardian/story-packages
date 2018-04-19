package story_packages.updates

import java.nio.ByteBuffer

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClientBuilder
import com.amazonaws.services.kinesis.model.{PutRecordsRequest, PutRecordsRequestEntry, PutRecordsResult}
import com.gu.facia.client.models.CollectionJson
import com.gu.storypackage.model.v1._
import com.gu.thrift.serializer.{GzipType, ThriftSerializer}
import org.joda.time.DateTime
import play.api.Logger
import conf.ApplicationConfiguration

class KinesisEventSender(config: ApplicationConfiguration) {

  val streamName: String = config.updates.capi

  def eventHandler(collectionId: String) = new AsyncHandler[PutRecordsRequest, PutRecordsResult] {
    def onError(exception: Exception): Unit = {
      Logger.error(s"$streamName - Error when sending thrift update to kinesis stream", exception)
    }
    def onSuccess(request: PutRecordsRequest, result: PutRecordsResult): Unit = {
      Logger.info(s"$streamName - Kinesis thrift update for collection $collectionId sent correctly")
    }
  }

  private lazy val client = {
    AmazonKinesisAsyncClientBuilder.standard
      .withCredentials(config.aws.mandatoryCredentials)
      .withRegion(config.aws.region)
      .build
  }

  def createUpdatePayload(collectionJson: CollectionJson): List[Article] = {
    collectionJson.live.map(article => {
      article.meta match {
        case Some(trailMetaData) =>
          Article(
            id = article.id,
            articleType = ArticleType.Article,
            group = trailMetaData.group match {
              case Some("1") => Group.Included
              case _ => Group.Linked
            },
            headline = trailMetaData.headline,
            href = trailMetaData.href,
            trailText = trailMetaData.trailText,
            imageSrc = if (trailMetaData.imageReplace.exists(identity)) {
              trailMetaData.imageSrc
            } else if (trailMetaData.imageCutoutReplace.exists(identity)) {
              trailMetaData.imageCutoutSrc
            } else {
              None
            },
            isBoosted = trailMetaData.isBoosted,
            imageHide = trailMetaData.imageHide,
            showMainVideo = trailMetaData.showMainVideo,
            showKickerTag = trailMetaData.showKickerTag,
            showKickerSection = trailMetaData.showKickerSection,
            showBoostedHeadline = trailMetaData.showBoostedHeadline,
            byline = trailMetaData.showByline.flatMap{ enabled =>
              if (enabled) trailMetaData.byline
              else None
            },
            customKicker = trailMetaData.customKicker
          )
        case None =>
          Article(
            id = article.id,
            group = Group.Linked,
            articleType = ArticleType.Article
          )}
    })
  }

  def putReindexDelete(packageId: String, displayName: String, collectionJson: CollectionJson, isHidden: Boolean): Unit = {
    sendUpdate(
      if (isHidden) config.updates.reindexPreview else config.updates.reindex,
      packageId,
      Event(
        eventType = EventType.Delete,
        packageId = packageId,
        packageName = displayName,
        lastModified = collectionJson.lastUpdated.toString(),
        articles = createUpdatePayload(collectionJson)))
  }

  def putReindexUpdate(packageId: String, displayName: String, collectionJson: CollectionJson, isHidden: Boolean): Unit = {
    sendUpdate(
      if (isHidden) config.updates.reindexPreview else config.updates.reindex,
      packageId,
      Event(
        eventType = EventType.Update,
        packageId = packageId,
        packageName = displayName,
        lastModified = collectionJson.lastUpdated.toString(),
        articles = createUpdatePayload(collectionJson)))
  }

  def putCapiDelete(packageId: String, isHidden: Boolean): Unit = {
    sendUpdate(
      if (isHidden) config.updates.preview else config.updates.capi,
      packageId,
      Event(
        eventType = EventType.Delete,
        packageId = packageId,
        packageName = "",
        lastModified = DateTime.now().toString(),
        articles = Nil))
  }

  def putCapiUpdate(packageId: String, displayName: String, collectionJson: CollectionJson, isHidden: Boolean): Unit = {
    sendUpdate(
      if (isHidden) config.updates.preview else config.updates.capi,
      packageId,
      Event(
        eventType = EventType.Update,
        packageId = packageId,
        packageName = displayName,
        lastModified = collectionJson.lastUpdated.toString(),
        articles = createUpdatePayload(collectionJson)))
  }

  def sendUpdate(streamName: String, collectionId: String, event: Event) {
    val request = new PutRecordsRequest().withStreamName(streamName)
    val bytes = ThriftSerializer.serializeToBytes(event, Some(GzipType), Some(128))
    if (bytes.length > config.updates.maxDataSize) {
      Logger.error(s"$streamName - NOT sending because size (${bytes.length} bytes) is larger than max size (${config.updates.maxDataSize})")
    } else {
      Logger.info(s"$streamName - sending thrift update with size of ${bytes.length} bytes")
      val record = new PutRecordsRequestEntry()
        .withPartitionKey(event.packageId)
        .withData(ByteBuffer.wrap(bytes))

      request.withRecords(record)
      client.putRecordsAsync(request, eventHandler(collectionId))
    }

  }
}
