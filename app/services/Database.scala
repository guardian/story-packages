package services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.{ScanSpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.model.ReturnValue
import com.gu.pandomainauth.model.User
import conf.ApplicationConfiguration
import metrics.StoryPackagesMetrics
import model.StoryPackage
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import updates.ReindexPage
import util.Identity._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InvalidQueryResult(msg: String) extends Throwable(msg)

class Database(config: ApplicationConfiguration, awsEndpoints: AwsEndpoints) {
  private lazy val client = {
    val client = new AmazonDynamoDBClient(config.aws.mandatoryCredentials)
    client.setEndpoint(awsEndpoints.dynamoDb)
    client
  }
  private lazy val table = new DynamoDB(client).getTable(config.storage.configTable)

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

  def getPackage(id: String): Future[StoryPackage] = {
    val errorMessage = s"Unable to find story package with id $id"
    WithExceptionHandling(errorMessage, {
      val item = table.getItem("id", id)
      StoryPackagesMetrics.QueryCount.increment()
      DynamoToScala.convertToStoryPackage(item)
    })
  }

  def scanAllPackages(isHidden: Boolean = false): Future[ReindexPage] = {
      val errorMessage = s"Exception in fetching all packages"
      WithExceptionHandling(errorMessage, {
        val values = new ValueMap()
          .withBoolean(":is_hidden", isHidden)

        val scanRequest = new ScanSpec()
          .withFilterExpression("isHidden = :is_hidden")
          .withValueMap(values)
          .withProjectionExpression("id,deleted,packageName")

        val outcome = table.scan(scanRequest)
        StoryPackagesMetrics.ScanCount.increment()

        val listIds = DynamoToScala.convertToListOfStoryPackages(outcome)
        val totalCount = math.max(listIds.size, outcome.getAccumulatedItemCount)

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

      val updateSpec = new UpdateItemSpec()
        .withPrimaryKey("id", id)
        .addAttributeUpdate(new AttributeUpdate("deleted").put(true))
        .withReturnValues(ReturnValue.ALL_NEW)

      val outcome = table.updateItem(updateSpec)

      StoryPackagesMetrics.DeleteCount.increment()
      DynamoToScala.convertToStoryPackage(outcome.getItem)
    })
  }

  def touchPackage(id: String, user: User, newName: Option[String] = None): Future[StoryPackage] = {
    val errorMessage = s"Unable to update modification metadata for story package $id"
    WithExceptionHandling(errorMessage, {
      val modifyDate = new DateTime().withZone(DateTimeZone.UTC)

      val updateSpec = new UpdateItemSpec()
        .withPrimaryKey("id", id)
        .addAttributeUpdate(new AttributeUpdate("lastModify").put(modifyDate.toString))
        .addAttributeUpdate(new AttributeUpdate("lastModifyBy").put(user.email))
        .addAttributeUpdate(new AttributeUpdate("lastModifyByName").put(user.fullName))
        .withReturnValues(ReturnValue.ALL_NEW)

      if (newName.nonEmpty) {
        updateSpec.addAttributeUpdate(new AttributeUpdate("packageName").put(newName.get))
      }

      val outcome = table.updateItem(updateSpec)
      StoryPackagesMetrics.UpdateCount.increment()
      DynamoToScala.convertToStoryPackage(outcome.getItem)
    })
  }
}

private object WithExceptionHandling {
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
  implicit class RichItem(val item: Item) extends AnyVal {
    def withOptString(key: String, value: Option[String]) = {
      value.fold(item)(v => item.withString(key, v))
    }
  }

  implicit val codec: DynamoCodec[StoryPackage] = new DynamoCodec[StoryPackage] {
    override def toItem(story: StoryPackage): Item = {
      lazy val now = new DateTime().withZone(DateTimeZone.UTC)

      new Item()
        .withPrimaryKey("id", story.id.getOrElse(IdGeneration.nextId))
        .withOptString("packageName", story.name)
        .withOptString("searchName", story.name.map(_.toLowerCase))
        .withBoolean("isHidden", story.isHidden.getOrElse(true))
        .withString("lastModify", story.lastModify.getOrElse(now.toString))
        .withOptString("lastModifyBy", story.lastModifyBy)
        .withOptString("lastModifyByName", story.lastModifyByName)
        .withOptString("createdBy", story.createdBy)
        .withOptString("created", story.created)
        .withBoolean("deleted", story.deleted.getOrElse(false))
    }

    override def fromItem(item: Item): StoryPackage = {
      StoryPackage(
        id = Option(item.getString("id")),
        name = Option(item.getString("packageName")),
        isHidden = Option(if (item.hasAttribute("isHidden")) item.getBOOL("isHidden") else false),
        lastModify = Option(item.getString("lastModify")),
        lastModifyBy = Option(item.getString("lastModifyBy")),
        lastModifyByName = Option(item.getString("lastModifyByName")),
        createdBy = Option(item.getString("createdBy")),
        created = Option(item.getString("created")),
        deleted = if (item.hasAttribute("deleted")) Option(item.getBOOL("deleted")) else None
      )
    }
  }

  def convertToStoryPackage(item: Item): StoryPackage = {
    deserialize[StoryPackage](item)
  }

  def convertToItem(story: StoryPackage): Item = {
    serialize(story)
  }

  def convertToListOfStoryPackages(collection: ItemCollection[ScanOutcome]): List[StoryPackage] = {
    val iterator = collection.iterator().asScala
    iterator.map(convertToStoryPackage).toList
  }

  private def serialize[T: DynamoCodec](t: T): Item = implicitly[DynamoCodec[T]].toItem(t)

  private def deserialize[T: DynamoCodec](item: Item): T = implicitly[DynamoCodec[T]].fromItem(item)
}

trait DynamoCodec[T] {
  def toItem(t: T): Item
  def fromItem(item: Item): T
}
