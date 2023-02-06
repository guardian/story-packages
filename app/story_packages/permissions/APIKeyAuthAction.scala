package story_packages.permissions

import play.api.mvc.{ActionBuilder, AnyContent, Request, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.mvc.Results.Forbidden
import conf.ApplicationConfiguration

class APIKeyAuthAction(config: ApplicationConfiguration) extends ActionBuilder[Request, AnyContent] {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    request.queryString.getOrElse("api-key", Nil) match {
      case Seq(config.reindex.key) => block(request)
      case _ => Future(Forbidden("Missing or invalid api-key"))
    }
  }
}
