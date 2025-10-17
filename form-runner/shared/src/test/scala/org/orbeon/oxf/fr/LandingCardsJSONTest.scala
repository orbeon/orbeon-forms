package org.orbeon.oxf.fr

import org.scalatest.funspec.AnyFunSpecLike


class LandingCardsJSONTest extends AnyFunSpecLike {

  import LandingCard.*
  import LocalizedString.*

  describe("Parsing LandingCardsJSON") {

    val json = """
      [
        {
          "card-type": "quick-links"
        },
        {
          "card-type": "published-forms",
          "title": {
            "en": "My demo forms",
            "fr": "Mes formulaires de démonstration"
          },
          "description": {
            "en": "A collection of forms demonstrating various features of Orbeon Forms.",
            "fr": "Une collection de formulaires démontrant diverses fonctionnalités d'''Orbeon Forms."
          },
          "thumbnail": "my-thumbnail.svg",
          "app": "acme-demo"
        },
        {
          "card-type": "published-forms",
          "title": "landing.titles.demo-forms",
          "description": "landing.descriptions.demo-forms",
          "thumbnail": "/apps/fr/style/images/orbeon/sports-car.svg",
          "app": "orbeon"
        },
        {
          "card-type": "published-forms",
          "title": "landing.titles.demo-features",
          "description": "landing.descriptions.demo-features",
          "thumbnail": "/apps/fr/style/images/orbeon/checkboxes.svg",
          "app": "orbeon-features"
        },
        {
          "card-type": "published-forms",
          "title": "landing.titles.published-forms",
          "description": "landing.descriptions.published-forms",
          "thumbnail": "/apps/fr/style/images/orbeon/book.svg"
        },
        {
          "card-type": "form-data",
          "title": "landing.titles.form-builder-forms",
          "description": "landing.description.form-builder-forms",
          "thumbnail": "/apps/fr/style/images/orbeon/library.svg",
          "app": "orbeon",
          "form": "builder",
          "version": 1
        },
        {
          "card-type": "form-data",
          "app": "orbeon",
          "form": "bookshelf",
          "version": 1
        }
      ]
    """

    val expected: List[LandingCard] =
      List(
        QuickLinks,
        PublishedForms(
          title = ByLanguage(Map("en" -> "My demo forms", "fr" -> "Mes formulaires de démonstration")),
          description = ByLanguage(Map("en" -> "A collection of forms demonstrating various features of Orbeon Forms.", "fr" -> "Une collection de formulaires démontrant diverses fonctionnalités d'''Orbeon Forms.")),
          thumbnail = "my-thumbnail.svg",
          app = Some("acme-demo")
        ),
        PublishedForms(
          title = ByResource("landing.titles.demo-forms"),
          description = ByResource("landing.descriptions.demo-forms"),
          thumbnail = "/apps/fr/style/images/orbeon/sports-car.svg",
          app = Some("orbeon")
        ),
        PublishedForms(
          title = ByResource("landing.titles.demo-features"),
          description = ByResource("landing.descriptions.demo-features"),
          thumbnail = "/apps/fr/style/images/orbeon/checkboxes.svg",
          app = Some("orbeon-features")
        ),
        PublishedForms(
          title = ByResource("landing.titles.published-forms"),
          description = ByResource("landing.descriptions.published-forms"),
          thumbnail = "/apps/fr/style/images/orbeon/book.svg",
          app = None
        ),
        FormData(
          title = Some(ByResource("landing.titles.form-builder-forms")),
          description = Some(ByResource("landing.description.form-builder-forms")),
          thumbnail = Some("/apps/fr/style/images/orbeon/library.svg"),
          app = "orbeon",
          form = "builder",
          version = Some(1)
        ),
        FormData(
          title = None,
          description = None,
          thumbnail = None,
          app = "orbeon",
          form = "bookshelf",
          version = Some(1)
        )
      )

    it("must parse the json correctly") {
      assert(LandingCardsJSON.parseString(json).toOption.flatten.map(_._1).contains(expected))
    }

    it("must fail with invalid json") {
      val invalidJson = "{ invalid }"
      assert(LandingCardsJSON.parseString(invalidJson).isFailure)
    }

    it("must fail with an unknown card type") {
      val invalidJson = """[{"card-type": "unknown"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must fail if required field is missing") {
      val invalidJson = """[{"card-type": "published-forms"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must serialize correctly") {
      val jsonFromJsonString  = LandingCardsJSON.parseString(json).toOption.flatten.get._2
      val jsonFromCaseClasses = LandingCardsJSON.serializeToJson(expected)
      assert(jsonFromJsonString == jsonFromCaseClasses)
    }
  }
}
