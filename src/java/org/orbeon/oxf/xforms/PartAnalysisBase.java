/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.Text;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.analysis.*;
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl;
import org.orbeon.oxf.xforms.analysis.controls.ExternalLHHAAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.RootControl;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandler;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

public abstract class PartAnalysisBase implements PartAnalysis {

    private final XBLBindingsBase.Scope startScope;
    private final Metadata metadata;

    // All distinct scopes by scope id
    private final Map<String, XBLBindingsBase.Scope> scopeIds = new HashMap<String, XBLBindingsBase.Scope>();
    private final Map<String, XBLBindingsBase.Scope> prefixedIdToXBLScopeMap = new HashMap<String, XBLBindingsBase.Scope>();                // maps element prefixed id => XBL scope
    protected XBLBindings xblBindings;

    // Static representation of models and instances
    private LinkedHashMap<XBLBindingsBase.Scope, List<Model>> modelsByScope = new LinkedHashMap<XBLBindingsBase.Scope, List<Model>>();
    private Map<String, Model> modelsByPrefixedId = new LinkedHashMap<String, Model>();
    private Map<String, Model> modelByInstancePrefixedId = new LinkedHashMap<String, Model>();

    // Event handlers
    private Set<String> eventNames = new HashSet<String>(); // used event names
    // observerPrefixedId => List of eventHandlers for all observers with handlers
    private Map<String, List<XFormsEventHandler>> eventHandlersMap = new HashMap<String, List<XFormsEventHandler>>();
    // actionPrefixId => ancestorPrefixedId
    protected Map<String, String> eventHandlerAncestorsMap = new HashMap<String, String>();
    private List<XFormsEventHandler> keyHandlers = new ArrayList<XFormsEventHandler>();

    // Controls
    protected Document controlsDocument;
    // type => Map of prefixedId => ElementAnalysis
    protected Map<String, Map<String, ElementAnalysis>> controlTypes = new HashMap<String, Map<String, ElementAnalysis>>();
    // type => Set of appearances
    private Map<String, Set<QName>> controlAppearances = new HashMap<String, Set<QName>>();
    // controlPrefixedId => ElementAnalysis for all controls
    protected Map<String, ElementAnalysis> controlAnalysisMap = new LinkedHashMap<String, ElementAnalysis>();

    // XXFormsAttributeControl
    private Map<String, Map<String, AttributeControl>> attributeControls;        // Map<String forPrefixedId, Map<String name, AttributeControl control>>

    public PartAnalysisBase(Metadata metadata, XBLBindingsBase.Scope startScope) {
        this.metadata = metadata;

        // Add existing ids to scope map
        final String prefix = startScope.getFullPrefix();
        for (Iterator<String> i = metadata.idGenerator().iterator(); i.hasNext();) {
            final String id = i.next();
            final String prefixedId = prefix + id;
            startScope.idMap.put(id, prefixedId);
            indexScope(prefixedId, startScope);
        }

        // Add top-level if needed
        if (startScope.isTopLevelScope())
            indexScope(XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID, startScope);

        // Tell top-level static id generator to stop checking for duplicate ids
        // TODO: not nice, check what this is about
        metadata.idGenerator().setCheckDuplicates(false);

        registerScope(startScope);

        this.startScope = startScope;
    }

    public XBLBindingsBase.Scope newScope(XBLBindingsBase.Scope parent, String scopeId) {
        return registerScope(new XBLBindingsBase.Scope(parent, scopeId));
    }

    private XBLBindingsBase.Scope registerScope(XBLBindingsBase.Scope scope) {
        assert !scopeIds.containsKey(scope.scopeId);
        scopeIds.put(scope.scopeId, scope);
        return scope;
    }

    public void indexScope(String prefixedId, XBLBindingsBase.Scope scope) {

        if (prefixedIdToXBLScopeMap.containsKey(prefixedId)) // enforce constraint that mapping must be unique
            throw new OXFException("Duplicate id found for prefixed id: " + prefixedId);

        prefixedIdToXBLScopeMap.put(prefixedId, scope);
    }

