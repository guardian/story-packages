package conf

import play.api.mvc.ResponseHeader
import play.filters.gzip.GzipFilter

class CustomGzipFilter extends GzipFilter(shouldGzip = (_, resp) => !Responses.isImage(resp))

object Responses {
  def isImage(r: ResponseHeader): Boolean = {
    r.headers.get("Content-Type").exists(_.startsWith("image/"))
  }
}
