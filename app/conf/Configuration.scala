package conf

import java.io.{File, FileInputStream, InputStream}
import java.net.{URI, URL}
import com.amazonaws.AmazonClientException
import com.amazonaws.auth.profile.{ProfileCredentialsProvider => ProfileCredentialsProviderV1}
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentialsProviderChain, InstanceProfileCredentialsProvider => InstanceProfileCredentialsProviderV1}
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.permissions.PermissionsConfig
import org.apache.commons.io.IOUtils
import play.api.Mode
import play.api.{Configuration => PlayConfiguration}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider, AwsCredentialsProviderChain, InstanceProfileCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.{Region, ServiceMetadata}
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.s3.S3Client
import story_packages.services.Logging

import java.nio.charset.Charset
import scala.language.reflectiveCalls
import scala.jdk.CollectionConverters._

class BadConfigurationException(msg: String) extends RuntimeException(msg)

class ApplicationConfiguration(val playConfiguration: PlayConfiguration, val envMode: Mode) extends Logging  {
  private val propertiesFile = "/etc/gu/story-packages.properties"
  private val installVars = new File(propertiesFile) match {
    case f if f.exists => IOUtils.toString(new FileInputStream(f), Charset.defaultCharset())
    case _ =>
      Logger.warn("Missing configuration file $propertiesFile")
      ""
  }

  private val properties = Properties(installVars)
  private val stageFromProperties = properties.getOrElse("STAGE", "CODE")

  private def getString(property: String): Option[String] =
    playConfiguration.getOptional[String](stageFromProperties + "." + property)
      .orElse(playConfiguration.getOptional[String](property))

  private def getMandatoryString(property: String): String = getString(property)
    .getOrElse(throw new BadConfigurationException(s"$property of type string not configured for stage $stageFromProperties"))

  private def getBoolean(property: String): Option[Boolean] =
    playConfiguration.getOptional[Boolean](stageFromProperties + "." + property)
      .orElse(playConfiguration.getOptional[Boolean](property))

  private def getMandatoryBoolean(property: String): Boolean = getBoolean(property)
    .getOrElse(throw new BadConfigurationException(s"$property of type boolean not configured for stage $stageFromProperties"))

  object environment {
    lazy val applicationName: String = getMandatoryString("environment.applicationName")
    val stage: String = stageFromProperties.toLowerCase
    val mode: Mode = envMode
  }

  object aws {
    lazy val region: String = getMandatoryString("aws.region")
    lazy val bucket: String = getMandatoryString("aws.bucket")

    object endpoints {
      private val _region = RegionUtils.getRegion(region)
//      val monitoring: String = _region.getServiceEndpoint(AmazonCloudWatch.ENDPOINT_PREFIX)
      val dynamoDB: String = _region.getServiceEndpoint(AmazonDynamoDB.ENDPOINT_PREFIX)
//      val s3: String = _region.getServiceEndpoint(AmazonS3.ENDPOINT_PREFIX)
    }

    def mandatoryCredentials: AWSCredentialsProvider = credentials.getOrElse(throw new BadConfigurationException("AWS credentials are not configured"))
    val credentials: Option[AWSCredentialsProvider] = {
      val provider = new AWSCredentialsProviderChain(
        new ProfileCredentialsProviderV1("cmsFronts"),
        InstanceProfileCredentialsProviderV1.getInstance
      )

      // this is a bit of a convoluted way to check whether we actually have credentials.
      // I guess in an ideal world there would be some sort of isConfigued() method...
      try {
        val creds = provider.getCredentials
        Some(provider)
      } catch {
        case ex: AmazonClientException =>
          Logger.error("amazon client exception")

          // We really, really want to ensure that PROD is configured before saying a box is OK
          if (envMode == Mode.Prod) throw ex
          // this means that on dev machines you only need to configure keys if you are actually going to use them
          None
      }
    }

//    val s3Client: Option[AmazonS3] = credentials.map { credentials =>
//      AmazonS3ClientBuilder.standard
//        .withCredentials(credentials)
//        .withEndpointConfiguration(new EndpointConfiguration(endpoints.s3, region))
//        .build
//    }
  }

  object awsV2 {
    lazy val region: String = getMandatoryString("aws.region")
    lazy val bucket: String = getMandatoryString("aws.bucket")

    object endpoints {
      private val _region = Region.of(region)
      private def endpointFor(serviceMetadata: ServiceMetadata): URI = {
        val uri = serviceMetadata.endpointFor(_region)
        if (uri.isAbsolute) uri else new URI(s"https://$uri")
      }

      val monitoring: URI = endpointFor(CloudWatchClient.serviceMetadata())
      //      val dynamoDB: String = _region.getServiceEndpoint(AmazonDynamoDB.ENDPOINT_PREFIX)
      val s3: URI = endpointFor(S3Client.serviceMetadata())
    }