    public XBLBindingsBase.Scope getResolutionScopeByPrefix(String prefix) {
        assert prefix.length() == 0 || prefix.charAt(prefix.length() - 1) == XFormsConstants.COMPONENT_SEPARATOR;

        final String scopeId = (prefix.length() == 0) ? "" : prefix.substring(0, prefix.length() - 1);
        return scopeIds.get(scopeId);
    }

    /**
     * Return the resolution scope id for the given prefixed id.
     *
     * @param prefixedId    prefixed id of XForms element
     * @return              resolution scope
     */
    public XBLBindingsBase.Scope getResolutionScopeByPrefixedId(String prefixedId) {
        return prefixedIdToXBLScopeMap.get(prefixedId);
    }

    public static List<Document> extractNestedModels(DocumentWrapper compactShadowTreeWrapper, boolean detach, LocationData locationData) {

        final List<Document> result = new ArrayList<Document>();

        final List modelElements = XPathCache.evaluate(compactShadowTreeWrapper,
                "//xforms:model[not(ancestor::xforms:instance)]",
                XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING(), null, null, null, null, locationData);

        for (Object modelElement : modelElements) {
            final NodeInfo currentNodeInfo = (NodeInfo) modelElement;
            final Element currentModelElement = (Element) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

            final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(currentModelElement, detach);
            result.add(modelDocument);
        }

        return result;
    }

    /**
     * Register a model document. Used by this and XBLBindings.
     *
     * @param scope             XBL scope
     * @param modelDocument     model document
     */
    public Model addModel(XBLBindingsBase.Scope scope, Document modelDocument) {

        // Create model
        final StaticStateContext staticStateContext = new StaticStateContext(this, null, -1);
        final Model newModel = new Model(staticStateContext, scope, modelDocument.getRootElement());

        // Index model and instances
        List<Model> models = modelsByScope.get(scope);
        if (models == null) {
            models = new ArrayList<Model>();
            modelsByScope.put(scope, models);
        }
        models.add(newModel);
        modelsByPrefixedId.put(newModel.prefixedId(), newModel);
        for (final Instance instance : newModel.instancesMap().values())
            modelByInstancePrefixedId.put(instance.prefixedId(), newModel);

        // Register event handlers
        for (final XFormsEventHandlerImpl handler : newModel.eventHandlers())
            registerActionHandler(handler);

        return newModel;
    }

    // xxx for xxf:dynamic
    public void removeModel(Model model) {

        // Remove event handlers and scripts
//        TODO: xxx deregisterScripts

        // Deregister event handlers
        for (final XFormsEventHandlerImpl handler : model.eventHandlers())
            deregisterActionHandler(handler);

        // Deindex by instance id
        for (final Instance instance : model.instancesMap().values())
            modelByInstancePrefixedId.remove(instance.prefixedId());

        // Deindex by model id
        modelsByPrefixedId.remove(model.prefixedId());

        // Remove from list of models
        final List<Model> models = modelsByScope.get(model.scopeModel().scope());
        models.remove(model);
    }

    public Model getModel(String prefixedId) {
        return modelsByPrefixedId.get(prefixedId);
    }

    public Model getModelByInstancePrefixedId(String prefixedId) {
        return modelByInstancePrefixedId.get(prefixedId);
    }

