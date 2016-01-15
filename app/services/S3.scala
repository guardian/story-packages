package services

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList.{Private, PublicRead}
import com.amazonaws.services.s3.model._
import com.amazonaws.util.StringInputStream
import com.gu.pandomainauth.model.User
import conf.{Configuration, aws}
import metrics.S3Metrics.S3ClientExceptionsMetric
import org.joda.time.DateTime
import play.Play
import play.api.Logger

import scala.io.{Codec, Source}

trait S3 {

  lazy val bucket = Configuration.aws.bucket

  lazy val client: Option[AmazonS3Client] = aws.credentials.map{ credentials => {
      val client = new AmazonS3Client(credentials)
      client.setEndpoint(AwsEndpoints.s3)
      client
    }
  }

  private def withS3Result[T](key: String)(action: S3Object => T): Option[T] = client.flatMap { client =>
    try {

      val request = new GetObjectRequest(bucket, key)
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
      case e: AmazonS3Exception if e.getStatusCode == 404 => {
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
    result => Source.fromInputStream(result.getObjectContent).mkString
  }


  def getWithLastModified(key: String): Option[(String, DateTime)] = withS3Result(key) {
    result =>
      val content = Source.fromInputStream(result.getObjectContent).mkString
      val lastModified = new DateTime(result.getObjectMetadata.getLastModified)
      (content, lastModified)
  }

  def getLastModified(key: String): Option[DateTime] = withS3Result(key) {
    result => new DateTime(result.getObjectMetadata.getLastModified)
  }

  def putPrivate(key: String, value: String, contentType: String) {
    put(key: String, value: String, contentType: String, Private)
  }

  private def put(key: String, value: String, contentType: String, accessControlList: CannedAccessControlList) {
    val metadata = new ObjectMetadata()
    metadata.setCacheControl("no-cache,no-store")
    metadata.setContentType(contentType)
    metadata.setContentLength(value.getBytes("UTF-8").length)

    val request = new PutObjectRequest(bucket, key, new StringInputStream(value), metadata).withCannedAcl(accessControlList)

    try {
      client.foreach(_.putObject(request))
    } catch {
      case e: Exception =>
        S3ClientExceptionsMetric.increment()
        throw e
    }
  }
}

object S3 extends S3

object S3FrontsApi extends S3 {

  override lazy val bucket = Configuration.aws.bucket
  lazy val stage = if (Play.isTest) "TEST" else Configuration.facia.stage.toUpperCase
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
