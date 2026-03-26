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

import org.orbeon.dom.QName
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.fr.datamigration.PathElem
import org.orbeon.oxf.fr.importexport.ImportExportSupport.isBindRequired
import org.orbeon.oxf.fr.permission.SimpleConstraint
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.relational.{IndexedControl, SearchableValues, SummarySettings}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.saxon.om
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI


trait FormDefinition {

  // Legacy classes
  private val FRIndex         = "fr-index"
  private val FRSummaryShow   = "fr-summary"
  private val FRSummarySearch = "fr-search"

  private val ClassesPredicate = Set(FRIndex, FRSummaryShow, FRSummarySearch)

  private val databoundControlLocalNames =
    Set[QName](FRDataboundSelect1QName, FRDataboundSelect1SearchQName).map(_.localName)

  private          val logger                   = LoggerFactory.createLogger(classOf[FormDefinition])
//  private implicit val indentedLogger           = new IndentedLogger(logger)
  private implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

  private def withDynamicItems(
    resources     : collection.Seq[(String, NodeInfo)],
    distinctValues: Seq[String]
  ): collection.Seq[(String, NodeInfo)] = {
    // Remove empty values as they're not allowed for dropdown values and use them as labels
    val items = distinctValues.filter(_.nonEmpty).map { value =>
      <item>
        <label>{value}</label>
        <value>{value}</value>
      </item>
    }

    // Inject items retrieved from database into resources
    for ((lang, resourceHolder) <- resources) yield {
      val resourceElem = nodeInfoToElem(resourceHolder)
      lang -> (resourceElem.copy(child = resourceElem.child.filterNot(_.label == "item") ++ items): NodeInfo)
    }
  }



  // Returns the controls and other values that are searchable from a form definition
  def searchableValues(
    formDoc                     : DocumentNodeInfoType,
    appForm                     : AppForm,
    searchVersionOpt            : Option[SearchVersion],
    databaseDataFormatVersion   : DataFormatVersion,
    providedResourcesRootElemOpt: Option[NodeInfo] = None // present for the Form Builder Summary page only
  )(implicit
    indentedLogger: IndentedLogger
  ): SearchableValues = {

    implicit val ctx = new InDocFormRunnerDocContext(formDoc) {
      override lazy val bodyElemOpt: Option[om.NodeInfo] = {

        // For "Form Builder as form definition". This is used by the Summary page and the `reindex()` feature.
        frc.findFormRunnerBodyElem(formDoc) orElse
          (formDoc.rootElement / "*:body" / "*:div" find (_.id == "fb-pseudo-body"))
      }

      // Support override for the Form Builder Summary page
      override lazy val resourcesRootElemOpt: Option[om.NodeInfo] = {

        def fromSrc: Option[NodeInfo] =
          resourcesInstanceElemOpt
            .flatMap(_.attValueOpt("src"))
            .map(URI.create)
            .map(XFormsCrossPlatformSupport.readTinyTreeFromUrl(_, handleXInclude = true))
            .map(_.rootElement)

        def fromInline: Option[NodeInfo] =
          resourcesInstanceElemOpt.flatMap(_.firstChildOpt(*))

        providedResourcesRootElemOpt
          .orElse(fromSrc)
          .orElse(fromInline)
      }
    }

    // Look for indexed controls with fr:index sub-element or fr-index, fr-summary, etc. classes (legacy)
    val indexedControlBindPathHolders =
      FormRunner.searchControlsInFormBySubElement(subElements = Set("*:index"),   databaseDataFormatVersion) ++
      FormRunner.searchControlsInFormByClass     (classes     = ClassesPredicate, databaseDataFormatVersion)

    def pathString(path: List[PathElem]) = path.map(_.value).mkString("/")

    val distinctValuesOpt = searchVersionOpt.toSeq.map { searchVersion =>
      val dynamicControlPaths = indexedControlBindPathHolders.filter { controlInfo =>
        // Is the control databound/dynamic?
        databoundControlLocalNames.contains(controlInfo.control.localname)
      }.map { controlInfo =>
        pathString(controlInfo.path)
      }

      // Retrieve distinct values for all dynamic controls
      PersistenceApi.distinctValues(appForm, searchVersion, dynamicControlPaths)
    }

    val distinctValuesByControlPath = distinctValuesOpt.flatMap(_.controls).map { controlDetails =>
      controlDetails.path -> controlDetails.distinctValues
    }.toMap

    val controls = indexedControlBindPathHolders map { case ControlBindPathHoldersResources(control, bind, path, _, resources) =>
      val controlName = FormRunner.getControlName(control)
      val controlPath = pathString(path)

      val resourcesWithDynamicItems = distinctValuesByControlPath.get(controlPath) match {
        case Some(distinctValues) => withDynamicItems(resources, distinctValues)
        case None                 => resources
      }

      IndexedControl(
        name               = controlName,
        xpath              = controlPath,
        xsType             = (bind /@ "type" map (_.stringValue)).headOption getOrElse "xs:string",
        control            = control.localname,
        summarySettings    = summarySettings(control),
        staticallyRequired = isBindRequired(bind),
        htmlLabel          = FormRunner.hasHTMLMediatype(control / (XF -> "label")),
        resources          = resourcesWithDynamicItems.toList
      )
    }

    SearchableValues(
      controls        = controls,
      createdBy       = distinctValuesOpt.flatMap(_.createdBy),
      lastModifiedBy  = distinctValuesOpt.flatMap(_.lastModifiedBy),
      workflowStage   = distinctValuesOpt.flatMap(_.workflowStage)
    )
  }

  def matchForControl(control: String): String =
    if (control == "input" || control == "textarea" || control == "output")
      "substring"
    else if (FormRunner.isMultipleSelectionControl(control))
      "token"
    else
      "exact"

  def summarySettings(control: NodeInfo): SummarySettings = {

    // Check the presence/absence of a given sub-element for a given control
    def setting(control: NodeInfo, elementName: String, attOpt: Option[String] = None): Boolean =
      (control / elementName).headOption match {
        // An optional attribute is checked (default value: true)
        case Some(element) if attOpt.forall(element.attValueNonBlankOpt(_).forall(_ == "true")) =>

          SimpleConstraint.satisfiedForUserRolesFromCurrentSession(
            rolesOpt      = element.attValueNonBlankOpt("require-role"),
            constraintOpt = element.attValueNonBlankOpt("require-role-constraint")
          )

        case _ =>
          false
      }

    (control / "*:index").headOption match {
      case Some(index) =>
        // Look for settings in control sub-elements (current and legacy format)
        val show   = setting(index, "*:summary-show",    Some("column"))
        val search = setting(index, "*:summary-show",    Some("search")) || setting(index, "*:summary-search")
        val edit   = setting(index, "*:allow-bulk-edit", None          ) || setting(index, "*:summary-edit")

        SummarySettings(
          // #5994: keep show/search separate for now (for compatibility reasons), but we'd like to have a single setting ideally
          show          = show,
          search        = search,
          // #6010: edit setting enabled only if show and/or search enabled as well
          edit          = (show || search) && edit,
          editProcess   = (index / "*:allow-bulk-edit"     /@ "process"  ).headOption.map(_.stringValue),
          sortDirection = (index / "*:default-sort-column" /@ "direction").headOption.map(_.stringValue)
        )

      case None =>
        // Control sub-elements not present, use control classes (legacy)
        SummarySettings(
          show          = control.attClasses(FRSummaryShow),
          search        = control.attClasses(FRSummarySearch),
          edit          = false,
          editProcess   = None,
          sortDirection = None
        )
    }
  }
}
