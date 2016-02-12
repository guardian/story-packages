package updates

import java.util.concurrent.atomic.AtomicReference

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
  list: List[(String, Boolean)],
  next: Option[String]
)

class ReindexJobInProgress(scheduled: String, inProgress: String)
  extends Throwable(s"Cannot schedule job $scheduled because $inProgress is still running")

object Reindex {
  val jobAlreadyInProgress: AtomicReference[Option[String]] = new AtomicReference(None)

  def scheduleJob(job: String, isHidden: Boolean = false): Future[ReindexResult] = {
    jobAlreadyInProgress.get() match {
      case Some(jobInProgress) =>
        Logger.info(s"Cannot run multiple reindex at the same time on the same machine, waiting for $jobInProgress to complete")
        Future.failed(new ReindexJobInProgress(job, jobInProgress))
      case None =>
        Logger.info(s"Scheduling a reindex job with id $job")
        Database.scanAllPackages(isHidden)
          .map(scanResult => {
            val reindexResult = ReindexResult(
              scanResult.totalCount,
              job
            )
            jobAlreadyInProgress.set(Some(job))
            Akka.system.scheduler.scheduleOnce(1.seconds) {
              processScanResult(reindexResult.jobId, scanResult)
            }
            reindexResult
          })
    }
  }


  private def processScanResult(jobId: String, step: ReindexStep) = {
    Logger.info(s"Processing reindex job $jobId, step with ${step.list.size} packages out of ${step.totalCount}")
    val notifyEvery = math.ceil(step.totalCount / 100.0)

    step.list.foldLeft(Future.successful(0)) {
      (previousFuture, nextPackageIdWithDeleted) =>
        for {
          processedResults <- previousFuture
          _ <- sendToKinesisStream(nextPackageIdWithDeleted._1, nextPackageIdWithDeleted._2)
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
          jobAlreadyInProgress.set(None)
          Logger.error("Next page not implemented")
        case None =>
          jobAlreadyInProgress.set(None)
          sendProgressComplete(jobId)
      }
    })
    .recover {
      case NonFatal(e) =>
        Logger.error(s"Error when processing reindexing step of job $jobId", e)
        jobAlreadyInProgress.set(None)
        sendProgressFailed(jobId)
    }
  }

  private def sendToKinesisStream(packageId: String, isDelete: Boolean): Future[Unit] = {
    Logger.info(s"Getting stored package with id $packageId from S3")
    FrontsApi.amazonClient.collection(packageId).map {
      case Some(collectionJson) =>
        Logger.info(s"Sending reindex message on kinesis stream for package $packageId")
        if (isDelete)
          KinesisEventSender.putReindexDelete(packageId, collectionJson)
        else
          KinesisEventSender.putReindexUpdate(packageId, collectionJson)

      case None => Logger.info(s"Ignore reindex of empty story package $packageId")
    }
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
