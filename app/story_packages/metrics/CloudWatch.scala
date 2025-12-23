package story_packages.metrics

import conf.ApplicationConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, PutMetricDataResponse, StatisticSet}
import story_packages.services.Logging

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.{Failure, Success}

class CloudWatch(config: ApplicationConfiguration) extends Logging {

  lazy val cloudwatch: Option[CloudWatchAsyncClient] = config.awsV2.credentials.map { credentials =>
    CloudWatchAsyncClient.builder()
      .credentialsProvider(credentials)
      .region(Region.of(config.awsV2.region))
      .endpointOverride(config.awsV2.endpoints.monitoring)
      .build
  }

  case class AsyncHandlerForMetric(frontendStatisticSets: List[FrontendStatisticSet]) {
    def onError(exception: Throwable): Unit = {
      Logger.warn(s"Failed to put ${frontendStatisticSets.size} metrics: $exception")
      Logger.warn(s"Failed to put ${frontendStatisticSets.map(_.metric.name).mkString(",")}")
      frontendStatisticSets.foreach { _.reset() }
      Logger.info(s"CloudWatch PutMetricDataRequest error: ${exception.getMessage}}")
    }
    def onSuccess(request: PutMetricDataRequest, result: PutMetricDataResponse ): Unit = {
      Logger.info(s"Successfully put ${frontendStatisticSets.size} metrics")
      Logger.info(s"Successfully put ${frontendStatisticSets.map(_.metric.name).mkString(",")}")
      Logger.info("CloudWatch PutMetricDataRequest - success")
    }
  }

  def putMetricsWithStage(metrics: List[FrontendMetric], applicationDimension: Dimension, stageDimension: Dimension)(implicit ec:ExecutionContext): Unit =
    putMetrics("Application", metrics, List(stageDimension, applicationDimension))

  def putMetrics(metricNamespace: String, metrics: List[FrontendMetric], dimensions: List[Dimension])(implicit ec:ExecutionContext): Unit = {
    for {
      metricGroup <- metrics.filterNot(_.isEmpty).grouped(20)
    } {
      val metricsAsStatistics: List[FrontendStatisticSet] =
        metricGroup.map( metric => FrontendStatisticSet(metric, metric.getAndResetDataPoints))
      val request = PutMetricDataRequest.builder()
        .namespace(metricNamespace)
        .metricData {
          val metricData = for(metricStatistic <- metricsAsStatistics) yield {
            MetricDatum.builder()
              .statisticValues(frontendMetricToStatisticSet(metricStatistic))
              .unit(metricStatistic.metric.metricUnit)
              .metricName(metricStatistic.metric.name)
              .dimensions(dimensions.asJava)
              .build()
          }
          metricData.asJava
        }
        .build()
      val handler = AsyncHandlerForMetric(metricsAsStatistics)

      cloudwatch.foreach { client =>
        client
          .putMetricData(request)
          .asScala
          .onComplete {
            case Success(response) => handler.onSuccess(request, response)
            case Failure(e) => handler.onError(e)
          }
      }
    }
  }

  private def frontendMetricToStatisticSet(metricStatistics: FrontendStatisticSet): StatisticSet =
    StatisticSet.builder()
      .maximum(metricStatistics.maximum)
      .minimum(metricStatistics.minimum)
      .sampleCount(metricStatistics.sampleCount)
      .sum(metricStatistics.sum)
      .build()

}

