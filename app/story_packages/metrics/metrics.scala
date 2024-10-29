package story_packages.metrics

import java.io.File
import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}
import java.util.concurrent.atomic.AtomicLong
import org.apache.pekko.actor.Scheduler
import com.amazonaws.services.cloudwatch.model.{Dimension, StandardUnit}
import play.api.Logger
import story_packages.services.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.collection.mutable.Buffer

object SystemMetrics {

  class GcRateMetric(bean: GarbageCollectorMXBean) {
    private val lastGcCount = new AtomicLong(0)
    private val lastGcTime = new AtomicLong(0)

    lazy val name = bean.getName.replace(" ", "_")

    def gcCount: Double = {
      val totalGcCount = bean.getCollectionCount
      (totalGcCount - lastGcCount.getAndSet(totalGcCount)).toDouble
    }

    def gcTime: Double = {
      val totalGcTime = bean.getCollectionTime
      (totalGcTime - lastGcTime.getAndSet(totalGcTime)).toDouble
    }
  }


  lazy val garbageCollectors: Seq[GcRateMetric] = ManagementFactory.getGarbageCollectorMXBeans.asScala.map(new GcRateMetric(_)).toSeq


  // divide by 1048576 to convert bytes to MB

  object MaxHeapMemoryMetric extends GaugeMetric("max-heap-memory", "Max heap memory (MB)",
    () => ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getMax / 1048576
  )

  object UsedHeapMemoryMetric extends GaugeMetric("used-heap-memory", "Used heap memory (MB)",
    () => ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed / 1048576
  )

  object MaxNonHeapMemoryMetric extends GaugeMetric("max-non-heap-memory", "Max non heap memory (MB)",
    () => ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage.getMax / 1048576
  )

  object UsedNonHeapMemoryMetric extends GaugeMetric("used-non-heap-memory", "Used non heap memory (MB)",
    () => ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage.getUsed / 1048576
  )

  object AvailableProcessorsMetric extends GaugeMetric("available-processors", "Available processors",
    () => ManagementFactory.getOperatingSystemMXBean.getAvailableProcessors
  )

  object FreeDiskSpaceMetric extends GaugeMetric("free-disk-space", "Free disk space (MB)",
    () => new File("/").getUsableSpace / 1048576
  )

  object TotalDiskSpaceMetric extends GaugeMetric("total-disk-space", "Total disk space (MB)",
    () => new File("/").getTotalSpace / 1048576
  )

  // yeah, casting to com.sun.. ain't too pretty
  object TotalPhysicalMemoryMetric extends GaugeMetric("total-physical-memory", "Total physical memory",
    () => ManagementFactory.getOperatingSystemMXBean match {
      case b: com.sun.management.OperatingSystemMXBean => b.getTotalPhysicalMemorySize
      case _ => -1
    }
  )

  object FreePhysicalMemoryMetric extends GaugeMetric("free-physical-memory", "Free physical memory",
    () => ManagementFactory.getOperatingSystemMXBean match {
      case b: com.sun.management.OperatingSystemMXBean => b.getFreePhysicalMemorySize
      case _ => -1
    }
  )

  object OpenFileDescriptorsMetric extends GaugeMetric("open-file-descriptors", "Open file descriptors",
    () => ManagementFactory.getOperatingSystemMXBean match {
      case b: com.sun.management.UnixOperatingSystemMXBean => b.getOpenFileDescriptorCount
      case _ => -1
    }
  )

  object MaxFileDescriptorsMetric extends GaugeMetric("max-file-descriptors", "Max file descriptors",
    () => ManagementFactory.getOperatingSystemMXBean match {
      case b: com.sun.management.UnixOperatingSystemMXBean => b.getMaxFileDescriptorCount
      case _ => -1
    }
  )
}

object S3Metrics {
  object S3ClientExceptionsMetric extends CountMetric(
    "s3-client-exceptions",
    "Number of times the AWS S3 client has thrown an Exception"
  )
}

object FaciaToolMetrics {
  object ApiUsageCount extends CountMetric(
    "api-usage",
    "Number of requests to the Facia API from clients (The tool)"
  )

