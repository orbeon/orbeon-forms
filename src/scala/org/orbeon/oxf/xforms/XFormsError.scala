/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import action.XFormsAPI._
import event.events.XXFormsBindingErrorEvent
import event.XFormsEventTarget
import model.DataModel.Reason
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.processor.xinclude.XIncludeProcessor
import collection.JavaConverters._
import org.orbeon.oxf.xml._
import dom4j.LocationData
import processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.saxon.om.NodeInfo
import org.apache.commons.lang.StringUtils
import org.orbeon.oxf.common.{ValidationException, OXFException}
import java.util.{List ⇒ JList}

object XFormsError {

    // Represent a non-fatal server XForms error
    case class ServerError(message: String, private val locationData: Option[LocationData], exceptionClass: Option[String] = None) {

        assert(message ne null)

        val file = locationData flatMap (l ⇒ Option(l.getSystemID))
        val line = locationData map (_.getLine) filter (_ >= 0)
        val col = locationData map (_.getCol) filter (_ >= 0)

        private val attributes = Seq("file", "line", "column", "exception")
        private val description = Seq("in", "line", "column", "cause")

        private def collectSeq(names: Seq[String]) = names zip
            Seq(file, line, col, exceptionClass) collect
                {case (k, Some(v)) ⇒ Seq(k, v.toString)} flatten

        def getDetailsAsArray = collectSeq(attributes) toArray

        // NOTE: A similar concatenation logic is in AjaxServer.js
        def getDetailsAsUserMessage = Seq(message) ++ collectSeq(description) mkString " "
    }

    object ServerError {
        def apply(t: Throwable): ServerError = {
            val root = OXFException.getRootThrowable(t)
            ServerError(StringUtils.trimToEmpty(root.getMessage), Option(ValidationException.getRootLocationData(t)), Some(root.getClass.getName))
        }
    }

    def handleNonFatalXPathError(containingDocument: XFormsContainingDocument, t: Throwable) {
        handleNonFatalXFormsError(containingDocument, "exception while evaluating XPath expression", t)
    }

    def logNonFatalXPathErrorAsDebug(containingDocument: XFormsContainingDocument, t: Throwable) {
        containingDocument.getIndentedLogger.logDebug("", "exception while evaluating XPath expression", t)
    }

    def handleNonFatalXFormsError(containingDocument: XFormsContainingDocument, message: String, t: Throwable) {
        if (containingDocument.isInitializing) {
            // The error is non fatal only upon XForms updates
            throw new OXFException(t)
        } else {
            // Log + add server error
            containingDocument.getIndentedLogger.logWarning("", message, t)
            containingDocument.addServerError(ServerError(t))
        }
    }

    def handleNonFatalSetvalueError(containingDocument: XFormsContainingDocument, locationData: LocationData, reason: Reason) {
        if (containingDocument.isInitializing) {
            // The error is non fatal only upon XForms updates
            throw new OXFException(reason.message)
        } else {
            // Log + add server error
            containingDocument.getIndentedLogger.logWarning("", reason.message)
            containingDocument.addServerError(ServerError(reason.message, Option(locationData)))
        }
    }

    // Output the Ajax error panel with a placeholder for errors
    def outputAjaxErrorPanel(containingDocument: XFormsContainingDocument, helper: ContentHandlerHelper, htmlPrefix: String) {
        helper.element("", XMLConstants.XINCLUDE_URI, "include", Array(
            "href", XHTMLBodyHandler.getIncludedResourceURL(containingDocument.getRequestPath, "error-dialog.xml"),
            "fixup-xml-base", "false"))
    }

    // Output the Noscript error panel and insert the errors
    def outputNoscriptErrorPanel(containingDocument: XFormsContainingDocument, helper: ContentHandlerHelper, htmlPrefix: String) {
        val errors = containingDocument.getServerErrors.asScala
        if (errors nonEmpty) {

            // Read the template
            val resourcePath = XHTMLBodyHandler.getIncludedResourcePath(containingDocument.getRequestPath, "error-dialog-noscript.xml")
            val template = new DocumentWrapper(ResourceManagerWrapper.instance().getContentAsDOM4J(resourcePath), null, XPathCache.getGlobalConfiguration)

            // Find insertion point and insert list of errors
            // NOTE: This is a poor man's template system. Ideally, we would use XForms or XSLT for this.
            template \\ * find (_.attClasses("xforms-error-panel-details")) foreach { div ⇒

                val ul: Seq[NodeInfo] =
                    <ul xmlns="http://www.w3.org/1999/xhtml">{
                        for (error ← errors)
                            yield <li>{error.getDetailsAsUserMessage}</li>
                    }</ul>

                insert(into = div, origin = ul)
            }

            // Write out result using XInclude semantics
            // NOTE: Parent namespace information is not passed here, and that is probably not right
            TransformerUtils.writeDom4j(unwrapElement(template.rootElement),
                new EmbeddedDocumentXMLReceiver(new XIncludeProcessor.XIncludeXMLReceiver(null, helper.getXmlReceiver, null, null)))
        }
    }

    // Insert server errors into the Ajax response
    def outputAjaxErrors(ch: ContentHandlerHelper, errors: JList[ServerError]) {
        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "errors")
        for (error ← errors.asScala) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "error", error.getDetailsAsArray)
            ch.text(error.message)
            ch.endElement()
        }
        ch.endElement()
    }
}