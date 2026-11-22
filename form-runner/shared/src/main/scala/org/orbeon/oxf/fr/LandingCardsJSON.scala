package org.orbeon.oxf.fr

import io.circe.parser.parse
import io.circe.{Json, JsonObject}
import org.orbeon.oxf.util.StringUtils.*

import scala.util.{Success, Try}


sealed trait LandingCard

object LandingCard {
  case class QuickLinks(
    title      : Option[LocalizedString]  = None,
    description: Option[LocalizedString]  = None,
    links      : List[QuickLinkToken]     = Nil,
    layout     : Option[QuickLinksLayout] = None
  ) extends LandingCard

  case class PublishedForms(
    title      : LocalizedString,
    description: Option[LocalizedString]               = None,
    thumbnail  : Option[String]                        = None,
    app        : Option[String]                        = None,
    linkTo     : List[LinkTo]                          = Nil,
    sort       : Option[(SortBy, Option[SortDirection])] = None
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

sealed trait QuickLinkToken
object QuickLinkToken {
  case object Main               extends QuickLinkToken
  case object Admin              extends QuickLinkToken
  case object All                extends QuickLinkToken
  case object FormBuilderNew     extends QuickLinkToken
  case object FormBuilderSummary extends QuickLinkToken
  case object PublishedForms     extends QuickLinkToken
  case object FormsAdmin         extends QuickLinkToken
  case object Configuration      extends QuickLinkToken
  case object AdminConsole       extends QuickLinkToken
  case object FormsAnalytics     extends QuickLinkToken
}

sealed trait QuickLinksLayout
object QuickLinksLayout {
  case object Tiles extends QuickLinksLayout
  case object List  extends QuickLinksLayout
}

sealed trait LinkTo
object LinkTo {
  case object New     extends LinkTo
  case object Summary extends LinkTo
}

sealed trait SortBy
object SortBy {
  case object AppName        extends SortBy
  case object FormName       extends SortBy
  case object FormVersion    extends SortBy
  case object Created        extends SortBy
  case object LastModified   extends SortBy
  case object LastModifiedBy extends SortBy
  case object Title          extends SortBy
}

sealed trait SortDirection
object SortDirection {
  case object Ascending  extends SortDirection
  case object Descending extends SortDirection
}

sealed trait LocalizedString
object LocalizedString {
  case class ByLanguage(values: Map[String, String]) extends LocalizedString
  case class ByResource(key: String)                 extends LocalizedString
}

object LandingCardsJSON {

  import LandingCard.*
  import Private.*

  val BuiltInThumbnails: List[String] = List(
    "/apps/fr/style/images/orbeon/book.svg",
    "/apps/fr/style/images/orbeon/sports-car.svg",
    "/apps/fr/style/images/orbeon/checkboxes.svg",
    "/apps/fr/style/images/orbeon/library.svg",
    "/apps/fr/style/images/orbeon/cogwheels.svg",
    "/apps/fr/style/images/orbeon/configuration.svg",
    "/apps/fr/style/images/orbeon/console.svg",
    "/apps/fr/style/images/orbeon/analytics.svg",
    "/apps/fr/style/images/orbeon/crane.svg"
  )

  val DefaultCards: List[LandingCard] = List(
    QuickLinks(
      links = List(QuickLinkToken.Main)
    ),
    QuickLinks(
      title = Some(LocalizedString.ByResource("landing.titles.admin")),
      links = List(QuickLinkToken.Admin)
    ),
    PublishedForms(
      title       = LocalizedString.ByResource("landing.titles.demo-forms"),
      description = Some(LocalizedString.ByResource("landing.descriptions.demo-forms")),
      thumbnail   = Some("/apps/fr/style/images/orbeon/sports-car.svg"),
      app         = Some("orbeon"),
      sort        = Some((SortBy.Title, None))
    ),
    PublishedForms(
      title       = LocalizedString.ByResource("landing.titles.demo-features"),
      description = Some(LocalizedString.ByResource("landing.descriptions.demo-features")),
      thumbnail   = Some("/apps/fr/style/images/orbeon/checkboxes.svg"),
      app         = Some("orbeon-features"),
      linkTo      = List(LinkTo.New),
      sort        = Some((SortBy.Title, None))
    ),
    PublishedForms(
      title       = LocalizedString.ByResource("landing.titles.published-forms"),
      description = Some(LocalizedString.ByResource("landing.descriptions.published-forms")),
      thumbnail   = Some("/apps/fr/style/images/orbeon/book.svg"),
      linkTo      = List(LinkTo.Summary, LinkTo.New),
      sort        = Some((SortBy.LastModified, None))
    ),
    FormData(
      title       = Some(LocalizedString.ByResource("landing.titles.form-builder-forms")),
      description = Some(LocalizedString.ByResource("landing.descriptions.form-builder-forms")),
      thumbnail   = Some("/apps/fr/style/images/orbeon/library.svg"),
      app         = "orbeon",
      form        = "builder",
      version     = Some(1)
    ),
    FormData(
      title       = None,
      description = None,
      thumbnail   = None,
      app         = "orbeon",
      form        = "bookshelf",
      version     = Some(1)
    )
  )

  def parseValue(rawValue: String): Try[Option[Seq[LandingCard]]] =
    rawValue.trimAllToOpt match {
      case None =>
        Success(None)
      case Some(v) if v.startsWith("[") =>
        parseString(v).map(_.map(_._1))
      case Some(v) =>
        Try(Some(legacyTokensToCaseClasses(v)))
    }

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
        case QuickLinks(title, description, links, layout) =>
          Json.obj(
            "card-type" -> Json.fromString("quick-links")
          )
            .deepMerge(title      .map(t => Json.obj("title"       -> serializeLocalizedString(t))) .getOrElse(Json.obj()))
            .deepMerge(description.map(d => Json.obj("description" -> serializeLocalizedString(d))) .getOrElse(Json.obj()))
            .deepMerge(if (links.nonEmpty) Json.obj("links" -> serializeQuickLinks(links))          else Json.obj())
            .deepMerge(layout     .map(l => Json.obj("layout"      -> serializeQuickLinksLayout(l))).getOrElse(Json.obj()))
        case PublishedForms(title, description, thumbnail, app, linkTo, sort) =>
          Json.obj(
            "card-type" -> Json.fromString("published-forms"),
            "title"     -> serializeLocalizedString(title)
          )
            .deepMerge(description.map(d => Json.obj("description" -> serializeLocalizedString(d))).getOrElse(Json.obj()))
            .deepMerge(thumbnail  .map(t => Json.obj("thumbnail"   -> Json.fromString(t)))         .getOrElse(Json.obj()))
            .deepMerge(app        .map(a => Json.obj("app"         -> Json.fromString(a)))         .getOrElse(Json.obj()))
            .deepMerge(if (linkTo.nonEmpty) Json.obj("link-to" -> serializeLinkTo(linkTo)) else Json.obj())
            .deepMerge(
              sort.map { case (sortBy, sortDirection) =>
                Json.obj("sort-by" -> serializeSortBy(sortBy))
                  .deepMerge(sortDirection.map(d => Json.obj("sort-direction" -> serializeSortDirection(d))).getOrElse(Json.obj()))
              }
              .getOrElse(Json.obj())
            )
        case FormData(title, description, thumbnail, app, form, version) =>
          Json.obj(
            "card-type"  -> Json.fromString("form-data"),
            "app"        -> Json.fromString(app),
            "form"       -> Json.fromString(form)
          )
            .deepMerge(title      .map(t => Json.obj("title"       -> serializeLocalizedString(t))).getOrElse(Json.obj()))
            .deepMerge(description.map(d => Json.obj("description" -> serializeLocalizedString(d))).getOrElse(Json.obj()))
            .deepMerge(thumbnail  .map(t => Json.obj("thumbnail"   -> Json.fromString(t)))         .getOrElse(Json.obj()))
            .deepMerge(version    .map(v => Json.obj("version"     -> Json.fromInt(v)))            .getOrElse(Json.obj()))
      }
    )

