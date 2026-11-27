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

import org.orbeon.oxf.fr.process.ProcessEditorUI
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*


object ButtonsEditorUI {

  val DefaultEditButtons : String = "summary clear pdf wizard-toc wizard-prev wizard-next save-final review"
  val DefaultViewButtons : String = "edit pdf"
  val DefaultInnerButtons: String = ""

  val ButtonNameRegex = "^[A-Za-z0-9_\\-]+$".r

  private val DefaultWildcardVisible = "fr:owns-lease-or-none-required()"

  case class StandardButtonDef(
    name                  : String,
    category              : String,
    defaultIcon           : String,
    defaultEnglishLabel   : String,
    defaultVisible        : String  = DefaultWildcardVisible,
    defaultEnabled        : String  = "",
    defaultLoading        : String  = "",
    defaultProcessFallback: String  = "",
    keyboardShortcut      : String  = "",
    legacy                : Boolean = false
  )

  val StandardDetailButtons: List[StandardButtonDef] = List(
    // Save & Workflow
    StandardButtonDef(
      name                = "save-final",
      category            = "workflow",
      defaultIcon         = "fa-hdd-o",
      defaultEnglishLabel = "Save",
      defaultLoading      = "inline",
      keyboardShortcut    = "⌘S"
    ),
    StandardButtonDef(
      name                = "save-progress",
      category            = "workflow",
      defaultIcon         = "fa-inbox",
      defaultEnglishLabel = "Save Progress",
      defaultLoading      = "inline"
    ),
    StandardButtonDef(
      name                = "review",
      category            = "workflow",
      defaultIcon         = "fa-eye",
      defaultEnglishLabel = "Review",
      defaultVisible      = "fr:is-embedded('portlet') or not(fr:is-embedded())"
    ),
    StandardButtonDef(
      name                = "edit",
      category            = "workflow",
      defaultIcon         = "fa-arrow-left",
      defaultEnglishLabel = "Edit",
      defaultVisible      = "(fr:is-embedded('portlet') or not(fr:is-embedded())) and fr:can-update()"
    ),
    StandardButtonDef(
      name                = "send",
      category            = "workflow",
      defaultIcon         = "fa-arrow-right",
      defaultEnglishLabel = "Send"
    ),
    StandardButtonDef(
      name                = "email",
      category            = "workflow",
      defaultIcon         = "fa-envelope",
      defaultEnglishLabel = "Email"
    ),
    StandardButtonDef(
      name                = "validate",
      category            = "workflow",
      defaultIcon         = "fa-check",
      defaultEnglishLabel = "Validate"
    ),
    StandardButtonDef(
      name                = "submit",
      category            = "workflow",
      defaultIcon         = "fa-arrow-right",
      defaultEnglishLabel = "Submit",
      defaultLoading      = "modal"
    ),

    // Navigation & Wizard
    StandardButtonDef(
      name                = "summary",
      category            = "navigation",
      defaultIcon         = "fa-list",
      defaultEnglishLabel = "Summary",
      defaultVisible      = "(fr:is-embedded('portlet') or not(fr:is-embedded())) and fr:can-list()"
    ),
    StandardButtonDef(
      name                = "home",
      category            = "navigation",
      defaultIcon         = "fa-th",
      defaultEnglishLabel = "Home",
      defaultVisible      = "fr:is-embedded('portlet') or not(fr:is-embedded())"
    ),
    StandardButtonDef(
      name                = "close",
      category            = "navigation",
      defaultIcon         = "fa-times",
      defaultEnglishLabel = "Close"
    ),
    StandardButtonDef(
      name                = "new",
      category            = "navigation",
      defaultIcon         = "fa-plus",
      defaultEnglishLabel = "New"
    ),
    StandardButtonDef(
      name                   = "wizard-prev",
      category               = "navigation",
      defaultIcon            = "fa-chevron-left",
      defaultEnglishLabel    = "Prev",
      defaultVisible         = "fr:owns-lease-or-none-required() and fr:is-wizard-body-shown()",
      defaultEnabled         = "not(fr:is-wizard-first-page())",
      defaultProcessFallback = "wizard-prev"
    ),
    StandardButtonDef(
      name                   = "wizard-next",
      category               = "navigation",
      defaultIcon            = "fa-chevron-right",
      defaultEnglishLabel    = "Next",
      defaultVisible         = "fr:owns-lease-or-none-required() and fr:is-wizard-body-shown()",
      defaultEnabled         = "not(fr:is-wizard-last-page())",
      defaultProcessFallback = "wizard-next"
    ),
    StandardButtonDef(
      name                   = "wizard-toc",
      category               = "navigation",
      defaultIcon            = "fa-list",
      defaultEnglishLabel    = "Content",
      defaultVisible         = "fr:owns-lease-or-none-required() and fr:is-wizard-separate-toc() and fr:is-wizard-body-shown()",
      defaultProcessFallback = "wizard-toc"
    ),
    StandardButtonDef(
      name                = "search-first",
      category            = "navigation",
      defaultIcon         = "fa-step-backward",
      defaultEnglishLabel = "First",
      defaultVisible      = "(fr:is-embedded('portlet') or not(fr:is-embedded())) and xxf:non-blank(xxf:instance('fr-search-navigation-instance')/search-param)",
      defaultEnabled      = "xxf:instance('fr-search-navigation-instance')/has-first = 'true'"
    ),
    StandardButtonDef(
      name                = "search-prev",
      category            = "navigation",
      defaultIcon         = "fa-chevron-left",
      defaultEnglishLabel = "Previous",
      defaultVisible      = "(fr:is-embedded('portlet') or not(fr:is-embedded())) and xxf:non-blank(xxf:instance('fr-search-navigation-instance')/search-param)",
      defaultEnabled      = "xxf:instance('fr-search-navigation-instance')/has-previous = 'true'"
    ),
    StandardButtonDef(
      name                = "search-next",
      category            = "navigation",
      defaultIcon         = "fa-chevron-right",
      defaultEnglishLabel = "Next",
      defaultVisible      = "(fr:is-embedded('portlet') or not(fr:is-embedded())) and xxf:non-blank(xxf:instance('fr-search-navigation-instance')/search-param)",
      defaultEnabled      = "xxf:instance('fr-search-navigation-instance')/has-next = 'true'"
    ),
    StandardButtonDef(
      name                = "search-last",
      category            = "navigation",
      defaultIcon         = "fa-step-forward",
      defaultEnglishLabel = "Last",
      defaultVisible      = "(fr:is-embedded('portlet') or not(fr:is-embedded())) and xxf:non-blank(xxf:instance('fr-search-navigation-instance')/search-param)",
      defaultEnabled      = "xxf:instance('fr-search-navigation-instance')/has-last = 'true'"
    ),

    // Export & Utilities
    StandardButtonDef(
      name                = "pdf",
      category            = "export",
      defaultIcon         = "fa-file-pdf-o",
      defaultEnglishLabel = "PDF",
      defaultVisible      = "not(fr:is-browser-environment())"
    ),
    StandardButtonDef(
      name                = "tiff",
      category            = "export",
      defaultIcon         = "fa-image",
      defaultEnglishLabel = "TIFF",
      defaultVisible      = "not(fr:is-browser-environment())"
    ),
    StandardButtonDef(
      name                = "excel-export",
      category            = "export",
      defaultIcon         = "fa-file-excel-o",
      defaultEnglishLabel = "Excel Export",
      defaultVisible      = "not(fr:is-browser-environment())"
    ),
    StandardButtonDef(
      name                = "xml-export",
      category            = "export",
      defaultIcon         = "fa-file-excel-o",
      defaultEnglishLabel = "XML Export",
      defaultVisible      = "not(fr:is-browser-environment())"
    ),
    StandardButtonDef(
      name                = "clear",
      category            = "export",
      defaultIcon         = "fa-remove",
      defaultEnglishLabel = "Clear"
    ),
    StandardButtonDef(
      name                = "collapse-all",
      category            = "export",
      defaultIcon         = "fa-compress",
      defaultEnglishLabel = ""
    ),
    StandardButtonDef(
      name                = "expand-all",
      category            = "export",
      defaultIcon         = "fa-expand",
      defaultEnglishLabel = ""
    ),

    // Legacy / compatibility aliases (recognized when present in existing configs, hidden from primary Add menu)
    StandardButtonDef(
      name                = "save",
      category            = "legacy",
      defaultIcon         = "fa-hdd-o",
      defaultEnglishLabel = "Save",
      keyboardShortcut    = "⌘S",
      legacy              = true
    ),
    StandardButtonDef(
      name                = "save-draft",
      category            = "legacy",
      defaultIcon         = "fa-inbox",
      defaultEnglishLabel = "Save Progress",
      defaultLoading      = "inline",
      legacy              = true
    ),
    StandardButtonDef(
      name                = "workflow-review",
      category            = "legacy",
      defaultIcon         = "fa-arrow-right",
      defaultEnglishLabel = "Review",
      legacy              = true
    ),
    StandardButtonDef(
      name                = "workflow-edit",
      category            = "legacy",
      defaultIcon         = "fa-arrow-left",
      defaultEnglishLabel = "Edit",
      legacy              = true
    ),
    StandardButtonDef(
      name                   = "workflow-send",
      category               = "legacy",
      defaultIcon            = "fa-arrow-right",
      defaultEnglishLabel    = "Send",
      defaultProcessFallback = "send",
      legacy                 = true
    ),
    StandardButtonDef(
      name                   = "delete",
      category               = "legacy",
      defaultIcon            = "fa-remove",
      defaultEnglishLabel    = "Delete",
      defaultProcessFallback = "delete",
      legacy                 = true
    ),
    StandardButtonDef(
      name                   = "duplicate",
      category               = "legacy",
      defaultIcon            = "fa-copy",
      defaultEnglishLabel    = "Duplicate",
      defaultProcessFallback = "duplicate",
      legacy                 = true
    )
  )

