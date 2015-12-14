package services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import conf.{Configuration, aws}
import model.{StoryPackage, StoryPackageSearchResult}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger

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

  def createStoryPackage(story: StoryPackage, email: String): Future[StoryPackage] = {
    val errorMessage = "Exception in dynamoDB putItem while creating a story package"
    WithExceptionHandling.storyPackage(errorMessage, {
      val generatedId = IdGeneration.nextId
      val modifyDate = new DateTime().withZone(DateTimeZone.UTC)
      val dbItem = new Item()
        .withPrimaryKey("id", generatedId)
        .withString("packageName", story.name.get)
        .withString("searchName", story.name.get.toLowerCase)
        .withBoolean("isHidden", story.isHidden.get)
        .withNumber("lastModify", modifyDate.getMillis)
        .withString("lastModifyBy", email)
        .withString("createdBy", email)

      table.putItem(dbItem)
      val newStoryPackage = story.copy(
        id = Some(generatedId),
        lastModify = Some(modifyDate.toString)
      )
      Logger.info(s"New story package created with id:${newStoryPackage.id} -> $newStoryPackage")
      newStoryPackage
    })
  }

  def searchPackages(term: String, isHidden: Boolean = false): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in searchPackages while searching $term"
    WithExceptionHandling.searchResult(errorMessage, {
      val values = new ValueMap()
        .withString(":search_term", term)
        .withBoolean(":is_hidden", isHidden)

      // TODO pagination
      val scanRequest = new ScanSpec()
        .withFilterExpression("begins_with (searchName, :search_term) and isHidden = :is_hidden")
        .withValueMap(values)

      val results = table.scan(scanRequest)
      StoryPackageSearchResult(
        term = Some(term),
        results = DynamoToScala.convertToListOfStoryPackages(results)
      )
    })
  }

  def latestPackages(maxAge: Int, isHidden: Boolean = false): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in latestPackages fetching packages since $maxAge days ago"
    WithExceptionHandling.searchResult(errorMessage, {
      val since = new DateTime().withZone(DateTimeZone.UTC).minusDays(maxAge)
      val values = new ValueMap()
        .withNumber(":since", since.getMillis)
        .withBoolean(":is_hidden", isHidden)

      // TODO sort
      // TODO withMaxResultSize ?
      val scanRequest = new ScanSpec()
        .withFilterExpression("lastModify > :since and isHidden = :is_hidden")
        .withValueMap(values)

      val results = table.scan(scanRequest)
      StoryPackageSearchResult(
        latest = Some(maxAge),
        results = DynamoToScala.convertToListOfStoryPackages(results)
      )
    })
  }

  def getPackage(id: String): Future[StoryPackage] = {
    val errorMessage = s"Unable to find story package with id $id"
    WithExceptionHandling.storyPackage(errorMessage, {
      val item = table.getItem("id", id)
      DynamoToScala.convertToStoryPackage(item)
    })
  }
}

private object WithExceptionHandling {
  def storyPackage(errorMessage: String, block: => StoryPackage): Future[StoryPackage] = {
    Try(block) match {
      case Success(newStoryPackage) =>
        Future.successful(newStoryPackage)
      case Failure(t: Throwable) =>
        Logger.error(errorMessage, t)
        Future.failed(t)}}

  def searchResult(errorMessage: String, block: => StoryPackageSearchResult): Future[StoryPackageSearchResult] = {
    Try(block) match {
      case Success(result) => Future.successful(result)
      case Failure(t: Throwable) =>
        Logger.error(errorMessage, t)
        Future.failed(t)}}
}

object DynamoToScala {
  implicit val codec: DynamoCodec[StoryPackage] = new DynamoCodec[StoryPackage] {
    override def toItem(story: StoryPackage): Item = {
      new Item()
    }

    override def fromItem(item: Item): StoryPackage = {
      StoryPackage(
        id = Option(item.getString("id")),
        name = Option(item.getString("packageName")),
        isHidden = Option(item.getBOOL("isHidden")),
        lastModify = Option(item.getLong("lastModify")).map(new DateTime(_).withZone(DateTimeZone.UTC).toString),
        lastModifyBy = Option(item.getString("lastModifyBy")),
        createdBy = Option(item.getString("createdBy"))
      )
    }
  }

  def convertToStoryPackage(item: Item): StoryPackage = {
    deserialize[StoryPackage](item)
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
