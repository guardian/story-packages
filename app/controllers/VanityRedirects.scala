package controllers

import java.net.URLEncoder

import auth.PanDomainAuthActions
import model.NoCache
import play.mvc.Controller

object VanityRedirects extends Controller with PanDomainAuthActions {

  def storyPackage(id: String) = AuthAction { request => {
    NoCache(Redirect(s"/editorial?layout=latest,content:$id,packages", 301))}
  }

  def addTrail(id: String) = AuthAction { request => {
    NoCache(Redirect(s"/editorial?layout=latest,content,packages:create&q=${URLEncoder.encode(id, "utf-8")}", 301))
  }}
}
