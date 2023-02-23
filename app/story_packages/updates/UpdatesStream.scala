package story_packages.updates

import story_packages.model.StoryPackage

class UpdatesStream(auditingUpdates: AuditingUpdates, kinesisEventSender: KinesisEventSender) {

  def putStreamUpdate(streamUpdate: AuditUpdate): Unit = {

    auditingUpdates.putAudit(streamUpdate)
    for {
      (collectionId, collectionJson) <- streamUpdate.collections
      isHidden <- streamUpdate.storyPackage.isHidden
      displayName <- streamUpdate.storyPackage.name
    } yield {
      kinesisEventSender.putCapiUpdate(collectionId, displayName, collectionJson, isHidden)
    }
  }

  def putStreamDelete(streamUpdate: AuditUpdate, packageId: String, isHidden: Boolean): Unit = {
    auditingUpdates.putAudit(streamUpdate)
    kinesisEventSender.putCapiDelete(packageId, isHidden)
  }

  def putStreamCreate(storyPackage: StoryPackage, email: String): Unit = {
    for {
      id <- storyPackage.id
      isHidden <- storyPackage.isHidden
      name <- storyPackage.name
    } yield {
      val updateMessage = CreatePackage(id, isHidden, name)
      val streamUpdate = AuditUpdate(updateMessage, email, Map(), storyPackage)
      auditingUpdates.putAudit(streamUpdate)
    }
  }
}
