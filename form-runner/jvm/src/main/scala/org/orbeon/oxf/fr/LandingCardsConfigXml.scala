/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.util.StringUtils.*

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success}


object LandingCardsConfigXml {

  import LandingCard.*
  import Private.*

  val DefaultThumbnail: String = "/apps/fr/style/images/orbeon/book.svg"

  def isValidLandingCards(value: String): Boolean =
    LandingCardsJSON.parseValue(value) match {
      case Success(None)        => true
      case Success(Some(cards)) =>
        cards.forall {
          case QuickLinks(_, _, _, _) =>
            true
          case PublishedForms(title, _, _, appOpt, _, _) =>
            isNonBlankLocalizedString(title) &&
              appOpt.forall(a => AppFormSlugRegex.matches(a.trim))
          case FormData(_, _, _, app, form, versionOpt) =>
            AppFormSlugRegex.matches(app.trim) &&
              AppFormSlugRegex.matches(form.trim) &&
              versionOpt.forall(_ >= 1)
        }
      case Failure(_) =>
        false
    }

  def toEditorDom(rawValue: String, fallbackValue: String): Document = {
    val resolvedCards =
      LandingCardsJSON.parseValue(rawValue).toOption.flatten
        .orElse(LandingCardsJSON.parseValue(fallbackValue).toOption.flatten)
        .getOrElse(LandingCardsJSON.DefaultCards)

    val doc        = Document("editor")
    val editorRoot = doc.getRootElement
    val cardsElem  = editorRoot.addElement("cards")

    resolvedCards.foreach { card =>
      appendCardElement(cardsElem, card, idOpt = None, expanded = false)
    }

    val templatesElem = editorRoot.addElement("templates")

    // Blank templates for "+ Published Forms", "+ Form Data", "+ Quick Links"
    appendCardElement(
      templatesElem,
      PublishedForms(
        title       = LocalizedString.ByLanguage(Map("_" -> "")),
        description = None,
        thumbnail   = Some(DefaultThumbnail),
        app         = None,
        linkTo      = List(LinkTo.Summary, LinkTo.New),
        sort        = Some((SortBy.LastModified, None))
      ),
      idOpt    = Some("new-published-forms"),
      expanded = true
    )

    appendCardElement(
      templatesElem,
      FormData(
        title       = None,
        description = None,
        thumbnail   = None,
        app         = "",
        form        = "",
        version     = Some(1)
      ),
      idOpt    = Some("new-form-data"),
      expanded = true
    )

    appendCardElement(
      templatesElem,
      QuickLinks(
        links = List(QuickLinkToken.Main)
      ),
      idOpt    = Some("new-quick-links"),
      expanded = true
    )

    // One-click built-in presets
    PresetDefinitions.foreach { case (presetId, presetCard) =>
      appendCardElement(templatesElem, presetCard, idOpt = Some(presetId), expanded = false)
    }

    val translationElem = templatesElem.addElement("translation")
    translationElem.addAttribute("lang", "en")

    doc
  }

  def fromEditorDom(editorDoc: Document): List[LandingCard] = {
    val rootOpt   = Option(editorDoc.getRootElement)
    val cardsElem =
      rootOpt.flatMap { r =>
        if (r.getName == "cards") Some(r) else Option(r.element("cards"))
      }

    cardsElem.toList.flatMap(_.elements("card")).flatMap(parseCardElement)
  }

  def toJsonString(editorDoc: Document): String =
    LandingCardsJSON.serializeToJson(fromEditorDom(editorDoc)).spaces2

  private object Private {

    val AppFormSlugRegex = """^[A-Za-z0-9_\-]+$""".r

    val MainQuickLinksTokens  = "form-builder-new form-builder-summary published-forms"
    val AdminQuickLinksTokens = "forms-admin configuration admin-console forms-analytics"
    val AllQuickLinksTokens   = s"$MainQuickLinksTokens $AdminQuickLinksTokens"

