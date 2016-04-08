package permissions

import conf.ApplicationConfiguration
import play.api.mvc.{ActionBuilder, Request, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.mvc.Results.Forbidden

class APIKeyAuthAction(config: ApplicationConfiguration) extends ActionBuilder[Request] {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    request.queryString.getOrElse("api-key", Nil) match {
      case Seq(config.reindex.key) => block(request)
      case _ => Future(Forbidden("Missing or invalid api-key"))
    }
  }
}
