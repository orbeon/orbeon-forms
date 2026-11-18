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
package org.orbeon.oxf.fb

import org.orbeon.dom.QName
import org.orbeon.oxf.fb.UndoAction.{ControlLhha, ControlSettings, ControlSettingsBatch, Rename}
import org.orbeon.oxf.fb.XMLNames.*
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.NodeInfoCell.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.fr.importexport.ImportExportSupport
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analysis.model.{MipName, Types}
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.*
import org.orbeon.oxf.xforms.xbl.BindingIndex.DatatypeMatch
import org.orbeon.oxf.xforms.xbl.{BindingDescriptor, BindingIndex}
import org.orbeon.oxf.xml.{SaxonUtils, XMLConstants}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames.{APPEARANCE_QNAME, BIND_QNAME}
import org.orbeon.xforms.{Namespaces, XFormsId}

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.collection.mutable
import scala.util.Try


trait ControlOverviewOps {

  private val DefaultAutoNameRegex = """^(control|section)-\d+$""".r
  private val NonValueLocalNames   = Set("explanation", "trigger", "hyperlink")

  def buildControlOverviewXml(
    fbLang        : String,
    isReadonlyMode: Boolean = false
  )(implicit
    ctx           : FormBuilderDocContext
  ): NodeInfo = {

    implicit val bindingIndex: BindingIndex[BindingDescriptor] = ctx.bindingIndex

    val effectiveLang = fbLang.trimAllToOpt.getOrElse("en")
    val formLangs     = {
      val raw = Try(allLangs(FormBuilder.resourcesRoot).toList.filter(_.nonEmpty)).getOrElse(Nil)
      if (raw.nonEmpty) raw else List(effectiveLang)
    }

    val appName  = ctx.metadataRootElemOpt.map(m => (m / "application-name").stringValue.trimAllToEmpty).getOrElse("")
    val formName = ctx.metadataRootElemOpt.map(m => (m / "form-name").stringValue.trimAllToEmpty).getOrElse("")

    val outerResourcesByLang: Map[String, Map[String, NodeInfo]] =
      Try(allResources(ctx.resourcesRootElem)).getOrElse(Nil).map { resElem =>
        val lang    = resElem.attValue(XMLLangQName)
        val holders = (resElem / *).map(h => h.localname -> h).toMap
        lang -> holders
      }.toMap

    val outerBindsByName: Map[String, NodeInfo] =
      ctx.topLevelBindElemOpt.toList
        .flatMap(_ descendantOrSelf XFBindTest)
        .flatMap(b => findBindName(b).map(_ -> b))
        .toMap

    val headOpt = (ctx.formDefinitionRootElem / XHHeadTest).headOption

    var nextRowCounter = 0
    def nextRowId(): String = {
      nextRowCounter += 1
      s"r$nextRowCounter"
    }

    val itemsBuffer        = mutable.ListBuffer[scala.xml.Elem]()
    val editableOrigNames  = mutable.Set[String]()

    def lookupResource(
      resourcesByLang: Map[String, Map[String, NodeInfo]],
      controlName    : String,
      lhht           : String,
      lang           : String
    ): String =
      resourcesByLang.get(lang).flatMap(_.get(controlName)).flatMap(_ elemValueOpt lhht).getOrElse("")

    def isHtmlMediatypeOnElem(controlElem: NodeInfo, lhht: String): Boolean = {
      val qName = FormBuilder.controlLHHATQName(lhht)
      (controlElem / qName).exists(_.attValue("mediatype") == "text/html")
    }

    def findMetadataText(lang: String, elems: Iterable[NodeInfo]): Option[String] =
      elems.find(_.attValue("lang") == lang).orElse(elems.headOption).map(_.stringValue).flatMap(_.trimAllToOpt)

    def resolveControlTypeAndIcon(
      controlElem  : NodeInfo,
      datatypeQName: QName
    ): (String, String, Option[NodeInfo]) = {
      val rawAtts = BindingDescriptor.getAtts(controlElem)
      val incomingAppearanceOpt =
        rawAtts.collectFirst { case (APPEARANCE_QNAME, app) => app.trimAllToOpt }.flatten

      val (virtualName, resolvedAppearanceOpt) =
        findVirtualNameAndAppearance(
          searchElemName = controlElem.uriQualifiedName,
          searchDatatype = datatypeQName,
          searchAtts     = updateAttAppearance(rawAtts, incomingAppearanceOpt)
        )

      val searchAtts = updateAttAppearance(updateAttAppearance(rawAtts, None), resolvedAppearanceOpt)

      val descriptorOpt =
        findMostSpecificBindingUseEqualOrToken(
          searchElemName = virtualName,
          datatypeMatch  = DatatypeMatch.makeExcludeStringMatch(datatypeQName),
          searchAtts     = searchAtts
        )

      val bindingOpt  = descriptorOpt.flatMap(_.binding).orElse(FormBuilder.bindingForControlElement(controlElem))
      val metadataOpt = bindingOpt.flatMap(b => (b / FBMetadataTest).headOption)

      val displayName =
        metadataOpt
          .flatMap(m => findMetadataText(effectiveLang, m / FBDisplayNameTest))
          .getOrElse(controlElem.localname)

      val iconClass =
        metadataOpt
          .flatMap(m => findMetadataText(effectiveLang, m / FBIconTest / FBIconClassTest))
          .getOrElse("fa fa-fw fa-cube")

      (displayName, iconClass, bindingOpt)
    }

    def readContainerFormulaSummary(
      bindElemOpt      : Option[NodeInfo],
      inSectionTemplate: Boolean,
      isRepeat         : Boolean
    ): (Int, String) = {
      val parts = mutable.ListBuffer[String]()

      bindElemOpt.foreach { bindElem =>
        def readMipAttrOrChild(mip: MipName.Computed): Option[String] =
          if (! inSectionTemplate) {
            val (attQName, _) = FormBuilder.mipToFBMIPQNames(mip)
            val v = FormRunner.readDenormalizedCalculatedMip(bindElem, mip, attQName)
            v.trimAllToOpt
          } else {
            val rawOpt =
              (bindElem /@ mip.aName).headOption.map(_.stringValue)
                .orElse((bindElem / mip.eName).headOption.flatMap(_.attValueOpt("value")))
            rawOpt.flatMap(_.trimAllToOpt)
          }

        readMipAttrOrChild(MipName.Relevant).filter(_ != "true()").foreach(v => parts += s"Visibility: $v")
        readMipAttrOrChild(MipName.Readonly).filter(_ != "false()").foreach(v => parts += s"Read-Only: $v")

        if (isRepeat) {
          (bindElem / XFBindTest).headOption.foreach { iterBind =>
            val iterRel =
              if (! inSectionTemplate)
                FormRunner.readDenormalizedCalculatedMip(iterBind, MipName.Relevant, FormBuilder.mipToFBMIPQNames(MipName.Relevant)._1).trimAllToOpt
              else
                (iterBind /@ MipName.Relevant.aName).headOption.map(_.stringValue).flatMap(_.trimAllToOpt)
            val iterRo =
              if (! inSectionTemplate)
                FormRunner.readDenormalizedCalculatedMip(iterBind, MipName.Readonly, FormBuilder.mipToFBMIPQNames(MipName.Readonly)._1).trimAllToOpt
              else
                (iterBind /@ MipName.Readonly.aName).headOption.map(_.stringValue).flatMap(_.trimAllToOpt)

            iterRel.filter(_ != "true()").foreach(v => parts += s"Iteration Visibility: $v")
            iterRo.filter(_ != "false()").foreach(v => parts += s"Iteration Read-Only: $v")
          }
        }
      }

      parts.size -> parts.mkString(" | ")
    }

    def visitContainersAndControls(
      parentChildren       : Iterable[NodeInfo],
      level                : Int,
      sectionPathParts     : List[String],
      ancestorContainerIds : List[String],
      ancestorContainerNames: List[String],
      inSectionTemplate    : Boolean,
      resourcesByLang      : Map[String, Map[String, NodeInfo]],
      bindsByName          : Map[String, NodeInfo]
    ): Unit =
      parentChildren.foreach {
        case sectionElem if IsSection(sectionElem) =>
          val rowId             = nextRowId()
          val nameOpt           = getControlNameOpt(sectionElem)
          val sectionName       = nameOpt.getOrElse("")
          val isRepeatSection   = isRepeat(sectionElem)
          val isSecTemplateHost = ! inSectionTemplate && isSectionWithTemplateContent(sectionElem)
          val rowReadonly       = isReadonlyMode || inSectionTemplate || nameOpt.isEmpty

          if (! inSectionTemplate && nameOpt.isDefined) {
            editableOrigNames += sectionName
            if (isSecTemplateHost)
              editableOrigNames += s"$sectionName$TemplateContentSuffix"
          }

          val customIterNameOpt =
            if (! inSectionTemplate && isRepeatSection && sectionName.nonEmpty && FormBuilderXPathApi.hasCustomIterationName(sectionName))
              FormRunner.findRepeatIterationName(sectionName)
            else
              None

          val isLabelHtml = sectionName.nonEmpty && isHtmlMediatypeOnElem(sectionElem, LHHA.Label.entryName)

          val labelValues: List[(String, String)] =
            formLangs.map { lang =>
              val v = if (sectionName.nonEmpty) lookupResource(resourcesByLang, sectionName, LHHA.Label.entryName, lang) else ""
              lang -> v
            }

          val currentLangLabelRaw =
            labelValues.find(_._1 == effectiveLang).map(_._2).filter(_.nonAllBlank)
              .orElse(labelValues.map(_._2).find(_.nonAllBlank))
              .getOrElse("")

          val strippedSectionTitle =
            if (currentLangLabelRaw.nonAllBlank)
              (if (isLabelHtml) FormRunner.removeHtmlTags(currentLangLabelRaw) else currentLangLabelRaw).trimAllToEmpty
            else
              sectionName

          val bindElemOpt = if (sectionName.nonEmpty) bindsByName.get(sectionName) else None
          val (formulaCount, formulasTooltip) = readContainerFormulaSummary(bindElemOpt, inSectionTemplate, isRepeatSection)

          val iconClass =
            if (isSecTemplateHost) "fa fa-fw fa-puzzle-piece"
            else if (isRepeatSection) "fa fa-fw fa-repeat"
            else "fa fa-fw fa-columns"

          val typeKind =
            if (isSecTemplateHost) "section-template"
            else if (isRepeatSection) "repeated-section"
            else "section"

          itemsBuffer +=
            <item
              row-id={rowId}
              kind="section"
              type-kind={typeKind}
              level={level.toString}
              section-path={sectionPathParts.mkString(" / ")}
              ancestor-container-ids={ancestorContainerIds.mkString(" ")}
              in-section-template={inSectionTemplate.toString}
              is-section-template={isSecTemplateHost.toString}
              is-repeat={isRepeatSection.toString}
              has-custom-iteration-name={customIterNameOpt.isDefined.toString}
              custom-iteration-name={customIterNameOpt.getOrElse("")}
              readonly={rowReadonly.toString}
              has-name={nameOpt.isDefined.toString}
              original-name={sectionName}
              icon-class={iconClass}
              type-label=""
              grid-size=""
              datatype=""
              required-supported="false"
              required-mode="false"
              required-formula=""
              original-required="false"
              summary-supported="false"
              summary-readonly="true"
              summary-readonly-reason=""
              original-summary="false"
              formula-count={formulaCount.toString}
              formulas-tooltip={formulasTooltip}
              validation-count="0"
              validations-tooltip=""
              custom-pdf-name=""
              pdf-suffix=""
              effective-pdf-name=""
              effective-excel-name="">
              <name>{sectionName}</name>
              <required>false</required>
              <summary>false</summary>
              <label supported="true" isHTML={isLabelHtml.toString} preview={if (isLabelHtml) strippedSectionTitle else ""}>
                {
                  labelValues.map { case (lang, v) =>
                    <value lang={lang}>{v}</value>
                  }
                }
              </label>
              <hint supported="false" isHTML="false" preview="">
                {
                  formLangs.map(lang => <value lang={lang}/>)
                }
              </hint>
            </item>

          val nextSectionPath       = if (strippedSectionTitle.nonEmpty) sectionPathParts :+ strippedSectionTitle else sectionPathParts
          val nextAncestorIds       = if (nameOpt.isDefined) ancestorContainerIds :+ rowId else ancestorContainerIds
          val nextAncestorNames     = if (nameOpt.isDefined) ancestorContainerNames :+ sectionName else ancestorContainerNames

          if (isSecTemplateHost) {
            for {
              head        <- headOpt
              bindingDoc  <- xblBindingForSection(head, sectionElem)
            } locally {
              val bindingElem = bindingDoc.rootElement
              val tmplResourcesByLang: Map[String, Map[String, NodeInfo]] =
                findXblInstance(bindingElem, Names.FormResources).toList
                  .flatMap(_ / * / "resource")
                  .map { resElem =>
                    val lang    = resElem.attValue(XMLLangQName)
                    val holders = (resElem / *).map(h => h.localname -> h).toMap
                    lang -> holders
                  }.toMap

              // Fall back to first language in the section template if the outer form has languages not translated in the library
              val fallbackTmplHolders: Map[String, NodeInfo] =
                tmplResourcesByLang.get(effectiveLang).orElse(tmplResourcesByLang.values.headOption).getOrElse(Map.empty)

              val normalizedTmplResources: Map[String, Map[String, NodeInfo]] =
                formLangs.map { lang =>
                  lang -> tmplResourcesByLang.getOrElse(lang, fallbackTmplHolders)
                }.toMap

              val tmplBindsByName: Map[String, NodeInfo] =
                findXblBinds(bindingElem)
                  .flatMap(_ descendantOrSelf XFBindTest)
                  .flatMap(b => findBindName(b).map(_ -> b))
                  .toMap

              val tmplChildren = bindingElem / XBLTemplateTest / * / *
              visitContainersAndControls(
                parentChildren         = tmplChildren,
                level                  = level + 1,
                sectionPathParts       = nextSectionPath,
                ancestorContainerIds   = nextAncestorIds,
                ancestorContainerNames = nextAncestorNames,
                inSectionTemplate      = true,
                resourcesByLang        = normalizedTmplResources,
                bindsByName            = tmplBindsByName
              )
            }
          } else {
            visitContainersAndControls(
              parentChildren         = sectionElem / *,
              level                  = level + 1,
              sectionPathParts       = nextSectionPath,
              ancestorContainerIds   = nextAncestorIds,
              ancestorContainerNames = nextAncestorNames,
              inSectionTemplate      = inSectionTemplate,
              resourcesByLang        = resourcesByLang,
              bindsByName            = bindsByName
            )
          }

        case gridElem if IsGrid(gridElem) =>
          val rowId        = nextRowId()
          val rawGridId    = gridElem.id
          val isTmpGridId  = rawGridId.startsWith("tmp-") && rawGridId.endsWith("-tmp")
          val nameOpt      = if (isTmpGridId) None else getControlNameOpt(gridElem)
          val gridName     = nameOpt.getOrElse("")
          val isRepeatGrid = isRepeat(gridElem)
          val rowReadonly  = isReadonlyMode || inSectionTemplate || nameOpt.isEmpty

          if (! inSectionTemplate && nameOpt.isDefined)
            editableOrigNames += gridName

          val customIterNameOpt =
            if (! inSectionTemplate && isRepeatGrid && gridName.nonEmpty && FormBuilderXPathApi.hasCustomIterationName(gridName))
              FormRunner.findRepeatIterationName(gridName)
            else
              None

          val cols     = if (gridElem.attValue("columns") == "24") 24 else 12
          val cells    = gridElem / CellTest
          val rows     = if (cells.isEmpty) 0 else cells.map(c => NodeInfoCellOps.y(c).getOrElse(1) + NodeInfoCellOps.h(c).getOrElse(1) - 1).max
          val gridSize = s"$cols×$rows"

          val bindElemOpt = if (gridName.nonEmpty) bindsByName.get(gridName) else None
          val (formulaCount, formulasTooltip) = readContainerFormulaSummary(bindElemOpt, inSectionTemplate, isRepeatGrid)

          val iconClass = if (isRepeatGrid) "fa fa-fw fa-repeat" else "fa fa-fw fa-table"
          val typeKind  = if (isRepeatGrid) "repeated-grid" else "grid"

          itemsBuffer +=
            <item
              row-id={rowId}
              kind="grid"
              type-kind={typeKind}
              level={level.toString}
              section-path={sectionPathParts.mkString(" / ")}
              ancestor-container-ids={ancestorContainerIds.mkString(" ")}
              in-section-template={inSectionTemplate.toString}
              is-section-template="false"
              is-repeat={isRepeatGrid.toString}
              has-custom-iteration-name={customIterNameOpt.isDefined.toString}
              custom-iteration-name={customIterNameOpt.getOrElse("")}
              readonly={rowReadonly.toString}
              has-name={nameOpt.isDefined.toString}
              original-name={gridName}
              icon-class={iconClass}
              type-label=""
              grid-size={gridSize}
              datatype=""
              required-supported="false"
              required-mode="false"
              required-formula=""
              original-required="false"
              summary-supported="false"
              summary-readonly="true"
              summary-readonly-reason=""
              original-summary="false"
              formula-count={formulaCount.toString}
              formulas-tooltip={formulasTooltip}
              validation-count="0"
              validations-tooltip=""
              custom-pdf-name=""
              pdf-suffix=""
              effective-pdf-name=""
              effective-excel-name="">
              <name>{gridName}</name>
              <required>false</required>
              <summary>false</summary>
              <label supported="false" isHTML="false" preview="">
                {
                  formLangs.map(lang => <value lang={lang}/>)
                }
              </label>
              <hint supported="false" isHTML="false" preview="">
                {
                  formLangs.map(lang => <value lang={lang}/>)
                }
              </hint>
            </item>

          // Non-repeated grids do not contribute to PDF field paths (#4099)
          val nextAncestorIds   = if (isRepeatGrid && nameOpt.isDefined) ancestorContainerIds :+ rowId else ancestorContainerIds
          val nextAncestorNames = if (isRepeatGrid && nameOpt.isDefined) ancestorContainerNames :+ gridName else ancestorContainerNames

          cells.foreach { cellElem =>
            (cellElem / *).headOption.foreach { controlElem =>
              // Skip design-time cell badge groups if any exist
              if (controlElem.id.nonEmpty && ! controlElem.attClasses("fb-control-cell-badges"))
                visitControl(
                  controlElem            = controlElem,
                  level                  = level + 1,
                  sectionPath            = sectionPathParts.mkString(" / "),
                  ancestorContainerIds   = nextAncestorIds,
                  ancestorContainerNames = nextAncestorNames,
                  inSectionTemplate      = inSectionTemplate,
                  resourcesByLang        = resourcesByLang,
                  bindsByName            = bindsByName
                )
            }
          }

        case _ =>
      }

    def visitControl(
      controlElem           : NodeInfo,
      level                 : Int,
      sectionPath           : String,
      ancestorContainerIds  : List[String],
      ancestorContainerNames: List[String],
      inSectionTemplate     : Boolean,
      resourcesByLang       : Map[String, Map[String, NodeInfo]],
      bindsByName           : Map[String, NodeInfo]
    ): Unit = {
      val rowId       = nextRowId()
      val nameOpt     = getControlNameOpt(controlElem)
      val controlName = nameOpt.getOrElse("")
      val rowReadonly = isReadonlyMode || inSectionTemplate || nameOpt.isEmpty

      if (! inSectionTemplate && nameOpt.isDefined)
        editableOrigNames += controlName

      val bindElemOpt = if (controlName.nonEmpty) bindsByName.get(controlName) else None
      val hasBind     = bindElemOpt.isDefined && controlElem.hasAtt(BIND_QNAME)
      val isValueCtrl = hasBind && ! NonValueLocalNames(controlElem.localname)

      // Datatype & Required
      val (datatypeQName, datatypeDisplay, reqMode, reqFormula) =
        if (! inSectionTemplate && hasBind && controlName.nonEmpty) {
          val dtVal  = FormBuilder.DatatypeValidation.fromForm(controlName)
          val dtQN   = dtVal.datatypeQName
          val dtDisp = dtVal.datatype.fold(_._1.localName, _.qualifiedName)
          val reqVal = FormBuilder.RequiredValidation.fromForm(controlName)
          val (rMode, rForm) = reqVal.required match {
            case Left(true)  => ("true", "")
            case Left(false) =>
              dtVal.datatype match {
                case Left((_, true)) => ("true", "")
                case _               => ("false", "")
              }
            case Right(expr) => ("formula", expr)
          }
          (dtQN, dtDisp, rMode, rForm)
        } else {
          val rawTypeOpt =
            bindElemOpt.flatMap { b =>
              (b /@ "type").headOption.orElse((b / (XF -> "type")).headOption).map(_.stringValue.trimAllToEmpty).filter(_.nonEmpty)
            }
          val dtQN = rawTypeOpt.flatMap(t => bindElemOpt.flatMap(b => Option(b.resolveQName(t)))).getOrElse(XMLConstants.XS_STRING_QNAME)
          val dtDisp =
            if (Namespaces.isForBuiltinType(dtQN)) dtQN.localName
            else rawTypeOpt.getOrElse(dtQN.qualifiedName)

          val rawReqOpt =
            bindElemOpt.flatMap { b =>
              (b /@ "required").headOption
                .orElse((b / (XF -> "required")).headOption.flatMap(_ /@ "value" headOption))
                .map(_.stringValue.trimAllToEmpty)
                .filter(_.nonEmpty)
            }
          val (rMode, rForm) = rawReqOpt match {
            case Some("true()")                          => ("true", "")
            case Some("false()") | None                  =>
              if (dtQN.namespace.uri == XMLConstants.XSD_URI && rawTypeOpt.isDefined) ("true", "") else ("false", "")
            case Some(expr)                              => ("formula", expr)
          }
          (dtQN, dtDisp, rMode, rForm)
        }

      val (typeLabel, iconClass, bindingOpt) = resolveControlTypeAndIcon(controlElem, datatypeQName)

      // LHHT support
      val isExplanation = controlElem.localname == "explanation"
      val labelSupported =
        ! isExplanation && (
          (controlElem / (XF -> "label")).nonEmpty ||
          bindingOpt.exists(b => FormBuilder.hasViewTemplateSupportElementFor(b, LHHA.Label.entryName))
        )
      val hintSupported =
        ! isExplanation && (
          (controlElem / (XF -> "hint")).nonEmpty ||
          bindingOpt.exists(b => FormBuilder.hasViewTemplateSupportElementFor(b, LHHA.Hint.entryName))
        )

      val isLabelHtml =
        if (isExplanation) true
        else labelSupported && controlName.nonEmpty && isHtmlMediatypeOnElem(controlElem, LHHA.Label.entryName)

      val isHintHtml =
        hintSupported && controlName.nonEmpty && isHtmlMediatypeOnElem(controlElem, LHHA.Hint.entryName)

      val labelValues: List[(String, String)] =
        formLangs.map { lang =>
          val v =
            if (controlName.isEmpty) ""
            else if (isExplanation) lookupResource(resourcesByLang, controlName, "text", lang)
            else if (labelSupported) lookupResource(resourcesByLang, controlName, LHHA.Label.entryName, lang)
            else ""
          lang -> v
        }

      val hintValues: List[(String, String)] =
        formLangs.map { lang =>
          val v = if (hintSupported && controlName.nonEmpty) lookupResource(resourcesByLang, controlName, LHHA.Hint.entryName, lang) else ""
          lang -> v
        }

      def previewFor(values: List[(String, String)], isHtml: Boolean): String =
        if (! isHtml) ""
        else {
          val raw =
            values.find(_._1 == effectiveLang).map(_._2).filter(_.nonAllBlank)
              .orElse(values.map(_._2).find(_.nonAllBlank))
              .getOrElse("")
          FormRunner.removeHtmlTags(raw).trimAllToEmpty
        }

      val labelPreview = previewFor(labelValues, isLabelHtml)
      val hintPreview  = previewFor(hintValues, isHintHtml)

      // Summary show & guard conditions
      val hasEncrypt       = (controlElem / (FR -> "encrypt")).nonEmpty
      val summaryShowElem  = (controlElem / "*:index" / "*:summary-show").headOption
      val isSummaryEnabled = summaryShowElem.exists { ss =>
        ! (ss.attValueOpt("column").contains("false") && ss.attValueOpt("search").contains("false"))
      }
      val hasSummaryRoles  = summaryShowElem.exists(_.attValueOpt("require-role").exists(_.nonAllBlank))
      val hasSummaryLegacy = summaryShowElem.exists(ss => ss.attValueOpt("column").contains("false") || ss.attValueOpt("search").contains("false"))
      val hasBulkOrSort    = (controlElem / "*:index" / ("*:allow-bulk-edit" || "*:default-sort-column")).nonEmpty

      val summaryReadonlyReason =
        if (! isValueCtrl) ""
        else if (hasEncrypt) "encrypted"
        else if (hasSummaryRoles || hasSummaryLegacy || hasBulkOrSort) "advanced-index"
        else ""

      val summaryReadonly = rowReadonly || ! isValueCtrl || summaryReadonlyReason.nonEmpty

      // Formulas & Validations badges
      val formulaParts    = mutable.ListBuffer[String]()
      val validationParts = mutable.ListBuffer[String]()

      if (reqMode == "formula" && reqFormula.nonEmpty)
        formulaParts += s"Required: $reqFormula"

      bindElemOpt.foreach { bindElem =>
        def readControlMip(mip: MipName.Computed): Option[String] =
          if (! inSectionTemplate)
            FormRunner.readDenormalizedCalculatedMip(bindElem, mip, FormBuilder.mipToFBMIPQNames(mip)._1).trimAllToOpt
          else
            (bindElem /@ mip.aName).headOption.map(_.stringValue)
              .orElse((bindElem / mip.eName).headOption.flatMap(_.attValueOpt("value")))
              .flatMap(_.trimAllToOpt)

        readControlMip(MipName.Calculate).foreach(v => formulaParts += s"Calculated: $v")
        readControlMip(MipName.Default).foreach(v => formulaParts += s"Initial: $v")
        readControlMip(MipName.Relevant).filter(_ != "true()").foreach(v => formulaParts += s"Visibility: $v")
        readControlMip(MipName.Readonly).filter(_ != "false()").foreach(v => formulaParts += s"Read-Only: $v")

        if (datatypeDisplay.nonEmpty && datatypeDisplay != "string")
          validationParts += s"Type: $datatypeDisplay"

        val constraintExprs: Seq[String] =
          if (! inSectionTemplate)
            ((bindElem /@ (FB -> "constraint")).map(_.stringValue) ++
              (bindElem / (FB -> "constraint")).flatMap(_.attValueOpt("value"))).filter(_.nonAllBlank)
          else
            ((bindElem /@ "constraint").map(_.stringValue) ++
              (bindElem / (XF -> "constraint")).flatMap(_.attValueOpt("value"))).filter(_.nonAllBlank)

        constraintExprs.foreach(c => validationParts += c)
      }

      // PDF & Excel field names
      val customPdfName = controlElem.attValueOpt(FR -> "pdf-template-field-name").flatMap(_.trimAllToOpt).getOrElse("")
      val pdfSuffix     =
        if (! inSectionTemplate && controlName.nonEmpty)
          Try(XFormsId.getEffectiveIdSuffix(FormBuilder.buildControlEffectiveId(controlElem)).trimAllToEmpty).getOrElse("")
        else
          ""

      val effectivePdfName =
        if (customPdfName.nonEmpty)
          customPdfName
        else if (controlName.nonEmpty)
          (ancestorContainerNames ++ (controlName :: pdfSuffix.trimAllToOpt.toList)).mkString(FormRunner.PdfFieldSeparator)
        else
          ""

      val effectiveExcelName =
        if (controlName.nonEmpty)
          ImportExportSupport.controlNameToNamedRangeName(controlName)(ImportExportSupport.DefaultExcelNameManglingConfig)
        else
          ""

      val initialRequiredBool = (reqMode == "true").toString

      itemsBuffer +=
        <item
          row-id={rowId}
          kind="control"
          type-kind={if (isExplanation) "explanation" else "control"}
          level={level.toString}
          section-path={sectionPath}
          ancestor-container-ids={ancestorContainerIds.mkString(" ")}
          in-section-template={inSectionTemplate.toString}
          is-section-template="false"
          is-repeat="false"
          has-custom-iteration-name="false"
          custom-iteration-name=""
          readonly={rowReadonly.toString}
          has-name={nameOpt.isDefined.toString}
          original-name={controlName}
          icon-class={iconClass}
          type-label={typeLabel}
          grid-size=""
          datatype={if (datatypeDisplay == "string") "" else datatypeDisplay}
          required-supported={isValueCtrl.toString}
          required-mode={reqMode}
          required-formula={reqFormula}
          original-required={initialRequiredBool}
          summary-supported={isValueCtrl.toString}
          summary-readonly={summaryReadonly.toString}
          summary-readonly-reason={summaryReadonlyReason}
          original-summary={isSummaryEnabled.toString}
          formula-count={formulaParts.size.toString}
          formulas-tooltip={formulaParts.mkString(" | ")}
          validation-count={validationParts.size.toString}
          validations-tooltip={validationParts.mkString(" | ")}
          custom-pdf-name={customPdfName}
          pdf-suffix={pdfSuffix}
          effective-pdf-name={effectivePdfName}
          effective-excel-name={effectiveExcelName}>
          <name>{controlName}</name>
          <required>{initialRequiredBool}</required>
          <summary>{isSummaryEnabled.toString}</summary>
          <label supported={labelSupported.toString} isHTML={isLabelHtml.toString} preview={labelPreview}>
            {
              labelValues.map { case (lang, v) =>
                <value lang={lang}>{v}</value>
              }
            }
          </label>
          <hint supported={hintSupported.toString} isHTML={isHintHtml.toString} preview={hintPreview}>
            {
              hintValues.map { case (lang, v) =>
                <value lang={lang}>{v}</value>
              }
            }
          </hint>
        </item>
    }

    ctx.bodyElemOpt.foreach { bodyElem =>
      visitContainersAndControls(
        parentChildren         = bodyElem / *,
        level                  = 1,
        sectionPathParts       = Nil,
        ancestorContainerIds   = Nil,
        ancestorContainerNames = Nil,
        inSectionTemplate      = false,
        resourcesByLang        = outerResourcesByLang,
        bindsByName            = outerBindsByName
      )
    }

    val allNamesInUse = Try(FormBuilder.iterateNamesInUseCtx.toSet).getOrElse(Set.empty)
    val externalNames = (allNamesInUse -- editableOrigNames).toList.sorted.mkString(" ")

    elemToNodeInfo(
      <overview external-names={externalNames} app={appName} form={formName}>
        <langs>
          {
            formLangs.map(l => <lang>{l}</lang>)
          }
        </langs>
        {itemsBuffer}
      </overview>
    )
  }

