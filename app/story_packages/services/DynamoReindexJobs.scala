package story_packages.services

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBClientBuilder}
import com.amazonaws.services.dynamodbv2.document.spec.{DeleteItemSpec, QuerySpec, ScanSpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.document.{AttributeUpdate, DynamoDB, Item}
import story_packages.metrics.ReindexMetrics
import conf.ApplicationConfiguration
import story_packages.updates._

import scala.collection.JavaConverters._

class DynamoReindexJobs(config: ApplicationConfiguration) extends Logging {
  private lazy val client =
    AmazonDynamoDBClientBuilder.standard
      .withCredentials(config.aws.mandatoryCredentials)
      .withEndpointConfiguration(new EndpointConfiguration(config.aws.endpoints.dynamoDB, config.aws.region))
      .build

  private lazy val table = new DynamoDB(client).getTable(config.reindex.progressTable)

  private def asReindexProgress(item: Item): ReindexProgress = {
    ReindexProgress(
      status = item.getString("reindexStatus"),
      documentsIndexed = item.getInt("documentsIndexed"),
      documentsExpected = item.getInt("documentsExpected")
    )
  }

  def hasJobInProgress(isHidden: Boolean): Boolean = {
    jobInProgress(isHidden).nonEmpty
  }

  def jobInProgress(isHidden: Boolean): Option[ReindexProgress] = {
    val values = new ValueMap()
      .withString(":status", "in progress")
      .withBoolean(":hidden", isHidden)

    val queryExpression = new QuerySpec()
      .withKeyConditionExpression("reindexStatus = :status")
      .withFilterExpression("isHidden = :hidden")
      .withValueMap(values)
      .withMaxResultSize(1)

    val job = table.query(queryExpression).asScala.toList.map(asReindexProgress).headOption
    ReindexMetrics.QueryCount.increment()
    job
  }

  def createJob(reindexPage: ReindexPage): RunningJob = {
    val job = RunningJob(reindexPage)
    val item = new Item()
      .withPrimaryKey("reindexStatus", job.status.label, "startTime", job.startTime.toString)
      .withInt("documentsIndexed", job.documentsIndexed)
      .withInt("documentsExpected", job.documentsExpected)
      .withBoolean("isHidden", job.isHidden)

    Logger.info(s"Creating reindex job at ${job.startTime}")
    table.putItem(item)
    ReindexMetrics.UpdateCount.increment()
    job
  }

  def markProgressUpdate(previousRunningJob: RunningJob, processedResults: Int) = {
    val job = previousRunningJob.copy(documentsIndexed = processedResults)
    val updateSpec = new UpdateItemSpec()
      .withPrimaryKey("reindexStatus", job.status.label, "startTime", job.startTime.toString)
      .addAttributeUpdate(new AttributeUpdate("documentsIndexed").put(job.documentsIndexed))
      .addAttributeUpdate(new AttributeUpdate("documentsExpected").put(job.documentsExpected))
      .addAttributeUpdate(new AttributeUpdate("isHidden").put(job.isHidden))

    Logger.info(s"Marking reindex progress update at ${job.startTime}")
    table.updateItem(updateSpec)
    ReindexMetrics.UpdateCount.increment()
  }

  def markCompleteJob(previousRunningJob: RunningJob, lastProcessedResult: Int) = {
    val job = previousRunningJob.copy(
      status = Completed(),
      documentsIndexed = lastProcessedResult
    )

    Logger.info(s"Marking reindex complete at ${previousRunningJob.startTime}")
    val item = new Item()
      .withPrimaryKey("reindexStatus", job.status.label, "startTime", job.startTime.toString)
      .withInt("documentsIndexed", job.documentsIndexed)
      .withInt("documentsExpected", job.documentsExpected)
      .withBoolean("isHidden", job.isHidden)
    table.putItem(item)
    ReindexMetrics.UpdateCount.increment()

    table.deleteItem(new DeleteItemSpec()
      .withPrimaryKey("reindexStatus", previousRunningJob.status.label, "startTime", previousRunningJob.startTime.toString)
    )
    ReindexMetrics.DeleteCount.increment()
  }

  def markFailedJob(previousRunningJob: RunningJob) = {
    val job = previousRunningJob.copy(
      status = Failed()
    )

    Logger.info(s"Marking reindex failed at ${previousRunningJob.startTime}")
    val item = new Item()
      .withPrimaryKey("reindexStatus", job.status.label, "startTime", job.startTime.toString)
      .withInt("documentsIndexed", job.documentsIndexed)
      .withInt("documentsExpected", job.documentsExpected)
      .withBoolean("isHidden", job.isHidden)
    table.putItem(item)
    ReindexMetrics.UpdateCount.increment()

    table.deleteItem(new DeleteItemSpec()
      .withPrimaryKey("reindexStatus", previousRunningJob.status.label, "startTime", previousRunningJob.startTime.toString)
    )
    ReindexMetrics.UpdateCount.increment()
  }

  def getLastStartedJob(isHidden: Boolean): Option[ReindexProgress] = {
    val values = new ValueMap()
      .withString(":status", "in progress")
      .withBoolean(":hidden", isHidden)

    Logger.info(s"Scanning reindex jobs for last started job with isHidden $isHidden")

    val scanRequest = new ScanSpec()
      .withFilterExpression("isHidden = :hidden and not reindexStatus = :status")
      .withValueMap(values)

    import SortItemsByLastStartTime._
    val progress = table.scan(scanRequest).asScala.toList.sorted.map(asReindexProgress).headOption
    ReindexMetrics.ScanCount.increment()
    progress
  }
}
