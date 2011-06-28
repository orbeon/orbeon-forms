/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

public class XXFormsLang extends XFormsFunction {
    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final String elementId = (argument.length > 0) ? argument[0].evaluateAsString(xpathContext).toString() : null;

        final XBLContainer container = getXBLContainer(xpathContext);

        final Element element;
        if (elementId == null) {
            // Get element on which the expression is used
            element = getSourceElement(xpathContext);
        } else {
            // Do a bit more work to find current scope first
            final XBLBindingsBase.Scope scope = container.getPartAnalysis().getResolutionScopeByPrefixedId(XFormsUtils.getPrefixedId(getSourceEffectiveId(xpathContext)));
            final String elementPrefixedId = scope.getPrefixedIdForStaticId(elementId);

            element = container.getPartAnalysis().getControlElement(elementPrefixedId);
        }

        final String lang = resolveXMLangHandleAVTs(getXBLContainer(xpathContext), element);

        return (lang == null) ? null : StringValue.makeStringValue(lang);
    }

    /**
     * Return an element's static xml:lang value, checking ancestors as well.
     *
     * @param element   element to check
     * @return          xml:lang value or null if not found
     */
    private static String resolveXMLang(Element element) {
        // Allow for null Element
        if (element == null)
            return null;

        // Collect xml:lang values
        Element currentElement = element;
        do {
            final String xmlLangAttribute = currentElement.attributeValue(XMLConstants.XML_LANG_QNAME);
            if (xmlLangAttribute != null)
                return xmlLangAttribute;
            currentElement = currentElement.getParent();
        } while(currentElement != null);

        // Not found
        return null;
    }

    /**
     * Resolve an element's dynamic xml:lang value. This resolves top-level AVTs and goes over XBL container boundaries.
     *
     * @param xblContainer      current XBL container
     * @param element           element within XBL container
     * @return                  xml:lang value, or null if not found
     */
    public static String resolveXMLangHandleAVTs(XBLContainer xblContainer, Element element) {

        // Resolve static value
        final String xmlLang = resolveXMLang(element);

        if (xmlLang == null && xblContainer.getParentXBLContainer() == null) {
            // We are at the top-level and nothing was found
            return null;
        } else if (xmlLang == null) {
            // We are not at the top-level, so try parent container
            return resolveXMLangHandleAVTs(xblContainer.getParentXBLContainer(),
                    xblContainer.getContainingDocument().getStaticOps().getControlElement(xblContainer.getPrefixedId()));
        } else if (!xmlLang.startsWith("#")) {
            // Found static value
            return xmlLang;
        } else {
            // If this starts with "#", this is a reference to a control (set in XFormsExtractorContentHandler)
            // NOTE: For now, this is a control's static id and works only for top-level AVTs

            final XFormsContainingDocument containingDocument = xblContainer.getContainingDocument();

            final String attributeControlStaticId; {
                final AttributeControl controlAnalysis = containingDocument.getStaticOps().getAttributeControl(xmlLang.substring(1), "xml:lang");
                attributeControlStaticId = controlAnalysis.element().attributeValue(XFormsConstants.ID_QNAME);
            }

            final XXFormsAttributeControl attributeControl = (XXFormsAttributeControl) containingDocument.getControls().getObjectByEffectiveId(attributeControlStaticId);
            return attributeControl.getExternalValue();
        }
    }
}
