package story_packages.services

import com.gu.pandomainauth.model.User
import story_packages.metrics.S3Metrics.S3ClientExceptionsMetric
import org.joda.time.DateTime
import conf.ApplicationConfiguration
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, NoSuchKeyException, ObjectCannedACL, PutObjectRequest}

import java.nio.charset.StandardCharsets
import scala.io.{Codec, Source}

trait S3 extends Logging {
  def config: ApplicationConfiguration

  lazy val bucket = config.awsV2.bucket

  private def withS3Result[T](key: String)(action: ResponseInputStream[GetObjectResponse] => T): Option[T] = config.awsV2.s3Client.flatMap { client =>
    try {
      val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
      val result = client.getObject(request)

      // http://stackoverflow.com/questions/17782937/connectionpooltimeoutexception-when-iterating-objects-in-s3
      try {
        Some(action(result))
      }
      catch {
        case e: Exception =>
          S3ClientExceptionsMetric.increment()
          throw e
      }
      finally {
        result.close()
      }
    } catch {
      case e: NoSuchKeyException if e.statusCode() == 404 => {
        Logger.warn("not found at %s - %s" format(bucket, key))
        None
      }
      case e: Exception => {
        S3ClientExceptionsMetric.increment()
        throw e
      }
    }
  }

  def get(key: String)(implicit codec: Codec): Option[String] = withS3Result(key) {
    result => Source.fromInputStream(result).mkString
  }


  def getWithLastModified(key: String): Option[(String, DateTime)] = withS3Result(key) {
    result =>
      val content = Source.fromInputStream(result).mkString
      val lastModified = new DateTime(result.response().lastModified())
      (content, lastModified)
  }

  def getLastModified(key: String): Option[DateTime] = withS3Result(key) {
    result => new DateTime(result.response().lastModified)
  }

  def putPrivate(key: String, value: String, contentType: String): Unit = {
    put(key: String, value: String, contentType: String, ObjectCannedACL.PRIVATE)
  }

  private def put(key: String, value: String, contentType: String, accessControlList: ObjectCannedACL): Unit = {
    val request = PutObjectRequest
      .builder()
      .bucket(bucket)
      .key(key)
      .acl(accessControlList)
      .cacheControl("no-cache,no-store")
      .contentType(contentType)
      .build()

    try {
      config.awsV2.s3Client.foreach(_.putObject(request, RequestBody.fromString(value, StandardCharsets.UTF_8)))
    } catch {
      case e: Exception =>
        S3ClientExceptionsMetric.increment()
        throw e
    }
  }
}

class S3FrontsApi(val config: ApplicationConfiguration, isTest: Boolean) extends S3 {

  lazy val stage = if (isTest) "TEST" else config.facia.stage.toUpperCase
  val namespace = "frontsapi"
  lazy val location = s"$stage/$namespace"

  def putCollectionJson(id: String, json: String) = {
    val putLocation: String = s"$location/collection/$id/collection.json"
    putPrivate(putLocation, json, "application/json")
  }

  def archive(id: String, json: String, identity: User) = {
    val now = DateTime.now
    putPrivate(s"$location/history/collection/${now.year.get}/${"%02d".format(now.monthOfYear.get)}/${"%02d".format(now.dayOfMonth.get)}/$id/${now}.${identity.email}.json", json, "application/json")
  }

  def getCollectionLastModified(path: String): Option[String] =
    getLastModified(s"/collection/$path/collection.json").map(_.toString)
}
