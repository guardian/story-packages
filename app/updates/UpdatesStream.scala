package updates

import stream.CapiUpdates

object UpdatesStream {

  val capiUpdates = new CapiUpdates()

  def putStreamUpdate(streamUpdate: StreamUpdateWithCollections): Unit = {
    AuditingUpdates.putStreamUpdate(streamUpdate)
    capiUpdates.putCapiUpdate(streamUpdate.collections)
  }
}
