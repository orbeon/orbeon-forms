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

import org.orbeon.css.CSSSelectorParser
import org.orbeon.dom.{Document, Element, Namespace, QName}
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.fr.persistence.PersistencePropertyProvider
import org.orbeon.oxf.fr.process.{ProcessEditorParams, ProcessEditorUI, ProcessParser}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.xbl.BindingDescriptor
import org.orbeon.oxf.xml.{ParserConfiguration, XMLConstants}
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.xbl.QuantitySupport
import org.orbeon.xforms.XFormsNames
import org.orbeon.xml.NamespaceMapping

import scala.collection.mutable
import scala.util.Try


trait FormRunnerConfiguration {

  import FormRunnerConfiguration.*

  //@XPathFunction
  def invalidatePersistencePropertiesCache(): Unit =
    PersistencePropertyProvider.invalidateCache()

  //@XPathFunction
  def configuredPropertyProfiles(): Array[String] =
    Properties.instance.getPropertySet.allProfiles.toArray.sorted

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
  def normalizeConfigurationOverridesXml(configNode: NodeInfo): DocumentNodeInfoType = {
    val configDoc = StaticXPath.tinyTreeToOrbeonDom(configNode)
    normalizeOverridesDom(configDoc)
    new DocumentWrapper(configDoc, null, StaticXPath.GlobalConfiguration)
  }
  //@XPathFunction
  def isValidCacheMaxSizeBytes(value: String): Boolean =
    value.trimAllToOpt match {
      case None | Some("0") | Some("-1") => true
      case Some(nonEmpty)                => ObjectCache.parseMaxSizeBytes(nonEmpty, 1024L * 1024L * 1024L).isDefined
    }

  //@XPathFunction
  def isValidAttachmentMaxSizeBytes(value: String): Boolean =
    value.trimAllToOpt match {
      case Some("-1")     => true
      case Some(nonEmpty) => Try(nonEmpty.toLong).toOption.exists(_ >= 0L)
      case None           => false
    }

  //@XPathFunction
  def dataSizeQuantityToBytes(rawAmount: String, unitCode: String): String =
    dataSizeQuantityToBytesOpt(rawAmount, unitCode) match {
      case Some(bytes) => bytes.toString
      case None        => Option(rawAmount).map(_.trimAllToEmpty).getOrElse("")
    }

  //@XPathFunction
  def isValidAttachmentSizeQuantity(rawAmount: String, unitCode: String): Boolean =
    dataSizeQuantityToBytesOpt(rawAmount, unitCode).exists(_ >= 0L)

  //@XPathFunction
  def isValidPositiveByteSizeQuantity(rawAmount: String, unitCode: String): Boolean =
    dataSizeQuantityToBytesOpt(rawAmount, unitCode).exists(_ > 0L)

  //@XPathFunction
  def isValidHeapPercentage(rawRatio: String): Boolean =
    Option(rawRatio).flatMap(_.trimAllToOpt).flatMap(s => Try(BigDecimal(s)).toOption).exists { ratio =>
      ratio > BigDecimal(0) && ratio <= BigDecimal(1)
    }

  //@XPathFunction
  def heapRatioToPercentString(rawRatio: String): String =
    Option(rawRatio).flatMap(_.trimAllToOpt).flatMap(s => Try(BigDecimal(s)).toOption) match {
      case Some(ratio) if ratio > BigDecimal(0) && ratio <= BigDecimal(1) =>
        s"${QuantitySupport.formatBigDecimalPlain(ratio * BigDecimal(100))}%"
      case _ =>
        Option(rawRatio).map(_.trimAllToEmpty).getOrElse("")
    }

  //@XPathFunction
  def isValidCacheDuration(value: String): Boolean =
    value.trimAllToOpt match {
      case None | Some("0") | Some("-1") => true
      case Some(nonEmpty)                => ObjectCache.parseDurationMillis(nonEmpty).isDefined
    }

  //@XPathFunction
  def isValidJson(value: String): Boolean =
    value.trimAllToOpt match {
      case None           => true
      case Some(nonEmpty) => io.circe.parser.parse(nonEmpty).isRight
    }

  //@XPathFunction
  def isValidLandingCards(value: String): Boolean =
    LandingCardsConfigXml.isValidLandingCards(value)

  //@XPathFunction
  def isValidPermissionsJson(value: String): Boolean =
    org.orbeon.oxf.fr.permission.PermissionsEditorUI.isValidPermissionsJson(value)

  //@XPathFunction
  def landingCardsToEditorXml(rawValue: String, fallbackValue: String): DocumentNodeInfoType = {
    val editorDoc = LandingCardsConfigXml.toEditorDom(rawValue, fallbackValue)
    new DocumentWrapper(editorDoc, null, StaticXPath.GlobalConfiguration)
  }

  //@XPathFunction
  def landingCardsFromEditorXml(editorNode: NodeInfo): String = {
    val editorDoc = StaticXPath.tinyTreeToOrbeonDom(editorNode)
    LandingCardsConfigXml.toJsonString(editorDoc)
  }

  //@XPathFunction
  def builtinDetailProcessNames(): Array[String] =
    BuiltinDetailProcessNames

  //@XPathFunction
  def defaultDetailProcessDefinition(name: String, app: String, form: String): String =
    name.trimAllToOpt.flatMap { trimmedName =>
      val effApp  = app.trimAllToOpt.getOrElse("*")
      val effForm = form.trimAllToOpt.getOrElse("*")
      Properties.instance.getPropertySet
        .getStringOrURIAsStringOpt(s"$DetailProcessPropertyPrefix$trimmedName.$effApp.$effForm")
        .orElse(Properties.instance.getPropertySet.getStringOrURIAsStringOpt(s"$DetailProcessPropertyPrefix$trimmedName.*.*"))
    }.getOrElse("")

  //@XPathFunction
  def isValidProcessDefinition(source: String): Boolean =
    source.trimAllToOpt.exists(s => Try(ProcessParser.parse(s)).isSuccess)

  //@XPathFunction
  def formatProcessDefinition(source: String): String =
    ProcessEditorUI.formatProcessString(source)

  //@XPathFunction
  def formatProcessDefinitionHtml(source: String): String =
    ProcessEditorUI.formatProcessStringToHtml(source)

  //@XPathFunction
  def availableControlSettingsXml(lang: String): DocumentNodeInfoType =
    new DocumentWrapper(ControlSettingsSupport.availableControlSettingsDom(lang), null, StaticXPath.GlobalConfiguration)

  //@XPathFunction
  def controlSettingsQName(elemOrNull: NodeInfo): String =
    Option(elemOrNull)
      .map(n => ControlSettingsSupport.canonicalQNameForNode(n.getURI, n.getPrefix, n.getLocalPart))
      .getOrElse("")

  //@XPathFunction
  def controlSettingsDisplayName(qName: String, lang: String): String =
    ControlSettingsSupport.displayNameForQName(qName, lang)

  //@XPathFunction
  def controlSettingsIconClass(qName: String): String =
    ControlSettingsSupport.iconClassForQName(qName)

  //@XPathFunction
  def buildControlSettingsSubform(qName: String, existingElemOrNull: NodeInfo, lang: String): DocumentNodeInfoType = {
    val existingOpt = Option(existingElemOrNull).map(n => StaticXPath.tinyTreeToOrbeonDom(n).getRootElement)
    val subformDoc  = ControlSettingsSupport.buildSubformDom(qName, existingOpt, lang)
    new DocumentWrapper(subformDoc, null, StaticXPath.GlobalConfiguration)
  }

  //@XPathFunction
  def extractNormalizedControlSettingsElem(qName: String, customInstanceElemOrNull: NodeInfo): DocumentNodeInfoType = {
    val elemOpt   = Option(customInstanceElemOrNull).map(n => StaticXPath.tinyTreeToOrbeonDom(n).getRootElement)
    val resultDoc = ControlSettingsSupport.extractNormalizedControlSettingsDom(qName, elemOpt)
    new DocumentWrapper(resultDoc, null, StaticXPath.GlobalConfiguration)
  }

