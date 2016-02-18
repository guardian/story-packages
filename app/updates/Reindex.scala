package updates

import com.amazonaws.services.dynamodbv2.document.Item
import model.StoryPackage
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import play.libs.Akka
import services.{Database, DynamoReindexJobs, FrontsApi}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

case class ReindexPage(
  totalCount: Int,
  list: List[StoryPackage],
  next: Option[String],
  isHidden: Boolean
)

case class RunningJob(
  startTime: DateTime,
  status: ReindexStatus,
  documentsIndexed: Int,
  documentsExpected: Int,
  isHidden: Boolean
)
object RunningJob {
  def apply(reindexPage: ReindexPage): RunningJob = {
    val now = new DateTime()
    RunningJob(now, InProgress(), 0, reindexPage.totalCount, reindexPage.isHidden)
  }
}

case class ReindexProgress(
  status: String,
  documentsIndexed: Int,
  documentsExpected: Int
)
object ReindexProgress {
  implicit val jsonFormat = Json.format[ReindexProgress]
}

sealed trait ReindexStatus{val label: String}
case class InProgress(val label: String = "in progress") extends ReindexStatus
case class Failed(val label: String = "failed") extends ReindexStatus
case class Completed(val label: String = "completed") extends ReindexStatus
case class Cancelled(val label: String = "cancelled") extends ReindexStatus

object SortItemsByLastStartTime {
  implicit def sortByStartTime: Ordering[Item] = {
    def convertToDateTime(item: Item) = new DateTime(item.getString("startTime"))
    Ordering.fromLessThan(convertToDateTime(_) isAfter convertToDateTime(_))
  }
}

object Reindex {
  def scheduleJob(isHidden: Boolean = false): Future[Option[RunningJob]] = {
    if (DynamoReindexJobs.hasJobInProgress(isHidden)) {
      Logger.info(s"Cannot run multiple reindex at the same time")
      Future.successful(None)
    } else {
      Logger.info("Scanning table for reindex job")
      Database.scanAllPackages(isHidden)
        .map(reindexPage => {
            val job = DynamoReindexJobs.createJob(reindexPage)
            Akka.system.scheduler.scheduleOnce(1.seconds) {
              processJob(job, reindexPage)
            }
            Some(job)
        })
    }
  }

  private def processJob(job: RunningJob, step: ReindexPage) = {
    Logger.info(s"Processing reindex job step with ${step.list.size} packages out of ${step.totalCount}")
    val notifyEvery = math.ceil(step.totalCount / 100.0)

    step.list.foldLeft(Future.successful(0)) {
      (previousFuture, nextPackage) =>
        for {
          processedResults <- previousFuture
          _ <- sendToKinesisStream(nextPackage)
        } yield {
          if (processedResults % notifyEvery == 0) DynamoReindexJobs.markProgressUpdate(job, processedResults)
          processedResults + 1
        }
    }
    .map(lastProcessedResult => {
      Logger.info(s"Processing reindex job $job, step completed successfully")
      step.next match {
        case Some(nextPage) =>
          // TODO iterate on next page
          DynamoReindexJobs.markCompleteJob(job, lastProcessedResult)
          Logger.error("Next page not implemented")
        case None =>
          DynamoReindexJobs.markCompleteJob(job, lastProcessedResult)
      }
    })
    .recover {
      case NonFatal(e) =>
        Logger.error(s"Error when processing reindexing step of job $job", e)
        DynamoReindexJobs.markFailedJob(job)
    }
  }

  private def sendToKinesisStream(storyPackage: StoryPackage): Future[Unit] = {
    (for {
      packageId <- storyPackage.id
      displayName <- storyPackage.name
    } yield {
      Logger.info(s"Getting stored package with id $packageId from S3")
      FrontsApi.amazonClient.collection(packageId).map {
        case Some(collectionJson) =>
          Logger.info(s"Sending reindex message on kinesis stream for package ${storyPackage.id}")
          if (storyPackage.deleted.getOrElse(false)) {
            KinesisEventSender.putReindexDelete(packageId, displayName, collectionJson)
          } else {
            KinesisEventSender.putReindexUpdate(packageId, displayName, collectionJson)
          }
        case None =>
          Logger.info(s"Ignore reindex of empty story package $packageId")
      }
    }).getOrElse({
      Logger.error(s"Story package $storyPackage doesn't have id or name")
      Future.successful(None)
    })
  }

  def getJobProgress(isHidden: Boolean): Option[ReindexProgress] = {
    DynamoReindexJobs.jobInProgress(isHidden).orElse(DynamoReindexJobs.getLastStartedJob(isHidden))
  }
}