    val PresetDefinitions: List[(String, LandingCard)] = List(
      "preset-quick-links"        -> LandingCardsJSON.DefaultCards(0),
      "preset-admin"              -> LandingCardsJSON.DefaultCards(1),
      "preset-published-forms"    -> LandingCardsJSON.DefaultCards(4),
      "preset-form-builder-forms" -> LandingCardsJSON.DefaultCards(5),
      "preset-demo-forms"         -> LandingCardsJSON.DefaultCards(2),
      "preset-demo-features"      -> LandingCardsJSON.DefaultCards(3),
      "preset-bookshelf"          -> LandingCardsJSON.DefaultCards(6)
    )

    def isNonBlankLocalizedString(ls: LocalizedString): Boolean =
      ls match {
        case LocalizedString.ByResource(key)    => key.nonAllBlank
        case LocalizedString.ByLanguage(values) => values.nonEmpty && values.values.exists(_.nonAllBlank)
      }

    def quickLinkTokenToString(t: QuickLinkToken): String =
      t match {
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
      }

    def stringToQuickLinkTokenOpt(s: String): Option[QuickLinkToken] =
      s match {
        case "main"                                        => Some(QuickLinkToken.Main)
        case "admin"                                       => Some(QuickLinkToken.Admin)
        case "all"                                         => Some(QuickLinkToken.All)
        case "form-builder-new"                            => Some(QuickLinkToken.FormBuilderNew)
        case "form-builder-summary" | "form-builder-forms" => Some(QuickLinkToken.FormBuilderSummary)
        case "published-forms"                             => Some(QuickLinkToken.PublishedForms)
        case "forms-admin" | "form-runner-admin"           => Some(QuickLinkToken.FormsAdmin)
        case "configuration"                               => Some(QuickLinkToken.Configuration)
        case "admin-console"                               => Some(QuickLinkToken.AdminConsole)
        case "forms-analytics"                             => Some(QuickLinkToken.FormsAnalytics)
        case _                                             => None
      }

    def sortByToString(sb: SortBy): String =
      sb match {
        case SortBy.AppName        => "application-name"
        case SortBy.FormName       => "form-name"
        case SortBy.FormVersion    => "form-version"
        case SortBy.Created        => "created"
        case SortBy.LastModified   => "last-modified"
        case SortBy.LastModifiedBy => "last-modified-by"
        case SortBy.Title          => "title"
      }

    def stringToSortByOpt(s: String): Option[SortBy] =
      s match {
        case "application-name" | "app-name" => Some(SortBy.AppName)
        case "form-name"                     => Some(SortBy.FormName)
        case "form-version"                  => Some(SortBy.FormVersion)
        case "created"                       => Some(SortBy.Created)
        case "last-modified"                 => Some(SortBy.LastModified)
        case "last-modified-by"              => Some(SortBy.LastModifiedBy)
        case "title"                         => Some(SortBy.Title)
        case _                               => None
      }

    def sortDirectionToString(sd: SortDirection): String =
      sd match {
        case SortDirection.Ascending  => "ascending"
        case SortDirection.Descending => "descending"
      }

    def stringToSortDirectionOpt(s: String): Option[SortDirection] =
      s match {
        case "ascending"  | "asc"  => Some(SortDirection.Ascending)
        case "descending" | "desc" => Some(SortDirection.Descending)
        case _                     => None
      }

