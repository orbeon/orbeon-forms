/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import org.dom4j.{Document, Element}
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils.htmlStringToDom4jTagSoup
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils.domToString

/**
 * xf:textarea control
 */
class XFormsTextareaControl(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
        extends XFormsSingleNodeControl(container, parent, element, id)
        with XFormsValueControl
        with FocusableTrait
        with Logging {

    override def getJavaScriptInitialization = {
        val hasInitialization = XFormsControl.isHTMLMediatype(this) || staticControl.appearances(XXFORMS_AUTOSIZE_APPEARANCE_QNAME)
        if (hasInitialization) getCommonJavaScriptInitialization else null
    }

    /**
     * For textareas with mediatype="text/html", we first clean the HTML with TagSoup, and then transform it with
     * a stylesheet that removes all unknown or dangerous content.
     */
    override def translateExternalValue(externalValue: String): String = {

        def sanitizeForMediatype(s: String) =
            if (XFormsControl.isHTMLMediatype(this)) {
                implicit val logger = containingDocument.getControls.getIndentedLogger

                withDebug("cleaning-up xf:textarea HTML", Seq("value" → s)) {

                    def cleanWithTagSoup(s: String) = {
                        val result = htmlStringToDom4jTagSoup(s, null)
                        debug("after TagSoup xf:textarea cleanup", Seq("value" → domToString(result)))
                        result
                    }

                    def cleanWithXSLT(document: Document) = {
                        val cleanedDocument = XMLUtils.cleanXML(document, "oxf:/ops/xforms/clean-html.xsl")

                        // Remove dummy tag are added by the XSLT
                        domToString(cleanedDocument) match {
                            case "<dummy-root/>" ⇒ ""
                            case value ⇒
                                val tagLength = "<dummy-root>".size
                                value.substring(tagLength, value.length - tagLength - 1)
                        }
                    }

                    // Do TagSoup and XSLT cleaning
                    val result = cleanWithXSLT(cleanWithTagSoup(s))
                    debug("after XSLT xf:textarea cleanup", Seq("value" → result))
                    result
                }
            } else
                s


        // Replacement-based input sanitation
        containingDocument.getStaticState.sanitizeInput(sanitizeForMediatype(externalValue))
    }
}