    /**
     * Statically create and register an event handler.
     *
     * @param eventHandlerImpl  event handler implementation
     */
    public void registerActionHandler(XFormsEventHandlerImpl eventHandlerImpl) {

        for (final String observerPrefixedId : eventHandlerImpl.getObserversPrefixedIds()) {

            // Get handlers for observer
            final List<XFormsEventHandler> eventHandlersForObserver;
            {
                final List<XFormsEventHandler> currentList = eventHandlersMap.get(observerPrefixedId);
                if (currentList == null) {
                    eventHandlersForObserver = new ArrayList<XFormsEventHandler>();
                    eventHandlersMap.put(observerPrefixedId, eventHandlersForObserver);
                } else {
                    eventHandlersForObserver = currentList;
                }
            }

            // Add event handler
            eventHandlersForObserver.add(eventHandlerImpl);
        }

        // Remember closest ancestor observer for all nested actions
        // This is used to find the closest ancestor control, which in turn is used to find the repeat hierarchy
        // TODO: This is awful, we shouldn't need this
        {
            final String prefix = eventHandlerImpl.getPrefix();
            final String ancestorObserverPrefixedId = prefix + eventHandlerImpl.getAncestorObserverStaticId();
            eventHandlerAncestorsMap.put(prefix + eventHandlerImpl.getStaticId(), ancestorObserverPrefixedId);

            Dom4jUtils.visitSubtree(eventHandlerImpl.getEventHandlerElement(), new Dom4jUtils.VisitorListener() {
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
        if (eventHandlerImpl.isAllEvents()) {
            eventNames.add(XFormsConstants.XXFORMS_ALL_EVENTS);
        } else {
            for (final String eventName: eventHandlerImpl.getEventNames()) {
                eventNames.add(eventName);
                // Remember specially keypress events (could have eventNames<String, List<XFormsEventHandlerImpl>)
                // instead of separate list, if useful for more events
                if (XFormsEvents.KEYPRESS.equals(eventName))
                    keyHandlers.add(eventHandlerImpl);
            }
        }
    }

    public void deregisterActionHandler(XFormsEventHandlerImpl eventHandlerImpl) {

        for (final String observerPrefixedId : eventHandlerImpl.getObserversPrefixedIds()) {
            final List<XFormsEventHandler> eventHandlersForObserver = eventHandlersMap.get(observerPrefixedId);
            if (eventHandlersForObserver != null) // it shouldn't be null!
                eventHandlersForObserver.remove(eventHandlerImpl);
        }

        // Remove from ancestor map
        {
            final String prefix = eventHandlerImpl.getPrefix();
            eventHandlerAncestorsMap.remove(prefix + eventHandlerImpl.getStaticId());

            Dom4jUtils.visitSubtree(eventHandlerImpl.getEventHandlerElement(), new Dom4jUtils.VisitorListener() {
                public void startElement(Element element) {
                    final String id = XFormsUtils.getElementStaticId(element);
                    if (id != null)
                        eventHandlerAncestorsMap.remove(prefix + id);
                }

                public void endElement(Element element) {}
                public void text(Text text) {}
            });
        }

        // NOTE: We don't remove from eventNames as we are unable to figure this one out right now

        // Remove keypress events
        for (final String eventName : eventHandlerImpl.getEventNames()) {
            if (XFormsEvents.KEYPRESS.equals(eventName))
                keyHandlers.remove(eventHandlerImpl);
        }
    }

    /**
     * Perform static analysis.
     *
     * @return                  true iif analysis was just performed in this call
     */
    public void analyze() {

        getIndentedLogger().startHandleOperation("", "performing static analysis");

        // Iterate over main static controls tree
        final ContainerTrait rootControlAnalysis = new RootControl();

        // Analyze models first
        analyzeModelsXPathForScope(startScope);

        // List of external LHHA
        final List<ExternalLHHAAnalysis> externalLHHA = new ArrayList<ExternalLHHAAnalysis>();

        // Then analyze controls
        analyzeComponentTree(startScope, controlsDocument.getRootElement(),
                rootControlAnalysis, externalLHHA);

        // Analyze new global XBL controls introduced above
        // NOTE: should recursively check?
        if (xblBindings != null) {
            final scala.collection.Iterator<XBLBindingsBase.Global> i = xblBindings.allGlobals().values().iterator();
            while (i.hasNext())
                analyzeComponentTree(startScope, i.next().compactShadowTree.getRootElement(),
                        rootControlAnalysis, externalLHHA);
        }

        // Process deferred external LHHA elements
        for (final ExternalLHHAAnalysis entry : externalLHHA)
            entry.attachToControl();

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

        // Analyze controls XPath
        analyzeControlsXPath();

        // Set baseline resources before freeing transient state
        xblBindings.baselineResources();

        // Once analysis is done, some state can be freed
        freeTransientState();

        getIndentedLogger().endHandleOperation("controls", Integer.toString(controlAnalysisMap.size()));

        // Log if needed
        if (XFormsProperties.getDebugLogXPathAnalysis())
            dumpAnalysis();

        // Clean-up to finish initialization
        freeTransientState();
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

    public Model getDefaultModelForScope(XBLBindingsBase.Scope scope) {
        final List<Model> models = modelsByScope.get(scope);
        if (models == null || models.size() == 0) {
            // No model found for the given scope
            return null;
        } else {
            return models.get(0);
        }
    }

    public Model getModelByScopeAndBind(XBLBindingsBase.Scope scope, String bindStaticId) {
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

    public final List<Model> getModelsForScope(XBLBindingsBase.Scope scope) {
        final List<Model> models = modelsByScope.get(scope);
        return (models != null) ? models : Collections.<Model>emptyList();
    }

    public String findInstancePrefixedId(XBLBindingsBase.Scope startScope, String instanceStaticId) {
        XBLBindingsBase.Scope currentScope = startScope;
        while (currentScope != null) {
            for (final Model model : getModelsForScope(currentScope)) {
                if (model.instancesMap().containsKey(instanceStaticId)) {
                    return currentScope.getPrefixedIdForStaticId(instanceStaticId);
                }
            }
            currentScope = currentScope.parent;
        }
        return null;
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
                getIndentedLogger().logDebug("", "namespace mappings not cached",
                        "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element));
                // TODO: this case should not be allowed at all
                return new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element));
            }
        } else {
            // No id attribute
            getIndentedLogger().logDebug("", "namespace mappings not available because element doesn't have an id attribute",
                    "prefix", prefix, "element", Dom4jUtils.elementToDebugString(element));
            return new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element));
        }
    }

