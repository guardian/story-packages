package controllers

import java.net.{URI, URLEncoder}
import story_packages.auth.PanDomainAuthActions
import com.gu.contentapi.client.{IAMEncoder, IAMSigner}
import story_packages.metrics.FaciaToolMetrics
import story_packages.model.Cached
import play.api.libs.ws.WSClient
import play.api.mvc._
import conf.ApplicationConfiguration
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import story_packages.switchboard.SwitchManager
import story_packages.util.ContentUpgrade.rewriteBody

import scala.concurrent.ExecutionContext.Implicits.global


class FaciaContentApiProxy(config: ApplicationConfiguration, components: ControllerComponents, wsClient: WSClient) extends StoryPackagesBaseController(config, components, wsClient) with PanDomainAuthActions {

  implicit class string2encodings(s: String) {
    lazy val urlEncoded: String = URLEncoder.encode(s, "utf-8")
  }

  private val previewSigner = {
    val region = Region.of(config.awsV2.region)
    val stsClient = StsClient.builder.region(region).build()
    val assumeRoleRequest = AssumeRoleRequest.builder.roleSessionName("capi").roleArn(config.contentApi.previewRole).build

    val capiPreviewCredentials = AwsCredentialsProviderChain.of(
      ProfileCredentialsProvider.create("capi"),
      StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient).refreshRequest(assumeRoleRequest).build()
    )

    new IAMSigner(
      credentialsProvider = capiPreviewCredentials,
      awsRegion = config.awsV2.region
    )
  }

  private def getPreviewHeaders(url: String): Seq[(String,String)] =
    previewSigner.addIAMHeaders(headers = Map.empty, URI.create(url)).toSeq

  def capiPreview(path: String): Action[AnyContent] = APIAuthAction.async { request =>
    FaciaToolMetrics.ProxyCount.increment()
    val queryString = IAMEncoder.encodeParams(request.queryString)

    val contentApiHost: String = if (SwitchManager.getStatus("facia-tool-draft-content"))
      config.contentApi.contentApiDraftHost
    else
      config.contentApi.contentApiLiveHost

    val url = s"$contentApiHost/$path?$queryString${config.contentApi.key.map(key => s"&api-key=$key").getOrElse("")}"

    Logger.info(s"Proxying preview API query to: $url")

    wsClient.url(url).withHttpHeaders(getPreviewHeaders(url): _*).get().map { response =>
      Cached(60) {
        Ok(rewriteBody(response.body)).as("application/javascript")
      }
    }
  }

  def capiLive(path: String): Action[AnyContent] = APIAuthAction.async { request =>
    FaciaToolMetrics.ProxyCount.increment()
    val queryString = request.queryString.filter(_._2.exists(_.nonEmpty)).map { p =>
       "%s=%s".format(p._1, p._2.head.urlEncoded)
    }.mkString("&")

    val contentApiHost = config.contentApi.contentApiLiveHost

    val url = s"$contentApiHost/$path?$queryString${config.contentApi.key.map(key => s"&api-key=$key").getOrElse("")}"

    Logger.info(s"Proxying live API query to: $url")

    wsClient.url(url).get().map { response =>
      Cached(60) {
        Ok(rewriteBody(response.body)).as("application/javascript")
      }
    }
  }

  def http(url: String): Action[AnyContent] = APIAuthAction.async { request =>
    FaciaToolMetrics.ProxyCount.increment()
    Logger.info(s"Proxying http request to: $url")

    wsClient.url(url).get().map { response =>
      Cached(60) {
        Ok(response.body).as("text/html")
      }
    }
  }

  def json(url: String): Action[AnyContent] = APIAuthAction.async { request =>
    FaciaToolMetrics.ProxyCount.increment()
    Logger.info(s"Proxying json request to: $url")

    wsClient.url(url).withHttpHeaders(getPreviewHeaders(url): _*).get().map { response =>
      Cached(60) {
        Ok(rewriteBody(response.body)).as("application/json")
      }
    }
  }

  def ophan(path: String): Action[AnyContent] = APIAuthAction.async { request =>
    FaciaToolMetrics.ProxyCount.increment()
    val paths = request.queryString.get("path").map(_.mkString("path=", "&path=", "")).getOrElse("")
    val queryString = request.queryString.filterNot(_._1 == "path").filter(_._2.exists(_.nonEmpty)).map { p =>
      "%s=%s".format(p._1, p._2.head.urlEncoded)
    }.mkString("&")
    val ophanApiHost = config.ophanApi.host.get
    val ophanKey = config.ophanApi.key.map(key => s"&api-key=$key").getOrElse("")

    val url = s"$ophanApiHost/$path?$queryString&$paths&$ophanKey"

    Logger.info(s"Proxying ophan request to: $url")

    wsClient.url(url).get().map { response =>
      Cached(60) {
        Ok(response.body).as("application/json")
      }
    }
  }
}
