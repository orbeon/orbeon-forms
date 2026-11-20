/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.connection.ConnectionContextSupport
import org.orbeon.connection.ConnectionContextSupport.ConnectionContexts
import org.orbeon.dom
import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataFormatVersionName, DataXml}
import org.orbeon.oxf.fr.XMLNames.{FR, FRNamespace, XF, XH, XXF}
import org.orbeon.oxf.fr.permission.SimpleConstraint
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CoreCrossPlatformSupport.{properties, runtime}
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.oxf.xml.{ElemFilter, SaxonUtils, ShareableXPathStaticContext, XMLConstants}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames
import org.orbeon.xml.NamespaceMapping

import java.time.{LocalDateTime, ZoneId}
import java.util.regex.Matcher
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try


trait FormRunnerSummary {

  import FormRunnerSummary.*

  private def zoneIdToOffsetAsOfNow(zoneId: String): String = {
    // NOTE: It's unclear how right this is just around the time of transitioning from regular
    // time to daylight saving time.
    val zid = ZoneId.of(zoneId)
    zid.getRules.getOffset(LocalDateTime.now(zid)).getId
  }

  private val OffsetR = """([+-])(\d{2}):(\d{2})""".r

  private def offsetToDuration(offset: String): String =
    offset match {
      case "Z"                           => "PT0H0M"
      case OffsetR(sign, hours, minutes) => (if (sign == "+") "PT" else "-PT") + hours + 'H' + minutes + 'M'
      case other                         => throw new IllegalArgumentException(other)
    }

  //@XPathFunction
  def encodeSummarySearch(searchInstance: NodeInfo, versionOrAll: String): String =
    SummarySearchContext.encode(
      SummarySearchContext.fromSearchInstance(searchInstance, versionOrAll)
    )

  //@XPathFunction
  def decodeSummarySearch(encodedSearch: String): NodeInfo = {
    val elem =
      SummarySearchContext
        .decode(encodedSearch)
        .map(_.toDecodedElem)
        .getOrElse(<search has-structured-filters="false"/>)
    org.orbeon.scaxon.NodeConversions.elemToDocumentInfo(elem).rootElement
  }

  //@XPathFunction
  def computeSearchNavigation(
    app          : String,
    form         : String,
    mode         : String,
    documentId   : String,
    isDraft      : Boolean,
    encodedSearch: String,
    fallbackPos  : String
  ): NodeInfo = {
    implicit val indentedLogger: IndentedLogger = inScopeContainingDocument.getIndentedLogger("form-runner")
    val appForm             = AppForm(app, form)
    val fallbackPositionOpt = SummarySearchNavigation.parsePositionOpt(fallbackPos)
    val neighborsOpt        =
      encodedSearch.trimAllToOpt.flatMap { searchParam =>
        SummarySearchNavigation.resolveNeighbors(
          appForm             = appForm,
          documentId          = documentId,
          isDraft             = isDraft,
          encodedSearch       = searchParam,
          fallbackPositionOpt = fallbackPositionOpt
        )
      }
    SummarySearchNavigation.toNavigationXml(
      appForm             = appForm,
      currentMode         = mode,
      encodedSearch       = encodedSearch.trimAllToEmpty,
      fallbackPositionOpt = fallbackPositionOpt,
      neighborsOpt        = neighborsOpt
    )
  }

  //@XPathFunction
  def buildSummaryLinkButton(app: String, form: String, buttonName: String, documentId: String, lang: String): String = {

    val (mode, params) = buttonName match {
      case "excel-export" => "export" -> List("export-format" -> RenderedFormat.ExcelWithNamedRanges.entryName)
      case "xml-export"   => "export" -> List("export-format" -> RenderedFormat.XmlFormStructureAndData.entryName)
      case other          => other    -> Nil
    }

    PathUtils.recombineQuery(s"/fr/$app/$form/$mode/$documentId", "fr-language" -> lang :: params)
  }

  //@XPathFunction
  def defaultTimezoneToOffsetString: String =
    properties.getPropertyOpt("oxf.fr.default-timezone")
      .flatMap(_.stringValue.trimAllToOpt)
      .map(zoneIdToOffsetAsOfNow)
      .map(offsetToDuration)
      .orNull

