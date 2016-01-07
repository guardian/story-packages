package updates

import services.Database
import play.api.libs.concurrent.Execution.Implicits._

object UpdatesStream {

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {

    AuditingUpdates.putStreamUpdate(streamUpdate)

    streamUpdate.collections.foreach(keyValue => {
      for {
        storyPackage <- Database.getPackage(keyValue._1)
      } yield {
        storyPackage.isHidden match {
          case Some(false) => KinesisEventSender.putCapiUpdate(streamUpdate.collections)
          case _ =>
        }
      }
    })
  }
}
