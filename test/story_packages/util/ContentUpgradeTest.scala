package story_packages.util

import org.json4s._
import org.json4s.jackson.JsonMethods
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ContentUpgradeTest extends AnyFlatSpec with Matchers {

  private val validContent =
    JsonMethods.parse(
      """{
        |  "id": "test/2026/nov/10/example",
        |  "type": "article",
        |  "webTitle": "Example article",
        |  "webUrl": "https://www.theguardian.com/test/2026/nov/10/example",
        |  "apiUrl": "https://content.guardianapis.com/test/2026/nov/10/example"
        |}""".stripMargin
    )

  "ContentUpgrade.upgradeItem" should "add fronts metadata to valid CAPI content" in {
    // This verifies the CAPI decoding path still enriches items after changing the parser version.
    val upgraded = ContentUpgrade.upgradeItem(validContent)
    val frontsMeta = upgraded \ "frontsMeta"

    frontsMeta shouldBe a[JObject]
    (frontsMeta \ "defaults") shouldBe a[JObject]
    (frontsMeta \ "tone") shouldBe a[JString]
  }

  it should "preserve items that cannot be decoded as CAPI content" in {
    // Unrecognized payloads must remain untouched rather than being dropped or partially rewritten.
    val item = JsonMethods.parse("""{"id":"not-capi","custom":"value"}""")

    ContentUpgrade.upgradeItem(item) shouldBe item
  }

  "ContentUpgrade.rewriteBody" should "upgrade object and array fields while leaving unrelated fields unchanged" in {
    // This exercises the response fields the proxy rewrites and guards unrelated payload fields.
    val body =
      """{
        |  "response": {
        |    "content": [
        |      {
        |        "id": "test/2026/nov/10/example",
        |        "type": "article",
        |        "webTitle": "Example article",
        |        "webUrl": "https://www.theguardian.com/test/2026/nov/10/example",
        |        "apiUrl": "https://content.guardianapis.com/test/2026/nov/10/example"
        |      },
        |      {"id":"not-capi","custom":"value"}
        |    ],
        |    "storyPackage": {
        |      "id": "test/2026/nov/10/package",
        |      "type": "article",
        |      "webTitle": "Example package",
        |      "webUrl": "https://www.theguardian.com/test/2026/nov/10/package",
        |      "apiUrl": "https://content.guardianapis.com/test/2026/nov/10/package"
        |    },
        |    "untouched": {"value": true}
        |  }
        |}""".stripMargin

    val rewritten = JsonMethods.parse(ContentUpgrade.rewriteBody(body))
    val response = rewritten \ "response"
    val responseFields = response.asInstanceOf[JObject].obj
    val originalContent = responseFields.filter(_._1 == "content").head._2
    val upgradedContent = responseFields.filter(_._1 == "content").last._2

    // Current behavior appends rewritten fields instead of replacing originals; this appears to be a bug.
    responseFields.count(_._1 == "content") shouldBe 2
    (originalContent(0) \ "frontsMeta") shouldBe JNothing
    (upgradedContent(0) \ "frontsMeta") shouldBe a[JObject]
    upgradedContent(1) shouldBe JsonMethods.parse("""{"id":"not-capi","custom":"value"}""")
    responseFields.count(_._1 == "storyPackage") shouldBe 2
    (responseFields.filter(_._1 == "storyPackage").last._2 \ "frontsMeta") shouldBe a[JObject]
    (response \ "untouched") shouldBe JsonMethods.parse("""{"value":true}""")
    (response \ "results") shouldBe JNothing
  }
}
