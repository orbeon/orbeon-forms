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
import org.dom4j.Attribute;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.resources.OXFProperties;

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

    private Map nonDefaultProperties = new HashMap();
    private Map externalEventsMap;

    private String baseURI;
    private String containerType;
    private String containerNamespace;
    private LocationData locationData;

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

        final Element rootElement = staticStateDocument.getRootElement();

        // Remember UUID
        this.uuid = UUIDUtils.createPseudoUUID();

        // Get controls document
        controlsDocument = Dom4jUtils.createDocumentCopyParentNamespaces(rootElement.element("controls"));

        // Get models from static state
        final Element modelsElement = rootElement.element("models");

        // Get all models
        // FIXME: we don't get a System ID here. Is there a simple solution?
        for (Iterator i = modelsElement.elements().iterator(); i.hasNext();) {
            final Element modelElement = (Element) i.next();
            final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);

            modelDocuments.add(modelDocument);
        }

        // Scripts
        final Element scriptsElement = rootElement.element("scripts");
        if (scriptsElement != null) {
            xxformsScripts = new HashMap();
            for (Iterator i = scriptsElement.elements("script").iterator(); i.hasNext();) {
                final Element scriptElement = (Element) i.next();
                xxformsScripts.put(scriptElement.attributeValue("id"), scriptElement.getStringValue());
            }
        }

        // Handle properties
        for (Iterator i = rootElement.attributeIterator(); i.hasNext();) {
            final Attribute currentAttribute = (Attribute) i.next();
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(currentAttribute.getNamespaceURI()))
                nonDefaultProperties.put(currentAttribute.getName(), currentAttribute.getValue());
        }

        // Handle default for properties
        final OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
        for (Iterator i = XFormsProperties.SUPPORTED_DOCUMENT_PROPERTIES.entrySet().iterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final String propertyName = (String) currentEntry.getKey();
            final XFormsProperties.PropertyDefinition propertyDefinition = (XFormsProperties.PropertyDefinition) currentEntry.getValue();

            final String defaultPropertyValue = propertyDefinition.getDefaultValue().toString(); // value can be String, Boolean, Integer
            final String propertyValue = (String) nonDefaultProperties.get(propertyName);
            if (propertyValue == null) {
                // Property not defined in the document, try to obtain from global properties
                final String globalPropertyValue = propertySet.getObject(XFormsProperties.XFORMS_PROPERTY_PREFIX + propertyName, defaultPropertyValue).toString();

                // If the global property is different from the default, add it
                if (!globalPropertyValue.equals(defaultPropertyValue))
                    nonDefaultProperties.put(propertyName, globalPropertyValue);

            } else {
                // Property defined in the document

                // If the property is identical to the deault, remove it
                if (propertyValue.equals(defaultPropertyValue))
                    nonDefaultProperties.remove(propertyName);
            }
        }

        // Check validity of properties of known type
        {
            {
                final String stateHandling = getStringProperty(XFormsProperties.STATE_HANDLING_PROPERTY);
                if (!(stateHandling.equals(XFormsProperties.STATE_HANDLING_CLIENT_VALUE)
                                || stateHandling.equals(XFormsProperties.STATE_HANDLING_SESSION_VALUE)
                                || stateHandling.equals(XFormsProperties.STATE_HANDLING_SERVER_VALUE)))
                    throw new ValidationException("Invalid xxforms:" + XFormsProperties.STATE_HANDLING_PROPERTY + " attribute value: " + stateHandling, getLocationData());
            }

            {
                final String readonlyAppearance = getStringProperty(XFormsProperties.READONLY_APPEARANCE_PROPERTY);
                if (!(readonlyAppearance.equals(XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE)
                                || readonlyAppearance.equals(XFormsProperties.READONLY_APPEARANCE_DYNAMIC_VALUE)))
                    throw new ValidationException("Invalid xxforms:" + XFormsProperties.READONLY_APPEARANCE_PROPERTY + " attribute value: " + readonlyAppearance, getLocationData());
            }
        }

        final String externalEvents = getStringProperty(XFormsProperties.EXTERNAL_EVENTS_PROPERTY);
        if (externalEvents != null) {
            final StringTokenizer st = new StringTokenizer(externalEvents);
            while (st.hasMoreTokens()) {
                if (externalEventsMap == null)
                    externalEventsMap = new HashMap();
                externalEventsMap.put(st.nextToken(), "");
            }
        }

        baseURI = rootElement.attributeValue(XMLConstants.XML_BASE_QNAME);
        containerType = rootElement.attributeValue("container-type");
        containerNamespace = rootElement.attributeValue("container-namespace");
        if (containerNamespace == null)
            containerNamespace = "";

        {
            final String systemId = rootElement.attributeValue("system-id");
            if (systemId != null) {
                locationData = new LocationData(systemId, Integer.parseInt(rootElement.attributeValue("line")), Integer.parseInt(rootElement.attributeValue("column")));
            }
        }

        // Extract instances if present
        final Element instancesElement = rootElement.element("instances");
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
                    final boolean addContent = !currentInstance.isApplicationShared();
                    instancesElement.add(currentInstance.createContainerElement(addContent));
                }
            }

            // Remember encoded state and discard Document
            final boolean isStateHandlingClient = getStringProperty(XFormsProperties.STATE_HANDLING_PROPERTY).equals(XFormsProperties.STATE_HANDLING_CLIENT_VALUE);
            encodedStaticState = XFormsUtils.encodeXML(pipelineContext, staticStateDocument, isStateHandlingClient ? XFormsProperties.getXFormsPassword() : null, true);
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

    public Map getExternalEventsMap() {
        return externalEventsMap;
    }

    public String getContainerType() {
        return containerType;
    }

    public String getContainerNamespace() {
        return containerNamespace;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public Map getNonDefaultProperties() {
        return nonDefaultProperties;
    }

    public String getStringProperty(String propertyName) {
        final String documentProperty = (String) nonDefaultProperties.get(propertyName);
        if (documentProperty != null)
            return documentProperty;
        else
            return (String) ((XFormsProperties.PropertyDefinition) XFormsProperties.SUPPORTED_DOCUMENT_PROPERTIES.get(propertyName)).getDefaultValue();
    }

    public boolean getBooleanProperty(String propertyName) {
        final String documentProperty = (String) nonDefaultProperties.get(propertyName);
        if (documentProperty != null)
            return new Boolean(documentProperty).booleanValue();
        else
            return ((Boolean) ((XFormsProperties.PropertyDefinition) XFormsProperties.SUPPORTED_DOCUMENT_PROPERTIES.get(propertyName)).getDefaultValue()).booleanValue();
    }
}