  private def labelTextForSuggestion(itemElem: NodeInfo, lang: String): Option[String] = {
    val labelElemOpt = (itemElem / "label").headOption.filter(_.attValue("supported") == "true")
    labelElemOpt.flatMap { labelElem =>
      val isHtml    = labelElem.attValue("isHTML") == "true"
      val values    = labelElem / "value"
      val rawString =
        values.find(_.attValue("lang") == lang).map(_.stringValue).filter(_.nonAllBlank)
          .orElse(values.map(_.stringValue).find(_.nonAllBlank))
          .getOrElse("")
      val plain = if (isHtml) FormRunner.removeHtmlTags(rawString) else rawString
      plain.trimAllToOpt
    }
  }

  def slugSuggestionFromLabel(label: String, usedNames: Set[String], isSectionTemplate: Boolean = false): String = {
    val cleaned = label.replaceAll("[^a-zA-Z0-9\\-_\\s]", "_")
    val baseSlug = {
      val replaced = cleaned.trim.replaceAll("\\s+", "-").toLowerCase
      if (! SaxonUtils.isValidNCName(replaced))
        s"_$replaced"
      else
        replaced
    }
    val safeBase = if (baseSlug.endsWith(DefaultIterationSuffix)) s"${baseSlug}_" else baseSlug

    def isCandidateFree(candidate: String): Boolean =
      ! usedNames.contains(candidate) &&
      ! candidate.endsWith(DefaultIterationSuffix) &&
      (! isSectionTemplate || ! usedNames.contains(s"$candidate$TemplateContentSuffix"))

    Iterator.iterate((safeBase, None: Option[Int])) { case (base, indexOpt) =>
      (base, Some(indexOpt.getOrElse(0) + 1))
    }
    .map {
      case (base, None)        => base
      case (base, Some(index)) => s"$base-$index"
    }
    .find(isCandidateFree)
    .get
  }

