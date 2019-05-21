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

import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.datamigration.MigrationSupport.{findMigrationForVersion, findMigrationOps}
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunner}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.MarkupUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

trait FormDefinition {

  private val FRSummary       = "fr-summary"
  private val FRSearch        = "fr-search"

  private val ClassesPredicate = Set(FRSummary, FRSearch)

  // Returns the controls that are searchable from a form definition
  def findIndexedControls(
    formDoc                   : DocumentInfo,
    databaseDataFormatVersion : DataFormatVersion
  ): Seq[IndexedControl] = {

    // Controls in the view, with the `fr-summary` or `fr-search` class
    val indexedControlBindPathHolders = {

      // Support missing `head` and `body` in particular for tests. That just means there is nothing to index.
      val headOpt = formDoc / "*:html" / "*:head" headOption
      val bodyOpt = formDoc / "*:html" / "*:body" headOption

      bodyOpt.toList flatMap { body ⇒

        val topLevelOnly =
          FormRunner.searchControlsTopLevelOnly(
            body      = body,
            data      = None,
            predicate = FormRunner.hasAnyClassPredicate(ClassesPredicate)
          )

        val withSectionTemplatesOpt =
          headOpt map { head ⇒
            FormRunner.searchControlsUnderSectionTemplates(
              head      = head,
              body      = body,
              data      = None,
              predicate = FormRunner.hasAnyClassPredicate(ClassesPredicate)
            )
          }

        topLevelOnly ++ withSectionTemplatesOpt.toList.flatten
      }
    }

    val (_, migrationOpsToApply) =
      findMigrationOps(
        srcVersion = DataFormatVersion.Edge,
        dstVersion = databaseDataFormatVersion
      )

    // Find the migration functions once and for all
    val pathMigrationFunctions =
      for {
        metadataElem ← FormRunner.metadataInstanceRootOpt(formDoc).toList
        ops          ← migrationOpsToApply
        json         ← findMigrationForVersion(metadataElem, ops.version)
      } yield
        ops.adjustPathTo40(ops.decodeMigrationSetFromJson(json), _)

    indexedControlBindPathHolders map { case FormRunner.ControlBindPathHoldersResources(control, bind, path, _, resources) ⇒

      val adjustedBindPathElems =
        (pathMigrationFunctions.iterator flatMap (_.apply(path)) nextOption()) getOrElse path

      IndexedControl(
        name      = FormRunner.getControlName(control),
        inSearch  = control.attClasses(FRSearch),
        inSummary = control.attClasses(FRSummary),
        xpath     = adjustedBindPathElems map (_.value) mkString "/",
        xsType    = (bind /@ "type" map (_.stringValue)).headOption getOrElse "xs:string",
        control   = control.localname,
        htmlLabel = FormRunner.hasHTMLMediatype(control / (XF → "label")),
        resources = resources.toList
      )
    }
  }

  case class IndexedControl(
    name      : String,
    inSearch  : Boolean,
    inSummary : Boolean,
    xpath     : String,
    xsType    : String,
    control   : String,
    htmlLabel : Boolean,
    resources : List[(String, NodeInfo)]
  ) {
    def toXML: NodeInfo =
      <query
        name={name}
        path={xpath}
        type={xsType}
        control={control}
        search-field={inSearch.toString}
        summary-field={inSummary.toString}
        match="substring"
        html-label={htmlLabel.toString}>{
        for ((lang, resourceHolder) ← resources)
          yield
            <resources lang={lang}>{
              val labelElemOpt =
                resourceHolder elemValueOpt "label" map { label ⇒
                  <label>{if (htmlLabel) label else MarkupUtils.escapeXMLMinimal(label)}</label>
                }

              val itemElems = resourceHolder child "item" map { item ⇒
                <item>{
                  <label>{item elemValue "label"}</label>
                  <value>{item elemValue "value"}</value>
                }</item>
              }

              labelElemOpt.toList ++ itemElems
            }</resources>
      }</query>
  }
}
