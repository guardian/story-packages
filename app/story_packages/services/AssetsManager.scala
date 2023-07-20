package story_packages.services

import conf.ApplicationConfiguration
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.io.Source

class InvalidAssetsException(msg: String) extends RuntimeException(msg)

object Bundles {
  implicit object bundleReads extends Reads[Bundles] {
    override def reads(json: JsValue): JsResult[Bundles] = {
      (json \ "packages.js").validate[String].map(Bundles(_))
    }
  }
}
case class Bundles (packages: String)


class AssetsManager(config: ApplicationConfiguration, isDev: Boolean) {
  val resourcePath = "/public/story-packages/bundles/assets-map.json"
  val assetsMap = if (isDev) None else Some(readFromPath(resourcePath))

  private def readFromPath(path: String): Bundles = {
    val assetsMapSource = Source.fromResource(path)
    val maybeJson = Json.parse(assetsMapSource.mkString)
    maybeJson.validate[Bundles] match {
      case e: JsError => throw new InvalidAssetsException(s"JSON in $resourcePath does not match a valid Bundles: $e")
      case json: JsSuccess[Bundles] => json.getOrElse(throw new InvalidAssetsException(s"Invalid JSON Bundle in $resourcePath"))
    }
  }

  def pathForPackages: String = pathFor(assetsMap.map(_.packages).getOrElse(""))

  private def pathFor(hashedFileName: String): String = {
    s"/assets/story-packages/bundles/$hashedFileName"
  }
}
