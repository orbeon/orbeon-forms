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
 *
 * The static state may contain constant shared XForms instances. These may be used as:
 *
 * o read-only instances that don't need to be in the dynamic state
 * o initial instances needed for xforms:reset
 * o initial instances needed for back/reload
 *
 * Instances in the static state may not be initialized yet (i.e. not have the actual instance document include) in case
 * they are shared instances.
 *
 * NOTE: This code will have to change a bit if we move towards TinyTree to store the static state.
 */
public class XFormsStaticState {

    private boolean initialized;

    private String uuid;
    private String encodedStaticState;
    private Document staticStateDocument;

    private Document controlsDocument;
    private List modelDocuments = new ArrayList();
    private Map xxformsScripts;

    private Map instancesMap;

    private String baseURI;
    private String stateHandling;
    private boolean readonly;
    private String readonlyAppearance;
    private Map externalEventsMap;
    private String containerType;
    private String containerNamespace;

    /**
     * Create static state object from an encoded version.
     *
     * @param pipelineContext       current PipelineContext
     * @param encodedStaticState    encoded static state
     */
    public XFormsStaticState(PipelineContext pipelineContext, String encodedStaticState) {
        this(XFormsUtils.decodeXML(pipelineContext, encodedStaticState), encodedStaticState);
    }

    /**
     * Create static state object from a Document.
     *
     * @param staticStateDocument   Document containing the static state
     */
    public XFormsStaticState(Document staticStateDocument) {
        this(staticStateDocument, null);
    }

//    public XFormsEngineStaticState(PipelineContext pipelineContext, Document staticStateDocument, String uuid) {
//        this.uuid = uuid;

    private XFormsStaticState(Document staticStateDocument, String encodedStaticState) {

        // Remember UUID
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

        final String externalEventsAttribute = staticStateDocument.getRootElement().attributeValue(XFormsConstants.XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME);
        if (externalEventsAttribute != null) {
            final StringTokenizer st = new StringTokenizer(externalEventsAttribute);
            while (st.hasMoreTokens()) {
                if (externalEventsMap == null)
                    externalEventsMap = new HashMap();
                externalEventsMap.put(st.nextToken(), "");
            }
        }

        baseURI = staticStateDocument.getRootElement().attributeValue(XMLConstants.XML_BASE_QNAME);
        containerType = staticStateDocument.getRootElement().attributeValue("container-type");
        containerNamespace = staticStateDocument.getRootElement().attributeValue("container-namespace");
        if (containerNamespace == null)
            containerNamespace = "";

        // Extract instances if present
        final Element instancesElement = staticStateDocument.getRootElement().element("instances");
        if (instancesElement != null) {
            instancesMap = new HashMap();

            for (Iterator instanceIterator = instancesElement.elements("instance").iterator(); instanceIterator.hasNext();) {
                final Element currentInstanceElement = (Element) instanceIterator.next();
                final XFormsInstance newInstance = new SharedXFormsInstance(currentInstanceElement);
                instancesMap.put(newInstance.getEffectiveId(), newInstance);
            }
        }

        if (encodedStaticState != null) {
            // Static state is fully initialized
            this.encodedStaticState = encodedStaticState;
            initialized = true;
        } else {
            // Remember this temporarily only if the encoded state is not yet known
            this.staticStateDocument = staticStateDocument;
            initialized = false;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void addInstance(SharedXFormsInstance instance) {
        if (initialized)
            throw new IllegalStateException("Cannot add instances to static state after initialization.");

        if (instancesMap == null)
            instancesMap = new HashMap();

        instancesMap.put(instance.getEffectiveId(), instance);
    }

    public String getUUID() {
        return uuid;
    }

    public String getEncodedStaticState(PipelineContext pipelineContext) {

        if (!initialized) {

            if (staticStateDocument.getRootElement().element("instances") != null)
                throw new IllegalStateException("Element instances already present in static state.");

            // Add instances to Document if needed
            if (instancesMap != null && instancesMap.size() > 0) {
                final Element instancesElement = staticStateDocument.getRootElement().addElement("instances");
                for (Iterator instancesIterator = instancesMap.values().iterator(); instancesIterator.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) instancesIterator.next();

                    // Add information for all shared instances, but don't add content for globally shared instances
                    // NOTE: This strategy could be changed in the future or be configurable
                    final boolean addContent = !currentInstance.isShared();
                    instancesElement.add(currentInstance.createContainerElement(addContent));
                }
            }

            // Serialize Document
            final boolean isStateHandlingSession = stateHandling.equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE);

            // Remember encoded state an discard Document
            encodedStaticState = XFormsUtils.encodeXML(pipelineContext, staticStateDocument, isStateHandlingSession ? null : XFormsUtils.getEncryptionKey());
            staticStateDocument = null;
            initialized = true;
        }

        return encodedStaticState;
    }

    public Map getInstancesMap() {
        if (!initialized)
            throw new IllegalStateException("Cannot get instances from static before initialization.");

        return instancesMap;
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

    public Map getExternalEventsMap() {
        return externalEventsMap;
    }

    public String getContainerType() {
        return containerType;
    }

    public String getContainerNamespace() {
        return containerNamespace;
    }
}
