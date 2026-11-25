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

import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerSummaryOrder.SummaryControlColumn
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*


object SummaryOrderOps {

  import Private.*

  case class SummaryControlColumnDetails(
    key           : SummaryControlColumn,
    defaultPos    : Int,
    roleRestricted: Boolean,
    labels        : List[(String, String)],
    sectionLabels : List[(String, String)]
  )

  def findDefaultSummaryControlColumns(implicit ctx: FormBuilderDocContext): List[SummaryControlColumnDetails] =
    collectCandidates(includeControlNameOpt = None).zipWithIndex.map { case (raw, idx0) =>
      SummaryControlColumnDetails(
        key            = raw.key,
        defaultPos     = idx0 + 1,
        roleRestricted = raw.roleRestricted,
        labels         = raw.labels,
        sectionLabels  = raw.sectionLabels
      )
    }

  def findOrderedSummaryControlColumns(implicit ctx: FormBuilderDocContext): List[SummaryControlColumnDetails] = {
    val defaultCols = findDefaultSummaryControlColumns
    val customOrder = FormRunnerSummaryOrder.readSummaryControlColumns(ctx.metadataRootElemOpt)
    FormRunnerSummaryOrder.sortBySummaryOrder(defaultCols, customOrder)(_.key)
  }

  def buildSummaryControlColumnsXml(implicit ctx: FormBuilderDocContext): NodeInfo = {
    val ordered = findOrderedSummaryControlColumns
    val elem =
      <summary-control-columns>{
        ordered.map { col =>
          <column
            control={col.key.control}
            section-name={col.key.sectionNameOpt.getOrElse("")}
            default-pos={col.defaultPos.toString}
            role-restricted={col.roleRestricted.toString}>{
            col.labels.map { case (lang, text) =>
              <label lang={lang}>{text}</label>
            } ++
            col.sectionLabels.map { case (lang, text) =>
              <section-label lang={lang}>{text}</section-label>
            }
          }</column>
        }
      }</summary-control-columns>

    elemToNodeInfo(elem)
  }

  def controlSummaryPositionInfo(controlName: String)(implicit ctx: FormBuilderDocContext): NodeInfo = {
    val trimmedName = controlName.trimAllToEmpty
    val targetKey   = SummaryControlColumn(trimmedName, None)
    val customOrder = FormRunnerSummaryOrder.readSummaryControlColumns(ctx.metadataRootElemOpt)
    val defaultCols = findDefaultSummaryControlColumns
    val orderedCols = FormRunnerSummaryOrder.sortBySummaryOrder(defaultCols, customOrder)(_.key)

    val existingIdx = orderedCols.indexWhere(_.key == targetKey)
    val (initiallyEnabled, activePos, newPos, otherCount) =
      if (existingIdx >= 0) {
        val pos = existingIdx + 1
        (true, pos, pos, orderedCols.size - 1)
      } else if (trimmedName.isEmpty) {
        (false, 1, orderedCols.size + 1, orderedCols.size)
      } else {
        val withTargetDefault = collectCandidates(includeControlNameOpt = Some(trimmedName))
        val simulatedOrdered  = FormRunnerSummaryOrder.sortBySummaryOrder(withTargetDefault, customOrder)(_.key)
        val simIdx            = simulatedOrdered.indexWhere(_.key == targetKey)
        val computedNewPos    = if (simIdx >= 0) simIdx + 1 else orderedCols.size + 1
        (false, computedNewPos, computedNewPos, orderedCols.size)
      }

    elemToNodeInfo(
      <summary-position
        initially-enabled={initiallyEnabled.toString}
        active-pos={activePos.toString}
        new-pos={newPos.toString}
        other-count={otherCount.toString}/>
    )
  }

  private object Private {

    case class RawCandidate(
      key           : SummaryControlColumn,
      roleRestricted: Boolean,
      labels        : List[(String, String)],
      sectionLabels : List[(String, String)]
    )

    private val LegacySummaryClasses = Set("fr-summary", "fr-search")

