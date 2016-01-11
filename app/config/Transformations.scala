package config

import com.gu.facia.client.models.{CollectionConfigJson => CollectionConfig, ConfigJson, FrontJson => Front}

object Transformations {
  /** The Config ought never to contain empty fronts or collections that do not belong to any fronts */
  def prune(config: ConfigJson): ConfigJson = {
    val emptyFronts = config.fronts.filter(_._2.collections.isEmpty).map(_._1)
    val collectionIdsReferencedInFronts = config.fronts.values.flatMap(_.collections).toSet
    val orphanedCollections = config.collections.keySet -- collectionIdsReferencedInFronts

    config.copy(
      config.fronts -- emptyFronts,
      config.collections -- orphanedCollections
    )
  }

  def updateFront(frontId: String, front: Front)(config: ConfigJson): ConfigJson = {
    config.copy(fronts = config.fronts + (frontId -> front))
  }

  def updateCollection(
      frontIds: List[String],
      collectionId: String,
      collection: CollectionConfig
  )(config: ConfigJson): ConfigJson = {
    val updatedFronts = frontIds flatMap { frontId =>
      config.fronts.get(frontId) map { front =>
        frontId -> front.copy(collections = (front.collections ++ List(collectionId)).distinct)
      }
    }

    config.copy(
      fronts = config.fronts ++ updatedFronts,
      collections = config.collections + (collectionId -> collection)
    )
  }
}
