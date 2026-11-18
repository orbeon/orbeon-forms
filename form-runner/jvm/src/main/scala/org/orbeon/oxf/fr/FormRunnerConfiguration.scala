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

import org.orbeon.dom.{Document, Element, Namespace}
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.fr.persistence.PersistencePropertyProvider
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.NodeInfo

import scala.collection.mutable


trait FormRunnerConfiguration {

  import FormRunnerConfiguration.*

  //@XPathFunction
  def invalidatePersistencePropertiesCache(): Unit =
    PersistencePropertyProvider.invalidateCache()

  //@XPathFunction
  def propertiesToConfigurationXml(propertiesNode: NodeInfo): DocumentNodeInfoType = {
    val propertiesDoc = StaticXPath.tinyTreeToOrbeonDom(propertiesNode)
    val configDoc     = propertiesDomToConfigurationDom(propertiesDoc)
    new DocumentWrapper(configDoc, null, StaticXPath.GlobalConfiguration)
  }

  //@XPathFunction
  def configurationXmlToProperties(configNode: NodeInfo): DocumentNodeInfoType = {
    val configDoc     = StaticXPath.tinyTreeToOrbeonDom(configNode)
    val propertiesDoc = configurationDomToPropertiesDom(configDoc)
    new DocumentWrapper(propertiesDoc, null, StaticXPath.GlobalConfiguration)
  }
  //@XPathFunction
  def isValidCacheMaxSizeBytes(value: String): Boolean =
    value.trimAllToOpt match {
      case None | Some("0") | Some("-1") => true
      case Some(nonEmpty)                => ObjectCache.parseMaxSizeBytes(nonEmpty, 1024L * 1024L * 1024L).isDefined
    }

  //@XPathFunction
  def isValidCacheDuration(value: String): Boolean =
    value.trimAllToOpt match {
      case None | Some("0") | Some("-1") => true
      case Some(nonEmpty)                => ObjectCache.parseDurationMillis(nonEmpty).isDefined
    }
}

object FormRunnerConfiguration extends FormRunnerConfiguration {

  case class CuratedPropertyDef(
    key            : String,
    category       : String,
    baseName       : String,
    xsType         : String,
    hasAppForm     : Boolean,
    hasProfiles    : Boolean = false,
    defaultValue   : String,
    validationOpt  : Option[String] = None,
    legacyBaseNames: List[String] = Nil
  ) {
    def globalPropertyName: String =
      if (hasAppForm) s"$baseName.*.*" else baseName

    def legacyGlobalPropertyNames: List[String] =
      if (hasAppForm) legacyBaseNames.map(n => s"$n.*.*") else legacyBaseNames

    def appFormPropertyName(app: String, form: String): String =
      s"$baseName.$app.$form"
  }

  case class OverrideScope(
    app     : String,
    form    : String,
    profiles: String
  )

  case class CustomProperty(
    name         : String,
    xsType       : String,
    value        : String,
    profiles     : String,
    processorName: String
  )

  case class ResourceOverrideKey(
    lang: String,
    name: String
  )

  private val ResourcePropertyPrefix = "oxf.fr.resource."
  private val HtmlSuffix             = "html"

  val Categories: List[String] =
    List(
      "system",
      "navbar",
      "appearance",
      "detail",
      "wizard",
      "formulas",
      "landing",
      "summary",
      "admin",
      "import",
      "pdf",
      "email",
      "form-builder",
      "formats",
      "security"
    )

