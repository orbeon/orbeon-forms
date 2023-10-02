/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.index

import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.importexport.ImportExportSupport.isBindRequired
import org.orbeon.oxf.fr.persistence.relational.{IndexedControl, SummarySettings}
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunner, InDocFormRunnerDocContext}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.saxon.om
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._


trait FormDefinition {

  // Legacy classes
  private val FRIndex         = "fr-index"
  private val FRSummaryShow   = "fr-summary"
  private val FRSummarySearch = "fr-search"

  private val ClassesPredicate = Set(FRIndex, FRSummaryShow, FRSummarySearch)

  // Returns the controls that are searchable from a form definition
  def findIndexedControls(
    formDoc                   : DocumentNodeInfoType,
    databaseDataFormatVersion : DataFormatVersion,
    forUserRoles              : Option[List[String]]
  ): List[IndexedControl] = {

    implicit val ctx = new InDocFormRunnerDocContext(formDoc) {
      override lazy val bodyElemOpt: Option[om.NodeInfo] = {

        // For "Form Builder as form definition". This is used by the Summary page and the `reindex()` feature.
        frc.findFormRunnerBodyElem(formDoc) orElse
          (formDoc.rootElement / "*:body" / "*:div" find (_.id == "fb-pseudo-body"))
      }
    }

    // Look for indexed controls with fr:index sub-element or fr-index, fr-summary, etc. classes (legacy)
    val indexedControlBindPathHolders =
      FormRunner.searchControlsInFormBySubElement(subElements = Set("*:index"),   databaseDataFormatVersion) ++
      FormRunner.searchControlsInFormByClass     (classes     = ClassesPredicate, databaseDataFormatVersion)

    indexedControlBindPathHolders map { case ControlBindPathHoldersResources(control, bind, path, _, resources) =>
      val controlName = FormRunner.getControlName(control)

      IndexedControl(
        name               = controlName,
        xpath              = path map (_.value) mkString "/",
        xsType             = (bind /@ "type" map (_.stringValue)).headOption getOrElse "xs:string",
        control            = control.localname,
        summarySettings    = summarySettings(control, forUserRoles),
        staticallyRequired = isBindRequired(bind),
        htmlLabel          = FormRunner.hasHTMLMediatype(control / (XF -> "label")),
        resources          = resources.toList
      )
    }
  }

  def matchForControl(control: String): String =
    if (control == "input" || control == "textarea" || control == "output")
      "substring"
    else if (FormRunner.isMultipleSelectionControl(control))
      "token"
    else
      "exact"

  // Re-use existing Condition trait, which already has RolesAnyOf, but is more complex?
  protected object SimpleConstraint {
    sealed trait SimpleConstraint {
      def satisfiedFor(currentRoles: List[String]): Boolean
    }

    case class All(roles: List[String]) extends SimpleConstraint {
      override def satisfiedFor(currentRoles: List[String]): Boolean = roles.forall(currentRoles.contains)
    }

    case class Any(roles: List[String]) extends SimpleConstraint {
      override def satisfiedFor(currentRoles: List[String]): Boolean = roles.exists(currentRoles.contains)
    }

    case object AlwaysSatisfied extends SimpleConstraint {
      def satisfiedFor(currentRoles: List[String]): Boolean = true
    }

    def apply(rolesOpt: Option[String], constraintOpt: Option[String]): SimpleConstraint =
      rolesOpt match {
        case None =>
          AlwaysSatisfied

        case Some(roles) =>
          val rolesAsList = roles.splitTo[List]().map(_.trimAllToEmpty)
          val constraint  = constraintOpt.map(_.toLowerCase.trimAllToEmpty).getOrElse("all")

          constraint match {
            case "all" | "and" => All(rolesAsList)
            case "any" | "or"  => Any(rolesAsList)
            case _             => throw new IllegalArgumentException(s"Illegal roles constraint: $constraint")
          }
      }
  }

  def summarySettings(
    control         : NodeInfo,
    forUserRolesOpt : Option[List[String]]
  ): SummarySettings = {
    // Check the presence/absence of a given sub-element for a given field
    def setting(field: NodeInfo, elementName: String, attOpt: Option[String] = None): Boolean =
      (field / elementName).headOption match {
        // An optional attribute is checked (default value: true)
        case Some(element) if attOpt.forall(element.attValueNonBlankOpt(_).forall(_ == "true")) =>
          val rolesOpt      = element.attValueNonBlankOpt("require-role")
          val constraintOpt = element.attValueNonBlankOpt("require-role-constraint")

          forUserRolesOpt match {
            case Some(forUserRoles) =>
              val simpleConstraint = SimpleConstraint(rolesOpt = rolesOpt, constraintOpt = constraintOpt)
              simpleConstraint.satisfiedFor(forUserRoles)

            case None =>
              // Current user roles not specified (see readPublishedFormEncryptionAndIndexDetails case and tests)
              true
          }

        case _ =>
          false
      }

    (control / "*:index").headOption match {
      case Some(index) =>
        // Look for settings in control sub-elements (current and legacy format)
        SummarySettings(
          show   = setting(index, "*:summary-show", Some("column")) || setting(index, "*:summary-show"),
          search = setting(index, "*:summary-show", Some("search")) || setting(index, "*:summary-search"),
          edit   = setting(index, "*:allow-bulk-edit", None       ) || setting(index, "*:summary-edit")
        )

      case None =>
        // Control sub-elements not present, use control classes (legacy)
        SummarySettings(
          show   = control.attClasses(FRSummaryShow),
          search = control.attClasses(FRSummarySearch),
          edit   = false
        )
    }
  }
}
