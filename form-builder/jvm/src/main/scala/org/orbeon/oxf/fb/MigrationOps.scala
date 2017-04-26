/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.DataMigration
import org.orbeon.oxf.fr.DataMigration.{Migration, PathElem}
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._

object MigrationOps {

  private def findAllGridRepeats(doc: DocumentInfo) =
    findFRBodyElement(doc) descendant * filter IsGrid filter isRepeat

  private def findLegacyGridRepeats(doc: DocumentInfo) =
    findAllGridRepeats(doc) filter isLegacyRepeat

  //@XPathFunction
  def findAllRepeatNames(doc: DocumentInfo): SequenceIterator =
    findFRBodyElement(doc) descendant * filter isRepeat map getControlName

  // Used for migrating the form definition's legacy grid repeats
  // The rest of the implementation is in annotate.xpl.
  //@XPathFunction
  def findLegacyGridBindsAndTemplates(outerDocument: DocumentInfo): Seq[NodeInfo] = {

    val legacyGrids = findLegacyGridRepeats(outerDocument)
    val names       = legacyGrids map getControlName

    val binds       = names flatMap (findBindByName      (outerDocument, _))
    val templates   = names flatMap (findTemplateInstance(outerDocument, _))

    binds ++ templates
  }

  // Return a sequence of simple paths pointing to nodes to move into nested iterations
  //
  // Assumptions:
  //
  // - grids are not nested (they are not meant to be)
  // - section templates are not nested (they could be in the future)
  // - repeated grids can have repeat="true|content"
  //
  // For an example of JSON, see MigrationTest.
  //
  //@XPathFunction
  def buildGridMigrationMap(
    outerDocument        : DocumentInfo,
    availableXBLBindings : Option[DocumentInfo],
    legacyGridsOnly      : Boolean
  ): String = {

    def findGrids(doc: DocumentInfo) =
      if (legacyGridsOnly) findLegacyGridRepeats(doc) else findAllGridRepeats(doc)

    // Process section templates only if bindings are provided
    val (sectionsWithTemplates, xblBindingsByURIQualifiedName) = availableXBLBindings match {
      case Some(bindingsDocument) ⇒
        (
          findSectionsWithTemplates(findFRBodyElement(outerDocument)),
          FormBuilder.sectionTemplateXBLBindingsByURIQualifiedName(bindingsDocument.rootElement / XBLXBLTest)
        )
      case None ⇒
        (
          Nil,
          Map.empty[URIQualifiedName, DocumentInfo]
        )
    }

    def gridRepeatIterationName(grid: NodeInfo): String = {
      val controlName = getControlName(grid)
      if (isLegacyRepeat(grid))
        defaultIterationName(controlName)
      else
        findRepeatIterationName(grid, controlName).get
    }

    def pathsForBinding(doc: DocumentInfo): Seq[Migration] = {

      val names =
        findGrids(doc) map
        (grid ⇒ (getControlName(grid), gridRepeatIterationName(grid)))

      for {
        (gridName, iterationName) ← names
        (_, pathElems)            ← findBindAndPathStatically(doc, gridName)
      } yield
        Migration(pathElems, PathElem(iterationName))
    }

    def pathsForSectionTemplate(section: NodeInfo): Seq[Migration] = {

      val sectionName    = getControlNameOpt(section).get                // section must have a name
      val xblBindingName = sectionTemplateBindingName(section).get       // section must have a binding
      val xblBinding     = xblBindingsByURIQualifiedName(xblBindingName) // binding must be available

      // NOTE: Don't use findDataHolders. We don't want current holders, as there might be none if there is are
      // currently no iterations around a section template, for example. We must find this statically.
      val (_, holderPathElems) = findBindAndPathStatically(outerDocument, sectionName).head // bind must be found

      pathsForBinding(xblBinding.root) map {
        case Migration(pathElems, iterationElem) ⇒ Migration(holderPathElems ++ pathElems, iterationElem)
      }
    }

    pathsForBinding(outerDocument) ++ (sectionsWithTemplates flatMap pathsForSectionTemplate) match {
      case Nil        ⇒ ""
      case migrations ⇒ DataMigration.encodeMigrationsToJSON(migrations)
    }
  }
}
