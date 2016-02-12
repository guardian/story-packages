package permissions

import conf.Configuration
import controllers.StoryPackagesController._
import play.api.mvc.{Result, Request, ActionBuilder}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object APIKeyAuthAction extends ActionBuilder[Request] {

  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {

    request.queryString.getOrElse("api-key", Nil) match {
      case Seq(apiKey) => {
        if (Configuration.reindex.key == apiKey)
          block(request)
        else
          Future(Forbidden)
      }
      case _ => Future(Forbidden)
    }
  }
}
