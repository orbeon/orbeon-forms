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
package org.orbeon.oxf.xforms;

import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.analysis.IdGenerator;
import org.orbeon.oxf.xforms.analysis.XFormsAnnotatorContentHandler;
import org.orbeon.oxf.xforms.analysis.XFormsExtractorContentHandler;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandler;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
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
 * NOTE: This code will have to change a bit if we move towards TinyTree to store the static state.
 */
public class XFormsStaticState {

    public static final String LOGGING_CATEGORY = "analysis";
    private static final Logger logger = LoggerFactory.createLogger(XFormsStaticState.class);
    private final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

    private boolean initialized;

    private String uuid;
    private String encodedStaticState;      // encoded state
    private Document staticStateDocument;   // if present, stored there temporarily only until getEncodedStaticState() is called and encodedStaticState is produced

    private Document controlsDocument;                                      // controls document
    private LinkedHashMap<String, Document> modelDocuments = new LinkedHashMap<String, Document>(); // Map<String modelPrefixedId, Document modelDocument>
    private SAXStore xhtmlDocument;                                         // entire XHTML document for noscript mode only

    private Map<String, String> xxformsScripts;                             // Map of id to script content

    private final Map<String, Object> nonDefaultProperties = new HashMap<String, Object>(); // Map of property name to property value (String, Integer, Boolean)
    private final Set<String> allowedExternalEvents = new HashSet<String>();        // Set<String eventName>

    private boolean isSeparateDeployment;
    private String requestContextPath;
    private String baseURI;
    private String containerType;
    private String containerNamespace;
    private LocationData locationData;

    private List<URLRewriterUtils.PathMatcher> versionedPathMatchers;

    // Static analysis
    private boolean isAnalyzed;                                             // whether this document has been analyzed already

    private XFormsAnnotatorContentHandler.Metadata metadata;

    // Event handlers
    private Set<String> eventNames;                                         // used event names
    private Map<String, List<XFormsEventHandler>> eventHandlersMap;         // Map<String observerPrefixedId, List<XFormsEventHandler> eventHandler>: for all observers with handlers
    private Map<String, String> eventHandlerAncestorsMap;                   // Map<String actionPrefixId, String ancestorPrefixedId>
    private List<XFormsEventHandler> keyHandlers;

    // Controls
    private Map<String, Map<String, ControlInfo>> controlTypes;             // Map<String type, Map<String prefixedId, ControlInfo info>>
    // TODO: move itemsInfoMap and controlClasses to ControlInfo?
    private Map<String, ControlInfo> controlInfoMap;                        // Map<String controlPrefixedId, ControlInfo>: for all controls
    private Map<String, ItemsInfo> itemsInfoMap;                            // Map<String controlPrefixedId, ItemsInfo>: all select/select1
    private Map<String, String> controlClasses;                             // Map<String controlPrefixedId, String classes>: all controls w/ special class info (currently only for offline)

    // xforms:repeat
    // TODO: move repeatChildrenMap to ControlInfo?
    private Map<String, List<String>> repeatChildrenMap;                    // Map<String, List> of repeat id to List of children ids
    private String repeatHierarchyString;                                   // contains comma-separated list of space-separated repeat prefixed id and ancestor if any

    // XXFormsAttributeControl
    private Map<String, Map<String, ControlInfo>> attributeControls;        // Map<String forPrefixedId, Map<String name, ControlInfo info>>

    // Offline support
    private boolean hasOfflineSupport;                                      // whether the document requires offline support
    private List<String> offlineInsertTriggerIds;                           // List<String triggerPrefixedId> of triggers can do inserts

    // All these are Map<String controlPrefixedId, Element element>
    private final Map<String, Element> labelsMap = new HashMap<String, Element>();
    private final Map<String, Element> helpsMap = new HashMap<String, Element>();
    private final Map<String, Element> hintsMap = new HashMap<String, Element>();
    private final Map<String, Element> alertsMap = new HashMap<String, Element>();

    // Components
    private XBLBindings xblBindings;

    private static final Map<String, String> BASIC_NAMESPACE_MAPPINGS = new HashMap<String, String>();

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
     * @param propertyContext       current context
     * @param staticStateDocument   document containing the static state, may be modified by this constructor and must be discarded afterwards by the caller
     * @param metadata              metadata or null if not available
     * @param annotatedDocument     SAXStore containing the XHTML for noscript mode, null if not available
     */
    public XFormsStaticState(PropertyContext propertyContext, Document staticStateDocument, XFormsAnnotatorContentHandler.Metadata metadata, SAXStore annotatedDocument) {
        initialize(propertyContext, staticStateDocument, metadata, annotatedDocument, null);
    }

    /**
     * Create static state object from an encoded version. This constructor is used when restoring a static state from
     * a serialized form.
     *
     * @param pipelineContext       current context
     * @param encodedStaticState    encoded static state
     */
    public XFormsStaticState(PropertyContext pipelineContext, String encodedStaticState) {

        // Decode encodedStaticState into staticStateDocument
        final Document staticStateDocument = XFormsUtils.decodeXML(pipelineContext, encodedStaticState);

        // Initialize
        initialize(pipelineContext, staticStateDocument, null, null, encodedStaticState);
    }

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    /**
     * Return path matchers for versioned resources mode.
     *
     * @return  List of PathMatcher
     */
    public List<URLRewriterUtils.PathMatcher> getVersionedPathMatchers() {
        return versionedPathMatchers;
    }

