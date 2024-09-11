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

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.library.FRComponentParamSupport.{ancestorContainerNamesIt, closestAncestorSectionName}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.control.controls.{XFormsSwitchControl, XFormsVariableControl}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.Constants
import shapeless.syntax.typeable._


object Wizard {

  import Private._

  //@XPathFunction
  def normalizeWizardMode(mode: String): String =
    mode match {
      case "true" | "lax" => "lax"
      case "strict"       => "strict"
      case _              => "free"
    }

  def isWizardTocShown: Boolean =
    findWizardState map (_ elemValue "show-toc") contains "true"

  def isWizardBodyShown: Boolean =
    findWizardState map (_ elemValue "show-body") contains "true"

  def getWizardValidatedMode: String =
    findWizardState map (_ elemValue "validate") getOrElse (throw new IllegalStateException)

  def isWizardValidatedMode: Boolean =
    getWizardValidatedMode != "free"

  def isWizardSeparateToc: Boolean =
    findWizardState map (_ elemValue "separate-toc") contains "true"

  def wizardAvailableSections: Set[String] = {

    val resultOpt =
      for {
        model    <- findWizardModel
        instance <- model.findInstance("available-top-level-sections")
        root     = instance.rootElement
      } yield
        root.stringValue.tokenizeToSet

    resultOpt getOrElse (throw new IllegalStateException)
  }

  def isWizardFirstPage: Boolean =
    findWizardVariableValue("fr-wizard-is-first-nav") exists booleanValue

  def isWizardLastPage: Boolean =
    findWizardVariableValue("fr-wizard-is-last-nav") exists booleanValue

  def isPrevAllowed: Boolean =
    findWizardVariableValue("fr-wizard-allow-prev") exists booleanValue

  def isNextAllowed: Boolean =
    findWizardVariableValue("fr-wizard-allow-next") exists booleanValue

  def wizardCurrentCaseIdOpt: Option[String] =
    findWizardState map (_ elemValue "current-case-id") flatMap (_.trimAllToOpt)

  //@XPathFunction
  def sectionIdFromCaseIdOpt(id: String): Option[String] =
    id.endsWith("-case") option id.substring(0, id.length - "-case".length)

  def wizardCurrentPageNameOpt: Option[String] =
    wizardCurrentCaseIdOpt            flatMap
      sectionIdFromCaseIdOpt          flatMap
      FormRunner.controlNameFromIdOpt

  //@XPathFunction
  def gatherTopLevelSectionStatusJava(relevantTopLevelSectionIds: Array[String]): om.SequenceIterator =
    gatherTopLevelSectionStatus(relevantTopLevelSectionIds.to(List)) map { sectionStatus =>
      SaxonUtils.newMapItem(
        Map[AtomicValue, ValueRepresentationType](
          (SaxonUtils.fixStringValue("name")                         , sectionStatus.name),
          (SaxonUtils.fixStringValue("is-visited")                   , sectionStatus.isVisited),
          (SaxonUtils.fixStringValue("has-incomplete-fields")        , sectionStatus.hasIncompleteFields),
          (SaxonUtils.fixStringValue("has-error-fields")             , sectionStatus.hasErrorFields),
          (SaxonUtils.fixStringValue("has-visible-incomplete-fields"), sectionStatus.hasVisibleIncompleteFields),
          (SaxonUtils.fixStringValue("has-visible-error-fields")     , sectionStatus.hasVisibleErrorFields),
          (SaxonUtils.fixStringValue("is-available")                 , sectionStatus.isAccessible)
        )
      )
    }

  // `export`
  def gatherTopLevelSectionStatus(relevantTopLevelSectionIds: List[String]): List[SectionStatus] =
    Private.gatherTopLevelSectionStatus(relevantTopLevelSectionIds)

  //@XPathFunction
  def caseIdsForTopLevelSection(topLevelSectionId: String): om.SequenceIterator =
    caseIdsForTopLevelSectionAsList(topLevelSectionId)

  def caseIdsForTopLevelSectionAsList(topLevelSectionId: String): List[String] =
    for {
      control               <- inScopeContainingDocument.resolveObjectByIdInScope(Constants.DocumentId, s"$topLevelSectionId-switch", None).toList
      switchControl         <- control.narrowTo[XFormsSwitchControl].toList
      subsectionCaseControl <- switchControl.getChildrenCases
    } yield
      subsectionCaseControl.getId

