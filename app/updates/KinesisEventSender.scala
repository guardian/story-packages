package updates

import java.nio.ByteBuffer

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordsRequest, PutRecordsRequestEntry, PutRecordsResult}
import com.gu.facia.client.models.CollectionJson
import conf.{Configuration, aws}
import play.api.Logger
import storypackage.thrift.{Article, ArticleType, Event, EventType}

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

  def putCapiUpdate(collectionId: String, collectionJson: CollectionJson): Unit = {
    val thriftArticles = collectionJson.live.map(article => {
      article.meta match {
        case Some(trailMetaData) =>
          Article(
            id = article.id,
            articleType = ArticleType.Article,
            headline = trailMetaData.headline,
            href = trailMetaData.href,
            trailText = trailMetaData.trailText,
            imageSrc = trailMetaData.imageReplace.flatMap{ enabled =>
              if (enabled) trailMetaData.imageSrc
              else None
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
            customKicker = trailMetaData.customKicker,
            imageCutoutSrc = trailMetaData.imageCutoutReplace.flatMap{ enabled =>
              if (enabled) trailMetaData.imageCutoutSrc
              else None
            })
        case None =>
          Article(
            id = article.id,
            articleType = ArticleType.Article
          )}
    })

    sendUpdate(collectionId, Event(EventType.Update, collectionId, thriftArticles))
  }

  def sendUpdate(collectionId: String, event: Event) {
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