    /**
     * Initialize. Either there is:
     *
     * o staticStateDocument, topLevelStaticIds, namespaceMap, and optional xhtmlDocument
     * o staticStateDocument and encodedStaticState
     *
     * @param propertyContext       current context
     * @param staticStateDocument   document containing the static state, may be modified by this constructor and must be discarded afterwards by the caller
     * @param metadata              metadata or null if not available
     * @param xhtmlDocument         SAXStore containing the XHTML for noscript mode, null if not available
     * @param encodedStaticState    existing serialization of static state, null if not available
     */
    private void initialize(PropertyContext propertyContext, Document staticStateDocument,
                            XFormsAnnotatorContentHandler.Metadata metadata,
                            SAXStore xhtmlDocument, String encodedStaticState) {

        indentedLogger.startHandleOperation("", "initializing static state");

        final Element staticStateElement = staticStateDocument.getRootElement();

        // Remember UUID
        this.uuid = UUIDUtils.createPseudoUUID();

        // TODO: if staticStateDocument contains XHTML document, get controls and models from there

        // Extract top-level information
        isSeparateDeployment = "separate".equals(staticStateElement.attributeValue("deployment"));
        requestContextPath = staticStateElement.attributeValue("context-path");
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
        if (metadata == null) {
            final IdGenerator idGenerator;
            {
                // Use the last id used for id generation. During state restoration, XBL components must start with this id.
                final Element currentIdElement = staticStateElement.element(XFormsExtractorContentHandler.LAST_ID_QNAME);
                assert currentIdElement != null;
                final String lastId = currentIdElement.attributeValue("id");
                assert lastId != null;
                idGenerator = new IdGenerator(Integer.parseInt(lastId));
            }
            final Map<String, Map<String, String>> namespacesMap = new HashMap<String, Map<String, String>>();
            this.metadata = new XFormsAnnotatorContentHandler.Metadata(idGenerator, namespacesMap);
            try {
//                if (xhtmlDocument == null) {
                    // Recompute from staticStateDocument
                    // TODO: Can there be in this case a nested xhtml:html element, thereby causing duplicate id exceptions?
                    final Transformer identity = TransformerUtils.getIdentityTransformer();

                    // Detach xhtml element as models and controls are enough to produce namespaces map
                    if (htmlElement != null)
                        htmlElement.detach();
                    // Compute namespaces map
                    identity.transform(new DocumentSource(staticStateDocument), new SAXResult(new XFormsAnnotatorContentHandler(this.metadata)));
                    // Re-attach xhtml element
                    if (htmlElement != null)
                        staticStateElement.add(htmlElement);
//                } else {
//                    // Recompute from xhtmlDocument
//                    final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
//                    identity.setResult(new SAXResult(new XFormsAnnotatorContentHandler(namespacesMap)));
//                    xhtmlDocument.replay(identity);
//                }
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            // Use map that was passed
            this.metadata = metadata;
        }

        // Extract controls, models and components documents
        extractControlsModelsComponents(propertyContext, staticStateElement, this.metadata.idGenerator);

        // Extract properties information
        extractProperties(staticStateElement);

        // Extract XHTML if present and requested
        {
            if (xhtmlDocument == null && htmlElement != null) {
                // Get from static state document if available there
                final Document htmlDocument = Dom4jUtils.createDocument();
                htmlDocument.setRootElement((Element) htmlElement.detach());
                this.xhtmlDocument = TransformerUtils.dom4jToSAXStore(htmlDocument);
            } else if (isNoScript()) {
                // Use provided SAXStore ONLY if noscript mode is requested
                this.xhtmlDocument = xhtmlDocument;
            } else if (this.metadata.marks.size() > 0) {
                // Keep XHTML if we have marks
                this.xhtmlDocument = xhtmlDocument;
            } else {
                // Otherwise there is no need to keep XHTML
                this.xhtmlDocument = null;
            }

            if (this.xhtmlDocument != null && indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug("", "keeping XHTML tree", "approximate size (bytes)", Long.toString(this.xhtmlDocument.getApproximateSize()));
            }
        }

        // Extract versioned paths matchers if present
        {
            final Element matchersElement = staticStateElement.element("matchers");
            if (matchersElement != null) {
                final List<Element> matchersElements = Dom4jUtils.elements(matchersElement, "matcher");
                this.versionedPathMatchers = new ArrayList<URLRewriterUtils.PathMatcher>(matchersElements.size());
                for (Element currentMatcherElement: matchersElements) {
                    versionedPathMatchers.add(new URLRewriterUtils.PathMatcher(currentMatcherElement));
                }
            } else {
                // Otherwise use matchers from the pipeline context
                this.versionedPathMatchers = (List<URLRewriterUtils.PathMatcher>) propertyContext.getAttribute(PipelineContext.PATH_MATCHERS);
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

        indentedLogger.endHandleOperation();
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
                        final String propertyName = currentAttribute.getName();
                        final Object propertyValue = XFormsProperties.parseProperty(propertyName, currentAttribute.getValue());
                        if (propertyValue != null) {
                            nonDefaultProperties.put(currentAttribute.getName(), propertyValue);
                        } else {
                            indentedLogger.logWarning("", "ignoring global property", "name", propertyName);
                        }
                    }
                }
            }
            // Properties on xforms:model elements
            for (final Map.Entry<String, Document> currentEntry: modelDocuments.entrySet()) {
                final Document currentModelDocument = currentEntry.getValue();
                for (Iterator j = currentModelDocument.getRootElement().attributeIterator(); j.hasNext();) {
                    final Attribute currentAttribute = (Attribute) j.next();
                    if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(currentAttribute.getNamespaceURI())) {
                        final String propertyName = currentAttribute.getName();
                        final Object propertyValue = XFormsProperties.parseProperty(propertyName, currentAttribute.getValue());
                        if (propertyValue != null) {
                            // Only take the first occurrence into account, and make sure the property is supported
                            if (nonDefaultProperties.get(propertyName) == null && XFormsProperties.getPropertyDefinition(propertyName) != null)
                                nonDefaultProperties.put(propertyName, propertyValue);
                        } else {
                            indentedLogger.logWarning("", "ignoring property on xforms:model element", "name", propertyName);
                        }
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
                allowedExternalEvents.add(st.nextToken());
            }
        }
    }

    private void extractControlsModelsComponents(PropertyContext pipelineContext, Element staticStateElement, IdGenerator idGenerator) {

        final Configuration xpathConfiguration = new Configuration();

        // Get top-level models from static state document
        {
            final List modelsElements = staticStateElement.elements(XFormsConstants.XFORMS_MODEL_QNAME);
            modelDocuments.clear();

            // FIXME: we don't get a System ID here. Is there a simple solution?
            int modelsCount = 0;
            for (Iterator i = modelsElements.iterator(); i.hasNext(); modelsCount++) {
                final Element modelElement = (Element) i.next();
                // Copy the element because we may need it in staticStateDocument for encoding
                final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);
                addModelDocument(modelElement.attributeValue("id"), modelDocument);
            }

            indentedLogger.logDebug("", "created top-level model documents", "count", Integer.toString(modelsCount));
        }

        // Get controls document
        {
            // Create document
            controlsDocument = Dom4jUtils.createDocument();
            final Element controlsElement = Dom4jUtils.createElement("controls");
            controlsDocument.setRootElement(controlsElement);

            // Find all top-level controls
            int topLevelControlsCount = 0;
            for (Object o: staticStateElement.elements()) {
                final Element currentElement = (Element) o;
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

            indentedLogger.logDebug("", "created controls document", "top-level controls count", Integer.toString(topLevelControlsCount));
        }

        // Extract models nested within controls
        {
            final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(controlsDocument, null, xpathConfiguration);
            final List<Document> extractedModels = extractNestedModels(pipelineContext, controlsDocumentInfo, false, locationData);
            indentedLogger.logDebug("", "created nested model documents", "count", Integer.toString(extractedModels.size()));
            for (final Document currentModelDocument: extractedModels) {
                addModelDocument(currentModelDocument.getRootElement().attributeValue("id"), currentModelDocument);
            }
        }

        // Extract components
        xblBindings = new XBLBindings(indentedLogger, this, idGenerator, metadata.namespaceMappings, staticStateElement);
    }

