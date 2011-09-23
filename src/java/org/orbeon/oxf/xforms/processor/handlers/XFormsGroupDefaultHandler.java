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
package org.orbeon.oxf.xforms.processor.handlers;

import org.dom4j.QName;
import org.orbeon.oxf.xforms.analysis.controls.ContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Default group handler.
 */
public class XFormsGroupDefaultHandler extends XFormsGroupHandler {

    private String elementName;
    private String elementQName;

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        super.init(uri, localname, qName, attributes);

        // Use explicit container element name if present, otherwise use default
        final QName explicitQName = ((ContainerControl) containingDocument.getStaticOps().getControlAnalysis(getPrefixedId())).elementQName();
        if (explicitQName != null) {
            elementName = explicitQName.getName();
            elementQName = explicitQName.getQualifiedName();
        } else {
            elementName = super.getContainingElementName();
            elementQName = super.getContainingElementQName();// NOTE: this calls back getContainingElementName()
        }
    }

    @Override
    protected String getContainingElementName() {
        return elementName;
    }

    @Override
    protected String getContainingElementQName() {
        return elementQName;
    }

    public void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, final String effectiveId, XFormsControl control) throws SAXException {
        if (!handlerContext.isSpanHTMLLayout()) {
            // Open containing element
            final ElementHandlerController controller = handlerContext.getController();
            controller.getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName(),
                    getContainerAttributes(uri, localname, attributes, effectiveId, control, false));
        }
    }

    @Override
    public void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        if (!handlerContext.isSpanHTMLLayout()) {
            // Close containing element
            final ElementHandlerController controller = handlerContext.getController();
            controller.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName());
        }
    }

    @Override
    protected void handleLabel() throws SAXException {
        // TODO: check why we output our own label here

        final XFormsSingleNodeControl groupControl = (XFormsSingleNodeControl) getControl();
        final String effectiveId = getEffectiveId();

        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, getLabelClasses(groupControl));
        outputLabelFor(handlerContext, reusableAttributes, effectiveId, effectiveId, LHHAC.LABEL, handlerContext.getLabelElementName(),
                getLabelValue(groupControl), groupControl != null && groupControl.isHTMLLabel(), !handlerContext.isSpanHTMLLayout());
    }
}