  // Filter `<a>` in addition to unsafe elements as we don't want links in the summary table
  private val FilterOut: String => ElemFilter =
    s => if (s == "a") ElemFilter.Remove else ElemFilter.Keep

  //@XPathFunction
  def maybeFilterHtml(htmlString: String): String =
    htmlString
      .trimAllToOpt
      .filter(_.startsWith("<div")) // heuristic; don't close `<div>` so that it works if there are attributes
      .map(HtmlParsing.sanitizeHtmlString(_, FilterOut))
      .getOrElse(htmlString)

  //@XPathFunction
  def plainOrFilteredHtml(htmlOrPlainString: String): String =
    Option(htmlOrPlainString)
      .flatMap(_.trimAllToOpt)
      .map { trimmed =>
        if (trimmed.startsWith("<div")) // heuristic; don't close `<div>` so that it works if there are attributes
          HtmlParsing.sanitizeHtmlString(trimmed)
        else
          trimmed.escapeXmlMinimal
      }
      .getOrElse("")

  //@XPathFunction
  def searchableValues(formDoc: DocumentInfo, app: String, form: String, version: Int, resourcesRootElem: NodeInfo): NodeInfo = {
    implicit val indentedLogger: IndentedLogger = inScopeContainingDocument.getIndentedLogger("form-runner")
    implicit val propertySet   : PropertySet    = CoreCrossPlatformSupport.properties
    val appForm = AppForm(app, form)
    Index.searchableValues(
      formDoc,
      appForm,
      Some(SearchVersion.Specific(version)),
      FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm),
      providedResourcesRootElemOpt = Option(resourcesRootElem)
    ).toXML
  }

  //@XPathFunction
  def buildSummaryDynamicForm(
    formDoc          : DocumentInfo,
    templateNode     : NodeInfo,
    app              : String,
    form             : String,
    version          : Int,
    resourcesRootElem: NodeInfo
  ): NodeInfo = {
    val xfcd = inScopeContainingDocument
    implicit val indentedLogger: IndentedLogger = xfcd.getIndentedLogger("form-runner")
    implicit val propertySet   : PropertySet    = CoreCrossPlatformSupport.properties
    val appForm = AppForm(app, form)
    buildSummaryDynamicFormImpl(
      formDoc                   = formDoc,
      templateNode              = templateNode,
      appForm                   = appForm,
      databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm),
      resourcesRootElemOpt      = Option(resourcesRootElem),
      functionLibrary           = xfcd.partAnalysis.functionLibrary
    )
  }

  def buildSummaryDynamicFormImpl(
    formDoc                  : DocumentInfo,
    templateNode             : NodeInfo,
    appForm                  : AppForm,
    databaseDataFormatVersion: DataFormatVersion,
    resourcesRootElemOpt     : Option[NodeInfo],
    functionLibrary          : FunctionLibrary
  )(implicit
    indentedLogger           : IndentedLogger
  ): NodeInfo = {

    val resolvedResourcesRootOpt =
      resourcesRootElemOpt.filter(_.localname != "_")
        .orElse(
          (formDoc.rootElement / "*:head" / "*:model" / "*:instance")
            .find(_.id == "fr-form-resources")
            .flatMap(_.firstChildOpt(*))
        )

    val allControls =
      Index.searchableValues(
        formDoc                      = formDoc,
        appForm                      = appForm,
        searchVersionOpt             = None,
        databaseDataFormatVersion    = databaseDataFormatVersion,
        includeControlElem           = true,
        providedResourcesRootElemOpt = resolvedResourcesRootOpt
      ).controls.toList

    val dynamicRootElem = StaticXPath.tinyTreeToOrbeonDom(templateNode).getRootElement.createCopy
    val dynamicDoc      = dom.Document(dynamicRootElem)

    // Copy any custom/library namespace declarations from the source form root onto the dynamic document root
    val sourceFormDomRoot = StaticXPath.tinyTreeToOrbeonDom(formDoc.rootElement).getRootElement
    for {
      ns <- sourceFormDomRoot.declaredNamespacesIterator
      if ns.prefix.nonEmpty && dynamicRootElem.getNamespaceForPrefix(ns.prefix) == null
    } locally {
      dynamicRootElem.addNamespace(ns.prefix, ns.uri)
    }

    // Copy top-level resources into the dynamic model's `fr-form-resources` instance
    val resourcesInstanceElem =
      findById(dynamicRootElem, "fr-form-resources")
        .getOrElse(throw new IllegalStateException("Missing `fr-form-resources` instance in dynamic template"))

    resourcesInstanceElem.clearContent()
    val dynamicResourcesRoot =
      resolvedResourcesRootOpt match {
        case Some(resNode) =>
          val copied = StaticXPath.tinyTreeToOrbeonDom(resNode).getRootElement.createCopy
          resourcesInstanceElem.add(copied)
          copied
        case None =>
          resourcesInstanceElem.addElement("resources")
      }

    // 1. Add `<xf:bind>` elements under `fr-details-bind` for all indexed controls
    val detailsBindElem =
      findById(dynamicRootElem, "fr-details-bind")
        .getOrElse(throw new IllegalStateException("Missing `fr-details-bind` in dynamic template"))

    allControls.zipWithIndex.foreach { case (ic, idx0) =>
      val detailPos = idx0 + 1
      val bindElem  = detailsBindElem.addElement(XfBindQName)
      bindElem.addAttribute("id",   s"fr-col-$detailPos-bind")
      bindElem.addAttribute("type", ic.xsType)
      bindElem.addAttribute("ref",  detailXPathExpr(ic.xpath, detailPos))
    }

    // Track control names that appear more than once (e.g. across multiple section templates)
    val controlNameCounts = allControls.groupMapReduce(_.name)(_ => 1)(_ + _)

    val referencedVars = mutable.LinkedHashSet.empty[String]
    val isRestricted   = appForm != AppForm.FormBuilder

    // 2. Prepare visible summary controls (`@summary-show = 'true'`)
    val preparedColumns =
      for {
        (ic, idx0)  <- allControls.zipWithIndex
        if ic.summarySettings.show
        controlNode <- ic.controlElem
      } yield {
        val detailPos = idx0 + 1
        val rawElem   = StaticXPath.tinyTreeToOrbeonDom(controlNode).getRootElement.createCopy

        // Convert legacy `xf:input` bound to `date` or `time` to `fr:date` or `fr:time`
        val elem = maybeConvertLegacyDateTimeInput(rawElem, ic.xsType)

        // Check whether this control's resources are missing from top-level `fr-form-resources` (section template)
        // or collide with another indexed control sharing the same control name
        val needsDisambiguatedResources =
          controlNameCounts.getOrElse(ic.name, 0) > 1 ||
            ! hasResourceHolderInAllLangs(dynamicResourcesRoot, ic.name)

        if (needsDisambiguatedResources && ic.resources.nonEmpty) {
          val syntheticName = s"fr-summary-col-$detailPos"
          injectControlResources(dynamicResourcesRoot, syntheticName, ic.resources)
          rewriteFormResourceReferences(elem, ic.name, syntheticName)
        }

        sanitizeSummaryControlElement(elem, detailPos, isRestricted)

        collectReferencedVariables(elem, functionLibrary).foreach { varName =>
          if (! TemplateScopedVars(varName))
            referencedVars += varName
        }

        val fieldAlignOpt = elem.attributeValueOpt("field-align")
        val alignRight    =
          fieldAlignOpt.contains("right") ||
            (fieldAlignOpt.isEmpty && RightAlignedControlLocalNames(elem.getName))

        PreparedSummaryColumn(
          xpath      = ic.xpath,
          detailPos  = detailPos,
          controlElem = elem,
          alignRight = alignRight
        )
      }

    // 3. Populate `<xh:tr id="fr-summary-header-row">`
    val headerRowElem =
      findById(dynamicRootElem, "fr-summary-header-row")
        .getOrElse(throw new IllegalStateException("Missing `fr-summary-header-row` in dynamic template"))
    headerRowElem.removeAttribute(XFormsNames.ID_QNAME)

    if (preparedColumns.isEmpty) {
      val emptyTh = headerRowElem.addElement(XhThQName)
      emptyTh.addAttribute("scope", "col")
    } else {
      preparedColumns.foreach { col =>
        val sortableTh = headerRowElem.addElement(FrSortableThQName)
        if (col.alignRight)
          sortableTh.addAttribute("class", "fr-align-right")
        val escapedPath = escapeXPathSingleQuotes(col.xpath)
        sortableTh.addAttribute(
          "label",
          s"(xxf:instance('fr-search-labels-instance')/query[@path = '$escapedPath']/resources[@lang = xxf:instance('fr-language-instance')]/label, " +
          s"xxf:instance('fr-search-labels-instance')/query[@path = '$escapedPath']/resources[1]/label, " +
          s"xf:element('label'))[1]"
        )
        sortableTh.addAttribute("sort-supported", "{$sort-supported}")
        sortableTh.addAttribute("instance",       "xxf:instance('fr-order-by')")
        sortableTh.addAttribute("column",         col.xpath)
      }
    }

    // 4. Insert referenced `$other-control` variables before `<xh:tr id="fr-summary-body-row">` and populate body cells
    val documentsRepeatElem =
      findById(dynamicRootElem, "documents-repeat")
        .getOrElse(throw new IllegalStateException("Missing `documents-repeat` in dynamic template"))
    val bodyRowElem =
      findById(documentsRepeatElem, "fr-summary-body-row")
        .getOrElse(throw new IllegalStateException("Missing `fr-summary-body-row` in dynamic template"))
    bodyRowElem.removeAttribute(XFormsNames.ID_QNAME)

    if (referencedVars.nonEmpty) {
      val trIndex = documentsRepeatElem.jContent.indexOf(bodyRowElem)
      referencedVars.zipWithIndex.foreach { case (varName, offset) =>
        val varElem = dom.Element(XfVarQName)
        varElem.addAttribute("name", varName)
        val valueExpr =
          allControls.zipWithIndex.find(_._1.name == varName) match {
            case Some((targetIc, targetIdx0)) => s"details/${detailXPathExpr(targetIc.xpath, targetIdx0 + 1)}"
            case None                         => "()"
          }
        varElem.addAttribute("value", valueExpr)
        documentsRepeatElem.jContent.add(trIndex + offset, varElem)
      }
    }

    if (preparedColumns.isEmpty) {
      val emptyTd = bodyRowElem.addElement(XhTdQName)
      val linkElem = emptyTd.addElement(XhAQName)
      linkElem.addAttribute("href",  "{$current-document-href-opt}")
      linkElem.addAttribute("class", "fr-summary-cell-link{' xforms-hidden'[empty($current-document-href-opt)]}")
      linkElem.addAttribute("aria-label", "{($fr-resources/summary/titles/*[local-name() = $edit-view-opt], $edit-view-opt)[1]}")
    } else {
      preparedColumns.zipWithIndex.foreach { case (col, colIdx0) =>
        val pos          = colIdx0 + 1
        val valueGroupId = s"fr-summary-col-$pos-value"

        val tdElem = bodyRowElem.addElement(XhTdQName)
        tdElem.addAttribute("class", if (col.alignRight) "fr-td fr-align-right" else "fr-td")

        val linkElem = tdElem.addElement(XhAQName)
        linkElem.addAttribute("href",            "{$current-document-href-opt}")
        linkElem.addAttribute("class",           "fr-summary-cell-link{' xforms-hidden'[empty($current-document-href-opt)]}")
        linkElem.addAttribute("aria-labelledby", s"{xxf:client-id('$valueGroupId')}")

        val groupElem = tdElem.addElement(XfGroupQName)
        groupElem.addAttribute("id",                        valueGroupId)
        groupElem.addAttribute("bind",                      "fr-details-bind")
        groupElem.addAttribute(XxfElementQName,            "div")
        groupElem.addAttribute(XxfUpdateQName,             "full")
        groupElem.addAttribute(XxfReadonlyAppearanceQName, "static")
        if (isRestricted)
          groupElem.addAttribute(XxfFunctionsQName, "restricted")
        groupElem.addAttribute("class", "fr-summary-cell-value")
        groupElem.add(col.controlElem)
      }
    }

    StaticXPath.orbeonDomToTinyTree(dynamicDoc).rootElement
  }

  //@XPathFunction
  def showSummaryMetadataColumn(metadataElem: NodeInfo, columnName: String, app: String, form: String): Boolean = {

    val columnOpt =
      Option(metadataElem)
        .flatMap(m => (m / "summary-metadata-columns" / "column").find(_.attValue("name") == columnName))

    columnOpt match {
      case Some(column) =>

        // Use form metadata
        if (column.attValue("show") == "true") {

          // Check against role constraint, if any
          SimpleConstraint.satisfiedForUserRolesFromCurrentSession(
            rolesOpt      = column.attValueNonBlankOpt("require-role"),
            constraintOpt = column.attValueNonBlankOpt("require-role-constraint")
          )
        } else {
          // Explicitly do not show column
          false
        }

      case None =>
        // Fall back to property
        implicit val formRunnerParams: FormRunnerParams = FormRunnerParams(AppForm(app, form), "summary")
        frc.booleanFormRunnerProperty(s"oxf.fr.summary.show-$columnName")
    }
  }

  //@XPathFunction
  def duplicate(
    data          : NodeInfo,
    app           : String,
    form          : String,
    fromDocument  : String,
    toDocument    : String,
    formVersion   : String,
    workflowStage : String
  ): Unit = {

    implicit val safeRequestCtx : SafeRequestContext       = SafeRequestContext(CoreCrossPlatformSupport.externalContext)
    implicit val connectionCtx  : ConnectionContexts       = ConnectionContextSupport.findContext(Map.empty)
    implicit val xfcd           : XFormsContainingDocument = inScopeContainingDocument
    implicit val indentedLogger : IndentedLogger           = xfcd.getIndentedLogger("form-runner")
    implicit val propertySet    : PropertySet              = CoreCrossPlatformSupport.properties

    val databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form))

    Await.result(
      putWithAttachments(
        liveData           = data.root,
        migrate            = None,
        toBaseURI          = "", // local save
        fromBasePaths      = List(createFormDataBasePath(app, form, isDraft = false, fromDocument) -> formVersion.trimAllToOpt.map(_.toInt).getOrElse(1)),
        toBasePath         = createFormDataBasePath(app, form, isDraft = false, toDocument),
        filename           = DataXml,
        commonQueryString  = s"$DataFormatVersionName=${databaseDataFormatVersion.entryName}",
        forceAttachments   = true,
        formVersion        = Some(formVersion),
        workflowStage      = Some(workflowStage)
      ).unsafeToFuture(),
      Duration.Inf
    )

    // We don't need to update the attachment paths, since the caller just reads data, saves it under a different
    // document id, and then disposes of the read data.
  }
}

