package conf

import java.io.{File, FileInputStream, InputStream}
import java.net.URL

import org.apache.commons.io.IOUtils
import play.api.Play.current
import play.api.{Configuration => PlayConfiguration, Logger}

import scala.collection.JavaConversions._
import scala.io.Source
import scala.language.reflectiveCalls

class BadConfigurationException(msg: String) extends RuntimeException(msg)

object Configuration {
  private val playConfiguration: PlayConfiguration = play.api.Play.configuration

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

  object contentApi {
    case class Auth(user: String, password: String)

    val contentApiLiveHost: String = getMandatoryString("content.api.host")
    def contentApiDraftHost: String = getMandatoryString("content.api.draft.host")

    lazy val key: Option[String] = getString("content.api.key")
    lazy val timeout: Int = 2000

    lazy val previewAuth: Option[Auth] = for {
      user <- getString("content.api.preview.user")
      password <- getString("content.api.preview.password")
    } yield Auth(user, password)
  }

  object ajax {
    lazy val corsOrigins: Seq[String] = getString("ajax.cors.origin").map(_.split(",")
    .map(_.trim).toSeq).getOrElse(Nil)
  }

  object aws {
    lazy val region = getMandatoryString("aws.region")
    lazy val bucket = getMandatoryString("aws.bucket")
    lazy val crossAccount = getMandatoryBoolean("aws.crossAccount")
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

  object pandomain {
    lazy val host = getMandatoryString("pandomain.host")
    lazy val domain = getMandatoryString("pandomain.domain")
    lazy val service = getMandatoryString("pandomain.service")
    lazy val roleArn = getMandatoryString("pandomain.roleArn")
  }

  object permission {
    lazy val cache = getMandatoryString("permissions.cache")
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