  def legacyTokensToCaseClasses(v: String): List[LandingCard] =
    v.splitTo[List]().flatMap {
      case "quick-links"          =>
        List(LandingCard.QuickLinks())
      case "admin"                =>
        List(
          LandingCard.QuickLinks(
            title = Some(LocalizedString.ByResource("landing.titles.admin")),
            links = List(QuickLinkToken.Admin)
          )
        )
      case "demo-forms"           =>
        List(
          LandingCard.PublishedForms(
            title       = LocalizedString.ByResource("landing.titles.demo-forms"),
            description = Some(LocalizedString.ByResource("landing.descriptions.demo-forms")),
            thumbnail   = Some("/apps/fr/style/images/orbeon/sports-car.svg"),
            app         = Some("orbeon"),
            linkTo      = List(LinkTo.Summary),
            sort        = Some((SortBy.Title, Some(SortDirection.Ascending)))
          ),
          LandingCard.PublishedForms(
            title       = LocalizedString.ByResource("landing.titles.demo-features"),
            description = Some(LocalizedString.ByResource("landing.descriptions.demo-features")),
            thumbnail   = Some("/apps/fr/style/images/orbeon/checkboxes.svg"),
            app         = Some("orbeon-features"),
            linkTo      = List(LinkTo.New),
            sort        = Some((SortBy.Title, Some(SortDirection.Ascending)))
          )
        )
      case "published-forms"      =>
        List(
          LandingCard.PublishedForms(
            title       = LocalizedString.ByResource("landing.titles.published-forms"),
            description = Some(LocalizedString.ByResource("landing.descriptions.published-forms")),
            thumbnail   = Some("/apps/fr/style/images/orbeon/book.svg"),
            app         = None,
            linkTo      = List(LinkTo.Summary),
            sort        = Some((SortBy.LastModified, Some(SortDirection.Descending)))
          )
        )
      case "form-builder-forms"   =>
        List(
          LandingCard.FormData(
            title       = Some(LocalizedString.ByResource("landing.titles.form-builder-forms")),
            description = Some(LocalizedString.ByResource("landing.descriptions.form-builder-forms")),
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

  private object Private {

    val AppFormVersionRegex = """([A-Za-z0-9\-_]+)/([A-Za-z0-9\-_]+)/(\d+)""".r

    def serializeLocalizedString(ls: LocalizedString): Json =
      ls match {
        case LocalizedString.ByLanguage(values) =>
          Json.obj(values.view.mapValues(Json.fromString).toSeq*)
        case LocalizedString.ByResource(key) =>
          Json.fromString(key)
      }

    def serializeQuickLinks(links: List[QuickLinkToken]): Json =
      Json.fromString(
        links.map {
          case QuickLinkToken.Main               => "main"
          case QuickLinkToken.Admin              => "admin"
          case QuickLinkToken.All                => "all"
          case QuickLinkToken.FormBuilderNew     => "form-builder-new"
          case QuickLinkToken.FormBuilderSummary => "form-builder-summary"
          case QuickLinkToken.PublishedForms     => "published-forms"
          case QuickLinkToken.FormsAdmin         => "forms-admin"
          case QuickLinkToken.Configuration      => "configuration"
          case QuickLinkToken.AdminConsole       => "admin-console"
          case QuickLinkToken.FormsAnalytics     => "forms-analytics"
        }.mkString(" ")
      )

    def serializeQuickLinksLayout(layout: QuickLinksLayout): Json =
      Json.fromString(
        layout match {
          case QuickLinksLayout.Tiles => "tiles"
          case QuickLinksLayout.List  => "list"
        }
      )

    def serializeLinkTo(linkTo: List[LinkTo]): Json =
      Json.fromString(
        linkTo.map {
          case LinkTo.New     => "new"
          case LinkTo.Summary => "summary"
        }.mkString(" ")
      )

    def serializeSortBy(sortBy: SortBy): Json =
      Json.fromString(
        sortBy match {
          case SortBy.AppName        => "application-name"
          case SortBy.FormName       => "form-name"
          case SortBy.FormVersion    => "form-version"
          case SortBy.Created        => "created"
          case SortBy.LastModified   => "last-modified"
          case SortBy.LastModifiedBy => "last-modified-by"
          case SortBy.Title          => "title"
        }
      )

    def serializeSortDirection(sortDirection: SortDirection): Json =
      Json.fromString(
        sortDirection match {
          case SortDirection.Ascending  => "ascending"
          case SortDirection.Descending => "descending"
        }
      )

    def error(message: String): Nothing =
      throw new IllegalArgumentException(message)

    def parseCard(json: Json): LandingCard = {
      val o = json.asObject.getOrElse(error("Expected a card object"))
      o("card-type").flatMap(_.asString).getOrElse(error("Missing card-type")) match {
        case "quick-links"     => parseQuickLinks(o)
        case "published-forms" => parsePublishedForms(o)
        case "form-data"       => parseFormData(o)
        case other             => error(s"Unknown card type: $other")
      }
    }

    def parseQuickLinks(o: JsonObject): QuickLinks =
      QuickLinks(
        title       = o("title")      .filterNot(_.isNull).map(parseLocalizedString),
        description = o("description").filterNot(_.isNull).map(parseLocalizedString),
        links       = o("links")      .filterNot(_.isNull).map(parseQuickLinkTokens).getOrElse(Nil),
        layout      = o("layout")     .filterNot(_.isNull).flatMap(parseQuickLinksLayout)
      )

    def parseQuickLinksLayout(json: Json): Option[QuickLinksLayout] =
      json.asString match {
        case Some(s) =>
          s.trimAllToOpt.map {
            case "tiles" => QuickLinksLayout.Tiles
            case "list"  => QuickLinksLayout.List
            case other   => error(s"Unknown quick-links `layout`: $other")
          }
        case None =>
          error("Expected string for `layout`")
      }

    def parseQuickLinkTokens(json: Json): List[QuickLinkToken] =
      json.asString match {
        case Some(s) =>
          s.splitTo[List]().distinct.map(parseQuickLinkToken)
        case None =>
          json.asArray match {
            case Some(arr) =>
              arr.toList
                .flatMap { elem =>
                  elem.asString.getOrElse(error("Expected string in `links` array")).splitTo[List]()
                }
                .distinct
                .map(parseQuickLinkToken)
            case None =>
              error("Expected string or array of strings for `links`")
          }
      }

    def parseQuickLinkToken(token: String): QuickLinkToken =
      token match {
        case "main"                                        => QuickLinkToken.Main
        case "admin"                                       => QuickLinkToken.Admin
        case "all"                                         => QuickLinkToken.All
        case "form-builder-new"                            => QuickLinkToken.FormBuilderNew
        case "form-builder-summary" | "form-builder-forms" => QuickLinkToken.FormBuilderSummary
        case "published-forms"                             => QuickLinkToken.PublishedForms
        case "forms-admin" | "form-runner-admin"           => QuickLinkToken.FormsAdmin
        case "configuration"                               => QuickLinkToken.Configuration
        case "admin-console"                               => QuickLinkToken.AdminConsole
        case "forms-analytics"                             => QuickLinkToken.FormsAnalytics
        case other                                         => error(s"Unknown quick link: $other")
      }

    def parsePublishedForms(o: JsonObject): PublishedForms = {
      val sortByOpt        = o("sort-by").filterNot(_.isNull).map(parseSortBy)
      val sortDirectionOpt = o("sort-direction").filterNot(_.isNull).map(parseSortDirection)

      if (sortByOpt.isEmpty && sortDirectionOpt.isDefined)
        error("Missing `sort-by` when `sort-direction` is present")

      PublishedForms(
        title       = o("title")      .filterNot(_.isNull).map(parseLocalizedString).getOrElse(error("Missing `title` for `published-forms`")),
        description = o("description").filterNot(_.isNull).map(parseLocalizedString),
        thumbnail   = o("thumbnail")  .filterNot(_.isNull).flatMap(j => j.asString.getOrElse(error("Expected string for `thumbnail`")).trimAllToOpt),
        app         = o("app")        .filterNot(_.isNull).flatMap(j => j.asString.getOrElse(error("Expected string for `app`")).trimAllToOpt),
        linkTo      = o("link-to")    .filterNot(_.isNull).map(parseLinkTo).getOrElse(Nil),
        sort        = sortByOpt.map(sortBy => (sortBy, sortDirectionOpt))
      )
    }

    def parseLinkTo(json: Json): List[LinkTo] =
      json.asString match {
        case Some(s) =>
          s.splitTo[List]().distinct.map {
            case "new"     => LinkTo.New
            case "summary" => LinkTo.Summary
            case other     => error(s"Unknown `link-to`: $other")
          }
        case None =>
          error("Expected string for `link-to`")
      }

    def parseSortBy(json: Json): SortBy =
      json.asString match {
        case Some("application-name" | "app-name") => SortBy.AppName
        case Some("form-name")                     => SortBy.FormName
        case Some("form-version")                  => SortBy.FormVersion
        case Some("created")                       => SortBy.Created
        case Some("last-modified")                 => SortBy.LastModified
        case Some("last-modified-by")              => SortBy.LastModifiedBy
        case Some("title")                         => SortBy.Title
        case Some(other)                           => error(s"Unknown `sort-by`: $other")
        case None                                  => error("Expected string for `sort-by`")
      }

    def parseSortDirection(json: Json): SortDirection =
      json.asString match {
        case Some("ascending"  | "asc")  => SortDirection.Ascending
        case Some("descending" | "desc") => SortDirection.Descending
        case Some(other)                 => error(s"Unknown `sort-direction`: $other")
        case None                        => error("Expected string for `sort-direction`")
      }

    def parseFormData(o: JsonObject): FormData =
      FormData(
        title       = o("title")      .filterNot(_.isNull).map(parseLocalizedString),
        description = o("description").filterNot(_.isNull).map(parseLocalizedString),
        thumbnail   = o("thumbnail")  .filterNot(_.isNull).flatMap(j => j.asString.getOrElse(error("Expected string for `thumbnail`")).trimAllToOpt),
        app         = o("app")        .flatMap(_.asString).getOrElse(error("Missing `app` for `form-data`")),
        form        = o("form")       .flatMap(_.asString).getOrElse(error("Missing `form` for `form-data`")),
        version     = o("version")    .filterNot(_.isNull).flatMap(_.asNumber).flatMap(_.toInt)
      )

    def parseLocalizedString(json: Json): LocalizedString = {
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
}