  //@XPathFunction
  def formatControlSettingsSummaryHtml(controlElemNode: NodeInfo, lang: String, formResourcesOrNull: NodeInfo): String = {
    val controlElem      = StaticXPath.tinyTreeToOrbeonDom(controlElemNode).getRootElement
    val formResourcesOpt = Option(formResourcesOrNull).map(n => StaticXPath.tinyTreeToOrbeonDom(n).getRootElement)
    ControlSettingsSupport.formatSummaryHtml(controlElem, lang, formResourcesOpt)
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

  private val ResourcePropertyPrefix      = "oxf.fr.resource."
  private val DetailProcessPropertyPrefix = "oxf.fr.detail.process."
  private val XblPropertyPrefix           = "oxf.xforms.xbl."
  private val HtmlSuffix                  = "html"

  private val BuiltinDetailButtonProcessNames: List[String] = List(
    "save",
    "validate",
    "send",
    "email",
    "edit",
    "review",
    "new",
    "collapse-all",
    "expand-all",
    "new-to-edit",
    "wizard-prev",
    "wizard-next",
    "search-first",
    "search-prev",
    "search-next",
    "search-last"
  )

  val BuiltinDetailProcessNames: Array[String] =
    (ProcessEditorParams.BuiltinSubProcesses.filterNot(_.deprecated).map(_.name) ++ BuiltinDetailButtonProcessNames)
      .distinct
      .sorted
      .toArray

  val Categories: List[String] =
    List(
      "system",
      "appearance",
      "navbar",
      "landing",
      "summary",
      "detail",
      "wizard",
      "formulas",
      "admin",
      "pdf",
      "email",
      "import",
      "form-builder",
      "security",
      "formats",
      "caches"
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
    // 2. Caches
    CuratedPropertyDef(
      key             = "cache-documents-size",
      category        = "caches",
      baseName        = "oxf.xforms.cache.live-documents.size",
      xsType          = "xs:integer",
      hasAppForm      = false,
      defaultValue    = "500",
      validationOpt   = Some("non-negative-integer"),
      legacyBaseNames = List("oxf.xforms.cache.documents.size")
    ),
    CuratedPropertyDef(
      key             = "cache-documents-max-size-bytes",
      category        = "caches",
      baseName        = "oxf.xforms.cache.live-documents.max-size-bytes",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "15%",
      validationOpt   = Some("byte-size-or-percent"),
      legacyBaseNames = List("oxf.xforms.cache.documents.max-size-bytes")
    ),
    CuratedPropertyDef(
      key             = "cache-documents-max-idle",
      category        = "caches",
      baseName        = "oxf.xforms.cache.live-documents.max-idle",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "",
      validationOpt   = Some("duration"),
      legacyBaseNames = List("oxf.xforms.cache.documents.max-idle")
    ),
    CuratedPropertyDef(
      key             = "cache-static-state-size",
      category        = "caches",
      baseName        = "oxf.xforms.cache.static-states.size",
      xsType          = "xs:integer",
      hasAppForm      = false,
      defaultValue    = "200",
      validationOpt   = Some("non-negative-integer"),
      legacyBaseNames = List("oxf.xforms.cache.static-state.size")
    ),
    CuratedPropertyDef(
      key             = "cache-static-state-max-size-bytes",
      category        = "caches",
      baseName        = "oxf.xforms.cache.static-states.max-size-bytes",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "15%",
      validationOpt   = Some("byte-size-or-percent"),
      legacyBaseNames = List("oxf.xforms.cache.static-state.max-size-bytes")
    ),
    CuratedPropertyDef(
      key             = "cache-static-state-max-idle",
      category        = "caches",
      baseName        = "oxf.xforms.cache.static-states.max-idle",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "",
      validationOpt   = Some("duration"),
      legacyBaseNames = List("oxf.xforms.cache.static-state.max-idle")
    ),
    CuratedPropertyDef(
      key             = "cache-pipelines-size",
      category        = "caches",
      baseName        = "oxf.core.cache.pipelines.size",
      xsType          = "xs:integer",
      hasAppForm      = false,
      defaultValue    = "2000",
      validationOpt   = Some("non-negative-integer"),
      legacyBaseNames = List("oxf.cache.size", "oxf.cache.main.size")
    ),
    CuratedPropertyDef(
      key             = "cache-pipelines-max-size-bytes",
      category        = "caches",
      baseName        = "oxf.core.cache.pipelines.max-size-bytes",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "10%",
      validationOpt   = Some("byte-size-or-percent"),
      legacyBaseNames = List("oxf.cache.max-size-bytes", "oxf.cache.main.max-size-bytes")
    ),
    CuratedPropertyDef(
      key             = "cache-pipelines-max-idle",
      category        = "caches",
      baseName        = "oxf.core.cache.pipelines.max-idle",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "",
      validationOpt   = Some("duration"),
      legacyBaseNames = List("oxf.cache.max-idle", "oxf.cache.main.max-idle")
    ),
    CuratedPropertyDef(
      key             = "cache-xpath-size",
      category        = "caches",
      baseName        = "oxf.core.cache.xpath.size",
      xsType          = "xs:integer",
      hasAppForm      = false,
      defaultValue    = "20000",
      validationOpt   = Some("non-negative-integer"),
      legacyBaseNames = List("oxf.cache.xpath.size")
    ),
    CuratedPropertyDef(
      key             = "cache-xpath-max-size-bytes",
      category        = "caches",
      baseName        = "oxf.core.cache.xpath.max-size-bytes",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "5%",
      validationOpt   = Some("byte-size-or-percent"),
      legacyBaseNames = List("oxf.cache.xpath.max-size-bytes")
    ),
    CuratedPropertyDef(
      key             = "cache-xpath-max-idle",
      category        = "caches",
      baseName        = "oxf.core.cache.xpath.max-idle",
      xsType          = "xs:string",
      hasAppForm      = false,
      defaultValue    = "",
      validationOpt   = Some("duration"),
      legacyBaseNames = List("oxf.cache.xpath.max-idle")
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
      key          = "global-style-theme",
      category     = "appearance",
      baseName     = "oxf.fr.style.theme",
      xsType       = "xs:string",
      hasAppForm   = false,
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
      key           = "autosave-delay",
      category      = "detail",
      baseName      = "oxf.fr.detail.autosave-delay",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "5000",
      validationOpt = Some("non-negative-integer")
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
      key           = "messages-delay",
      category      = "detail",
      baseName      = "oxf.fr.detail.messages.delay",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "10000",
      validationOpt = Some("non-negative-integer")
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
      key           = "lease-duration",
      category      = "detail",
      baseName      = "oxf.fr.detail.lease.duration.*",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "10",
      validationOpt = Some("non-negative-integer")
    ),
    CuratedPropertyDef(
      key           = "lease-renew-threshold",
      category      = "detail",
      baseName      = "oxf.fr.detail.lease.renew-threshold",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "1",
      validationOpt = Some("non-negative-integer")
    ),
    CuratedPropertyDef(
      key           = "lease-alert-threshold",
      category      = "detail",
      baseName      = "oxf.fr.detail.lease.alert-threshold",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "2",
      validationOpt = Some("non-negative-integer")
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
      key           = "attachment-max-size-per-file",
      category      = "detail",
      baseName      = "oxf.fr.detail.attachment.max-size-per-file",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "",
      validationOpt = Some("attachment-max-size")
    ),
    CuratedPropertyDef(
      key           = "attachment-max-size-aggregate-per-control",
      category      = "detail",
      baseName      = "oxf.fr.detail.attachment.max-size-aggregate-per-control",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "",
      validationOpt = Some("attachment-max-size")
    ),
    CuratedPropertyDef(
      key           = "attachment-max-size-aggregate-per-form",
      category      = "detail",
      baseName      = "oxf.fr.detail.attachment.max-size-aggregate-per-form",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "",
      validationOpt = Some("attachment-max-size")
    ),
    CuratedPropertyDef(
      key           = "attachment-max-files-per-control",
      category      = "detail",
      baseName      = "oxf.fr.detail.attachment.max-files-per-control",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "",
      validationOpt = Some("optional-integer-or-unlimited")
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
      key          = "nonrelevant",
      category     = "formulas",
      baseName     = "oxf.fr.detail.nonrelevant",
      xsType       = "xs:string",
      hasAppForm   = true,
      hasProfiles = true,
      defaultValue = "keep"
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
      key           = "summary-page-size",
      category      = "summary",
      baseName      = "oxf.fr.summary.page-size",
      xsType        = "xs:integer",
      hasAppForm    = true,
      defaultValue  = "20",
      validationOpt = Some("positive-integer")
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
      key           = "landing-page-size",
      category      = "landing",
      baseName      = "oxf.fr.landing.page-size",
      xsType        = "xs:integer",
      hasAppForm    = false,
      defaultValue  = "8",
      validationOpt = Some("positive-integer")
    ),
    CuratedPropertyDef(
      key           = "landing-cards",
      category      = "landing",
      baseName      = "oxf.fr.landing.cards",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = LandingCardsJSON.serializeToJson(LandingCardsJSON.DefaultCards).spaces2,
      validationOpt = Some("landing-cards")
    ),

    // 8. Admin Page
    CuratedPropertyDef(
      key           = "home-page-size",
      category      = "admin",
      baseName      = "oxf.fr.home.page-size",
      xsType        = "xs:integer",
      hasAppForm    = false,
      defaultValue  = "20",
      validationOpt = Some("positive-integer")
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
      key           = "home-remote-servers",
      category      = "admin",
      baseName      = "oxf.fr.home.remote-servers",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "[]",
      validationOpt = Some("json")
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
      key           = "pdf-long-content-threshold",
      category      = "pdf",
      baseName      = "oxf.fr.detail.pdf.long-content-threshold",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "512",
      validationOpt = Some("non-negative-integer")
    ),
    CuratedPropertyDef(
      key           = "pdf-jpeg-compression",
      category      = "pdf",
      baseName      = "oxf.fr.pdf.jpeg.compression",
      xsType        = "xs:string",
      hasAppForm    = false,
      defaultValue  = "0.9",
      validationOpt = Some("unit-interval")
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
      key           = "pdf-header-footer-default",
      category      = "pdf",
      baseName      = "oxf.fr.detail.pdf.header-footer.default",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "",
      validationOpt = Some("json")
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
      key           = "email-smtp-port",
      category      = "email",
      baseName      = "oxf.fr.email.smtp.port",
      xsType        = "xs:string",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "",
      validationOpt = Some("optional-port")
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
      key           = "permissions",
      category      = "security",
      baseName      = "oxf.fr.permissions",
      xsType        = "xs:string",
      hasAppForm    = true,
      defaultValue  = "",
      validationOpt = Some("permissions-json")
    ),
    CuratedPropertyDef(
      key           = "access-token-validity",
      category      = "security",
      baseName      = "oxf.fr.access-token.validity",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "0",
      validationOpt = Some("non-negative-integer")
    ),
    CuratedPropertyDef(
      key           = "state-token-validity",
      category      = "security",
      baseName      = "oxf.fr.state-token.validity",
      xsType        = "xs:integer",
      hasAppForm    = true,
      hasProfiles   = true,
      defaultValue  = "15",
      validationOpt = Some("non-negative-integer")
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
    val catalog = ControlSettingsSupport.currentCatalog()

    val globalCuratedValues    = mutable.Map.empty[String, String]
    val globalResourceValues   = mutable.LinkedHashMap.empty[ResourceOverrideKey, String]
    val globalResourceHtml     = mutable.Map.empty[ResourceOverrideKey, Boolean]
    val globalProcesses        = mutable.LinkedHashMap.empty[String, String]
    val globalControlSettings  = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[QName, (String, Boolean)]]
    val appFormOverrides       = mutable.LinkedHashMap.empty[OverrideScope, mutable.Map[String, String]]
    val appFormResources       = mutable.LinkedHashMap.empty[OverrideScope, mutable.LinkedHashMap[ResourceOverrideKey, String]]
    val appFormResourceHtml    = mutable.Map.empty[OverrideScope, mutable.Map[ResourceOverrideKey, Boolean]]
    val appFormProcesses       = mutable.LinkedHashMap.empty[OverrideScope, mutable.LinkedHashMap[String, String]]
    val appFormControlSettings = mutable.LinkedHashMap.empty[OverrideScope, mutable.LinkedHashMap[String, mutable.LinkedHashMap[QName, String]]]
    val customProperties       = mutable.ListBuffer.empty[CustomProperty]
    val customNamespaces       = mutable.LinkedHashMap.empty[String, String]

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

    def tryMatchProcessProperty(
      name         : String,
      asType       : String,
      value        : String,
      profiles     : String,
      processorName: String
    ): Boolean =
      if (processorName.isEmpty && asType == "xs:string" && name.startsWith(DetailProcessPropertyPrefix)) {
        val suffix = name.substring(DetailProcessPropertyPrefix.length)
        suffix.splitTo[List](".") match {
          case List(procName, app, form)
            if ProcessEditorParams.ProcessNameRegex.matches(procName) &&
               app.nonEmpty && form.nonEmpty &&
               AppForm(app, form) != AppForm.FormBuilder &&
               AppForm(app, form) != AppForm.FormConfiguration =>
            if (app == "*" && form == "*" && profiles.isEmpty) {
              if (! globalProcesses.contains(procName)) {
                globalProcesses.put(procName, value)
                true
              } else {
                false
              }
            } else {
              val scope = OverrideScope(app, form, profiles)
              appFormOverrides.getOrElseUpdate(scope, mutable.Map.empty)
              val procMap = appFormProcesses.getOrElseUpdate(scope, mutable.LinkedHashMap.empty)
              if (! procMap.contains(procName)) {
                procMap.put(procName, value)
                true
              } else {
                false
              }
            }
          case _ =>
            false
        }
      } else {
        false
      }

    def tryMatchControlSettingProperty(
      name         : String,
      asType       : String,
      value        : String,
      profiles     : String,
      processorName: String
    ): Boolean =
      if (processorName.isEmpty && ControlSettingsSupport.SupportedPropertyAsTypes(asType) && name.startsWith(XblPropertyPrefix)) {
        val suffix = name.substring(XblPropertyPrefix.length)
        suffix.splitTo[List](".") match {
          case List(prefix, comp, param, app, form)
            if app.nonEmpty && form.nonEmpty &&
               AppForm(app, form) != AppForm.FormBuilder &&
               AppForm(app, form) != AppForm.FormConfiguration =>
            catalog.byPrefixAndLocal.get((prefix, comp)).flatMap(d => d.resolveSetting(param).map(d -> _)) match {
              case Some((descriptor, settingDef)) =>
                if (app == "*" && form == "*" && profiles.isEmpty) {
                  val attMap = globalControlSettings.getOrElseUpdate(descriptor.qNameString, mutable.LinkedHashMap.empty)
                  attMap.get(settingDef.attrQName) match {
                    case Some((_, true)) =>
                      false
                    case _ =>
                      attMap.put(settingDef.attrQName, (value, true))
                      true
                  }
                } else {
                  val scope = OverrideScope(app, form, profiles)
                  appFormOverrides.getOrElseUpdate(scope, mutable.Map.empty)
                  val compMap = appFormControlSettings.getOrElseUpdate(scope, mutable.LinkedHashMap.empty)
                  val attMap  = compMap.getOrElseUpdate(descriptor.qNameString, mutable.LinkedHashMap.empty)
                  if (! attMap.contains(settingDef.attrQName)) {
                    attMap.put(settingDef.attrQName, value)
                    true
                  } else {
                    false
                  }
                }
              case None =>
                false
            }
          case List(prefix, comp, param) if profiles.isEmpty =>
            catalog.byPrefixAndLocal.get((prefix, comp)).flatMap(d => d.resolveSetting(param).map(d -> _)) match {
              case Some((descriptor, settingDef)) =>
                val attMap = globalControlSettings.getOrElseUpdate(descriptor.qNameString, mutable.LinkedHashMap.empty)
                if (! attMap.contains(settingDef.attrQName)) {
                  attMap.put(settingDef.attrQName, (value, false))
                  true
                } else {
                  false
                }
              case None =>
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

        if (! tryMatchResourceProperty(name, asType, value, profiles, processorName) &&
            ! tryMatchProcessProperty(name, asType, value, profiles, processorName)  &&
            ! tryMatchControlSettingProperty(name, asType, value, profiles, processorName)) {
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

    val catalogNamespaces =
      for {
        d <- catalog.descriptors
        p  = d.directQName.namespace.prefix
        u  = d.directQName.namespace.uri
        if p.nonEmpty && u.nonEmpty && ! StandardNamespacePrefixes(p)
      } yield p -> u

    val allConfigNamespaces = mutable.LinkedHashMap.from(StandardPropertyNamespaces) ++ catalogNamespaces ++ customNamespaces
    for ((prefix, uri) <- allConfigNamespaces)
      configRoot.add(Namespace(prefix, uri))

    val globalControlSettingsPlain =
      globalControlSettings.map { case (qNameStr, attMap) =>
        qNameStr -> attMap.map { case (k, (v, _)) => k -> v }
      }

    // 1. Global curated properties, resource overrides, processes & control settings
    appendCuratedBlock(configRoot, globalCuratedValues, appFormOnly = false)
    appendResourcesBlock(configRoot, globalResourceValues, globalResourceHtml)
    appendProcessesBlock(configRoot, globalProcesses)
    appendControlSettingsBlock(configRoot, globalControlSettingsPlain, catalog)

    // 2. Application / Form / Profile overrides
    val overridesElem = configRoot.addElement("app-form-overrides")
    for ((scope, values) <- appFormOverrides) {
      val overrideElem = overridesElem.addElement("override")
      overrideElem.addAttribute("id", "")
      overrideElem.addAttribute("app", scope.app)
      overrideElem.addAttribute("form", scope.form)
      overrideElem.addAttribute("profiles", scope.profiles)
      appendCuratedBlock(overrideElem, values, appFormOnly = true)
      appendResourcesBlock(
        overrideElem,
        appFormResources.getOrElse(scope, Map.empty),
        appFormResourceHtml.getOrElse(scope, Map.empty)
      )
      appendProcessesBlock(
        overrideElem,
        appFormProcesses.getOrElse(scope, Map.empty)
      )
      appendControlSettingsBlock(
        overrideElem,
        appFormControlSettings.getOrElse(scope, Map.empty),
        catalog
      )
    }

    def appendEmptyCustomProperty(parent: Element): Element = {
      val propElem = parent.addElement("property")
      propElem.addAttribute("name", "")
      propElem.addAttribute("type", "xs:string")
      propElem.addAttribute("profiles", "")
      propElem.addAttribute("processor-name", "")
      propElem
    }

    // 3. Custom properties
    val customPropsElem = configRoot.addElement("custom-properties")
    if (customProperties.isEmpty) {
      appendEmptyCustomProperty(customPropsElem)
    } else {
      for (cp <- customProperties) {
        val propElem = customPropsElem.addElement("property")
        propElem.addAttribute("name", cp.name)
        propElem.addAttribute("type", cp.xsType)
        propElem.addAttribute("profiles", cp.profiles)
        propElem.addAttribute("processor-name", cp.processorName)
        propElem.setText(cp.value)
      }
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
    templateOverride.addAttribute("id", "")
    templateOverride.addAttribute("app", "*")
    templateOverride.addAttribute("form", "*")
    templateOverride.addAttribute("profiles", "")
    appendCuratedBlock(templateOverride, Map.empty, appFormOnly = true)
    appendResourcesBlock(templateOverride, Map.empty, Map.empty)
    appendProcessesBlock(templateOverride, Map.empty)
    appendControlSettingsBlock(templateOverride, Map.empty, catalog)

    appendEmptyCustomProperty(templatesElem)

    normalizeOverridesDom(configDoc)
    configDoc
  }

  private val DataUnitFactorsDesc: List[(String, Long)] = List(
    ("TB", 1_000_000_000_000L),
    ("GB", 1_000_000_000L),
    ("MB", 1_000_000L),
    ("KB", 1_000L)
  )

  private val DataUnitFactorByCode: Map[String, BigDecimal] = Map(
    "B"  -> BigDecimal(1L),
    "KB" -> BigDecimal(1_000L),
    "MB" -> BigDecimal(1_000_000L),
    "GB" -> BigDecimal(1_000_000_000L),
    "TB" -> BigDecimal(1_000_000_000_000L)
  )

  def dataSizeQuantityToBytesOpt(rawAmount: String, unitCode: String): Option[Long] =
    for {
      trimmed <- Option(rawAmount).flatMap(_.trimAllToOpt)
      bd      <- Try(BigDecimal(trimmed)).toOption
      if bd >= BigDecimal(0)
      unit     = Option(unitCode).flatMap(_.trimAllToOpt).getOrElse("MB")
      factor  <- DataUnitFactorByCode.get(unit)
      bytesBd  = bd * factor
      if bytesBd.isWhole && bytesBd <= BigDecimal(Long.MaxValue)
    } yield bytesBd.bigDecimal.longValueExact()

  def bytesToDisplayQuantity(bytes: Long): (String, String) =
    if (bytes <= 0L) {
      (bytes.toString, "B")
    } else {
      DataUnitFactorsDesc.iterator
        .filter { case (_, factor) => bytes >= factor }
        .flatMap { case (code, factor) =>
          val bd       = BigDecimal(bytes) / BigDecimal(factor)
          val stripped = BigDecimal(bd.bigDecimal.stripTrailingZeros())
          Option.when(stripped.scale <= 2)((QuantitySupport.formatBigDecimalPlain(bd), code))
        }
        .nextOption()
        .getOrElse((bytes.toString, "B"))
    }

  private def appendCuratedBlock(parent: Element, values: scala.collection.Map[String, String], appFormOnly: Boolean): Unit = {
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

          val rawOverrideOpt = values.get(c.key)
          rawOverrideOpt match {
            case Some(v) =>
              elem.addAttribute("override", "true")
              elem.addAttribute("value", v)
              elem.addAttribute("selection", if (c.key == "home-table-link-to") v.splitTo[List]().mkString(" ") else v)
            case None =>
              elem.addAttribute("override", "false")
              elem.addAttribute("value", c.defaultValue)
              elem.addAttribute("selection", "__default__")
          }

          if (c.validationOpt.contains("attachment-max-size")) {
            val modeElem = elem.addElement("mode")
            val qtyElem  = elem.addElement("quantity")
            rawOverrideOpt match {
              case None =>
                modeElem.setText("unlimited")
                qtyElem.addAttribute("unit", "MB")
              case Some(v) =>
                val trimmed = v.trim
                if (trimmed.isEmpty || trimmed == "-1") {
                  elem.addAttribute("value", "-1")
                  modeElem.setText("unlimited")
                  qtyElem.addAttribute("unit", "MB")
                } else {
                  Try(trimmed.toLong).toOption.filter(_ >= 0L) match {
                    case Some(bytes) =>
                      val (numStr, unitCode) = bytesToDisplayQuantity(bytes)
                      elem.addAttribute("value", bytes.toString)
                      modeElem.setText("size")
                      qtyElem.addAttribute("unit", unitCode)
                      qtyElem.setText(numStr)
                    case None =>
                      modeElem.setText("size")
                      qtyElem.addAttribute("unit", "B")
                      qtyElem.setText(trimmed)
                  }
                }
            }
          } else if (c.validationOpt.contains("byte-size-or-percent")) {
            val modeElem = elem.addElement("mode")
            val pctElem  = elem.addElement("percentage")
            val qtyElem  = elem.addElement("quantity")
            val defaultRatioStr =
              if (c.defaultValue.endsWith("%"))
                Try(BigDecimal(c.defaultValue.dropRight(1).trim) / BigDecimal(100))
                  .map(QuantitySupport.formatBigDecimalPlain(_))
                  .getOrElse("0.15")
              else
                "0.15"
            val effectiveRaw = rawOverrideOpt.getOrElse(c.defaultValue).trim
            if (effectiveRaw.isEmpty || effectiveRaw == "0" || effectiveRaw == "-1") {
              if (rawOverrideOpt.isDefined)
                elem.addAttribute("value", "0")
              modeElem.setText("unlimited")
              pctElem.setText(defaultRatioStr)
              qtyElem.addAttribute("unit", "MB")
            } else if (effectiveRaw.endsWith("%")) {
              val pctNumStr = effectiveRaw.dropRight(1).trim
              Try(BigDecimal(pctNumStr)).toOption.filter(p => p > BigDecimal(0) && p <= BigDecimal(100)) match {
                case Some(pctBd) =>
                  val ratioStr = QuantitySupport.formatBigDecimalPlain(pctBd / BigDecimal(100))
                  val normPct  = s"${QuantitySupport.formatBigDecimalPlain(pctBd)}%"
                  elem.addAttribute("value", normPct)
                  modeElem.setText("percent")
                  pctElem.setText(ratioStr)
                  qtyElem.addAttribute("unit", "MB")
                case None =>
                  modeElem.setText("percent")
                  pctElem.setText(Try(BigDecimal(pctNumStr) / BigDecimal(100)).map(QuantitySupport.formatBigDecimalPlain(_)).getOrElse(pctNumStr))
                  qtyElem.addAttribute("unit", "MB")
              }
            } else {
              ObjectCache.parseMaxSizeBytes(effectiveRaw, 0L) match {
                case Some(bytes) =>
                  val (numStr, unitCode) = bytesToDisplayQuantity(bytes)
                  elem.addAttribute("value", bytes.toString)
                  modeElem.setText("size")
                  pctElem.setText(defaultRatioStr)
                  qtyElem.addAttribute("unit", unitCode)
                  qtyElem.setText(numStr)
                case None =>
                  modeElem.setText("size")
                  pctElem.setText(defaultRatioStr)
                  qtyElem.addAttribute("unit", "B")
                  qtyElem.setText(effectiveRaw)
              }
            }
          }
        }
      }
    }
  }

  private def appendResourcesBlock(
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

  private def appendProcessesBlock(
    parent   : Element,
    processes: scala.collection.Map[String, String]
  ): Unit = {
    val processesElem = parent.addElement("processes")
    for ((name, v) <- processes) {
      val procElem = processesElem.addElement("process")
      procElem.addAttribute("name", name)
      procElem.setText(v)
    }
  }

  private def appendControlSettingsBlock(
    parent         : Element,
    controlSettings: scala.collection.Map[String, scala.collection.Map[QName, String]],
    catalog        : ControlSettingsSupport.Catalog
  ): Unit = {
    val containerElem = parent.addElement("control-settings")
    for {
      descriptor <- catalog.descriptors.sortBy(_.displayName("en").toLowerCase)
      attMap     <- controlSettings.get(descriptor.qNameString)
      if attMap.nonEmpty
    } {
      val compElem = containerElem.addElement(descriptor.directQName)
      for ((attrQName, v) <- attMap)
        compElem.addAttribute(attrQName, v)
    }
  }

  def normalizeOverridesDom(configDoc: Document): Unit =
    for {
      configRoot    <- Option(configDoc.getRootElement)
      overridesElem <- Option(configRoot.element("app-form-overrides"))
    } {
      val initialElems = overridesElem.elements("override").toList
      val seenScopes   = mutable.LinkedHashSet.empty[OverrideScope]
      val keptElems    = mutable.ListBuffer.empty[Element]

      for (elem <- initialElems) {
        val app         = elem.attributeValueOpt("app").flatMap(_.trimAllToOpt).getOrElse("*")
        val form        = elem.attributeValueOpt("form").flatMap(_.trimAllToOpt).getOrElse("*")
        val profileList = elem.attributeValueOpt("profiles").toList.flatMap(_.splitTo[List]()).distinct
        val profiles    = profileList.mkString(" ")
        val scope       = OverrideScope(app, form, profileList.sorted.mkString(" "))

        elem.addAttribute("app", app)
        elem.addAttribute("form", form)
        elem.addAttribute("profiles", profiles)

        if ((app != "*" || form != "*" || profiles.nonEmpty) && ! seenScopes.contains(scope)) {
          seenScopes += scope
          keptElems  += elem
        }
      }

      // Ensure an unprofiled parent Application row (app / *) exists whenever any override references `app`
      // (whether a profiled app override `app / * [p]`, a form override `app / form`, or a profiled form override `app / form [p]`)
      val appsNeedingParent =
        seenScopes.iterator.collect {
          case OverrideScope(app, _, _) if app.nonEmpty && app != "*" => app
        }.toList.distinct

      for (app <- appsNeedingParent) {
        val parentScope = OverrideScope(app, "*", "")
        if (! seenScopes.contains(parentScope)) {
          val parentElem = Element("override")
          parentElem.addAttribute("id", "")
          parentElem.addAttribute("app", app)
          parentElem.addAttribute("form", "*")
          parentElem.addAttribute("profiles", "")
          appendCuratedBlock(parentElem, Map.empty, appFormOnly = true)
          appendResourcesBlock(parentElem, Map.empty, Map.empty)
          appendProcessesBlock(parentElem, Map.empty)
          parentElem.addElement("control-settings")
          seenScopes += parentScope
          keptElems  += parentElem
        }
      }

      // Ensure an unprofiled parent Form row (app / form) exists whenever a profiled form override (app / form [p]) exists,
      // so profiled form overrides indent cleanly under their unprofiled (app / form) row
      val appFormsNeedingParent =
        seenScopes.iterator.collect {
          case OverrideScope(app, form, profiles)
            if app.nonEmpty && app != "*" && form.nonEmpty && form != "*" && profiles.nonEmpty =>
            (app, form)
        }.toList.distinct

      for ((app, form) <- appFormsNeedingParent) {
        val parentScope = OverrideScope(app, form, "")
        if (! seenScopes.contains(parentScope)) {
          val parentElem = Element("override")
          parentElem.addAttribute("id", "")
          parentElem.addAttribute("app", app)
          parentElem.addAttribute("form", form)
          parentElem.addAttribute("profiles", "")
          appendCuratedBlock(parentElem, Map.empty, appFormOnly = true)
          appendResourcesBlock(parentElem, Map.empty, Map.empty)
          appendProcessesBlock(parentElem, Map.empty)
          parentElem.addElement("control-settings")
          seenScopes += parentScope
          keptElems  += parentElem
        }
      }

      // Sort in canonical tree order:
      // Global [profile] (L1) -> App (L1) -> App [profile] (L2) -> Form (L2) -> Form [profile] (L3)
      def treeSortKey(elem: Element): ((Int, String, String), (Int, String, String), (Int, String)) = {
        val app      = elem.attributeValueOpt("app").flatMap(_.trimAllToOpt).getOrElse("*")
        val form     = elem.attributeValueOpt("form").flatMap(_.trimAllToOpt).getOrElse("*")
        val profiles = elem.attributeValueOpt("profiles").getOrElse("")
        (
          (if (app == "*") 0 else 1, app.toLowerCase, app),
          (if (form == "*") 0 else 1, form.toLowerCase, form),
          (if (profiles.isEmpty) 0 else 1, profiles)
        )
      }

      val sortedElems = keptElems.toList.sortBy(treeSortKey)

      // Assign unique non-empty @id values while preserving any existing unique @id
      val usedIds = mutable.Set.empty[String]
      var nextSeq = 1
      def freshId(): String = {
        var candidate = s"scope-$nextSeq"
        nextSeq += 1
        while (usedIds.contains(candidate)) {
          candidate = s"scope-$nextSeq"
          nextSeq += 1
        }
        usedIds += candidate
        candidate
      }

      // First pass: reserve the first occurrence of every non-empty @id so synthesized parent rows never steal an existing @id
      val preservedElems = mutable.Set.empty[Element]
      for (elem <- sortedElems) {
        elem.attributeValueOpt("id").flatMap(_.trimAllToOpt) match {
          case Some(existingId) if ! usedIds.contains(existingId) =>
            usedIds        += existingId
            preservedElems += elem
            elem.addAttribute("id", existingId)
          case _ =>
        }
      }

      // Second pass: assign fresh unique @id values to any element without a preserved @id
      for (elem <- sortedElems if ! preservedElems.contains(elem))
        elem.addAttribute("id", freshId())

      for (elem <- initialElems)
        elem.detach()
      for (elem <- sortedElems)
        overridesElem.add(elem)
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

    def appendProcessProperties(processesElemOpt: Option[Element], app: String, form: String, profiles: String = ""): Unit =
      for {
        processesElem <- processesElemOpt.toList
        procElem      <- processesElem.elements("process")
        procName      <- procElem.attributeValueOpt("name").flatMap(_.trimAllToOpt)
      } {
        val value = procElem.getText
        appendProperty(
          name     = s"$DetailProcessPropertyPrefix$procName.$app.$form",
          xsType   = "xs:string",
          value    = value,
          profiles = profiles
        )
      }

    val catalog = ControlSettingsSupport.currentCatalog()

    def appendControlSettingsProperties(controlSettingsElemOpt: Option[Element], app: String, form: String, profiles: String = ""): Unit =
      for {
        controlSettingsElem <- controlSettingsElemOpt.toList
        compElem            <- controlSettingsElem.elements
        localName            = compElem.getName
        if localName.nonEmpty
        descriptorOpt        =
          catalog.byUriAndLocal
            .get((compElem.getNamespaceURI, localName))
            .orElse(catalog.byPrefixAndLocal.get((compElem.getNamespacePrefix, localName)))
        prefix               =
          descriptorOpt
            .map(_.directQName.namespace.prefix)
            .orElse(Option(compElem.getNamespacePrefix).filter(_.nonEmpty))
            .orElse(allPropsNamespaces.collectFirst { case (p, u) if u == compElem.getNamespaceURI && p.nonEmpty => p })
            .getOrElse("")
        if prefix.nonEmpty
      } {
        for (att <- compElem.attributes) {
          val (paramName, declaredType) =
            descriptorOpt
              .flatMap(_.findSettingByAttr(att.getQName))
              .map(s => (s.paramName, s.xsType))
              .getOrElse((att.getName, "xs:string"))
          val rawVal       = att.getValue
          val xsTypeToEmit =
            if (declaredType == "xs:boolean" && (rawVal == "true" || rawVal == "false")) "xs:boolean"
            else "xs:string"
          appendProperty(
            name     = s"$XblPropertyPrefix$prefix.$localName.$paramName.$app.$form",
            xsType   = xsTypeToEmit,
            value    = rawVal,
            profiles = profiles
          )
        }
      }

    configRootOpt.foreach { configRoot =>
      // 1. Global curated properties, resource overrides, processes & control settings
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
      appendProcessProperties(Option(configRoot.element("processes")), "*", "*")
      appendControlSettingsProperties(Option(configRoot.element("control-settings")), "*", "*")

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
        appendProcessProperties(Option(overrideElem.element("processes")), app, form, profiles)
        appendControlSettingsProperties(Option(overrideElem.element("control-settings")), app, form, profiles)
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

  private[fr] object ControlSettingsSupport {

    val SupportedPropertyAsTypes: Set[String] =
      Set("xs:string", "xs:boolean", "xs:integer")

    private val ReservedControlAttLocalNames: Set[String] =
      Set("id", "bind", "ref", "appearance", "class", "autocomplete", "selection")

    private val LhhaLocalNames: Set[String] =
      Set("label", "hint", "help", "alert")

    private val FbNamespaceUri = "http://orbeon.org/oxf/xml/form-builder"
    private val XfNamespace    = Namespace("xf", "http://www.w3.org/2002/xforms")
    private val XhNamespace    = Namespace("xh", XMLConstants.XHTML_NAMESPACE_URI)

    case class ControlSettingDef(
      paramName: String,
      attrQName: QName,
      xsType   : String
    )

    case class XblControlDescriptor(
      directQName              : QName,
      displayNames             : Map[String, String],
      iconClass                : String,
      templateDefaultAtts      : Map[QName, String],
      hasExplicitCustomSettings: Boolean,
      settingsByParam          : Map[String, ControlSettingDef],
      settingsByAttr           : Map[QName, ControlSettingDef],
      settingLabels            : Map[String, Map[String, String]],
      inScopeNamespaces        : Map[String, String],
      controlDetailsElem       : Element
    ) {
      val qNameString: String =
        s"${directQName.namespace.prefix}:${directQName.localName}"

      def displayName(lang: String): String =
        Option(lang)
          .flatMap(_.trimAllToOpt)
          .flatMap(displayNames.get)
          .orElse(displayNames.get("en"))
          .orElse(displayNames.values.headOption)
          .getOrElse(directQName.localName)

      def resolveSetting(paramName: String): Option[ControlSettingDef] =
        if (hasExplicitCustomSettings)
          settingsByParam.get(paramName)
        else
          Option.when(! ReservedControlAttLocalNames(paramName))(
            ControlSettingDef(paramName, QName(paramName), "xs:string")
          )

      def findSettingByAttr(attrQName: QName): Option[ControlSettingDef] =
        settingsByAttr.get(attrQName)
    }

    case class Catalog(
      descriptors     : List[XblControlDescriptor],
      byQName         : Map[String, XblControlDescriptor],
      byPrefixAndLocal: Map[(String, String), XblControlDescriptor],
      byUriAndLocal   : Map[(String, String), XblControlDescriptor]
    )

    @volatile private var cachedCatalogOpt: Option[(List[String], Catalog)] = None

    def currentCatalog(): Catalog = {
      val uris = configuredToolboxXblUris()
      cachedCatalogOpt match {
        case Some((cachedUris, catalog)) if cachedUris == uris =>
          catalog
        case _ =>
          val built = buildCatalog(uris)
          cachedCatalogOpt = Some((uris, built))
          built
      }
    }

    private def configuredToolboxXblUris(): List[String] = {
      val propertySet = Properties.instance.getPropertySet
      val groupNames =
        propertySet
          .propertiesStartsWith("oxf.fb.toolbox.group")
          .flatMap(_.splitTo[List](".").lift(4))
          .distinct

      groupNames.flatMap { g =>
        propertySet
          .getStringOrURIAsStringOpt(s"oxf.fb.toolbox.group.$g.uri.*.*")
          .toList
          .flatMap(_.splitTo[List]())
      }.distinct
    }

    private def buildCatalog(uris: List[String]): Catalog = {
      val rm          = ResourceManagerWrapper.instance
      val seenQNames  = mutable.Set.empty[String]
      val descriptors = mutable.ListBuffer.empty[XblControlDescriptor]

      for {
        uri <- uris
        key  = uri.stripPrefix("oxf:")
        if key.nonEmpty
        doc <- Try(rm.getContentAsOrbeonDom(key, ParserConfiguration.XIncludeOnly, false)).toOption.toList
        root <- Option(doc.getRootElement).toList
        bindingElem <- root.elements(XFormsNames.XBL_BINDING_QNAME)
        if Option(bindingElem.element(XFormsNames.XBL_TEMPLATE_QNAME)).isDefined
        metadataElem       <- bindingElem.elements.find(_.getName == "metadata").toList
        controlDetailsElem <- metadataElem.elements.find(_.getName == "control-details").toList
        if ! metadataElem.elements.find(_.getName == "form-settings").exists(_.attributeValueOpt("show").contains("false"))
      } {
        val inScopeNs       = bindingElem.allInScopeNamespacesAsStrings
        val nsMapping       = NamespaceMapping(inScopeNs)
        val rawElemSelector = bindingElem.attributeValueOpt("element").getOrElse("")

        val directQNameOpt =
          for {
            rawDirect <- Try(CSSSelectorParser.parseSelectors(rawElemSelector)).toOption.toList.flatten
              .collectFirst(BindingDescriptor.directBindingPF(nsMapping, None))
              .flatMap(_.elementName)
            nsUri  = rawDirect.namespace.uri
            prefix <- inScopeNs.collectFirst { case (p, `nsUri`) if p.nonEmpty => p }
          } yield QName(rawDirect.localName, Namespace(prefix, nsUri))

        for {
          directQName <- directQNameOpt
          qNameStr     = s"${directQName.namespace.prefix}:${directQName.localName}"
          if ! seenQNames.contains(qNameStr)
        } {
          seenQNames += qNameStr

          val displayNames =
            (for {
              dn   <- metadataElem.elements.filter(_.getName == "display-name")
              lang <- dn.attributeValueOpt("lang").orElse(dn.attributeValueOpt(XMLConstants.XML_LANG_QNAME)).flatMap(_.trimAllToOpt)
              text <- dn.getTextTrim.trimAllToOpt
            } yield lang -> text).toMap

          val iconClass =
            metadataElem.elements
              .find(_.getName == "icon")
              .flatMap(_.elements.find(_.getName == "icon-class"))
              .flatMap(_.getTextTrim.trimAllToOpt)
              .getOrElse("fa fa-fw fa-sliders")

          val viewElemOpt =
            metadataElem.elements
              .find(_.getName == "templates")
              .flatMap(_.elements.find(_.getName == "view"))
              .flatMap(_.elements.headOption)
              .orElse(metadataElem.elements.find(_.getName == "template").flatMap(_.elements.headOption))

          val templateDefaultAtts =
            viewElemOpt.toList.flatMap(_.attributes).collect {
              case att
                if ! ReservedControlAttLocalNames(att.getName) &&
                   att.getNamespaceURI != FbNamespaceUri =>
                att.getQName -> att.getValue
            }.toMap

          val customSettingsElemOpt = metadataElem.elements.find(_.getName == "custom-settings")
          val parsedSettings =
            for {
              csElem      <- customSettingsElemOpt.toList
              settingElem <- csElem.elements.filter(_.getName == "setting")
              if settingElem.attributeValueOpt("ref").isEmpty
              rawName     <- settingElem.attributeValueOpt("name").flatMap(_.trimAllToOpt)
              (pfxOpt, bareName) =
                if (rawName.contains(':'))
                  (Some(rawName.substring(0, rawName.indexOf(':'))), rawName.substring(rawName.indexOf(':') + 1))
                else
                  (None, rawName)
              if ! pfxOpt.contains("fb") && ! ReservedControlAttLocalNames(bareName)
              attrLocal = settingElem.attributeValueOpt("attribute").flatMap(_.trimAllToOpt).getOrElse(bareName)
              attrQName = pfxOpt match {
                case Some(p) =>
                  inScopeNs.get(p).filter(_.nonEmpty).map(u => QName(attrLocal, Namespace(p, u))).getOrElse(QName(attrLocal))
                case None =>
                  QName(attrLocal)
              }
              xsType = settingElem.attributeValueOpt("type").map(_.stripPrefix("xs:")) match {
                case Some("boolean") => "xs:boolean"
                case _               => "xs:string"
              }
            } yield ControlSettingDef(bareName, attrQName, xsType)

          val settingLabels = extractSettingLabels(controlDetailsElem)

          descriptors += XblControlDescriptor(
            directQName               = directQName,
            displayNames              = displayNames,
            iconClass                 = iconClass,
            templateDefaultAtts       = templateDefaultAtts,
            hasExplicitCustomSettings = customSettingsElemOpt.isDefined,
            settingsByParam           = parsedSettings.map(s => s.paramName -> s).toMap,
            settingsByAttr            = parsedSettings.map(s => s.attrQName -> s).toMap,
            settingLabels             = settingLabels,
            inScopeNamespaces         = inScopeNs,
            controlDetailsElem        = controlDetailsElem.createCopy
          )
        }
      }

      val list = descriptors.toList
      Catalog(
        descriptors      = list,
        byQName          = list.map(d => d.qNameString -> d).toMap,
        byPrefixAndLocal = list.map(d => (d.directQName.namespace.prefix, d.directQName.localName) -> d).toMap,
        byUriAndLocal    = list.map(d => (d.directQName.namespace.uri, d.directQName.localName) -> d).toMap
      )
    }

    def canonicalQNameForNode(uri: String, prefix: String, localName: String): String = {
      val catalog = currentCatalog()
      catalog.byUriAndLocal
        .get((Option(uri).getOrElse(""), Option(localName).getOrElse("")))
        .orElse(catalog.byPrefixAndLocal.get((Option(prefix).getOrElse(""), Option(localName).getOrElse(""))))
        .map(_.qNameString)
        .getOrElse {
          val p =
            Option(prefix)
              .filter(_.nonEmpty)
              .orElse(StandardPropertyNamespaces.collectFirst { case (sp, su) if su == uri && sp.nonEmpty => sp })
          p.fold(Option(localName).getOrElse(""))(pfx => s"$pfx:${Option(localName).getOrElse("")}")
        }
    }

    private def extractSettingLabels(controlDetailsElem: Element): Map[String, Map[String, String]] = {
      val result = mutable.Map.empty[String, mutable.Map[String, String]]

      def findResourcesInstances(elem: Element): List[Element] = {
        val selfMatch =
          if (elem.getName == "instance" && elem.attributeValueOpt("id").contains("fr-form-resources")) List(elem)
          else Nil
        selfMatch ++ elem.elements.flatMap(findResourcesInstances)
      }

      for {
        instElem <- findResourcesInstances(controlDetailsElem)
        rootElem <- instElem.elements
        resElem  <- rootElem.elements.filter(_.getName == "resource")
        lang     <- resElem.attributeValueOpt(XMLConstants.XML_LANG_QNAME).orElse(resElem.attributeValueOpt("lang")).flatMap(_.trimAllToOpt)
        child    <- resElem.elements
      } {
        val labelOpt =
          Option(child.element("label"))
            .flatMap(_.getTextTrim.trimAllToOpt)
            .orElse(Option.when(child.elements.isEmpty)(child.getTextTrim).flatMap(_.trimAllToOpt))

        for (lbl <- labelOpt) {
          val key = if (child.getName == "automatic-pdf") "pdf-appearance" else child.getName
          result.getOrElseUpdate(key, mutable.Map.empty).put(lang, lbl)
        }
      }

      result.view.mapValues(_.toMap).toMap
    }

    def availableControlSettingsDom(lang: String): Document = {
      val catalog = currentCatalog()
      val doc     = Document("components")
      val root    = doc.getRootElement
      val sorted  = catalog.descriptors.sortBy(d => (d.displayName(lang).toLowerCase, d.qNameString))

      for (d <- sorted) {
        val elem  = root.addElement("component")
        val label = d.displayName(lang)
        val enLbl = d.displayName("en")
        elem.addAttribute("qname", d.qNameString)
        elem.addAttribute("prefix", d.directQName.namespace.prefix)
        elem.addAttribute("local-name", d.directQName.localName)
        elem.addAttribute("label", label)
        elem.addAttribute("icon-class", d.iconClass)
        val searchTokens =
          (List(label, enLbl, d.qNameString, s"${XblPropertyPrefix}${d.directQName.namespace.prefix}.${d.directQName.localName}") ++
            d.settingsByParam.keys.toList)
            .map(_.toLowerCase)
            .distinct
            .mkString(" ")
        elem.addAttribute("search-text", searchTokens)
      }

      doc
    }

    def displayNameForQName(qName: String, lang: String): String =
      Option(qName)
        .flatMap(_.trimAllToOpt)
        .flatMap(currentCatalog().byQName.get)
        .map(_.displayName(lang))
        .getOrElse(Option(qName).getOrElse(""))

    def iconClassForQName(qName: String): String =
      Option(qName)
        .flatMap(_.trimAllToOpt)
        .flatMap(currentCatalog().byQName.get)
        .map(_.iconClass)
        .getOrElse("fa fa-fw fa-sliders")

    def buildSubformDom(qName: String, existingOpt: Option[Element], lang: String): Document = {
      val effLang = Option(lang).flatMap(_.trimAllToOpt).getOrElse("en")
      currentCatalog().byQName.get(Option(qName).map(_.trimAllToEmpty).getOrElse("")) match {
        case None =>
          Document(Element(QName("html", XhNamespace)))
        case Some(descriptor) =>
          val htmlElem = Element(QName("html", XhNamespace))
          val subformDoc = Document(htmlElem)

          val mergedNamespaces =
            mutable.LinkedHashMap.from(StandardPropertyNamespaces) ++
              descriptor.inScopeNamespaces.filter { case (p, u) => p.nonEmpty && u.nonEmpty && ! StandardNamespacePrefixes(p) }
          for ((prefix, uri) <- mergedNamespaces if prefix != "xh")
            htmlElem.add(Namespace(prefix, uri))

          val headElem       = htmlElem.addElement(QName("head", XhNamespace))
          val customModel    = headElem.addElement(QName("model", XfNamespace))
          customModel.addAttribute("id", "custom-model")
          val customInstance = customModel.addElement(QName("instance", XfNamespace))
          customInstance.addAttribute("id", "custom-instance")

          val controlElem = customInstance.addElement(descriptor.directQName)
          for ((k, v) <- descriptor.templateDefaultAtts)
            controlElem.addAttribute(k, v)
          for {
            existing <- existingOpt.toList
            att      <- existing.attributes
            if ! ReservedControlAttLocalNames(att.getName) && att.getNamespaceURI != FbNamespaceUri
          } controlElem.addAttribute(att.getQName, att.getValue)

          val detailsCopy = descriptor.controlDetailsElem.createCopy
          filterInlineLhhaLangs(detailsCopy, effLang)

          for {
            innerModel <- detailsCopy.elements.filter(e => e.getNamespaceURI == XfNamespace.uri && e.getName == "model")
            child      <- innerModel.elements.toList
          } {
            child.detach()
            customModel.add(child)
          }

          val bodyElem = htmlElem.addElement(QName("body", XhNamespace))
          for (child <- detailsCopy.elements.toList if ! (child.getNamespaceURI == XfNamespace.uri && child.getName == "model")) {
            child.detach()
            bodyElem.add(child)
          }

          subformDoc
      }
    }

    private def filterInlineLhhaLangs(elem: Element, requestedLang: String): Unit = {
      for (lhhaName <- LhhaLocalNames) {
        val withLang =
          elem.elements.filter { c =>
            c.getNamespaceURI == XfNamespace.uri &&
              c.getName == lhhaName &&
              c.attributeValueOpt("lang").isDefined
          }
        if (withLang.nonEmpty) {
          val targetLang =
            if (withLang.exists(_.attributeValueOpt("lang").contains(requestedLang))) requestedLang
            else "en"
          for (c <- withLang if ! c.attributeValueOpt("lang").contains(targetLang))
            c.detach()
        }
      }
      for (child <- elem.elements.toList)
        filterInlineLhhaLangs(child, requestedLang)
    }

    def extractNormalizedControlSettingsDom(qName: String, elemOpt: Option[Element]): Document = {
      val resultDoc  = Document("result")
      val resultRoot = resultDoc.getRootElement
      val catalog    = currentCatalog()

      def unwrapCustomInstanceElem(el: Element): Option[Element] =
        if (el.getNamespaceURI == XhNamespace.uri && el.getName == "html") {
          el.elements
            .find(_.getName == "head")
            .flatMap(_.elements.find(m => m.getName == "model" && m.attributeValueOpt("id").contains("custom-model")))
            .flatMap(_.elements.find(i => i.getName == "instance" && i.attributeValueOpt("id").contains("custom-instance")))
            .flatMap(_.elements.headOption)
        } else {
          Some(el)
        }

      for {
        rawElem    <- elemOpt
        sourceElem <- unwrapCustomInstanceElem(rawElem)
        descriptor <- catalog.byQName.get(Option(qName).map(_.trimAllToEmpty).getOrElse(""))
      } {
        val keptAtts =
          sourceElem.attributes.filter { att =>
            ! ReservedControlAttLocalNames(att.getName) &&
              att.getNamespaceURI != FbNamespaceUri &&
              ! descriptor.templateDefaultAtts.get(att.getQName).contains(att.getValue) &&
              (! descriptor.hasExplicitCustomSettings || descriptor.settingsByAttr.contains(att.getQName))
          }

        if (keptAtts.nonEmpty) {
          if (descriptor.directQName.namespace.prefix.nonEmpty && descriptor.directQName.namespace.uri.nonEmpty)
            resultRoot.add(descriptor.directQName.namespace)
          val cleanElem = resultRoot.addElement(descriptor.directQName)
          if (descriptor.directQName.namespace.prefix.nonEmpty && descriptor.directQName.namespace.uri.nonEmpty)
            cleanElem.add(descriptor.directQName.namespace)
          for (att <- keptAtts) {
            if (att.getNamespacePrefix.nonEmpty && att.getNamespaceURI.nonEmpty)
              cleanElem.add(att.getNamespace)
            cleanElem.addAttribute(att.getQName, att.getValue)
          }
        }
      }

      resultDoc
    }

    def formatSummaryHtml(controlElem: Element, lang: String, formResourcesOpt: Option[Element]): String = {
      val effLang       = Option(lang).flatMap(_.trimAllToOpt).getOrElse("en")
      val catalog       = currentCatalog()
      val descriptorOpt =
        catalog.byUriAndLocal
          .get((controlElem.getNamespaceURI, controlElem.getName))
          .orElse(catalog.byPrefixAndLocal.get((controlElem.getNamespacePrefix, controlElem.getName)))

      val noneLabel =
        formResourcesOpt
          .flatMap(r => Option(r.element("control-settings-section")))
          .flatMap(s => Option(s.element("none-value")))
          .flatMap(_.getTextTrim.trimAllToOpt)
          .getOrElse("(none)")

      def fallbackResourceLabel(paramName: String): Option[String] =
        paramName match {
          case "field-width" | "field-align" =>
            formResourcesOpt
              .flatMap(r => Option(r.element("xbl-control-settings")))
              .flatMap(s => Option(s.element(paramName)))
              .flatMap(_.getTextTrim.trimAllToOpt)
          case "edit-format" =>
            formResourcesOpt.flatMap(r => Option(r.element("modes"))).flatMap(m => Option(m.element("editable"))).flatMap(_.getTextTrim.trimAllToOpt)
          case "view-format" =>
            formResourcesOpt.flatMap(r => Option(r.element("modes"))).flatMap(m => Option(m.element("review"))).flatMap(_.getTextTrim.trimAllToOpt)
          case "pdf-format" =>
            formResourcesOpt.flatMap(r => Option(r.element("modes"))).flatMap(m => Option(m.element("pdf-automatic"))).flatMap(_.getTextTrim.trimAllToOpt)
          case "pdf-template-format" =>
            formResourcesOpt.flatMap(r => Option(r.element("modes"))).flatMap(m => Option(m.element("pdf-template"))).flatMap(_.getTextTrim.trimAllToOpt)
          case "open-selection" =>
            formResourcesOpt
              .flatMap(r => Option(r.element("dialog-control-settings")))
              .flatMap(d => Option(d.element("open-selection")))
              .flatMap(o => Option(o.element("label")))
              .flatMap(_.getTextTrim.trimAllToOpt)
          case _ =>
            None
        }

      val pills =
        controlElem.attributes.map { att =>
          val paramName =
            descriptorOpt
              .flatMap(_.findSettingByAttr(att.getQName))
              .map(_.paramName)
              .getOrElse(att.getName)

          val label =
            descriptorOpt
              .flatMap(_.settingLabels.get(paramName))
              .flatMap(m => m.get(effLang).orElse(m.get("en")).orElse(m.values.headOption))
              .orElse(fallbackResourceLabel(paramName))
              .getOrElse(paramName)

          val valueHtml =
            if (att.getValue.isEmpty)
              s"""<em class="fr-config-control-prop-empty">${noneLabel.escapeXmlMinimal}</em>"""
            else if (att.getValue.forall(_ == ' '))
              s"""<code class="fr-config-control-prop-value">${("\u2423" * att.getValue.length).escapeXmlMinimal}</code>"""
            else
              s"""<code class="fr-config-control-prop-value">${att.getValue.escapeXmlMinimal}</code>"""

          s"""<span class="fr-config-control-prop-pill"><span class="fr-config-control-prop-label">${label.escapeXmlMinimal}:</span> $valueHtml</span>"""
        }

      pills.mkString(" ")
    }
  }
}
