package updates

object UpdatesStream {

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {
    AuditingUpdates.putStreamUpdate(streamUpdate)
    KinesisEventSender.putCapiUpdate(streamUpdate.collections)
  }
}