    /**
     * Register a model document. Used by this and XBLBindings.
     *
     * @param prefixedId        prefixed id of the model
     * @param modelDocument     model document
     */
    public void addModelDocument(String prefixedId, Document modelDocument) {
        modelDocuments.put(prefixedId, modelDocument);
    }

    public void extractXFormsScripts(PropertyContext pipelineContext, DocumentWrapper documentInfo, String prefix) {

        // TODO: Not sure why we actually extract the scripts: we could just keep pointers on them, right? There is
        // probably not a notable performance if any at all, especially since this is needed at page generation time
        // only.
 
        final String xpathExpression = "/descendant-or-self::xxforms:script[not(ancestor::xforms:instance) and exists(@id)]";

        final List scripts = XPathCache.evaluate(pipelineContext, documentInfo, xpathExpression,
                BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        if (scripts.size() > 0) {
            if (xxformsScripts == null)
                xxformsScripts = new HashMap<String, String>();
            for (Object script: scripts) {
                final NodeInfo currentNodeInfo = (NodeInfo) script;
                final Element scriptElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                // Remember script content
                xxformsScripts.put(prefix + scriptElement.attributeValue("id"), scriptElement.getStringValue());
            }
        }
    }

    public String getUUID() {
        return uuid;
    }

    /**
     * Get a serialized static state. If an encodedStaticState was provided during restoration, return that. Otherwise,
     * return a serialized static state computed from models, instances, and XHTML documents.
     *
     * @param pipelineContext   current PropertyContext
     * @return                  serialized static sate
     */
    public String getEncodedStaticState(PropertyContext pipelineContext) {

        if (!initialized) {

            final Element rootElement = staticStateDocument.getRootElement();

            if (rootElement.element("instances") != null)
                throw new IllegalStateException("Element instances already present in static state.");

            // TODO: if staticStateDocument will contains XHTML document, don't store controls and models in there

            // Handle XHTML document if needed (for noscript mode)
            if (xhtmlDocument != null && rootElement.element(XMLConstants.XHTML_HTML_QNAME) == null) {
                // Add document
                final Document document = TransformerUtils.saxStoreToDom4jDocument(xhtmlDocument);
                staticStateDocument.getRootElement().add(document.getRootElement().detach());
            }

            // Remember versioned paths
            if (versionedPathMatchers != null && versionedPathMatchers.size() > 0) {
                final Element matchersElement = rootElement.addElement("matchers");
                for (final URLRewriterUtils.PathMatcher pathMatcher: versionedPathMatchers) {
                    matchersElement.add(pathMatcher.serialize());
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

    /**
     * Return all instance containers of the specified model.
     *
     * @param modelPrefixedId       model prefixed id
     * @return                      container elements
     */
    public List<Element> getInstanceContainers(String modelPrefixedId) {
        
        // Find model document
        final Document modelDocument = modelDocuments.get(modelPrefixedId);

        // Return all containers
        return Dom4jUtils.elements(modelDocument.getRootElement(), XFormsConstants.XFORMS_INSTANCE_QNAME);
    }

    /**
     * Whether the noscript mode is enabled.
     *
     * @return true iif noscript mode is enabled
     */
    public boolean isNoScript() {
        return getBooleanProperty(XFormsProperties.NOSCRIPT_PROPERTY);
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

    public Map<String, Document> getModelDocuments() {
        return modelDocuments;
    }

    public Map<String, String> getScripts() {
        return xxformsScripts;
    }

    public boolean isSeparateDeployment() {
        return isSeparateDeployment;
    }

    public String getRequestContextPath() {
        return requestContextPath;
    }

    public Set<String> getAllowedExternalEvents() {
        return allowedExternalEvents;
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

    public Map<String, Object> getNonDefaultProperties() {
        return nonDefaultProperties;
    }
    
    public Object getProperty(String propertyName) {
        final Object documentProperty = nonDefaultProperties.get(propertyName);
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
            return documentProperty;
        } else {
            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
            return (Boolean) propertyDefinition.getDefaultValue();
        }
    }

    public int getIntegerProperty(String propertyName) {
        final Integer documentProperty = (Integer) nonDefaultProperties.get(propertyName);
        if (documentProperty != null) {
            return documentProperty;
        } else {
            final XFormsProperties.PropertyDefinition propertyDefinition = XFormsProperties.getPropertyDefinition(propertyName);
            return (Integer) propertyDefinition.getDefaultValue();
        }
    }

    public List<XFormsEventHandler> getEventHandlers(String observerPrefixedId) {
        return eventHandlersMap.get(observerPrefixedId);
    }

    public boolean observerHasHandlerForEvent(String observerPrefixedId, String eventName) {
        final List<XFormsEventHandler> handlers = getEventHandlers(observerPrefixedId);
        if (handlers == null || handlers.isEmpty())
            return false;
        for (XFormsEventHandler handler: handlers) {
            if (handler.isAllEvents() || handler.getEventNames().contains(eventName))
                return true;
        }
        return false;
    }

    public Map<String, ControlInfo> getRepeatControlInfoMap() {
        return controlTypes.get("repeat");
    }

    public Element getControlElement(String prefixedId) {
        final ControlInfo controlInfo = controlInfoMap.get(prefixedId);
        return (controlInfo == null) ? null : controlInfo.element;
    }

    public int getControlPosition(String prefixedId) {
        final ControlInfo controlInfo = controlInfoMap.get(prefixedId);
        return (controlInfo == null) ? -1 : controlInfo.index;
    }

    public boolean hasNodeBinding(String prefixedId) {
        final ControlInfo controlInfo = controlInfoMap.get(prefixedId);
        return (controlInfo == null) ? false : controlInfo.hasNodeBinding;
    }

    public Element getLabelElement(String prefixedId) {
        return labelsMap.get(prefixedId);
    }

    public Element getHelpElement(String prefixedId) {
        return helpsMap.get(prefixedId);
    }

    public Element getHintElement(String prefixedId) {
        return hintsMap.get(prefixedId);
    }

    public Element getAlertElement(String prefixedId) {
        return alertsMap.get(prefixedId);
    }

    /**
     * Statically check whether a control is a value control.
     *
     * @param controlEffectiveId    prefixed id or effective id of the control
     * @return                      true iif the control is a value control
     */
    public boolean isValueControl(String controlEffectiveId) {
        final ControlInfo controlInfo = controlInfoMap.get(XFormsUtils.getPrefixedId(controlEffectiveId));
        return (controlInfo != null) && controlInfo.isValueControl;
    }

    /**
     * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
     * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
     * as the mapping is considered transient and not sharable among pages.
     *
     * @param prefix
     * @param element       Element to get namespace mapping for
     * @return              Map<String prefix, String uri>
     */
    public Map<String, String> getNamespaceMappings(String prefix, Element element) {
        final String id = element.attributeValue("id");
        if (id != null) {
            // There is an id attribute
            final String prefixedId = (prefix != null) ? prefix + id : id; 
            final Map<String, String> cachedMap = metadata.namespaceMappings.get(prefixedId);
            if (cachedMap != null) {
                return cachedMap;
            } else {
                indentedLogger.logDebug("", "namespace mappings not cached",
                        "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element));
                return Dom4jUtils.getNamespaceContextNoDefault(element);
            }
        } else {
            // No id attribute
            indentedLogger.logDebug("", "namespace mappings not available because element doesn't have an id attribute",
                    "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element));
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
        return (itemsInfoMap != null) ? itemsInfoMap.get(controlPrefixedId) : null;
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
        final Map<String, ControlInfo> mapForId = attributeControls.get(prefixedForAttribute);
        return (mapForId != null) ? mapForId.get(attributeName) : null;
    }

    /**
     * Return XBL bindings information.
     *
     * @return XBL bindings information
     */
    public XBLBindings getXBLBindings() {
        return xblBindings;
    }

    /**
     * Perform static analysis on this document if not already done.
     *
     * @param propertyContext   current pipeline context
     * @return                  true iif analysis was just performed in this call
     */
    public synchronized boolean analyzeIfNecessary(final PropertyContext propertyContext) {
        if (!isAnalyzed) {
            final long startTime = indentedLogger.isDebugEnabled() ? System.currentTimeMillis() : 0;

            controlTypes = new HashMap<String, Map<String, ControlInfo>>();
            eventNames = new HashSet<String>();
            eventHandlersMap = new HashMap<String, List<XFormsEventHandler>>();
            eventHandlerAncestorsMap = new HashMap<String, String>();
            keyHandlers = new ArrayList<XFormsEventHandler>();
            controlInfoMap = new HashMap<String, ControlInfo>();
            repeatChildrenMap = new HashMap<String, List<String>>();

            // Iterate over main static controls tree
            final Configuration xpathConfiguration = new Configuration();
            final StringBuilder repeatHierarchyStringBuffer = new StringBuilder(1024);
            final Stack<String> repeatAncestorsStack = new Stack<String>();

            analyzeComponentTree(propertyContext, xpathConfiguration, "", controlsDocument.getRootElement(), repeatHierarchyStringBuffer, repeatAncestorsStack);

            if (xxformsScripts != null && xxformsScripts.size() > 0)
                indentedLogger.logDebug("", "extracted script elements", "count", Integer.toString(xxformsScripts.size()));

            // Finalize repeat hierarchy
            repeatHierarchyString = repeatHierarchyStringBuffer.toString();

            // Iterate over models to extract event handlers and scripts
            for (final Map.Entry<String, Document> currentEntry: modelDocuments.entrySet()) {
                final String modelPrefixedId = currentEntry.getKey();
                final Document modelDocument = currentEntry.getValue();
                final DocumentWrapper modelDocumentInfo = new DocumentWrapper(modelDocument, null, xpathConfiguration);
                // NOTE: Say we don't want to exclude gathering event handlers within nested models, since this is a model
                extractEventHandlers(propertyContext, xpathConfiguration, modelDocumentInfo, XFormsUtils.getEffectiveIdPrefix(modelPrefixedId), false);
                extractXFormsScripts(propertyContext, modelDocumentInfo, XFormsUtils.getEffectiveIdPrefix(modelPrefixedId));
            }

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug("", "performed static analysis",
                            "time", Long.toString(System.currentTimeMillis() - startTime),
                            "controls", Integer.toString(controlInfoMap.size()));

            }

            // Once analysis is done, some state can be freed
            xblBindings.freeTransientState();

            isAnalyzed = true;
            return true;
        } else {
            indentedLogger.logDebug("", "static analysis already available");
            return false;
        }
    }

    private void extractEventHandlers(PropertyContext propertyContext, Configuration xpathConfiguration, DocumentInfo documentInfo, String prefix, boolean isControls) {

        // Register event handlers on any element which has an id or an observer attribute. This also allows
        // registering event handlers on XBL components. This follows the semantics of XML Events.

        // Special work is done to annotate handlers children of bound nodes, because that annotation is not done
        // in XFormsAnnotatorContentHandler. Maybe it should be done there?

        // Top-level handlers within controls are also handled. If they don't have an @ev:observer attribute, they
        // are assumed to listen to a virtual element around all controls. 

        // NOTE: Placing a listener on say a <div> element won't work at this point. Listeners have to be placed within
        // elements which have a representation in the compact component tree.
        // UPDATE: This is right to a point: things should work for elements with @ev:observer, and element which have a
        // compact tree ancestor element. Will not work for top-level handlers without @ev:observer though. Check more!

        // Two expressions depending on whether handlers within models are excluded or not
        final String xpathExpression = isControls ?
                "//*[@ev:event and not(ancestor::xforms:instance) and not(ancestor::xforms:model) and (parent::*/@id or @ev:observer or /* is ..)]" :
                "//*[@ev:event and not(ancestor::xforms:instance) and (parent::*/@id or @ev:observer or /* is ..)]";

        // Get all candidate elements
        final List actionHandlers = XPathCache.evaluate(propertyContext, documentInfo,
                xpathExpression, BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        final XBLBindings.Scope innerScope = xblBindings.getResolutionScopeByPrefix(prefix); // if at top-level, prefix is ""
        final XBLBindings.Scope outerScope = (prefix.length() == 0) ? xblBindings.getResolutionScopeByPrefix("") : xblBindings.getResolutionScopeByPrefixedId(innerScope.scopeId);

        // Check all candidate elements
        for (Object actionHandler: actionHandlers) {
            final NodeInfo currentNodeInfo = (NodeInfo) actionHandler;

            if (currentNodeInfo instanceof NodeWrapper) {
                final Element actionElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                if (XFormsActions.isActionName(actionElement.getNamespaceURI(), actionElement.getName())) {
                    // This is a known action name

                    final Element newActionElement;
                    final String evObserversStaticIds = actionElement.attributeValue(XFormsConstants.XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME);

                    final String parentStaticId;
                    if (isControls) {
                        // Analyzing controls

                        if (isControlsTopLevelHandler(actionElement)) {
                            // Specially handle #document static id for top-level handlers
                            parentStaticId = XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID;
                        } else {
                             // Nested handler
                            parentStaticId = actionElement.getParent().attributeValue("id");
                        }

                        if (parentStaticId != null) {
                            final String parentPrefixedId = prefix + parentStaticId;
                            if (xblBindings.hasBinding(parentPrefixedId)) {
                                // Parent is a bound node, so we found an action handler which is a child of a bound element

                                // Annotate handler
                                final XBLBindings.Scope bindingScope = xblBindings.getResolutionScopeByPrefixedId(parentPrefixedId);
                                final XFormsConstants.XXBLScope startScope = innerScope.equals(bindingScope) ? XFormsConstants.XXBLScope.inner : XFormsConstants.XXBLScope.outer;
                                newActionElement = xblBindings.annotateHandler(actionElement, prefix, innerScope, outerScope, startScope).getRootElement();

                                // Extract scripts in the handler
                                final DocumentWrapper handlerWrapper = new DocumentWrapper(newActionElement.getDocument(), null, xpathConfiguration);
                                extractXFormsScripts(propertyContext, handlerWrapper, prefix);

                            } else if (controlInfoMap.containsKey(parentPrefixedId)) {
                                // Parent is a control but not a bound node
                                newActionElement = actionElement;
                            } else if (isControlsTopLevelHandler(actionElement)) {
                                // Handler is a top-level handler
                                newActionElement = actionElement;
                            } else {
                                // Neither
                                newActionElement = null;
                            }
                        } else if (evObserversStaticIds != null) {
                            // There is no parent static id but an explicit @ev:observer
                            // TODO: if the element is a descendant of a bound node, it must be ignored
                            newActionElement = actionElement;
                        } else {
                            // No parent id and no @ev:observer, so we ignore the handler
                            newActionElement = null;
                        }
                    } else {
                        // Analyzing models
                        newActionElement = actionElement;
                        parentStaticId = actionElement.getParent().attributeValue("id");
                    }

                    // Register action handler
                    if (newActionElement != null) {
                        // If possible, find closest ancestor observer for XPath context evaluation

                        final String ancestorObserverStaticId; {
                            final Element ancestorObserver = findAncestorObserver(actionElement);
                            if (ancestorObserver != null) {
                                assert ancestorObserver.attributeValue("id") != null : "ancestor observer must have an id";
                                ancestorObserverStaticId = ancestorObserver.attributeValue("id");
                            } else if (isControls && isControlsTopLevelHandler(actionElement)) {
                                // Specially handle #document static id for top-level handlers
                                ancestorObserverStaticId = XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID;
                            } else {
                                ancestorObserverStaticId = null;
                            }
                        }

                        // The observers to which this handler is attached might not be in the same scope. Try to find
                        // that scope.
                        final String observersPrefix;
                        if (evObserversStaticIds != null) {
                            // Explicit ev:observer, prefix might be different
                            final XBLBindings.Scope actionScope = xblBindings.getResolutionScopeByPrefixedId(prefix + newActionElement.attributeValue("id"));
                            observersPrefix = (actionScope != null) ? actionScope.getFullPrefix() : prefix;
                        } else {
                            // Parent is observer and has the same prefix
                            observersPrefix = prefix;
                        }

                        // Create and register the handler
                        final XFormsEventHandlerImpl eventHandler = new XFormsEventHandlerImpl(prefix, newActionElement, parentStaticId, ancestorObserverStaticId);
                        registerActionHandler(eventHandler, observersPrefix);
                    }
                }
            }
        }
    }

    private boolean isControlsTopLevelHandler(Element actionElement) {
        // Structure is:
        // <controls>
        //   <xforms:action .../>
        //   ...
        // </controls>
        return actionElement.getParent() == actionElement.getDocument().getRootElement();
    }

    private Element findAncestorObserver(Element actionElement) {
        // Recurse until we find an element which is an event observer
        Element currentAncestor = actionElement.getParent();
        while (currentAncestor != null) {
            if (isEventObserver(currentAncestor))
                return currentAncestor;
            currentAncestor = currentAncestor.getParent();
        }

        return null;
    }

    private boolean hasAncestorBinding(String prefix, Element element) {

        if (xblBindings == null)
            return false;

        // Recurse until we find an element which is a binding
        Element currentAncestor = element.getParent();
        while (currentAncestor != null) {
            if (xblBindings.hasBinding(prefix + currentAncestor.attributeValue("id"))) {
                return true;
            }
            currentAncestor = currentAncestor.getParent();
        }

        return false;
    }

    /**
     * Return true if the given element is an event observer. Must return true for controls, components, xforms:model,
     * xforms:instance, xforms:submission.
     *
     * @param element       element to check
     * @return              true iif the element is an event observer
     */
    private boolean isEventObserver(Element element) {

        // Whether this is a built-in control or a component
        if (XFormsControlFactory.isBuiltinControl(element.getNamespaceURI(), element.getName()) || xblBindings.isComponent(element.getQName())) {
            return true;
        }

        final String localName = element.getName();
        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(element.getNamespaceURI())
                && ("model".equals(localName) || "instance".equals(localName) || "submission".equals(localName))) {
            return true;
        }

        return false;
    }

    public void analyzeComponentTree(final PropertyContext propertyContext, final Configuration xpathConfiguration,
                                     final String prefix, Element startElement, final StringBuilder repeatHierarchyStringBuffer,
                                     final Stack<String> repeatAncestorsStack) {

        final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(startElement.getDocument(), null, xpathConfiguration);

        // Extract scripts for this tree of controls
        extractXFormsScripts(propertyContext, controlsDocumentInfo, prefix);

        // Visit tree
        visitAllControlStatic(startElement, new XFormsStaticState.ControlElementVisitorListener() {

            public void startVisitControl(Element controlElement, String controlStaticId) {

                // Check for mandatory id
                if (controlStaticId == null)
                    throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName(), locationData);

                // Prefixed id
                final String controlPrefixedId = prefix + controlStaticId;

                // Gather control name
                final String controlName = controlElement.getName();
                final String controlURI = controlElement.getNamespaceURI();

                final LocationData locationData = new ExtendedLocationData((LocationData) controlElement.getData(), "gathering static control information", controlElement);

                // If element is not built-in, check XBL and generate shadow content if needed
                xblBindings.processElementIfNeeded(propertyContext, indentedLogger, controlElement, controlPrefixedId, locationData,
                        controlsDocumentInfo, xpathConfiguration, prefix, repeatHierarchyStringBuffer, repeatAncestorsStack);

                // Check for mandatory and optional bindings
                final boolean hasNodeBinding;
                {
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

                    hasNodeBinding = hasBind || hasRef || hasNodeset;
                }

                // Create and index static control information

                final ControlInfo parentRepeatControlInfo;
                if (repeatAncestorsStack.size() > 0) {
                    parentRepeatControlInfo = controlInfoMap.get(repeatAncestorsStack.peek());
                } else {
                    parentRepeatControlInfo = null;
                }

                final ControlInfo info = new ControlInfo(controlPrefixedId, controlElement, controlInfoMap.size() + 1, hasNodeBinding,
                        XFormsControlFactory.isValueControl(controlURI, controlName), parentRepeatControlInfo);
                controlInfoMap.put(controlPrefixedId, info);
                {
                    Map<String, ControlInfo> controlsMap = controlTypes.get(controlName);
                    if (controlsMap == null) {
                        controlsMap = new LinkedHashMap<String, ControlInfo>();
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
                            final String parentRepeatId = repeatAncestorsStack.peek();
                            repeatHierarchyStringBuffer.append(' ');
                            repeatHierarchyStringBuffer.append(parentRepeatId);
                        }
                    }
                    // Find repeat children
                    {
                        if (repeatAncestorsStack.size() > 0) {
                            // If we have a parent, tell the parent that it has a child
                            final String parentRepeatId = repeatAncestorsStack.peek();
                            List<String> parentRepeatList = repeatChildrenMap.get(parentRepeatId);
                            if (parentRepeatList == null) {
                                parentRepeatList = new ArrayList<String>();
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
                    // don't check things like event handlers. Also check for AVTs ion @class and @style.
                    final boolean hasNonStaticItem = (Boolean) XPathCache.evaluateSingle(propertyContext, controlNodeInfo,
                            "exists(./(xforms:choices | xforms:item | xforms:itemset)//xforms:*[@ref or @nodeset or @bind or @value or (@class, @style)[contains(., '{')]])", BASIC_NAMESPACE_MAPPINGS,
                            null, null, null, null, locationData);

                    // Remember information
                    if (itemsInfoMap == null)
                        itemsInfoMap = new HashMap<String, ItemsInfo>();
                    itemsInfoMap.put(controlPrefixedId, new XFormsStaticState.ItemsInfo(controlName.equals("select"), hasNonStaticItem));
//                } else if (controlName.equals("case")) {
                    // TODO: Check that xforms:case is within: switch
//                    if (!(currentControlsContainer.getName().equals("switch")))
//                        throw new ValidationException("xforms:case with id '" + effectiveControlId + "' is not directly within an xforms:switch container.", xformsControl.getLocationData());
                } else if ("attribute".equals(controlName)) {
                    // Special indexing of xxforms:attribute controls
                    final String prefixedForAttribute = prefix + controlElement.attributeValue("for");
                    final String nameAttribute = controlElement.attributeValue("name");
                    Map<String, ControlInfo> mapForId;
                    if (attributeControls == null) {
                        attributeControls = new HashMap<String, Map<String, ControlInfo>>();
                        mapForId = new HashMap<String, ControlInfo>();
                        attributeControls.put(prefixedForAttribute, mapForId);
                    } else {
                        mapForId = attributeControls.get(prefixedForAttribute);
                        if (mapForId == null) {
                            mapForId = new HashMap<String, ControlInfo>();
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

        // Extract event handlers for this tree of controls
        // NOTE: Do this after analysing controls above so that XBL bindings are available for detection of nested event handlers.
        extractEventHandlers(propertyContext, xpathConfiguration, controlsDocumentInfo, prefix, true);

        // Gather label, hint, help, alert information
        {
            // Search LHHA elements that either:
            //
            // o have @for attribute
            // o are the child of an xforms:* or xxforms:* element that has an id
            final List lhhaElements = XPathCache.evaluate(propertyContext, controlsDocumentInfo,
                "//(xforms:label | xforms:help | xforms:hint | xforms:alert)[not(ancestor::xforms:instance) and exists(@for | parent::xforms:*/@id | parent::xxforms:*/@id)]", BASIC_NAMESPACE_MAPPINGS,
                null, null, null, null, locationData);

            int lhhaCount = 0;
            for (Iterator i = lhhaElements.iterator(); i.hasNext();) {
                final NodeInfo currentNodeInfo = (NodeInfo) i.next();
                final Element lhhaElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                final Element parentElement = lhhaElement.getParent();

                final String forAttribute = lhhaElement.attributeValue("for");
                final String controlPrefixedId;
                if (forAttribute == null || XFormsControlFactory.isCoreControl(parentElement.getNamespaceURI(), parentElement.getName())) {
                    // Element is directly nested in XForms element OR it has a @for attribute but is within a core control so we ignore the @for attribute
                    controlPrefixedId = prefix + lhhaElement.getParent().attributeValue("id");
                } else {
                    // Element has a @for attribute and is not within a core control
                    if (xblBindings != null) { // mmh, can bindings be null?

                        if (hasAncestorBinding(prefix, lhhaElement)) {
                            // Ignore content of bound elements
                            controlPrefixedId = null;
                        } else {
                            // Find prefixed id of control with assumption that it is in the same scope as the LHHA element
                            final XBLBindings.Scope lhhaScope = xblBindings.getResolutionScopeByPrefixedId(prefix + lhhaElement.attributeValue("id"));
                            controlPrefixedId = lhhaScope.getPrefixedIdForStaticId(forAttribute);
                        }
                    } else {
                        // Degenerate case where there are no XBL components
                        controlPrefixedId = prefix + forAttribute;
                    }
                }

                if (controlPrefixedId != null) {

                    lhhaCount++;

                    final String elementName = lhhaElement.getName();
                    if ("label".equals(elementName)) {
                        labelsMap.put(controlPrefixedId, lhhaElement);
                    } else if ("help".equals(elementName)) {
                        helpsMap.put(controlPrefixedId, lhhaElement);
                    } else if ("hint".equals(elementName)) {
                        hintsMap.put(controlPrefixedId, lhhaElement);
                    } else if ("alert".equals(elementName)) {
                        alertsMap.put(controlPrefixedId, lhhaElement);
                    }
                }
            }
            indentedLogger.logDebug("", "extracted label, help, hint and alert elements", "count", Integer.toString(lhhaCount));
        }

        // Gather online/offline information
        {
            {
                // Create list of all the documents to search
                final List<DocumentWrapper> documentInfos = new ArrayList<DocumentWrapper>(modelDocuments.size() + 1);
                for (final Map.Entry<String, Document> currenEntry: modelDocuments.entrySet()) {
                    final Document currentModelDocument = currenEntry.getValue();
                    documentInfos.add(new DocumentWrapper(currentModelDocument, null, xpathConfiguration));
                }
                documentInfos.add(controlsDocumentInfo);

                // Search for xxforms:offline which are not within instances
                for (final DocumentWrapper currentDocumentInfo: documentInfos) {
                    hasOfflineSupport |= (Boolean) XPathCache.evaluateSingle(propertyContext, currentDocumentInfo,
                            "exists(//xxforms:offline[not(ancestor::xforms:instance)])", BASIC_NAMESPACE_MAPPINGS,
                            null, null, null, null, locationData);

                    if (hasOfflineSupport) {
                        break;
                    }
                }
            }

            if (hasOfflineSupport) {
                // NOTE: We attempt to localize what triggers can cause, upon DOMActivate, xxforms:online, xxforms:offline and xxforms:offline-save actions
                final List onlineTriggerIds = XPathCache.evaluate(propertyContext, controlsDocumentInfo,
                    "distinct-values(for $handler in for $action in //xxforms:online return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                    "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id))", BASIC_NAMESPACE_MAPPINGS,
                    null, null, null, null, locationData);

                final List offlineTriggerIds = XPathCache.evaluate(propertyContext, controlsDocumentInfo,
                    "distinct-values(for $handler in for $action in //xxforms:offline return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                    "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id))", BASIC_NAMESPACE_MAPPINGS,
                    null, null, null, null, locationData);

                final List offlineSaveTriggerIds = XPathCache.evaluate(propertyContext, controlsDocumentInfo,
                    "distinct-values(for $handler in for $action in //xxforms:offline-save return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                    "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id))", BASIC_NAMESPACE_MAPPINGS,
                    null, null, null, null, locationData);

                offlineInsertTriggerIds = XPathCache.evaluate(propertyContext, controlsDocumentInfo,
                    "distinct-values(for $handler in for $action in //xforms:insert return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                    "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id))", BASIC_NAMESPACE_MAPPINGS,
                    null, null, null, null, locationData);

                final List offlineDeleteTriggerIds = XPathCache.evaluate(propertyContext, controlsDocumentInfo,
                    "distinct-values(for $handler in for $action in //xforms:delete return ($action/ancestor-or-self::*[@ev:event and tokenize(@ev:event, '\\s+') = 'DOMActivate'])[1]" +
                    "   return for $id in $handler/../descendant-or-self::xforms:trigger/@id return string($id))", BASIC_NAMESPACE_MAPPINGS,
                    null, null, null, null, locationData);

                for (Object onlineTriggerId: onlineTriggerIds) {
                    final String currentId = (String) onlineTriggerId;
                    addClasses(prefix + currentId, "xxforms-online");
                }

                for (Object offlineTriggerId: offlineTriggerIds) {
                    final String currentId = (String) offlineTriggerId;
                    addClasses(prefix + currentId, "xxforms-offline");
                }

                for (Object offlineSaveTriggerId: offlineSaveTriggerIds) {
                    final String currentId = (String) offlineSaveTriggerId;
                    addClasses(prefix + currentId, "xxforms-offline-save");
                }

                for (final String currentId: offlineInsertTriggerIds) {
                    addClasses(prefix + currentId, "xxforms-offline-insert");
                }

                for (Object offlineDeleteTriggerId: offlineDeleteTriggerIds) {
                    final String currentId = (String) offlineDeleteTriggerId;
                    addClasses(prefix + currentId, "xxforms-offline-delete");
                }
            }
        }
    }

    public static List<Document> extractNestedModels(PropertyContext pipelineContext, DocumentWrapper compactShadowTreeWrapper, boolean detach, LocationData locationData) {

        final List<Document> result = new ArrayList<Document>();

        final List modelElements = XPathCache.evaluate(pipelineContext, compactShadowTreeWrapper,
                "//xforms:model[not(ancestor::xforms:instance)]",
                BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        if (modelElements.size() > 0) {
            for (Object modelElement : modelElements) {
                final NodeInfo currentNodeInfo = (NodeInfo) modelElement;
                final Element currentModelElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentModelElement, detach);
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
            controlClasses = new HashMap<String, String>();
        final String currentClasses = controlClasses.get(controlPrefixedId);
        if (currentClasses == null) {
            // Set
            controlClasses.put(controlPrefixedId, classes);
        } else {
            // Append
            controlClasses.put(controlPrefixedId, currentClasses + ' ' + classes);
        }
    }
    
    public void appendClasses(StringBuilder sb, String prefixedId) {
        if ((controlClasses == null))
            return;

        if (sb.length() > 0)
            sb.append(' ');

        final String classes = controlClasses.get(prefixedId);
        if (classes != null)
            sb.append(classes);
    }

    public List<String> getOfflineInsertTriggerIds() {
        return offlineInsertTriggerIds;
    }

    /**
     * Returns whether there is any event handler registered anywhere in the controls for the given event name.
     *
     * @param eventName event name, like xforms-value-changed
     * @return          true if there is a handler, false otherwise
     */
    public boolean hasHandlerForEvent(String eventName) {
        // Check for #all as well as specific event
        return eventNames.contains(XFormsConstants.XXFORMS_ALL_EVENTS) || eventNames.contains(eventName);
    }

    public List<XFormsEventHandler> getKeyHandlers() {
        return keyHandlers;
    }

    /**
     * Statically create and register an event handler.
     *
     * @param newEventHandlerImpl           event handler implementation
     * @param observersPrefix               prefix of observers, e.g. "" or "foo$bar$"
     */
    public void registerActionHandler(XFormsEventHandlerImpl newEventHandlerImpl, String observersPrefix) {

        // Register event handler
        final String[] observersStaticIds = newEventHandlerImpl.getObserversStaticIds();
        if (observersStaticIds.length > 0) {
            // There is at least one observer
            for (final String currentObserverStaticId: observersStaticIds) {
                // NOTE: Handle special case of global id on containing document
                final String currentObserverPrefixedId
                        = XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID.equals(currentObserverStaticId)
                        ? currentObserverStaticId : observersPrefix + currentObserverStaticId;

                // Get handlers for observer
                final List<XFormsEventHandler> eventHandlersForObserver;
                {
                    final List<XFormsEventHandler> currentList = eventHandlersMap.get(currentObserverPrefixedId);
                    if (currentList == null) {
                        eventHandlersForObserver = new ArrayList<XFormsEventHandler>();
                        eventHandlersMap.put(currentObserverPrefixedId, eventHandlersForObserver);
                    } else {
                        eventHandlersForObserver = currentList;
                    }
                }

                // Add event handler
                eventHandlersForObserver.add(newEventHandlerImpl);
            }

            // Remember closest ancestor observer for all nested actions
            // This is used to find the closest ancestor control, which in turn is used to find the repeat hierarchy
            {
                final String prefix = newEventHandlerImpl.getPrefix();
                final String ancestorObserverPrefixedId = prefix + newEventHandlerImpl.getAncestorObserverStaticId();
                eventHandlerAncestorsMap.put(prefix + newEventHandlerImpl.getStaticId(), ancestorObserverPrefixedId);

                Dom4jUtils.visitSubtree(newEventHandlerImpl.getEventHandlerElement(), new Dom4jUtils.VisitorListener() {
                    public void startElement(Element element) {
                        final String id = element.attributeValue("id");
                        if (id != null)
                            eventHandlerAncestorsMap.put(prefix + id, ancestorObserverPrefixedId);
                    }

                    public void endElement(Element element) {}
                    public void text(Text text) {}
                });
            }

            // Remember all event names
            if (newEventHandlerImpl.isAllEvents()) {
                eventNames.add(XFormsConstants.XXFORMS_ALL_EVENTS);
            } else {
                for (final String eventName: newEventHandlerImpl.getEventNames()) {
                    eventNames.add(eventName);
                    // Remember specially keypress events (could have eventNames<String, List<XFormsEventHandlerImpl>)
                    // instead of separate list, if useful for more events
                    if (XFormsEvents.KEYPRESS.equals(eventName))
                        keyHandlers.add(newEventHandlerImpl);
                }
            }
        }
    }

    /**
     * Visit all the control elements without handling repeats or looking at the binding contexts. This is done entirely
     * statically. Only controls are visited, including grouping controls, leaf controls, and components.
     */
    private void visitAllControlStatic(Element startElement, ControlElementVisitorListener controlElementVisitorListener) {
        handleControlsStatic(controlElementVisitorListener, startElement);
    }

    private void handleControlsStatic(ControlElementVisitorListener controlElementVisitorListener, Element container) {
        for (Object o: container.elements()) {
            final Element currentControlElement = (Element) o;

            final String controlName = currentControlElement.getName();
            final String controlId = currentControlElement.attributeValue("id");

            if (XFormsControlFactory.isContainerControl(currentControlElement.getNamespaceURI(), controlName)) {
                // Handle XForms grouping controls
                controlElementVisitorListener.startVisitControl(currentControlElement, controlId);
                handleControlsStatic(controlElementVisitorListener, currentControlElement);
                controlElementVisitorListener.endVisitControl(currentControlElement, controlId);
            } else if (XFormsControlFactory.isCoreControl(currentControlElement.getNamespaceURI(), controlName) || xblBindings.isComponent(currentControlElement.getQName())) {
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
        private boolean isMultiple;
        private boolean hasNonStaticItem;

        public ItemsInfo(boolean isMultiple, boolean hasNonStaticItem) {
            this.isMultiple = isMultiple;
            this.hasNonStaticItem = hasNonStaticItem;
        }

        public boolean isMultiple() {
            return isMultiple;
        }

        public boolean hasNonStaticItem() {
            return hasNonStaticItem;
        }
    }

    public static class ControlInfo {
        public final String prefixedId;
        public final Element element;
        public final int index;
        public final boolean hasNodeBinding;
        public final boolean isValueControl;
        public final ControlInfo ancestorRepeat;

        public ControlInfo(String prefixedId, Element element, int index, boolean hasNodeBinding, boolean isValueControl, ControlInfo ancestorRepeat) {
            this.prefixedId = prefixedId;
            this.element = element;
            this.index = index;
            this.hasNodeBinding = hasNodeBinding;
            this.isValueControl = isValueControl;
            this.ancestorRepeat = ancestorRepeat;
        }
    }

    /**
     * Find the closest common ancestor repeat given two prefixed ids.
     *
     * @param prefixedId1   first control prefixed id
     * @param prefixedId2   second control prefixed id
     * @return              prefixed id of common ancestor repeat, or null if not found
     */
    public String findClosestCommonAncestorRepeat(String prefixedId1, String prefixedId2) {
        final List<String> ancestors1 = getAncestorRepeats(prefixedId1, null);
        final List<String> ancestors2 = getAncestorRepeats(prefixedId2, null);

        // If one of them has no ancestors, there is no common ancestor
        if (ancestors1.size() == 0 || ancestors2.size() == 0)
            return null;

        Collections.reverse(ancestors1);
        Collections.reverse(ancestors2);

        final Iterator<String> iterator1 = ancestors1.iterator();
        final Iterator<String> iterator2 = ancestors2.iterator();

        String result = null;
        while (iterator1.hasNext() && iterator2.hasNext()) {
            final String repeatId1 = iterator1.next();
            final String repeatId2 = iterator2.next();

            if (!repeatId1.equals(repeatId2))
                break;

            result = repeatId1;
        }
        
        return result;
    }

    /**
     * Get prefixed ids of all of the start control's repeat ancestors, stopping at endPrefixedId if not null. If
     * endPrefixedId is a repeat, it is excluded. If the source doesn't exist, return the empty list.
     *
     * @param startPrefixedId   prefixed id of start control or start action within control
     * @param endPrefixedId     prefixed id of end repeat, or null
     * @return                  list of prefixed id from leaf to root, or the empty list
     */
    public List<String> getAncestorRepeats(String startPrefixedId, String endPrefixedId) {

        // Try control infos
        ControlInfo controlInfo = controlInfoMap.get(startPrefixedId);
        if (controlInfo == null) {
            // Not found, so try actions
            final String newStartPrefixedId = eventHandlerAncestorsMap.get(startPrefixedId);
            controlInfo = controlInfoMap.get(newStartPrefixedId);
        }

        // Simple case where source doesn't exist
        if (controlInfo == null)
            return Collections.emptyList();

        // Simple case where there is no ancestor repeat
        ControlInfo repeatControlInfo = controlInfo.ancestorRepeat;
        if (repeatControlInfo == null)
            return Collections.emptyList();

        // At least one ancestor repeat
        final List<String> result = new ArrayList<String>();
        // Go until there are no more ancestors OR we find the boundary repeat
        while (repeatControlInfo != null && (endPrefixedId == null || !endPrefixedId.equals(repeatControlInfo.prefixedId)) ) {
            result.add(repeatControlInfo.prefixedId);
            repeatControlInfo = repeatControlInfo.ancestorRepeat;
        }
        return result;
    }

    public SAXStore.Mark getElementMark(String prefixedId) {
        return metadata.marks.get(prefixedId);
    }
}