  def suggestControlNameForOverviewRow(
    overviewElem: NodeInfo,
    rowId       : String,
    lang        : String
  ): Option[String] = {
    val items         = overviewElem / "item"
    val targetItemOpt = items.find(_.attValue("row-id") == rowId)

    targetItemOpt.flatMap { targetItem =>
      if (targetItem.attValue("readonly") == "true")
        None
      else {
        val currentName = (targetItem / "name").stringValue.trimAllToEmpty
        if (! DefaultAutoNameRegex.matches(currentName))
          None
        else
          labelTextForSuggestion(targetItem, lang).map { labelText =>
            val externalNames = overviewElem.attValue("external-names").splitTo[Set]()
            val otherStagedNames =
              items
                .filter(i => i.attValue("readonly") != "true" && i.attValue("row-id") != rowId)
                .flatMap { i =>
                  val n = (i / "name").stringValue.trimAllToEmpty
                  if (i.attValue("is-section-template") == "true" && n.nonEmpty)
                    List(n, s"$n$TemplateContentSuffix")
                  else if (n.nonEmpty)
                    List(n)
                  else
                    Nil
                }
                .toSet

            slugSuggestionFromLabel(
              label             = labelText,
              usedNames         = externalNames ++ otherStagedNames,
              isSectionTemplate = targetItem.attValue("is-section-template") == "true"
            )
          }
      }
    }
  }

