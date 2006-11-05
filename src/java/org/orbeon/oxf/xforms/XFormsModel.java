/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.style.StandardNames;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.BuiltInSchemaFactory;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.ValidationErrorValue;

import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * Represents an XForms model.
 */
public class XFormsModel implements XFormsEventTarget, XFormsEventHandlerContainer, Cloneable {

    private Document modelDocument;

    // Model attributes
    private String modelId;

    // Instances
    private List instanceIds;
    private List instances;
    private Map instancesMap;

    // Event handlers
    private List eventHandlers;

    private InstanceConstructListener instanceConstructListener;

    // Submission information
    private Map submissions;

    // Binds
    private List binds;
    private FunctionLibrary xformsFunctionLibrary;

    // Schema validation
    private XFormsModelSchemaValidator schemaValidator;

    // Containing document
    private XFormsContainingDocument containingDocument;

    public XFormsModel(Document modelDocument) {
        this.modelDocument = modelDocument;

        // Basic check trying to make sure this is an XForms model
        // TODO: should rather use schema here or when obtaining document passed to this constructor
        final Element modelElement = modelDocument.getRootElement();
        String rootNamespaceURI = modelElement.getNamespaceURI();
        if (!rootNamespaceURI.equals(XFormsConstants.XFORMS_NAMESPACE_URI))
            throw new ValidationException("Root element of XForms model must be in namespace '"
                    + XFormsConstants.XFORMS_NAMESPACE_URI + "'. Found instead: '" + rootNamespaceURI + "'",
                    (LocationData) modelElement.getData());

        // Get model id (may be null)
        modelId = modelElement.attributeValue("id");

        // Extract list of instances ids
        {
            List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
            instanceIds = new ArrayList(instanceContainers.size());
            if (instanceContainers.size() > 0) {
                for (Iterator i = instanceContainers.iterator(); i.hasNext();) {
                    final Element instanceContainer = (Element) i.next();
                    String instanceId = instanceContainer.attributeValue("id");
                    if (instanceId == null)
                        instanceId = "";
                    instanceIds.add(instanceId);
                }
            }
        }
    }

    public void setContainingDocument(XFormsContainingDocument xFormsContainingDocument) {

        this.containingDocument = xFormsContainingDocument;

        final Element modelElement = modelDocument.getRootElement();

        // Get <xforms:submission> elements (may be missing)
        {
            for (Iterator i = modelElement.elements(new QName("submission", XFormsConstants.XFORMS_NAMESPACE)).iterator(); i.hasNext();) {
                final Element submissionElement = (Element) i.next();
                String submissionId = submissionElement.attributeValue("id");
                if (submissionId == null)
                    submissionId = "";

                if (this.submissions == null)
                    this.submissions = new HashMap();
                this.submissions.put(submissionId, new XFormsModelSubmission(containingDocument, submissionId, submissionElement, this));
            }
        }

        // Extract event handlers
        eventHandlers = XFormsEventHandlerImpl.extractEventHandlers(containingDocument, this, modelElement);

        // Create XForms function library
         xformsFunctionLibrary = new XFormsFunctionLibrary(this, getContainingDocument().getXFormsControls());
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public Document getModelDocument() {
        return modelDocument;
    }

    /**
     * Get object with the id specified.
     */
    public Object getObjectByid(PipelineContext pipelineContext, String id) {

        // Check model itself
        if (id.equals(modelId))
            return this;

        // Search instances
        final XFormsInstance instance = (XFormsInstance) instancesMap.get(id);
        if (instance != null)
            return instance;

        // Search submissions
        if (submissions != null) {
            final XFormsModelSubmission resultSubmission = (XFormsModelSubmission) submissions.get(id);
            if (resultSubmission != null)
                return resultSubmission;
        }

        return null;
    }

    private void resetBinds() {
        binds = new ArrayList();
        handleBindContainer(modelDocument.getRootElement(), null);
    }

    /**
     * Gather xforms:bind elements information.
     */
    private void handleBindContainer(Element container, ModelBind parent) {
        for (Iterator i = container.elements(new QName("bind", XFormsConstants.XFORMS_NAMESPACE)).iterator(); i.hasNext();) {
            final Element bindElement = (Element) i.next();
            final ModelBind modelBind = new ModelBind(bindElement, parent);
            if (parent != null) {
                parent.addChild(modelBind);
                modelBind.setParent(parent);
            }
            binds.add(modelBind);
            handleBindContainer(bindElement, modelBind);
        }
    }

    /**
     * Return the default instance for this model, i.e. the first instance. Return null if there is
     * no instance in this model.
     */
    public XFormsInstance getDefaultInstance() {
        return (XFormsInstance) ((instances != null && instances.size() > 0) ? instances.get(0) : null);
    }

    /**
     * Return all XFormsInstance objects for this model, in the order they appear in the model.
     */
    public List getInstances() {
        return instances;
    }

    /**
     * Return the number of instances in this model.
     */
    public int getInstanceCount() {
        return instanceIds.size();
    }

    /**
     * Return the XFormsInstance with given id, null if not found.
     */
    public XFormsInstance getInstance(String instanceId) {
        return (instancesMap == null) ? null : (XFormsInstance) (instancesMap.get(instanceId));
    }

    /**
     * Return the XFormsInstance object containing the given node.
     */
    public XFormsInstance getInstanceForNode(NodeInfo nodeInfo) {

        final DocumentInfo documentInfo = nodeInfo.getDocumentRoot();

        if (instances != null) {
            for (Iterator i = instances.iterator(); i.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) i.next();
                if (currentInstance.getInstanceDocumentInfo().isSameNodeInfo(documentInfo))
                    return currentInstance;
            }
        }

        return null;
    }

