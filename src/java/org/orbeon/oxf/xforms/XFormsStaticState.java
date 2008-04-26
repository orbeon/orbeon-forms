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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.processor.XFormsDocumentAnnotatorContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.Configuration;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
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

    // Static analysis
    private boolean isAnalyzed;             // whether this document has been analyzed already
    private Map controlNamesMap;            // Map<String, String> of control name to ""
    private Map eventNamesMap;              // Map<String, String> of event name to ""
    private Map eventHandlersMap;           // Map<String, List<XFormsEventHandler>> of control id to event handlers
    private Map controlElementsMap;         // Map<String, Element> of control id to control Element
    private Map namespacesMap;              // Map<String, Map<String, String>> of control id to Map of namespace mappings
    private Map defaultRepeatIdToIndex;     // Map<String, Integer> of repeat id to default repeat index
    private Map repeatChildrenMap;          // Map<String, List> of repeat id to List of children
    private Map repeatDescendantsMap;       // Map<String, List> of repeat id to List of descendants (computed on demand)
    private String repeatHierarchyString;   // contains comma-separated list of space-separated repeat id and ancestor if any
    private Map itemsInfoMap;               // Map<String, ItemsInfo> of control id to ItemsInfo
    private Map controlClasses;             // Map<String, String> of control id to class
    private boolean hasOfflineSupport;      // whether the document requires offline support

    private static final HashMap BASIC_NAMESPACE_MAPPINGS = new HashMap();
    static {
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XFORMS_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XXFORMS_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XMLConstants.XHTML_PREFIX, XMLConstants.XHTML_NAMESPACE_URI);
    }

    /**
     * Create static state object from an encoded version.
     *
     * @param pipelineContext       current PipelineContext
     * @param encodedStaticState    encoded static state
     */
    public XFormsStaticState(PipelineContext pipelineContext, String encodedStaticState) {

        // Parse document
        final Document staticStateDocument = XFormsUtils.decodeXML(pipelineContext, encodedStaticState);

        // Recompute namespace mappings
        final Map namespacesMap = new HashMap();
        try {
            final Transformer identity = TransformerUtils.getIdentityTransformer();
            identity.transform(new DocumentSource(staticStateDocument), new SAXResult(new XFormsDocumentAnnotatorContentHandler(namespacesMap)));
        } catch (TransformerException e) {
            throw new OXFException(e);
        }

        // Initialize
        initialize(staticStateDocument, encodedStaticState, namespacesMap);
    }
    

    /**
     * Create static state object from a Document.
     *
     * @param staticStateDocument   Document containing the static state
     * @param namespacesMap         Map<String, Map<String, String>> of control id to Map of namespace mappings
     */
    public XFormsStaticState(Document staticStateDocument, Map namespacesMap) {
        initialize(staticStateDocument, null, namespacesMap);
    }

