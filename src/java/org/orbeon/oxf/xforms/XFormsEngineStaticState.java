/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.UUIDUtils;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * This class encapsulates containing document static state information.
 *
 * All the information contained here must be constant, and never change as the XForms engine operates on a page. This
 * information can be shared between multiple running copies of an XForms pages.
 */
public class XFormsEngineStaticState {

    private String uuid;
    private String encodedStaticState;

    private Document controlsDocument;
    private List modelDocuments = new ArrayList();

    private String baseURI;
    private String stateHandling;
    private String containerType;

    public XFormsEngineStaticState(PipelineContext pipelineContext, Document staticStateDocument) {

        uuid = UUIDUtils.createPseudoUUID();
        encodedStaticState = XFormsUtils.encodeXML(pipelineContext, staticStateDocument, XFormsUtils.getEncryptionKey());

        // Get controls document
        controlsDocument = Dom4jUtils.createDocumentCopyParentNamespaces(staticStateDocument.getRootElement().element("controls"));

        // Get models from static state
        final Element modelsElement = staticStateDocument.getRootElement().element("models");

        // Get all models
        // FIXME: we don't get a System ID here. Is there a simple solution?
        for (Iterator i = modelsElement.elements().iterator(); i.hasNext();) {
            final Element modelElement = (Element) i.next();
            final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);

            modelDocuments.add(modelDocument);
        }

        // Attributes
        final String stateHandlingAttribute = staticStateDocument.getRootElement().attributeValue(XFormsConstants.XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME);
        stateHandling = (stateHandlingAttribute != null)
                ? stateHandlingAttribute
                : XFormsUtils.isCacheSession() ? XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE : XFormsConstants.XXFORMS_STATE_HANDLING_CLIENT_VALUE;

        baseURI = staticStateDocument.getRootElement().attributeValue(XMLConstants.XML_BASE_QNAME);
        containerType = staticStateDocument.getRootElement().attributeValue("container-type");
    }

    public String getUUID() {
        return uuid;
    }

    public String getEncodedStaticState() {
        return encodedStaticState;
    }

    public Document getControlsDocument() {
        return controlsDocument;
    }

    public List getModelDocuments() {
        return modelDocuments;
    }

    public String getBaseURI() {
        return baseURI;
    }

    public String getStateHandling() {
        return stateHandling;
    }

    public String getContainerType() {
        return containerType;
    }
}
