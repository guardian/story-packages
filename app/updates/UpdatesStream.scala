package updates

import play.api.Logger

object UpdatesStream {

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {

    AuditingUpdates.putStreamUpdate(streamUpdate)
    for {
      (collectionId, collectionJson) <- streamUpdate.collections
      isHidden <- streamUpdate.storyPackage.isHidden
    } yield {
      if (!isHidden) {
        KinesisEventSender.putCapiUpdate(collectionId, collectionJson)
      } else {
        Logger.info(s"Ignoring CAPI update for hidden package $collectionId")
      }
    }
  }

  def putStreamDelete(collectionId: String, isHidden: Boolean): Unit = {
    if (!isHidden)
      KinesisEventSender.putCapiDelete(collectionId)
    else
      Logger.info(s"Ignoring CAPI delete for hidden package $collectionId")

  }
}
