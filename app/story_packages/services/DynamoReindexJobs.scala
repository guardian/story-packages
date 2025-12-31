package story_packages.services

import story_packages.metrics.ReindexMetrics
import conf.ApplicationConfiguration
import software.amazon.awssdk.enhanced.dynamodb.{AttributeConverterProvider, AttributeValueType, DynamoDbEnhancedClient, Expression, Key, TableMetadata, TableSchema}
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument
import software.amazon.awssdk.enhanced.dynamodb.model.{QueryConditional, QueryEnhancedRequest, ScanEnhancedRequest}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import story_packages.services.DynamoReindexJobs.jobAsItem
import story_packages.updates._

import scala.jdk.CollectionConverters._

class DynamoReindexJobs(config: ApplicationConfiguration) extends Logging {
  private lazy val client =
    DynamoDbClient.builder()
      .credentialsProvider(config.awsV2.mandatoryCredentials)
      .region(Region.of(config.awsV2.region))
      .endpointOverride(config.awsV2.endpoints.dynamoDB)
      .build()

  private lazy val enhancedClient = DynamoDbEnhancedClient.builder()
    .dynamoDbClient(client)
    .build();

  private lazy val table = enhancedClient.table(config.storage.configTable,
    TableSchema.documentSchemaBuilder()
      .addIndexPartitionKey(TableMetadata.primaryIndexName(), "reindexStatus", AttributeValueType.S)
      .addIndexSortKey(TableMetadata.primaryIndexName(), "startTime", AttributeValueType.S)
      .attributeConverterProviders(AttributeConverterProvider.defaultProvider())
      .build())

  private def asReindexProgress(item: EnhancedDocument): ReindexProgress = {
    ReindexProgress(
      status = item.getString("reindexStatus"),
      documentsIndexed = item.getNumber("documentsIndexed").intValue(),
      documentsExpected = item.getNumber("documentsExpected").intValue()
    )
  }

  def hasJobInProgress(isHidden: Boolean): Boolean = {
    jobInProgress(isHidden).nonEmpty
  }

  def jobInProgress(isHidden: Boolean): Option[ReindexProgress] = {

    val statusInProgress = Key.builder().partitionValue("in progress").build()

    val isHiddenExpression = Expression.builder()
      .expression("isHidden = :is_hidden")
      .putExpressionValue(":is_hidden", AttributeValue.builder().bool(isHidden).build())
      .build();

    val queryExpression = QueryEnhancedRequest.builder()
      .queryConditional(QueryConditional.keyEqualTo(statusInProgress))
      .filterExpression(isHiddenExpression)
      .build()

    val job = table.query(queryExpression).items().asScala.toList.map(asReindexProgress).headOption
    ReindexMetrics.QueryCount.increment()
    job
  }

  def createJob(reindexPage: ReindexPage): RunningJob = {
    val job = RunningJob(reindexPage)
    val item = jobAsItem(job)

    Logger.info(s"Creating reindex job at ${job.startTime}")
    table.putItem(item)
    ReindexMetrics.UpdateCount.increment()
    job
  }

  def markProgressUpdate(previousRunningJob: RunningJob, processedResults: Int) = {
    val job = previousRunningJob.copy(documentsIndexed = processedResults)
    val updatedItem = jobAsItem(job)

    Logger.info(s"Marking reindex progress update at ${job.startTime}")
    table.updateItem(updatedItem)
    ReindexMetrics.UpdateCount.increment()
  }

  def markCompleteJob(previousRunningJob: RunningJob, lastProcessedResult: Int) = {
    val job = previousRunningJob.copy(
      status = Completed(),
      documentsIndexed = lastProcessedResult
    )

    Logger.info(s"Marking reindex complete at ${previousRunningJob.startTime}")
    table.putItem(jobAsItem(job))
    ReindexMetrics.UpdateCount.increment()

    table.deleteItem(jobAsItem(previousRunningJob))
    ReindexMetrics.DeleteCount.increment()
  }

  def markFailedJob(previousRunningJob: RunningJob) = {
    val job = previousRunningJob.copy(
      status = Failed()
    )

    Logger.info(s"Marking reindex failed at ${previousRunningJob.startTime}")
    table.putItem(jobAsItem(job))
    ReindexMetrics.UpdateCount.increment()

    table.deleteItem(jobAsItem(previousRunningJob))
    ReindexMetrics.UpdateCount.increment()
  }

  def getLastStartedJob(isHidden: Boolean): Option[ReindexProgress] = {
    Logger.info(s"Scanning reindex jobs for last started job with isHidden $isHidden")

    val expression = Expression.builder()
      .expression("isHidden = :hidden and not reindexStatus = :status")
      .putExpressionValue(":hidden", AttributeValue.builder().bool(isHidden).build())
      .putExpressionValue(":status", AttributeValue.builder().s("in progress").build())
      .build();

    val scanRequest = ScanEnhancedRequest.builder()
      .filterExpression(expression)
      .build()

    import SortItemsByLastStartTime._
    val progress = table.scan(scanRequest).items().asScala.toList.sorted.map(asReindexProgress).headOption
    ReindexMetrics.ScanCount.increment()
    progress
  }
}

object DynamoReindexJobs {
  def jobAsItem(job: RunningJob): EnhancedDocument = EnhancedDocument.builder()
      .attributeConverterProviders(AttributeConverterProvider.defaultProvider())
      .putString("reindexStatus", job.status.label)
      .putString("startTime", job.startTime.toString)
      .putNumber("documentsIndexed", job.documentsIndexed)
      .putNumber("documentsExpected", job.documentsExpected)
      .putBoolean("isHidden", job.isHidden)
      .build()
}
