package story_packages.auth

import com.gu.pandomainauth.action.AuthActions
import com.gu.pandomainauth.model.AuthenticatedUser
import com.gu.pandomainauth.PanDomain
import com.gu.permissions.{PermissionDefinition, PermissionsProvider}
import play.api.mvc._
import conf.ApplicationConfiguration
import story_packages.services.Logging

trait PanDomainAuthActions extends AuthActions with Results with Logging {
  def config: ApplicationConfiguration

  val permissions = PermissionsProvider(config.permissions)

  val StoryPackagesAccess = PermissionDefinition("story_packages_access", "story-packages")

  override def validateUser(authedUser: AuthenticatedUser): Boolean = {
    if (!permissions.hasPermission(StoryPackagesAccess, authedUser.user.email)) {
      Logger.warn(s"User ${authedUser.user.email} does not have ${StoryPackagesAccess.name} permission")
      false
    } else {
      PanDomain.guardianValidation(authedUser)
    }
  }

  override def authCallbackUrl: String = config.pandomain.host  + "/oauthCallback"

  override def showUnauthedMessage(message: String)(implicit request: RequestHeader): Result = {
    Logger.info(message)
    Ok(views.html.auth.login(Some(message)))
  }

  override def invalidUserMessage(claimedAuth: AuthenticatedUser): String = {
    if( (claimedAuth.user.emailDomain == "guardian.co.uk") && !claimedAuth.multiFactor) {
      s"${claimedAuth.user.email} is not valid for use with the Story Packages tool as you need to have two factor authentication enabled." +
       s" Please contact the Helpdesk by emailing 34444@theguardian.com or calling 34444 and request assistance setting up two factor authentication on your Google account."
    } else if (claimedAuth.user.emailDomain != "guardian.co.uk") {
      s"${claimedAuth.user.email} is not valid for use with the Fronts Tool. You need to use your Guardian Google account to login. Please sign in with your Guardian Google account first, then retry logging in."
    } else {
      s"${claimedAuth.user.email} has not been granted access to the Story Packages tool. Please contact Central Production at central.production@guardian.co.uk requesting access to the Story Packages tool."
    }
  }
}