  //@XPathFunction
  def closestAncestorSectionNameForControlId(controlId: String): Option[String] =
    inScopeContainingDocument.resolveObjectByIdInScope(Constants.DocumentId, controlId).collect {
      case control: XFormsControl => closestAncestorSectionName(control)
    }.flatten

  //@XPathFunction
  def ancestorContainerNamesForControlId(controlId: String): Array[String] =
    inScopeContainingDocument.resolveObjectByIdInScope(Constants.DocumentId, controlId).iterator.collect {
      case control: XFormsControl => ancestorContainerNamesIt(control)
    }.flatten.toArray

  case class SectionStatus(
    name                       : String,
    isVisited                  : Boolean,
    hasIncompleteFields        : Boolean,
    hasErrorFields             : Boolean,
    hasVisibleIncompleteFields : Boolean,
    hasVisibleErrorFields      : Boolean,
    isAccessible               : Boolean
  )

  private object Private {

    def findWizardContainer: Option[XBLContainer] =
      XFormsAPI.resolveAs[XFormsComponentControl]("fr-view-component") flatMap (_.nestedContainerOpt)

    def findWizardModel: Option[XFormsModel] =
      for {
        container <- findWizardContainer
        model     <- container.findDefaultModel
      } yield
        model

    def findWizardState: Option[om.NodeInfo] =
      for {
        model     <- findWizardModel
        instance  <- model.defaultInstanceOpt
      } yield
        instance.rootElement

    def findWizardVariableValue(staticOrAbsoluteId: String): Option[ValueRepresentationType] =
      XFormsAPI.resolveAs[XFormsVariableControl](staticOrAbsoluteId) flatMap (_.valueOpt)

    def booleanValue(value: ValueRepresentationType) = value match {
      case v: AtomicValue => SaxonUtils.effectiveBooleanValue(v.iterate())
      case _              => false
    }

    def gatherTopLevelSectionStatus(relevantTopLevelSectionIds: List[String]): List[SectionStatus] = {

      val sectionNames = relevantTopLevelSectionIds flatMap controlNameFromIdOpt toSet

      val topLevelSectionNamesWithErrorsMap =
        ErrorSummary.topLevelSectionsWithErrors(sectionNamesSet = sectionNames, onlyVisible = false)

      val visibleTopLevelSectionNamesWithErrorsMap =
        ErrorSummary.topLevelSectionsWithErrors(sectionNamesSet = sectionNames, onlyVisible = true)

      val wizardMode = getWizardValidatedMode

      val sectionStatusesWithDummyHead =
        relevantTopLevelSectionIds.scanLeft(None: Option[SectionStatus]) { case (prevOpt, sectionId) =>

          val sectionName = controlNameFromId(sectionId)

          // 2021-02-16: Allow for "missing" bindings in case of initially non-relevant/lazily loaded sections.
          val sectionBoundNodeOpt =
            XFormsAPI.resolveAs[XFormsComponentControl](sectionId) flatMap (_.boundNodeOpt)

          val isVisited = sectionBoundNodeOpt exists (_ hasAtt "*:section-status")

          def strictIsAccessible =
            prevOpt match {
              case Some(prev) => prev.isAccessible && ! (prev.hasIncompleteFields || prev.hasErrorFields)
              case None       => true
            }

          val isAccessible =
            wizardMode match {
              case "free"   => true
              case "lax"    => isVisited || strictIsAccessible
              case "strict" => strictIsAccessible
            }

          val incompleteAndErrorCounts        = topLevelSectionNamesWithErrorsMap.get(sectionName)
          val visibleIncompleteAndErrorCounts = visibleTopLevelSectionNamesWithErrorsMap.get(sectionName)

          Some(
            SectionStatus(
              name                       = sectionName,
              isVisited                  = isVisited,
              hasIncompleteFields        = incompleteAndErrorCounts exists (_._1 > 0),
              hasErrorFields             = incompleteAndErrorCounts exists (_._2 > 0),
              hasVisibleIncompleteFields = visibleIncompleteAndErrorCounts exists (_._1 > 0),
              hasVisibleErrorFields      = visibleIncompleteAndErrorCounts exists (_._2 > 0),
              isAccessible               = isAccessible
            )
          )
        }

      sectionStatusesWithDummyHead.flatten
    }
  }
}
