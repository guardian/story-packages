package services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.{Item, DynamoDB}
import conf.{Configuration, aws}
import model.StoryPackage
import play.api.Logger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Database {
  private lazy val client = new DynamoDB({
    val client = new AmazonDynamoDBClient(aws.mandatoryCredentials)
    client.setEndpoint(AwsEndpoints.dynamoDb)
    client
  })
  private lazy val table = client.getTable(Configuration.storage.configTable)

  def createStoryPackage(story: StoryPackage): Future[StoryPackage] = {
    withExceptionHandlingForCreateStoryPackage({
      val generatedId = IdGeneration.nextId
      val dbItem = new Item()
        .withPrimaryKey("id", generatedId)
        .withString("name", story.name)
        .withBoolean("isHidden", story.isHidden)

      table.putItem(dbItem)
      story.copy(id = Some(generatedId))
    })
  }

  private def withExceptionHandlingForCreateStoryPackage(block: => StoryPackage): Future[StoryPackage] = {
    Try(block) match {
      case Success(newStoryPackage) =>
        Logger.info(s"New story package created with id:${newStoryPackage.id} -> $newStoryPackage")
        Future.successful(newStoryPackage)
      case Failure(t: Throwable) =>
        Logger.error(s"Exception in dynamoDB putItem while creating a story package", t)
        Future.failed(t)
    }
  }
}
