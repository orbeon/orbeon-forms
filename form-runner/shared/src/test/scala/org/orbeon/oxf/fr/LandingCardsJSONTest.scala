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
          "app": "orbeon",
          "link-to": "new",
          "sort-by": "title"
        },
        {
          "card-type": "published-forms",
          "title": "landing.titles.demo-features",
          "description": "landing.descriptions.demo-features",
          "thumbnail": "/apps/fr/style/images/orbeon/checkboxes.svg",
          "app": "orbeon-features",
          "link-to": "summary",
          "sort-by": "last-modified"
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
          title       = ByLanguage(Map("en" -> "My demo forms", "fr" -> "Mes formulaires de démonstration")),
          description = ByLanguage(Map("en" -> "A collection of forms demonstrating various features of Orbeon Forms.", "fr" -> "Une collection de formulaires démontrant diverses fonctionnalités d'''Orbeon Forms.")),
          thumbnail   = "my-thumbnail.svg",
          app         = Some("acme-demo"),
          linkTo      = Nil,
          sort        = None
        ),
        PublishedForms(
          title       = ByResource("landing.titles.demo-forms"),
          description = ByResource("landing.descriptions.demo-forms"),
          thumbnail   = "/apps/fr/style/images/orbeon/sports-car.svg",
          app         = Some("orbeon"),
          linkTo      = List(LinkTo.New),
          sort        = Some((SortBy.Title, None))
        ),
        PublishedForms(
          title       = ByResource("landing.titles.demo-features"),
          description = ByResource("landing.descriptions.demo-features"),
          thumbnail   = "/apps/fr/style/images/orbeon/checkboxes.svg",
          app         = Some("orbeon-features"),
          linkTo      = List(LinkTo.Summary),
          sort        = Some((SortBy.LastModified, None))
        ),
        PublishedForms(
          title       = ByResource("landing.titles.published-forms"),
          description = ByResource("landing.descriptions.published-forms"),
          thumbnail   = "/apps/fr/style/images/orbeon/book.svg",
          app         = None,
          linkTo      = Nil,
          sort        = None
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

    it("must fail with an unknown link-to") {
      val invalidJson1 = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "unknown"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson1).get)
      val invalidJson2 = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "new unknown"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson2).get)
    }

    it("must fail if link-to is not a string") {
      val invalidJson = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": 123}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must parse and serialize link-to tokens correctly") {
      val json =
        """[
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": null},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": ""},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "   "},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "new"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "summary"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "new summary"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "link-to": "summary new"}
          |]""".stripMargin

      val cards = LandingCardsJSON.parseString(json).get.get._1.collect { case p: PublishedForms => p }
      assert(cards.map(_.linkTo) == List(
        Nil,
        Nil,
        Nil,
        Nil,
        List(LinkTo.New),
        List(LinkTo.Summary),
        List(LinkTo.New, LinkTo.Summary),
        List(LinkTo.Summary, LinkTo.New)
      ))

      val serialized = LandingCardsJSON.serializeToJson(cards)
      val reparsed = LandingCardsJSON.parseString(serialized.noSpaces).get.get._1.collect { case p: PublishedForms => p }
      assert(reparsed == cards)
    }

    it("must fail with an unknown sort-by") {
      val invalidJson = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "unknown"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must fail if sort-by is not a string") {
      val invalidJson = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": 123}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must parse explicit sort-direction correctly") {
      val json =
        """[
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "title",            "sort-direction": "descending"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "last-modified",    "sort-direction": "ascending"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "created",          "sort-direction": "asc"},
          |  {"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "application-name", "sort-direction": "desc"}
          |]""".stripMargin
      val cards = LandingCardsJSON.parseString(json).get.get._1.collect { case p: PublishedForms => p }
      assert(cards.map(_.sort) == List(
        Some((SortBy.Title, Some(SortDirection.Descending))),
        Some((SortBy.LastModified, Some(SortDirection.Ascending))),
        Some((SortBy.Created, Some(SortDirection.Ascending))),
        Some((SortBy.AppName, Some(SortDirection.Descending)))
      ))
    }

    it("must fail with an unknown sort-direction") {
      val invalidJson = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "title", "sort-direction": "sideways"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must fail if sort-direction is not a string") {
      val invalidJson = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-by": "title", "sort-direction": 456}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must fail if sort-direction is present without sort-by") {
      val invalidJson = """[{"card-type": "published-forms", "title": "t", "description": "d", "thumbnail": "th", "sort-direction": "ascending"}]"""
      assertThrows[IllegalArgumentException](LandingCardsJSON.parseString(invalidJson).get)
    }

    it("must parse legacy tokens correctly") {
      val legacy = LandingCardsJSON.legacyTokensToCaseClasses("demo-forms published-forms")
      assert(legacy.collect { case p: PublishedForms => p.linkTo } == List(
        List(LinkTo.Summary),
        List(LinkTo.New),
        List(LinkTo.Summary)
      ))
    }

    it("must serialize correctly") {
      val jsonFromJsonString  = LandingCardsJSON.parseString(json).toOption.flatten.get._2
      val jsonFromCaseClasses = LandingCardsJSON.serializeToJson(expected)
      assert(jsonFromJsonString == jsonFromCaseClasses)

      // Test serialization with explicit sort-direction
      val cardWithDirection = PublishedForms(
        title = ByResource("t"), description = ByResource("d"), thumbnail = "th", app = None, linkTo = Nil,
        sort = Some((SortBy.Title, Some(SortDirection.Descending)))
      )
      val serialized = LandingCardsJSON.serializeToJson(List(cardWithDirection))
      val reparsed = LandingCardsJSON.parseString(serialized.noSpaces).get.get._1
      assert(reparsed == List(cardWithDirection))
    }
  }
}