object FormRunnerSummary extends FormRunnerSummary {

  private val XfNamespace  = Namespace("xf",  XF)
  private val XxfNamespace = Namespace("xxf", XXF)
  private val XhNamespace  = Namespace("xh",  XH)

  private val XfBindQName                = QName("bind",                XfNamespace)
  private val XfGroupQName               = QName("group",               XfNamespace)
  private val XfVarQName                 = QName("var",                 XfNamespace)
  private val XhThQName                  = QName("th",                  XhNamespace)
  private val XhTdQName                  = QName("td",                  XhNamespace)
  private val XhAQName                   = QName("a",                   XhNamespace)
  private val FrSortableThQName          = QName("sortable-th",         FRNamespace)
  private val FrStaticReadonlyQName      = QName("static-readonly",     FRNamespace)
  private val XxfElementQName            = QName("element",             XxfNamespace)
  private val XxfUpdateQName             = QName("update",              XxfNamespace)
  private val XxfReadonlyAppearanceQName = QName("readonly-appearance", XxfNamespace)
  private val XxfFunctionsQName          = QName("functions",           XxfNamespace)
  private val XxfAllowedFunctionsQName   = QName("allowed-functions",   XxfNamespace)
  private val XmlLangQName               = XMLConstants.XML_LANG_QNAME

