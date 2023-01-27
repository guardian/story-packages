package controllers

import play.api.mvc.{BaseController, ControllerComponents}

class StatusController(val controllerComponents: ControllerComponents) extends BaseController {

  def healthStatus = Action {
    Ok("Ok.")
  }
}
