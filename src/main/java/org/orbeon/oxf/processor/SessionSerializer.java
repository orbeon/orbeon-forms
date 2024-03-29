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
package org.orbeon.oxf.processor;

import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom.LocationSAXWriter;

public class SessionSerializer extends ProcessorImpl {

    public SessionSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        try {
            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

            SAXStore store = new SAXStore();
            Document document = readCacheInputAsOrbeonDom(context, INPUT_DATA);
            Element value = document.getRootElement();
            if (value == null)
                throw new OXFException("Session serializer input data is null");

            String namespaceURI = value.getNamespaceURI();
            String localName = value.getName();
            String key = (namespaceURI.equals("")) ? localName : "{" + namespaceURI + "}" + localName;

            LocationSAXWriter saxw = new LocationSAXWriter();
            saxw.setContentHandler(store);
            saxw.write(document);

            // Store the document into the session
            externalContext.getSession(true).javaSetAttribute(key, store);

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
