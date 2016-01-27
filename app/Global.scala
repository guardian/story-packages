import conf.Gzipper
import logging.LogStashConfig
import metrics.{CloudWatchApplicationMetrics, FaciaToolMetrics, FrontendMetric, S3Metrics, StoryPackagesMetrics}
import play.api._
import play.api.mvc.WithFilters
import switchboard.{Lifecycle => NewSwitchboardLifecycle}

object Global extends WithFilters(Gzipper)
  with GlobalSettings
  with CloudWatchApplicationMetrics
  with NewSwitchboardLifecycle
  with LogStashConfig {

  override lazy val applicationName = "story-packages"

  override def applicationMetrics: List[FrontendMetric] = super.applicationMetrics ::: List(
    FaciaToolMetrics.ApiUsageCount,
    FaciaToolMetrics.ProxyCount,
    S3Metrics.S3ClientExceptionsMetric,
    StoryPackagesMetrics.QueryCount,
    StoryPackagesMetrics.ScanCount,
    StoryPackagesMetrics.DeleteCount,
    StoryPackagesMetrics.ErrorCount,
    StoryPackagesMetrics.UpdateCount
  )

}
