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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import scala.Tuple3;

import java.util.Map;

/**
 * Represents an xforms:textarea control.
 */
public class XFormsTextareaControl extends XFormsValueControl {

    // List of attributes to handle as AVTs
    private static final QName[] EXTENSION_ATTRIBUTES = {
            XFormsConstants.XXFORMS_MAXLENGTH_QNAME,
            XFormsConstants.XXFORMS_COLS_QNAME,
            XFormsConstants.XXFORMS_ROWS_QNAME
    };

    public XFormsTextareaControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
    }

    @Override
    protected QName[] getExtensionAttributes() {
        return EXTENSION_ATTRIBUTES;
    }

    @Override
    public Tuple3<String, String, String> getJavaScriptInitialization() {
        final boolean hasInitialization = "text/html".equals(getMediatype()) || getAppearances().contains(XFormsConstants.XXFORMS_AUTOSIZE_APPEARANCE_QNAME);

        return hasInitialization ? getCommonJavaScriptInitialization() : null;
    }

    // NOTE: textarea doesn't support maxlength natively (this is added in HTML 5), but this can be implemented in JavaScript
    public String getMaxlength() {
        return getExtensionAttributeValue(XFormsConstants.XXFORMS_MAXLENGTH_QNAME);
    }

    /**
     * For textareas with mediatype="text/html", we first clean the HTML with TagSoup, and then transform it with
     * a stylesheet that removes all unknown or dangerous content.
     */
    @Override
    public void storeExternalValue(String value) {
        if ("text/html".equals(getMediatype())) {
            final IndentedLogger indentedLogger = containingDocument.getControls().getIndentedLogger();
            final boolean isDebugEnabled = indentedLogger.isDebugEnabled();
            if (isDebugEnabled)
                indentedLogger.startHandleOperation("xforms:textarea", "cleaning-up HTML", "value", value);

            // Do TagSoup and XSLT cleaning
            final Document tagSoupedDocument = XFormsUtils.htmlStringToDom4jTagSoup(value, null);
            if (isDebugEnabled)
                indentedLogger.logDebug("xforms:textarea", "after TagSoup cleanup", "value", Dom4jUtils.domToString(tagSoupedDocument));
            final Document cleanedDocument = XMLUtils.cleanXML(tagSoupedDocument, "oxf:/ops/xforms/clean-html.xsl");

            // Remove dummy tags (the dummy tags are added by the XSLT, as we need a root element for XSLT processing)
            value = Dom4jUtils.domToString(cleanedDocument);
            if ("<dummy-root/>".equals(value)) {
                value = "";                                                                 // Becomes empty
            } else {
                value = value.substring("<dummy-root>".length());                           // Remove start dummy tag
                value = value.substring(0, value.length() - "</dummy-root>".length());      // Remove end dummy tag
            }
            if (isDebugEnabled)
                indentedLogger.endHandleOperation("value", value);
        }
        super.storeExternalValue(value);
    }
}
