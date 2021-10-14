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
package org.orbeon.oxf.processor.generator;

import org.orbeon.dom.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.processor.*;
import org.xml.sax.ContentHandler;

import java.util.Iterator;

public class RequestSecurityGenerator extends ProcessorImpl {

    public static final String REQUEST_SECURITY_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/request-security";

    public RequestSecurityGenerator() {
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_CONFIG, REQUEST_SECURITY_NAMESPACE_URI));
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(RequestSecurityGenerator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                if (externalContext == null)
                    throw new OXFException("Missing external context object in RequestSecurityGenerator");
                Node config = readCacheInputAsOrbeonDom(context, INPUT_CONFIG);
                try {
                    xmlReceiver.startDocument();
                    String rootElementName = "request-security";
                    xmlReceiver.startElement("", rootElementName, rootElementName, SAXUtils.EMPTY_ATTRIBUTES);

                    ExternalContext.Request request = externalContext.getRequest();
                    addElement(xmlReceiver, "auth-type", request.getAuthType());
                    if (request.isSecure()) {
                        addElement(xmlReceiver, "secure", "true");
                    } else {
                        addElement(xmlReceiver, "secure", "false");
                    }
                    final String username = request.getUsername();
                    addElement(xmlReceiver, "remote-user", username);
                    addElement(xmlReceiver, "user-principal", username); // for backward compatibility only
                    // NOTE: We could output username, user-group, user-roles, but those are already present as headers.
                    for (Iterator i = XPathUtils.selectNodeIterator(config, "/config/role"); i.hasNext();) {
                        Node node = (Node) i.next();
                        String nodeString = XPathUtils.selectStringValueNormalize(node, ".");
                        if (request.isUserInRole(nodeString))
                            addElement(xmlReceiver, "role", nodeString);
                    }

                    xmlReceiver.endElement("", rootElementName, rootElementName);
                    xmlReceiver.endDocument();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    protected void addString(ContentHandler contentHandler, String string)
            throws Exception {
        char[] charArray = string.toCharArray();
        contentHandler.characters(charArray, 0, charArray.length);
    }

    protected void addElement(ContentHandler contentHandler, String name, String value)
            throws Exception {
        if (value != null) {
            contentHandler.startElement("", name, name, SAXUtils.EMPTY_ATTRIBUTES);
            addString(contentHandler, value);
            contentHandler.endElement("", name, name);
        }
    }
}
