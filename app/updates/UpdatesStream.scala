package updates

import model.StoryPackage
import play.api.Logger

class UpdatesStream(auditingUpdates: AuditingUpdates, kinesisEventSender: KinesisEventSender) {

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {

    auditingUpdates.putStreamUpdate(streamUpdate)
    for {
      (collectionId, collectionJson) <- streamUpdate.collections
      isHidden <- streamUpdate.storyPackage.isHidden
      displayName <- streamUpdate.storyPackage.name
    } yield {
      if (!isHidden) {
        kinesisEventSender.putCapiUpdate(collectionId, displayName, collectionJson)
      } else {
        Logger.info(s"Ignoring CAPI update for hidden package $collectionId")
      }
    }
  }

  def putStreamDelete(streamUpdate: StreamUpdate, packageId: String, isHidden: Boolean): Unit = {
    auditingUpdates.putStreamUpdate(streamUpdate)
    if (!isHidden)
      kinesisEventSender.putCapiDelete(packageId)
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
      auditingUpdates.putStreamUpdate(streamUpdate)
    }
  }
}
