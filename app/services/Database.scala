package services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.{DeleteItemSpec, ScanSpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.model.{DeleteItemResult, ReturnValue}
import com.gu.pandomainauth.model.User
import conf.{Configuration, aws}
import model.{StoryPackage, StoryPackageSearchResult}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import util.Identity._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InvalidQueryResult(msg: String) extends Throwable(msg)

object Database {
  private lazy val client = {
    val client = new AmazonDynamoDBClient(aws.mandatoryCredentials)
    client.setEndpoint(AwsEndpoints.dynamoDb)
    client
  }
  private lazy val table = new DynamoDB(client).getTable(Configuration.storage.configTable)

  def createStoryPackage(story: StoryPackage, user: User): Future[StoryPackage] = {
    val errorMessage = "Exception in dynamoDB putItem while creating a story package"
    WithExceptionHandling(errorMessage, {
      val item = DynamoToScala.convertToItem(story.copy(
        lastModify = Some(new DateTime().withZone(DateTimeZone.UTC).toString),
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

  def searchPackages(term: String, isHidden: Boolean = false): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in searchPackages while searching $term"
    WithExceptionHandling(errorMessage, {
      val values = new ValueMap()
        .withString(":search_term", term)
        .withBoolean(":is_hidden", isHidden)

      val scanRequest = new ScanSpec()
        .withFilterExpression("begins_with (searchName, :search_term) and isHidden = :is_hidden")
        .withValueMap(values)
        .withMaxResultSize(Configuration.storage.maxPageSize)

      val results = table.scan(scanRequest)

      import model.SortByName._
      StoryPackageSearchResult(
        term = Some(term),
        results = DynamoToScala.convertToListOfStoryPackages(results).sorted
      )
    })
  }

  def latestPackages(maxAge: Int, isHidden: Boolean = false): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in latestPackages fetching packages since $maxAge days ago"
    WithExceptionHandling(errorMessage, {
      val since = new DateTime().withZone(DateTimeZone.UTC).minusDays(maxAge)
      val values = new ValueMap()
        .withString(":since", since.toString)
        .withBoolean(":is_hidden", isHidden)

      val scanRequest = new ScanSpec()
        .withFilterExpression("lastModify > :since and isHidden = :is_hidden")
        .withValueMap(values)
        .withMaxResultSize(Configuration.storage.maxLatestResults)

      val results = table.scan(scanRequest)

      import model.SortByLastModify._
      StoryPackageSearchResult(
        latest = Some(maxAge),
        results = DynamoToScala.convertToListOfStoryPackages(results).sorted
      )
    })
  }

  def getPackage(id: String): Future[StoryPackage] = {
    val errorMessage = s"Unable to find story package with id $id"
    WithExceptionHandling(errorMessage, {
      val item = table.getItem("id", id)
      DynamoToScala.convertToStoryPackage(item)
    })
  }

  def removePackage(id: String): Future[DeleteItemResult] = {
    val errorMessage = s"Unable to delete story package $id"
    WithExceptionHandling(errorMessage, {
      val outcome = table.deleteItem(new DeleteItemSpec()
        .withPrimaryKey("id", id)
        .withReturnValues(ReturnValue.ALL_OLD)
      )
      outcome.getDeleteItemResult()
    })
  }

  def touchPackage(id: String, user: User): Future[StoryPackage] = {
    val errorMessage = s"Unable to update modification metadata for story package $id"
    WithExceptionHandling(errorMessage, {
      val modifyDate = new DateTime().withZone(DateTimeZone.UTC)

      val updateSpec = new UpdateItemSpec()
        .withPrimaryKey("id", id)
        .addAttributeUpdate(new AttributeUpdate("lastModify").put(modifyDate.toString))
        .addAttributeUpdate(new AttributeUpdate("lastModifyBy").put(user.email))
        .addAttributeUpdate(new AttributeUpdate("lastModifyByName").put(user.fullName))
        .withReturnValues(ReturnValue.ALL_NEW)

      val outcome = table.updateItem(updateSpec)
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
    }

    override def fromItem(item: Item): StoryPackage = {
      StoryPackage(
        id = Option(item.getString("id")),
        name = Option(item.getString("packageName")),
        isHidden = Option(item.getBOOL("isHidden")),
        lastModify = Option(item.getString("lastModify")),
        lastModifyBy = Option(item.getString("lastModifyBy")),
        lastModifyByName = Option(item.getString("lastModifyByName")),
        createdBy = Option(item.getString("createdBy"))
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
