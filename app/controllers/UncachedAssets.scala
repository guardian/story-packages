package controllers

import play.api.mvc.Controller

class UncachedAssets extends Controller {
  def at(file: String, relativePath: String = "") = story_packages.model.NoCache {
    controllers.Assets.at(path= "/public/src", relativePath + file)
  }
}
