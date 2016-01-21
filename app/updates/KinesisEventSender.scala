package updates

import java.nio.ByteBuffer

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordsRequest, PutRecordsRequestEntry, PutRecordsResult}
import com.gu.facia.client.models.CollectionJson
import com.gu.storypackage.model.v1.{Article, ArticleType, Group, Event, EventType}
import conf.{Configuration, aws}
import org.joda.time.DateTime
import play.api.Logger

object KinesisEventSender extends ThriftSerializer {

  val streamName: String = Configuration.updates.capi

  def eventHandler(collectionId: String) = new AsyncHandler[PutRecordsRequest, PutRecordsResult] {
    def onError(exception: Exception): Unit = {
      Logger.error(s"$streamName - Error when sending thrift update to kinesis stream", exception)
    }
    def onSuccess(request: PutRecordsRequest, result: PutRecordsResult): Unit = {
      Logger.info(s"$streamName - Kinesis thrift update for collection $collectionId sent correctly")
    }
  }

  private lazy val client = {
    val kinesisClient = new AmazonKinesisAsyncClient(
      aws.mandatoryCredentials
    )
    kinesisClient.configureRegion(Regions.fromName(Configuration.aws.region))
    kinesisClient
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

  def putReindexUpdate(collectionId: String, collectionJson: CollectionJson): Unit = {
    sendUpdate(
      Configuration.updates.reindex,
      collectionId,
      Event(EventType.Update, collectionId, collectionJson.lastUpdated.toString(), createUpdatePayload(collectionJson)))
  }

  def putCapiDelete(collectionId: String): Unit = {
    sendUpdate(
      Configuration.updates.capi,
      collectionId,
      Event(EventType.Delete, collectionId, DateTime.now().toString(), List()))
  }

  def putCapiUpdate(collectionId: String, collectionJson: CollectionJson): Unit = {
    sendUpdate(
      Configuration.updates.capi,
      collectionId,
      Event(EventType.Update, collectionId, collectionJson.lastUpdated.toString(), createUpdatePayload(collectionJson)))
  }

  def sendUpdate(streamName: String, collectionId: String, event: Event) {
    val request = new PutRecordsRequest().withStreamName(streamName)
    val bytes = serializeToBytes(event)
    if (bytes.length > Configuration.updates.maxDataSize) {
      Logger.error(s"$streamName - NOT sending because size (${bytes.length} bytes) is larger than max kinesis size(${Configuration.updates.maxDataSize})")
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
