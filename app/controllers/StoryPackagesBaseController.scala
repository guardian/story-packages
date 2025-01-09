package controllers

import com.gu.pandomainauth.{PanDomainAuthSettingsRefresher, S3BucketLoader}
import conf.ApplicationConfiguration
import play.api.libs.ws.WSClient
import play.api.mvc.{BaseController, ControllerComponents}
import story_packages.services.Logging

abstract class StoryPackagesBaseController(
  val config: ApplicationConfiguration,
  components: ControllerComponents,
  val wsClient: WSClient
) extends BaseController with Logging {
  final override val controllerComponents: ControllerComponents = components

  lazy val panDomainSettings: PanDomainAuthSettingsRefresher =
    PanDomainAuthSettingsRefresher(
      domain = config.pandomain.domain,
      system = config.pandomain.service,
      S3BucketLoader.forAwsSdkV1(
      config.aws.s3Client.get,
        "pan-domain-auth-settings"
      )
    )
}
