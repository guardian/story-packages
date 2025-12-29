package story_packages.services

import com.gu.etagcaching.aws.sdkv2.s3.S3ObjectFetching
import com.gu.facia.client.{ApiClient, Environment}
import conf.ApplicationConfiguration
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient

import scala.concurrent.ExecutionContext.Implicits.global

class FrontsApi(config: ApplicationConfiguration) {
  lazy val amazonClient: ApiClient = {

    val client = S3AsyncClient
      .builder()
      .httpClient(NettyNioAsyncHttpClient.builder().build())
      .credentialsProvider(config.awsV2.mandatoryCredentials)
      .region(Region.EU_WEST_1)
      .build()

    val bucket = config.awsV2.bucket
    val stage = config.facia.stage.toUpperCase

    ApiClient.withCaching(
      bucket,
      Environment(stage),
      S3ObjectFetching.byteArraysWith(client),
    )
  }
}
