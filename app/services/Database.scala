package services

import java.util.HashMap

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ScanRequest}
import conf.{Configuration, aws}
import model.{StoryPackage, StoryPackageSearchResult}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Database {
  private lazy val client = {
    val client = new AmazonDynamoDBClient(aws.mandatoryCredentials)
    client.setEndpoint(AwsEndpoints.dynamoDb)
    client
  }
  private lazy val table = new DynamoDB(client).getTable(Configuration.storage.configTable)

  def createStoryPackage(story: StoryPackage): Future[StoryPackage] = {
    WithExceptionHandling.storyPackage({
      val generatedId = IdGeneration.nextId
      val modifyDate = new DateTime().withZone(DateTimeZone.UTC)
      val dbItem = new Item()
        .withPrimaryKey("id", generatedId)
        .withString("packageName", story.name.get)
        .withString("searchName", story.name.get.toLowerCase)
        .withBoolean("isHidden", story.isHidden.get)
        .withNumber("lastModify", modifyDate.getMillis)

      table.putItem(dbItem)
      story.copy(
        id = Some(generatedId),
        lastModify = Some(modifyDate.toString)
      )
    })
  }

  def searchPackages(term: String, isHidden: Boolean = false): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in searchPackages while searching $term"
    WithExceptionHandling.searchResult(errorMessage, {
      var values = new HashMap[String, AttributeValue]
      values.put(":search_term", new AttributeValue().withS(term))
      values.put(":is_hidden", new AttributeValue().withBOOL(isHidden))

      // TODO pagination
      val scanRequest = new ScanRequest()
        .withTableName(Configuration.storage.configTable)
        .withFilterExpression("begins_with (searchName, :search_term) and isHidden = :is_hidden")
        .withExpressionAttributeValues(values)

      val result = client.scan(scanRequest)
      StoryPackageSearchResult(
        term = Some(term),
        results = DynamoToScala.convertToListOfStoryPackages(result.getItems)
      )
    })
  }

  def latestPackages(maxAge: Int, isHidden: Boolean = false): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in latestPackages fetching packages since $maxAge days ago"
    WithExceptionHandling.searchResult(errorMessage, {
      val since = new DateTime().withZone(DateTimeZone.UTC).minusDays(maxAge)
      var values = new HashMap[String, AttributeValue]
      values.put(":since", new AttributeValue().withN(since.getMillis.toString))
      values.put(":is_hidden", new AttributeValue().withBOOL(isHidden))

      // TODO sort
      val scanRequest = new ScanRequest()
        .withTableName(Configuration.storage.configTable)
        .withFilterExpression("lastModify > :since and isHidden = :is_hidden")
        .withExpressionAttributeValues(values)

      val result = client.scan(scanRequest)
      StoryPackageSearchResult(
        latest = Some(maxAge),
        results = DynamoToScala.convertToListOfStoryPackages(result.getItems)
      )
    })
  }
}

private object WithExceptionHandling {
  def storyPackage(block: => StoryPackage): Future[StoryPackage] = {
    Try(block) match {
      case Success(newStoryPackage) =>
        Logger.info(s"New story package created with id:${newStoryPackage.id} -> $newStoryPackage")
        Future.successful(newStoryPackage)
      case Failure(t: Throwable) =>
        Logger.error(s"Exception in dynamoDB putItem while creating a story package", t)
        Future.failed(t)}}

  def searchResult(errorMessage: String, block: => StoryPackageSearchResult): Future[StoryPackageSearchResult] = {
    Try(block) match {
      case Success(result) => Future.successful(result)
      case Failure(t: Throwable) =>
        Logger.error(errorMessage, t)
        Future.failed(t)}}
}

object DynamoToScala {
  private def iterateOnList(list: java.util.List[java.util.Map[String, AttributeValue]]): List[StoryPackage] = {
    var newList: List[StoryPackage] = Nil
    val iterator = list.iterator()
    while (iterator.hasNext()) {
      newList = convertToStoryPackage(iterator.next()) +: newList
      None
    }
    newList
  }
  private def iterateOnCollection(coll: ItemCollection[QueryOutcome]): List[StoryPackage] = {
    var newList: List[StoryPackage] = Nil
    val iterator = coll.iterator()
    while (iterator.hasNext()) {
      newList = convertToStoryPackage(iterator.next()) +: newList
      None
    }
    newList
  }

  def convertToListOfStoryPackages(list: java.util.List[java.util.Map[String, AttributeValue]]): List[StoryPackage] = {
    iterateOnList(list)
  }
  def convertToListOfStoryPackages(coll: ItemCollection[QueryOutcome]): List[StoryPackage] = {
    iterateOnCollection(coll)
  }

  def convertToStoryPackage(result: java.util.Map[String, AttributeValue]): StoryPackage = {
    StoryPackage(
      id = Some(result.get("id").getS),
      name = Some(result.get("packageName").getS),
      isHidden = Some(result.get("isHidden").getBOOL),
      lastModify = Some(new DateTime(result.get("lastModify").getN.toLong).withZone(DateTimeZone.UTC).toString)
    )
  }
  def convertToStoryPackage(item: Item): StoryPackage = {
    StoryPackage(
      id = Some("id"),
      name = Some("packageName"),
      isHidden = Some(true),
      lastModify = Some("asd")
    )
  }
}
