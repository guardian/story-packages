package stream

import conf.{Configuration, aws}
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.{PutRecordsRequestEntry, PutRecordsRequest, PutRecordsResult}
import thrift.{Event, EventType, ArticleType, Article}
import something.ThriftSerializer
import com.gu.facia.client.models.{CollectionJson, Trail}
import java.nio.ByteBuffer
import com.amazonaws.regions.Regions
import play.api.Logger
import services.ConfigAgent


class CapiUpdates() extends ThriftSerializer {

  val streamName: String = Configuration.updates.capi.get
  val maxDataSize = 1024000

  private lazy val client = {
    val kinesisClient = new AmazonKinesisAsyncClient(
      aws.mandatoryCredentials
    )
    kinesisClient.configureRegion(Regions.fromName(Configuration.aws.region))
    kinesisClient
  }

  def putCapiUpdate(updatedCollections: Option[Map[String, CollectionJson]]): Unit = {
    val collections = updatedCollections.get
    val collectionKey = collections.keys.head
    val articles = collections(collectionKey).live
    var headline: Option[String] = None

    val thriftArticles = articles.map((article) => {

      article.meta match {
        case Some(trailMetaData) => {
          headline = trailMetaData.headline
        }
        case None => println("no meta")
      }

      Article(article.id, ArticleType.Article, headline)
    })
    val event = Event(EventType.Update, collectionKey, thriftArticles)
    sendUpdate(event)

  }

  def sendUpdate(event: Event) {


    val request = new PutRecordsRequest().withStreamName(streamName)

    val bytes = serializeToBytes(event)
      if (bytes.length > maxDataSize) {
        Logger.error(s"${streamName} - NOT sending because size (${bytes.length} bytes) is larger than max kinesis size(${maxDataSize})")
        false
      } else {
        Logger.info(s"${streamName} - sending with size of ${bytes.length} bytes")
        true
      }

      val record = new PutRecordsRequestEntry()
        .withPartitionKey(event.packageId)
        .withData(ByteBuffer.wrap(bytes))
      request.withRecords(record)

    /* Send the request to Kinesis*/
    client.putRecordsAsync(request)

  }
}

