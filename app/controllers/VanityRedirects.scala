package controllers

import auth.PanDomainAuthActions
import model.NoCache
import play.mvc.Controller

object VanityRedirects extends Controller with PanDomainAuthActions {

  def storyPackage(id: String) = (AuthAction) { request => {
    NoCache(Redirect(s"/editorial?layout=latest,front:$id,packages", 301))}
  }
}