    /**
     * Set an instance document for this model. There may be multiple instance documents. Each
     * instance document may have an associated id that identifies it.
     */
    public void setInstanceDocument(PipelineContext pipelineContext, int instancePosition, Object instanceDocument, String instanceSourceURI, String username, String password) {
        // Initialize containers if needed
        if (instances == null) {
            instances = Arrays.asList(new XFormsInstance[instanceIds.size()]);
            instancesMap = new HashMap(instanceIds.size());
        }
        // Prepare and set instance
        final String instanceId = (String) instanceIds.get(instancePosition);
        final XFormsInstance newInstance;
        {
            if (instanceDocument instanceof Document)
                newInstance = new XFormsInstance(pipelineContext, instanceId, (Document) instanceDocument, instanceSourceURI, username, password, this);
            else if (instanceDocument instanceof DocumentInfo)
                newInstance = new XFormsInstance(pipelineContext, instanceId, (DocumentInfo) instanceDocument, instanceSourceURI, username, password, this);
            else
                throw new OXFException("Invalid type for instance document: " + instanceDocument.getClass().getName());
        }
        instances.set(instancePosition, newInstance);

        // Create mapping instance id -> instance
        if (instanceId != null)
            instancesMap.put(instanceId, newInstance);
    }

    /**
     * Apply calculate binds.
     */
    public void applyCalculateBinds(final PipelineContext pipelineContext) {
        applyBinds(new BindRunner() {
            public void applyBind(ModelBind modelBind) {
                handleCalculateBinds(pipelineContext, modelBind, this);
            }
        });
    }

    /**
     * Apply required, relevant and readonly binds.
     */
    public void applyComputedExpressionBinds(final PipelineContext pipelineContext) {
        applyBinds(new BindRunner() {
            public void applyBind(ModelBind modelBind) {
                handleComputedExpressionBinds(pipelineContext, modelBind, this);
            }
        });
    }

    private static interface BindRunner {
        public void applyBind(ModelBind modelBind);
    }

    /**
     * Apply binds.
     */
    private void applyBinds(BindRunner bindRunner) {

        if (binds == null)
            resetBinds();

        // Iterate over all binds
        for (Iterator i = binds.iterator(); i.hasNext();) {
            final ModelBind modelBind = (ModelBind) i.next();
            // But only consider top-level binds, as children are handled recursively
            if (modelBind.getParent() == null) {
                try {
                    modelBind.setCurrentNodeInfo(getDefaultInstance().getInstanceRootElementInfo());
                    bindRunner.applyBind(modelBind);
                } catch (final Exception e) {
                    throw new ValidationException(e, modelBind.getLocationData());
                }
            }
        }
    }

