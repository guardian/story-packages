import logging.LogStashConfig
import metrics.CloudWatchApplicationMetrics
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, Logger}

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

    components.application
  }
}
