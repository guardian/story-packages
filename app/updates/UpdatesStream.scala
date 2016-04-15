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
      kinesisEventSender.putCapiUpdate(collectionId, displayName, collectionJson, isHidden)
    }
  }

  def putStreamDelete(streamUpdate: StreamUpdate, packageId: String, isHidden: Boolean): Unit = {
    auditingUpdates.putStreamUpdate(streamUpdate)
    kinesisEventSender.putCapiDelete(packageId, isHidden)
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