  def suggestAllDefaultControlNames(overviewElem: NodeInfo, lang: String): Int = {
    val editableItems = (overviewElem / "item").filter(_.attValue("readonly") != "true")
    val externalNames = overviewElem.attValue("external-names").splitTo[Set]()

    val usedNames = mutable.Set[String]() ++= externalNames

    // First reserve all names of editable items that will NOT be auto-renamed
    editableItems.foreach { item =>
      val currentName = (item / "name").stringValue.trimAllToEmpty
      val canSuggest  = DefaultAutoNameRegex.matches(currentName) && labelTextForSuggestion(item, lang).isDefined
      if (! canSuggest && currentName.nonEmpty) {
        usedNames += currentName
        if (item.attValue("is-section-template") == "true")
          usedNames += s"$currentName$TemplateContentSuffix"
      }
    }

    var updatedCount = 0

    editableItems.foreach { item =>
      val nameElem    = item / "name"
      val currentName = nameElem.stringValue.trimAllToEmpty
      if (DefaultAutoNameRegex.matches(currentName)) {
        labelTextForSuggestion(item, lang).foreach { labelText =>
          val isSecTmpl  = item.attValue("is-section-template") == "true"
          val suggestion = slugSuggestionFromLabel(labelText, usedNames.toSet, isSecTmpl)
          usedNames += suggestion
          if (isSecTmpl)
            usedNames += s"$suggestion$TemplateContentSuffix"
          if (suggestion != currentName) {
            setvalue(nameElem, suggestion)
            updatedCount += 1
          }
        }
      }
    }

    updatedCount
  }

