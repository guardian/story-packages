package services

import java.io.FileInputStream

import conf.ApplicationConfiguration
import play.api.libs.json._
import play.api.libs.json.Reads._

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
  val filePath = "/etc/gu/story-packages.assets-map.json"
  val assetsMap: Option[Bundles] = if (isDev) None else Some(readFromPath(filePath))

  private def readFromPath(path: String): Bundles = {
    val stream = new FileInputStream(filePath)
    val maybeJson = try { Json.parse(stream) } finally { stream.close() }
    maybeJson.validate[Bundles] match {
      case e: JsError => throw new InvalidAssetsException(s"JSON in $filePath does not match a valid Bundles")
      case json: JsSuccess[Bundles] => json.getOrElse(throw new InvalidAssetsException(s"Invalid JSON Bundle in $filePath"))
    }
  }

  def pathForPackages: String = pathFor(assetsMap.map(_.packages).getOrElse(""))

  private def pathFor(hashedFileName: String): String = {
    val stage = config.environment.stage.toUpperCase
    s"${config.cdn.host}/cms-fronts-static-assets/$stage/static-story-packages/$hashedFileName"
  }
}
