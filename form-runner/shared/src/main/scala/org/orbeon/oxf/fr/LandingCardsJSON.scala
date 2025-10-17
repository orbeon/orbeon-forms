package org.orbeon.oxf.fr

import io.circe.parser.parse
import io.circe.{Json, JsonObject}
import org.orbeon.oxf.json.Converter
import org.orbeon.oxf.util.StringUtils.*

import scala.util.{Success, Try}


sealed trait LandingCard

object LandingCard {
  case object QuickLinks extends LandingCard

  case class PublishedForms(
    title      : LocalizedString,
    description: LocalizedString,
    thumbnail  : String,
    app        : Option[String]
  ) extends LandingCard

  case class FormData(
    title      : Option[LocalizedString],
    description: Option[LocalizedString],
    thumbnail  : Option[String],
    app        : String,
    form       : String,
    version    : Option[Int]
  ) extends LandingCard
}

sealed trait LocalizedString
object LocalizedString {
  case class ByLanguage(values: Map[String, String]) extends LocalizedString
  case class ByResource(key: String)                 extends LocalizedString
}

object LandingCardsJSON {

  import LandingCard.*

  def parseString(jsonString: String): Try[Option[(Seq[LandingCard], Json)]] =
    jsonString.trimAllToOpt match {
      case Some(trimmedJsonString) =>
        parse(trimmedJsonString).toTry.flatMap { json =>
          Try {
            Some(json.asArray.get.map(parseCard) -> json)
          }
        }
      case None =>
        Success(None)
    }

  def serializeToJson(landingCards: Iterable[LandingCard]): Json =
    Json.fromValues(
      landingCards.map {
        case QuickLinks =>
          Json.obj(
            "card-type" -> Json.fromString("quick-links")
          )
        case PublishedForms(title, description, thumbnail, app) =>
          Json.obj(
            "card-type"  -> Json.fromString("published-forms"),
            "title"      -> serializeLocalizedString(title),
            "description"-> serializeLocalizedString(description),
            "thumbnail"  -> Json.fromString(thumbnail)
          ) deepMerge
          app.map(a => Json.obj("app" -> Json.fromString(a))).getOrElse(Json.obj())
        case FormData(title, description, thumbnail, app, form, version) =>
          Json.obj(
            "card-type"  -> Json.fromString("form-data"),
            "app"        -> Json.fromString(app),
            "form"       -> Json.fromString(form)
          ) deepMerge
          title.map(t => Json.obj("title" -> serializeLocalizedString(t))).getOrElse(Json.obj()) deepMerge
          description.map(d => Json.obj("description" -> serializeLocalizedString(d))).getOrElse(Json.obj()) deepMerge
          thumbnail.map(t => Json.obj("thumbnail" -> Json.fromString(t))).getOrElse(Json.obj()) deepMerge
          version.map(v => Json.obj("version" -> Json.fromInt(v))).getOrElse(Json.obj())
      }
    )

  private val AppFormVersionRegex = """([A-Za-z0-9\-_]+)/([A-Za-z0-9\-_]+)/(\d+)""".r

  def legacyTokensToCaseClasses(v: String): List[LandingCard] =
    v.splitTo[List]().flatMap {
      case "quick-links"          =>
        List(LandingCard.QuickLinks)
      case "demo-forms"           =>
        List(
          LandingCard.PublishedForms(
            title       = LocalizedString.ByResource("landing.titles.demo-forms"),
            description = LocalizedString.ByResource("landing.descriptions.demo-forms"),
            thumbnail   = "/apps/fr/style/images/orbeon/sports-car.svg",
            app         = Some("orbeon")
          ),
          LandingCard.PublishedForms(
            title       = LocalizedString.ByResource("landing.titles.demo-features"),
            description = LocalizedString.ByResource("landing.descriptions.demo-features"),
            thumbnail   = "/apps/fr/style/images/orbeon/checkboxes.svg",
            app         = Some("orbeon-features")
          )
        )
      case "published-forms"      =>
        List(
          LandingCard.PublishedForms(
            title       = LocalizedString.ByResource("landing.titles.published-forms"),
            description = LocalizedString.ByResource("landing.descriptions.published-forms"),
            thumbnail   = "/apps/fr/style/images/orbeon/book.svg",
            app         = None
          )
        )
      case "form-builder-forms"   =>
        List(
          LandingCard.FormData(
            title       = Some(LocalizedString.ByResource("landing.titles.form-builder-forms")),
            description = Some(LocalizedString.ByResource("landing.description.form-builder-forms")),
            thumbnail   = Some("/apps/fr/style/images/orbeon/library.svg"),
            app         = "orbeon",
            form        = "builder",
            version     = Some(1)
          )
        )
      case AppFormVersionRegex(app, form, version) =>
        List(
          LandingCard.FormData(
            title       = None,
            description = None,
            thumbnail   = None,
            app         = app,
            form        = form,
            version     = Some(version.toInt)
          )
        )
      case other =>
        throw new IllegalArgumentException(other)
    }

  private def serializeLocalizedString(ls: LocalizedString): Json =
    ls match {
      case LocalizedString.ByLanguage(values) =>
        Json.obj(values.view.mapValues(Json.fromString).toSeq*)
      case LocalizedString.ByResource(key) =>
        Json.fromString(key)
    }


  private def error(message: String): Nothing =
    throw new IllegalArgumentException(message)

  private def parseCard(json: Json): LandingCard = {
    val o = json.asObject.getOrElse(error("Expected a card object"))
    o("card-type").flatMap(_.asString).getOrElse(error("Missing card-type")) match {
      case "quick-links"     => parseQuickLinks(o)
      case "published-forms" => parsePublishedForms(o)
      case "form-data"       => parseFormData(o)
      case other             => error(s"Unknown card type: $other")
    }
  }

  private def parseQuickLinks(o: JsonObject): QuickLinks.type =
    QuickLinks

  private def parsePublishedForms(o: JsonObject): PublishedForms =
    PublishedForms(
      title       = o("title").map(parseLocalizedString).getOrElse(error("Missing `title` for `published-forms`")),
      description = o("description").map(parseLocalizedString).getOrElse(error("Missing `description` for `published-forms`")),
      thumbnail   = o("thumbnail").flatMap(_.asString).getOrElse(error("Missing `thumbnail` for `published-forms`")),
      app         = o("app").flatMap(_.asString)
    )

  private def parseFormData(o: JsonObject): FormData =
    FormData(
      title       = o("title").map(parseLocalizedString),
      description = o("description").map(parseLocalizedString),
      thumbnail   = o("thumbnail").flatMap(_.asString),
      app         = o("app").flatMap(_.asString).getOrElse(error("Missing `app` for `form-data`")),
      form        = o("form").flatMap(_.asString).getOrElse(error("Missing `form` for `form-data`")),
      version     = o("version").flatMap(_.asNumber).flatMap(_.toInt)
    )

  private def parseLocalizedString(json: Json): LocalizedString = {
    json.asObject match {
      case Some(o) if o.isEmpty =>
        error("`LocalizedString` object cannot be empty")
      case Some(o) =>
        LocalizedString.ByLanguage(
          o.toMap.map { case (k, v) => k -> v.asString.getOrElse(error(s"Expected string value for language `$k`")) }
        )
      case None =>
        json.asString match {
          case Some(key) =>
            LocalizedString.ByResource(key)
          case None =>
            error("Expected a `LocalizedString` object")
        }
    }
  }
}