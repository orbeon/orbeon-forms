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

import org.orbeon.oxf.fr.DataMigration
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.xml.TransformerUtils.extractAsMutableDocument
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._
import FormBuilder._

object MigrationOps {

    private def findAllGridRepeats(doc: DocumentInfo) =
        findFRBodyElement(doc) descendant * filter IsGrid filter isRepeat

    private def findLegacyGridRepeats(doc: DocumentInfo) =
        findAllGridRepeats(doc) filter isLegacyRepeat

    // Used for migrating the form definition's legacy grid repeats
    // The rest of the implementation is in annotate.xpl.
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
    // The list of paths looks like:
    //
    // [
    //   {
    //     "path": "section-3/section-3-iteration/grid-4",
    //     "iteration-name": "grid-4-iteration"
    //   },
    //   {
    //     "path": "section-13/grid-6",
    //     "iteration-name": "grid-6-iteration"
    //   },
    //   {
    //     "path": "section-13/grid-14",
    //     "iteration-name": "grid-14-iteration"
    //   },
    //   {
    //     "path": "section-8/grid-3",
    //     "iteration-name": "my-custom-grid-3-iteration"
    //   },
    //   {
    //     "path": "section-23/grid-3",
    //     "iteration-name": "my-custom-grid-3-iteration"
    //   }
    // ]
    //
    def buildGridMigrationMap(
        outerDocument        : DocumentInfo,
        availableXBLBindings : Option[DocumentInfo],
        legacyGridsOnly      : Boolean
    ): String = {

        def findGrids(doc: DocumentInfo) =
            if (legacyGridsOnly) findLegacyGridRepeats(doc) else findAllGridRepeats(doc)

        // Process section templates only if bindings are provided
        val (sectionsWithTemplates, xblBindingsByURIQualifiedName) =
            if (availableXBLBindings.nonEmpty) {

                val sectionsWithTemplates =
                    findSectionsWithTemplates(findFRBodyElement(outerDocument))

                val xblBindingsByURIQualifiedName = {

                    val bindingsForSectionTemplates = availableXBLBindings.toList flatMap { bindings ⇒
                        availableSectionTemplateXBLBindings(bindings.rootElement / XBLXBLTest / XBLBindingTest)
                    }

                    // NOTE: Detach binding because downstream functions assume they can search for the document root.
                    bindingsForSectionTemplates map { binding ⇒
                        bindingFirstURIQualifiedName(binding) → extractAsMutableDocument(binding)
                    } toMap
                }

                (sectionsWithTemplates, xblBindingsByURIQualifiedName)
            } else
                (Nil, Map.empty[(String, String), DocumentInfo])

        def gridRepeatIterationName(grid: NodeInfo): String = {
            val controlName = getControlName(grid)
            if (isLegacyRepeat(grid))
                defaultIterationName(controlName)
            else
                findRepeatIterationName(grid, controlName).get
        }

        def pathsForBinding(doc: DocumentInfo): Seq[(String, String)] = {

            val names =
                findGrids(doc) map
                (grid ⇒ (getControlName(grid), gridRepeatIterationName(grid)))

            for {
                (gridName, iterationName) ← names
                (_, path)                 ← findDataHoldersPathStatically(doc, gridName)
            } yield
                (path, iterationName)
        }

        def pathsForSectionTemplate(section: NodeInfo): Seq[(String, String)] = {

            val sectionName    = getControlNameOpt(section).get                // section must have a name
            val xblBindingName = sectionTemplateBindingName(section).get       // section must have a binding
            val xblBinding     = xblBindingsByURIQualifiedName(xblBindingName) // binding must be available

            // NOTE: Don't use findDataHolders. We don't want current holders, as there might be none if there is are
            // currently no iterations around a section template, for example. We must find this statically.
            val (_, holderPath) = findDataHoldersPathStatically(outerDocument, sectionName).head // bind must be found

            pathsForBinding(xblBinding) map {
                case (path, iterationName) ⇒ (holderPath + '/' + path, iterationName)
            }
        }

        pathsForBinding(outerDocument) ++ (sectionsWithTemplates flatMap pathsForSectionTemplate) match {
            case Nil        ⇒ ""
            case migrations ⇒ DataMigration.encodeMigrationsToJSON(migrations)
        }
    }
}
