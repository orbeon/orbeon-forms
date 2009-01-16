/**
 *  Copyright (C) 2006-2008 Orbeon, Inc.
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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.processor.XFormsDocumentAnnotatorContentHandler;
import org.orbeon.oxf.xforms.xbl.XBLUtils;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import java.util.*;

/**
 * This class encapsulates containing document static state information.
 *
 * All the information contained here must be constant, and never change as the XForms engine operates on a page. This
 * information can be shared between multiple running copies of an XForms pages.
 *
 * The only exception to the above is during initialization, between the time initialized == false and initialized ==
 * true, where instances can be added.
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
    private String encodedStaticState;      // encoded state
    private Document staticStateDocument;   // if present, stored there temporarily only until getEncodedStaticState() is called and encodedStaticState is produced

    private Document controlsDocument;                  // controls cocument
    private LinkedHashMap modelDocuments = new LinkedHashMap();// Map<String modelPrefixedId, Document modelDocument>
    private SAXStore xhtmlDocument;                     // entire XHTML document for noscript mode only

    private Map xxformsScripts;                         // Map<String, String> of id to script content

    private Map instancesMap;                           // Map<String, SharedXFormsInstance> of id to shared instance

    private Map nonDefaultProperties = new HashMap();   // Map<String, Object> of property name to property value (String, Integer, Boolean)
    private Map externalEventsMap;                      // Map<String, ""> of event names

    private String baseURI;
    private String containerType;
    private String containerNamespace;
    private LocationData locationData;

    // Static analysis
    private boolean isAnalyzed;             // whether this document has been analyzed already
    private Map controlTypes;               // Map<String type, LinkedHashMap<String prefixedId, ControlInfo info>>
    private Map eventNamesMap;              // Map<String eventName, String "">
    private Map eventHandlersMap;           // Map<String controlPrefixedId, List<XFormsEventHandler> eventHandler>
    private Map controlInfoMap;             // Map<String controlPrefixedId, ControlInfo>
    private Map attributeControls;          // Map<String forPrefixedId, Map<String name, ControlInfo info>>
    // NOTE: namespaces are gathered fully statically (no use of prefix ids); may be right or not
    private Map namespacesMap;              // Map<String staticId, Map<String prefix, String uri>> of namespace mappings
    private Map repeatChildrenMap;          // Map<String, List> of repeat id to List of children
    private String repeatHierarchyString;   // contains comma-separated list of space-separated repeat prefixed id and ancestor if any
    private Map itemsInfoMap;               // Map<String controlPrefixedId, ItemsInfo>
    private Map controlClasses;             // Map<String controlPrefixedId, String classes>
    private boolean hasOfflineSupport;      // whether the document requires offline support
    private List offlineInsertTriggerIds;   // List<String triggerPrefixedId> of triggers can do inserts

    private Map labelsMap = new HashMap();  // Map<String controlPrefixedId, Element element>
    private Map helpsMap = new HashMap();   // Map<String controlPrefixedId, Element element>
    private Map hintsMap = new HashMap();   // Map<String controlPrefixedId, Element element>
    private Map alertsMap = new HashMap();  // Map<String controlPrefixedId, Element element>

    // Components
    private Map componentsFactories;        // Map<QName, Factory> of QNames to component factory
    private Map componentBindings;          // Map<QName, Element> of QNames to bindings
    private Map fullShadowTrees;            // Map<String treePrefixedId, Document> (with full content, e.g. XHTML)
    private Map compactShadowTrees;         // Map<String treePrefixedId, Document> (without full content, only the XForms controls)

    private static final HashMap BASIC_NAMESPACE_MAPPINGS = new HashMap();
    static {
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XFORMS_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XXFORMS_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XMLConstants.XHTML_PREFIX, XMLConstants.XHTML_NAMESPACE_URI);
    }

    /**
     * Create static state object from a Document. This constructor is used when creating an initial static state upon
     * producing an XForms page.
     *
     * @param staticStateDocument   Document containing the static state. The document may be modifed by this constructor and must be discarded afterwards by the caller.
     * @param namespacesMap         Map<String staticId, Map<String prefix, String uri>> of namespace mappings
     * @param annotatedDocument     optional SAXStore containing XHTML for noscript mode
     */
    public XFormsStaticState(PipelineContext pipelineContext, Document staticStateDocument, Map namespacesMap, SAXStore annotatedDocument) {
        initialize(pipelineContext, staticStateDocument, namespacesMap, annotatedDocument, null);
    }

    /**
     * Create static state object from an encoded version. This constructor is used when restoring a static state from
     * a serialized form.
     *
     * @param pipelineContext       current PipelineContext
     * @param encodedStaticState    encoded static state
     */
    public XFormsStaticState(PipelineContext pipelineContext, String encodedStaticState) {

        // Decode encodedStaticState into staticStateDocument
        final Document staticStateDocument = XFormsUtils.decodeXML(pipelineContext, encodedStaticState);

        // Initialize
        initialize(pipelineContext, staticStateDocument, null, null, encodedStaticState);
    }

    /**
     * Initialize. Either there is:
     *
     * o staticStateDocument, namespaceMap, and optional xhtmlDocument
     * o staticStateDocument and encodedStaticState
     *
     * @param staticStateDocument
     * @param encodedStaticState
     * @param namespacesMap
     * @param xhtmlDocument
     */
    private void initialize(PipelineContext pipelineContext, Document staticStateDocument, Map namespacesMap, SAXStore xhtmlDocument, String encodedStaticState) {

        final Element staticStateElement = staticStateDocument.getRootElement();

//        System.out.println(Dom4jUtils.domToString(staticStateDocument));

        // Remember UUID
        this.uuid = UUIDUtils.createPseudoUUID();

        // TODO: if staticStateDocument contains XHTML document, get controls and models from there

        // Extract top-level information
        baseURI = staticStateElement.attributeValue(XMLConstants.XML_BASE_QNAME);
        containerType = staticStateElement.attributeValue("container-type");
        containerNamespace = staticStateElement.attributeValue("container-namespace");
        if (containerNamespace == null)
            containerNamespace = "";

        {
            final String systemId = staticStateElement.attributeValue("system-id");
            if (systemId != null) {
                locationData = new LocationData(systemId, Integer.parseInt(staticStateElement.attributeValue("line")), Integer.parseInt(staticStateElement.attributeValue("column")));
            }
        }

        // Recompute namespace mappings if needed
        final Element htmlElement = staticStateElement.element(XMLConstants.XHTML_HTML_QNAME);
        if (namespacesMap == null) {
            this.namespacesMap = new HashMap();
            try {
//                if (xhtmlDocument == null) {
                    // Recompute from staticStateDocument
                    // TODO: Can there be in this case a nested xhtml:html element, thereby causing duplicate id exceptions?
                    final Transformer identity = TransformerUtils.getIdentityTransformer();

                    // Detach xhtml element as models and controls are enough to produce namespaces map
                    if (htmlElement != null)
                        htmlElement.detach();
                    // Compute namespaces map
                    identity.transform(new DocumentSource(staticStateDocument), new SAXResult(new XFormsDocumentAnnotatorContentHandler(this.namespacesMap)));
                    // Re-attach xhtml element
                    if (htmlElement != null)
                        staticStateElement.add(htmlElement);
//                } else {
//                    // Recompute from xhtmlDocument
//                    final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
//                    identity.setResult(new SAXResult(new XFormsDocumentAnnotatorContentHandler(namespacesMap)));
//                    xhtmlDocument.replay(identity);
//                }
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            // Use map that was passed
            this.namespacesMap = namespacesMap;
        }

        // Extract controls, models and components documents
        extractControlsModelsComponents(pipelineContext, staticStateElement);

        // Extract properties information
        extractProperties(staticStateElement);

        // Extract XHTML if present and requested
        {
            if (xhtmlDocument == null && htmlElement != null) {
                // Get from static state document if available there
                final Document htmlDocument = Dom4jUtils.createDocument();
                htmlDocument.setRootElement((Element) htmlElement.detach());
                this.xhtmlDocument = TransformerUtils.dom4jToSAXStore(htmlDocument);
            } else if (getBooleanProperty(XFormsProperties.NOSCRIPT_PROPERTY)) {
                // Use provided SAXStore ONLY if noscript mode is requested
                this.xhtmlDocument = xhtmlDocument;
            }
        }

        // Extract instances if present
        final Element instancesElement = staticStateElement.element("instances");
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

    private void extractProperties(Element staticStateElement) {
        // Gather xxforms:* properties
        {
            // Global properties (outside models and controls)
            {
                final Element propertiesElement = staticStateElement.element(XFormsConstants.STATIC_STATE_PROPERTIES_QNAME);
                if (propertiesElement != null) {
                    for (Iterator i = propertiesElement.attributeIterator(); i.hasNext();) {
                        final Attribute currentAttribute = (Attribute) i.next();
                        final Object propertyValue = XFormsProperties.parseProperty(currentAttribute.getName(), currentAttribute.getValue());
                        nonDefaultProperties.put(currentAttribute.getName(), propertyValue);
                    }
                }
            }
            // Properties on xforms:model elements
            for (Iterator i = modelDocuments.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currenEntry = (Map.Entry) i.next();
                final Document currentModelDocument = (Document) currenEntry.getValue();
                for (Iterator j = currentModelDocument.getRootElement().attributeIterator(); j.hasNext();) {
                    final Attribute currentAttribute = (Attribute) j.next();
                    if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(currentAttribute.getNamespaceURI())) {
                        final String propertyName = currentAttribute.getName();
                        final Object propertyValue = XFormsProperties.parseProperty(propertyName, currentAttribute.getValue());
                        // Only take the first occurrence into account, and make sure the property is supported
                        if (nonDefaultProperties.get(propertyName) == null && XFormsProperties.getPropertyDefinition(propertyName) != null)
                            nonDefaultProperties.put(propertyName, propertyValue);
                    }
                }
            }
        }

        // Handle default for properties
        final PropertySet propertySet = Properties.instance().getPropertySet();
        for (Iterator i = XFormsProperties.getPropertyDefinitionEntryIterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final String propertyName = (String) currentEntry.getKey();
            final XFormsProperties.PropertyDefinition propertyDefinition = (XFormsProperties.PropertyDefinition) currentEntry.getValue();

            final Object defaultPropertyValue = propertyDefinition.getDefaultValue(); // value can be String, Boolean, Integer
            final Object actualPropertyValue = nonDefaultProperties.get(propertyName); // value can be String, Boolean, Integer
            if (actualPropertyValue == null) {
                // Property not defined in the document, try to obtain from global properties
                final Object globalPropertyValue = propertySet.getObject(XFormsProperties.XFORMS_PROPERTY_PREFIX + propertyName, defaultPropertyValue);

                // If the global property is different from the default, add it
                if (!globalPropertyValue.equals(defaultPropertyValue))
                    nonDefaultProperties.put(propertyName, globalPropertyValue);

            } else {
                // Property defined in the document

                // If the property is identical to the deault, remove it
                if (actualPropertyValue.equals(defaultPropertyValue))
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

        // Parse external-events property
        final String externalEvents = getStringProperty(XFormsProperties.EXTERNAL_EVENTS_PROPERTY);
        if (externalEvents != null) {
            final StringTokenizer st = new StringTokenizer(externalEvents);
            while (st.hasMoreTokens()) {
                if (externalEventsMap == null)
                    externalEventsMap = new HashMap();
                externalEventsMap.put(st.nextToken(), "");
            }
        }
    }

    private void extractControlsModelsComponents(PipelineContext pipelineContext, Element staticStateElement) {

        final Configuration xpathConfiguration = new Configuration();

        // Get top-level models from static state document
        {
            final List modelsElements = staticStateElement.elements(XFormsConstants.XFORMS_MODEL_QNAME);
            modelDocuments.clear();

            // Get all models
            // FIXME: we don't get a System ID here. Is there a simple solution?
            int modelsCount = 0;
            for (Iterator i = modelsElements.iterator(); i.hasNext(); modelsCount++) {
                final Element modelElement = (Element) i.next();
                // Copy the element because we may need it in staticStateDocument for encoding
                final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);
                modelDocuments.put(modelElement.attributeValue("id"), modelDocument);
            }

            XFormsContainingDocument.logDebugStatic("static state", "created top-level model documents", new String[] { "count", Integer.toString(modelsCount) });
        }

        // Get controls document
        {
            // Create document
            controlsDocument = Dom4jUtils.createDocument();
            final Element controlsElement = Dom4jUtils.createElement("controls");
            controlsDocument.setRootElement(controlsElement);

            // Find all top-level controls
            int topLevelControlsCount = 0;
            for (Iterator i = staticStateElement.elements().iterator(); i.hasNext();) {
                final Element currentElement = (Element) i.next();
                final QName currentElementQName = currentElement.getQName();

                if (!currentElementQName.equals(XFormsConstants.XFORMS_MODEL_QNAME)
                        && !currentElementQName.equals(XMLConstants.XHTML_HTML_QNAME)
                        && !XFormsConstants.XBL_NAMESPACE_URI.equals(currentElement.getNamespaceURI())
                        && currentElement.getNamespaceURI() != null && !"".equals(currentElement.getNamespaceURI())) {
                    // Any element in a namespace (xforms:*, xxforms:*, exforms:*, custom namespaces) except xforms:model, xhtml:html and xbl:*

                    // Copy the element because we may need it in staticStateDocument for encoding
                    controlsElement.add(Dom4jUtils.copyElementCopyParentNamespaces(currentElement));
                    topLevelControlsCount++;
                }
            }

            XFormsContainingDocument.logDebugStatic("static state", "created controls document", new String[] { "top-level controls count", Integer.toString(topLevelControlsCount) });
        }

        // Extract models nested within controls
        {
            final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(controlsDocument, null, xpathConfiguration);
            final List extractedModels = extractNestedModels(pipelineContext, controlsDocumentInfo, false, locationData);
            XFormsContainingDocument.logDebugStatic("static state", "created nested model documents", new String[] { "count", Integer.toString(extractedModels.size()) });
            for (Iterator i = extractedModels.iterator(); i.hasNext();) {
                final Document currentModelDocument = (Document) i.next();
                modelDocuments.put(currentModelDocument.getRootElement().attributeValue("id"), currentModelDocument);
            }
        }

        // Extract top-level scripts
        {
            final Document staticStateDocument = staticStateElement.getDocument();
            final DocumentWrapper documentInfo = new DocumentWrapper(staticStateDocument, null, xpathConfiguration);
            extractScripts(pipelineContext, documentInfo, "");
        }

        // Extract components
        {
            final List xblElements = staticStateElement.elements(XFormsConstants.XBL_XBL_QNAME);
            if (xblElements.size() > 0) {
                componentsFactories = new HashMap();
                componentBindings = new HashMap();
                fullShadowTrees = new HashMap();
                compactShadowTrees = new HashMap();

                int xblCount = 0;
                int xblBindingCount = 0;
                for (Iterator i = xblElements.iterator(); i.hasNext(); xblCount++) {
                    final Element currentXBLElement = (Element) i.next();
                    // Copy the element because we may need it in staticStateDocument for encoding
                    final Document currentXBLDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentXBLElement);

                    // Find bindings
                    for (Iterator j = currentXBLDocument.getRootElement().elements(XFormsConstants.XBL_BINDING_QNAME).iterator(); j.hasNext(); xblBindingCount++) {
                        final Element currentBindingElement = (Element) j.next();
                        final String currentElementAttribute = currentBindingElement.attributeValue("element");

                        if (currentElementAttribute != null) {

                            // For now, only handle "prefix|name" selectors
                            final QName currentQNameMatch
                                    = Dom4jUtils.extractTextValueQName(getNamespaceMappings(currentBindingElement), currentElementAttribute.replace('|', ':'), false);

                            // Create and remember factory for this QName
                            componentsFactories.put(currentQNameMatch,
                                new XFormsControlFactory.Factory() {
                                    public XFormsControl createXFormsControl(XFormsContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                                        return new XFormsComponentControl(container, parent, element, name, effectiveId);
                                    }
                                });

                            componentBindings.put(currentQNameMatch, currentBindingElement);
                        }
                    }
                }

                XFormsContainingDocument.logDebugStatic("static state", "created top-level XBL documents",
                        new String[] { "xbl:xbl count", Integer.toString(xblCount), "xbl:binding count", Integer.toString(xblBindingCount)});
            }
        }
    }

    private void extractScripts(PipelineContext pipelineContext, DocumentWrapper documentInfo, String prefix) {
        final List scripts = XPathCache.evaluate(pipelineContext, documentInfo,
                    "/*/(xforms:* | xxforms:*)/descendant-or-self::xxforms:script[not(ancestor::xforms:instance)]",
                BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        if (scripts.size() > 0) {
            if (xxformsScripts == null)
                xxformsScripts = new HashMap();
            for (Iterator i = scripts.iterator(); i.hasNext();) {
                final NodeInfo currentNodeInfo = (NodeInfo) i.next();
                final Element scriptElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                // Remember script content
                xxformsScripts.put(prefix + scriptElement.attributeValue("id"), scriptElement.getStringValue());
                // Detach as the element is no longer needed within the controls
                //scriptElement.detach();
            }
        }
    }

    public String getUUID() {
        return uuid;
    }

    /**
     * Whether the static state is fully initialized. It is the case when:
     *
     * o An encodedStaticState string was provided when restoring the static state, OR
     * o getEncodedStaticState() was called, thereby creating an encodedStaticState string
     *
     * Before the static state if fully initialized, shared instances can be added and contribute to the static state.
     * The lifecycle goes as follows:
     *
     * o Create initial static state from document
     * o 0..n add instances to state
     * o Create serialized static state string
     *
     * o Get existing static state from cache, OR
     * o Restore static state from serialized form
     *
     * @return  true iif static state is fully initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Add a shared instance to this static state. Can only be called if the static state is not entirely initialized.
     *
     * @param instance  shared instance
     */
    public void addSharedInstance(SharedXFormsInstance instance) {
        if (initialized)
            throw new IllegalStateException("Cannot add instances to static state after initialization.");

        if (instancesMap == null)
            instancesMap = new HashMap();

        instancesMap.put(instance.getEffectiveId(), instance);
    }

    /**
     * Get a serialized static state. If an encodedStaticState was provided during restoration, return that. Otherwise,
     * return a serialized static state computed from models, instances, and XHTML documents.
     *
     * @param pipelineContext   current PipelineContext
     * @return                  serialized static sate
     */
    public String getEncodedStaticState(PipelineContext pipelineContext) {

        if (!initialized) {

            final Element rootElement = staticStateDocument.getRootElement();

            if (rootElement.element("instances") != null)
                throw new IllegalStateException("Element instances already present in static state.");

            // TODO: if staticStateDocument will contains XHTML document, don't store controls and models in there

            // Add instances to Document if needed
            if (instancesMap != null && instancesMap.size() > 0) {
                final Element instancesElement = rootElement.addElement("instances");
                for (Iterator instancesIterator = instancesMap.values().iterator(); instancesIterator.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) instancesIterator.next();

                    // Add information for all shared instances, but don't add content for globally shared instances
                    // NOTE: This strategy could be changed in the future or be configurable
                    final boolean serializeInstance = !currentInstance.isApplicationShared();
                    instancesElement.add(currentInstance.createContainerElement(serializeInstance));
                }
            }

            // Handle XHTML document if needed (for noscript mode)
            if (xhtmlDocument != null && rootElement.element(XMLConstants.XHTML_HTML_QNAME) == null) {
                // Add document
                final Document document = TransformerUtils.saxStoreToDom4jDocument(xhtmlDocument);
                staticStateDocument.getRootElement().add(document.getRootElement().detach());
            }

            // Remember encoded state and discard Document
            final boolean isStateHandlingClient = getStringProperty(XFormsProperties.STATE_HANDLING_PROPERTY).equals(XFormsProperties.STATE_HANDLING_CLIENT_VALUE);
            encodedStaticState = XFormsUtils.encodeXML(pipelineContext, staticStateDocument, isStateHandlingClient ? XFormsProperties.getXFormsPassword() : null, true);

            staticStateDocument = null;
            initialized = true;
        }

        return encodedStaticState;
    }

    /**
     * Get a map of available shared instances. Can only be called after initialization is complete.
     *
     * @return  Map<String, SharedXFormsInstance
     */
    public Map getSharedInstancesMap() {
        if (!initialized)
            throw new IllegalStateException("Cannot get instances from static before initialization.");

        return instancesMap;
    }

    /**
     * Return the complete XHTML document if available. Only for noscript mode.
     *
     * @return  SAXStore containing XHTML document
     */
    public SAXStore getXHTMLDocument() {
        return xhtmlDocument;
    }

    public Document getControlsDocument() {
        return controlsDocument;
    }

    public Map getModelDocuments() {
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
    
    public Object getProperty(String propertyName) {
        final Object documentProperty = (Object) nonDefaultProperties.get(propertyName);
        if (documentProperty != null) {
            return documentProperty;
        } else {
            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
            return (propertyDefinition != null) ? propertyDefinition.getDefaultValue() : null; // may be null for example for type formats
        }
    }

    public String getStringProperty(String propertyName) {
        final String documentProperty = (String) nonDefaultProperties.get(propertyName);
        if (documentProperty != null) {
            return documentProperty;
        } else {
            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
            return (propertyDefinition != null) ? (String) propertyDefinition.getDefaultValue() : null; // may be null for example for type formats
        }
    }

    public boolean getBooleanProperty(String propertyName) {
        final Boolean documentProperty = (Boolean) nonDefaultProperties.get(propertyName);
        if (documentProperty != null) {
            return documentProperty.booleanValue();
        } else {
            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
            return ((Boolean) propertyDefinition.getDefaultValue()).booleanValue();
        }
    }

    public int getIntegerProperty(String propertyName) {
        final Integer documentProperty = (Integer) nonDefaultProperties.get(propertyName);
        if (documentProperty != null) {
            return documentProperty.intValue();
        } else {
            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
            return ((Integer) propertyDefinition.getDefaultValue()).intValue();
        }
    }

    public Map getEventNamesMap() {
        return eventNamesMap;
    }

    public List getEventHandlers(String id) {
        return (List) eventHandlersMap.get(id);
    }

    public Map getControlInfoMap() {
        return controlInfoMap;
    }

    public Map getRepeatControlInfoMap() {
        return (Map) controlTypes.get("repeat");
    }

    public Element getControlElement(String prefixeId) {
        return ((XFormsStaticState.ControlInfo) controlInfoMap.get(prefixeId)).getElement();
    }

    public Element getLabelElement(String prefixeId) {
        return (Element) labelsMap.get(prefixeId);
    }

    public Element getHelpElement(String prefixeId) {
        return (Element) helpsMap.get(prefixeId);
    }

    public Element getHintElement(String prefixeId) {
        return (Element) hintsMap.get(prefixeId);
    }

    public Element getAlertElement(String prefixeId) {
        return (Element) alertsMap.get(prefixeId);
    }

    /**
     * Statically check whether a control is a value control.
     *
     * @param controlEffectiveId    prefixed id or effective id of the control
     * @return                      true iif the control is a value control
     */
    public boolean isValueControl(String controlEffectiveId) {
        final ControlInfo controlInfo = (ControlInfo) controlInfoMap.get(XFormsUtils.getEffectiveIdNoSuffix(controlEffectiveId));
        return (controlInfo != null) ? controlInfo.isValueControl() : false;
    }

    /**
     * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
     * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
     * as the mapping is considered transient and not sharable among pages.
     *
     * @param element       Element to get namsepace mapping for
     * @return              Map<String prefix, String uri>
     */
    public Map getNamespaceMappings(Element element) {
        final String id = element.attributeValue("id");
        if (id != null) {
            // There is an id attribute
            final Map cachedMap = (Map) namespacesMap.get(id);
            if (cachedMap != null) {
                return cachedMap;
            } else {
                XFormsContainingDocument.logDebugStatic("static state", "namespace mappings not cached", new String[] { "element", Dom4jUtils.elementToString(element) });
                return Dom4jUtils.getNamespaceContextNoDefault(element);
            }
        } else {
            // No id attribute
            XFormsContainingDocument.logDebugStatic("static state", "namespace mappings not cached", new String[] { "element", Dom4jUtils.elementToString(element) });
            return Dom4jUtils.getNamespaceContextNoDefault(element);
        }
    }

    public String getRepeatHierarchyString() {
        return repeatHierarchyString;
    }

    public boolean hasControlByName(String controlName) {
        return controlTypes.get(controlName) != null;
    }

    public ItemsInfo getItemsInfo(String controlPrefixedId) {
        return (itemsInfoMap != null) ? (XFormsStaticState.ItemsInfo) itemsInfoMap.get(controlPrefixedId) : null;
    }

    /**
     * Whether a host language element with the given id ("for attribute") has an AVT on an attribute.
     *
     * @param prefixedForAttribute  id of the host language element to check
     * @return                      true iif that element has one or more AVTs
     */
    public boolean hasAttributeControl(String prefixedForAttribute) {
        return attributeControls != null && attributeControls.get(prefixedForAttribute) != null;
    }

    public ControlInfo getAttributeControl(String prefixedForAttribute, String attributeName) {
        final Map mapForId = (Map) attributeControls.get(prefixedForAttribute);
        return (mapForId != null) ? (ControlInfo) mapForId.get(attributeName) : null;
    }

    /*
     * Return whether this document has at leat one component in use.
     */
    public boolean hasComponentsInUse() {
        return componentBindings != null && componentBindings.size() > 0;
    }

    /**
     * All component bindings.
     *
     * @return Map<QName, Element> of QNames to bindings, or null
     */
    public Map getComponentBindings() {
        return componentBindings;
    }

    /**
     * Return whether the given QName has an associated binding.
     *
     * @param qName QName to check
     * @return      true iif there is a binding
     */
    public boolean isComponent(QName qName) {
        return componentBindings != null && componentBindings.get(qName) != null;
    }

    /**
     * Return a control factory for the given QName.
     *
     * @param qName QName to check
     * @return      control factory, or null
     */
    public XFormsControlFactory.Factory getComponentFactory(QName qName) {
        return (componentsFactories == null) ? null : (XFormsControlFactory.Factory) componentsFactories.get(qName);
    }

    /**
     * Return the expanded shadow tree for the given static control id.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      full expanded shadow tree, or null
     */
    public Element getFullShadowTree(String controlPrefixedId) {
        return (fullShadowTrees == null) ? null : ((Document) fullShadowTrees.get(controlPrefixedId)).getRootElement();
    }

    /**
     * Return the expanded shadow tree for the given static control id, with only XForms controls and no markup.
     *
     * @param controlPrefixedId     prefixed control id
     * @return                      compact expanded shadow tree, or null
     */
    public Element getCompactShadowTree(String controlPrefixedId) {
        return (compactShadowTrees == null) ? null : ((Document) compactShadowTrees.get(controlPrefixedId)).getRootElement();
    }

    /**
     * Perform static analysis on this document if not already done.
     *
     * @param pipelineContext   current pipeline context
     * @return                  true iif analysis was just performed in this call
     */
    public synchronized boolean analyzeIfNecessary(final PipelineContext pipelineContext) {
        if (!isAnalyzed) {
            controlTypes = new HashMap();
            eventNamesMap = new HashMap();
            eventHandlersMap = new HashMap();
            controlInfoMap = new HashMap();
            repeatChildrenMap = new HashMap();

            // Iterate over main static controls tree
            final Configuration xpathConfiguration = new Configuration();
            final FastStringBuffer repeatHierarchyStringBuffer = new FastStringBuffer(1024);
            // NOTE: Say we DO want to exclude gathering event handlers within nested models, since those are gathered below
            analyzeComponentTree(pipelineContext, xpathConfiguration, "", controlsDocument.getRootElement(), repeatHierarchyStringBuffer, true);

            if (xxformsScripts != null && xxformsScripts.size() > 0)
                XFormsContainingDocument.logDebugStatic("static state", "extracted script elements", new String[] { "count", Integer.toString(xxformsScripts.size()) });

            // Finalize repeat hierarchy
            repeatHierarchyString = repeatHierarchyStringBuffer.toString();

            // Iterate over models to extract event handlers
            for (Iterator i = modelDocuments.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();

                final String modelPrefixedId = (String) currentEntry.getKey();
                final Document modelDocument = (Document) currentEntry.getValue();
                final DocumentWrapper modelDocumentInfo = new DocumentWrapper(modelDocument, null, xpathConfiguration);
                // NOTE: Say we don't want to exclude gathering event handlers within nested models, since this is a model
                extractEventHandlers(pipelineContext, modelDocumentInfo, XFormsUtils.getEffectiveIdPrefix(modelPrefixedId), false);
            }

            isAnalyzed = true;
            return true;
        } else {
            return false;
        }
    }

    private void extractEventHandlers(PipelineContext pipelineContext, DocumentInfo documentInfo, String prefix, boolean excludeModels) {

        // Two expressions depending on whether handlers within models are excluded or not
        final String xpathExpression = excludeModels ?
                "//(xforms:* | xxforms:*)[@ev:event and not(ancestor::xforms:instance) and not(ancestor::xforms:model) and (parent::xforms:* | parent::xxforms:*)/@id]" :
                "//(xforms:* | xxforms:*)[@ev:event and not(ancestor::xforms:instance) and (parent::xforms:* | parent::xxforms:*)/@id]";

        // Get all candidate elements
        final List actionHandlers = XPathCache.evaluate(pipelineContext, documentInfo,
                xpathExpression, BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        // Check them all
        for (Iterator i = actionHandlers.iterator(); i.hasNext();) {
            final NodeInfo currentNodeInfo = (NodeInfo) i.next();

            if (currentNodeInfo instanceof NodeWrapper) {
                final Element currentElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                if (XFormsActions.isActionName(currentElement.getNamespaceURI(), currentElement.getName())) {
                    // This is a known action name
                    // TODO: The code below excludes cases like <xforms:model><xform:action ev:event="abc"><xforms:setvalue ev:event="def">

//                    final Element parentElement = currentElement.getParent();
//                    final String observerAttribute = currentElement.attributeValue(XFormsConstants.XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME);
//                    if (isEventObserver(parentElement) || observerAttribute != null) {
                        // Either 1) the parent is an observer or 2) there is an explicit ev:observer attribute  
                        XFormsEventHandlerImpl.addActionHandler(eventNamesMap, eventHandlersMap, currentElement, prefix);
//                    }
                }
            }
        }
    }

    private void analyzeComponentTree(final PipelineContext pipelineContext, final Configuration xpathConfiguration,
                                      final String prefix, Element startElement, final FastStringBuffer repeatHierarchyStringBuffer, boolean excludeModelEventHandlers) {

        final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(startElement.getDocument(), null, xpathConfiguration);

        // Extract event handlers for this tree of controls
        extractEventHandlers(pipelineContext, controlsDocumentInfo, prefix, excludeModelEventHandlers);

        // Visit tree
        visitAllControlStatic(startElement, new XFormsStaticState.ControlElementVisitorListener() {

            private Stack repeatAncestorsStack = new Stack();

            public void startVisitControl(Element controlElement, String controlStaticId) {

                // Check for mandatory id
                // TODO: Currently, XFDA does not automatically produce id attributes on elements to which components are bound
                if (controlStaticId == null)
                    throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName(), locationData);

                // Prefixed id
                final String controlPrefixedId = prefix + controlStaticId;

                // Gather control name
                final String controlName = controlElement.getName();
                final String controlURI = controlElement.getNamespaceURI();

                final LocationData locationData = new ExtendedLocationData((LocationData) controlElement.getData(), "gathering static control information", controlElement);

                // If element is not built-in, check XBL and generate shadow content if needed
                if (componentBindings != null) {
                    final Element bindingElement = (Element) componentBindings.get(controlElement.getQName());
                    if (bindingElement != null) {
                        // A custom component is bound to this element

                        // TODO: add namespaces to namespacesMap if not present yet, as namespaces on components are not gathered by XFDA
                        // NOTE: namespaces are gathered fully statically (no use of prefix ids); may be right or not

                        // If the document has a template, recurse into it
                        final Document fullShadowTreeDocument = XBLUtils.generateXBLShadowContent(pipelineContext, controlsDocumentInfo, controlElement, bindingElement, namespacesMap);
                        if (fullShadowTreeDocument != null) {

                            // Extract models from components instances
                            final DocumentWrapper fullShadowTreeWrapper = new DocumentWrapper(fullShadowTreeDocument, null, xpathConfiguration);
                            final List extractedModels = extractNestedModels(pipelineContext, fullShadowTreeWrapper, true, locationData);

                            for (Iterator i = extractedModels.iterator(); i.hasNext();) {
                                final Document currentModelDocument = (Document) i.next();
                                // Store models by "prefixed id"
                                modelDocuments.put(controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR + currentModelDocument.getRootElement().attributeValue("id"), currentModelDocument);
                            }

                            XFormsContainingDocument.logDebugStatic("static state", "created nested model documents", new String[] { "count", Integer.toString(extractedModels.size()) });

                            // Remember full shadow tree for this prefixed id
                            fullShadowTrees.put(controlPrefixedId, fullShadowTreeDocument);

                            // Generate compact shadow tree for this static id
                            final Document compactShadowTreeDocument = XBLUtils.filterShadowTree(fullShadowTreeDocument, controlElement);
                            compactShadowTrees.put(controlPrefixedId, compactShadowTreeDocument);

                            // Find new prefix
                            final String newPrefix = controlPrefixedId + XFormsConstants.COMPONENT_SEPARATOR;

                            // Extract scripts within this shadow tree
                            final DocumentWrapper compactShadowTreeWrapper = new DocumentWrapper(compactShadowTreeDocument, null, xpathConfiguration);
                            extractScripts(pipelineContext, compactShadowTreeWrapper, newPrefix);

                            // NOTE: Say we don't want to exclude gathering event handlers within nested models
                            analyzeComponentTree(pipelineContext, xpathConfiguration, newPrefix, compactShadowTreeDocument.getRootElement(), repeatHierarchyStringBuffer, false);
                        }
                    }
                }

                // Check for mandatory and optional bindings
                final boolean hasBinding;
                if (controlElement != null) {

                    final boolean hasBind = controlElement.attribute("bind") != null;
                    final boolean hasRef = controlElement.attribute("ref") != null;
                    final boolean hasNodeset = controlElement.attribute("nodeset") != null;

                    if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(controlURI)) {
                        if (XFormsControlFactory.MANDATORY_SINGLE_NODE_CONTROLS.get(controlName) != null && !(hasRef || hasBind)) {
                            throw new ValidationException("Missing mandatory single node binding for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControlFactory.NO_SINGLE_NODE_CONTROLS.get(controlName) != null && (hasRef || hasBind)) {
                            throw new ValidationException("Single node binding is prohibited for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControlFactory.MANDATORY_NODESET_CONTROLS.get(controlName) != null && !(hasNodeset || hasBind)) {
                            throw new ValidationException("Missing mandatory nodeset binding for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControlFactory.NO_NODESET_CONTROLS.get(controlName) != null && hasNodeset) {
                            throw new ValidationException("Node-set binding is prohibited for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControlFactory.SINGLE_NODE_OR_VALUE_CONTROLS.get(controlName) != null && !(hasRef || hasBind || controlElement.attribute("value") != null)) {
                            throw new ValidationException("Missing mandatory single node binding or value attribute for element: " + controlElement.getQualifiedName(), locationData);
                        }
                    }

                    hasBinding = hasBind || hasRef || hasNodeset;
                } else {
                    hasBinding = false;
                }

                // Create and index static control information
                final ControlInfo info = new ControlInfo(controlElement, hasBinding, XFormsControlFactory.isValueControl(controlURI, controlName));
                controlInfoMap.put(controlPrefixedId, info);
                {
                    Map controlsMap = (Map) controlTypes.get(controlName);
                    if (controlsMap == null) {
                        controlsMap = new LinkedHashMap();
                        controlTypes.put(controlName, controlsMap);
                    }

                    controlsMap.put(controlPrefixedId, info);
                }

                if (controlName.equals("repeat")) {
                    // Gather xforms:repeat information

                    // Find repeat parents
                    {
                        // Create repeat hierarchy string
                        if (repeatHierarchyStringBuffer.length() > 0)
                            repeatHierarchyStringBuffer.append(',');

                        repeatHierarchyStringBuffer.append(controlPrefixedId);

                        if (repeatAncestorsStack.size() > 0) {
                            // If we have a parent, append it
                            final String parentRepeatId = (String) repeatAncestorsStack.peek();
                            repeatHierarchyStringBuffer.append(' ');
                            repeatHierarchyStringBuffer.append(parentRepeatId);
                        }
                    }
                    // Find repeat children
                    {
                        if (repeatAncestorsStack.size() > 0) {
                            // If we have a parent, tell the parent that it has a child
                            final String parentRepeatId = (String) repeatAncestorsStack.peek();
                            List parentRepeatList = (List) repeatChildrenMap.get(parentRepeatId);
                            if (parentRepeatList == null) {
                                parentRepeatList = new ArrayList();
                                repeatChildrenMap.put(parentRepeatId, parentRepeatList);
                            }
                            parentRepeatList.add(controlPrefixedId);
                        }

                    }

                    repeatAncestorsStack.push(controlPrefixedId);
                } else if (controlName.equals("select") || controlName.equals("select1")) {
                    // Gather itemset information

                    final NodeInfo controlNodeInfo = controlsDocumentInfo.wrap(controlElement);

                    // Try to figure out if we have dynamic items. This attempts to cover all cases, including
                    // nested xforms:output controls. Check only under xforms:choices, xforms:item and xforms:itemset so that we
                    // don't check things like event handlers.
                    final boolean hasNonStaticItem = ((Boolean) XPathCache.evaluateSingle(pipelineContext, controlNodeInfo,
                            "exists(./(xforms:choices | xforms:item | xforms:itemset)//xforms:*[@ref or @nodeset or @bind or @value])", BASIC_NAMESPACE_MAPPINGS,
                            null, null, null, null, locationData)).booleanValue();

                    // Remember information
                    if (itemsInfoMap == null)
                        itemsInfoMap = new HashMap();
                    itemsInfoMap.put(controlPrefixedId, new XFormsStaticState.ItemsInfo(hasNonStaticItem));
//                } else if (controlName.equals("case")) {
                    // TODO: Check that xforms:case is within: switch
//                    if (!(currentControlsContainer.getName().equals("switch")))
//                        throw new ValidationException("xforms:case with id '" + effectiveControlId + "' is not directly within an xforms:switch container.", xformsControl.getLocationData());
                } else  if ("attribute".equals(controlName)) {
                    // Special indexing of xxforms:attribute controls
                    final String prefixedForAttribute = prefix + controlElement.attributeValue("for");
                    final String nameAttribute = controlElement.attributeValue("name");
                    Map mapForId;
                    if (attributeControls == null) {
                        attributeControls = new HashMap();
                        mapForId = new HashMap();
                        attributeControls.put(prefixedForAttribute, mapForId);
                    } else {
                        mapForId = (Map) attributeControls.get(prefixedForAttribute);
                        if (mapForId == null) {
                            mapForId = new HashMap();
                            attributeControls.put(prefixedForAttribute, mapForId);
                        }
                    }
                    mapForId.put(nameAttribute, info);
                }
            }

            public void endVisitControl(Element controlElement, String controlId) {
                final String controlName = controlElement.getName();
                if (controlName.equals("repeat")) {
                    repeatAncestorsStack.pop();
                }
            }
        });

        // Gather label, hint, help, alert information
        {
            // Search LHHA elements that either:
            //
            // o have @for attribute
            // o are the child of an xforms:* or xxforms:* element that has an id
            final List lhhaElements = XPathCache.evaluate(pipelineContext, controlsDocumentInfo,
                "//(xforms:label | xforms:help | xforms:hint | xforms:alert)[not(ancestor::xforms:instance) and exists(@for | parent::xforms:*/@id | parent::xxforms:*/@id)]", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            int lhhaCount = 0;
            for (Iterator i = lhhaElements.iterator(); i.hasNext(); lhhaCount++) {
                final NodeInfo currentNodeInfo = (NodeInfo) i.next();
                final Element llhaElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                final Element parentElement = llhaElement.getParent();

                final String forAttribute = llhaElement.attributeValue("for");
                final String controlPrefixedId;
                if (forAttribute == null || XFormsControlFactory.isCoreControl(parentElement.getNamespaceURI(), parentElement.getName())) {
                    // Element is directly nested in XForms element OR it has a @for attribute but is within a core control so we ignore the @for attribute
                    controlPrefixedId = prefix + llhaElement.getParent().attributeValue("id");
                } else {
                    // Element has a @for attribute and is not within a core control
                    controlPrefixedId = prefix + forAttribute;
                }

                final String elementName = llhaElement.getName();
                if ("label".equals(elementName)) {
                    labelsMap.put(controlPrefixedId, llhaElement);
                } else if ("help".equals(elementName)) {
                    helpsMap.put(controlPrefixedId, llhaElement);
                } else if ("hint".equals(elementName)) {
                    hintsMap.put(controlPrefixedId, llhaElement);
                } else if ("alert".equals(elementName)) {
                    alertsMap.put(controlPrefixedId, llhaElement);
                }
            }
            XFormsContainingDocument.logDebugStatic("static state", "extracted label, help, hint and alert elements", new String[] { "count", Integer.toString(lhhaCount) });
        }

        // Gather online/offline information
        {
            // NOTE: We attempt to localize what triggers can cause, upon DOMActivate, xxforms:online, xxforms:offline and xxforms:offline-save actions
            final List onlineTriggerIds = XPathCache.evaluate(pipelineContext, controlsDocumentInfo,
                "for $handler in for $action in //xxforms:online return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id)", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            final List offlineTriggerIds = XPathCache.evaluate(pipelineContext, controlsDocumentInfo,
                "for $handler in for $action in //xxforms:offline return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id)", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            final List offlineSaveTriggerIds = XPathCache.evaluate(pipelineContext, controlsDocumentInfo,
                "for $handler in for $action in //xxforms:offline-save return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id)", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            offlineInsertTriggerIds = XPathCache.evaluate(pipelineContext, controlsDocumentInfo,
                "for $handler in for $action in //xforms:insert return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id)", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            final List offlineDeleteTriggerIds = XPathCache.evaluate(pipelineContext, controlsDocumentInfo,
                "for $handler in for $action in //xforms:delete return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id)", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            for (Iterator i = onlineTriggerIds.iterator(); i.hasNext();) {
                final String currentId = (String) i.next();
                addClasses(prefix + currentId, "xxforms-online");
            }

            for (Iterator i = offlineTriggerIds.iterator(); i.hasNext();) {
                final String currentId = (String) i.next();
                addClasses(prefix + currentId, "xxforms-offline");
            }

            for (Iterator i = offlineSaveTriggerIds.iterator(); i.hasNext();) {
                final String currentId = (String) i.next();
                addClasses(prefix + currentId, "xxforms-offline-save");
            }

            for (Iterator i = offlineInsertTriggerIds.iterator(); i.hasNext();) {
                final String currentId = (String) i.next();
                addClasses(prefix + currentId, "xxforms-offline-insert");
            }

            for (Iterator i = offlineDeleteTriggerIds.iterator(); i.hasNext();) {
                final String currentId = (String) i.next();
                addClasses(prefix + currentId, "xxforms-offline-delete");
            }

            {
                // Create list of all the documents to search
                final List documentInfos = new ArrayList(modelDocuments.size() + 1);
                for (Iterator i = modelDocuments.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currenEntry = (Map.Entry) i.next();
                    final Document currentModelDocument = (Document) currenEntry.getValue();
                    documentInfos.add(new DocumentWrapper(currentModelDocument, null, xpathConfiguration));
                }
                documentInfos.add(controlsDocumentInfo);

                // Search for xxforms:offline which are not within instances
                for (Iterator i = documentInfos.iterator(); i.hasNext();) {
                    final DocumentInfo currentDocumentInfo = (DocumentInfo) i.next();
                    hasOfflineSupport |= ((Boolean) XPathCache.evaluateSingle(pipelineContext, currentDocumentInfo,
                        "exists(//xxforms:offline[not(ancestor::xforms:instance)])", BASIC_NAMESPACE_MAPPINGS,
                        null, null, null, null, locationData)).booleanValue();
                }
            }
        }
    }

    private static List extractNestedModels(PipelineContext pipelineContext, DocumentWrapper compactShadowTreeWrapper, boolean detach, LocationData locationData) {

        final List result = new ArrayList();

        final List modelElements = XPathCache.evaluate(pipelineContext, compactShadowTreeWrapper,
                "//xforms:model[not(ancestor::xforms:instance)]",
                BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        if (modelElements.size() > 0) {
            for (Iterator i = modelElements.iterator(); i.hasNext();) {
                final NodeInfo currentNodeInfo = (NodeInfo) i.next();
                final Element currentModelElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                final Document modelDocument;
                if (detach) {
                    modelDocument = Dom4jUtils.createDocument();
                    modelDocument.setRootElement((Element) currentModelElement.detach());
                } else {
                    // Copy the element because we may need it in staticStateDocument for encoding
                    modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentModelElement);
                }

                result.add(modelDocument);
            }
        }

        return result;
    }

    public boolean isHasOfflineSupport() {
        return hasOfflineSupport;
    }

    private void addClasses(String controlPrefixedId, String classes) {
        if (controlClasses == null)
            controlClasses = new HashMap();
        final String currentClasses = (String) controlClasses.get(controlPrefixedId);
        if (currentClasses == null) {
            // Set
            controlClasses.put(controlPrefixedId, classes);
        } else {
            // Append
            controlClasses.put(controlPrefixedId, currentClasses + ' ' + classes);
        }
    }
    
    public void appendClasses(FastStringBuffer sb, String prefixedId) {
        if ((controlClasses == null))
            return;

        if (sb.length() > 0)
            sb.append(' ');

        final String classes = (String) controlClasses.get(prefixedId);
        if (classes != null)
            sb.append(classes);
    }

    public List getOfflineInsertTriggerIds() {
        return offlineInsertTriggerIds;
    }

    /**
     * Visit all the control elements without handling repeats or looking at the binding contexts. This is done entirely
     * staticaly. Only controls are visited, including grouping controls, leaf controls, and components.
     */
    private void visitAllControlStatic(Element startElement, ControlElementVisitorListener controlElementVisitorListener) {
        handleControlsStatic(controlElementVisitorListener, startElement);
    }

    private void handleControlsStatic(ControlElementVisitorListener controlElementVisitorListener, Element container) {
        for (Iterator i = container.elements().iterator(); i.hasNext();) {
            final Element currentControlElement = (Element) i.next();

            final String controlName = currentControlElement.getName();
            final String controlId = currentControlElement.attributeValue("id");

            if (XFormsControlFactory.isContainerControl(currentControlElement.getNamespaceURI(), controlName)) {
                // Handle XForms grouping controls
                controlElementVisitorListener.startVisitControl(currentControlElement, controlId);
                handleControlsStatic(controlElementVisitorListener, currentControlElement);
                controlElementVisitorListener.endVisitControl(currentControlElement, controlId);
            } else if (XFormsControlFactory.isCoreControl(currentControlElement.getNamespaceURI(), controlName) || componentBindings != null && componentBindings.get(currentControlElement.getQName()) != null) {
                // Handle core control or component
                controlElementVisitorListener.startVisitControl(currentControlElement, controlId);
                controlElementVisitorListener.endVisitControl(currentControlElement, controlId);
            }
        }
    }

    private static interface ControlElementVisitorListener {
        public void startVisitControl(Element controlElement, String controlId);
        public void endVisitControl(Element controlElement, String controlId);
    }

    public static class ItemsInfo {
        private boolean hasNonStaticItem;

        public ItemsInfo(boolean hasNonStaticItem) {
            this.hasNonStaticItem = hasNonStaticItem;
        }

        public boolean hasNonStaticItem() {
            return hasNonStaticItem;
        }
    }

    public static class ControlInfo {
        private Element element;
        private boolean hasBinding;
        private boolean isValueControl;

        public ControlInfo(Element element, boolean hasBinding, boolean isValueControl) {
            this.element = element;
            this.hasBinding = hasBinding;
            this.isValueControl = isValueControl;
        }

        public Element getElement() {
            return element;
        }

        public boolean hasBinding() {
            return hasBinding;
        }

        public boolean isValueControl() {
            return isValueControl;
        }
    }

    /**
     * Return true if the given element is an event observer. Must return true for controls, xforms:model,
     * xforms:instance, xforms:submission.
     *
     * @param element   element to check
     * @return          true iif the element is an event observer
     */
    public static boolean isEventObserver(Element element) {

        if (XFormsControlFactory.isBuiltinControl(element.getNamespaceURI(), element.getName())) {
            return true;
        }

        final String localName = element.getName();
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(element.getNamespaceURI())
                && ("model".equals(localName) || "instance".equals(localName) || "submission".equals(localName))) {
            return true;
        }

        return false;
    }
}
