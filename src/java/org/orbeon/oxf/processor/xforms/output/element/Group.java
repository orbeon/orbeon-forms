/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xforms.output.element;

import org.dom4j.*;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsElementContext;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class Group extends XFormsElement {

    private boolean isFirstGroup;

    public void start(XFormsElementContext context, String uri, String localname, String qname, Attributes attributes) throws SAXException {
        isFirstGroup = context.getParentElement(0) == null;
        super.start(context, uri, localname, qname, attributes);
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        if (isFirstGroup) {

            // Go through the instance and wipe out the nodes bound to a control (generated is true)
            Document instance = context.getCurrentInstance().getDocument();
            instance.accept(new VisitorSupport() {
                public void visit(Attribute node) {
                    wipeValue(node);
                }

                public void visit(Element node) {
                    wipeValue(node);
                }

                private void wipeValue(Node node) {
                    InstanceData data;
                    if(node instanceof Element)
                        data = (InstanceData)((Element)node).getData();
                    else
                        data = (InstanceData)((Attribute)node).getData();

                    if(data != null && data.isGenerated())
                        node.setText("");
                }
            });

            // Encode instance in a string and put in hidden field

            XFormsUtils.removeInstanceAttributes(instance);
            String instanceString = XFormsUtils.encodeXML(context.getPipelineContext(), instance);
            sendHiddenElement(context, "$instance", instanceString);
        }

        // Close form
        super.end(context, uri, localname, qname);
    }

    private void sendHiddenElement(XFormsElementContext context, String name, String value) throws SAXException {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "name",
                XFormsConstants.XXFORMS_PREFIX + ":name", "CDATA", name);
        attributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "value",
                XFormsConstants.XXFORMS_PREFIX + ":value", "CDATA", value);
        String elementLocalName = "hidden";
        String elementQName = XFormsConstants.XXFORMS_PREFIX + ":" + elementLocalName;
        context.getContentHandler().startElement(XFormsConstants.XXFORMS_NAMESPACE_URI,
                elementLocalName, elementQName, attributes);
        context.getContentHandler().endElement(XFormsConstants.XXFORMS_NAMESPACE_URI,
                elementLocalName, elementQName);
    }
}