  val StandardDetailButtonsByName: Map[String, StandardButtonDef] =
    StandardDetailButtons.map(b => b.name -> b).toMap

  val CuratedButtonIcons: List[(String, String)] = List(
    ""                  -> "none",
    "fa-hdd-o"          -> "Save",
    "fa-inbox"          -> "Inbox / Draft",
    "fa-check"          -> "Check",
    "fa-arrow-right"    -> "Arrow Right",
    "fa-arrow-left"     -> "Arrow Left",
    "fa-paper-plane"    -> "Send / Plane",
    "fa-envelope"       -> "Email",
    "fa-eye"            -> "View / Review",
    "fa-pen"            -> "Edit / Pen",
    "fa-plus"           -> "Add / New",
    "fa-times"          -> "Close",
    "fa-remove"         -> "Clear / Delete",
    "fa-rotate-left"    -> "Revert / Reset",
    "fa-list"           -> "List / Summary",
    "fa-th"             -> "Grid / Home",
    "fa-chevron-left"   -> "Previous",
    "fa-chevron-right"  -> "Next",
    "fa-file-pdf-o"     -> "PDF",
    "fa-image"          -> "Image / TIFF",
    "fa-file-excel-o"   -> "Spreadsheet",
    "fa-download"       -> "Download",
    "fa-upload"         -> "Upload",
    "fa-copy"           -> "Copy / Duplicate",
    "fa-print"          -> "Print",
    "fa-file-signature" -> "Sign",
    "fa-thumbs-up"      -> "Approve",
    "fa-thumbs-down"    -> "Reject",
    "fa-lock"           -> "Lock",
    "fa-unlock"         -> "Unlock",
    "fa-flag"           -> "Flag",
    "fa-bolt"           -> "Action / Bolt",
    "fa-external-link"  -> "External Link"
  )