  private val RightAlignedControlLocalNames: Set[String] =
    Set("number", "number-stepper", "currency", "percentage")

  private val EditOnlyAppearances: Set[String] =
    Set("character-counter", "xxf:autosize")

  private val SummaryStripChildQNames: Set[QName] = Set(
    QName("index",               FRNamespace),
    QName("short-label",         FRNamespace),
    QName("iteration-label",     FRNamespace),
    QName("add-iteration-label", FRNamespace),
    QName("label",               XfNamespace),
    QName("hint",                XfNamespace),
    QName("help",                XfNamespace),
    QName("alert",               XfNamespace)
  )

  private val XPathAttributeLocalNames: Set[String] =
    Set("ref", "nodeset", "value", "context", "if", "while", "iterate")

  private val TemplateScopedVars: Set[String] = Set(
    "fr-resources",
    "form-resources",
    "parameters",
    "app",
    "form",
    "tz",
    "global-operations",
    "source-form-metadata",
    "show-created",
    "show-last-modified",
    "show-workflow-stage",
    "show-created-by",
    "show-last-modified-by",
    "metadata-columns",
    "sort-supported",
    "datelang",
    "documents",
    "have-drafts",
    "have-read-only",
    "labels-class",
    "encoded-search-context",
    "search-page-offset",
    "document",
    "current-document-name",
    "selection-token",
    "current-document-operations",
    "edit-view-opt",
    "req-parameters-opt",
    "current-document-href-opt",
    "fr-search-value",
    "fr-search-page"
  )

