package controllers

import play.api.mvc.Controller

class UncachedAssets extends Controller {
  def at(file: String, relativePath: String = "") = model.NoCache {
    controllers.Assets.at(path= "/public/src", relativePath + file)
  }
}
