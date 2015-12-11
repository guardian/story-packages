package services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec
import com.amazonaws.services.dynamodbv2.document.utils.{NameMap, ValueMap}
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ScanRequest}
import conf.{Configuration, aws}
import model.{StoryPackage, StoryPackageSearchResult}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger

import java.util.HashMap
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
        .withString("name", story.name.get)
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
    println(term, isHidden)
    val errorMessage = s"Exception in searchPackages while searching $term"
    WithExceptionHandling.searchResult(errorMessage, {
      val valueMap = new ValueMap()
//        .withBoolean(":is_hidden", isHidden)
        .withString(":search_term", term)
      val nameMap = new NameMap()
        .`with`("#n", "name")
      val spec = new QuerySpec()
        .withKeyConditionExpression("#n = :search_term")
        .withValueMap(valueMap)
        .withNameMap(nameMap)
        .withMaxPageSize(Configuration.storage.maxPageSize)

      val results = table.query(spec)
      val list: List[StoryPackage] = {
        val runningList = Nil
        val iterator = results.iterator()
        while (iterator.hasNext()) {
          val result = iterator.next()
          StoryPackage(
            id = Some(result.getString("id")),
            name = Some(result.getString("name")),
            isHidden = Some(result.getBoolean("isHidden")),
            lastModify = Some(new DateTime(result.getLong("lastModify")).withZone(DateTimeZone.UTC).toString)
          ) +: runningList
        }
        runningList
      }
      println(list)
      StoryPackageSearchResult(
        term = Some(term),
        results = list
      )
    })
  }

  def latestPackages(maxResults: Int, maxAge: Int): Future[StoryPackageSearchResult] = {
    val errorMessage = s"Exception in latestPackages fetching $maxResults packages since $maxAge days ago"
    WithExceptionHandling.searchResult(errorMessage, {
      val since = new DateTime().withZone(DateTimeZone.UTC).minusDays(maxAge)
      var expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":since", new AttributeValue().withN(since.getMillis.toString))

      val scanRequest = new ScanRequest()
        .withTableName(Configuration.storage.configTable)
        .withFilterExpression("lastModify > :since")
        .withExpressionAttributeValues(expressionAttributeValues)
//        .withLimit(3)

      val result = client.scan(scanRequest)
      println(result)
      StoryPackageSearchResult(
        latest = Some(maxResults),
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
  def iterate(list: java.util.List[java.util.Map[String, AttributeValue]])(convert: java.util.Map[String, AttributeValue] => StoryPackage): List[StoryPackage] = {
    var newList: List[StoryPackage] = Nil
    val iterator = list.iterator()
    while (iterator.hasNext()) {
      newList = convertToStoryPackage(iterator.next()) +: newList
      None
    }
    newList
  }

  def convertToListOfStoryPackages(list: java.util.List[java.util.Map[String, AttributeValue]]): List[StoryPackage] = {
    iterate(list)(convertToStoryPackage)
  }

  def convertToStoryPackage(result: java.util.Map[String, AttributeValue]): StoryPackage = {
    StoryPackage(
      id = Some(result.get("id").getS),
      name = Some(result.get("name").getS),
      isHidden = Some(result.get("isHidden").getBOOL),
      lastModify = Some(new DateTime(result.get("lastModify").getN.toLong).withZone(DateTimeZone.UTC).toString)
    )
  }
}
