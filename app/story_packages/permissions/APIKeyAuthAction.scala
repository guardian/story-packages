package story_packages.permissions

import play.api.mvc.{ActionBuilder, BodyParser, BodyParsers, Request, Result}

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Results.Forbidden
import conf.ApplicationConfiguration

class APIKeyAuthAction(config: ApplicationConfiguration)(implicit val executionContext: ExecutionContext) extends ActionBuilder[Request, Unit] {
  override def parser: BodyParser[Unit] = BodyParsers.utils.empty
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    request.queryString.getOrElse("api-key", Nil) match {
      case Seq(config.reindex.key) => block(request)
      case _ => Future(Forbidden("Missing or invalid api-key"))
    }
  }
}