  val CuratedProperties: List[CuratedPropertyDef] = List(
    // 1. System
    CuratedPropertyDef(
      key          = "default-language",
      category     = "system",
      baseName     = "oxf.fr.default-language",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "en"
    ),
    CuratedPropertyDef(
      key          = "available-languages",
      category     = "system",
      baseName     = "oxf.fr.available-languages",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "*"
    ),
    CuratedPropertyDef(
      key          = "global-available-languages",
      category     = "system",
      baseName     = "oxf.fr.available-languages",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "en es fr it de pt sv fi nl da ar"
    ),
    CuratedPropertyDef(
      key          = "default-timezone",
      category     = "system",
      baseName     = "oxf.fr.default-timezone",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "session-heartbeat",
      category     = "system",
      baseName     = "oxf.xforms.session-heartbeat",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key             = "cache-documents-size",
      category        = "system",
      baseName        = "oxf.xforms.cache.live-documents.size",
      xsType          = "xs:integer",
      hasAppForm      = false,
      defaultValue    = "50",
      validationOpt   = Some("non-negative-integer"),
      legacyBaseNames = List("oxf.xforms.cache.documents.size")
    ),
    CuratedPropertyDef(
      key             = "cache-documents-max-size-bytes",
      category        = "system",
      baseName        = "oxf.xforms.cache.live-documents.max-size-bytes",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "15%",
      validationOpt   = Some("byte-size-or-percent"),
      legacyBaseNames = List("oxf.xforms.cache.documents.max-size-bytes")
    ),
    CuratedPropertyDef(
      key             = "cache-documents-max-idle",
      category        = "system",
      baseName        = "oxf.xforms.cache.live-documents.max-idle",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "",
      validationOpt   = Some("duration"),
      legacyBaseNames = List("oxf.xforms.cache.documents.max-idle")
    ),
    CuratedPropertyDef(
      key             = "cache-static-state-size",
      category        = "system",
      baseName        = "oxf.xforms.cache.static-states.size",
      xsType          = "xs:integer",
      hasAppForm      = false,
      defaultValue    = "50",
      validationOpt   = Some("non-negative-integer"),
      legacyBaseNames = List("oxf.xforms.cache.static-state.size")
    ),
    CuratedPropertyDef(
      key             = "cache-static-state-max-size-bytes",
      category        = "system",
      baseName        = "oxf.xforms.cache.static-states.max-size-bytes",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "15%",
      validationOpt   = Some("byte-size-or-percent"),
      legacyBaseNames = List("oxf.xforms.cache.static-state.max-size-bytes")
    ),
    CuratedPropertyDef(
      key             = "cache-static-state-max-idle",
      category        = "system",
      baseName        = "oxf.xforms.cache.static-states.max-idle",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "",
      validationOpt   = Some("duration"),
      legacyBaseNames = List("oxf.xforms.cache.static-state.max-idle")
    ),

    // 2. Navigation Bar
    CuratedPropertyDef(
      key          = "navbar-home-link",
      category     = "navbar",
      baseName     = "oxf.fr.navbar.home-link.enable",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "navbar-language-selector",
      category     = "navbar",
      baseName     = "oxf.fr.navbar.language-selector.enable",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "navbar-color-scheme-selector",
      category     = "navbar",
      baseName     = "oxf.fr.navbar.color-scheme-selector.enable",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "navbar-share-button",
      category     = "navbar",
      baseName     = "oxf.fr.navbar.share-button.enable",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "navbar-revision-history",
      category     = "navbar",
      baseName     = "oxf.fr.navbar.revision-history.enable",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "navbar-workflow-stage",
      category     = "navbar",
      baseName     = "oxf.fr.navbar.workflow-stage.enable",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),

    // 2. Appearance, Branding & Read-Only Display
    CuratedPropertyDef(
      key          = "style-theme",
      category     = "appearance",
      baseName     = "oxf.fr.style.theme",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "2026"
    ),
    CuratedPropertyDef(
      key          = "color-scheme",
      category     = "appearance",
      baseName     = "oxf.fr.style.color-scheme",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "light"
    ),
    CuratedPropertyDef(
      key          = "html-page-layout",
      category     = "appearance",
      baseName     = "oxf.fr.detail.html-page-layout",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "fixed"
    ),
    CuratedPropertyDef(
      key          = "detail-density",
      category     = "appearance",
      baseName     = "oxf.fr.detail.density",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "comfortable"
    ),
    CuratedPropertyDef(
      key          = "grid-markup",
      category     = "appearance",
      baseName     = "oxf.xforms.xbl.fr.grid.markup",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "css-grid"
    ),
    CuratedPropertyDef(
      key          = "label-appearance",
      category     = "appearance",
      baseName     = "oxf.fr.detail.label.appearance",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "full"
    ),
    CuratedPropertyDef(
      key          = "hint-appearance",
      category     = "appearance",
      baseName     = "oxf.fr.detail.hint.appearance",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "full"
    ),
    CuratedPropertyDef(
      key          = "static-readonly-hint",
      category     = "appearance",
      baseName     = "oxf.fr.detail.static-readonly-hint",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "static-readonly-alert",
      category     = "appearance",
      baseName     = "oxf.fr.detail.static-readonly-alert",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "static-readonly-required",
      category     = "appearance",
      baseName     = "oxf.fr.detail.static-readonly-required",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "view-show-explanation",
      category     = "appearance",
      baseName     = "oxf.fr.detail.view.show-explanation",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "default-logo-uri",
      category     = "appearance",
      baseName     = "oxf.fr.default-logo.uri",
      xsType       = "xs:anyURI",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "/apps/fr/style/orbeon-logo.svg"
    ),
    CuratedPropertyDef(
      key          = "favicon-uri",
      category     = "appearance",
      baseName     = "oxf.fr.favicon.uri",
      xsType       = "xs:anyURI",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "/ops/images/orbeon-icon-64.png"
    ),
    CuratedPropertyDef(
      key          = "custom-css-uri",
      category     = "appearance",
      baseName     = "oxf.fr.css.custom.uri",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "custom-js-uri",
      category     = "appearance",
      baseName     = "oxf.fr.js.custom.uri",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "hide-logo",
      category     = "appearance",
      baseName     = "oxf.fr.detail.hide-logo",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "hide-header",
      category     = "appearance",
      baseName     = "oxf.fr.detail.hide-header",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "hide-footer",
      category     = "appearance",
      baseName     = "oxf.fr.detail.hide-footer",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "hide-buttons-bar",
      category     = "appearance",
      baseName     = "oxf.fr.detail.hide-buttons-bar",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "keyboard-shortcuts-show-hints",
      category     = "appearance",
      baseName     = "oxf.fr.keyboard-shortcuts.show-hints",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),

    // 3. Detail Page / Form Options
    CuratedPropertyDef(
      key          = "detail-buttons",
      category     = "detail",
      baseName     = "oxf.fr.detail.buttons",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "home review pdf delete duplicate clear save-final wizard-prev wizard-next"
    ),
    CuratedPropertyDef(
      key          = "detail-buttons-view",
      category     = "detail",
      baseName     = "oxf.fr.detail.buttons.view",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "edit pdf"
    ),
    CuratedPropertyDef(
      key          = "autosave-delay",
      category     = "detail",
      baseName     = "oxf.fr.detail.autosave-delay",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "5000"
    ),
    CuratedPropertyDef(
      key          = "data-migration",
      category     = "detail",
      baseName     = "oxf.fr.detail.data-migration",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "enabled"
    ),
    CuratedPropertyDef(
      key          = "grid-tab-order",
      category     = "detail",
      baseName     = "oxf.xforms.xbl.fr.grid.tab-order",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "rows"
    ),
    CuratedPropertyDef(
      key          = "initial-focus",
      category     = "detail",
      baseName     = "oxf.fr.detail.initial-focus",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "automatic-hints",
      category     = "detail",
      baseName     = "oxf.fr.detail.hint.automatic",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "error-summary",
      category     = "detail",
      baseName     = "oxf.fr.detail.error-summary",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "bottom"
    ),
    CuratedPropertyDef(
      key          = "captcha-component",
      category     = "detail",
      baseName     = "oxf.fr.detail.captcha.component",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "singleton-enabled",
      category     = "detail",
      baseName     = "oxf.fr.detail.singleton.enabled",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "singleton-if-one-exists",
      category     = "detail",
      baseName     = "oxf.fr.detail.singleton.if-one-exists",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "open"
    ),
    CuratedPropertyDef(
      key          = "toc-min-sections",
      category     = "detail",
      baseName     = "oxf.fr.detail.toc.min-sections",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "-2"
    ),
    CuratedPropertyDef(
      key          = "toc-position",
      category     = "detail",
      baseName     = "oxf.fr.detail.toc.position",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "top"
    ),
    CuratedPropertyDef(
      key          = "toc-modes",
      category     = "detail",
      baseName     = "oxf.fr.detail.toc.modes",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "new edit view"
    ),
    CuratedPropertyDef(
      key          = "messages-appearance",
      category     = "detail",
      baseName     = "oxf.fr.detail.messages.appearance",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "toast"
    ),
    CuratedPropertyDef(
      key          = "messages-position",
      category     = "detail",
      baseName     = "oxf.fr.detail.messages.position",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "top-right"
    ),
    CuratedPropertyDef(
      key          = "messages-delay",
      category     = "detail",
      baseName     = "oxf.fr.detail.messages.delay",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "10000"
    ),
    CuratedPropertyDef(
      key          = "lease-enabled",
      category     = "detail",
      baseName     = "oxf.fr.detail.lease.enabled",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "lease-duration",
      category     = "detail",
      baseName     = "oxf.fr.detail.lease.duration.*",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "10"
    ),
    CuratedPropertyDef(
      key          = "lease-renew-threshold",
      category     = "detail",
      baseName     = "oxf.fr.detail.lease.renew-threshold",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "1"
    ),
    CuratedPropertyDef(
      key          = "lease-alert-threshold",
      category     = "detail",
      baseName     = "oxf.fr.detail.lease.alert-threshold",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "2"
    ),
    CuratedPropertyDef(
      key          = "session-expiration-dialog-enabled",
      category     = "detail",
      baseName     = "oxf.fr.detail.session-expiration-dialog.enabled",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key           = "warn-when-data-unsafe",
      category      = "detail",
      baseName      = "oxf.fr.detail.warn-when-data-unsafe",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles  = true,
      defaultValue  = "true",
      validationOpt = Some("xpath2-or-avt")
    ),
    CuratedPropertyDef(
      key          = "validate-selection-controls-choices",
      category     = "detail",
      baseName     = "oxf.fr.detail.validate-selection-controls-choices",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "section-collapsible",
      category     = "detail",
      baseName     = "oxf.xforms.xbl.fr.section.collapsible",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "section-animate",
      category     = "detail",
      baseName     = "oxf.xforms.xbl.fr.section.animate",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "section-insert",
      category     = "detail",
      baseName     = "oxf.xforms.xbl.fr.section.insert",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "index"
    ),
    CuratedPropertyDef(
      key          = "grid-insert",
      category     = "detail",
      baseName     = "oxf.xforms.xbl.fr.grid.insert",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "index"
    ),
    CuratedPropertyDef(
      key          = "attachment-max-size-per-file",
      category     = "detail",
      baseName     = "oxf.fr.detail.attachment.max-size-per-file",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "attachment-max-size-aggregate-per-control",
      category     = "detail",
      baseName     = "oxf.fr.detail.attachment.max-size-aggregate-per-control",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "attachment-max-size-aggregate-per-form",
      category     = "detail",
      baseName     = "oxf.fr.detail.attachment.max-size-aggregate-per-form",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "attachment-max-files-per-control",
      category     = "detail",
      baseName     = "oxf.fr.detail.attachment.max-files-per-control",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "attachment-mediatypes",
      category     = "detail",
      baseName     = "oxf.fr.detail.attachment.mediatypes",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "*/*"
    ),
    CuratedPropertyDef(
      key          = "upload-reject-empty-files",
      category     = "detail",
      baseName     = "oxf.fr.upload.reject-empty-files",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "attachment-upload-error-appearance",
      category     = "detail",
      baseName     = "oxf.xforms.xbl.fr.attachment.upload-error-appearance",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "dialog"
    ),

    // 4. Wizard
    CuratedPropertyDef(
      key          = "wizard-view",
      category     = "wizard",
      baseName     = "oxf.fr.detail.view.appearance",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "full"
    ),
    CuratedPropertyDef(
      key          = "wizard-mode",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.validate",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "free"
    ),
    CuratedPropertyDef(
      key          = "wizard-subsections-nav",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.subsections-nav",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "wizard-subsections-toc",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.subsections-toc",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "active"
    ),
    CuratedPropertyDef(
      key          = "wizard-toc-position",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.toc.position",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "left"
    ),
    CuratedPropertyDef(
      key          = "wizard-section-status",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.section-status",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "wizard-allow-url-param",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.allow-url-param",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "wizard-update-url-param",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.update-url-param",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "wizard-visit-on-page-change",
      category     = "wizard",
      baseName     = "oxf.xforms.xbl.fr.wizard.visit-on-page-change",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "always"
    ),

    // 5. Formulas
    CuratedPropertyDef(
      key          = "readonly-disable-calculate",
      category     = "formulas",
      baseName     = "oxf.fr.detail.readonly.disable-calculate",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "analysis-calculate",
      category     = "formulas",
      baseName     = "oxf.xforms.analysis.calculate",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "formula-allowed-functions",
      category     = "formulas",
      baseName     = "oxf.fr.detail.formula.allowed-functions",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),

    // 6. Summary, Landing, Home & Import/Export
    CuratedPropertyDef(
      key          = "summary-page-size",
      category     = "summary",
      baseName     = "oxf.fr.summary.page-size",
      xsType       = "xs:integer",
      hasAppForm   = true,
      defaultValue = "20"
    ),
    CuratedPropertyDef(
      key          = "summary-buttons",
      category     = "summary",
      baseName     = "oxf.fr.summary.buttons",
      xsType       = "xs:string",
      hasAppForm   = true,
      defaultValue = "home review pdf delete duplicate excel-export-with-search new"
    ),
    CuratedPropertyDef(
      key          = "summary-show-created",
      category     = "summary",
      baseName     = "oxf.fr.summary.show-created",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "summary-show-last-modified",
      category     = "summary",
      baseName     = "oxf.fr.summary.show-last-modified",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "summary-show-workflow-stage",
      category     = "summary",
      baseName     = "oxf.fr.summary.show-workflow-stage",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "summary-show-created-by",
      category     = "summary",
      baseName     = "oxf.fr.summary.show-created-by",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "summary-show-last-modified-by",
      category     = "summary",
      baseName     = "oxf.fr.summary.show-last-modified-by",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "summary-show-version-selector",
      category     = "summary",
      baseName     = "oxf.fr.summary.show-version-selector",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "summary-new-form-version",
      category     = "summary",
      baseName     = "oxf.fr.summary.new.form-version",
      xsType       = "xs:string",
      hasAppForm   = true,
      defaultValue = "current"
    ),
    CuratedPropertyDef(
      key          = "summary-search-options-opened-on-load",
      category     = "summary",
      baseName     = "oxf.fr.summary.search-options-opened-on-load",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    // 7. Landing Page
    CuratedPropertyDef(
      key          = "landing-page-size",
      category     = "landing",
      baseName     = "oxf.fr.landing.page-size",
      xsType       = "xs:integer",
      hasAppForm   = false,
      defaultValue = "8"
    ),
    CuratedPropertyDef(
      key          = "landing-cards",
      category     = "landing",
      baseName     = "oxf.fr.landing.cards",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = ""
    ),

    // 8. Admin Page
    CuratedPropertyDef(
      key          = "home-page-size",
      category     = "admin",
      baseName     = "oxf.fr.home.page-size",
      xsType       = "xs:integer",
      hasAppForm   = false,
      defaultValue = "20"
    ),
    CuratedPropertyDef(
      key          = "home-table-link-to",
      category     = "admin",
      baseName     = "oxf.fr.home.table.link-to",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "summary new"
    ),
    CuratedPropertyDef(
      key          = "home-search-options-opened-on-load",
      category     = "admin",
      baseName     = "oxf.fr.home.search-options-opened-on-load",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "home-remote-servers",
      category     = "admin",
      baseName     = "oxf.fr.home.remote-servers",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "[]"
    ),

    // 9. Import & Export Pages
    CuratedPropertyDef(
      key          = "export-page-enable",
      category     = "import",
      baseName     = "oxf.fr.export.page.enable",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "import-allow-invalid-documents",
      category     = "import",
      baseName     = "oxf.fr.import.allow-invalid-documents",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "import-allow-delete-existing-records",
      category     = "import",
      baseName     = "oxf.fr.import.allow-delete-existing-records",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "import-restrict-to-latest-version",
      category     = "import",
      baseName     = "oxf.fr.import.restrict-to-latest-version",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "import-allowed-formats",
      category     = "import",
      baseName     = "oxf.fr.import.allowed-formats",
      xsType       = "xs:string",
      hasAppForm   = true,
      defaultValue = "excel-headings xml-form-structure-and-data"
    ),
    CuratedPropertyDef(
      key          = "import-show-app-form-names",
      category     = "import",
      baseName     = "oxf.fr.import.show-app-form-names",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "import-buttons",
      category     = "import",
      baseName     = "oxf.fr.import.buttons",
      xsType       = "xs:string",
      hasAppForm   = true,
      defaultValue = "home summary close"
    ),

    // 7. PDF
    CuratedPropertyDef(
      key          = "rendered-page-orientation",
      category     = "pdf",
      baseName     = "oxf.fr.detail.rendered-page-orientation",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "portrait"
    ),
    CuratedPropertyDef(
      key          = "rendered-page-size",
      category     = "pdf",
      baseName     = "oxf.fr.detail.rendered-page-size",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "letter"
    ),
    CuratedPropertyDef(
      key           = "pdf-filename",
      category      = "pdf",
      baseName      = "oxf.fr.detail.pdf.filename",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles  = true,
      defaultValue  = "concat(fr:form-title(), ' - ', substring(fr:document-id(), 1, 16), '.pdf')",
      validationOpt = Some("xpath2")
    ),
    CuratedPropertyDef(
      key          = "pdf-accessibility",
      category     = "pdf",
      baseName     = "oxf.fr.pdf.accessibility",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "pdf-a",
      category     = "pdf",
      baseName     = "oxf.fr.pdf.pdf/a",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "none"
    ),
    CuratedPropertyDef(
      key          = "pdf-color-mode",
      category     = "pdf",
      baseName     = "oxf.fr.detail.pdf.color-mode",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "black-and-white"
    ),
    CuratedPropertyDef(
      key          = "pdf-hyperlinks",
      category     = "pdf",
      baseName     = "oxf.fr.detail.pdf.hyperlinks",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "pdf-barcode",
      category     = "pdf",
      baseName     = "oxf.fr.detail.pdf.barcode",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "pdf-disable-if-invalid",
      category     = "pdf",
      baseName     = "oxf.fr.detail.pdf.disable-if-invalid",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "pdf-long-content-threshold",
      category     = "pdf",
      baseName     = "oxf.fr.detail.pdf.long-content-threshold",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "512"
    ),
    CuratedPropertyDef(
      key          = "pdf-jpeg-compression",
      category     = "pdf",
      baseName     = "oxf.fr.pdf.jpeg.compression",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "0.9"
    ),
    CuratedPropertyDef(
      key          = "pdf-font-family",
      category     = "pdf",
      baseName     = "oxf.fr.pdf.font.family.inter",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "Helvetica Neue"
    ),
    CuratedPropertyDef(
      key          = "pdf-template-font-paths",
      category     = "pdf",
      baseName     = "oxf.fr.pdf.template.font.paths",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "pdf-header-footer-default",
      category     = "pdf",
      baseName     = "oxf.fr.detail.pdf.header-footer.default",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),

    // 8. Email
    CuratedPropertyDef(
      key          = "email-transport",
      category     = "email",
      baseName     = "oxf.fr.email.transport",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "smtp"
    ),
    CuratedPropertyDef(
      key          = "email-smtp-host",
      category     = "email",
      baseName     = "oxf.fr.email.smtp.host",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-smtp-port",
      category     = "email",
      baseName     = "oxf.fr.email.smtp.port",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-smtp-username",
      category     = "email",
      baseName     = "oxf.fr.email.smtp.username",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-smtp-encryption",
      category     = "email",
      baseName     = "oxf.fr.email.smtp.encryption",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-from",
      category     = "email",
      baseName     = "oxf.fr.email.from",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-reply-to",
      category     = "email",
      baseName     = "oxf.fr.email.reply-to",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-to",
      category     = "email",
      baseName     = "oxf.fr.email.to",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-cc",
      category     = "email",
      baseName     = "oxf.fr.email.cc",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-bcc",
      category     = "email",
      baseName     = "oxf.fr.email.bcc",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "email-attach-pdf",
      category     = "email",
      baseName     = "oxf.fr.email.attach-pdf",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "email-attach-xml",
      category     = "email",
      baseName     = "oxf.fr.email.attach-xml",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "email-attach-tiff",
      category     = "email",
      baseName     = "oxf.fr.email.attach-tiff",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "email-attach-excel",
      category     = "email",
      baseName     = "oxf.fr.email.attach-excel-with-named-ranges",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "email-attach-xml-export",
      category     = "email",
      baseName     = "oxf.fr.email.attach-xml-form-structure-and-data",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "email-attach-files",
      category     = "email",
      baseName     = "oxf.fr.email.attach-files",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "all"
    ),

    // 9. Form Builder
    CuratedPropertyDef(
      key          = "fb-menu-schema",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.schema",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-pdf",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.pdf",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-edit-source",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.edit-source",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-workflow",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.workflow",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-permissions",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.permissions",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-email",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.email",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-confirmation",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.confirmation",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-messages",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.messages",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-processes",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.processes",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-services-http",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.services.http",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-services-database",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.services.database",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-menu-actions",
      category     = "form-builder",
      baseName     = "oxf.fb.menu.actions",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-form-title-required",
      category     = "form-builder",
      baseName     = "oxf.fb.form-title.required",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "fb-form-description-required",
      category     = "form-builder",
      baseName     = "oxf.fb.form-description.required",
      xsType       = "xs:boolean",
      hasAppForm   = true,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "fb-publish-version",
      category     = "form-builder",
      baseName     = "oxf.fb.publish.version",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "default-to-select"
    ),
    CuratedPropertyDef(
      key          = "fb-metadata-logo",
      category     = "form-builder",
      baseName     = "oxf.fb.metadata.logo",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),
    CuratedPropertyDef(
      key          = "fb-mcp-enable",
      category     = "form-builder",
      baseName     = "oxf.fb.mcp.enable",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key          = "fb-mcp-token-enable",
      category     = "form-builder",
      baseName     = "oxf.fb.mcp.token.enable",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "true"
    ),

    // 10. Global Input / Output Formats
    CuratedPropertyDef(
      key          = "format-input-date",
      category     = "formats",
      baseName     = "oxf.xforms.format.input.date",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "[M]/[D]/[Y]"
    ),
    CuratedPropertyDef(
      key          = "format-input-time",
      category     = "formats",
      baseName     = "oxf.xforms.format.input.time",
      xsType       = "xs:string",
      hasAppForm   = false,
      defaultValue = "[h]:[m]:[s] [P,*-2]"
    ),
    CuratedPropertyDef(
      key           = "format-output-date",
      category      = "formats",
      baseName      = "oxf.xforms.format.output.date",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "if (. castable as xs:date) then format-date(xs:date(.), '[M]/[D]/[Y]', xxf:lang(), (), ()) else .",
      validationOpt = Some("xpath2")
    ),
    CuratedPropertyDef(
      key           = "format-output-datetime",
      category      = "formats",
      baseName      = "oxf.xforms.format.output.dateTime",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[M]/[D]/[Y] [h]:[m]:[s] [P,*-2]', xxf:lang(), (), ()) else .",
      validationOpt = Some("xpath2")
    ),
    CuratedPropertyDef(
      key           = "format-output-time",
      category      = "formats",
      baseName      = "oxf.xforms.format.output.time",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "if (. castable as xs:time) then format-time(xs:time(.), '[h]:[m]:[s] [P,*-2]', xxf:lang(), (), ()) else .",
      validationOpt = Some("xpath2")
    ),
    CuratedPropertyDef(
      key           = "format-output-decimal",
      category      = "formats",
      baseName      = "oxf.xforms.format.output.decimal",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "if (. castable as xs:decimal) then format-number(xs:decimal(.),'#,##0.00') else .",
      validationOpt = Some("xpath2")
    ),
    CuratedPropertyDef(
      key           = "format-output-integer",
      category      = "formats",
      baseName      = "oxf.xforms.format.output.integer",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "if (. castable as xs:integer) then format-number(xs:integer(.),'#,##0') else .",
      validationOpt = Some("xpath2")
    ),

    // 11. Authentication & Security
    CuratedPropertyDef(
      key          = "auth-user-menu-enable",
      category     = "security",
      baseName     = "oxf.fr.authentication.user-menu.enable",
      xsType       = "xs:boolean",
      hasAppForm   = false,
      defaultValue = "false"
    ),
    CuratedPropertyDef(
      key           = "auth-user-menu-uri-login",
      category      = "security",
      baseName      = "oxf.fr.authentication.user-menu.uri.login",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "/fr/auth?source={(xxf:get-request-parameter('source'), xxf:get-request-path())[1]}",
      validationOpt = Some("xpath2-avt")
    ),
    CuratedPropertyDef(
      key           = "auth-user-menu-uri-logout",
      category      = "security",
      baseName      = "oxf.fr.authentication.user-menu.uri.logout",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "/fr/logout?source={xxf:get-request-path()}",
      validationOpt = Some("xpath2-avt")
    ),
    CuratedPropertyDef(
      key           = "auth-user-menu-uri-register",
      category      = "security",
      baseName      = "oxf.fr.authentication.user-menu.uri.register",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "",
      validationOpt = Some("xpath2-avt")
    ),
    CuratedPropertyDef(
      key          = "permissions",
      category     = "security",
      baseName     = "oxf.fr.permissions",
      xsType       = "xs:string",
      hasAppForm   = true,
      defaultValue = ""
    ),
    CuratedPropertyDef(
      key          = "access-token-validity",
      category     = "security",
      baseName     = "oxf.fr.access-token.validity",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "0"
    ),
    CuratedPropertyDef(
      key          = "state-token-validity",
      category     = "security",
      baseName     = "oxf.fr.state-token.validity",
      xsType       = "xs:integer",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "15"
    )
  )

  private val CuratedByGlobalName: Map[String, CuratedPropertyDef] =
    CuratedProperties.flatMap(c => (c.globalPropertyName :: c.legacyGlobalPropertyNames).map(_ -> c)).toMap

  private val CuratedAppFormList: List[CuratedPropertyDef] =
    CuratedProperties.filter(_.hasAppForm).sortBy(-_.baseName.length)

  private val StandardNamespacePrefixes: Set[String] =
    Set("", "xml", "xs")

  private val StandardPropertyNamespaces: List[(String, String)] = List(
    "xs"    -> XMLConstants.XSD_URI,
    "oxf"   -> "http://www.orbeon.com/oxf/processors",
    "fn"    -> "http://www.w3.org/2005/xpath-functions",
    "math"  -> "http://www.w3.org/2005/xpath-functions/math",
    "map"   -> "http://www.w3.org/2005/xpath-functions/map",
    "array" -> "http://www.w3.org/2005/xpath-functions/array",
    "xh"    -> XMLConstants.XHTML_NAMESPACE_URI,
    "xf"    -> "http://www.w3.org/2002/xforms",
    "xxf"   -> "http://orbeon.org/oxf/xml/xforms",
    "exf"   -> "http://www.exforms.org/exf/1-0",
    "xbl"   -> "http://www.w3.org/ns/xbl",
    "xxbl"  -> "http://orbeon.org/oxf/xml/xbl",
    "fr"    -> "http://orbeon.org/oxf/xml/form-runner",
    "fb"    -> "http://orbeon.org/oxf/xml/form-builder",
    "frf"   -> "java:org.orbeon.oxf.fr.FormRunner",
    "saxon" -> "http://saxon.sf.net/"
  )

  def propertiesDomToConfigurationDom(propertiesDoc: Document): Document = {
    val rootOpt = Option(propertiesDoc.getRootElement)

    val globalCuratedValues  = mutable.Map.empty[String, String]
    val globalResourceValues = mutable.LinkedHashMap.empty[ResourceOverrideKey, String]
    val globalResourceHtml   = mutable.Map.empty[ResourceOverrideKey, Boolean]
    val appFormOverrides     = mutable.LinkedHashMap.empty[OverrideScope, mutable.Map[String, String]]
    val appFormResources     = mutable.LinkedHashMap.empty[OverrideScope, mutable.LinkedHashMap[ResourceOverrideKey, String]]
    val appFormResourceHtml  = mutable.Map.empty[OverrideScope, mutable.Map[ResourceOverrideKey, Boolean]]
    val customProperties     = mutable.ListBuffer.empty[CustomProperty]
    val customNamespaces     = mutable.LinkedHashMap.empty[String, String]

    def collectNamespaces(elem: Element): Unit =
      for {
        ns <- elem.declaredNamespacesIterator
        if ! StandardNamespacePrefixes(ns.prefix) && ns.uri.nonEmpty
      } customNamespaces.put(ns.prefix, ns.uri)

    def tryMatchResourceProperty(
      name         : String,
      asType       : String,
      value        : String,
      profiles     : String,
      processorName: String
    ): Boolean =
      if (profiles.isEmpty && processorName.isEmpty && name.startsWith(ResourcePropertyPrefix)) {
        val suffix = name.substring(ResourcePropertyPrefix.length)
        suffix.splitTo[List](".") match {
          case app :: form :: lang :: rest
            if app.nonEmpty && form.nonEmpty && lang.nonEmpty && rest.nonEmpty &&
               AppForm(app, form) != AppForm.FormBuilder &&
               AppForm(app, form) != AppForm.FormConfiguration =>
            if (asType == "xs:boolean" && rest.length >= 2 && rest.last == HtmlSuffix) {
              val key    = ResourceOverrideKey(lang, rest.init.mkString("."))
              val isHtml = value.trim == "true"
              if (app == "*" && form == "*") {
                if (! globalResourceHtml.contains(key))
                  globalResourceHtml.put(key, isHtml)
              } else {
                val scope   = OverrideScope(app, form, "")
                val htmlMap = appFormResourceHtml.getOrElseUpdate(scope, mutable.Map.empty)
                if (! htmlMap.contains(key))
                  htmlMap.put(key, isHtml)
              }
              true
            } else if (asType == "xs:string" && ! (rest.length >= 2 && rest.last == HtmlSuffix)) {
              val key = ResourceOverrideKey(lang, rest.mkString("."))
              if (app == "*" && form == "*") {
                if (! globalResourceValues.contains(key)) {
                  globalResourceValues.put(key, value)
                  true
                } else {
                  false
                }
              } else {
                val scope = OverrideScope(app, form, "")
                appFormOverrides.getOrElseUpdate(scope, mutable.Map.empty)
                val resMap = appFormResources.getOrElseUpdate(scope, mutable.LinkedHashMap.empty)
                if (! resMap.contains(key)) {
                  resMap.put(key, value)
                  true
                } else {
                  false
                }
              }
            } else {
              false
            }
          case _ =>
            false
        }
      } else {
        false
      }

    rootOpt.foreach { root =>
      collectNamespaces(root)

      for (propElem <- root.elements("property")) {
        collectNamespaces(propElem)

        val name          = propElem.attributeValueOpt("name").flatMap(_.trimAllToOpt).getOrElse("")
        val asType        = propElem.attributeValueOpt("as").flatMap(_.trimAllToOpt).getOrElse("xs:string")
        val value         = propElem.attributeValueOpt("value").getOrElse(propElem.getText)
        val profiles      = propElem.attributeValueOpt("profiles").orElse(propElem.attributeValueOpt("profile")).map(_.splitTo[List]().mkString(" ")).getOrElse("")
        val processorName = propElem.attributeValueOpt("processor-name").flatMap(_.trimAllToOpt).getOrElse("")

        if (! tryMatchResourceProperty(name, asType, value, profiles, processorName)) {
          val matchedGlobal =
            if (profiles.isEmpty && processorName.isEmpty)
              CuratedByGlobalName.get(name).filter(c => c.xsType == asType && ! globalCuratedValues.contains(c.key))
            else
              None

          matchedGlobal match {
            case Some(curated) =>
              globalCuratedValues.put(curated.key, value)
            case None =>
              val matchedAppForm =
                if (processorName.isEmpty)
                  CuratedAppFormList.view.flatMap { c =>
                    val prefix = c.baseName + "."
                    if (c.xsType == asType && (profiles.isEmpty || c.hasProfiles) && name.startsWith(prefix)) {
                      val suffix = name.substring(prefix.length)
                      suffix.splitTo[List](".") match {
                        case List(app, form)
                          if app.nonEmpty && form.nonEmpty &&
                             (app != "*" || form != "*" || profiles.nonEmpty) &&
                             AppForm(app, form) != AppForm.FormBuilder        &&
                             AppForm(app, form) != AppForm.FormConfiguration =>
                          val scope = OverrideScope(app, form, profiles)
                          if (! appFormOverrides.get(scope).exists(_.contains(c.key)))
                            Some((scope, c, value))
                          else
                            None
                        case _ =>
                          None
                      }
                    } else {
                      None
                    }
                  }.headOption
                else
                  None

              matchedAppForm match {
                case Some((scope, curated, v)) =>
                  val map = appFormOverrides.getOrElseUpdate(scope, mutable.Map.empty)
                  map.put(curated.key, v)
                case None =>
                  if (name.nonEmpty)
                    customProperties += CustomProperty(
                      name          = name,
                      xsType        = asType,
                      value         = value,
                      profiles      = profiles,
                      processorName = processorName
                    )
              }
          }
        }
      }
    }

    val configDoc  = Document("configuration")
    val configRoot = configDoc.getRootElement

    val allConfigNamespaces = mutable.LinkedHashMap.from(StandardPropertyNamespaces) ++ customNamespaces
    for ((prefix, uri) <- allConfigNamespaces)
      configRoot.add(Namespace(prefix, uri))

    def appendCuratedBlock(parent: Element, values: scala.collection.Map[String, String], appFormOnly: Boolean): Unit = {
      val curatedElem = parent.addElement("curated")
      for (category <- Categories) {
        val propsInCategory = CuratedProperties.filter(c => c.category == category && (! appFormOnly || c.hasAppForm))
        if (propsInCategory.nonEmpty) {
          val catElem = curatedElem.addElement(category)
          for (c <- propsInCategory) {
            val elem = catElem.addElement(c.key)
            elem.addAttribute("type", c.xsType)
            elem.addAttribute("default", c.defaultValue)
            elem.addAttribute("name", if (appFormOnly) c.baseName else c.globalPropertyName)
            elem.addAttribute("has-profiles", c.hasProfiles.toString)
            c.validationOpt.foreach(elem.addAttribute("validation", _))
            values.get(c.key) match {
              case Some(v) =>
                elem.addAttribute("override", "true")
                elem.addAttribute("value", v)
                elem.addAttribute("selection", v)
              case None =>
                elem.addAttribute("override", "false")
                elem.addAttribute("value", c.defaultValue)
                elem.addAttribute("selection", "__default__")
            }
          }
        }
      }
    }

    def appendResourcesBlock(
      parent   : Element,
      resources: scala.collection.Map[ResourceOverrideKey, String],
      htmlMap  : scala.collection.Map[ResourceOverrideKey, Boolean]
    ): Unit = {
      val resourcesElem = parent.addElement("resources")
      for ((key, v) <- resources) {
        val msgElem = resourcesElem.addElement("message")
        msgElem.addAttribute(XMLConstants.XML_LANG_QNAME, key.lang)
        msgElem.addAttribute("name", key.name)
        msgElem.addAttribute("html", htmlMap.getOrElse(key, false).toString)
        msgElem.setText(v)
      }
    }

    // 1. Global curated properties & global resource overrides
    appendCuratedBlock(configRoot, globalCuratedValues, appFormOnly = false)
    appendResourcesBlock(configRoot, globalResourceValues, globalResourceHtml)

    // 2. Application / Form / Profile overrides
    val overridesElem = configRoot.addElement("app-form-overrides")
    for ((scope, values) <- appFormOverrides) {
      val overrideElem = overridesElem.addElement("override")
      overrideElem.addAttribute("app", scope.app)
      overrideElem.addAttribute("form", scope.form)
      overrideElem.addAttribute("profiles", scope.profiles)
      appendCuratedBlock(overrideElem, values, appFormOnly = true)
      appendResourcesBlock(
        overrideElem,
        appFormResources.getOrElse(scope, Map.empty),
        appFormResourceHtml.getOrElse(scope, Map.empty)
      )
    }

    // 3. Custom properties
    val customPropsElem = configRoot.addElement("custom-properties")
    for (cp <- customProperties) {
      val propElem = customPropsElem.addElement("property")
      propElem.addAttribute("name", cp.name)
      propElem.addAttribute("type", cp.xsType)
      propElem.addAttribute("profiles", cp.profiles)
      propElem.addAttribute("processor-name", cp.processorName)
      propElem.setText(cp.value)
    }

    // 4. Custom namespaces preserved for round-trip
    val customNsElem = configRoot.addElement("custom-namespaces")
    for ((prefix, uri) <- customNamespaces) {
      val nsElem = customNsElem.addElement("ns")
      nsElem.addAttribute("prefix", prefix)
      nsElem.addAttribute("uri", uri)
    }

    // 5. Templates for XForms repeat insertions
    val templatesElem = configRoot.addElement("templates")
    val templateOverride = templatesElem.addElement("override")
    templateOverride.addAttribute("app", "*")
    templateOverride.addAttribute("form", "*")
    templateOverride.addAttribute("profiles", "")
    appendCuratedBlock(templateOverride, Map.empty, appFormOnly = true)
    appendResourcesBlock(templateOverride, Map.empty, Map.empty)

    val templateProp = templatesElem.addElement("property")
    templateProp.addAttribute("name", "")
    templateProp.addAttribute("type", "xs:string")
    templateProp.addAttribute("profiles", "")
    templateProp.addAttribute("processor-name", "")

    configDoc
  }

  def configurationDomToPropertiesDom(configDoc: Document): Document = {
    val propertiesDoc  = Document("properties")
    val propertiesRoot = propertiesDoc.getRootElement

    val configRootOpt = Option(configDoc.getRootElement)

    // Restore custom namespaces if any, merged with standard property namespaces for XPath evaluation
    val restoredCustomNamespaces =
      for {
        configRoot  <- configRootOpt.toList
        nsContainer <- configRoot.elements("custom-namespaces")
        nsElem      <- nsContainer.elements("ns")
        prefix      <- nsElem.attributeValueOpt("prefix").flatMap(_.trimAllToOpt)
        uri         <- nsElem.attributeValueOpt("uri").flatMap(_.trimAllToOpt)
        if ! StandardNamespacePrefixes(prefix)
      } yield prefix -> uri

    val allPropsNamespaces = mutable.LinkedHashMap.from(StandardPropertyNamespaces) ++ restoredCustomNamespaces
    for ((prefix, uri) <- allPropsNamespaces)
      propertiesRoot.add(Namespace(prefix, uri))

    def appendProperty(
      name         : String,
      xsType       : String,
      value        : String,
      profiles     : String = "",
      processorName: String = ""
    ): Unit = {
      val propElem = propertiesRoot.addElement("property")
      propElem.addAttribute("as", xsType)
      propElem.addAttribute("name", name)
      if (profiles.nonEmpty)
        propElem.addAttribute("profiles", profiles)
      if (processorName.nonEmpty)
        propElem.addAttribute("processor-name", processorName)

      if (value.contains('\n') || value.contains('\r'))
        propElem.setText(value)
      else
        propElem.addAttribute("value", value)
    }

    def appendResourceProperties(resourcesElemOpt: Option[Element], app: String, form: String): Unit =
      for {
        resourcesElem <- resourcesElemOpt.toList
        msgElem       <- resourcesElem.elements("message")
        lang          <- msgElem.attributeValueOpt(XMLConstants.XML_LANG_QNAME).orElse(msgElem.attributeValueOpt("lang")).flatMap(_.trimAllToOpt)
        resName       <- msgElem.attributeValueOpt("name").flatMap(_.trimAllToOpt)
      } {
        val isHtml       = msgElem.attributeValueOpt("html").exists(_.trim == "true")
        val value        = msgElem.getText
        val fullPropName = s"$ResourcePropertyPrefix$app.$form.$lang.$resName"

        appendProperty(
          name   = fullPropName,
          xsType = "xs:string",
          value  = value
        )
        if (isHtml)
          appendProperty(
            name   = s"$fullPropName.$HtmlSuffix",
            xsType = "xs:boolean",
            value  = "true"
          )
      }

    configRootOpt.foreach { configRoot =>
      // 1. Global curated properties & global resource overrides
      for {
        curatedElem <- Option(configRoot.element("curated")).toList
        c           <- CuratedProperties
        catElem     <- Option(curatedElem.element(c.category)).toList
        propElem    <- Option(catElem.element(c.key)).toList
        if propElem.attributeValueOpt("override").contains("true")
      } appendProperty(
        name   = c.globalPropertyName,
        xsType = c.xsType,
        value  = propElem.attributeValueOpt("value").getOrElse(propElem.getText)
      )

      appendResourceProperties(Option(configRoot.element("resources")), "*", "*")

      // 2. Application / Form / Profile overrides
      for {
        overridesElem <- Option(configRoot.element("app-form-overrides")).toList
        overrideElem  <- overridesElem.elements("override")
        app           <- overrideElem.attributeValueOpt("app").flatMap(_.trimAllToOpt)
        form          <- overrideElem.attributeValueOpt("form").flatMap(_.trimAllToOpt)
      } {
        val profiles = overrideElem.attributeValueOpt("profiles").map(_.splitTo[List]().mkString(" ")).getOrElse("")
        for {
          curatedElem <- Option(overrideElem.element("curated")).toList
          c           <- CuratedProperties
          if c.hasAppForm && (profiles.isEmpty || c.hasProfiles)
          catElem     <- Option(curatedElem.element(c.category)).toList
          propElem    <- Option(catElem.element(c.key)).toList
          if propElem.attributeValueOpt("override").contains("true")
        } appendProperty(
          name     = c.appFormPropertyName(app, form),
          xsType   = c.xsType,
          value    = propElem.attributeValueOpt("value").getOrElse(propElem.getText),
          profiles = profiles
        )

        if (profiles.isEmpty)
          appendResourceProperties(Option(overrideElem.element("resources")), app, form)
      }

      // 3. Custom properties
      for {
        customPropsElem <- Option(configRoot.element("custom-properties")).toList
        propElem        <- customPropsElem.elements("property")
        name            <- propElem.attributeValueOpt("name").flatMap(_.trimAllToOpt)
      } {
        val xsType        = propElem.attributeValueOpt("type").flatMap(_.trimAllToOpt).getOrElse("xs:string")
        val profiles      = propElem.attributeValueOpt("profiles").flatMap(_.trimAllToOpt).getOrElse("")
        val processorName = propElem.attributeValueOpt("processor-name").flatMap(_.trimAllToOpt).getOrElse("")
        val value         = propElem.getText

        appendProperty(
          name          = name,
          xsType        = xsType,
          value         = value,
          profiles      = profiles,
          processorName = processorName
        )
      }
    }

    propertiesDoc
  }
}
