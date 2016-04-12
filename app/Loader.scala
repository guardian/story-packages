import logging.LogStashConfig
import metrics.CloudWatchApplicationMetrics
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, Logger}
import switchboard.{SwitchboardConfiguration, Lifecycle => SwitchboardLifecycle}

class Loader extends ApplicationLoader {
  override def load(context: Context): Application = {
    Logger.configure(context.environment)

    val components = new AppComponents(context)

    new CloudWatchApplicationMetrics(
      components.config.environment.applicationName,
      components.config.environment.stage,
      components.cloudwatch,
      components.actorSystem.scheduler,
      components.isDev
    )
    new LogStashConfig(components.config)
    new SwitchboardLifecycle(SwitchboardConfiguration(
      objectKey = components.config.switchBoard.objectKey,
      bucket = components.config.switchBoard.bucket,
      credentials = components.config.aws.mandatoryCredentials,
      endpoint = components.awsEndpoints.s3
    ), components.actorSystem.scheduler)

    components.application
  }
}
