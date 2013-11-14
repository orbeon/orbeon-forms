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

import org.orbeon.errorified.Exceptions
import action.XFormsAPI._
import event.XFormsEventTarget
import model.DataModel.Reason
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.util.XPath
import collection.JavaConverters._
import org.orbeon.oxf.xml._
import dom4j.LocationData
import processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.saxon.om.NodeInfo
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.common.{OrbeonLocationException, OXFException}
import java.util.{List ⇒ JList}
import xbl.{Scope, XBLContainer}

object XFormsError {

    // Represent a non-fatal server XForms error
    case class ServerError(message: String, private val locationData: Option[LocationData], exceptionClass: Option[String] = None) {

        assert(message ne null)

        val file = locationData flatMap (l ⇒ Option(l.getSystemID))
        val line = locationData map     (_.getLine) filter (_ >= 0)
        val col  = locationData map     (_.getCol)  filter (_ >= 0)

        private val attributes  = Seq("file", "line", "column", "exception")
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
            val root = Exceptions.getRootThrowable(t)
            ServerError(StringUtils.trimToEmpty(root.getMessage), OrbeonLocationException.getRootLocationData(t), Some(root.getClass.getName))
        }
    }

    def handleNonFatalXPathError(container: XBLContainer, t: Throwable): Unit =
        handleNonFatalXFormsError(container, "exception while evaluating XPath expression", t)

    def logNonFatalXPathErrorAsDebug(container: XBLContainer, scope: Scope, t: Throwable): Unit =
        container.getContainingDocument.indentedLogger.logDebug("", "exception while evaluating XPath expression", t)

    def handleNonFatalActionError(target: XFormsEventTarget, t: Throwable): Unit =
        handleNonFatalXFormsError(target.container, "exception while running action", t)

    private def handleNonFatalXFormsError(container: XBLContainer, message: String, t: Throwable): Unit = {

        def log() = {
            // Log + add server error
            val containingDocument = container.getContainingDocument
            containingDocument.indentedLogger.logWarning("", message, t)
            containingDocument.addServerError(ServerError(t))
        }

        fatalOrNot(container, throw new OXFException(t), log())
    }

    def handleNonFatalSetvalueError(target: XFormsEventTarget, locationData: LocationData, reason: Reason): Unit = {

        def log() = {
            // Log + add server error
            val containingDocument = target.container.getContainingDocument
            containingDocument.indentedLogger.logWarning("", reason.message)
            containingDocument.addServerError(ServerError(reason.message, Option(locationData)))
        }

        fatalOrNot(target.container, throw new OXFException(reason.message), log())
    }

    // The error is non fatal only upon XForms updates OR for nested parts
    private def fatalOrNot(container: XBLContainer, fatal: ⇒ Any, nonFatal: ⇒ Any): Unit =
        if (container.getPartAnalysis.isTopLevel && container.getContainingDocument.isInitializing)
            fatal
        else
            nonFatal

    // Output the Ajax error panel with a placeholder for errors
    def outputAjaxErrorPanel(containingDocument: XFormsContainingDocument, helper: XMLReceiverHelper, htmlPrefix: String): Unit =
        helper.element("", XMLConstants.XINCLUDE_URI, "include", Array(
            "href", XHTMLBodyHandler.getIncludedResourceURL(containingDocument.getRequestPath, "error-dialog.xml"),
            "fixup-xml-base", "false"))

    // Output the Noscript error panel and insert the errors
    def outputNoscriptErrorPanel(containingDocument: XFormsContainingDocument, helper: XMLReceiverHelper, htmlPrefix: String): Unit = {
        val errors = containingDocument.getServerErrors.asScala
        if (errors nonEmpty) {

            // Read the template
            val resourcePath = XHTMLBodyHandler.getIncludedResourcePath(containingDocument.getRequestPath, "error-dialog-noscript.xml")
            val template = new DocumentWrapper(ResourceManagerWrapper.instance().getContentAsDOM4J(resourcePath), null, XPath.GlobalConfiguration)

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
                new EmbeddedDocumentXMLReceiver(new XIncludeReceiver(null, helper.getXmlReceiver, null, null)))
        }
    }

    // Insert server errors into the Ajax response
    def outputAjaxErrors(ch: XMLReceiverHelper, errors: JList[ServerError]): Unit = {
        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "errors")
        for (error ← errors.asScala) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "error", error.getDetailsAsArray)
            ch.text(error.message)
            ch.endElement()
        }
        ch.endElement()
    }
}