  private val VarRefFallbackRegex = """\$([A-Za-z_][\w.\-]*)""".r

  private case class PreparedSummaryColumn(
    xpath      : String,
    detailPos  : Int,
    controlElem: dom.Element,
    alignRight : Boolean
  )

  private def escapeXPathSingleQuotes(s: String): String =
    s.replace("'", "''")

  private def detailXPathExpr(xpath: String, detailPos: Int): String =
    s"(detail[@path = '${escapeXPathSingleQuotes(xpath)}'], detail[$detailPos])[1]"

  private def childElements(elem: dom.Element): Iterator[dom.Element] =
    elem.nodeIterator.collect { case e: dom.Element => e }

  private def descendantOrSelfElements(root: dom.Element): Iterator[dom.Element] =
    Iterator.single(root) ++ childElements(root).flatMap(descendantOrSelfElements)

  private def findById(root: dom.Element, id: String): Option[dom.Element] =
    descendantOrSelfElements(root).find(_.attributeValue("id") == id)

  private def renameElement(elem: dom.Element, newQName: QName): dom.Element = {
    val copy = dom.Element(newQName)
    elem.declaredNamespacesIterator.foreach(ns => copy.add(ns.createCopy))
    elem.attributeIterator.foreach(att => copy.add(att.createCopy))
    elem.nodeIterator.foreach(node => copy.add(node.createCopy))
    copy
  }

