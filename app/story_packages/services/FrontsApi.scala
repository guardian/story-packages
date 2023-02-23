package story_packages.services

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.gu.facia.client.{AmazonSdkS3Client, ApiClient}
import conf.ApplicationConfiguration

import scala.concurrent.ExecutionContext.Implicits.global

class FrontsApi(config: ApplicationConfiguration) {
  lazy val amazonClient: ApiClient = {

    val client = AmazonS3ClientBuilder.standard
      .withCredentials(config.aws.mandatoryCredentials)
      .withEndpointConfiguration(new EndpointConfiguration(config.aws.endpoints.s3, config.aws.region))
      .build

    val bucket = config.aws.bucket
    val stage = config.facia.stage.toUpperCase
    ApiClient(bucket, stage, AmazonSdkS3Client(client))
  }
}
