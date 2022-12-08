package conf

import play.api.mvc.RequestHeader

object Responses {
  def isImage(r: RequestHeader): Boolean = {
    r.headers.get("Content-Type").exists(_.startsWith("image/"))
  }
}