  case class ParsedButtonLabel(
    mode: String, // "structured" | "raw-html"
    icon: String,
    text: String
  )

  /*@XPathFunction*/
  def standardDetailButtonsCatalogXml(): NodeInfo = {
    val buttonsXml =
      StandardDetailButtons.map { b =>
        s"""<button
           | name="${b.name.escapeXmlForAttribute}"
           | category="${b.category.escapeXmlForAttribute}"
           | default-icon="${b.defaultIcon.escapeXmlForAttribute}"
           | default-en-label="${b.defaultEnglishLabel.escapeXmlForAttribute}"
           | default-visible="${b.defaultVisible.escapeXmlForAttribute}"
           | default-enabled="${b.defaultEnabled.escapeXmlForAttribute}"
           | default-loading="${b.defaultLoading.escapeXmlForAttribute}"
           | default-process-fallback="${b.defaultProcessFallback.escapeXmlForAttribute}"
           | keyboard-shortcut="${b.keyboardShortcut.escapeXmlForAttribute}"
           | legacy="${b.legacy}"/>""".stripMargin.replace('\n', ' ')
      }.mkString

    val iconsXml =
      CuratedButtonIcons.map { case (cls, hint) =>
        s"""<icon class="${cls.escapeXmlForAttribute}" hint="${hint.escapeXmlForAttribute}"/>"""
      }.mkString

    ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
      s"""<catalog default-edit="${DefaultEditButtons.escapeXmlForAttribute}" default-view="${DefaultViewButtons.escapeXmlForAttribute}" default-inner="${DefaultInnerButtons.escapeXmlForAttribute}">
         |  <buttons>$buttonsXml</buttons>
         |  <icons>$iconsXml</icons>
         |</catalog>""".stripMargin
    )
  }

  /*@XPathFunction*/
  def isValidButtonName(raw: String): Boolean =
    Option(raw).flatMap(_.trimAllToOpt).exists(ButtonNameRegex.matches)

  def parseButtonLabel(rawValue: String, isHtml: Boolean): ParsedButtonLabel = {
    val safeRaw = Option(rawValue).getOrElse("")
    if (! isHtml) {
      ParsedButtonLabel(mode = "structured", icon = "", text = safeRaw)
    } else {
      Private.LeadingFontAwesomeIconRegex.findFirstMatchIn(safeRaw) match {
        case Some(m) =>
          val classTokens = m.group(1).splitTo[List]()
          val iconToken   = classTokens.find(t => t.startsWith("fa-") && t != "fa-fw").getOrElse("")
          val remainder   = m.group(2)
          if (iconToken.nonEmpty && ! remainder.contains('<') && ! remainder.contains('>'))
            ParsedButtonLabel(mode = "structured", icon = iconToken, text = Private.unescapeBasicHtmlEntities(remainder))
          else
            ParsedButtonLabel(mode = "raw-html", icon = "", text = safeRaw)
        case None =>
          if (! safeRaw.contains('<') && ! safeRaw.contains('>'))
            ParsedButtonLabel(mode = "structured", icon = "", text = Private.unescapeBasicHtmlEntities(safeRaw))
          else
            ParsedButtonLabel(mode = "raw-html", icon = "", text = safeRaw)
      }
    }
  }

  def composeButtonLabel(iconClass: String, plainText: String): (String, Boolean) = {
    val cleanIcon = Option(iconClass).flatMap(_.trimAllToOpt).filter(Private.IconTokenRegex.matches).getOrElse("")
    val cleanText = Option(plainText).map(_.trim).getOrElse("")
    if (cleanIcon.isEmpty) {
      (Option(plainText).getOrElse(""), false)
    } else if (cleanText.isEmpty) {
      (s"""<i class="fa fa-fw $cleanIcon"></i>""", true)
    } else {
      (s"""<i class="fa fa-fw $cleanIcon"></i> ${cleanText.escapeXmlMinimal}""", true)
    }
  }

  /*@XPathFunction*/
  def parseButtonLabelXml(rawValue: String, isHtml: Boolean): NodeInfo = {
    val parsed = parseButtonLabel(rawValue, isHtml)
    ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
      s"""<parsed-label mode="${parsed.mode.escapeXmlForAttribute}" icon="${parsed.icon.escapeXmlForAttribute}">${parsed.text.escapeXmlMinimal}</parsed-label>"""
    )
  }

  /*@XPathFunction*/
  def composeButtonLabelXml(iconClass: String, plainText: String): NodeInfo = {
    val (value, isHtml) = composeButtonLabel(iconClass, plainText)
    ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
      s"""<composed-label html="$isHtml">${value.escapeXmlMinimal}</composed-label>"""
    )
  }

  /*@XPathFunction*/
  def formatButtonChipHtml(rawValue: String, isHtml: Boolean, fallbackName: String): String = {
    val safeFallback = Option(fallbackName).flatMap(_.trimAllToOpt).getOrElse("")
    val parsed       = parseButtonLabel(rawValue, isHtml)
    parsed.mode match {
      case "structured" =>
        val iconHtml =
          if (parsed.icon.nonEmpty)
            s"""<i class="fa fa-fw ${parsed.icon.escapeXmlForAttribute}" aria-hidden="true"></i>"""
          else
            ""
        val displayLabel =
          if (parsed.text.nonEmpty) parsed.text
          else if (parsed.icon.nonEmpty) ""
          else safeFallback
        val textHtml =
          if (displayLabel.nonEmpty)
            s"""<span>${displayLabel.escapeXmlMinimal}</span>"""
          else
            ""
        List(iconHtml, textHtml).filter(_.nonEmpty).mkString(" ")
      case _ =>
        val stripped = Private.HtmlTagRegex.replaceAllIn(Option(rawValue).getOrElse(""), "").trim
        val text     = Private.unescapeBasicHtmlEntities(stripped).trimAllToOpt.getOrElse(safeFallback)
        s"""<span>${text.escapeXmlMinimal}</span>"""
    }
  }

  /*@XPathFunction*/
  def parseBarTokensXml(rawBarValue: String): NodeInfo = {
    val tokens = Option(rawBarValue).toList.flatMap(_.splitTo[List]())
    val itemsXml =
      tokens.flatMap { token =>
        val parts = token.splitTo[List]("|")
        Option.when(parts.nonEmpty) {
          val canonicalToken = parts.mkString("|")
          val primary        = parts.head
          val isSplit        = parts.size > 1
          val partsXml       = parts.map(p => s"""<part name="${p.escapeXmlForAttribute}"/>""").mkString
          s"""<item token="${canonicalToken.escapeXmlForAttribute}" primary="${primary.escapeXmlForAttribute}" is-split="$isSplit">$partsXml</item>"""
        }
      }.mkString
    ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(s"<items>$itemsXml</items>")
  }

  /*@XPathFunction*/
  def serializeBarItems(itemsParentOrNull: NodeInfo): String =
    Option(itemsParentOrNull)
      .toList
      .flatMap(parent => (parent / "item").toList)
      .flatMap { item =>
        val parts = (item / "part").flatMap(_.attValueOpt("name")).flatMap(_.trimAllToOpt).toList
        if (parts.nonEmpty)
          Some(parts.mkString("|"))
        else
          item.attValueOpt("token").flatMap(_.trimAllToOpt)
      }
      .mkString(" ")

  /*@XPathFunction*/
  def barContainsButton(rawBarValue: String, buttonName: String): Boolean =
    Option(buttonName).flatMap(_.trimAllToOpt).exists { target =>
      Option(rawBarValue)
        .toList
        .flatMap(_.splitTo[List]())
        .flatMap(_.splitTo[List]("|"))
        .contains(target)
    }

  /*@XPathFunction*/
  def renameButtonInBarValue(rawBarValue: String, oldName: String, newName: String): String =
    (Option(oldName).flatMap(_.trimAllToOpt), Option(newName).flatMap(_.trimAllToOpt)) match {
      case (Some(oldN), Some(newN)) if oldN != newN =>
        Option(rawBarValue)
          .toList
          .flatMap(_.splitTo[List]())
          .map { token =>
            token
              .splitTo[List]("|")
              .map(p => if (p == oldN) newN else p)
              .distinct
              .mkString("|")
          }
          .mkString(" ")
      case _ =>
        Option(rawBarValue).map(_.splitTo[List]().mkString(" ")).getOrElse("")
    }

  private object Private {

    val LeadingFontAwesomeIconRegex =
      """(?s)^\s*<i\s+class="([^"]*\bfa\b[^"]*)"(?:\s+aria-(?:label|hidden)="[^"]*")*\s*>\s*</i>\s*(.*?)\s*$""".r

    val IconTokenRegex =
      """^fa-[A-Za-z0-9_\-]+$""".r

    val HtmlTagRegex =
      """<[^>]+>""".r

    def unescapeBasicHtmlEntities(s: String): String =
      s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
  }
}
