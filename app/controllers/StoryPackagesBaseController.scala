package controllers

import com.gu.pandomainauth.PanDomainAuthSettingsRefresher
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
    new PanDomainAuthSettingsRefresher(
      config.pandomain.domain,
      config.pandomain.service,
      config.pandomain.bucketName,
      config.pandomain.settingsFileKey,
      config.aws.s3Client.get
    )
}
