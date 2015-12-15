package model

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json

case class StoryPackage(
  id: Option[String],
  name: Option[String],
  isHidden: Option[Boolean],
  var lastModify: Option[String] = None,
  lastModifyMillis: Option[Long],
  lastModifyBy: Option[String],
  createdBy: Option[String]
) {
  lastModify = lastModifyMillis.map(new DateTime(_).withZone(DateTimeZone.UTC).toString)
}
object StoryPackage {
  implicit val jsonFormat = Json.format[StoryPackage]
}

object SortByLastModify {
  implicit val sortByModifyDate = new Ordering[StoryPackage] {
    def compare(a: StoryPackage, b: StoryPackage) = (a.lastModifyMillis, b.lastModifyMillis) match {
      case (None, None) => 0
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case (Some(one), Some(two)) => (two - one).toInt
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

case class StoryPackageSearchResult(
  term: Option[String] = None,
  latest: Option[Int] = None,
  results: List[StoryPackage] = Nil
) {}
object StoryPackageSearchResult {
  implicit val jsonFormat = Json.format[StoryPackageSearchResult]
}
