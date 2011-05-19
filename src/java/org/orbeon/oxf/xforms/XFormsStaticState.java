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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.ResourceNotFoundException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.analysis.*;
import org.orbeon.oxf.xforms.analysis.controls.*;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandler;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.script.ServerScript;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.tinytree.TinyBuilder;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.*;

/**
 * This class encapsulates containing document static state information.
 *
 * All the information contained here must be constant, and never change as the XForms engine operates on a page. This
 * information can be shared between multiple running copies of an XForms pages.
 *
 * NOTE: This code will have to change a bit if we move towards TinyTree to store the static state.
 */
public class XFormsStaticState implements XMLUtils.DebugXML {

    public static final String LOGGING_CATEGORY = "analysis";
    private static final Logger logger = LoggerFactory.createLogger(XFormsStaticState.class);
    private final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

    public static final int DIGEST_LENGTH = 32; // 128-bit MD5 digest -> 32 hex digits

    private String digest;                          // digest of the static state data
    private String encodedStaticState;              // encoded state
    private boolean encodedStaticStateAvailable;    // whether the encoded state is available
    private Document staticStateDocument;           // if present, stored there temporarily only until getEncodedStaticState() is called and encodedStaticState is produced

    // Global static-state Saxon Configuration
    // Would be nice to have one per static state maybe, but expressions in XPathCache are shared so NamePool must be shared
    private static Configuration xpathConfiguration = XPathCache.getGlobalConfiguration();
    private DocumentWrapper documentWrapper = new DocumentWrapper(Dom4jUtils.createDocument(), null, xpathConfiguration);

    private Document controlsDocument;                                      // controls document

    // Static representation of models and instances
    private LinkedHashMap<XBLBindings.Scope, List<Model>> modelsByScope = new LinkedHashMap<XBLBindings.Scope, List<Model>>();
    private Map<String, Model> modelsByPrefixedId = new LinkedHashMap<String, Model>();
    private Map<String, Model> modelByInstancePrefixedId = new LinkedHashMap<String, Model>();

    private Map<String, Script> scripts;

    private final Map<String, Object> nonDefaultProperties = new HashMap<String, Object>(); // Map of property name to property value (String, Integer, Boolean)
    private final Set<String> allowedExternalEvents = new HashSet<String>();        // Set<String eventName>

    private LocationData locationData;

    private Metadata metadata;

    // Event handlers
    private Set<String> eventNames;                                         // used event names
    private Map<String, List<XFormsEventHandler>> eventHandlersMap;         // Map<String observerPrefixedId, List<XFormsEventHandler> eventHandler>: for all observers with handlers
    private Map<String, String> eventHandlerAncestorsMap;                   // Map<String actionPrefixId, String ancestorPrefixedId>
    private List<XFormsEventHandler> keyHandlers;

    // Controls
    private Map<String, Map<String, ElementAnalysis>> controlTypes;         // Map<String type, Map<String prefixedId, ElementAnalysis>>
    private Map<String, Set<QName>> controlAppearances;                     // Map<String type, Set<QName appearance>>
    private Map<String, ElementAnalysis> controlAnalysisMap;                // Map<String controlPrefixedId, ElementAnalysis>: for all controls

    // xforms:repeat
    private String repeatHierarchyString;                                   // contains comma-separated list of space-separated repeat prefixed id and ancestor if any

    // XXFormsAttributeControl
    private Map<String, Map<String, AttributeControl>> attributeControls;        // Map<String forPrefixedId, Map<String name, AttributeControl control>>

    // Commonly used properties (use getter to access them)
    private boolean propertiesRead;
    private boolean isNoscript;
    private boolean isXPathAnalysis;

    // Components
    private XBLBindings xblBindings;

