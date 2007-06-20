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
package org.orbeon.oxf.processor.generator;

import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;

import java.util.Iterator;
import java.security.Principal;

public class RequestSecurityGenerator extends ProcessorImpl {

    public static final String REQUEST_SECURITY_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/request-security";

    public RequestSecurityGenerator() {
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_CONFIG, REQUEST_SECURITY_NAMESPACE_URI));
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                if (externalContext == null)
                    throw new OXFException("Missing external context object in RequestSecurityGenerator");
                Node config = readCacheInputAsDOM4J(context, INPUT_CONFIG);
                try {
                    contentHandler.startDocument();
                    String rootElementName = "request-security";
                    contentHandler.startElement("", rootElementName, rootElementName, XMLUtils.EMPTY_ATTRIBUTES);

                    ExternalContext.Request request = externalContext.getRequest();
                    addElement(contentHandler, "auth-type", request.getAuthType());
                    if (request.isSecure()) {
                        addElement(contentHandler, "secure", "true");
                    } else {
                        addElement(contentHandler, "secure", "false");
                    }
                    addElement(contentHandler, "remote-user", request.getRemoteUser());
                    {
                        final Principal principal = request.getUserPrincipal();
                        if (principal != null) {
                            addElement(contentHandler, "user-principal", principal.getName());
                        }
                    }

                    for (Iterator i = XPathUtils.selectIterator(config, "/config/role"); i.hasNext();) {
                        Node node = (Node) i.next();
                        String nodeString = XPathUtils.selectStringValueNormalize(node, ".");
                        if (request.isUserInRole(nodeString))
                            addElement(contentHandler, "role", nodeString);
                    }

                    contentHandler.endElement("", rootElementName, rootElementName);
                    contentHandler.endDocument();
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
            contentHandler.startElement("", name, name, XMLUtils.EMPTY_ATTRIBUTES);
            addString(contentHandler, value);
            contentHandler.endElement("", name, name);
        }
    }
}