  private def maybeConvertLegacyDateTimeInput(elem: dom.Element, xsType: String): dom.Element =
    if (elem.getQName == XFormsNames.XFORMS_INPUT_QNAME) {
      xsType.splitTo[List](":").lastOption match {
        case Some("date") => renameElement(elem, QName("date", FRNamespace))
        case Some("time") => renameElement(elem, QName("time", FRNamespace))
        case _            => elem
      }
    } else {
      elem
    }

  private def hasResourceHolderInAllLangs(resourcesRoot: dom.Element, controlName: String): Boolean = {
    val resourceElems = childElements(resourcesRoot).filter(_.getName == "resource").toList
    resourceElems.nonEmpty && resourceElems.forall(r => childElements(r).exists(_.getName == controlName))
  }

  private def injectControlResources(
    resourcesRoot: dom.Element,
    syntheticName: String,
    resources    : List[(String, NodeInfo)]
  ): Unit =
    resources.foreach { case (lang, holderNode) =>
      val resourceElem =
        childElements(resourcesRoot)
          .find(r => r.getName == "resource" && r.attributeValue(XmlLangQName) == lang)
          .getOrElse {
            val created = resourcesRoot.addElement("resource")
            created.addAttribute(XmlLangQName, lang)
            created
          }

      childElements(resourceElem).filter(_.getName == syntheticName).toList.foreach(resourceElem.remove)

      val holderElem    = resourceElem.addElement(syntheticName)
      val srcHolderElem = StaticXPath.tinyTreeToOrbeonDom(holderNode).getRootElement
      srcHolderElem.nodeIterator.foreach(n => holderElem.add(n.createCopy))
    }