    public static final NamespaceMapping BASIC_NAMESPACE_MAPPING;
    static {
        final Map basicMapping = new HashMap<String, String>();

        basicMapping.put(XFormsConstants.XFORMS_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        basicMapping.put(XFormsConstants.XXFORMS_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
        basicMapping.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        basicMapping.put(XMLConstants.XHTML_PREFIX, XMLConstants.XHTML_NAMESPACE_URI);

        BASIC_NAMESPACE_MAPPING = new NamespaceMapping(basicMapping);
    }

    /**
     * Create static state object from a Document. This constructor is used when creating an initial static state upon
     * producing an XForms page.
     *
     * @param staticStateDocument   document containing the static state, may be modified by this constructor and must be discarded afterwards by the caller
     * @param digest                digest of the static state document
     * @param metadata              metadata or null if not available
     */
    public XFormsStaticState(Document staticStateDocument, String digest, Metadata metadata) {
        // Set XPath configuration
        initialize(staticStateDocument, digest, metadata, null);
    }

    /**
     * Create static state object from an encoded version. This constructor is used when restoring a static state from
     * a serialized form.
     *
     * @param staticStateDigest     digest of the static state if known
     * @param encodedStaticState    encoded static state (digest + serialized XML)
     */
    public XFormsStaticState(String staticStateDigest, String encodedStaticState) {

        // Decode encodedStaticState into staticStateDocument
        final Document staticStateDocument = XFormsUtils.decodeXML(encodedStaticState);

        // Initialize
        initialize(staticStateDocument, staticStateDigest, null, encodedStaticState);

        assert (staticStateDigest != null) && isServerStateHandling() || (staticStateDigest == null) && isClientStateHandling();
    }

    public Configuration getXPathConfiguration() {
        return xpathConfiguration;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    /**
     * Initialize. Either there is:
     *
     * o staticStateDocument, topLevelStaticIds, namespaceMap, and optional xhtmlDocument
     * o staticStateDocument and encodedStaticState
     *
     * @param staticStateDocument   document containing the static state, may be modified by this constructor and must be discarded afterwards by the caller
     * @param digest                digest of the static state document
     * @param metadata              metadata or null if not available
     * @param encodedStaticState    existing serialization of static state, null if not available
     */
    private void initialize(Document staticStateDocument, String digest, Metadata metadata, String encodedStaticState) {

        // Remember digest
        this.digest = digest;

        // Extract static state
        extract(staticStateDocument, metadata, encodedStaticState);

        // Analyze
        analyze();

        // Debug if needed
        if (XFormsProperties.getDebugLogXPathAnalysis())
            dumpAnalysis();
    }

    private void extract(Document staticStateDocument, Metadata metadata, String encodedStaticState) {

        indentedLogger.startHandleOperation("", "extracting static state");

        final Element staticStateElement = staticStateDocument.getRootElement();

        // TODO: if staticStateDocument contains XHTML document, get controls and models from there

        // Extract top-level information
        {
            final String systemId = staticStateElement.attributeValue("system-id");
            if (systemId != null) {
                locationData = new LocationData(systemId, Integer.parseInt(staticStateElement.attributeValue("line")), Integer.parseInt(staticStateElement.attributeValue("column")));
            }
        }

        // Recompute namespace mappings if needed
        if (metadata == null) {
            final IdGenerator idGenerator;
            {
                // Use the last id used for id generation. During state restoration, XBL components must start with this id.
                final Element currentIdElement = staticStateElement.element(XFormsExtractorContentHandler.LAST_ID_QNAME);
                assert currentIdElement != null;
                final String lastId = XFormsUtils.getElementStaticId(currentIdElement);
                assert lastId != null;
                idGenerator = new IdGenerator(Integer.parseInt(lastId));
            }
            this.metadata = new Metadata(idGenerator);
            try {
                // Recompute from staticStateDocument
                // TODO: Can there be in this case a nested xhtml:html element, thereby causing duplicate id exceptions?
                TransformerUtils.sourceToSAX(new DocumentSource(staticStateDocument), new XFormsAnnotatorContentHandler(this.metadata));
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            // Use map that was passed
            this.metadata = metadata;
        }

        final List<Element> topLevelModelsElements = Dom4jUtils.elements(staticStateElement, XFormsConstants.XFORMS_MODEL_QNAME);

        // Extract properties information
        // Do this first so that e.g. extracted models know about properties
        extractProperties(staticStateElement);

        // Extract controls, models and components documents
        extractControlsModelsComponents(staticStateElement, topLevelModelsElements);

        if (encodedStaticState != null) {
            // Static state is fully initialized
            this.encodedStaticState = encodedStaticState;
            encodedStaticStateAvailable = true;
        } else {
            // Remember this temporarily only if the encoded state is not yet known
            this.staticStateDocument = staticStateDocument;
            encodedStaticStateAvailable = false;
        }

        indentedLogger.endHandleOperation();
    }

    private void extractProperties(Element staticStateElement) {
        // NOTE: XFormsExtractorContentHandler takes care of propagating only non-default properties
        final Element propertiesElement = staticStateElement.element(XFormsConstants.STATIC_STATE_PROPERTIES_QNAME);
        if (propertiesElement != null) {
            for (final Attribute attribute : Dom4jUtils.attributes(propertiesElement)) {
                final String propertyName = attribute.getName();
                final Object propertyValue = XFormsProperties.parseProperty(propertyName, attribute.getValue());

                nonDefaultProperties.put(propertyName, propertyValue);
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

    /**
     * Perform static analysis.
     *
     * @return                  true iif analysis was just performed in this call
     */
    private void analyze() {

        indentedLogger.startHandleOperation("", "performing static analysis");

        controlTypes = new HashMap<String, Map<String, ElementAnalysis>>();
        controlAppearances = new HashMap<String, Set<QName>>();
        eventNames = new HashSet<String>();
        eventHandlersMap = new HashMap<String, List<XFormsEventHandler>>();
        eventHandlerAncestorsMap = new HashMap<String, String>();
        keyHandlers = new ArrayList<XFormsEventHandler>();
        controlAnalysisMap = new LinkedHashMap<String, ElementAnalysis>();

        // Iterate over main static controls tree
        final XBLBindings.Scope rootScope = xblBindings.getTopLevelScope();
        final ContainerTrait rootControlAnalysis = new RootControl(this);

        // Analyze models first
        analyzeModelsXPathForScope(xblBindings.getTopLevelScope());

        // List of external LHHA
        final List<ExternalLHHAAnalysis> externalLHHA = new ArrayList<ExternalLHHAAnalysis>();

        // Then analyze controls
        analyzeComponentTree(xpathConfiguration, rootScope, controlsDocument.getRootElement(),
                rootControlAnalysis, externalLHHA);

        // Analyze new global XBL controls introduced above
        // NOTE: should recursively check?
        if (xblBindings != null)
            for (final XBLBindings.Global global : xblBindings.getGlobals().values())
                analyzeComponentTree(xpathConfiguration, rootScope, global.compactShadowTree.getRootElement(),
                        rootControlAnalysis, externalLHHA);

        // Process deferred external LHHA elements
        for (final ExternalLHHAAnalysis entry : externalLHHA)
            entry.attachToControl();

        if (scripts != null && scripts.size() > 0)
            indentedLogger.logDebug("", "extracted script elements", "count", Integer.toString(scripts.size()));

        // Finalize repeat hierarchy
        if (controlTypes.get("repeat") != null) {
            final Collection<ElementAnalysis> values = controlTypes.get("repeat").values();
            if (values != null && values.size() > 0) {
                final StringBuilder sb = new StringBuilder();
                for (final ElementAnalysis repeatControl : values) {

                    if (sb.length() > 0)
                        sb.append(',');

                    sb.append(repeatControl.prefixedId());

                    final ElementAnalysis ancestorRepeat = RepeatControl.getAncestorRepeatOrNull(repeatControl);
                    if (ancestorRepeat != null) {
                        // If we have an ancestor, append it
                        sb.append(' ');
                        sb.append(ancestorRepeat.prefixedId());
                    }
                }
                repeatHierarchyString = sb.toString();
            }
        }

        // Index attribute controls
        if (controlTypes.get("attribute") != null) {
            final Collection<ElementAnalysis> values = controlTypes.get("attribute").values();
            if (values != null && values.size() > 0) {
                attributeControls = new HashMap<String, Map<String, AttributeControl>>();

                for (final ElementAnalysis value : values) {
                    final AttributeControl attributeControl = (AttributeControl) value;
                    final String forPrefixedId = attributeControl.forPrefixedId();
                    final String attributeName = attributeControl.attributeName();
                    Map<String, AttributeControl> mapForId = attributeControls.get(forPrefixedId);
                    if (mapForId == null) {
                        mapForId = new HashMap<String, AttributeControl>();
                        attributeControls.put(forPrefixedId, mapForId);
                    }
                    mapForId.put(attributeName, attributeControl);
                }
            }
        }

        // Iterate over models to extract event handlers and scripts
        for (final Map.Entry<String, Model> currentEntry: modelsByPrefixedId.entrySet()) {
            final String modelPrefixedId = currentEntry.getKey();
            final Document modelDocument = currentEntry.getValue().element().getDocument();
            final DocumentWrapper modelDocumentInfo = new DocumentWrapper(modelDocument, null, xpathConfiguration);
            // NOTE: Say we don't want to exclude gathering event handlers within nested models, since this is a model
            extractEventHandlers(xpathConfiguration, modelDocumentInfo, XFormsUtils.getEffectiveIdPrefix(modelPrefixedId), false);
            extractXFormsScripts(modelDocumentInfo, XFormsUtils.getEffectiveIdPrefix(modelPrefixedId));
        }

        // Analyze controls XPath
        analyzeControlsXPath();

        // Set baseline resources before freeing transient state
        xblBindings.getBaselineResources();

        // Once analysis is done, some state can be freed
        freeTransientState();

        indentedLogger.endHandleOperation("controls", Integer.toString(controlAnalysisMap.size()));
    }

    private void freeTransientState() {

        xblBindings.freeTransientState();

        for (final ElementAnalysis controlAnalysis: controlAnalysisMap.values()) {
            controlAnalysis.freeTransientState();
        }

        for (final Model model: modelsByPrefixedId.values()) {
            model.freeTransientState();
        }
    }

    public boolean isCacheDocument() {
        return getBooleanProperty(XFormsProperties.CACHE_DOCUMENT_PROPERTY);
    }

    public boolean isClientStateHandling() {
        final String stateHandling = getStringProperty(XFormsProperties.STATE_HANDLING_PROPERTY);
        return stateHandling.equals(XFormsProperties.STATE_HANDLING_CLIENT_VALUE);
    }

    public boolean isServerStateHandling() {
        final String stateHandling = getStringProperty(XFormsProperties.STATE_HANDLING_PROPERTY);
        return stateHandling.equals(XFormsProperties.STATE_HANDLING_SERVER_VALUE);
    }

    private void extractControlsModelsComponents(Element staticStateElement, List<Element> topLevelModelsElements) {

        // Extract static components information
        // NOTE: Do this here so that xblBindings is available for scope resolution
        xblBindings = new XBLBindings(indentedLogger, this, metadata, staticStateElement);

        // Get top-level models from static state document
        {
            // FIXME: we don't get a System ID here. Is there a simple solution?
            int modelsCount = 0;
            for (final Element modelElement: topLevelModelsElements) {
                // Copy the element because we may need it in staticStateDocument for encoding
                final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);
                addModelDocument(xblBindings.getTopLevelScope(), modelDocument);
                modelsCount++;
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
            for (final Element currentElement : Dom4jUtils.elements(staticStateElement)) {
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
            final List<Document> extractedModels = extractNestedModels(controlsDocumentInfo, false, locationData);
            indentedLogger.logDebug("", "created nested model documents", "count", Integer.toString(extractedModels.size()));
            int modelsCount = 0;
            for (final Document currentModelDocument: extractedModels) {
                addModelDocument(xblBindings.getTopLevelScope(), currentModelDocument);
                modelsCount++;
            }
            indentedLogger.logDebug("", "created nested top-level model documents", "count", Integer.toString(modelsCount));
        }
    }

    /**
     * Register a model document. Used by this and XBLBindings.
     *
     * @param scope             XBL scope
     * @param modelDocument     model document
     */
    public void addModelDocument(XBLBindings.Scope scope, Document modelDocument) {
        List<Model> models = modelsByScope.get(scope);
        if (models == null) {
            models = new ArrayList<Model>();
            modelsByScope.put(scope, models);
        }

        final StaticStateContext staticStateContext = new StaticStateContext(XFormsStaticState.this, null, -1);
        final Model newModel = new Model(staticStateContext, scope, modelDocument.getRootElement());
        models.add(newModel);
        modelsByPrefixedId.put(newModel.prefixedId(), newModel);
        for (final Instance instance : newModel.instancesMap().values())
            modelByInstancePrefixedId.put(instance.prefixedId(), newModel);
    }

    public Model getModel(String prefixedId) {
        return modelsByPrefixedId.get(prefixedId);
    }

    public Model getModelByInstancePrefixedId(String prefixedId) {
        return modelByInstancePrefixedId.get(prefixedId);
    }

    public void extractXFormsScripts(DocumentWrapper documentInfo, String prefix) {

        // TODO: Not sure why we actually extract the scripts: we could just keep pointers on them, right? There is
        // probably not a notable performance if any at all, especially since this is needed at page generation time
        // only.

        final String xpathExpression = "/descendant-or-self::xxforms:script[not(ancestor::xforms:instance) and exists(@id)]";

        final List scriptNodeInfos = XPathCache.evaluate(documentInfo, xpathExpression,
                BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData);

        if (scriptNodeInfos.size() > 0) {
            if (this.scripts == null)
                this.scripts = new HashMap<String, Script>();
            for (Object scriptNodeInfo : scriptNodeInfos) {
                final NodeInfo currentNodeInfo = (NodeInfo) scriptNodeInfo;
                final Element scriptElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                // Remember script
                final String prefixedId = prefix + XFormsUtils.getElementStaticId(scriptElement);
                final boolean isClient = !"server".equals(scriptElement.attributeValue("runat"));
                final Script script = isClient
                        ? new Script(prefixedId, isClient, scriptElement.attributeValue("type"), scriptElement.getStringValue())
                        : new ServerScript(prefixedId, isClient, scriptElement.attributeValue("type"), scriptElement.getStringValue());
                this.scripts.put(prefixedId, script);
            }
        }
    }

    public String getDigest() {
        return digest;
    }

    /**
     * Get a serialized static state. If an encodedStaticState was provided during restoration, return that. Otherwise,
     * return a serialized static state computed from models, instances, and XHTML documents.
     *
     * @return                  serialized static sate
     */
    public String getEncodedStaticState() {

        if (!encodedStaticStateAvailable) {
            // Remember encoded state and discard Document
            // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state)
            encodedStaticState = XFormsUtils.encodeXML(staticStateDocument, true, isClientStateHandling() ? XFormsProperties.getXFormsPassword() : null, true);

            staticStateDocument = null;
            encodedStaticStateAvailable = true;
        }

        return encodedStaticState;
    }

    /**
     * Return all instances of the specified model.
     *
     * @param modelPrefixedId       model prefixed id
     * @return                      instances
     */
    public Collection<Instance> getInstances(String modelPrefixedId) {
        return modelsByPrefixedId.get(modelPrefixedId).instancesMap().values();
    }

    /**
     * Whether the noscript mode is enabled.
     *
     * @return true iif enabled
     */
    public final boolean isNoscript() {
        readPropertiesIfNeeded();
        return isNoscript;
    }

    /**
     * Whether XPath analysis is enabled.
     *
     * @return true iif enabled
     */
    public final boolean isXPathAnalysis() {
        readPropertiesIfNeeded();
        return isXPathAnalysis;
    }

    private void readPropertiesIfNeeded() {
        if (!propertiesRead) {
            // NOTE: Later can be also based on:
            // o native controls used
            // o XBL hints
            isNoscript = Version.instance().isPEFeatureEnabled(getBooleanProperty(XFormsProperties.NOSCRIPT_PROPERTY)
                    && getBooleanProperty(XFormsProperties.NOSCRIPT_SUPPORT_PROPERTY), XFormsProperties.NOSCRIPT_PROPERTY);

            isXPathAnalysis = Version.instance().isPEFeatureEnabled(getBooleanProperty(XFormsProperties.XPATH_ANALYSIS_PROPERTY),
                    XFormsProperties.XPATH_ANALYSIS_PROPERTY);

            propertiesRead = true;
        }
    }

    public List<Element> getTopLevelControlElements() {

        final List<Element> result = new ArrayList<Element>();

        if (controlsDocument != null)
            result.add(controlsDocument.getRootElement());

        if (xblBindings != null)
            for (final XBLBindings.Global global : xblBindings.getGlobals().values())
                result.add(global.compactShadowTree.getRootElement());

        return result;
    }

    public boolean hasControls() {
        return getTopLevelControlElements().size() > 0;
    }

    public Model getDefaultModelForScope(XBLBindings.Scope scope) {
        final List<Model> models = modelsByScope.get(scope);
        if (models == null || models.size() == 0) {
            // No model found for the given scope
            return null;
        } else {
            return models.get(0);
        }
    }

    public Model getModelByScopeAndBind(XBLBindings.Scope scope, String bindStaticId) {
        final List<Model> models = modelsByScope.get(scope);
        if (models != null && models.size() > 0) {
//            final String bindPrefixedId = scope.getPrefixedIdForStaticId(bindStaticId);
            for (final Model model: models) {
                if (model.bindsById().get(bindStaticId) != null)
                    return model;
            }
        }
        return null;
    }

    public final List<Model> getModelsForScope(XBLBindings.Scope scope) {
        final List<Model> models = modelsByScope.get(scope);
        return (models != null) ? models : Collections.<Model>emptyList();
    }

    public String findInstancePrefixedId(XBLBindings.Scope startScope, String instanceStaticId) {
        XBLBindings.Scope currentScope = startScope;
        while (currentScope != null) {
            for (final Model model: getModelsForScope(currentScope)) {
                if (model.instancesMap().containsKey(instanceStaticId)) {
                    return currentScope.getPrefixedIdForStaticId(instanceStaticId);
                }
            }
            currentScope = currentScope.parent;
        }
        return null;
    }

    public Map<String, Script> getScripts() {
        return scripts;
    }

    public Set<String> getAllowedExternalEvents() {
        return allowedExternalEvents;
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

    public Map<String, ElementAnalysis> getRepeatControlAnalysisMap() {
        return controlTypes.get("repeat");
    }

    public Element getControlElement(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis == null) ? null : controlAnalysis.element();
    }

    public int getControlPosition(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis == null) ? -1 : ((ViewTrait) controlAnalysis).index();
    }

    public boolean hasNodeBinding(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis != null) && controlAnalysis.hasNodeBinding();
    }

    public LHHAAnalysis getLabel(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis instanceof LHHATrait) ? ((LHHATrait) controlAnalysis).getLHHA("label") : null;
    }

    public LHHAAnalysis getHelp(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis instanceof LHHATrait) ? ((LHHATrait) controlAnalysis).getLHHA("help") : null;
    }

    public LHHAAnalysis getHint(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis instanceof LHHATrait) ? ((LHHATrait) controlAnalysis).getLHHA("hint") : null;
    }

    public LHHAAnalysis getAlert(String prefixedId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(prefixedId);
        return (controlAnalysis instanceof LHHATrait) ? ((LHHATrait) controlAnalysis).getLHHA("alert") : null;
    }

    /**
     * Statically check whether a control is a value control.
     *
     * @param controlEffectiveId    prefixed id or effective id of the control
     * @return                      true iif the control is a value control
     */
    public boolean isValueControl(String controlEffectiveId) {
        final ElementAnalysis controlAnalysis = controlAnalysisMap.get(XFormsUtils.getPrefixedId(controlEffectiveId));
        return (controlAnalysis != null) && controlAnalysis.canHoldValue();
    }

    /**
     * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
     * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
     * as the mapping is considered transient and not sharable among pages.
     *
     * @param prefix
     * @param element       element to get namespace mapping for
     * @return              mapping
     */
    public NamespaceMapping getNamespaceMapping(String prefix, Element element) {
        final String id = XFormsUtils.getElementStaticId(element);
        if (id != null) {
            // There is an id attribute
            final String prefixedId = (prefix != null) ? prefix + id : id;
            final NamespaceMapping cachedMap = metadata.getNamespaceMapping(prefixedId);
            if (cachedMap != null) {
                return cachedMap;
            } else {
                indentedLogger.logDebug("", "namespace mappings not cached",
                        "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element));
                // TODO: this case should not be allowed at all
                return new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element));
            }
        } else {
            // No id attribute
            indentedLogger.logDebug("", "namespace mappings not available because element doesn't have an id attribute",
                    "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element));
            return new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element));
        }
    }

    public String getRepeatHierarchyString() {
        return repeatHierarchyString;
    }

    public boolean hasControlByName(String controlName) {
        return controlTypes.get(controlName) != null;
    }

    public boolean hasControlAppearance(String controlName, QName appearance) {
        final Set<QName> appearances = controlAppearances.get(controlName);
        return appearances != null && appearances.contains(appearance);
    }

    public SelectionControl getSelect1Analysis(String controlPrefixedId) {
        return ((SelectionControl) controlAnalysisMap.get(controlPrefixedId));
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

    public AttributeControl getAttributeControl(String prefixedForAttribute, String attributeName) {
        final Map<String, AttributeControl> mapForId = attributeControls.get(prefixedForAttribute);
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

    private void extractEventHandlers(Configuration xpathConfiguration, DocumentInfo documentInfo, String prefix, boolean isControls) {

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
        final List actionHandlers = XPathCache.evaluate(documentInfo,
                xpathExpression, BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData);

        final XBLBindings.Scope innerScope = xblBindings.getResolutionScopeByPrefix(prefix); // if at top-level, prefix is ""
        final XBLBindings.Scope outerScope = (prefix.length() == 0) ? xblBindings.getTopLevelScope() : xblBindings.getResolutionScopeByPrefixedId(innerScope.scopeId);

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
                            parentStaticId = XFormsUtils.getElementStaticId(actionElement.getParent());
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
                                extractXFormsScripts(handlerWrapper, prefix);

                            } else if (controlAnalysisMap.containsKey(parentPrefixedId)) {
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
                        parentStaticId = XFormsUtils.getElementStaticId(actionElement.getParent());
                    }

                    // Register action handler
                    if (newActionElement != null) {
                        // If possible, find closest ancestor observer for XPath context evaluation

                        final String ancestorObserverStaticId; {
                            final Element ancestorObserver = findAncestorObserver(actionElement);
                            if (ancestorObserver != null) {
                                assert XFormsUtils.getElementStaticId(ancestorObserver) != null : "ancestor observer must have an id";
                                ancestorObserverStaticId = XFormsUtils.getElementStaticId(ancestorObserver);
                            } else if (isControls && isControlsTopLevelHandler(actionElement)) {
                                // Specially handle #document static id for top-level handlers
                                ancestorObserverStaticId = XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID;
                            } else {
                                // Can this happen?
                                ancestorObserverStaticId = null;
                            }
                        }

                        // ancestorObserverStaticId should not be null! Right now it can be if handlers
                        // are found within a control with binding (e.g. under fr:tabview/fr:tab). The content has
                        // not been annotated, so ids are missing. See TODO above.
                        //
                        // We might want to check for handlers as we process controls anyway. This would allow
                        // control over exactly which handlers we look at, and to ignore the content of bound nodes.
                        //
                        // See: [ #315922 ] Event handler nested within bound node causes errors
                        // http://forge.ow2.org/tracker/index.php?func=detail&aid=315922&group_id=168&atid=350207

                        // The observers to which this handler is attached might not be in the same scope. Try to find
                        // that scope.
                        final String observersPrefix;
                        if (evObserversStaticIds != null) {
                            // Explicit ev:observer, prefix might be different
                            final XBLBindings.Scope actionScope = xblBindings.getResolutionScopeByPrefixedId(prefix + XFormsUtils.getElementStaticId(newActionElement));
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

    public void analyzeModelsXPathForScope(XBLBindings.Scope scope) {
        if (isXPathAnalysis() && modelsByScope.get(scope) != null)
            for (final Model model : modelsByScope.get(scope))
                model.analyzeXPath();
    }

    public void analyzeControlsXPath() {
        if (isXPathAnalysis())
            for (final ElementAnalysis control : controlAnalysisMap.values())
                control.analyzeXPath();
    }

    public void analyzeComponentTree(final Configuration xpathConfiguration,
                                     final XBLBindings.Scope innerScope, Element startElement, final ContainerTrait startControlAnalysis,
                                     final List<ExternalLHHAAnalysis> externalLHHA) {

        final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(startElement.getDocument(), null, xpathConfiguration);

        final String prefix = innerScope.getFullPrefix();

        // Extract scripts for this tree of controls
        extractXFormsScripts(controlsDocumentInfo, prefix);

        // Visit tree
        visitAllControlStatic(startElement, startControlAnalysis, new XFormsStaticState.ControlElementVisitorListener() {

            public ElementAnalysis startVisitControl(Element controlElement, ContainerTrait parentContainer, ElementAnalysis previousElementAnalysis, String controlStaticId) {

                // Check for mandatory id
                if (controlStaticId == null)
                    throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName(), locationData);

                // Prefixed id
                final String controlPrefixedId = prefix + controlStaticId;

                // Gather control name
                final String controlName = controlElement.getName();

                final LocationData locationData = new ExtendedLocationData((LocationData) controlElement.getData(), "gathering static control information", controlElement);

                // If element is not built-in, check XBL and generate shadow content if needed
                final XBLBindings.ConcreteBinding newConcreteBinding = xblBindings.processElementIfNeeded(
                        indentedLogger, controlElement, controlPrefixedId, locationData, controlsDocumentInfo, xpathConfiguration,
                        innerScope);

                // Create and index static control information
                final ElementAnalysis elementAnalysis; {
                    final XBLBindings.Scope controlScope = xblBindings.getResolutionScopeByPrefixedId(controlPrefixedId);
                    final StaticStateContext staticStateContext = new StaticStateContext(XFormsStaticState.this, controlsDocumentInfo, controlAnalysisMap.size() + 1);

                    elementAnalysis = ControlAnalysisFactory.create(staticStateContext, parentContainer, previousElementAnalysis, controlScope, controlElement);
                }

                // Index by prefixed id
                controlAnalysisMap.put(elementAnalysis.prefixedId(), elementAnalysis);
                // Index by type
                {
                    Map<String, ElementAnalysis> controlsMap = controlTypes.get(controlName);
                    if (controlsMap == null) {
                        controlsMap = new LinkedHashMap<String, ElementAnalysis>();
                        controlTypes.put(controlName, controlsMap);
                    }
                    controlsMap.put(elementAnalysis.prefixedId(), elementAnalysis);
                }

                // Remember appearances in use
                {
                    Set<QName> appearances = controlAppearances.get(controlName);
                    if (appearances == null) {
                        appearances = new HashSet<QName>();
                        controlAppearances.put(controlName, appearances);
                    }
                    final QName appearance = Dom4jUtils.extractAttributeValueQName(elementAnalysis.element(), XFormsConstants.APPEARANCE_QNAME);
                    if ("textarea".equals(controlName) && "text/html".equals(elementAnalysis.element().attributeValue(XFormsConstants.MEDIATYPE_QNAME))) {
                        // Special appearance: when text/html mediatype is found on <textarea>, do as if an xxforms:richtext
                        // appearance had been set, so that we can decide on feature usage based on appearance only.
                        appearances.add(XFormsConstants.XXFORMS_RICH_TEXT_APPEARANCE_QNAME);
                    } else if (appearance != null) {
                        appearances.add(appearance);
                    }
                }

                // Remember external LHHA if we are one
                if (elementAnalysis instanceof LHHAAnalysis)
                    externalLHHA.add((ExternalLHHAAnalysis) elementAnalysis);

                // Recursively analyze the binding's component tree if any
                // NOTE: Do this after creating the binding control as the sub-tree must have the binding control as parent
                if (newConcreteBinding != null) {
                    analyzeComponentTree(xpathConfiguration, newConcreteBinding.innerScope,
                            newConcreteBinding.compactShadowTree.getRootElement(),
                            (ContainerTrait) elementAnalysis, externalLHHA);
                }

                return elementAnalysis;
            }
        });

        // Extract event handlers for this tree of controls
        // NOTE: Do this after analysing controls above so that XBL bindings are available for detection of nested event handlers.
        extractEventHandlers(xpathConfiguration, controlsDocumentInfo, prefix, true);
    }

    /**
     * Visit all the control elements without handling repeats or looking at the binding contexts. This is done entirely
     * statically. Only controls are visited, including grouping controls, leaf controls, and components.
     */
    private void visitAllControlStatic(Element startElement, ContainerTrait startContainer, ControlElementVisitorListener controlElementVisitorListener) {
        handleControlsStatic(controlElementVisitorListener, startElement, startContainer);
    }

    private void handleControlsStatic(ControlElementVisitorListener controlElementVisitorListener, Element container, ContainerTrait containerTrait) {

        ElementAnalysis currentElementAnalysis = null;

        for (final Element currentElement : Dom4jUtils.elements(container)) {

            final String elementName = currentElement.getName();
            final String elementStaticId = XFormsUtils.getElementStaticId(currentElement);

            if (XFormsControlFactory.isContainerControl(currentElement.getNamespaceURI(), elementName)) {
                // Handle XForms grouping controls

                // Visit container
                currentElementAnalysis = controlElementVisitorListener.startVisitControl(currentElement, containerTrait, currentElementAnalysis, elementStaticId);
                // Visit children
                handleControlsStatic(controlElementVisitorListener, currentElement, (ContainerTrait) currentElementAnalysis);
            } else if (XFormsControlFactory.isCoreControl(currentElement.getNamespaceURI(), elementName)
                    || xblBindings.isComponent(currentElement.getQName())
                    || VariableAnalysis.isVariableElement(currentElement)
                    || (XFormsControlFactory.isLHHA(currentElement.getNamespaceURI(), elementName) && currentElement.attribute(XFormsConstants.FOR_QNAME) != null)) {
                // Handle core control, component, variable, and external LHHA
                currentElementAnalysis = controlElementVisitorListener.startVisitControl(currentElement, containerTrait, currentElementAnalysis, elementStaticId);
            }
        }
    }

    private static interface ControlElementVisitorListener {
        ElementAnalysis startVisitControl(Element controlElement, ContainerTrait containerControlAnalysis, ElementAnalysis previousElementAnalysis, String controlStaticId);
    }

    public static List<Document> extractNestedModels(DocumentWrapper compactShadowTreeWrapper, boolean detach, LocationData locationData) {

        final List<Document> result = new ArrayList<Document>();

        final List modelElements = XPathCache.evaluate(compactShadowTreeWrapper,
                "//xforms:model[not(ancestor::xforms:instance)]",
                BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData);

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

    public void appendClasses(StringBuilder sb, String prefixedId) {
        final String controlClasses = controlAnalysisMap.get(prefixedId).classes();
        if (StringUtils.isEmpty(controlClasses))
            return;

        if (sb.length() > 0)
            sb.append(' ');

        sb.append(controlClasses);
    }

    /**
     * Returns whether there is any event handler registered anywhere in the controls for the given event name.
     *
     * @param eventName event name, like xforms-value-changed
     * @return          true if there is a handler, false otherwise
     */
    public boolean hasHandlerForEvent(String eventName) {
        return hasHandlerForEvent(eventName, true);
    }

    /**
     * Returns whether there is any event handler registered anywhere in the controls for the given event name.
     *
     * @param eventName         event name, like xforms-value-changed
     * @param includeAllEvents  whether to include #all
     * @return          true if there is a handler, false otherwise
     */
    public boolean hasHandlerForEvent(String eventName, boolean includeAllEvents) {
        // Check for #all as well if includeAllEvents is true
        return (includeAllEvents && eventNames.contains(XFormsConstants.XXFORMS_ALL_EVENTS)) || eventNames.contains(eventName);
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
                        final String id = XFormsUtils.getElementStaticId(element);
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

        // Try control analysis
        ElementAnalysis elementAnalysis = controlAnalysisMap.get(startPrefixedId);
        if (elementAnalysis == null) {
            // Not found, so try actions
            final String newStartPrefixedId = eventHandlerAncestorsMap.get(startPrefixedId);
            elementAnalysis = controlAnalysisMap.get(newStartPrefixedId);
        }

        // Simple case where source doesn't exist
        if (elementAnalysis == null)
            return Collections.emptyList();

        // Simple case where there is no ancestor repeat
        RepeatControl repeatControl = RepeatControl.getAncestorRepeatOrNull(elementAnalysis);
        if (repeatControl == null)
            return Collections.emptyList();

        // At least one ancestor repeat
        final List<String> result = new ArrayList<String>();
        // Go until there are no more ancestors OR we find the boundary repeat
        while (repeatControl != null && (endPrefixedId == null || !endPrefixedId.equals(repeatControl.prefixedId())) ) {
            result.add(repeatControl.prefixedId());
            repeatControl = RepeatControl.getAncestorRepeatOrNull(repeatControl);
        }
        return result;
    }

    public SAXStore.Mark getElementMark(String prefixedId) {
        return metadata.marks.get(prefixedId);
    }

    public ElementAnalysis getControlAnalysis(String prefixedId) {
        return controlAnalysisMap.get(prefixedId);
    }

    // This for debug only
    public void dumpAnalysis() {
        if (isXPathAnalysis()) {
            System.out.println(Dom4jUtils.domToPrettyString(XMLUtils.createDocument(this)));
        }
    }

    public void toXML(ContentHandlerHelper helper) {
        XMLUtils.wrapWithRequestElement(helper, new XMLUtils.DebugXML() {
            public void toXML(ContentHandlerHelper helper) {

                for (final ElementAnalysis controlAnalysis: controlAnalysisMap.values())
                    if (!(controlAnalysis instanceof ExternalLHHAAnalysis))// because they are logged as part of their related control
                        controlAnalysis.javaToXML(helper);

                for (final Model model: modelsByPrefixedId.values())
                    model.javaToXML(helper);
            }
        });
    }

    public DocumentWrapper getDefaultDocumentWrapper() {
        return documentWrapper;
    }

    public final NodeInfo DUMMY_CONTEXT;
    {
        try {
            final TinyBuilder treeBuilder = new TinyBuilder();
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler(xpathConfiguration);
            identity.setResult(treeBuilder);

            identity.startDocument();
            identity.endDocument();

            DUMMY_CONTEXT = treeBuilder.getCurrentRoot();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    // If there is no XPath context defined at the root (in the case there is no default XForms model/instance
    // available), we should use an empty context. However, currently for non-relevance in particular we must not run
    // expressions with an empty context. To allow running expressions at the root of a container without models, we
    // create instead a context with an empty document node instead. This way there is a context for evaluation. In the
    // future, we should allow running expressions with no context, possibly after statically checking that they do not
    // depend on the context, as well as prevent evaluations within non-relevant content by other means.
//    final List<Item> DEFAULT_CONTEXT = XFormsConstants.EMPTY_ITEM_LIST;
    public final List<Item> DEFAULT_CONTEXT = Collections.singletonList((Item) DUMMY_CONTEXT);

    public static class Metadata {
        public final IdGenerator idGenerator;
        public final Map<String, SAXStore.Mark> marks = new HashMap<String, SAXStore.Mark>();

        private final Map<String, NamespaceMapping> namespaceMappings = new HashMap<String, NamespaceMapping>();
        private final Map<String, NamespaceMapping> hashes = new LinkedHashMap<String, NamespaceMapping>();

        private long lastModified = -1;                     // last modification date of bindings
        private Map<String, Set<String>> xblBindings;       // Map<String uri, <String localname>>
        private Map<String, String> automaticMappings;      // ns URI -> directory name
        private Set<String> bindingIncludes;                // set of paths

        // Initial
        public Metadata() {
            this(new IdGenerator());
        }

        // When restoring state
        public Metadata(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
        }

        public void addNamespaceMapping(String prefixedId, Map<String, String> mapping) {

            // Sort mapping by prefix
            final TreeMap<String, String> sorted = new TreeMap<String, String>();
            sorted.putAll(mapping);

            // Create hash for this mapping
            final String hexHash = NamespaceMapping.hashMapping(sorted);

            // Add if needed
            final NamespaceMapping existingMap = hashes.get(hexHash);
            if (existingMap != null) {
                // Keep existing map

                // Map id to existing map
                namespaceMappings.put(prefixedId, existingMap);
            } else {
                 // Put new map
                final NamespaceMapping newMap = new NamespaceMapping(hexHash, sorted);
                hashes.put(hexHash, newMap);

                // Remember mapping for id
                namespaceMappings.put(prefixedId, newMap);
            }
        }

        public NamespaceMapping getNamespaceMapping(String prefixedId) {
            return namespaceMappings.get(prefixedId);
        }

        public void debugReadOut() {
            System.out.println("Number of different namespace mappings: " + hashes.size());
            for (final Map.Entry<String, NamespaceMapping> entry : hashes.entrySet()) {
                System.out.println("   hash: " + entry.getKey());
                for (final Map.Entry<String, String> mapping: entry.getValue().mapping.entrySet()) {
                    System.out.println("     hash: " + mapping.getKey() + " -> " + mapping.getValue());
                }
            }
        }

        public boolean hasTopLevelMarks() {
            for (final String prefixedId: marks.keySet()) {
                if (prefixedId.equals(XFormsUtils.getStaticIdFromId(prefixedId)))
                    return true;
            }
            return false;
        }

        private void readAutomaticXBLMappingsIfNeeded() {
            if (automaticMappings == null) {

                final PropertySet propertySet = Properties.instance().getPropertySet();
                final List<String> propertyNames = propertySet.getPropertiesStartsWith(XBLBindings.XBL_MAPPING_PROPERTY_PREFIX);
                automaticMappings = propertyNames.size() > 0 ? new HashMap<String, String>() : Collections.<String, String>emptyMap();

                for (final String propertyName: propertyNames) {
                    final String prefix = propertyName.substring(XBLBindings.XBL_MAPPING_PROPERTY_PREFIX.length());
                    automaticMappings.put(propertySet.getString(propertyName), prefix);
                }
            }
        }

        public String getAutomaticXBLMappingPath(String uri, String localname) {
            if (automaticMappings == null) {
                readAutomaticXBLMappingsIfNeeded();
            }

            final String prefix = automaticMappings.get(uri);
            if (prefix != null) {
                // E.g. fr:tabview -> oxf:/xbl/orbeon/tabview/tabview.xbl
                final String path = "/xbl/" + prefix + '/' + localname + '/' + localname + ".xbl";
                return (ResourceManagerWrapper.instance().exists(path)) ? path : null;
            } else {
                return null;
            }
        }

        public boolean isXBLBindingCheckAutomaticBindings(String uri, String localname) {
            // Is this already registered?
            if (this.isXBLBinding(uri, localname))
                return true;

            // If not, check if it exists as automatic binding
            final String path = getAutomaticXBLMappingPath(uri, localname);
            if (path != null) {
                // Remember as binding
                storeXBLBinding(uri, localname);

                // Remember to include later
                if (bindingIncludes == null)
                    bindingIncludes = new LinkedHashSet<String>();
                bindingIncludes.add(path);

                return true;
            } else {
                return false;
            }
        }

        public boolean isXBLBinding(String uri, String localname) {
            if (xblBindings == null)
                return false;

            final Set<String> localnamesMap = xblBindings.get(uri);
            return localnamesMap != null && localnamesMap.contains(localname);
        }

        public void storeXBLBinding(String bindingURI, String localname) {
            if (xblBindings == null)
                xblBindings = new HashMap<String, Set<String>>();

            Set<String> localnamesSet = xblBindings.get(bindingURI);
            if (localnamesSet == null) {
                localnamesSet = new HashSet<String>();
                xblBindings.put(bindingURI, localnamesSet);
            }

            localnamesSet.add(localname);
        }

        public Set<String> getBindingsIncludes() {
            return bindingIncludes;
        }

        public void updateBindingsLastModified(long lastModified) {
            this.lastModified = Math.max(this.lastModified, lastModified);
        }

        /**
         * Check if the binding includes are up to date.
         *
         * @return true iif they are up to date
         */
        public boolean checkBindingsIncludes() {
            if (bindingIncludes != null) {
                try {
                    for (final String include : bindingIncludes) {
                        final long lastModified = ResourceManagerWrapper.instance().lastModified(include, false);
                        if (lastModified > this.lastModified)
                            return false;
                    }
                } catch (ResourceNotFoundException e) {
                    // Resource was removed
                    return false;
                }
            }

            return true;
        }
    }
}