    private void handleCalculateBinds(final PipelineContext pipelineContext, final ModelBind modelBind, BindRunner bindRunner) {
        // Handle calculate MIP
        if (modelBind.getCalculate() != null) {
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Compute calculated value
                    try {
                        final String stringResult = containingDocument.getEvaluator().evaluateAsString(pipelineContext, nodeInfo, modelBind.getCalculate(), modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                        XFormsInstance.setValueForNodeInfo(pipelineContext, nodeInfo, stringResult, null);

                    } catch (Exception e) {
                        throw new ValidationException("Error when evaluating string expression '" + modelBind.getCalculate() + "'", e, modelBind.getLocationData());
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, bindRunner);
    }

    private void handleComputedExpressionBinds(final PipelineContext pipelineContext, final ModelBind modelBind, BindRunner bindRunner) {

        // Handle required MIP
        if (modelBind.getRequired() != null) {
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Evaluate "required" XPath expression on this node
                    try {
                        // Mark node
                        final String xpath = "boolean(" + modelBind.getRequired() + ")";
                        final boolean required = ((Boolean) containingDocument.getEvaluator().evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID())).booleanValue();

                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                        instanceData.updateRequired(required, nodeInfo, modelBind.getId());
                    } catch (Exception e) {
                        throw new ValidationException("Error when evaluating boolean expression '" + modelBind.getRequired() + "'", e, modelBind.getLocationData());
                    }
                }
            });
        }

        // Handle relevant MIP
        if (modelBind.getRelevant() != null) {
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Evaluate "relevant" XPath expression on this node
                    try {
                        final String xpath = "boolean(" + modelBind.getRelevant() + ")";
                        boolean relevant = ((Boolean) containingDocument.getEvaluator().evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID())).booleanValue();
                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                        instanceData.getRelevant().set(relevant);
                    } catch (Exception e) {
                        throw new ValidationException("Error when evaluating boolean expression '" + modelBind.getRelevant() + "'", e, modelBind.getLocationData());
                    }
                }
            });
        }

        // Handle readonly MIP
        if (modelBind.getReadonly() != null) {
            // The bind has a readonly attribute
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Evaluate "readonly" XPath expression on this node
                    try {
                        final String xpath = "boolean(" + modelBind.getReadonly() + ")";
                        boolean readonly = ((Boolean) containingDocument.getEvaluator().evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID())).booleanValue();

                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                        instanceData.getReadonly().set(readonly);
                    } catch (Exception e) {
                        throw new ValidationException("Error when evaluating boolean expression '" + modelBind.getReadonly() + "'", e, modelBind.getLocationData());
                    }
                }
            });
        } else if (modelBind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Mark node
                    InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                    instanceData.getReadonly().set(true);
                }
            });
        }

        // Handle xxforms:externalize bind
        if (modelBind.getXXFormsExternalize() != null) {
            // The bind has an xxforms:externalize attribute
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Evaluate "externalize" XPath expression on this node
                    try {
                        final String xpath = "boolean(" + modelBind.getXXFormsExternalize() + ")";
                        boolean xxformsExternalize = ((Boolean) containingDocument.getEvaluator().evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID())).booleanValue();

                        // Mark node
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                        instanceData.getXXFormsExternalize().set(xxformsExternalize);
                    } catch (Exception e) {
                        throw new ValidationException("Error when evaluating boolean expression '" + modelBind.getXXFormsExternalize() + "'", e, modelBind.getLocationData());
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, bindRunner);
    }

    private void handleValidationBind(final PipelineContext pipelineContext, final ModelBind modelBind, BindRunner bindRunner) {

        // Handle XPath constraint MIP
        if (modelBind.getConstraint() != null) {
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Evaluate constraint
                    try {
                        final String xpath = "boolean(" + modelBind.getConstraint() + ")";
                        final Boolean valid = (Boolean) containingDocument.getEvaluator().evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                        instanceData.updateConstraint(valid.booleanValue(), nodeInfo, modelBind.getId());
                    } catch (Exception e) {
                        throw new ValidationException("Error when evaluating boolean expression '" + modelBind.getConstraint() + "'", e, modelBind.getLocationData());
                    }
                }
            });
        }

        // Handle type MIP
        if (modelBind.getType() != null) {

            // Need an evaluator to check and convert type below
            final XPathEvaluator xpathEvaluator;
            try {
                xpathEvaluator= new XPathEvaluator();
                // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
                IndependentContext context = xpathEvaluator.getStaticContext();
                for (Iterator j = modelBind.getNamespaceMap().keySet().iterator(); j.hasNext();) {
                    String prefix = (String) j.next();
                    context.declareNamespace(prefix, (String) modelBind.getNamespaceMap().get(prefix));
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }

            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {

                    // Get type information
                    int requiredTypeFingerprint = -1;
                    {
                        String type = modelBind.getType();
                        int prefixPosition = type.indexOf(':');
                        if (prefixPosition > 0) {
                            String prefix = type.substring(0, prefixPosition);
                            String namespace = (String) modelBind.getNamespaceMap().get(prefix);
                            if (namespace == null)
                                throw new ValidationException("Namespace not declared for prefix '" + prefix + "'",
                                        modelBind.getLocationData());

                            requiredTypeFingerprint = StandardNames.getFingerprint(namespace, type.substring(prefixPosition + 1));
                        }
                    }
                    if (requiredTypeFingerprint == -1)
                        throw new ValidationException("Invalid type '" + modelBind.getType() + "'",
                                modelBind.getLocationData());

                    // Pass-through the type value
                    InstanceData instanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                    instanceData.getType().set(requiredTypeFingerprint);

                    // Try to perform casting
                    final String nodeStringValue = nodeInfo.getStringValue().trim();
                    if (XFormsUtils.getLocalInstanceData(nodeInfo).getRequired().get() || nodeStringValue.length() != 0) {
                        StringValue stringValue = new StringValue(nodeStringValue);
                        XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getStaticContext().getConfiguration());
                        AtomicValue result = stringValue.convertPrimitive((BuiltInAtomicType) BuiltInSchemaFactory.getSchemaType(requiredTypeFingerprint), true, xpContext);

                        instanceData.updateValueValid(!(result instanceof ValidationErrorValue), nodeInfo, modelBind.getId());
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, bindRunner);
    }

    private void handleChildrenBinds(final PipelineContext pipelineContext, final ModelBind modelBind, BindRunner bindRunner) {
        // Handle children binds
        try {
            final List nodeset = containingDocument.getEvaluator().evaluate(pipelineContext,
                modelBind.getCurrentNodeInfo(),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo nodeInfo = (NodeInfo) j.next();
                for (Iterator childIterator = modelBind.getChildren().iterator(); childIterator.hasNext();) {
                    final ModelBind child = (ModelBind) childIterator.next();
                    child.setCurrentNodeInfo(nodeInfo);
                    bindRunner.applyBind(child);
                }
            }
        } catch (Exception e) {
            throw new ValidationException("Error when evaluating bind node-set '" + modelBind.getNodeset() + "'", e, modelBind.getLocationData());
        }
    }

    private void iterateNodeSet(PipelineContext pipelineContext, ModelBind modelBind, NodeHandler nodeHandler) {
        try {
            final List nodeset = containingDocument.getEvaluator().evaluate(pipelineContext,
                modelBind.getCurrentNodeInfo(),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary, null);
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo nodeInfo = (NodeInfo) j.next();
                nodeHandler.handleNode(nodeInfo);
            }
        } catch (Exception e) {
            throw new ValidationException("Error when evaluating bind node-set '" + modelBind.getNodeset() + "'", e, modelBind.getLocationData());
        }
    }

    private interface NodeHandler {
        void handleNode(NodeInfo node);
    }

    public String getEffectiveId() {
        return modelId;
    }

    public LocationData getLocationData() {
        return (LocationData) modelDocument.getRootElement().getData();
    }

    public List getBindNodeset(PipelineContext pipelineContext, ModelBind bind) {
        // Get a list of parents, ordered by grandfather first
        final List parents = new ArrayList();
        {
            parents.add(bind);
            ModelBind parent = bind;
            while ((parent = parent.getParent()) != null) {
                parents.add(parent);
            }
            Collections.reverse(parents);
        }

        final List nodeset = new ArrayList();

        // Find the root node
        nodeset.add(getDefaultInstance().getInstanceRootElementInfo());

        for (Iterator i = parents.iterator(); i.hasNext();) {
            final ModelBind current = (ModelBind) i.next();
            final List currentModelBindResults = new ArrayList();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo node = (NodeInfo) j.next();
                // Execute XPath expresssion
                currentModelBindResults.addAll(containingDocument.getEvaluator().evaluate(pipelineContext, node, current.getNodeset(),
                        current.getNamespaceMap(), null, xformsFunctionLibrary, current.getLocationData().getSystemID()));
            }
            nodeset.addAll(currentModelBindResults);
            // Last iteration of i: remove all except last
            if (!i.hasNext())
                nodeset.retainAll(currentModelBindResults);
        }
        return nodeset;
    }

    public ModelBind getModelBindById(String id) {

        if (binds == null)
            resetBinds();

        for (Iterator i = binds.iterator(); i.hasNext();) {
            ModelBind bind = (ModelBind) i.next();
            ModelBind result = getModelBindByIdWorker(bind, id);
            if (result != null)
                return result;
        }
        return null;
    }

    private ModelBind getModelBindByIdWorker(ModelBind parent, String id) {
        if (id.equals(parent.getId()))
            return parent;
        // Look in children
        for (Iterator j = parent.getChildren().iterator(); j.hasNext();) {
            ModelBind child = (ModelBind) j.next();
            ModelBind bind = getModelBindByIdWorker(child, id);
            if (bind != null)
                return bind;
        }
        return null;
    }

    private void loadSchemasIfNeeded(PipelineContext pipelineContext) {
        final Element modelElement = modelDocument.getRootElement();
        // Create Schema validator only if we have schemas specified
        if (modelElement.attributeValue("schema") != null && schemaValidator == null) {
            schemaValidator = new XFormsModelSchemaValidator(modelElement);
            schemaValidator.loadSchemas(pipelineContext, containingDocument);
        }
    }

    private void applySchemasIfNeeded() {
        // Don't do anything if there is no schema
        if (schemaValidator != null) {
            // Apply schemas to all instances
            for (Iterator i = getInstances().iterator(); i.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) i.next();
                // Currently we don't support validating read-only instances
                if (!currentInstance.isReadOnly())
                    schemaValidator.applySchema(currentInstance);
            }
        }
    }

    public String getSchemaURI() {
        if (schemaValidator != null) {
            return schemaValidator.getSchemaURIs();
        } else {
            return null;
        }
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return containingDocument;
    }

    /**
     * Return the List of XFormsEventHandler objects within this object.
     */
    public List getEventHandlers() {
        return eventHandlers;
    }

    /**
     * Initialize the state of the model when the model object was just recreated.
     */
    public void initializeState(PipelineContext pipelineContext ) {
        loadSchemasIfNeeded(pipelineContext);
        applyComputedExpressionBinds(pipelineContext);
        doRevalidate(pipelineContext);
        synchronizeInstanceDataEventState();
    }

    public void performDefaultAction(final PipelineContext pipelineContext, XFormsEvent event) {
        final String eventName = event.getEventName();
        if (XFormsEvents.XFORMS_MODEL_CONSTRUCT.equals(eventName)) {
            // 4.2.1 The xforms-model-construct Event
            // Bubbles: Yes / Cancelable: No / Context Info: None

            final Element modelElement = modelDocument.getRootElement();

            // 1. All XML Schemas loaded (throws xforms-link-exception)

            // TODO: support multiple schemas
            // Get schema URI
            loadSchemasIfNeeded(pipelineContext);
            // TODO: throw exception event

            // 2. Create XPath data model from instance (inline or external) (throws xforms-link-exception)
            //    Instance may not be specified.

//            if (instances == null) {
            if (instances == null) {
                instances = Arrays.asList(new XFormsInstance[instanceIds.size()]);
                instancesMap = new HashMap(instanceIds.size());
            }
            {
                // Build initial instance document
                final List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
                if (instanceContainers.size() > 0) {
                    // Iterate through all instances
                    int instancePosition = 0;
                    for (Iterator i = instanceContainers.iterator(); i.hasNext(); instancePosition++) {

                        final Element instanceContainerElement = (Element) i.next();
                        final boolean isReadonlyHint = "true".equals(instanceContainerElement.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME));

                        // Skip processing in case somebody has already set this particular instance
                        if (instances.get(instancePosition) != null)
                            continue;

                        final String srcAttribute = instanceContainerElement.attributeValue("src");
                        final LocationData locationData = (LocationData) instanceContainerElement.getData();

                        final Object instanceDocument;// Document or DocumentInfo
                        final String instanceSourceURI;
                        final String xxformsUsername;
                        final String xxformsPassword;
                        if (srcAttribute == null) {
                            // Inline instance
                            final List children = instanceContainerElement.elements();
                            if (children == null || children.size() != 1) {
                                final Throwable throwable = new ValidationException("xforms:instance element must contain exactly one child element", locationData);
                                containingDocument.dispatchEvent(pipelineContext, new XFormsLinkExceptionEvent(XFormsModel.this, null, instanceContainerElement, throwable));
                                break;
                            }
                            instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) children.get(0));
                            // TODO: support DocumentInfo (easier once static state is done with TinyTree as well)
                            instanceSourceURI = null;
                            xxformsUsername = null;
                            xxformsPassword = null;
                        } else if (!srcAttribute.trim().equals("")) {

                            // External instance
                            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                            // NOTE: Optimizing with include() for servlets doesn't allow detecting errors caused by
                            // the included resource, so we don't allow this for now. Furthermore, we are forced to
                            // "optimize" for portlet access.

//                            final boolean optimize = !NetUtils.urlHasProtocol(srcAttribute)
//                               && (externalContext.getRequest().getContainerType().equals("portlet")
//                                    || (externalContext.getRequest().getContainerType().equals("servlet")
//                                        && XFormsUtils.isOptimizeLocalInstanceLoads()));

                            final boolean optimizeForPortlets = !NetUtils.urlHasProtocol(srcAttribute)
                                                        && externalContext.getRequest().getContainerType().equals("portlet");

                            final XFormsModelSubmission.ConnectionResult connectionResult;
                            if (optimizeForPortlets) {
                                // Use optimized local mode
                                final URI resolvedURI = XFormsUtils.resolveXMLBase(instanceContainerElement, srcAttribute);
                                connectionResult = XFormsSubmissionUtils.doOptimized(pipelineContext, externalContext, null, "get", resolvedURI.toString(), null, false, null, null);

                                instanceSourceURI = resolvedURI.toString();
                                xxformsUsername = null;
                                xxformsPassword = null;

                                try {
                                    // Handle connection errors
                                    if (connectionResult.resultCode != 200) {
                                        final ValidationException validationException = new ValidationException("Got invalid return code while loading instance: " + srcAttribute + ", " + connectionResult.resultCode, locationData);
                                        dispatchXFormsLinkExceptionEvent(pipelineContext, connectionResult, srcAttribute, validationException, instanceContainerElement, locationData);
                                        break;
                                    }

                                    // Read result as XML
                                    if (!isReadonlyHint) {
                                        instanceDocument = TransformerUtils.readDom4j(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                    } else {
                                        instanceDocument = TransformerUtils.readTinyTree(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                    }
                                } catch (Exception e) {
                                    final ValidationException validationException = new ValidationException(e, locationData);
                                    dispatchXFormsLinkExceptionEvent(pipelineContext, connectionResult, srcAttribute, validationException, instanceContainerElement, locationData);
                                    break;
                                } finally {
                                    // Clean-up
                                    if (connectionResult != null)
                                        connectionResult.close();
                                }

                            } else {
                                // Connect using external protocol

                                // Extension: username and password
                                // NOTE: Those don't use AVTs for now, because XPath expressions in those could access
                                // instances that haven't been loaded yet.
                                xxformsUsername = instanceContainerElement.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME);
                                xxformsPassword = instanceContainerElement.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME);

                                final String resolvedURL = XFormsUtils.resolveURL(containingDocument, pipelineContext, instanceContainerElement, false, srcAttribute);

                                final long startTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;
                                if (containingDocument.getURIResolver() == null) {
                                    // We connect directly

                                    connectionResult = XFormsSubmissionUtils.doRegular(externalContext,
                                            "get", resolvedURL, xxformsUsername, xxformsPassword, null, null, null);

                                    try {
                                        // Handle connection errors
                                        if (connectionResult.resultCode != 200) {
                                            final ValidationException validationException = new ValidationException("Got invalid return code while loading instance: " + srcAttribute + ", " + connectionResult.resultCode, locationData);
                                            dispatchXFormsLinkExceptionEvent(pipelineContext, connectionResult, srcAttribute, validationException, instanceContainerElement, locationData);
                                            break;
                                        }

                                        // Read result as XML
                                        if (!isReadonlyHint) {
                                            instanceDocument = TransformerUtils.readDom4j(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                        } else {
                                            instanceDocument = TransformerUtils.readTinyTree(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                        }
                                    } catch (Exception e) {
                                        dispatchXFormsLinkExceptionEvent(pipelineContext, connectionResult, srcAttribute, e, instanceContainerElement, locationData);
                                        break;
                                    } finally {
                                        // Clean-up
                                        if (connectionResult != null)
                                            connectionResult.close();
                                    }

                                    instanceSourceURI = connectionResult.resourceURI;

                                } else {
                                    // Optimized case that uses the provided resolver
                                    final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(resolvedURL);
                                    final String urlString;
                                    if (inputName != null) {
                                        // URL is input:*, keep it as is
                                        urlString = resolvedURL;
                                    } else {
                                        // URL is regular URL, make sure it is absolute
                                        final URL finalURL = XFormsSubmissionUtils.createAbsoluteURL(resolvedURL, null, externalContext);
                                        urlString = finalURL.toExternalForm();
                                    }

                                    if (XFormsServer.logger.isDebugEnabled())
                                        XFormsServer.logger.debug("XForms - getting document from resolver for: " + urlString);

                                    try {
                                        if (!isReadonlyHint) {
                                            instanceDocument = containingDocument.getURIResolver().readURLAsDocument(urlString, xxformsUsername, xxformsPassword);
                                        } else {
                                            instanceDocument = containingDocument.getURIResolver().readURLAsDocumentInfo(urlString, xxformsUsername, xxformsPassword);
                                        }
                                        instanceSourceURI = urlString;
                                    } catch (Exception e) {
                                        final ValidationException validationException = new ValidationException(e, new ExtendedLocationData(new LocationData(urlString, -1, -1),
                                                "reading external instance", instanceContainerElement));
                                        dispatchXFormsLinkExceptionEvent(pipelineContext, new XFormsModelSubmission.ConnectionResult(urlString), srcAttribute, validationException, instanceContainerElement, locationData);
                                        break;
                                    }

                                    if (XFormsServer.logger.isDebugEnabled()) {
                                        final long submissionTime = System.currentTimeMillis() - startTime;
                                        XFormsServer.logger.debug("XForms - instance loading time (including handling returned body): " + submissionTime);
                                    }
                                }
                            }
                        } else {
                            // Got a blank src attribute, just dispatch xforms-link-exception
                            final String instanceId = instanceContainerElement.attributeValue("id");
                            final Throwable throwable = new ValidationException("Invalid blank URL specified for instance: " + instanceId, locationData);
                            containingDocument.dispatchEvent(pipelineContext, new XFormsLinkExceptionEvent(XFormsModel.this, srcAttribute, instanceContainerElement, throwable));
                            break;
                        }
                        // Set instance and associated information if everything went well
                        setInstanceDocument(pipelineContext, instancePosition, instanceDocument, instanceSourceURI, xxformsUsername, xxformsPassword);
                    }
                }
            }

            // Call special listener to update instance
            if (instanceConstructListener != null) {
                int position = 0;
                for (Iterator i = getInstances().iterator(); i.hasNext(); position++) {
                    instanceConstructListener.updateInstance(position, (XFormsInstance) i.next());
                }
            }

            // 3. P3P (N/A)

            // 4. Instance data is constructed. Evaluate binds:
            //    a. Evaluate nodeset
            //    b. Apply model item properties on nodes
            //    c. Throws xforms-binding-exception if the node has already model item property with same name
            // TODO: a, b, c xxx

            // 5. xforms-rebuild, xforms-recalculate, xforms-revalidate
            doRebuild(pipelineContext);
            doRecalculate(pipelineContext);
            doRevalidate(pipelineContext);

            synchronizeInstanceDataEventState();

        } else if (XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE.equals(eventName)) {
            // 4.2.2 The xforms-model-construct-done Event
            // Bubbles: Yes / Cancelable: No / Context Info: None

            // TODO: implicit lazy instance construction

        } else if (XFormsEvents.XFORMS_REBUILD.equals(eventName)) {
            // 4.3.7 The xforms-rebuild Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            doRebuild(pipelineContext);

        } else if (XFormsEvents.XFORMS_RECALCULATE.equals(eventName)) {
            // 4.3.6 The xforms-recalculate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            doRecalculate(pipelineContext);

        } else if (XFormsEvents.XFORMS_REVALIDATE.equals(eventName)) {
            // 4.3.5 The xforms-revalidate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            doRevalidate(pipelineContext);

        } else if (XFormsEvents.XFORMS_REFRESH.equals(eventName)) {
            // 4.3.4 The xforms-refresh Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            doRefresh(pipelineContext);

        } else if (XFormsEvents.XFORMS_RESET.equals(eventName)) {
            // 4.3.8 The xforms-reset Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // TODO
            // "The instance data is reset to the tree structure and values it had immediately
            // after having processed the xforms-ready event."

            // "Then, the events xforms-rebuild, xforms-recalculate, xforms-revalidate and
            // xforms-refresh are dispatched to the model element in sequence."
            containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(XFormsModel.this));
            containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(XFormsModel.this));
            containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(XFormsModel.this));
            containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(XFormsModel.this));

        } else if (XFormsEvents.XFORMS_COMPUTE_EXCEPTION.equals(eventName) || XFormsEvents.XFORMS_LINK_EXCEPTION.equals(eventName)) {
            // 4.5.4 The xforms-compute-exception Event
            // Bubbles: Yes / Cancelable: No / Context Info: Implementation-specific error string.
            // The default action for this event results in the following: Fatal error.

            // 4.5.2 The xforms-link-exception Event
            // Bubbles: Yes / Cancelable: No / Context Info: The URI that failed to load (xsd:anyURI)
            // The default action for this event results in the following: Fatal error.

            final XFormsExceptionEvent exceptionEvent = (XFormsExceptionEvent) event;
            final Throwable throwable = exceptionEvent.getThrowable();
            if (throwable instanceof RuntimeException)
                throw (RuntimeException) throwable;
            else
                throw new ValidationException("Received fatal error event: " + eventName, throwable, (LocationData) modelDocument.getRootElement().getData());
        }
    }

    private void dispatchXFormsLinkExceptionEvent(PipelineContext pipelineContext, XFormsModelSubmission.ConnectionResult connectionResult, String srcAttribute, Exception e, Element instanceContainerElement, LocationData locationData) {
        final Throwable throwable;
        if (connectionResult != null && connectionResult.resourceURI != null) {
            final ValidationException validationException
                = new ValidationException(e, new ExtendedLocationData(new LocationData(connectionResult.resourceURI, -1, -1),
                    "reading external instance", instanceContainerElement));
            validationException.addLocationData(locationData);
            throwable = validationException;
        } else {
            throwable = new ValidationException(e, locationData);
        }
        containingDocument.dispatchEvent(pipelineContext, new XFormsLinkExceptionEvent(XFormsModel.this, srcAttribute, instanceContainerElement, throwable));
    }

    public static class EventSchedule {

        public static final int VALUE = 1;
        public static final int REQUIRED = 2;
        public static final int RELEVANT = 4;
        public static final int READONLY = 8;
        public static final int VALID = 16;

        public static final int RELEVANT_BINDING = 32;

        public static final int ALL = VALUE | REQUIRED | RELEVANT | READONLY | VALID;

        private String effectiveControlId;
        private int type;
        private XFormsControl xformsControl;

        /**
         * Regular constructor.
         */
        public EventSchedule(String effectiveControlId, int type) {
            this.effectiveControlId = effectiveControlId;
            this.type = type;
        }

        /**
         * This special constructor allows passing an XFormsControl we know will become obsolete. This is currently the
         * only way we have to dispatch events to controls that have "disappeared".
         */
        public EventSchedule(String effectiveControlId, int type, XFormsControl xformsControl) {
            this(effectiveControlId, type);
            this.xformsControl = xformsControl;
        }

        public void updateType(int type) {
            if (this.type == RELEVANT_BINDING) {
                // NOP: all events will be sent
            } else {
                // Combine with existing events
                this.type |= type;
            }
        }

        public int getType() {
            return type;
        }

        public String getEffectiveControlId() {
            return effectiveControlId;
        }

        public XFormsControl getXFormsControl() {
            return xformsControl;
        }
    }

    private void synchronizeInstanceDataEventState() {
        if (instances != null) {
            for (Iterator i = instances.iterator(); i.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) i.next();
                currentInstance.synchronizeInstanceDataEventState();
            }
        }
    }

    public void doRebuild(PipelineContext pipelineContext) {
        // TODO: rebuild computational dependency data structures

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (deferredActionContext != null)
            deferredActionContext.rebuild = false;
    }

    public void doRecalculate(PipelineContext pipelineContext) {
        if (instances != null) {
            // NOTE: we do not correctly handle computational dependencies, but it doesn't hurt
            // to evaluate "calculate" binds before the other binds.

            // Clear state
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()), new XFormsUtils.InstanceWalker() {
                    public void walk(NodeInfo nodeInfo, InstanceData updatedInstanceData) {
                        if (updatedInstanceData != null) {
                            updatedInstanceData.clearOtherState();
                        }
                    }
                }, true);
            }

            // Apply calculate binds
            applyCalculateBinds(pipelineContext);

            // Update computed expression binds
            applyComputedExpressionBinds(pipelineContext);
        }

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (deferredActionContext != null)
            deferredActionContext.recalculate = false;
    }


    public void doRevalidate(final PipelineContext pipelineContext) {
        if (instances != null) {

            // Clear validation state
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()), new XFormsUtils.InstanceWalker() {
                    public void walk(NodeInfo nodeInfo, InstanceData updatedInstanceData) {
                        if (updatedInstanceData != null) {
                            updatedInstanceData.clearValidationState();
                        }
                    }
                }, true);
            }

            // Run validation
            applySchemasIfNeeded();
            applyBinds(new BindRunner() {
                public void applyBind(ModelBind modelBind) {
                    handleValidationBind(pipelineContext, modelBind, this);
                }
            });
        }

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (deferredActionContext != null)
            deferredActionContext.revalidate = false;
    }

    public void doRefresh(final PipelineContext pipelineContext) {
        // "1. All UI bindings should be reevaluated as necessary."

        // "2. A node can be changed by confirmed user input to a form control, by
        // xforms-recalculate (section 4.3.6) or by the setvalue (section 10.1.9) action. If the
        // value of an instance data node was changed, then the node must be marked for
        // dispatching the xforms-value-changed event."

        // "3. If the xforms-value-changed event is marked for dispatching, then all of the
        // appropriate model item property notification events must also be marked for
        // dispatching (xforms-optional or xforms-required, xforms-readwrite or xforms-readonly,
        // and xforms-enabled or xforms-disabled)."

        // "4. For each form control, each notification event that is marked for dispatching on
        // the bound node must be dispatched (xforms-value-changed, xforms-valid,
        // xforms-invalid, xforms-optional, xforms-required, xforms-readwrite, xforms-readonly,
        // and xforms-enabled, xforms-disabled). The notification events xforms-out-of-range or
        // xforms-in-range must also be dispatched as appropriate. This specification does not
        // specify an ordering for the events."

        // This just handles the legacy XForms engine which doesn't use the controls
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        if (xformsControls == null)
            return;

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - performing refresh");

        // Rebuild controls if needed
        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

        // Build list of events to send
        final Map relevantBindingEvents = xformsControls.getCurrentControlsState().getEventsToDispatch();
        final List eventsToDispatch = new ArrayList();

        // Iterate through controls and check the nodes they are bound to
        xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
            public void startVisitControl(XFormsControl xformsControl) {
                final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                final String effectiveId = xformsControl.getEffectiveId();

                // This can happen if control is not bound to anything
                if (currentNodeInfo == null)
                    return;

                // We only dispatch value-changed and other events for controls bound to a mutable document
                if (!(currentNodeInfo instanceof NodeWrapper))
                    return;

                final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);

                // Check if value has changed
                final boolean valueChanged = updatedInstanceData.isValueChanged();

                final EventSchedule existingEventSchedule = (relevantBindingEvents == null) ? null : (EventSchedule) relevantBindingEvents.get(effectiveId);

                if (valueChanged) {
                    // Value change takes care of everything
                    if (existingEventSchedule == null)
                        eventsToDispatch.add(new EventSchedule(effectiveId, EventSchedule.ALL));
                    else
                        existingEventSchedule.updateType(EventSchedule.ALL);
                } else {
                    // Dispatch xforms-optional/xforms-required if needed
                    {
                        final boolean previousRequiredState = updatedInstanceData.getPreviousRequiredState();
                        final boolean newRequiredState = updatedInstanceData.getRequired().get();

                        if ((previousRequiredState && !newRequiredState) || (!previousRequiredState && newRequiredState)) {
                            if (existingEventSchedule == null)
                                eventsToDispatch.add(new EventSchedule(effectiveId, EventSchedule.REQUIRED));
                            else
                                existingEventSchedule.updateType(EventSchedule.REQUIRED);
                        }
                    }
                    // Dispatch xforms-enabled/xforms-disabled if needed
                    {
                        final boolean previousRelevantState = updatedInstanceData.getPreviousInheritedRelevantState();
                        final boolean newRelevantState = updatedInstanceData.getInheritedRelevant().get();

                        if ((previousRelevantState && !newRelevantState) || (!previousRelevantState && newRelevantState)) {
                            if (existingEventSchedule == null)
                                eventsToDispatch.add(new EventSchedule(effectiveId, EventSchedule.RELEVANT));
                            else
                                existingEventSchedule.updateType(EventSchedule.RELEVANT);
                        }
                    }
                    // Dispatch xforms-readonly/xforms-readwrite if needed
                    {
                        final boolean previousReadonlyState = updatedInstanceData.getPreviousInheritedReadonlyState();
                        final boolean newReadonlyState = updatedInstanceData.getInheritedReadonly().get();

                        if ((previousReadonlyState && !newReadonlyState) || (!previousReadonlyState && newReadonlyState)) {
                            if (existingEventSchedule == null)
                                eventsToDispatch.add(new EventSchedule(effectiveId, EventSchedule.READONLY));
                            else
                                existingEventSchedule.updateType(EventSchedule.READONLY);
                        }
                    }

                    // Dispatch xforms-valid/xforms-invalid if needed

                    // NOTE: There is no mention in the spec that these events should be
                    // displatched automatically when the value has changed, contrary to the
                    // other events above.
                    {
                        final boolean previousValidState = updatedInstanceData.getPreviousValidState();
                        final boolean newValidState = updatedInstanceData.getValid().get();

                        if ((previousValidState && !newValidState) || (!previousValidState && newValidState)) {
                            if (existingEventSchedule == null)
                                eventsToDispatch.add(new EventSchedule(effectiveId, EventSchedule.VALID));
                            else
                                existingEventSchedule.updateType(EventSchedule.VALID);
                        }
                    }
                }
            }

            public void endVisitControl(XFormsControl XFormsControl) {
            }
        });

        // Clear InstanceData event state
        synchronizeInstanceDataEventState();
        xformsControls.getCurrentControlsState().setEventsToDispatch(null);

        // Add "relevant binding" events
        if (relevantBindingEvents != null)
            eventsToDispatch.addAll(relevantBindingEvents.values());

        // Send events and (try to) make sure the event corresponds to the current instance data
        // NOTE: event order and the exact steps to take are under-specified in 1.0.
        for (Iterator i = eventsToDispatch.iterator(); i.hasNext();) {
            final EventSchedule eventSchedule = (XFormsModel.EventSchedule) i.next();

            final String controlInfoId = eventSchedule.getEffectiveControlId();
            final int type = eventSchedule.getType();
            final boolean isRelevantBindingEvent = (type & EventSchedule.RELEVANT_BINDING) != 0;

            if (!isRelevantBindingEvent) {

                final XFormsControl xformsControl = (XFormsControl) xformsControls.getObjectById(controlInfoId);

                if (xformsControl == null) {
                    // In this case, the algorithm in the spec is not clear. Many thing can have happened between the
                    // initial determination of a control bound to a changing node, and now, including many events and
                    // actions.
                    continue;
                }

                // Re-obtain node to which control is bound, in case things have changed
                final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                if (currentNodeInfo == null) {
                    // See comment above about things that can have happened since.
                    continue;
                }

                // "The XForms processor is not considered to be executing an outermost action handler at the time that it
                // performs deferred update behavior for XForms models. Therefore, event handlers for events dispatched to
                // the user interface during the deferred refresh behavior are considered to be new outermost action
                // handler."

                if ((type & EventSchedule.VALUE) != 0) {
                    containingDocument.dispatchEvent(pipelineContext, new XFormsValueChangeEvent(xformsControl));
                }
                // TODO: after each event, we should get a new reference to the control as it may have changed
                if (currentNodeInfo != null && currentNodeInfo instanceof NodeWrapper) {
                    if ((type & EventSchedule.REQUIRED) != 0) {
                        final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                        final boolean currentRequiredState = updatedInstanceData.getRequired().get();
                        if (currentRequiredState) {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsRequiredEvent(xformsControl));
                        } else {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(xformsControl));
                        }
                    }
                    if ((type & EventSchedule.RELEVANT) != 0) {
                        final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                        final boolean currentRelevantState = updatedInstanceData.getInheritedRelevant().get();
                        if (currentRelevantState) {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsEnabledEvent(xformsControl));
                        } else {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsDisabledEvent(xformsControl));
                        }
                    }
                    if ((type & EventSchedule.READONLY) != 0) {
                        final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                        final boolean currentReadonlyState = updatedInstanceData.getInheritedReadonly().get();
                        if (currentReadonlyState) {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(xformsControl));
                        } else {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(xformsControl));
                        }
                    }
                    if ((type & EventSchedule.VALID) != 0) {
                        final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                        final boolean currentValidState = updatedInstanceData.getValid().get();
                        if (currentValidState) {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(xformsControl));
                        } else {
                            containingDocument.dispatchEvent(pipelineContext, new XFormsInvalidEvent(xformsControl));
                        }
                    }
                }
            } else {
                // Handle special case of "relevant binding" events, i.e. relevance that changes because a node becomes
                // bound or unbound to a node.
                final XFormsControl xformsControl = (XFormsControl) xformsControls.getObjectById(controlInfoId);
                if (xformsControl != null) {
                    final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                    if (currentNodeInfo != null) {
                        final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                        final boolean currentRelevantState = updatedInstanceData.getInheritedRelevant().get();
                        if (currentRelevantState) {
                            // The control is newly bound to a relevant node
                            containingDocument.dispatchEvent(pipelineContext, new XFormsEnabledEvent(xformsControl));

                            // Also send other MIP events
                            final boolean currentRequiredState = updatedInstanceData.getRequired().get();
                            if (currentRequiredState) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsRequiredEvent(xformsControl));
                            } else {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(xformsControl));
                            }

                            final boolean currentReadonlyState = updatedInstanceData.getInheritedReadonly().get();
                            if (currentReadonlyState) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(xformsControl));
                            } else {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(xformsControl));
                            }

                            final boolean currentValidState = updatedInstanceData.getValid().get();
                            if (currentValidState) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(xformsControl));
                            } else {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsInvalidEvent(xformsControl));
                            }
                        }
                    } else {
                        // The control is not bound to a node
                        sendDefaultEventsForDisabledControl(pipelineContext, xformsControl);
                    }
                } else {
                    // The control no longer exists
                    if (eventSchedule.getXFormsControl() != null) {
                        sendDefaultEventsForDisabledControl(pipelineContext, eventSchedule.getXFormsControl());
                    }
                }
            }
        }

        // "5. The user interface reflects the state of the model, which means that all forms
        // controls reflect for their corresponding bound instance data:"
        if (xformsControls != null) {
            xformsControls.refreshForModel(pipelineContext, this);
        }

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (deferredActionContext != null)
            deferredActionContext.refresh = false;
    }

    private void sendDefaultEventsForDisabledControl(PipelineContext pipelineContext, XFormsControl xformsControl) {
        // Control is disabled
        containingDocument.dispatchEvent(pipelineContext, new XFormsDisabledEvent(xformsControl));

        // Send events for default MIP values
        containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(xformsControl));
        containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(xformsControl));
        containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(xformsControl));
    }

    /**
     * Handle events related to externally updating one or more instance documents.
     */
    public void handleNewInstanceDocuments(PipelineContext pipelineContext ) {

        containingDocument.getXFormsControls().markDirty();

        // "Once the XML instance data has been replaced, the rebuild, recalculate, revalidate and refresh operations
        // are performed on the model, without dispatching events to invoke those four operations."

        doRebuild(pipelineContext);
        doRecalculate(pipelineContext);
        doRevalidate(pipelineContext);
        doRefresh(pipelineContext);
    }

    private DeferredActionContext deferredActionContext;

    public static class DeferredActionContext {
        public boolean rebuild;
        public boolean recalculate;
        public boolean revalidate;
        public boolean refresh;

        public void setAllDeferredFlags(boolean value) {
            rebuild = value;
            recalculate = value;
            revalidate = value;
            refresh = value;
        }
    }

    public DeferredActionContext getDeferredActionContext() {
        return deferredActionContext;
    }

    public void setAllDeferredFlags(boolean value) {
        if (deferredActionContext != null)
            deferredActionContext.setAllDeferredFlags(value);
    }

    public void startOutermostActionHandler() {
        if (deferredActionContext == null)
            deferredActionContext = new DeferredActionContext();
    }

    public void endOutermostActionHandler(PipelineContext pipelineContext) {

        // TODO: This is not 100% in line with the "correct" interpretation of the deferred updates, as deferred
        // behavior is triggered at the level of outermost action handlers, not outermost event dispatches.

        // TODO: upon recursion, it looks like currentDeferredActionContext can be null and it is not clear why

        // Process deferred behavior
        final DeferredActionContext currentDeferredActionContext = deferredActionContext;
        deferredActionContext = null;
        if (currentDeferredActionContext != null) {
            if (currentDeferredActionContext.rebuild) {
                containingDocument.startOutermostActionHandler();
                containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(this));
                containingDocument.endOutermostActionHandler(pipelineContext);
            }
            if (currentDeferredActionContext.recalculate) {
                containingDocument.startOutermostActionHandler();
                containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(this));
                containingDocument.endOutermostActionHandler(pipelineContext);
            }
            if (currentDeferredActionContext.revalidate) {
                containingDocument.startOutermostActionHandler();
                containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this));
                containingDocument.endOutermostActionHandler(pipelineContext);
            }
            if (currentDeferredActionContext.refresh) {
                containingDocument.startOutermostActionHandler();
                containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(this));
                containingDocument.endOutermostActionHandler(pipelineContext);
            }
        }
    }

    public void processDeferredUpdates(PipelineContext pipelineContext) {

    }

    /**
     * This class is cloneable.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static interface InstanceConstructListener {
        public void updateInstance(int position, XFormsInstance instance);
    }

    public void setInstanceConstructListener(InstanceConstructListener instanceConstructListener) {
        this.instanceConstructListener = instanceConstructListener;
    }
}
