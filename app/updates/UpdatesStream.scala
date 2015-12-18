package updates

object UpdatesStream {

  val capiUpdates = new CapiUpdates()

  def putStreamUpdate(streamUpdate: StreamUpdate): Unit = {
    AuditingUpdates.putStreamUpdate(streamUpdate)
    capiUpdates.putCapiUpdate(streamUpdate.collections)
  }
}