  def applyControlOverviewChanges(overviewElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Int = {

    val editableItems = (overviewElem / "item").filter(i => i.attValue("readonly") != "true" && i.attValue("has-name") == "true")
    val undoBuffer    = mutable.ListBuffer[UndoAction]()
    var modifiedItems = 0

    def currentLangValues(controlName: String, lhht: String, langs: Seq[String]): List[(String, String)] = {
      val byLang = FormBuilder.getControlResourcesWithLang(controlName, lhht, langs).toMap
      langs.map { lang =>
        lang -> byLang.get(lang).flatMap(_.headOption).map(_.getStringValue).getOrElse("")
      }.toList
    }

    val formLangs = (overviewElem / "langs" / "lang").map(_.stringValue).filter(_.nonEmpty).toList

    // Step 1: Apply non-rename updates (Label, Hint, Required, Summary) under original names
    editableItems.foreach { item =>
      val origName = item.attValue("original-name")
      val kind     = item.attValue("kind")

      if (kind == "section") {
        val labelElem = (item / "label").headOption.filter(l => l.attValue("supported") == "true" && l.attValue("isHTML") != "true")
        labelElem.foreach { lElem =>
          val newLangValues = (lElem / "value").map(v => v.attValue("lang") -> v.stringValue.trimAllToEmpty).toList
          val oldLangValues = currentLangValues(origName, LHHA.Label.entryName, formLangs)
          if (newLangValues != oldLangValues) {
            undoBuffer += ControlLhha(origName, LHHA.Label.entryName, oldLangValues, isOptional = true)
            FormBuilderXPathApi.settingControlLabelHintHelpOrText(origName, LHHA.Label.entryName, forceOptional = true) { implicit ctx =>
              FormBuilder.setControlResourcesWithLang(
                controlName  = origName,
                resourceName = LHHA.Label.entryName,
                langValues   = newLangValues.map { case (l, v) => l -> Seq(v) }
              )
            }
            modifiedItems += 1
          }
        }
      } else if (kind == "control") {
        val labelElemOpt = (item / "label").headOption.filter(l => l.attValue("supported") == "true" && l.attValue("isHTML") != "true")
        val hintElemOpt  = (item / "hint").headOption.filter(h => h.attValue("supported") == "true" && h.attValue("isHTML") != "true")

        val newLabelsOpt = labelElemOpt.map(e => (e / "value").map(v => v.attValue("lang") -> v.stringValue.trimAllToEmpty).toList)
        val oldLabelsOpt = labelElemOpt.map(_ => currentLangValues(origName, LHHA.Label.entryName, formLangs))
        val labelChanged = newLabelsOpt != oldLabelsOpt

        val newHintsOpt  = hintElemOpt.map(e => (e / "value").map(v => v.attValue("lang") -> v.stringValue.trimAllToEmpty).toList)
        val oldHintsOpt  = hintElemOpt.map(_ => currentLangValues(origName, LHHA.Hint.entryName, formLangs))
        val hintChanged  = newHintsOpt != oldHintsOpt

        val reqSupported = item.attValue("required-supported") == "true" && item.attValue("required-mode") != "formula"
        val origReq      = item.attValue("original-required") == "true"
        val newReq       = (item / "required").stringValue.trimAllToEmpty == "true"
        val reqChanged   = reqSupported && (origReq != newReq)

        val sumSupported = item.attValue("summary-supported") == "true" && item.attValue("summary-readonly") != "true"
        val origSum      = item.attValue("original-summary") == "true"
        val newSum       = (item / "summary").stringValue.trimAllToEmpty == "true"
        val sumChanged   = sumSupported && (origSum != newSum)

        if (labelChanged || hintChanged || reqChanged || sumChanged) {
          FormRunner.findControlByName(origName).foreach { controlElem =>
            ToolboxOps.controlOrContainerElemToXcv(controlElem).foreach { xcv =>
              undoBuffer += ControlSettings(origName, origName, xcv)
            }

            if (labelChanged) {
              newLabelsOpt.foreach { langVals =>
                if (FormBuilder.getControlLhhat(origName, LHHA.Label.entryName).isEmpty && langVals.exists(_._2.nonAllBlank))
                  FormBuilder.ensureCleanLHHAElements(origName, LHHA.Label.entryName, count = 1, replace = false, keepParams = true)
                FormBuilder.setControlResourcesWithLang(
                  controlName  = origName,
                  resourceName = LHHA.Label.entryName,
                  langValues   = langVals.map { case (l, v) => l -> Seq(v) }
                )
              }
            }

            if (hintChanged) {
              newHintsOpt.foreach { langVals =>
                if (FormBuilder.getControlLhhat(origName, LHHA.Hint.entryName).isEmpty && langVals.exists(_._2.nonAllBlank))
                  FormBuilder.ensureCleanLHHAElements(origName, LHHA.Hint.entryName, count = 1, replace = false, keepParams = true)
                FormBuilder.setControlResourcesWithLang(
                  controlName  = origName,
                  resourceName = LHHA.Hint.entryName,
                  langValues   = langVals.map { case (l, v) => l -> Seq(v) }
                )
              }
            }

            if (reqChanged) {
              val existingReq = FormBuilder.RequiredValidation.fromForm(origName)
              FormBuilder.writeValidations(origName, MipName.Required, List(existingReq.copy(required = Left(newReq))))
              val existingDt = FormBuilder.DatatypeValidation.fromForm(origName)
              existingDt.datatype match {
                case Left((qName, oldReq)) if oldReq != newReq =>
                  val updatedDt =
                    FormBuilder.DatatypeValidation.datatypeQNameFromStrings(
                      controlName    = origName,
                      builtinTypeOpt = Some(qName.localName -> newReq),
                      schemaTypeOpt  = None
                    )
                  FormBuilder.writeValidations(origName, MipName.Type, List(existingDt.copy(datatype = updatedDt)))
                case _ =>
              }
            }

            if (sumChanged) {
              if (newSum) {
                val indexElem =
                  (controlElem / "*:index").headOption.getOrElse {
                    insert(into = controlElem, origin = <fr:index xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>: NodeInfo).head
                  }
                delete(indexElem / "*:summary-show")
                insert(into = indexElem, origin = <fr:summary-show xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>: NodeInfo)
                FormRunner.findBindByName(origName).foreach { bindElem =>
                  delete(bindElem /@ "*:persist")
                }
              } else {
                (controlElem / "*:index").headOption.foreach { indexElem =>
                  delete(indexElem / "*:summary-show")
                  if ((indexElem / *).isEmpty)
                    delete(indexElem)
                }
              }
            }

            modifiedItems += 1
          }
        }
      }
    }

    // Step 2: Cycle-safe batch renames
    case class PendingRename(item: NodeInfo, oldName: String, newName: String)

    val renames: List[PendingRename] =
      editableItems.flatMap { item =>
        val oldName = item.attValue("original-name").trimAllToEmpty
        val newName = (item / "name").stringValue.trimAllToEmpty
        (oldName.nonEmpty && newName.nonEmpty && oldName != newName && SaxonUtils.isValidNCName(newName))
          .option(PendingRename(item, oldName, newName))
      }.toList

    def performSingleRename(item: NodeInfo, fromName: String, toName: String): Option[Rename] = {
      val isRep         = item.attValue("is-repeat") == "true"
      val hasCustomIter = item.attValue("has-custom-iteration-name") == "true"
      if (isRep && ! hasCustomIter)
        FormBuilder.renameControlIterationIfNeeded(fromName, toName, None, None)
      FormBuilder.renameControlIfNeeded(fromName, toName)
    }

    if (renames.nonEmpty) {
      val oldNamesSet = renames.map(_.oldName).toSet
      val hasChainOrCycle =
        renames.exists { r =>
          oldNamesSet.contains(r.newName) ||
          (r.item.attValue("is-section-template") == "true" && oldNamesSet.contains(s"${r.newName}$TemplateContentSuffix"))
        }

      if (! hasChainOrCycle) {
        renames.foreach { r =>
          performSingleRename(r.item, r.oldName, r.newName).foreach { undo =>
            undoBuffer += undo
            modifiedItems += 1
          }
        }
      } else {
        val reservedNames =
          FormBuilder.iterateNamesInUseCtx.toSet ++
          renames.flatMap(r => List(r.oldName, r.newName, s"${r.newName}$TemplateContentSuffix"))

        var tmpCounter = 0
        def nextTempControlName(): String = {
          var candidate = ""
          do {
            tmpCounter += 1
            candidate = s"fb-rename-tmp-$tmpCounter"
          } while (reservedNames.contains(candidate) || reservedNames.contains(s"$candidate$TemplateContentSuffix"))
          candidate
        }

        val stagedWithTemps = renames.map(r => (r, nextTempControlName()))

        stagedWithTemps.foreach { case (r, tmpName) =>
          performSingleRename(r.item, r.oldName, tmpName).foreach(undoBuffer += _)
        }
        stagedWithTemps.foreach { case (r, tmpName) =>
          performSingleRename(r.item, tmpName, r.newName).foreach { undo =>
            undoBuffer += undo
            modifiedItems += 1
          }
        }
      }
    }

    if (undoBuffer.nonEmpty)
      Undo.pushUserUndoAction(ControlSettingsBatch(undoBuffer.toList))

    modifiedItems
  }

  def controlOverviewToCsvString(overviewElem: NodeInfo, currentLang: String): String = {
    val langs        = (overviewElem / "langs" / "lang").map(_.stringValue).filter(_.nonEmpty).toList
    val effectiveLng = langs.find(_ == currentLang).orElse(langs.headOption).getOrElse("en")
    val multiLang    = langs.size > 1

    def escapeCsv(value: String): String =
      if (value.contains('"') || value.contains(',') || value.contains('\n') || value.contains('\r'))
        "\"" + value.replace("\"", "\"\"") + "\""
      else
        value

    val labelHeaders = if (multiLang) langs.map(l => s"Label ($l)") else List("Label")
    val hintHeaders  = if (multiLang) langs.map(l => s"Hint ($l)") else List("Hint")

    val headers =
      List("Kind", "Section Path", "Name", "Type", "Datatype") ++
      labelHeaders ++
      hintHeaders ++
      List("Required", "Summary", "Formulas", "Validations", "PDF Field Name", "Excel Range Name", "Section Template")

    val items = overviewElem / "item"
    val itemById: Map[String, NodeInfo] = items.map(i => i.attValue("row-id") -> i).toMap

    val rows = items.map { item =>
      val kind       = item.attValue("kind")
      val secPath    = item.attValue("section-path")
      val stagedName = (item / "name").stringValue.trimAllToEmpty
      val typeStr    =
        if (kind == "control") item.attValue("type-label")
        else if (kind == "grid") {
          val gs = item.attValue("grid-size")
          val base = if (item.attValue("is-repeat") == "true") "Repeated Grid" else "Grid"
          if (gs.nonEmpty) s"$base ($gs)" else base
        } else {
          if (item.attValue("is-section-template") == "true") "Section Template"
          else if (item.attValue("is-repeat") == "true") "Repeated Section"
          else "Section"
        }

      val datatype = item.attValue("datatype")

      def extractLhhaValues(elemName: String): List[String] = {
        val lhhaElemOpt = (item / elemName).headOption
        val isSupported = lhhaElemOpt.exists(_.attValue("supported") == "true") || (elemName == "label" && item.attValue("type-kind") == "explanation")
        val isHtml      = lhhaElemOpt.exists(_.attValue("isHTML") == "true")
        val targetLangs = if (multiLang) langs else List(effectiveLng)

        targetLangs.map { l =>
          if (! isSupported) ""
          else {
            val raw = lhhaElemOpt.toList.flatMap(_ / "value").find(_.attValue("lang") == l).map(_.stringValue).getOrElse("")
            if (isHtml && raw.nonEmpty) FormRunner.removeHtmlTags(raw).trimAllToEmpty else raw.trimAllToEmpty
          }
        }
      }

      val reqStr =
        if (item.attValue("required-supported") != "true") ""
        else if (item.attValue("required-mode") == "formula") item.attValue("required-formula")
        else (item / "required").stringValue.trimAllToEmpty

      val sumStr =
        if (item.attValue("summary-supported") != "true") ""
        else (item / "summary").stringValue.trimAllToEmpty

      val formulas    = item.attValue("formulas-tooltip")
      val validations = item.attValue("validations-tooltip")

      // Compute PDF & Excel names dynamically from staged names so CSV always reflects pending renames
      val pdfName =
        if (kind != "control" || stagedName.isEmpty) ""
        else {
          val customPdf = item.attValue("custom-pdf-name")
          if (customPdf.nonEmpty) customPdf
          else {
            val ancestorNames =
              item.attValue("ancestor-container-ids").splitTo[List]()
                .flatMap(cid => itemById.get(cid).map(c => (c / "name").stringValue.trimAllToEmpty))
                .filter(_.nonEmpty)
            val suffixList = item.attValue("pdf-suffix").trimAllToOpt.toList
            (ancestorNames ++ (stagedName :: suffixList)).mkString(FormRunner.PdfFieldSeparator)
          }
        }

      val excelName =
        if (kind == "control" && stagedName.nonEmpty)
          ImportExportSupport.controlNameToNamedRangeName(stagedName)(ImportExportSupport.DefaultExcelNameManglingConfig)
        else
          ""

      val inSecTmpl = item.attValue("in-section-template")

      val cols =
        List(kind, secPath, stagedName, typeStr, datatype) ++
        extractLhhaValues("label") ++
        extractLhhaValues("hint") ++
        List(reqStr, sumStr, formulas, validations, pdfName, excelName, inSecTmpl)

      cols.map(escapeCsv).mkString(",")
    }

    (headers.map(escapeCsv).mkString(",") +: rows.toList).mkString("\r\n")
  }

  def controlOverviewToCsvDownloadUri(overviewElem: NodeInfo, currentLang: String): String = {
    val csvWithBom = "\uFEFF" + controlOverviewToCsvString(overviewElem, currentLang)
    val base64     = Base64.getEncoder.encodeToString(csvWithBom.getBytes(StandardCharsets.UTF_8))
    s"data:text/csv;charset=utf-8;base64,$base64"
  }
}
