package story_packages.util

import com.gu.pandomainauth.model.User

object Identity {
  implicit class RichIdentity(user: User) {
    def fullName: String = {
      user.firstName + " " + user.lastName
    }
  }
}
