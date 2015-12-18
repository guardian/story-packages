package updates

import conf.{Configuration, aws}
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordsRequestEntry, PutRecordsRequest, PutRecordsResult}
import storypackage.thrift.{Event, EventType, ArticleType, Article}
import com.gu.facia.client.models.{CollectionJson, Trail}
import java.nio.ByteBuffer
import com.amazonaws.regions.Regions
import play.api.Logger
import services.ConfigAgent


class CapiUpdates() extends ThriftSerializer {

  val streamName: String = Configuration.updates.capi
  val maxDataSize = Configuration.updates.maxDataSize

  private lazy val client = {
    val kinesisClient = new AmazonKinesisAsyncClient(
      aws.mandatoryCredentials
    )
    kinesisClient.configureRegion(Regions.fromName(Configuration.aws.region))
    kinesisClient
  }

  def putCapiUpdate(collections: Map[String, CollectionJson]): Unit = {
    val collectionKey = collections.keys.head
    val articles = collections(collectionKey).live
    var headline: Option[String] = None
    var href: Option[String] = None
    var trailText: Option[String] = None
    var imageSrc: Option[String] = None
    var isBoosted: Option[Boolean] = None
    var imageHide: Option[Boolean] = None
    var showMainVideo: Option[Boolean] = None
    var showKickerTag: Option[Boolean] = None
    var showKickerSection: Option[Boolean] = None
    var byline: Option[String] = None
    var imageCutoutSrc: Option[String] = None
    var showBoostedHeadline: Option[Boolean] = None
    var showQuotedHeadline: Option[Boolean] = None

    val thriftArticles = articles.map((article) => {

      article.meta match {
        case Some(trailMetaData) => {
          headline = trailMetaData.headline
          href = trailMetaData.href
          trailText = trailMetaData.trailText
          trailMetaData.imageReplace match {
            case Some(_) => imageSrc = trailMetaData.imageSrc
            case None => ;
          }
          imageSrc = trailMetaData.imageSrc
          isBoosted = trailMetaData.isBoosted
          imageHide = trailMetaData.imageHide
          showMainVideo = trailMetaData.showMainVideo
          showKickerTag = trailMetaData.showKickerTag
          showKickerSection = trailMetaData.showKickerSection
          showBoostedHeadline = trailMetaData.showBoostedHeadline
          trailMetaData.showByline match {
            case Some(_) => byline = trailMetaData.byline
            case None => ;
          }
          trailMetaData.imageCutoutReplace match {
            case Some(_) => imageCutoutSrc = trailMetaData.imageCutoutSrc
            case None => ;
          }

        }
        case None => ;
      }

      Article(article.id, ArticleType.Article, headline, href, trailText, imageSrc, isBoosted, imageHide, showMainVideo, showKickerTag, showKickerSection, byline, imageCutoutSrc, showBoostedHeadline, showQuotedHeadline)
    })
    val event = Event(EventType.Update, collectionKey, thriftArticles)
    sendUpdate(event)

  }

  def sendUpdate(event: Event) {


    val request = new PutRecordsRequest().withStreamName(streamName)

    val bytes = serializeToBytes(event)
      if (bytes.length > maxDataSize) {
        Logger.error(s"${streamName} - NOT sending because size (${bytes.length} bytes) is larger than max kinesis size(${maxDataSize})")
      } else {
        Logger.info(s"${streamName} - sending with size of ${bytes.length} bytes")
        val record = new PutRecordsRequestEntry()
          .withPartitionKey(event.packageId)
          .withData(ByteBuffer.wrap(bytes))
          request.withRecords(record)

        /* Send the request to Kinesis*/
        client.putRecordsAsync(request)
      }

  }
}

