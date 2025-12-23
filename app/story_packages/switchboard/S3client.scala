package story_packages.switchboard

import play.api.libs.json.{JsError, JsSuccess, Json}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, NoSuchKeyException}
import story_packages.services.Logging

import scala.io.Source
import scala.util.{Failure, Success, Try}

class S3client (conf: SwitchboardConfiguration) extends Logging {

  lazy val bucket: String = conf.bucket
  lazy val objectKey: String = conf.objectKey

  lazy val client: S3Client = S3Client
    .builder()
    .credentialsProvider(conf.credentials)
    .region(Region.of(conf.region))
    .endpointOverride(conf.endpoint)
    .build

  def getSwitches(): Option[Map[String, Boolean]] = {
    val request: GetObjectRequest = GetObjectRequest.builder.bucket(bucket).key(objectKey).build()
    val t = Try(client.getObject(request)) flatMap { result =>
      val resultAsString: String = Source.fromInputStream(result).mkString
      result.close()
      Try(Json.parse(resultAsString)).map { json =>
        json.validate[Map[String, Boolean]] match {
          case JsSuccess(m, _) =>
            Logger.info("successfully got switches from switchboard at %s - %s" format(bucket, objectKey))
            json.asOpt[Map[String, Boolean]]
          case JsError(_) =>
            Logger.error("invalid json content at %s - %s : %s" format(bucket, objectKey, resultAsString))
            None}}}

    t match {
      case Success(result) => result
      case Failure(e: NoSuchKeyException) if e.statusCode == 404 =>
        Logger.warn("switches status not found at %s - %s" format(bucket, objectKey))
        None
      case Failure(e) =>
        Logger.error("Failure in switchboard S3 getSwitches", e)
        None
    }
  }
}
