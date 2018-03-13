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
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.{XFormsSwitchControl, XFormsVariableControl}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.MapFunctions
import org.orbeon.saxon.om.{SequenceIterator, ValueRepresentation}
import org.orbeon.saxon.value.{AtomicValue, Value}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import shapeless._
import shapeless.syntax.typeable._

object Wizard {

  import Private._

  //@XPathFunction
  def normalizeWizardMode(mode: String): String =
    mode match {
      case "true" | "lax" ⇒ "lax"
      case "strict"       ⇒ "strict"
      case _              ⇒ "free"
    }

  //@XPathFunction
  def isWizardSeparateToc: Boolean =
    formRunnerProperty("oxf.xforms.xbl.fr.wizard.separate-toc")(FormRunnerParams()) contains "true"

  def isWizardTocShown: Boolean =
    findWizardState map (_ elemValue "show-toc") contains "true"

  def isWizardBodyShown: Boolean =
    findWizardState map (_ elemValue "show-body") contains "true"

  def getWizardValidatedMode: String =
    findWizardState map (_ elemValue "validate") getOrElse (throw new IllegalStateException)

  def isWizardValidatedMode: Boolean =
    getWizardValidatedMode != "free"

  def wizardCurrentCaseIdOpt: Option[String] =
    findWizardState map (_ elemValue "current-case-id") flatMap (_.trimAllToOpt)

  def wizardAvailableSections: Set[String] = {

    val resultOpt =
      for {
        model    ← findWizardModel
        instance ← model.findInstance("available-sections")
        root     = instance.rootElement
      } yield
        stringToSet(root.stringValue)

    resultOpt getOrElse (throw new IllegalStateException)
  }

  def isWizardLastPage: Boolean =
    findWizardVariableValue("fr-wizard-is-last-nav") exists booleanValue

  def isWizardFirstPage: Boolean =
    findWizardVariableValue("fr-wizard-is-first-nav") exists booleanValue

  //@XPathFunction
  def gatherTopLevelSectionStatusJava(relevantTopLevelSectionIds: Array[String]): SequenceIterator =
    gatherTopLevelSectionStatus(relevantTopLevelSectionIds.to[List]) map { sectionStatus ⇒
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

  //@XPathFunction
  def caseIdsForTopLevelSection(topLevelSectionId: String): SequenceIterator =
    for {
      control               ← inScopeContainingDocument.resolveObjectByIdInScope("#document", s"$topLevelSectionId-switch", None).toList
      switchControl         ← control.cast[XFormsSwitchControl].toList
      subsectionCaseControl ← switchControl.getChildrenCases
    } yield
      subsectionCaseControl.getId

  private object Private {

    case class SectionStatus(
      name                : String,
      isVisited           : Boolean,
      hasIncompleteFields : Boolean,
      hasErrorFields      : Boolean,
      isAccessible        : Boolean
    )

    def findWizardContainer =
      XFormsAPI.resolveAs[XFormsComponentControl]("fr-view-component") map (_.nestedContainer)

    def findWizardModel =
      for {
        container ← findWizardContainer
        model     ← container.defaultModel
      } yield
        model

    def findWizardState =
      for {
        model     ← findWizardModel
        instance  ← model.defaultInstanceOpt
      } yield
        instance.rootElement

    def findWizardVariableValue(staticOrAbsoluteId: String) =
      XFormsAPI.resolveAs[XFormsVariableControl](staticOrAbsoluteId) flatMap (_.valueOpt)

    def booleanValue(value: ValueRepresentation) = value match {
      case v: Value    ⇒ v.effectiveBooleanValue
      case _           ⇒ false
    }

    def gatherTopLevelSectionStatus(relevantTopLevelSectionIds: List[String]): List[SectionStatus] = {

      val topLevelSectionNamesWithErrorsMap =
        ErrorSummary.topLevelSectionsWithErrors(
          relevantTopLevelSectionIds flatMap controlNameFromIdOpt toSet,
          onlyVisible = false
       )

      val wizardMode = getWizardValidatedMode

      val sectionStatusesWithDummyHead =
        relevantTopLevelSectionIds.scanLeft(None: Option[SectionStatus]) { case (prevOpt, sectionId) ⇒

          val sectionName = controlNameFromId(sectionId)

          val sectionBoundNode =
            XFormsAPI.resolveAs[XFormsComponentControl](sectionId) flatMap (_.boundNode) getOrElse (throw new IllegalStateException)

          val isVisited = sectionBoundNode hasAtt "*:section-status"

          def strictIsAccessible =
            prevOpt match {
              case Some(prev) ⇒ prev.isAccessible && ! (prev.hasIncompleteFields || prev.hasErrorFields)
              case None       ⇒ true
            }

          val isAccessible =
            wizardMode match {
              case "free"   ⇒ true
              case "lax"    ⇒ isVisited || strictIsAccessible
              case "strict" ⇒ strictIsAccessible
            }

          Some(
            SectionStatus(
              name                = sectionName,
              isVisited           = isVisited,
              hasIncompleteFields = topLevelSectionNamesWithErrorsMap.get(sectionName) exists (_._1 > 0),
              hasErrorFields      = topLevelSectionNamesWithErrorsMap.get(sectionName) exists (_._2 > 0),
              isAccessible        = isAccessible
            )
          )
        }

      sectionStatusesWithDummyHead.flatten
    }
  }

}
