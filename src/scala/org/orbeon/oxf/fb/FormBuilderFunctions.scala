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
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.xforms.XFormsConstants.{XFORMS_NAMESPACE_URI, XBL_NAMESPACE_URI}
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI, XSD_URI}
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.util.{UserAgent, NetUtils}
import org.orbeon.oxf.xforms.{XFormsModel, XFormsProperties}

/**
 * Form Builder functions.
 */
object FormBuilderFunctions extends Logging {

    implicit def logger = containingDocument.getIndentedLogger("form-builder")

    val XH = XHTML_NAMESPACE_URI
    val XF = XFORMS_NAMESPACE_URI
    val XS = XSD_URI
    val XBL = XBL_NAMESPACE_URI
    val FR = FormRunner.NS
    val FB = "http://orbeon.org/oxf/xml/form-builder"

    // Id of the xxf:dynamic control holding the edited form
    val DynamicControlId = "fb"

    // Get an id based on a name
    // NOTE: The idea as of 2011-06-21 is that we support reading indiscriminately the -control, -grid
    // suffixes, whatever type of actual control they apply to. The idea is that in the end we might decide to just use
    // -control. OTOH we must have distinct ids for binds, controls and templates, so the -bind, -control and -template
    // suffixes must remain.
    def bindId(controlName: String) = controlName + "-bind"
    def gridId(gridName: String) = gridName + "-grid"
    def controlId(controlName: String) = controlName + "-control"
    def templateId(gridName: String) = gridName + "-template"

    // Find the form document being edited
    def getFormDoc = asNodeInfo(model("fr-form-model").get.getVariable("model")).getDocumentRoot

    // Find the top-level form model of the form being edited
    def getFormModel = containingDocument.getObjectByEffectiveId(DynamicControlId + "$fr-form-model").asInstanceOf[XFormsModel] ensuring (_ ne null, "did not find fb$fr-form-model")

    // Get the body
    // NOTE: annotate.xpl replaces fr:body with xf:group[@class = 'fb-body']
    def findFRBodyElement(inDoc: NodeInfo) = inDoc.getDocumentRoot \ * \ "*:body" \\ (XF → "group") filter (_.attClasses("fb-body")) head

    // Get the form model
    def findModelElement(inDoc: NodeInfo) = inDoc.getDocumentRoot \ * \ "*:head" \ "*:model" filter (hasIdValue(_, "fr-form-model")) head

    // Find an xforms:instance element
    def instanceElement(inDoc: NodeInfo, id: String) =
        findModelElement(inDoc) \ "*:instance" filter (hasIdValue(_, id)) headOption

    // Find an inline instance's root element
    def inlineInstanceRootElement(inDoc: NodeInfo, id: String) =
        instanceElement(inDoc, id).toSeq \ * headOption

    // Get the root element of instances
    def formInstanceRoot(inDoc: NodeInfo) = inlineInstanceRootElement(inDoc, "fr-form-instance").get
    def metadataInstanceRoot(inDoc: NodeInfo) = inlineInstanceRootElement(inDoc, "fr-form-metadata").get
    def resourcesInstanceRoot(inDoc: NodeInfo) = inlineInstanceRootElement(inDoc, "fr-form-resources").get

    def formResourcesRoot = asNodeInfo(model("fr-form-model").get.getVariable("resources"))
    def templateRoot(inDoc: NodeInfo, templateName: String) = inlineInstanceRootElement(inDoc, templateId(templateName))

    // Find the next available id for a given token
    def nextId(inDoc: NodeInfo, token: String, useInstance: Boolean = true) =
        nextIds(inDoc, token, 1).head

    // Find a series of next available ids for a given token
    // Return ids of the form "foo-123-foo", where "foo" is the token
    def nextIds(inDoc: NodeInfo, token: String, count: Int, useInstance: Boolean = true) = {

        val prefix = token + "-"
        val suffix = "-" + token

        def findAllIds = {
            val root = inDoc.getDocumentRoot

            val instanceIds = if (useInstance) formInstanceRoot(root) \\ * map (localname(_) + suffix) else Seq()
            val elementIds = root \\ * \@ "id" map (_.stringValue) filter (_.endsWith(suffix))

            instanceIds ++ elementIds
        }

        val allIds = collection.mutable.Set() ++ findAllIds
        var guess = allIds.size + 1

        def nextId = {
            def buildId(i: Int) = prefix + i + suffix

            while (allIds(buildId(guess)))
                guess += 1

            val result = buildId(guess)
            allIds += result
            result
        }

        for (_ ← 1 to count)
            yield nextId
    }

    def makeInstanceExpression(name: String) = "instance('" + name + "')"

    // Minimal version of IE supported
    val minimalIEVersion = 8

    // Whether the browser is supported
    // Concretely, we only return false if the browser is an "old" version of IE
    def isBrowserSupported = {
        val request = NetUtils.getExternalContext.getRequest
        ! UserAgent.isUserAgentIE(request) || UserAgent.getMSIEVersion(request) >= minimalIEVersion
    }

    def debugDumpDocument(message: String, inDoc: NodeInfo) =
        if (XFormsProperties.getDebugLogging.contains("form-builder-grid"))
            debug(message, Seq("doc" → TransformerUtils.tinyTreeToString(inDoc.getDocumentRoot)))
}