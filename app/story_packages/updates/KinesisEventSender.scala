package story_packages.updates

import com.gu.facia.client.models.CollectionJson
import com.gu.storypackage.model.v1._
import com.gu.thrift.serializer.{GzipType, ThriftSerializer}
import org.joda.time.DateTime
import conf.ApplicationConfiguration
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.{PutRecordsRequest, PutRecordsRequestEntry, PutRecordsResponse}
import story_packages.services.Logging

import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.{Failure, Success}

class KinesisEventSender(config: ApplicationConfiguration) extends Logging {

  private val streamName: String = config.updates.capi

  case class AsyncEventHandler(collectionId: String)  {
    def onError(exception: Throwable): Unit = {
      Logger.error(s"$streamName - Error when sending thrift update to kinesis stream", exception)
    }
    def onSuccess(request: PutRecordsRequest, result: PutRecordsResponse): Unit = {
      Logger.info(s"$streamName - Kinesis thrift update for collection $collectionId sent correctly")
    }
  }

  private lazy val client = {
    KinesisAsyncClient.builder()
      .credentialsProvider(config.awsV2.mandatoryCredentials)
      .region(Region.of(config.awsV2.region))
      .build()
  }

  private def createUpdatePayload(collectionJson: CollectionJson): List[Article] = {
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

  def putReindexDelete(packageId: String, displayName: String, collectionJson: CollectionJson, isHidden: Boolean)(implicit ec:ExecutionContext): Unit = {
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

  def putReindexUpdate(packageId: String, displayName: String, collectionJson: CollectionJson, isHidden: Boolean)(implicit ec:ExecutionContext): Unit = {
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

  def putCapiDelete(packageId: String, isHidden: Boolean)(implicit ec:ExecutionContext): Unit = {
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

  def putCapiUpdate(packageId: String, displayName: String, collectionJson: CollectionJson, isHidden: Boolean)(implicit ec:ExecutionContext): Unit = {
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

  private def sendUpdate(streamName: String, collectionId: String, event: Event)(implicit ec:ExecutionContext): Unit = {
    val bytes = ThriftSerializer.serializeToBytes(event, Some(GzipType), Some(128))
    if (bytes.length > config.updates.maxDataSize) {
      Logger.error(s"$streamName - NOT sending because size (${bytes.length} bytes) is larger than max size (${config.updates.maxDataSize})")
    } else {
      Logger.info(s"$streamName - sending thrift update with size of ${bytes.length} bytes")
      val record = PutRecordsRequestEntry.builder()
        .partitionKey(event.packageId)
        .data(SdkBytes.fromByteArray(bytes))
        .build()

      val request = PutRecordsRequest.builder()
        .streamName(streamName)
        .records(record)
        .build()

      val handler = AsyncEventHandler(collectionId)

      client
        .putRecords(request)
        .asScala
        .onComplete {
          case Success(response) => handler.onSuccess(request, response)
          case Failure(e) => handler.onError(e)
        }
    }

  }
}
