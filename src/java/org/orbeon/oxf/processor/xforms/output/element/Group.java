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

import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.Document;
import org.dom4j.VisitorSupport;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Node;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

public class Group extends XFormsElement {

    private boolean isFirstGroup;

    public void start(XFormsElementContext context, String uri, String localname, String qname, Attributes attributes) throws SAXException {
        isFirstGroup = context.getParentElement(0) == null;
        super.start(context, uri, localname, qname, attributes);
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        if (isFirstGroup) {

            // Go through the instance and wipe out the nodes bound to a control (generated is true)
            Document instance = context.getInstance();
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
            String instanceString = XFormsUtils.instanceToString(context.getPipelineContext(),
                    context.getEncryptionPassword(), instance);
            sendHiddenElement(context, "$instance", instanceString);

            // Generate hidden field with random key encrypted with server key
            if (XFormsUtils.isHiddenEncryptionEnabled() || XFormsUtils.isNameEncryptionEnabled()) {
                String serverPassword = OXFProperties.instance().getPropertySet().getString(Constants.XFORMS_PASSWORD);
                String encryptedRandomKey = SecureUtils.encrypt(context.getPipelineContext(),
                        serverPassword, context.getEncryptionPassword());
                sendHiddenElement(context, "$key", encryptedRandomKey);
            }
        }

        // Close form
        super.end(context, uri, localname, qname);
    }

    private void sendHiddenElement(XFormsElementContext context, String name, String value) throws SAXException {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(Constants.XXFORMS_NAMESPACE_URI, "name",
                Constants.XXFORMS_PREFIX + ":name", "CDATA", name);
        attributes.addAttribute(Constants.XXFORMS_NAMESPACE_URI, "value",
                Constants.XXFORMS_PREFIX + ":value", "CDATA", value);
        String elementLocalName = "hidden";
        String elementQName = Constants.XXFORMS_PREFIX + ":" + elementLocalName;
        context.getContentHandler().startElement(Constants.XXFORMS_NAMESPACE_URI,
                elementLocalName, elementQName, attributes);
        context.getContentHandler().endElement(Constants.XXFORMS_NAMESPACE_URI,
                elementLocalName, elementQName);
    }
}