    def appendLocalizedStringElement(
      parent       : Element,
      elemName     : String,
      valueOpt     : Option[LocalizedString],
      allowAutoMode: Boolean
    ): Unit = {
      val elem = parent.addElement(elemName)
      valueOpt match {
        case None =>
          elem.addAttribute("mode", if (allowAutoMode) "auto" else "single")
          elem.addAttribute("resource-key", "")
          elem.addAttribute("single", "")
        case Some(LocalizedString.ByResource(key)) =>
          elem.addAttribute("mode", "resource")
          elem.addAttribute("resource-key", key)
          elem.addAttribute("single", "")
        case Some(LocalizedString.ByLanguage(values)) if values.size == 1 && values.contains("_") =>
          elem.addAttribute("mode", "single")
          elem.addAttribute("resource-key", "")
          elem.addAttribute("single", values("_"))
        case Some(LocalizedString.ByLanguage(values)) =>
          elem.addAttribute("mode", "localized")
          elem.addAttribute("resource-key", "")
          elem.addAttribute("single", values.values.headOption.getOrElse(""))
          values.foreach { case (lang, text) =>
            val tr = elem.addElement("translation")
            tr.addAttribute("lang", lang)
            tr.setText(text)
          }
      }
    }

    def appendThumbnailAttributes(elem: Element, thumbnailOpt: Option[String]): Unit =
      thumbnailOpt.flatMap(_.trimAllToOpt) match {
        case None =>
          elem.addAttribute("thumbnail-mode",   "none")
          elem.addAttribute("thumbnail-preset", DefaultThumbnail)
          elem.addAttribute("thumbnail-custom", "")
        case Some(path) if LandingCardsJSON.BuiltInThumbnails.contains(path) =>
          elem.addAttribute("thumbnail-mode",   "preset")
          elem.addAttribute("thumbnail-preset", path)
          elem.addAttribute("thumbnail-custom", "")
        case Some(customPath) =>
          elem.addAttribute("thumbnail-mode",   "custom")
          elem.addAttribute("thumbnail-preset", DefaultThumbnail)
          elem.addAttribute("thumbnail-custom", customPath)
      }