//    public XFormsEngineStaticState(PipelineContext pipelineContext, Document staticStateDocument, String uuid) {
//        this.uuid = uuid;

    private void initialize(Document staticStateDocument, String encodedStaticState, Map namespacesMap) {

        // Namespace mappings
        this.namespacesMap = namespacesMap;

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
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(currentAttribute.getNamespaceURI())) {

                final Object propertyValue = XFormsProperties.parseProperty(currentAttribute.getName(), currentAttribute.getValue());
                nonDefaultProperties.put(currentAttribute.getName(), propertyValue);
            }
        }

        // Handle default for properties
        final OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
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
            return (String) (XFormsProperties.getPropertyDefinition(propertyName)).getDefaultValue();
    }

    public boolean getBooleanProperty(String propertyName) {
        final Boolean documentProperty = (Boolean) nonDefaultProperties.get(propertyName);
        if (documentProperty != null)
            return documentProperty.booleanValue();
        else
            return ((Boolean) (XFormsProperties.getPropertyDefinition(propertyName)).getDefaultValue()).booleanValue();
    }

    public Map getEventNamesMap() {
        return eventNamesMap;
    }

    public List getEventHandlers(String id) {
        return (List) eventHandlersMap.get(id);
    }

    public Map getControlElementsMap() {
        return controlElementsMap;
    }

    /**
     * Return the namespace mappings for a given XForms element (xforms:* or xxforms:*).
     *
     * @param elementId     XForms element id
     * @return              Map<String, String> or null (should not be null for an existing XForms element)
     */
    public Map getNamespaceMappings(String elementId) {
        return (Map) namespacesMap.get(elementId);
    }

    /**
     * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
     * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
     * as the mapping is considered transient and not sharable among pages.
     *
     * @param element       Element to get namsepace mapping for
     * @return              Map<String, String>
     */
    public Map getNamespaceMappings(Element element) {
        final String id = element.attributeValue("id");
        if (id != null) {
            // There is an id attribute
            final Map cachedMap = getNamespaceMappings(id);
            if (cachedMap != null) {
                return cachedMap;
            } else {
//                XFormsContainingDocument.logDebugStatic("static state", "namespace mappings not cached", new String[] { "element", Dom4jUtils.elementToString(element) });
                return Dom4jUtils.getNamespaceContextNoDefault(element);
            }
        } else {
            // No id attribute
//            XFormsContainingDocument.logDebugStatic("static state", "namespace mappings not cached", new String[] { "element", Dom4jUtils.elementToString(element) });
            return Dom4jUtils.getNamespaceContextNoDefault(element);
        }
    }

    public Map getDefaultRepeatIdToIndex() {
        return defaultRepeatIdToIndex;
    }

    public String getRepeatHierarchyString() {
        return repeatHierarchyString;
    }

    public boolean hasControlByName(String controlName) {
        return controlNamesMap.get(controlName) != null;
    }

    public ItemsInfo getItemsInfo(String controlId) {
        return (XFormsStaticState.ItemsInfo) itemsInfoMap.get(controlId);
    }

    /**
     * Return the list of repeat ids descendant of a given repeat id.
     */
    public List getNestedRepeatIds(final String repeatId) {
        // Check if the result is already computed
        {
            final List cachedResult = (List) repeatDescendantsMap.get(repeatId);
            if (cachedResult != null)
                return cachedResult;
        }

        // Compute and cache for further requests
        synchronized (this) {
            final List newResult = new ArrayList();
            addRepeatChildren(newResult, repeatId);
            repeatDescendantsMap.put(repeatId, newResult);
            return newResult;
        }
    }

    private void addRepeatChildren(List result, String repeatId) {
        final List children = (List) repeatChildrenMap.get(repeatId);
        if (children != null) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final String currentChild = (String) i.next();
                result.add(currentChild);
                addRepeatChildren(result, currentChild);
            }
        }
    }

    public synchronized boolean analyzeIfNecessary(final PipelineContext pipelineContext) {
        if (!isAnalyzed) {
            controlNamesMap = new HashMap();
            eventNamesMap = new HashMap();
            eventHandlersMap = new HashMap();
            controlElementsMap = new HashMap();
            defaultRepeatIdToIndex = new HashMap();
            repeatChildrenMap = new HashMap();
            repeatDescendantsMap = new HashMap();

            final Configuration xpathConfiguration = new Configuration();
            final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(controlsDocument, null, xpathConfiguration);

            // Iterate over static controls tree
            final FastStringBuffer repeatHierarchyStringBuffer = new FastStringBuffer(1024);
            visitAllControlStatic(new ControlElementVisitorListener() {

                private Stack repeatAncestorsStack = new Stack();

                public void startVisitControl(Element controlElement, String controlId) {

                    // Gather control name
                    final String controlName = controlElement.getName();
                    controlNamesMap.put(controlName, "");

                    final LocationData locationData = new ExtendedLocationData((LocationData) controlElement.getData(), "gathering static control information", controlElement);

                    // Check for mandatory and optional bindings
                    if (controlElement != null && XFormsConstants.XFORMS_NAMESPACE_URI.equals(controlElement.getNamespaceURI())) {
                        if (XFormsControls.mandatorySingleNodeControls.get(controlName) != null
                                && !(controlElement.attribute("ref") != null || controlElement.attribute("bind") != null)) {
                            throw new ValidationException("Missing mandatory single node binding for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControls.noSingleNodeControls.get(controlName) != null
                                && (controlElement.attribute("ref") != null || controlElement.attribute("bind") != null)) {
                            throw new ValidationException("Single node binding is prohibited for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControls.mandatoryNodesetControls.get(controlName) != null
                                && !(controlElement.attribute("nodeset") != null || controlElement.attribute("bind") != null)) {
                            throw new ValidationException("Missing mandatory nodeset binding for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControls.noNodesetControls.get(controlName) != null
                                && controlElement.attribute("nodeset") != null) {
                            throw new ValidationException("Node-set binding is prohibited for element: " + controlElement.getQualifiedName(), locationData);
                        }
                        if (XFormsControls.singleNodeOrValueControls.get(controlName) != null
                                && !(controlElement.attribute("ref") != null || controlElement.attribute("bind") != null || controlElement.attribute("value") != null)) {
                            throw new ValidationException("Missing mandatory single node binding or value attribute for element: " + controlElement.getQualifiedName(), locationData);
                        }
                    }

                    // Gather event handlers
                    // Map<String, List<XFormsEventHandler>> of observer id to List of XFormsEventHandler
                    final Map controlEventHandlersMap = XFormsEventHandlerImpl.extractEventHandlers(controlElement, eventNamesMap);
                    mergeEventHandlers(eventHandlersMap, controlEventHandlersMap);

                    // Gather static control
                    controlElementsMap.put(controlId, controlElement);

                    // Gather xforms:repeat information
                    if (controlName.equals("repeat")) {
                        // Find initial indexes
                        {
                            // Create control without parent, just to hold iterations
                            final XFormsRepeatControl repeatControl
                                    = new XFormsRepeatControl(null, null, controlElement, controlElement.getName(), controlId);

                            // Remember initial index
                            defaultRepeatIdToIndex.put(repeatControl.getRepeatId(), new Integer(repeatControl.getStartIndex()));
                        }
                        // Find repeat parents
                        {
                            // Create repeat hierarchy string
                            if (repeatHierarchyStringBuffer.length() > 0)
                                repeatHierarchyStringBuffer.append(',');

                            repeatHierarchyStringBuffer.append(controlId);

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
                                parentRepeatList.add(controlId);
                            }

                        }

                        repeatAncestorsStack.push(controlId);
                    }

                    // Gather itemset information
                    if (controlName.equals("select") || controlName.equals("select1")) {

                        final NodeInfo controlNodeInfo = controlsDocumentInfo.wrap(controlElement);

                        // Try to figure out if we have dynamic items. This attempts to cover all cases, including
                        // nested xforms:output controls.
                        final boolean hasNonStaticItem = ((Boolean) XPathCache.evaluateSingle(pipelineContext, controlNodeInfo,
                                "exists(.//xforms:*[@ref or @nodeset or @bind or @value])", BASIC_NAMESPACE_MAPPINGS,
                                null, null, null, null, locationData)).booleanValue();

                        // Remember information
                        if (itemsInfoMap == null)
                            itemsInfoMap = new HashMap();
                        itemsInfoMap.put(controlId, new ItemsInfo(hasNonStaticItem));
                    }
                }

                public void endVisitControl(Element controlElement, String controlId) {
                    final String controlName = controlElement.getName();
                    if (controlName.equals("repeat")) {
                        repeatAncestorsStack.pop();
                    }
                }
            });

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

                for (Iterator i = onlineTriggerIds.iterator(); i.hasNext();) {
                    final String currentId = (String) i.next();
                    addClasses(currentId, "xxforms-online");
                }

                for (Iterator i = offlineTriggerIds.iterator(); i.hasNext();) {
                    final String currentId = (String) i.next();
                    addClasses(currentId, "xxforms-offline");
                }

                for (Iterator i = offlineSaveTriggerIds.iterator(); i.hasNext();) {
                    final String currentId = (String) i.next();
                    addClasses(currentId, "xxforms-offline-save");
                }

                {
                    // Create list of all the documents to search
                    final List documentInfos = new ArrayList(modelDocuments.size() + 1);
                    for (Iterator i = modelDocuments.iterator(); i.hasNext();) {
                        documentInfos.add(new DocumentWrapper((Document) i.next(), null, xpathConfiguration));
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

            repeatHierarchyString = repeatHierarchyStringBuffer.toString();

            // Iterate over models
            for (Iterator i = getModelDocuments().iterator(); i.hasNext();) {

                final Element modelElement; {
                    final Document modelDocument = (Document) i.next();
                    modelElement = modelDocument.getRootElement();
                }

                final Map modelEventHandlers = XFormsEventHandlerImpl.extractEventHandlers(modelElement, eventNamesMap);
                mergeEventHandlers(eventHandlersMap, modelEventHandlers);

                // Iterate over model submissions
                for (Iterator j = modelElement.elements(new QName("submission", XFormsConstants.XFORMS_NAMESPACE)).iterator(); j.hasNext();) {
                    final Element currentSubmissionElement = (Element) j.next();

                    final Map submissionEventHandlers = XFormsEventHandlerImpl.extractEventHandlers(currentSubmissionElement, eventNamesMap);
                    mergeEventHandlers(eventHandlersMap, submissionEventHandlers);
                }
            }
            isAnalyzed = true;
            return true;
        } else {
            return false;
        }
    }

    public boolean isHasOfflineSupport() {
        return hasOfflineSupport;
    }

    private void addClasses(String controlId, String classes) {
        if (controlClasses == null)
            controlClasses = new HashMap();
        final String currentClasses = (String) controlClasses.get(controlId);
        if (currentClasses == null) {
            // Set
            controlClasses.put(controlId, classes);
        } else {
            // Append
            controlClasses.put(controlId, currentClasses + ' ' + classes);
        }
    }
    
    public void appendClasses(FastStringBuffer sb, String controlId) {
        if ((controlClasses == null))
            return;

        if (sb.length() > 0)
            sb.append(' ');

        final String classes = (String) controlClasses.get(controlId);
        if (classes != null)
            sb.append(classes);
    }

    private static void mergeEventHandlers(Map destination, Map source) {
        if (source != null) {
            for (Iterator i = source.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();

                final String currentObserverid = (String) currentEntry.getKey();
                final List currentEventHandlers = (List) currentEntry.getValue();

                List existingEventHandlers = (List) destination.get(currentObserverid);
                if (existingEventHandlers == null) {
                    existingEventHandlers = new ArrayList();
                    destination.put(currentObserverid, existingEventHandlers);
                }

                existingEventHandlers.addAll(currentEventHandlers);
            }
        }
    }

    /**
     * Visit all the control elements without handling repeats or looking at the binding contexts.
     */
    public void visitAllControlStatic(ControlElementVisitorListener controlElementVisitorListener) {
        handleControlsStatic(controlElementVisitorListener, controlsDocument.getRootElement());
    }

    private void handleControlsStatic(ControlElementVisitorListener controlElementVisitorListener, Element container) {
        for (Iterator i = container.elements().iterator(); i.hasNext();) {
            final Element currentControlElement = (Element) i.next();

            final String controlName = currentControlElement.getName();
            final String controlId = currentControlElement.attributeValue("id");

            if (XFormsControls.isGroupingControl(controlName)) {
                // Handle XForms grouping controls
                controlElementVisitorListener.startVisitControl(currentControlElement, controlId);
                handleControlsStatic(controlElementVisitorListener, currentControlElement);
                controlElementVisitorListener.endVisitControl(currentControlElement, controlId);
            } else if (XFormsControls.isLeafControl(controlName)) {
                // Handle leaf control
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

        public boolean isHasNonStaticItem() {
            return hasNonStaticItem;
        }
    }
}
