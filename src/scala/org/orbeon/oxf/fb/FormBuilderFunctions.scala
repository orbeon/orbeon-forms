/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
/**
 * Form Builder functions.
 *
 * For now everything is in this object, but that should be split out over time.
 */
object FormBuilderFunctions {

    // Get an id based on a name
    // NOTE: The idea as of 2011-06-21 is that we support reading indiscriminately the -control, -grid and -section
    // suffixes, whatever type of actual control they apply to. The idea is that in the end we might decide to just use
    // -control. OTOH we must have distinct ids for binds, controls and templates, so the -bind, -control and -template
    // suffixes must remain.
    def bindId(controlName: String) = controlName + "-bind"
    def sectionId(sectionName: String) = sectionName + "-section"
    def gridId(gridName: String) = gridName + "-grid"
    def controlId(controlName: String) = controlName + "-control"
    def templateId(gridName: String) = gridName + "-template"
    def tdId(tdName: String) = tdName + "-td"

    // Get the body
    def findBodyElement(doc: NodeInfo) = doc.getDocumentRoot \ * \ "*:body" head

    // Get the form model
    def findModelElement(doc: NodeInfo) = doc.getDocumentRoot \ * \ "*:head" \ "*:model" filter (hasId(_, "fr-form-model")) head

    // Find an xforms:instance element
    def instanceElement(doc: NodeInfo, id: String) =
        findModelElement(doc) \ "*:instance" filter (hasId(_, id)) headOption

    // Find an inline instance's root element
    def inlineInstanceRootElement(doc: NodeInfo, id: String) =
        instanceElement(doc, id).toSeq \ * headOption

    // Get the root element of instances
    def formInstanceRoot(doc: NodeInfo) = inlineInstanceRootElement(doc, "fr-form-instance").get
    def formResourcesRoot(doc: NodeInfo) = inlineInstanceRootElement(doc, "fr-form-resources").get
    def templateRoot(doc: NodeInfo, templateName: String) = inlineInstanceRootElement(doc, templateId(templateName))

    // Find the next available id for a given token
    def nextId(doc: NodeInfo, token: String) = nextIds(doc, token, 1).head

    // Find a series of next available ids for a given token
    def nextIds(doc: NodeInfo, token: String, count: Int) = {

        val prefix = token + "-"
        val suffix = "-" + token

        val instance = formInstanceRoot(doc)
        val root = doc.getDocumentRoot

        // Start with the number of element the given suffix
        val initialGuess = 1 + (root \\ * flatMap (e => attValueOption(e \@ "id")) filter (_.endsWith(suffix)) size)

        // Increment from guess, checking both full ids in the whole document as well as element names in the instance
        def findNext(guess: Int) = {
            var result = guess
            while ((root \\ * filter (hasId(_, prefix + result + suffix)) nonEmpty) ||
                    (instance \\ * filter (name(_) == prefix + result)))
                result += 1
            result
        }

        // Return count results, each new id feeding the next guess
        (1 to count - 1 scanLeft(findNext(initialGuess)))((previousId, _) => findNext(previousId + 1))
    }

    // Whether the current form has a custom instance
    def isCustomInstance = {
        val metadataInstance = asNodeInfo(model("fr-form-model").get.getVariable("metadata-instance"))
        (metadataInstance ne null) && metadataInstance \ "form-instance-mode" === "custom"
    }

    def makeInstanceExpression(name: String) = "instance('" + name + "')"

    def debugDumpDocument(message: String, inDoc: NodeInfo) {
//        println(message)
//        println(TransformerUtils.tinyTreeToString(inDoc.getDocumentRoot))
    }
}