    public boolean hasControlByName(String controlName) {
        return controlTypes.get(controlName) != null;
    }

    public boolean hasControlAppearance(String controlName, QName appearance) {
        final Set<QName> appearances = controlAppearances.get(controlName);
        return appearances != null && appearances.contains(appearance);
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
        if (attributeControls == null)
            return null;

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

    public List<XFormsEventHandlerImpl> extractEventHandlers(DocumentInfo documentInfo, XBLBindingsBase.Scope innerScope, boolean isControls) {

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

        assert innerScope != null;
        final String prefix = innerScope.getFullPrefix();

        // Two expressions depending on whether handlers within models are excluded or not
        final String xpathExpression = isControls ?
                "//*[@ev:event and not(ancestor::xforms:instance) and not(ancestor::xforms:model) and (parent::*/@id or @ev:observer or /* is ..)]" :
                "//*[@ev:event and not(ancestor::xforms:instance) and (parent::*/@id or @ev:observer or /* is ..)]";

        // Get all candidate elements
        final List actionHandlers = XPathCache.evaluate(documentInfo,
                xpathExpression, XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING(), null, null, null, null, locationData());

        // NOTE: This might find a scope in an ancestor part
        final XBLBindingsBase.Scope outerScope = (prefix.length() == 0) ? startScope : searchResolutionScopeByPrefixedId(innerScope.scopeId);
        assert outerScope != null;

        final List<XFormsEventHandlerImpl> result = new ArrayList<XFormsEventHandlerImpl>();

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
                                final XBLBindingsBase.Scope bindingScope = getResolutionScopeByPrefixedId(parentPrefixedId);
//                                final XBLBindingsBase.Scope bindingScope = staticState().getResolutionScopeByPrefixedId(parentPrefixedId);
                                final XFormsConstants.XXBLScope startScope = innerScope.equals(bindingScope) ? XFormsConstants.XXBLScope.inner : XFormsConstants.XXBLScope.outer;
                                newActionElement = xblBindings.annotateHandler(actionElement, prefix, innerScope, outerScope, startScope).getRootElement();

                                // Extract scripts in the handler
                                final DocumentWrapper handlerWrapper = new DocumentWrapper(newActionElement.getDocument(), null, XPathCache.getGlobalConfiguration());
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
                            final XBLBindingsBase.Scope actionScope = getResolutionScopeByPrefixedId(prefix + XFormsUtils.getElementStaticId(newActionElement));
//                            final XBLBindingsBase.Scope actionScope = staticState().getResolutionScopeByPrefixedId(prefix + XFormsUtils.getElementStaticId(newActionElement));
                            observersPrefix = (actionScope != null) ? actionScope.getFullPrefix() : prefix;
                        } else {
                            // Parent is observer and has the same prefix
                            observersPrefix = prefix;
                        }

                        // Create and register the handler
                        final XFormsEventHandlerImpl newHandler = new XFormsEventHandlerImpl(prefix, newActionElement, parentStaticId, ancestorObserverStaticId, observersPrefix);
                        result.add(newHandler);
                    }
                }
            }
        }

        return result;
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

    public void analyzeModelsXPathForScope(XBLBindingsBase.Scope scope) {
        if (staticState().isXPathAnalysis() && modelsByScope.get(scope) != null)
            for (final Model model : modelsByScope.get(scope))
                model.analyzeXPath();
    }

    public void analyzeControlsXPath() {
        if (staticState().isXPathAnalysis())
            for (final ElementAnalysis control : controlAnalysisMap.values())
                control.analyzeXPath();
    }

    public void analyzeComponentTree(final XBLBindingsBase.Scope innerScope, Element startElement, final ContainerTrait startControlAnalysis,
                                     final List<ExternalLHHAAnalysis> externalLHHA) {

        final DocumentWrapper controlsDocumentInfo = new DocumentWrapper(startElement.getDocument(), null, XPathCache.getGlobalConfiguration());

        final String prefix = innerScope.getFullPrefix();

        // Extract scripts for this tree of controls
        extractXFormsScripts(controlsDocumentInfo, prefix);

        // Visit tree
        visitAllControlStatic(startElement, startControlAnalysis, new ControlElementVisitorListener() {

            public ElementAnalysis startVisitControl(Element controlElement, ContainerTrait parentContainer, ElementAnalysis previousElementAnalysis, String controlStaticId) {

                // Check for mandatory id
                if (controlStaticId == null)
                    throw new ValidationException("Missing mandatory id for element: " + controlElement.getQualifiedName(), locationData());

                // Prefixed id
                final String controlPrefixedId = prefix + controlStaticId;

                // Gather control name
                final String controlName = controlElement.getName();

                final LocationData locationData = new ExtendedLocationData((LocationData) controlElement.getData(), "gathering static control information", controlElement);

                // If element is not built-in, check XBL and generate shadow content if needed
                final XBLBindingsBase.ConcreteBinding newConcreteBinding = xblBindings.processElementIfNeeded(
                        getIndentedLogger(), controlElement, controlPrefixedId, locationData, controlsDocumentInfo,
                        innerScope);

                // Create and index static control information
                final ElementAnalysis elementAnalysis; {
                    final XBLBindingsBase.Scope controlScope = getResolutionScopeByPrefixedId(controlPrefixedId);
                    final StaticStateContext staticStateContext = new StaticStateContext(PartAnalysisBase.this, controlsDocumentInfo, controlAnalysisMap.size() + 1);

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
                    analyzeComponentTree(newConcreteBinding.innerScope,
                            newConcreteBinding.compactShadowTree.getRootElement(),
                            (ContainerTrait) elementAnalysis, externalLHHA);
                }

                return elementAnalysis;
            }
        });

        // Extract event handlers for this tree of controls
        // NOTE: Do this after analysing controls above so that XBL bindings are available for detection of nested event handlers.
        for (final XFormsEventHandlerImpl handler : extractEventHandlers(controlsDocumentInfo, innerScope, true))
            registerActionHandler(handler);
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

    public SAXStore.Mark getElementMark(String prefixedId) {
        return metadata.getElementMark(prefixedId);
    }

    public void freeTransientState() {

        xblBindings.freeTransientState();

        for (final ElementAnalysis controlAnalysis : controlAnalysisMap.values())
            controlAnalysis.freeTransientState();

        for (final Model model : modelsByPrefixedId.values())
            model.freeTransientState();
    }

    public void toXML(ContentHandlerHelper helper) {
        XMLUtils.wrapWithRequestElement(helper, new XMLUtils.DebugXML() {
            public void toXML(ContentHandlerHelper helper) {

                for (final ElementAnalysis controlAnalysis : controlAnalysisMap.values())
                    if (!(controlAnalysis instanceof ExternalLHHAAnalysis))// because they are logged as part of their related control
                        controlAnalysis.javaToXML(helper);

                for (final Model model : modelsByPrefixedId.values())
                    model.javaToXML(helper);
            }
        });
    }

    public void dumpAnalysis() {
        if (staticState().isXPathAnalysis())
            System.out.println(Dom4jUtils.domToPrettyString(XMLUtils.createDocument(this)));
    }
}