    def appendCardElement(
      parent  : Element,
      card    : LandingCard,
      idOpt   : Option[String],
      expanded: Boolean
    ): Unit = {
      val c = parent.addElement("card")
      idOpt.foreach(c.addAttribute("id", _))
      c.addAttribute("expanded", expanded.toString)

      card match {
        case QuickLinks(titleOpt, descriptionOpt, links, layoutOpt) =>
          c.addAttribute("type", "quick-links")

          val (linksMode, linksExplicitAll, linksCustom) =
            links match {
              case Nil =>
                ("all", "false", AllQuickLinksTokens)
              case List(QuickLinkToken.All) =>
                ("all", "true", AllQuickLinksTokens)
              case List(QuickLinkToken.Main) =>
                ("main", "false", MainQuickLinksTokens)
              case List(QuickLinkToken.Admin) =>
                ("admin", "false", AdminQuickLinksTokens)
              case other =>
                val expandedTokens =
                  other.flatMap {
                    case QuickLinkToken.Main  => MainQuickLinksTokens.splitTo[List]()
                    case QuickLinkToken.Admin => AdminQuickLinksTokens.splitTo[List]()
                    case QuickLinkToken.All   => AllQuickLinksTokens.splitTo[List]()
                    case single               => List(quickLinkTokenToString(single))
                  }.distinct
                ("custom", "false", expandedTokens.mkString(" "))
            }

          val (layoutStr, layoutExplicit) =
            layoutOpt match {
              case None                         => ("tiles", "false")
              case Some(QuickLinksLayout.Tiles) => ("tiles", "true")
              case Some(QuickLinksLayout.List)  => ("list",  "true")
            }

          c.addAttribute("links-mode",         linksMode)
          c.addAttribute("links-explicit-all", linksExplicitAll)
          c.addAttribute("links-custom",       linksCustom)
          c.addAttribute("layout",             layoutStr)
          c.addAttribute("layout-explicit",    layoutExplicit)
          c.addAttribute("app-mode",           "all")
          c.addAttribute("app",                "")
          c.addAttribute("form",               "")
          c.addAttribute("version-mode",       "specific")
          c.addAttribute("version",            "1")
          c.addAttribute("link-to",            "")
          c.addAttribute("sort-by",            "")
          c.addAttribute("sort-direction",     "")
          appendThumbnailAttributes(c, None)
          appendLocalizedStringElement(c, "title",       titleOpt,       allowAutoMode = true)
          appendLocalizedStringElement(c, "description", descriptionOpt, allowAutoMode = true)

        case PublishedForms(title, descriptionOpt, thumbnailOpt, appOpt, linkTo, sortOpt) =>
          c.addAttribute("type",               "published-forms")
          c.addAttribute("links-mode",         "main")
          c.addAttribute("links-explicit-all", "false")
          c.addAttribute("links-custom",       MainQuickLinksTokens)
          c.addAttribute("layout",             "tiles")
          c.addAttribute("layout-explicit",    "false")
          c.addAttribute("app-mode",           if (appOpt.isDefined) "specific" else "all")
          c.addAttribute("app",                appOpt.getOrElse(""))
          c.addAttribute("form",               "")
          c.addAttribute("version-mode",       "specific")
          c.addAttribute("version",            "1")
          c.addAttribute("link-to",            linkTo.map { case LinkTo.New => "new"; case LinkTo.Summary => "summary" }.mkString(" "))
          c.addAttribute("sort-by",            sortOpt.map(s => sortByToString(s._1)).getOrElse(""))
          c.addAttribute("sort-direction",     sortOpt.flatMap(_._2).map(sortDirectionToString).getOrElse(""))
          appendThumbnailAttributes(c, thumbnailOpt)
          appendLocalizedStringElement(c, "title",       Some(title),    allowAutoMode = false)
          appendLocalizedStringElement(c, "description", descriptionOpt, allowAutoMode = true)

        case FormData(titleOpt, descriptionOpt, thumbnailOpt, app, form, versionOpt) =>
          c.addAttribute("type",               "form-data")
          c.addAttribute("links-mode",         "main")
          c.addAttribute("links-explicit-all", "false")
          c.addAttribute("links-custom",       MainQuickLinksTokens)
          c.addAttribute("layout",             "tiles")
          c.addAttribute("layout-explicit",    "false")
          c.addAttribute("app-mode",           "specific")
          c.addAttribute("app",                app)
          c.addAttribute("form",               form)
          c.addAttribute("version-mode",       if (versionOpt.isDefined) "specific" else "default")
          c.addAttribute("version",            versionOpt.map(_.toString).getOrElse("1"))
          c.addAttribute("link-to",            "")
          c.addAttribute("sort-by",            "")
          c.addAttribute("sort-direction",     "")
          appendThumbnailAttributes(c, thumbnailOpt)
          appendLocalizedStringElement(c, "title",       titleOpt,       allowAutoMode = true)
          appendLocalizedStringElement(c, "description", descriptionOpt, allowAutoMode = true)
      }
    }

    def parseLocalizedStringElement(elemOpt: Option[Element], required: Boolean): Option[LocalizedString] =
      elemOpt.flatMap { elem =>
        elem.attributeValueOpt("mode").getOrElse("auto") match {
          case "auto" if ! required =>
            None
          case "resource" =>
            val key = elem.attributeValueOpt("resource-key").flatMap(_.trimAllToOpt)
            if (required)
              Some(LocalizedString.ByResource(key.getOrElse("")))
            else
              key.map(LocalizedString.ByResource.apply)
          case "localized" =>
            val pairs =
              elem.elements("translation").flatMap { tr =>
                tr.attributeValueOpt("lang").flatMap(_.trimAllToOpt).map(_ -> tr.getText.trim)
              }
            val map = ListMap.from(pairs)
            if (map.nonEmpty && (required || map.values.exists(_.nonAllBlank)))
              Some(LocalizedString.ByLanguage(map))
            else if (required)
              Some(LocalizedString.ByLanguage(Map("_" -> "")))
            else
              None
          case _ => // "single" (or fallback when required)
            val singleVal = elem.attributeValueOpt("single").map(_.trim).getOrElse("")
            if (required)
              Some(LocalizedString.ByLanguage(Map("_" -> singleVal)))
            else
              singleVal.trimAllToOpt.map(v => LocalizedString.ByLanguage(Map("_" -> v)))
        }
      }

