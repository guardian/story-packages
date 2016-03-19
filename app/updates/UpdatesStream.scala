package updates

import model.StoryPackage
import play.api.Logger

object UpdatesStream {

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {

    AuditingUpdates.putStreamUpdate(streamUpdate)
    for {
      (collectionId, collectionJson) <- streamUpdate.collections
      isHidden <- streamUpdate.storyPackage.isHidden
      displayName <- streamUpdate.storyPackage.name
    } yield {
      if (!isHidden) {
        KinesisEventSender.putCapiUpdate(collectionId, displayName, collectionJson)
      } else {
        Logger.info(s"Ignoring CAPI update for hidden package $collectionId")
      }
    }
  }

  def putStreamDelete(streamUpdate: StreamUpdate, packageId: String, isHidden: Boolean): Unit = {
    AuditingUpdates.putStreamUpdate(streamUpdate)
    if (!isHidden)
      KinesisEventSender.putCapiDelete(packageId)
    else
      Logger.info(s"Ignoring CAPI delete for hidden package $packageId")
  }

  def putStreamCreate(storyPackage: StoryPackage, email: String): Unit = {
    for {
      id <- storyPackage.id
      isHidden <- storyPackage.isHidden
      name <- storyPackage.name
    } yield {
      val updateMessage = CreatePackage(id, isHidden, name)
      val streamUpdate = StreamUpdate(updateMessage, email, Map(), storyPackage)
      AuditingUpdates.putStreamUpdate(streamUpdate)
    }
  }
}
