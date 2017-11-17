/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.XFormsVariableControl
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.MapFunctions
import org.orbeon.saxon.om.{SequenceIterator, ValueRepresentation}
import org.orbeon.saxon.value.{AtomicValue, Value}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

object Wizard {

  //@XPathFunction
  def normalizeWizardMode(mode: String): String =
    mode match {
      case "true" | "lax" ⇒ "lax"
      case "strict"       ⇒ "strict"
      case _              ⇒ "free"
    }

  //@XPathFunction
  def isWizardSeparateToc =
    formRunnerProperty("oxf.xforms.xbl.fr.wizard.separate-toc")(FormRunnerParams()) contains "true"

  private def findWizardContainer =
    XFormsAPI.resolveAs[XFormsComponentControl]("fr-view-component") map (_.nestedContainer)

  private def findWizardState =
    for {
      container ← findWizardContainer
      model     ← container.defaultModel
      instance  ← model.defaultInstanceOpt
    } yield
      instance.rootElement

  def isWizardTocShown =
    findWizardState map (_ elemValue "show-toc") contains "true"

  def isWizardBodyShown =
    findWizardState map (_ elemValue "show-body") contains "true"

  def getWizardValidatedMode =
    findWizardState map (_ elemValue "validate") getOrElse (throw new IllegalStateException)

  def isWizardValidatedMode =
    getWizardValidatedMode != "free"

  def wizardCurrentCaseIdOpt =
    findWizardState map (_ elemValue "current-case-id") flatMap (_.trimAllToOpt)

  private def findWizardVariableValue(staticOrAbsoluteId: String) =
    XFormsAPI.resolveAs[XFormsVariableControl](staticOrAbsoluteId) flatMap (_.valueOpt)

  private def booleanValue(value: ValueRepresentation) = value match {
    case v: Value    ⇒ v.effectiveBooleanValue
    case _           ⇒ false
  }

  def isWizardLastPage =
    findWizardVariableValue("fr-wizard-is-last-nav") exists booleanValue

  def isWizardFirstPage =
    findWizardVariableValue("fr-wizard-is-first-nav") exists booleanValue

  case class SectionStatus(
    name                : String,
    isVisited           : Boolean,
    hasIncompleteFields : Boolean,
    hasErrorFields      : Boolean,
    isAccessible        : Boolean
  )

  private val DummyLeftSectionStatus =
    SectionStatus(
      name                = "",
      isVisited           = true,
      hasIncompleteFields = false,
      hasErrorFields      = false,
      isAccessible        = true
    )

  //@XPathFunction
  def gatherSectionStatusJava(relevantTopLevelSectionIds: Array[String]): SequenceIterator =
    gatherSectionStatus(relevantTopLevelSectionIds.to[List]) map { sectionStatus ⇒
      MapFunctions.createValue(
        Map[AtomicValue, ValueRepresentation](
          (SaxonUtils.fixStringValue("name")                 , sectionStatus.name),
          (SaxonUtils.fixStringValue("is-visited")           , sectionStatus.isVisited),
          (SaxonUtils.fixStringValue("has-incomplete-fields"), sectionStatus.hasIncompleteFields),
          (SaxonUtils.fixStringValue("has-error-fields")     , sectionStatus.hasErrorFields),
          (SaxonUtils.fixStringValue("is-available")         , sectionStatus.isAccessible)
        )
      )
    }

  def gatherSectionStatus(relevantTopLevelSectionIds: List[String]): List[SectionStatus] = {

    val topLevelSectionNamesWithErrorsMap =
      ErrorSummary.topLevelSectionsWithErrors(
        relevantTopLevelSectionIds flatMap controlNameFromIdOpt toSet,
        onlyVisible = false
     )

    val wizardMode = getWizardValidatedMode

    val sectionStatusesWithDummyHead =
      relevantTopLevelSectionIds.scanLeft(DummyLeftSectionStatus) { case (prev, sectionId) ⇒

        val sectionName = controlNameFromId(sectionId)

        val sectionBoundNode =
          XFormsAPI.resolveAs[XFormsComponentControl](sectionId) flatMap (_.boundNode) getOrElse (throw new IllegalStateException)

        val isVisited = sectionBoundNode hasAtt "*:section-status"

        def strictIsAccessible =
          prev.isAccessible && ! (prev.hasIncompleteFields || prev.hasErrorFields)

        val isAccessible =
          wizardMode match {
            case "free"   ⇒ true
            case "lax"    ⇒ isVisited || strictIsAccessible
            case "strict" ⇒ strictIsAccessible
          }

        SectionStatus(
          name                = sectionName,
          isVisited           = isVisited,
          hasIncompleteFields = topLevelSectionNamesWithErrorsMap.get(sectionName) exists (_._1 > 0),
          hasErrorFields      = topLevelSectionNamesWithErrorsMap.get(sectionName) exists (_._2 > 0),
          isAccessible        = isAccessible
        )
      }

    sectionStatusesWithDummyHead.tail
  }

}
