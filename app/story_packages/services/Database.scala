package story_packages.services

import com.gu.pandomainauth.model.User
import story_packages.metrics.StoryPackagesMetrics
import story_packages.model.StoryPackage
import org.joda.time.{DateTime, DateTimeZone}
import conf.ApplicationConfiguration
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument
import software.amazon.awssdk.enhanced.dynamodb.model.{PageIterable, ScanEnhancedRequest}
import software.amazon.awssdk.enhanced.dynamodb.{AttributeConverterProvider, AttributeValueType, DynamoDbEnhancedClient, Expression, Key, TableMetadata, TableSchema}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import story_packages.updates.ReindexPage
import story_packages.util.Identity._

import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class Database(config: ApplicationConfiguration) extends Logging {
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
      .addIndexPartitionKey(TableMetadata.primaryIndexName(),"id", AttributeValueType.S)
      .attributeConverterProviders(AttributeConverterProvider.defaultProvider())
      .build())

  def createStoryPackage(story: StoryPackage, user: User): Future[StoryPackage] = {
    val errorMessage = "Exception in dynamoDB putItem while creating a story package"
    WithExceptionHandling(errorMessage, {
      val item = DynamoToScala.convertToItem(story.copy(
        lastModify = Some(new DateTime().withZone(DateTimeZone.UTC).toString),
        created = Some(new DateTime().withZone(DateTimeZone.UTC).toString),
        lastModifyBy = Some(user.email),
        lastModifyByName = Some(user.fullName),
        createdBy = Some(user.email)
      ))

      table.putItem(item)
      val newStoryPackage = DynamoToScala.convertToStoryPackage(item)
      Logger.info(s"New story package created with id:${newStoryPackage.id} -> $newStoryPackage")
      newStoryPackage
    })
  }

  private def getItem(id: String): EnhancedDocument = {
    table.getItem(Key.builder().partitionValue(id).build())
  }

  def getPackage(id: String): Future[StoryPackage] = {
    val errorMessage = s"Unable to find story package with id $id"
    WithExceptionHandling(errorMessage, {
      val item = getItem(id)
      StoryPackagesMetrics.QueryCount.increment()
      DynamoToScala.convertToStoryPackage(item)
    })
  }

  def scanAllPackages(isHidden: Boolean = false): Future[ReindexPage] = {
      val errorMessage = s"Exception in fetching all packages"
      WithExceptionHandling(errorMessage, {
        val expression = Expression.builder()
          .expression("isHidden = :is_hidden")
          .putExpressionValue(":is_hidden", AttributeValue.builder().bool(isHidden).build())
          .build();

        val scanRequest = ScanEnhancedRequest.builder()
          .filterExpression(expression)
          .attributesToProject("id", "deleted", "packageName")
          .build()

        val outcome = table.scan(scanRequest)
        StoryPackagesMetrics.ScanCount.increment()

        val listIds = DynamoToScala.convertToListOfStoryPackages(outcome)
        val totalCount = listIds.size

        ReindexPage(
          totalCount = totalCount,
          list = listIds,
          next = None,
          isHidden = isHidden
        )
      })
  }

  def removePackage(id: String): Future[StoryPackage] = {
    val errorMessage = s"Unable to delete story package $id"
    WithExceptionHandling(errorMessage, {

      val updatedItem = getItem(id).toBuilder
        .putBoolean("deleted", true)
        .build()

      val outcome = table.updateItem(updatedItem)

      StoryPackagesMetrics.DeleteCount.increment()
      DynamoToScala.convertToStoryPackage(outcome)
    })
  }

  def touchPackage(id: String, user: User, newName: Option[String] = None): Future[StoryPackage] = {
    val errorMessage = s"Unable to update modification metadata for story package $id"
    WithExceptionHandling(errorMessage, {
      val modifyDate = new DateTime().withZone(DateTimeZone.UTC)

      import DynamoToScala._ // for putOptString

      val updatedItem = getItem(id).toBuilder
        .putString("lastModify", modifyDate.toString)
        .putString("lastModifyBy", user.email)
        .putString("lastModifyByName", user.fullName)
        .putOptString("packageName", newName)
        .build()

      val outcome = table.updateItem(updatedItem)
      StoryPackagesMetrics.UpdateCount.increment()
      DynamoToScala.convertToStoryPackage(outcome)
    })
  }
}

private object WithExceptionHandling extends Logging {
  def apply[T](errorMessage: String, block: => T): Future[T] = {
    Try(block) match {
      case Success(result) =>
        Future.successful(result)
      case Failure(t: Throwable) =>
        Logger.error(errorMessage, t)
        StoryPackagesMetrics.ErrorCount.increment()
        Future.failed(t)}}
}

object DynamoToScala {
  implicit class RichItemBuilder(val builder: EnhancedDocument.Builder) extends AnyVal {
    def putOptString(key: String, value: Option[String]): EnhancedDocument.Builder = {
      value.fold(builder)(v => builder.putString(key, v))
    }
  }

  implicit val codec: DynamoCodec[StoryPackage] = new DynamoCodec[StoryPackage] {
    override def toItem(story: StoryPackage): EnhancedDocument = {
      lazy val now = new DateTime().withZone(DateTimeZone.UTC)

      EnhancedDocument.builder()
        .attributeConverterProviders(AttributeConverterProvider.defaultProvider())
        .putString("id", story.id.getOrElse(IdGeneration.nextId))
        .putOptString("packageName", story.name)
        .putOptString("searchName", story.name.map(_.toLowerCase))
        .putBoolean("isHidden", story.isHidden.getOrElse(true))
        .putString("lastModify", story.lastModify.getOrElse(now.toString))
        .putOptString("lastModifyBy", story.lastModifyBy)
        .putOptString("lastModifyByName", story.lastModifyByName)
        .putOptString("createdBy", story.createdBy)
        .putOptString("created", story.created)
        .putBoolean("deleted", story.deleted.getOrElse(false))
        .build()
    }

    override def fromItem(item: EnhancedDocument): StoryPackage = {
      StoryPackage(
        id = Option(item.getString("id")),
        name = Option(item.getString("packageName")),
        isHidden = Option(if (item.isPresent("isHidden")) item.getBoolean("isHidden") else false),
        lastModify = Option(item.getString("lastModify")),
        lastModifyBy = Option(item.getString("lastModifyBy")),
        lastModifyByName = Option(item.getString("lastModifyByName")),
        createdBy = Option(item.getString("createdBy")),
        created = Option(item.getString("created")),
        deleted = if (item.isPresent("deleted")) Option(item.getBoolean("deleted")) else None
      )
    }
  }

  def convertToStoryPackage(item: EnhancedDocument): StoryPackage = {
    deserialize[StoryPackage](item)
  }

  def convertToItem(story: StoryPackage): EnhancedDocument = {
    serialize(story)
  }

  def convertToListOfStoryPackages(collection: PageIterable[EnhancedDocument]): List[StoryPackage] = {
    val iterator = collection.items().asScala
    iterator.map(convertToStoryPackage).toList
  }

  private def serialize[T: DynamoCodec](t: T): EnhancedDocument = implicitly[DynamoCodec[T]].toItem(t)

  private def deserialize[T: DynamoCodec](item: EnhancedDocument): T = implicitly[DynamoCodec[T]].fromItem(item)
}

trait DynamoCodec[T] {
  def toItem(t: T): EnhancedDocument
  def fromItem(item: EnhancedDocument): T
}
