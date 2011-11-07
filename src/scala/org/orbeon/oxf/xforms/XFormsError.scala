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
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.processor.xinclude.XIncludeProcessor
import collection.JavaConverters._
import org.orbeon.oxf.xml._
import processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.saxon.om.NodeInfo

object XFormsError {

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
            containingDocument.getIndentedLogger.logWarning("", message, t)
            containingDocument.addServerError(new XFormsContainingDocument.ServerError(t))
        }
    }

    def outputAjaxErrorPanel(containingDocument: XFormsContainingDocument, helper: ContentHandlerHelper, htmlPrefix: String) {
        helper.element("", XMLConstants.XINCLUDE_URI, "include", Array(
            "href", XHTMLBodyHandler.getIncludedResourceURL(containingDocument.getRequestPath, "error-dialog.xml"),
            "fixup-xml-base", "false"))
    }

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
                            yield <li>{error.message}</li>
                    }</ul>

                insert(into = div, origin = ul)
            }

            // Write out result using XInclude semantics
            // NOTE: Parent namespace information is not passed here, and that is probably not right
            TransformerUtils.writeDom4j(unwrapElement(template.rootElement),
                new EmbeddedDocumentXMLReceiver(new XIncludeProcessor.XIncludeXMLReceiver(null, helper.getXmlReceiver, null, null)))
        }
    }
}