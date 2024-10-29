package story_packages.updates

import org.apache.pekko.actor.Scheduler
import com.amazonaws.services.dynamodbv2.document.Item
import story_packages.model.StoryPackage
import org.joda.time.DateTime
import play.api.libs.json.Json
import story_packages.services.{Database, DynamoReindexJobs, FrontsApi, Logging}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import play.api.libs.json.OFormat

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
  implicit val jsonFormat: OFormat[ReindexProgress] = Json.format[ReindexProgress]
}

sealed trait ReindexStatus{val label: String}
case class InProgress(label: String = "in progress") extends ReindexStatus
case class Failed(label: String = "failed") extends ReindexStatus
case class Completed(label: String = "completed") extends ReindexStatus
case class Cancelled(label: String = "cancelled") extends ReindexStatus

object SortItemsByLastStartTime {
  implicit def sortByStartTime: Ordering[Item] = {
    def convertToDateTime(item: Item) = new DateTime(item.getString("startTime"))
    Ordering.fromLessThan(convertToDateTime(_) isAfter convertToDateTime(_))
  }
}

class Reindex(dynamoReindexJobs: DynamoReindexJobs, database: Database, frontsApi: FrontsApi, kinesisEventSender: KinesisEventSender,
              scheduler: Scheduler) extends Logging {
  def scheduleJob(isHidden: Boolean = false): Future[Option[RunningJob]] = {
    if (dynamoReindexJobs.hasJobInProgress(isHidden)) {
      Logger.info(s"Cannot run multiple reindex at the same time")
      Future.successful(None)
    } else {
      Logger.info("Scanning table for reindex job")
      database.scanAllPackages(isHidden)
        .map(reindexPage => {
            val job = dynamoReindexJobs.createJob(reindexPage)
            scheduler.scheduleOnce(1.seconds) {
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
          _ <- sendToKinesisStream(nextPackage, job)
        } yield {
          if (processedResults % notifyEvery == 0) dynamoReindexJobs.markProgressUpdate(job, processedResults)
          processedResults + 1
        }
    }
    .map(lastProcessedResult => {
      Logger.info(s"Processing reindex job $job, step completed successfully")
      step.next match {
        case Some(nextPage) =>
          // TODO iterate on next page
          dynamoReindexJobs.markCompleteJob(job, lastProcessedResult)
          Logger.error("Next page not implemented")
        case None =>
          dynamoReindexJobs.markCompleteJob(job, lastProcessedResult)
      }
    })
    .recover {
      case NonFatal(e) =>
        Logger.error(s"Error when processing reindexing step of job $job", e)
        dynamoReindexJobs.markFailedJob(job)
    }
  }

  private def sendToKinesisStream(storyPackage: StoryPackage, job: RunningJob): Future[Unit] = {
    (for {
      packageId <- storyPackage.id
      displayName <- storyPackage.name
    } yield {
      Logger.info(s"Getting stored package with id $packageId from S3")
      frontsApi.amazonClient.collection(packageId).map {
        case Some(collectionJson) =>
          Logger.info(s"Sending reindex message on kinesis stream for package ${storyPackage.id}")
          if (storyPackage.deleted.getOrElse(false)) {
            kinesisEventSender.putReindexDelete(packageId, displayName, collectionJson, job.isHidden)
          } else {
            kinesisEventSender.putReindexUpdate(packageId, displayName, collectionJson, job.isHidden)
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
    dynamoReindexJobs.jobInProgress(isHidden).orElse(dynamoReindexJobs.getLastStartedJob(isHidden))
  }
}
