package model

import play.api.libs.json.Json

case class StoryPackage(
  id: Option[String],
  name: String,
  isHidden: Boolean
) {}
object StoryPackage {
  implicit val jsonFormat = Json.format[StoryPackage]
}
