package updates

import stream.CapiUpdates

object UpdatesStream {
  def putStreamUpdate(streamUpdate: StreamUpdateWithCollections): Unit = {
    AuditingUpdates.putStreamUpdate(streamUpdate)
    CapiUpdates.putCapiUpdate(streamUpdate.collections)
  }
}
