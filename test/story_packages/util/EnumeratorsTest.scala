package story_packages.util

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.iteratee.Iteratee
import story_packages.util.Enumerators._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnumeratorsTest extends AnyFlatSpec with Matchers with ScalaFutures {
  "enumerate" should "simply enumerate the list if the function applied lifts the value into a Future" in {
    enumerate(List(1, 2, 3))(Future.successful).run(Iteratee.getChunks).futureValue should equal(List(
      1, 2, 3
    ))
  }

  it should "transform the enumerator with the given function" in {
    enumerate(List(1, 2, 3)) { n =>
      Future {
        n * n
      }
    }.run(Iteratee.getChunks).futureValue should equal(List(1, 4, 9))
  }
}