    def parseThumbnailFromAttributes(elem: Element): Option[String] =
      elem.attributeValueOpt("thumbnail-mode").getOrElse("none") match {
        case "preset" => elem.attributeValueOpt("thumbnail-preset").flatMap(_.trimAllToOpt)
        case "custom" => elem.attributeValueOpt("thumbnail-custom").flatMap(_.trimAllToOpt)
        case _        => None
      }

    def parseCardElement(elem: Element): Option[LandingCard] =
      elem.attributeValueOpt("type").flatMap(_.trimAllToOpt).collect {
        case "quick-links" =>
          val linksMode        = elem.attributeValueOpt("links-mode").getOrElse("all")
          val linksExplicitAll = elem.attributeValueOpt("links-explicit-all").contains("true")
          val links: List[QuickLinkToken] =
            linksMode match {
              case "main"   => List(QuickLinkToken.Main)
              case "admin"  => List(QuickLinkToken.Admin)
              case "custom" => elem.attributeValueOpt("links-custom").getOrElse("").splitTo[List]().distinct.flatMap(stringToQuickLinkTokenOpt)
              case _        => if (linksExplicitAll) List(QuickLinkToken.All) else Nil
            }

          val layoutOpt: Option[QuickLinksLayout] =
            elem.attributeValueOpt("layout").getOrElse("tiles") match {
              case "list" => Some(QuickLinksLayout.List)
              case _      =>
                if (elem.attributeValueOpt("layout-explicit").contains("true"))
                  Some(QuickLinksLayout.Tiles)
                else
                  None
            }

          QuickLinks(
            title       = parseLocalizedStringElement(Option(elem.element("title")),       required = false),
            description = parseLocalizedStringElement(Option(elem.element("description")), required = false),
            links       = links,
            layout      = layoutOpt
          )

        case "published-forms" =>
          val title =
            parseLocalizedStringElement(Option(elem.element("title")), required = true)
              .getOrElse(LocalizedString.ByLanguage(Map("_" -> "")))

          val appOpt =
            if (elem.attributeValueOpt("app-mode").contains("specific"))
              elem.attributeValueOpt("app").flatMap(_.trimAllToOpt)
            else
              None

          val linkTo =
            elem.attributeValueOpt("link-to").getOrElse("").splitTo[List]().distinct.flatMap {
              case "new"     => Some(LinkTo.New)
              case "summary" => Some(LinkTo.Summary)
              case _         => None
            }

          val sortOpt =
            elem.attributeValueOpt("sort-by").flatMap(_.trimAllToOpt).flatMap(stringToSortByOpt).map { sb =>
              val sdOpt = elem.attributeValueOpt("sort-direction").flatMap(_.trimAllToOpt).flatMap(stringToSortDirectionOpt)
              (sb, sdOpt)
            }

          PublishedForms(
            title       = title,
            description = parseLocalizedStringElement(Option(elem.element("description")), required = false),
            thumbnail   = parseThumbnailFromAttributes(elem),
            app         = appOpt,
            linkTo      = linkTo,
            sort        = sortOpt
          )

        case "form-data" =>
          val versionOpt =
            if (elem.attributeValueOpt("version-mode").getOrElse("specific") == "specific")
              elem.attributeValueOpt("version").flatMap(_.trimAllToOpt).flatMap(_.toIntOption)
            else
              None

          FormData(
            title       = parseLocalizedStringElement(Option(elem.element("title")),       required = false),
            description = parseLocalizedStringElement(Option(elem.element("description")), required = false),
            thumbnail   = parseThumbnailFromAttributes(elem),
            app         = elem.attributeValueOpt("app").map(_.trim).getOrElse(""),
            form        = elem.attributeValueOpt("form").map(_.trim).getOrElse(""),
            version     = versionOpt
          )
      }
  }
}
