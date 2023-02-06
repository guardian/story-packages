package story_packages.metrics

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.cloudwatch.{AmazonCloudWatchAsync, AmazonCloudWatchAsyncClientBuilder}
import com.amazonaws.services.cloudwatch.model._
import play.api.Logger
import story_packages.services.AwsEndpoints
import conf.ApplicationConfiguration

import scala.collection.JavaConverters._

class CloudWatch(config: ApplicationConfiguration, awsEndpoints: AwsEndpoints) {

  lazy val cloudwatch: Option[AmazonCloudWatchAsync] = config.aws.credentials.map { credentials =>
    AmazonCloudWatchAsyncClientBuilder.standard
      .withEndpointConfiguration(new EndpointConfiguration(awsEndpoints.monitoring, config.aws.region))
      .withCredentials(credentials)
      .build
  }

  trait LoggingAsyncHandler extends AsyncHandler[PutMetricDataRequest, PutMetricDataResult] {
    def onError(exception: Exception)
    {
      Logger.info(s"CloudWatch PutMetricDataRequest error: ${exception.getMessage}}")
    }
    def onSuccess(request: PutMetricDataRequest, result: PutMetricDataResult )
    {
      Logger.info("CloudWatch PutMetricDataRequest - success")
    }
  }

  case class AsyncHandlerForMetric(frontendStatisticSets: List[FrontendStatisticSet]) extends LoggingAsyncHandler {
    override def onError(exception: Exception) = {
      Logger.warn(s"Failed to put ${frontendStatisticSets.size} metrics: $exception")
      Logger.warn(s"Failed to put ${frontendStatisticSets.map(_.metric.name).mkString(",")}")
      frontendStatisticSets.foreach { _.reset() }
      super.onError(exception)
    }
    override def onSuccess(request: PutMetricDataRequest, result: PutMetricDataResult ) = {
      Logger.info(s"Successfully put ${frontendStatisticSets.size} metrics")
      Logger.info(s"Successfully put ${frontendStatisticSets.map(_.metric.name).mkString(",")}")

      super.onSuccess(request, result)
    }
  }

  def putMetricsWithStage(metrics: List[FrontendMetric], applicationDimension: Dimension, stageDimension: Dimension): Unit =
    putMetrics("Application", metrics, List(stageDimension, applicationDimension))

  def putMetrics(metricNamespace: String, metrics: List[FrontendMetric], dimensions: List[Dimension]): Unit = {
    for {
      metricGroup <- metrics.filterNot(_.isEmpty).grouped(20)
    } {
      val metricsAsStatistics: List[FrontendStatisticSet] =
        metricGroup.map( metric => FrontendStatisticSet(metric, metric.getAndResetDataPoints))
      val request = new PutMetricDataRequest()
        .withNamespace(metricNamespace)
        .withMetricData {
          val metricData = for(metricStatistic <- metricsAsStatistics) yield {
            new MetricDatum()
              .withStatisticValues(frontendMetricToStatisticSet(metricStatistic))
              .withUnit(metricStatistic.metric.metricUnit)
              .withMetricName(metricStatistic.metric.name)
              .withDimensions(dimensions.asJava)
          }

          metricData.asJava
        }
      cloudwatch.foreach(_.putMetricDataAsync(request, AsyncHandlerForMetric(metricsAsStatistics)))
    }
  }

  private def frontendMetricToStatisticSet(metricStatistics: FrontendStatisticSet): StatisticSet =
    new StatisticSet()
      .withMaximum(metricStatistics.maximum)
      .withMinimum(metricStatistics.minimum)
      .withSampleCount(metricStatistics.sampleCount)
      .withSum(metricStatistics.sum)

}