  private def rewriteFormResourceReferences(root: dom.Element, oldName: String, newName: String): Unit = {
    val target      = s"$$form-resources/$oldName"
    val pattern     = java.util.regex.Pattern.quote(target) + """(?![\p{IsLetter}\p{IsDigit}.·\-_])"""
    val replacement = Matcher.quoteReplacement(s"$$form-resources/$newName")
    descendantOrSelfElements(root).foreach { elem =>
      elem.attributes.toList.foreach { att =>
        val v = att.getValue
        if (v != null && v.contains(target))
          att.setValue(v.replaceAll(pattern, replacement))
      }
    }
  }

  private def sanitizeSummaryControlElement(elem: dom.Element, detailPos: Int, isRestricted: Boolean): Unit = {
    List("ref", "nodeset", "bind", "model").foreach { attName =>
      elem.attributeOpt(attName).foreach(elem.remove)
    }

    elem.attributeOpt("appearance").foreach { appearanceAtt =>
      val remaining = appearanceAtt.getValue.splitTo[List]().filterNot(EditOnlyAppearances)
      if (remaining.isEmpty)
        elem.remove(appearanceAtt)
      else
        appearanceAtt.setValue(remaining.mkString(" "))
    }

    elem.addAttribute("id",                      s"fr-summary-col-$detailPos-control")
    elem.addAttribute("bind",                    s"fr-col-$detailPos-bind")
    elem.addAttribute(FrStaticReadonlyQName,      "true")
    elem.addAttribute(XxfReadonlyAppearanceQName, "static")

    childElements(elem).filter(c => SummaryStripChildQNames(c.getQName)).toList.foreach(elem.remove)

    if (isRestricted) {
      descendantOrSelfElements(elem).foreach { e =>
        e.removeAttribute(XxfFunctionsQName)
        e.removeAttribute(XxfAllowedFunctionsQName)
      }
    }
  }

  private def collectReferencedVariables(
    root           : dom.Element,
    functionLibrary: FunctionLibrary
  )(implicit
    logger         : IndentedLogger
  ): Iterator[String] =
    for {
      elem    <- descendantOrSelfElements(root)
      nsMap   = NamespaceMapping(elem.allInScopeNamespacesAsStrings)
      att     <- elem.attributeIterator
      attVal  = att.getValue
      if attVal != null && attVal.contains("$")
      isXfElem = elem.getNamespaceURI == XF || elem.getNamespaceURI == XXF
      isXPath  = isXfElem && XPathAttributeLocalNames(att.getName)
      isAvt    = ! isXPath && attVal.contains("{")
      if isXPath || isAvt
      varName <- extractVariablesFromExpr(attVal, nsMap, functionLibrary, isAvt)
    } yield
      varName

  private def extractVariablesFromExpr(
    exprString     : String,
    nsMapping      : NamespaceMapping,
    functionLibrary: FunctionLibrary,
    avt            : Boolean
  )(implicit
    logger         : IndentedLogger
  ): List[String] =
    Try {
      val compiled =
        XPath.compileExpressionMinimal(
          staticContext = new ShareableXPathStaticContext(
            XPath.GlobalConfiguration,
            nsMapping,
            functionLibrary
          ),
          xpathString   = exprString,
          avt           = avt
        )
      SaxonUtils.iterateExternalVariableReferences(compiled).toList
    }.getOrElse {
      VarRefFallbackRegex.findAllMatchIn(exprString).map(_.group(1)).toList
    }
}