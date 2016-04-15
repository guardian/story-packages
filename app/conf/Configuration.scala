package conf

import java.io.{File, FileInputStream, InputStream}
import java.net.URL

import com.amazonaws.AmazonClientException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import org.apache.commons.io.IOUtils
import play.api.Play.current
import play.api.{Logger, Play, Configuration => PlayConfiguration}

import scala.collection.JavaConversions._
import scala.io.Source
import scala.language.reflectiveCalls

class BadConfigurationException(msg: String) extends RuntimeException(msg)

class ApplicationConfiguration(val playConfiguration: PlayConfiguration, val isProd: Boolean) {
  private val propertiesFile = "/etc/gu/story-packages.properties"
  private val installVars = new File(propertiesFile) match {
    case f if f.exists => IOUtils.toString(new FileInputStream(f))
    case _ =>
      Logger.warn("Missing configuration file $propertiesFile")
      ""
  }

  private val properties = Properties(installVars)
  private val stageFromProperties = properties.getOrElse("STAGE", "CODE")

  private def getString(property: String): Option[String] =
    playConfiguration.getString(stageFromProperties + "." + property)
      .orElse(playConfiguration.getString(property))

  private def getMandatoryString(property: String): String = getString(property)
    .getOrElse(throw new BadConfigurationException(s"$property of type string not configured for stage $stageFromProperties"))

  private def getBoolean(property: String): Option[Boolean] =
    playConfiguration.getBoolean(stageFromProperties + "." + property)
      .orElse(playConfiguration.getBoolean(property))

  private def getMandatoryBoolean(property: String): Boolean = getBoolean(property)
    .getOrElse(throw new BadConfigurationException(s"$property of type boolean not configured for stage $stageFromProperties"))



  object environment {
    lazy val applicationName = getMandatoryString("environment.applicationName")
    val stage = stageFromProperties.toLowerCase
  }

  object aws {
    lazy val region = getMandatoryString("aws.region")
    lazy val bucket = getMandatoryString("aws.bucket")
    lazy val crossAccount = getMandatoryBoolean("aws.crossAccount")

    def mandatoryCredentials: AWSCredentialsProvider = credentials.getOrElse(throw new BadConfigurationException("AWS credentials are not configured"))
    val credentials: Option[AWSCredentialsProvider] = {
      val provider = new AWSCredentialsProviderChain(
        new ProfileCredentialsProvider("cmsFronts"),
        new InstanceProfileCredentialsProvider
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
          if (Play.isProd) throw ex
          // this means that on dev machines you only need to configure keys if you are actually going to use them
          None
      }
    }
  }

  object cdn {
    lazy val host = getString("cdn.host").getOrElse("")
  }

  object contentApi {
    case class Auth(user: String, password: String)

    val contentApiLiveHost: String = getMandatoryString("content.api.host")
    val packagesLiveHost: String = getString("content.api.packages.host").getOrElse(contentApiLiveHost)
    val contentApiDraftHost: String = getMandatoryString("content.api.draft.host")
    val packagesDraftHost: String = getString("content.api.packages.draft.host").getOrElse(contentApiDraftHost)

    lazy val key: Option[String] = getString("content.api.key")
    lazy val timeout: Int = 2000

    lazy val previewAuth: Option[Auth] = for {
      user <- getString("content.api.preview.user")
      password <- getString("content.api.preview.password")
    } yield Auth(user, password)
  }

  object facia {
    lazy val stage = getString("facia.stage").getOrElse(stageFromProperties)
    val includedCollectionCap: Int = 12
    val linkingCollectionCap: Int = 50
  }

  object logging {
    lazy val stream = getMandatoryString("logging.kinesis.stream")
    lazy val streamRegion = getMandatoryString("logging.kinesis.region")
    lazy val streamRole = getMandatoryString("logging.kinesis.roleArn")
    lazy val app = getMandatoryString("logging.fields.app")
    lazy val enabled = getBoolean("logging.enabled").getOrElse(false)
  }

  object media {
    lazy val baseUrl = getString("media.base.url")
    lazy val apiUrl = getString("media.api.url")
  }

  object ophanApi {
    lazy val key = getString("ophan.api.key")
    lazy val host = getString("ophan.api.host")
  }

  object pandomain {
    lazy val host = getMandatoryString("pandomain.host")
    lazy val domain = getMandatoryString("pandomain.domain")
    lazy val service = getMandatoryString("pandomain.service")
    lazy val roleArn = getMandatoryString("pandomain.roleArn")
  }

  object sentry {
    lazy val publicDSN = getString("sentry.publicDSN").getOrElse("")
  }

  object storage {
    val configTable = properties.getOrElse("TABLE_CONFIG", throw new BadConfigurationException("Missing TABLE_CONFIG property"))
    val maxPageSize = 50
    val maxLatestDays = 15
    val maxLatestResults = 50
  }

  object switchBoard {
    val bucket = getMandatoryString("switchboard.bucket")
    val objectKey = getMandatoryString("switchboard.object")
  }

  object updates {
    lazy val stream: Option[String] = properties.get("STREAM")
    lazy val capi: String = properties.getOrElse("CAPI_STREAM", throw new BadConfigurationException("CAPI stream name is not configured"))
    lazy val reindex: String = properties.getOrElse("REINDEX_STREAM", throw new BadConfigurationException("REINDEX stream name is not configured"))
    lazy val maxDataSize: Int = 1024000
  }

  object reindex {
    lazy val key: String = getMandatoryString("reindex.key")
    lazy val progressTable: String = properties.getOrElse("REINDEX_TABLE", throw new BadConfigurationException("REINDEX_TABLE is not configured"))
  }

  object latest {
    lazy val pageSize = 20
  }

  object auditing {
    lazy val stream: String = getMandatoryString("auditing.stream")
    lazy val maxDataSize: Int = 102400
  }
}

object Properties extends AutomaticResourceManagement {
  def apply(is: InputStream): Map[String, String] = {
    val properties = new java.util.Properties()
    withCloseable(is) { properties load _ }
    properties.toMap
  }

  def apply(text: String): Map[String, String] = apply(IOUtils.toInputStream(text))
  def apply(file: File): Map[String, String] = apply(new FileInputStream(file))
  def apply(url: URL): Map[String, String] = apply(url.openStream)
}

trait AutomaticResourceManagement {
  def withCloseable[T <: { def close() }](closeable: T) = new {
    def apply[S](body: T => S) = try {
      body(closeable)
    } finally {
      closeable.close()
    }
  }
}

