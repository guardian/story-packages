package model

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json

case class StoryPackage(
  id: Option[String],
  name: Option[String],
  isHidden: Option[Boolean],
  lastModify: Option[String],
  lastModifyBy: Option[String],
  lastModifyByName: Option[String],
  createdBy: Option[String],
  deleted: Option[Boolean]
) {}
object StoryPackage {
  implicit val jsonFormat = Json.format[StoryPackage]
}

object SortByLastModify {
  implicit val sortByModifyDate = new Ordering[StoryPackage] {
    def compare(a: StoryPackage, b: StoryPackage) = (a.lastModify, b.lastModify) match {
      case (None, None) => 0
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case (Some(one), Some(two)) => two.compareTo(one)
    }
  }
}
object SortByName {
  implicit val sortByModifyDate = new Ordering[StoryPackage] {
    def compare(a: StoryPackage, b: StoryPackage) = (a.name, b.name) match {
      case (None, None) => 0
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case (Some(one), Some(two)) => one.compareToIgnoreCase(two)
    }
  }
}