    def mandatoryCredentials: AwsCredentialsProvider = credentials.getOrElse(throw new BadConfigurationException("AWS credentials are not configured"))
    val credentials: Option[AwsCredentialsProvider] = {
      val provider = AwsCredentialsProviderChain.of(
        ProfileCredentialsProvider.create("cmsFronts"),
        InstanceProfileCredentialsProvider.create()
      )

      // this is a bit of a convoluted way to check whether we actually have credentials.
      // I guess in an ideal world there would be some sort of isConfigued() method...
      try {
        val creds = provider.resolveCredentials()
        Some(provider)
      } catch {
        case ex: AmazonClientException =>
          Logger.error("amazon client exception")

          // We really, really want to ensure that PROD is configured before saying a box is OK
          if (envMode == Mode.Prod) throw ex
          // this means that on dev machines you only need to configure keys if you are actually going to use them
          None
      }
    }

    val s3Client: Option[S3Client] = credentials.map { credentials =>
      S3Client
        .builder()
        .credentialsProvider(credentials)
        .region(Region.of(region))
        .endpointOverride(endpoints.s3)
        .build
    }
  }

  object contentApi {
    val contentApiLiveHost: String = getMandatoryString("content.api.host")
    val packagesLiveHost: String = getString("content.api.packages.host").getOrElse(contentApiLiveHost)
    val contentApiDraftHost: String = getMandatoryString("content.api.draft.iam-host")
    val packagesDraftHost: String = getString("content.api.packages.draft.host").getOrElse(contentApiDraftHost)

    lazy val key: Option[String] = getString("content.api.key")

    lazy val previewRole: String = getMandatoryString("content.api.draft.role")
  }

  object facia {
    lazy val stage: String = getString("facia.stage").getOrElse(stageFromProperties)
    val includedCollectionCap: Int = 12
    val linkingCollectionCap: Int = 50
  }

  object logging {
    lazy val stream: String = getMandatoryString("logging.kinesis.stream")
    lazy val streamRegion: String = getMandatoryString("logging.kinesis.region")
    lazy val streamRole: String = getMandatoryString("logging.kinesis.roleArn")
    lazy val app: String = getMandatoryString("logging.fields.app")
    lazy val enabled: Boolean = getBoolean("logging.enabled").getOrElse(false)
  }

  object media {
    lazy val baseUrl: Option[String] = getString("media.base.url")
    lazy val apiUrl: Option[String] = getString("media.api.url")
  }

  object ophanApi {
    lazy val key: Option[String] = getString("ophan.api.key")
    lazy val host: Option[String] = getString("ophan.api.host")
  }

  object pandomain {
    lazy val host: String = getMandatoryString("pandomain.host")
    lazy val domain: String = getMandatoryString("pandomain.domain")
    lazy val bucketName: String = getMandatoryString("pandomain.bucketName")
    lazy val settingsFileKey = s"$domain.settings"
    lazy val service: String = getMandatoryString("pandomain.service")
    lazy val roleArn: String = getMandatoryString("pandomain.roleArn")
  }

  object sentry {
    lazy val publicDSN: String = getString("sentry.publicDSN").getOrElse("")
  }

  object storage {
    val configTable: String = properties.getOrElse("TABLE_CONFIG", throw new BadConfigurationException("Missing TABLE_CONFIG property"))
    val maxPageSize = 50
    val maxLatestDays = 15
    val maxLatestResults = 50
  }

  object switchBoard {
    val bucket: String = getMandatoryString("switchboard.bucket")
    val objectKey: String = getMandatoryString("switchboard.object")
  }

  object updates {
    lazy val capi: String = properties.getOrElse("CAPI_STREAM", throw new BadConfigurationException("CAPI stream name is not configured"))
    lazy val preview: String = properties.getOrElse("PREVIEW_CAPI_STREAM", throw new BadConfigurationException("CAPI stream name is not configured"))
    lazy val reindex: String = properties.getOrElse("REINDEX_STREAM", throw new BadConfigurationException("REINDEX stream name is not configured"))
    lazy val reindexPreview: String = properties.getOrElse("PREVIEW_REINDEX_STREAM", throw new BadConfigurationException("REINDEX stream name is not configured"))
    lazy val maxDataSize: Int = 1024000
  }

  object reindex {
    lazy val key: String = getMandatoryString("reindex.key")
    lazy val progressTable: String = properties.getOrElse("REINDEX_TABLE", throw new BadConfigurationException("REINDEX_TABLE is not configured"))
  }

  object latest {
    lazy val pageSize = 20
  }

  val permissions: PermissionsConfig = PermissionsConfig(
    stage = environment.stage.toUpperCase,
    region = aws.region,
    awsCredentials = aws.mandatoryCredentials,
  )
}

object Properties extends AutomaticResourceManagement {
  def apply(is: InputStream): Map[String, String] = {
    val properties = new java.util.Properties()
    withCloseable(is) { properties load _ }
    properties.asScala.toMap
  }

  def apply(text: String): Map[String, String] = apply(IOUtils.toInputStream(text, Charset.defaultCharset()))
  def apply(file: File): Map[String, String] = apply(new FileInputStream(file))
  def apply(url: URL): Map[String, String] = apply(url.openStream)
}

trait AutomaticResourceManagement {
  def withCloseable[T <: { def close(): Unit }](closeable: T): Object {def apply[S](body: T => S): S} = new {
    def apply[S](body: T => S): S = try {
      body(closeable)
    } finally {
      closeable.close()
    }
  }
}