    def isStaticallySummaryEnabled(control: NodeInfo): (Boolean, Boolean) =
      (control / "*:index").headOption match {
        case Some(indexElem) =>
          val summaryShowOpt   = (indexElem / "*:summary-show").headOption
          val summarySearchOpt = (indexElem / "*:summary-search").headOption

          val showEnabled =
            summaryShowOpt.exists { ss =>
              ! (ss.attValueOpt("column").contains("false") && ss.attValueOpt("search").contains("false"))
            } || summarySearchOpt.isDefined

          val roleRestricted =
            summaryShowOpt.exists(_.attValueNonBlankOpt("require-role").isDefined) ||
            summarySearchOpt.exists(_.attValueNonBlankOpt("require-role").isDefined)

          (showEnabled, roleRestricted)

        case None if control.attValueNonBlankOpt("value").isDefined && (control / (FR -> "summary-show")).nonEmpty =>
          val ss             = (control / (FR -> "summary-show")).head
          val showEnabled    = ! ss.attValueOpt("column").contains("false")
          val roleRestricted = ss.attValueNonBlankOpt("require-role").isDefined
          (showEnabled, roleRestricted)

        case None =>
          val legacyEnabled = LegacySummaryClasses.exists(control.attClasses)
          (legacyEnabled, false)
      }

    def buildResourcesMap(resourcesRootOpt: Option[NodeInfo]): Map[String, Map[String, NodeInfo]] =
      resourcesRootOpt.toList
        .flatMap(_ / "resource")
        .map { resElem =>
          val lang    = resElem.attValue(XMLLangQName)
          val holders = (resElem / *).map(h => h.localname -> h).toMap
          lang -> holders
        }
        .toMap

    def resolveLabels(
      controlElem    : NodeInfo,
      controlName    : String,
      formLangs      : Seq[String],
      resourcesByLang: Map[String, Map[String, NodeInfo]]
    ): List[(String, String)] = {
      val isHtml = hasHTMLMediatype(findControlOrSeparateLabelElem(controlElem))

      val inlineLabelOpt =
        findControlOrSeparateLabelElem(controlElem)
          .headOption
          .filter(l => l.attValueOpt("ref").isEmpty && l.attValueOpt("value").isEmpty)
          .flatMap(_.stringValue.trimAllToOpt)

      formLangs.map { lang =>
        val holderLabelOpt =
          resourcesByLang
            .get(lang)
            .flatMap(_.get(controlName))
            .flatMap(_.elemValueOpt(LHHA.Label.entryName))
            .map(v => if (isHtml) removeHtmlTags(v) else v)
            .flatMap(_.trimAllToOpt)

        lang -> holderLabelOpt.orElse(inlineLabelOpt).getOrElse("")
      }.toList
    }

    def collectCandidates(
      includeControlNameOpt: Option[String]
    )(implicit
      ctx: FormBuilderDocContext
    ): List[RawCandidate] = {

      val formLangs          = allLangs(ctx.resourcesRootElem).toList
      val topResourcesByLang = buildResourcesMap(ctx.resourcesRootElemOpt)
      val headOpt            = (ctx.formDefinitionRootElem / "*:head").headOption

      val topLevelCandidates: List[RawCandidate] =
        (ctx.bodyElem descendant * filter IsControl).toList.flatMap { controlElem =>
          getControlNameOpt(controlElem).flatMap { controlName =>
            val (enabled, roleRestricted) = isStaticallySummaryEnabled(controlElem)
            if (enabled || includeControlNameOpt.contains(controlName))
              Some(
                RawCandidate(
                  key            = SummaryControlColumn(controlName, None),
                  roleRestricted = roleRestricted,
                  labels         = resolveLabels(controlElem, controlName, formLangs, topResourcesByLang),
                  sectionLabels  = Nil
                )
              )
            else
              None
          }
        }

      val sectionTemplateCandidates: List[RawCandidate] =
        for {
          head        <- headOpt.toList
          sectionElem <- findSectionsWithTemplates
          sectionName <- getControlNameOpt(sectionElem).toList
          bindingDoc  <- xblBindingForSection(head, sectionElem).toList
          bindingElem = bindingDoc.rootElement
          tmplResRoot = findXblInstance(bindingElem, Names.FormResources).flatMap(_.firstChildOpt(*))
          tmplResMap  = buildResourcesMap(tmplResRoot)
          fallbackMap = tmplResMap.values.headOption.getOrElse(Map.empty)
          normResMap  = formLangs.map(lang => lang -> tmplResMap.getOrElse(lang, fallbackMap)).toMap
          secLabels   = resolveLabels(sectionElem, sectionName, formLangs, topResourcesByLang)
          controlElem <- (bindingElem / XBLTemplateTest descendant * filter IsControl).toList
          controlName <- getControlNameOpt(controlElem).toList
          (enabled, roleRestricted) = isStaticallySummaryEnabled(controlElem)
          if enabled
        } yield
          RawCandidate(
            key            = SummaryControlColumn(controlName, Some(sectionName)),
            roleRestricted = roleRestricted,
            labels         = resolveLabels(controlElem, controlName, formLangs, normResMap),
            sectionLabels  = secLabels
          )

      topLevelCandidates ::: sectionTemplateCandidates
    }
  }
}
