package model

import play.api.libs.json.Json

case class StoryPackage(
  id: Option[String],
  name: Option[String],
  isHidden: Option[Boolean],
  lastModify: Option[String],
  lastModifyBy: Option[String],
  createdBy: Option[String]
) {}
object StoryPackage {
  implicit val jsonFormat = Json.format[StoryPackage]
}

case class StoryPackageSearchResult(
  term: Option[String] = None,
  latest: Option[Int] = None,
  results: List[StoryPackage] = Nil
) {}
object StoryPackageSearchResult {
  implicit val jsonFormat = Json.format[StoryPackageSearchResult]
}