  object ProxyCount extends CountMetric(
    "api-proxy-usage",
    "Number of requests to the Facia proxy endpoints (Ophan and Content API) from clients"
  )
}

object StoryPackagesMetrics {
  object QueryCount extends CountMetric(
    "dynamo-query",
    "Number of queries to dynamo from story packages"
  )

  object ScanCount extends CountMetric(
    "dynamo-scan",
    "Number of database scans from story packages"
  )

  object DeleteCount extends CountMetric(
    "dynamo-delete",
    "Number of database deletions from story packages"
  )

  object ErrorCount extends CountMetric(
    "dynamo-error",
    "Number of database errors from story packages"
  )

  object UpdateCount extends CountMetric(
    "dynamo-update",
    "Number of database updates from story packages"
  )
}

object ReindexMetrics {
  object QueryCount extends CountMetric(
    "reindex-query",
    "Number of queries to dynamo from story packages reindex"
  )

  object ScanCount extends CountMetric(
    "reindex-scan",
    "Number of database scans from story packages reindex"
  )

  object DeleteCount extends CountMetric(
    "reindex-delete",
    "Number of database deletions from story packages reindex"
  )

  object ErrorCount extends CountMetric(
    "reindex-error",
    "Number of database errors from story packages reindex"
  )

  object UpdateCount extends CountMetric(
    "reindex-update",
    "Number of database updates from story packages reindex"
  )
}

class CloudWatchApplicationMetrics(appName: String, stage: String, cloudWatch: CloudWatch, scheduler: Scheduler, isDev: Boolean) extends Logging {
  val applicationMetricsNamespace: String = "Application"
  val applicationDimension: Dimension = new Dimension().withName("ApplicationName").withValue(appName)
  def applicationMetrics: List[FrontendMetric] = List(
    StoryPackagesMetrics.QueryCount,
    StoryPackagesMetrics.ScanCount,
    StoryPackagesMetrics.DeleteCount,
    StoryPackagesMetrics.ErrorCount,
    StoryPackagesMetrics.UpdateCount,
    ReindexMetrics.QueryCount,
    ReindexMetrics.ScanCount,
    ReindexMetrics.DeleteCount,
    ReindexMetrics.ErrorCount,
    ReindexMetrics.UpdateCount,
    S3Metrics.S3ClientExceptionsMetric,
    FaciaToolMetrics.ApiUsageCount,
    FaciaToolMetrics.ProxyCount
  )

  def systemMetrics: List[FrontendMetric] = List(SystemMetrics.MaxHeapMemoryMetric,
    SystemMetrics.UsedHeapMemoryMetric, SystemMetrics.TotalPhysicalMemoryMetric, SystemMetrics.FreePhysicalMemoryMetric,
    SystemMetrics.AvailableProcessorsMetric, SystemMetrics.FreeDiskSpaceMetric,
    SystemMetrics.TotalDiskSpaceMetric, SystemMetrics.MaxFileDescriptorsMetric,
    SystemMetrics.OpenFileDescriptorsMetric) ++ SystemMetrics.garbageCollectors.flatMap{ gc => List(
      GaugeMetric(s"${gc.name}-gc-count-per-min" , "Used heap memory (MB)",
        () => gc.gcCount.toLong,
        StandardUnit.Count
      ),
      GaugeMetric(s"${gc.name}-gc-time-per-min", "Used heap memory (MB)",
        () => gc.gcTime.toLong,
        StandardUnit.Count
      )
    )}

  private def report(): Unit = {
    val allMetrics: List[FrontendMetric] = this.systemMetrics ::: this.applicationMetrics
    if (!isDev) {
      val stageDimension = new Dimension().withName("Stage").withValue(stage)
      cloudWatch.putMetricsWithStage(allMetrics, applicationDimension, stageDimension)
    }
  }

  Logger.info("Starting cloudwatch metrics")
  scheduler.scheduleWithFixedDelay(initialDelay = 1.seconds, delay = 1.minute) { () => report() }
}
