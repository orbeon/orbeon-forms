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
import org.orbeon.oxf.fr.persistence.relational.{IndexedControl, IndexSettings}
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunner, InDocFormRunnerDocContext}
import org.orbeon.saxon.om
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath._


trait FormDefinition {

  // Legacy classes
  private val FRIndex         = "fr-index"
  private val FRSummaryShow   = "fr-summary"
  private val FRSummarySearch = "fr-search"

  private val ClassesPredicate = Set(FRIndex, FRSummaryShow, FRSummarySearch)

  // Returns the controls that are searchable from a form definition
  def findIndexedControls(
    formDoc                   : DocumentInfo,
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
        name          = controlName,
        indexSettings = indexSettings(control, forUserRoles),
        xpath         = path map (_.value) mkString "/",
        xsType        = (bind /@ "type" map (_.stringValue)).headOption getOrElse "xs:string",
        control       = control.localname,
        htmlLabel     = FormRunner.hasHTMLMediatype(control / (XF -> "label")),
        resources     = resources.toList
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

  def indexSettings(
    control      : NodeInfo,
    forUserRoles : Option[List[String]]
  ): IndexSettings = {
    // Check the presence/absence of a given sub-element for a given field
    def setting(field: NodeInfo, elementName: String): Boolean =
      (field / elementName).headOption match {
        case Some(element) =>
          val requiredRoleOpt = (element /@ "require-role").map(_.stringValue).headOption.filter(_.nonEmpty)

          requiredRoleOpt match {
            case Some(requiredRole) => forUserRoles.forall(_.contains(requiredRole))
            case None               => true
          }

        case None =>
          false
      }

    (control / "*:index").headOption match {
      case Some(index) =>
        // Look for settings in control sub-elements
        IndexSettings(
          summaryShow   = setting(index, "*:summary-show"),
          summarySearch = setting(index, "*:summary-search"),
          summaryEdit   = setting(index, "*:summary-edit")
        )

      case None =>
        // Control sub-elements not present, use control classes (legacy)
        IndexSettings(
          summaryShow   = control.attClasses(FRSummaryShow),
          summarySearch = control.attClasses(FRSummarySearch),
          summaryEdit   = false
        )
    }
  }
}
