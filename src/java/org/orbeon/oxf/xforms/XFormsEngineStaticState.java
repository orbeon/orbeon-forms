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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.*;

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
    private Map xxformsScripts;

    private String baseURI;
    private String stateHandling;
    private boolean readonly;
    private String readonlyAppearance;
    private String containerType;
    private String containerNamespace;

//    public XFormsEngineStaticState(PipelineContext pipelineContext, Document staticStateDocument, String uuid) {
    public XFormsEngineStaticState(PipelineContext pipelineContext, Document staticStateDocument) {

        // Remember UUID
//        this.uuid = uuid;
        this.uuid = UUIDUtils.createPseudoUUID();

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

        // Scripts
        final Element scriptsElement = staticStateDocument.getRootElement().element("scripts");
        if (scriptsElement != null) {
            xxformsScripts = new HashMap();
            for (Iterator i = scriptsElement.elements("script").iterator(); i.hasNext();) {
                final Element scriptElement = (Element) i.next();
                xxformsScripts.put(scriptElement.attributeValue("id"), scriptElement.getStringValue());
            }
        }

        // Attributes
        final String stateHandlingAttribute = staticStateDocument.getRootElement().attributeValue(XFormsConstants.XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME);
        stateHandling = (stateHandlingAttribute != null)
                ? stateHandlingAttribute
                : XFormsUtils.isCacheSession() ? XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE : XFormsConstants.XXFORMS_STATE_HANDLING_CLIENT_VALUE;

        final String readonlyAttribute = staticStateDocument.getRootElement().attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME);
        readonly = (readonlyAttribute != null) && new Boolean(readonlyAttribute).booleanValue() ;

        final String readonlyAppearanceAttribute = staticStateDocument.getRootElement().attributeValue(XFormsConstants.XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME);
        readonlyAppearance = (readonlyAppearanceAttribute != null)
                ? readonlyAppearanceAttribute
                : XFormsConstants.XXFORMS_READONLY_APPEARANCE_DYNAMIC_VALUE;

        baseURI = staticStateDocument.getRootElement().attributeValue(XMLConstants.XML_BASE_QNAME);
        containerType = staticStateDocument.getRootElement().attributeValue("container-type");
        containerNamespace = staticStateDocument.getRootElement().attributeValue("container-namespace");
        if (containerNamespace == null)
            containerNamespace = "";

        final boolean isStateHandlingSession = stateHandling.equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE);
        encodedStaticState = XFormsUtils.encodeXML(pipelineContext, staticStateDocument, isStateHandlingSession ? null : XFormsUtils.getEncryptionKey());
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

    public Map getScripts() {
        return xxformsScripts;
    }

    public String getBaseURI() {
        return baseURI;
    }

    public String getStateHandling() {
        return stateHandling;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public String getReadonlyAppearance() {
        return readonlyAppearance;
    }

    public String getContainerType() {
        return containerType;
    }

    public String getContainerNamespace() {
        return containerNamespace;
    }
}
