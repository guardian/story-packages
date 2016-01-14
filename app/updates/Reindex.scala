package updates

import play.api.Logger
import play.api.libs.json.Json
import play.libs.Akka
import services.{Database, FrontsApi}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal


case class ReindexResult(
  totalCount: Int,
  jobId: String
)

object ReindexResult {
  implicit val jsonFormat = Json.format[ReindexResult]
}

case class ReindexStep(
  totalCount: Int,
  list: List[String],
  next: Option[String]
)

object Reindex {
  def scheduleJob(job: String, isHidden: Boolean = false): Future[ReindexResult] = {
    Logger.info(s"Scheduling a reindex job with id $job")

    Database.scanAllPackages(isHidden)
    .map(scanResult => {
      val reindexResult = ReindexResult(
        scanResult.totalCount,
        job)
      Akka.system.scheduler.scheduleOnce(1.seconds) { processScanResult(reindexResult.jobId, scanResult) }
      reindexResult
    })
  }

  private def processScanResult(jobId: String, step: ReindexStep) = {
    Logger.info(s"Processing reindex job $jobId, step with ${step.list.size} packages out of ${step.totalCount}")
    val notifyEvery = math.ceil(step.totalCount / 100.0)

    step.list.foldLeft(Future(0)) {
      (previousFuture, next) =>
        for {
          processedResults <- previousFuture
          next <- sendToKinesisStream(next)
        } yield {
          if (processedResults % notifyEvery == 0) sendProgressUpdate(jobId, processedResults)
          processedResults + 1
        }
    }
    .map(_ => {
      Logger.info(s"Processing reindex job $jobId, step completed successfully")
      step.next match {
        case Some(nextPage) =>
          // TODO iterate on next page
          Logger.error("Next page not implemented")
        case None => sendProgressComplete(jobId)
      }
    })
    .recover {
      case NonFatal(e) =>
        Logger.error(s"Error when processing reindexing step of job $jobId", e)
        sendProgressFailed(jobId)
    }
  }

  private def sendToKinesisStream(packageId: String): Future[Unit] = {
    Logger.info(s"Getting stored package with id $packageId from S3")
    FrontsApi.amazonClient.collection(packageId).map {
      case Some(collectionJson) =>
        Logger.info(s"Sending reindex message on kinesis stream for package $packageId")
        KinesisEventSender.putReindexUpdate(packageId, collectionJson)
      case None => Logger.info(s"Ignore reindex of empty story package $packageId")
    }
    Future.successful(None)
  }

  private def sendProgressUpdate(jobId: String, processedResults: Int): Unit = {
    println(s"sending progress update $jobId $processedResults")
  }

  private def sendProgressComplete(jobId: String): Unit = {
    println(s"sending complete update $jobId")
  }

  private def sendProgressFailed(jobId: String): Unit = {
    println(s"sending failed update $jobId")
  }
}
