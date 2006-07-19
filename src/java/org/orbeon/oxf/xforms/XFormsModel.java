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
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.controls.ControlInfo;
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

    // XPath evaluator
    private DocumentXPathEvaluator documentXPathEvaluator = new DocumentXPathEvaluator();

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

    public DocumentXPathEvaluator getEvaluator() {
        return documentXPathEvaluator;
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
        return (XFormsInstance) (instancesMap.get(instanceId));
    }

    /**
     * Return the XFormsInstance object containing the given node.
     */
    public XFormsInstance getInstanceForNode(NodeInfo nodeInfo) {

        final DocumentInfo documentInfo = nodeInfo.getDocumentRoot();

        if (instances != null) {
            for (Iterator i = instances.iterator(); i.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) i.next();
                if (currentInstance.getInstanceDocumentInfo() == documentInfo)
                    return currentInstance;
            }
        }

        return null;
    }

    /**
     * Set an instance document for this model. There may be multiple instance documents. Each
     * instance document may have an associated id that identifies it.
     */
    public void setInstanceDocument(PipelineContext pipelineContext, int instancePosition, Object instanceDocument, String instanceSourceURI, boolean hasUsername) {
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
                newInstance = new XFormsInstance(pipelineContext, instanceId, (Document) instanceDocument, instanceSourceURI, hasUsername, this);
            else if (instanceDocument instanceof DocumentInfo)
                newInstance = new XFormsInstance(pipelineContext, instanceId, (DocumentInfo) instanceDocument, instanceSourceURI, hasUsername, this);
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
                        final String stringResult = getEvaluator().evaluateAsString(pipelineContext, nodeInfo, modelBind.getCalculate(), modelBind.getNamespaceMap(), null,
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
                        final boolean required = ((Boolean) getEvaluator().evaluateSingle(pipelineContext,
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
                        boolean relevant = ((Boolean) getEvaluator().evaluateSingle(pipelineContext,
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
                        boolean readonly = ((Boolean) getEvaluator().evaluateSingle(pipelineContext,
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
                        boolean xxformsExternalize = ((Boolean) getEvaluator().evaluateSingle(pipelineContext,
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
                        final Boolean valid = (Boolean) getEvaluator().evaluateSingle(pipelineContext,
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
            final List nodeset = getEvaluator().evaluate(pipelineContext,
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
            final List nodeset = getEvaluator().evaluate(pipelineContext,
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

    public String getId() {
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

        // Find the final node
        final List nodeset = new ArrayList();
        final XFormsInstance defaultInstance = getDefaultInstance();
        nodeset.add(defaultInstance.getInstanceDocumentInfo());
        for (Iterator i = parents.iterator(); i.hasNext();) {
            final ModelBind current = (ModelBind) i.next();
            final List currentModelBindResults = new ArrayList();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo node = (NodeInfo) j.next();
                // Execute XPath expresssion
                currentModelBindResults.addAll(defaultInstance.getEvaluator().evaluate(pipelineContext, node, current.getNodeset(),
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

                        final Object instanceDocument;// Document or DocumentInfo
                        final String instanceSourceURI;
                        final boolean hasUsername;
                        if (srcAttribute == null) {
                            // Inline instance
                            final List children = instanceContainerElement.elements();
                            if (children == null || children.size() == 0)
                                throw new OXFException("xforms:instance element must contain exactly one child element");// TODO: Throw XForms event?
                            instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) children.get(0));
                            // TODO: support DocumentInfo (easier once static state is done with TinyTree as well)
                            instanceSourceURI = null;
                            hasUsername = false;
                        } else {
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
                                final URI resolvedURI = XFormsUtils.resolveURI(instanceContainerElement, srcAttribute);
                                connectionResult = XFormsSubmissionUtils.doOptimized(pipelineContext, externalContext, null, "get", resolvedURI.toString(), null, false, null, null);

                                instanceSourceURI = resolvedURI.toString();
                                hasUsername = false;

                                try {
                                    // Handle connection errors
                                    if (connectionResult.resultCode != 200)
                                        throw new OXFException("Got invalid return code while loading instance: " + srcAttribute + ", " + connectionResult.resultCode);

                                    // Read result as XML
                                    if (!isReadonlyHint) {
                                        instanceDocument = TransformerUtils.readDom4j(connectionResult.resultInputStream, connectionResult.resourceURI);
                                    } else {
                                        instanceDocument = TransformerUtils.readTinyTree(connectionResult.resultInputStream, connectionResult.resourceURI);
                                    }
                                } catch (Exception e) {
                                    throw new OXFException(e);
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
                                final String xxformsUsername = instanceContainerElement.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME);
                                final String xxformsPassword = instanceContainerElement.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME);

                                final String resolvedURL = XFormsUtils.resolveURL(containingDocument, pipelineContext, instanceContainerElement, false, srcAttribute);

                                if (containingDocument.getURIResolver() == null || xxformsUsername != null) {
                                    // We connect directly

                                    // TODO: We should be able to use the username/password as part of the cache key,
                                    // and therefore to use the resolver (or an extension of it supporting
                                    // username/password) when provided.

                                    connectionResult = XFormsSubmissionUtils.doRegular(pipelineContext, externalContext,
                                            "get", resolvedURL, xxformsUsername, xxformsPassword, null, false, null, null);

                                    try {
                                        // Handle connection errors
                                        if (connectionResult.resultCode != 200)
                                            throw new OXFException("Got invalid return code while loading instance: " + srcAttribute + ", " + connectionResult.resultCode);

                                        // Read result as XML
                                        if (!isReadonlyHint) {
                                            instanceDocument = TransformerUtils.readDom4j(connectionResult.resultInputStream, connectionResult.resourceURI);
                                        } else {
                                            instanceDocument = TransformerUtils.readTinyTree(connectionResult.resultInputStream, connectionResult.resourceURI);
                                        }
                                    } catch (Exception e) {
                                        if (connectionResult != null && connectionResult.resourceURI != null)
                                            throw new ValidationException(e, new ExtendedLocationData(new LocationData(connectionResult.resourceURI, -1, -1),
                                                    "reading external instance", instanceContainerElement));
                                        else
                                            throw new OXFException(e);
                                    } finally {
                                        // Clean-up
                                        if (connectionResult != null)
                                            connectionResult.close();
                                    }

                                    hasUsername = true;
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
                                            instanceDocument = TransformerURIResolver.readURLAsDocument(containingDocument.getURIResolver(), urlString);
                                        } else {
                                            instanceDocument = TransformerURIResolver.readURLAsDocumentInfo(containingDocument.getURIResolver(), urlString);
                                        }
                                        hasUsername = false;
                                        instanceSourceURI = urlString;
                                    } catch (Exception e) {
                                        throw new ValidationException(e, new ExtendedLocationData(new LocationData(urlString, -1, -1),
                                                "reading external instance", instanceContainerElement));
                                    }
                                }
                            }
                        }
                        // Set instance and associated information
                        setInstanceDocument(pipelineContext, instancePosition, instanceDocument, instanceSourceURI, hasUsername);
                    }
                }
            }
            // TODO: throw exception event

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

            // TODO: if instance exists (for now it does!), check that controls can bind, otherwise control must be "irrelevant"
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

        } else if (XFormsEvents.XFORMS_COMPUTE_EXCEPTION.equals(eventName)) {
            // 4.5.4 The xforms-compute-exception Event
            // Bubbles: Yes / Cancelable: No / Context Info: Implementation-specific error string.
            // The default action for this event results in the following: Fatal error.

            // TODO

        }
    }

    private static class EventSchedule {

        public static final int VALUE = 1;
        public static final int REQUIRED = 2;
        public static final int RELEVANT = 4;
        public static final int READONLY = 8;
        public static final int VALID = 16;

        public static final int ALL = VALUE | REQUIRED | RELEVANT | READONLY | VALID;

        private String controlInfoId;
        private Node node;
        private int type;

        public EventSchedule(String controlInfoId, Node node, int type) {
            this.controlInfoId = controlInfoId;
            this.node = node;
            this.type = type;
        }

        public Node getNode() {
            return node;
        }

        public int getType() {
            return type;
        }

        public String getControlInfoId() {
            return controlInfoId;
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
        getDeferredActionContext().rebuild = false;
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
        getDeferredActionContext().recalculate = false;
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
        getDeferredActionContext().revalidate = false;
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

        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        if (xformsControls == null)
            return;

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - performing refresh");

        // Rebuild controls if needed
        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

        // Build list of events to send
        final List eventsToDispatch = new ArrayList();

        // Iterate through controls and check the nodes they are bound to
        xformsControls.visitAllControlInfo(new XFormsControls.ControlInfoVisitorListener() {
            public void startVisitControl(ControlInfo controlInfo) {
                xformsControls.setBinding(pipelineContext, controlInfo);
                final NodeInfo currentNodeInfo = xformsControls.getCurrentSingleNode();

                // This can happen if control is not bound to anything
                if (currentNodeInfo == null)
                    return;

                // We only dispatch value-changed events for controls bound to a mutable document
                if (!(currentNodeInfo instanceof NodeWrapper))
                    return;

                final Node currentNode = (Node) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();// NOTE: we don't actually use the node later below!

                final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);

                // Check if value has changed
                final boolean valueChanged = updatedInstanceData.isValueChanged();

                // TODO: should check whether value of control has changed, not node.
                // However, is this compatible with with the way we rebuild the controls?

                if (valueChanged) {
                    // Value change takes care of everything
                    eventsToDispatch.add(new EventSchedule(controlInfo.getId(), currentNode, EventSchedule.ALL));
                } else {
                    // Dispatch xforms-optional/xforms-required if needed
                    {
                        final boolean previousRequiredState = updatedInstanceData.getPreviousRequiredState();
                        final boolean newRequiredState = updatedInstanceData.getRequired().get();

                        if ((previousRequiredState && !newRequiredState) || (!previousRequiredState && newRequiredState))
                            eventsToDispatch.add(new EventSchedule(controlInfo.getId(), currentNode, EventSchedule.REQUIRED));
                    }
                    // Dispatch xforms-enabled/xforms-disabled if needed
                    {
                        final boolean previousRelevantState = updatedInstanceData.getPreviousInheritedRelevantState();
                        final boolean newRelevantState = updatedInstanceData.getInheritedRelevant().get();

                        if ((previousRelevantState && !newRelevantState) || (!previousRelevantState && newRelevantState))
                            eventsToDispatch.add(new EventSchedule(controlInfo.getId(), currentNode, EventSchedule.RELEVANT));
                    }
                    // Dispatch xforms-readonly/xforms-readwrite if needed
                    {
                        final boolean previousReadonlyState = updatedInstanceData.getPreviousInheritedReadonlyState();
                        final boolean newReadonlyState = updatedInstanceData.getInheritedReadonly().get();

                        if ((previousReadonlyState && !newReadonlyState) || (!previousReadonlyState && newReadonlyState))
                            eventsToDispatch.add(new EventSchedule(controlInfo.getId(), currentNode, EventSchedule.READONLY));
                    }

                    // Dispatch xforms-valid/xforms-invalid if needed

                    // NOTE: There is no mention in the spec that these events should be
                    // displatched automatically when the value has changed, contrary to the
                    // other events above.
                    {
                        final boolean previousValidState = updatedInstanceData.getPreviousValidState();
                        final boolean newValidState = updatedInstanceData.getValid().get();

                        if ((previousValidState && !newValidState) || (!previousValidState && newValidState))
                            eventsToDispatch.add(new EventSchedule(controlInfo.getId(), currentNode, EventSchedule.VALID));
                    }
                }
            }

            public void endVisitControl(ControlInfo controlInfo) {
            }
        });
        

        // Clear InstanceData event state
        synchronizeInstanceDataEventState();

        // Send events and (try to) make sure the event corresponds to the current instance data
        // NOTE: deferred behavior is broken in XForms 1.0; 1.1 should introduce better
        // behavior. Also, event order and the exact steps to take are under-specified in 1.0.
        for (Iterator i = eventsToDispatch.iterator(); i.hasNext();) {
            final EventSchedule eventSchedule = (XFormsModel.EventSchedule) i.next();

            final String controlInfoId = eventSchedule.getControlInfoId();
            final ControlInfo controlInfo = (ControlInfo) xformsControls.getObjectById(controlInfoId);

            if (controlInfo == null) {
                // In this case, the algorithm in the spec is not clear. Many thing can have happened between the
                // initial determination of a control bound to a changing node, and now, including many events and
                // actions.
                continue;
            }

            // "The XForms processor is not considered to be executing an outermost action handler at the time that it
            // performs deferred update behavior for XForms models. Therefore, event handlers for events dispatched to
            // the user interface during the deferred refresh behavior are considered to be new outermost action
            // handler."
            // TODO: It doesn't really look like we are allowed to do a single start/end around multiple events below. Check that.
            containingDocument.startOutmostActionHandler();

            // Re-obtain node to which control is bound, in case things have shifted
            xformsControls.setBinding(pipelineContext, controlInfo);
            final NodeInfo currentNodeInfo = xformsControls.getCurrentSingleNode();

            final int type = eventSchedule.getType();
            if ((type & EventSchedule.VALUE) != 0) {
                containingDocument.dispatchEvent(pipelineContext, new XFormsValueChangeEvent(controlInfo));
            }
            if (currentNodeInfo != null && currentNodeInfo instanceof NodeWrapper) {

//                    final Node currentNode = (Node) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

                if ((type & EventSchedule.REQUIRED) != 0) {
                    final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                    final boolean currentRequiredState = updatedInstanceData.getRequired().get();
                    if (currentRequiredState) {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsRequiredEvent(controlInfo));
                    } else {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(controlInfo));
                    }
                }
                if ((type & EventSchedule.RELEVANT) != 0) {
                    final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                    final boolean currentRelevantState = updatedInstanceData.getInheritedRelevant().get();
                    if (currentRelevantState) {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsEnabledEvent(controlInfo));
                    } else {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsDisabledEvent(controlInfo));
                    }
                }
                if ((type & EventSchedule.READONLY) != 0) {
                    final InstanceData updatedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                    final boolean currentReadonlyState = updatedInstanceData.getInheritedReadonly().get();
                    if (currentReadonlyState) {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(controlInfo));
                    } else {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(controlInfo));
                    }
                }
                if ((type & EventSchedule.VALID) != 0) {
                    final InstanceData inheritedInstanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNodeInfo);
                    final boolean currentValidState = inheritedInstanceData.getValid().get();
                    if (currentValidState) {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(controlInfo));
                    } else {
                        containingDocument.dispatchEvent(pipelineContext, new XFormsInvalidEvent(controlInfo));
                    }
                }
            }

            containingDocument.endOutmostActionHandler(pipelineContext);
        }

        // "5. The user interface reflects the state of the model, which means that all forms
        // controls reflect for their corresponding bound instance data:"
        if (xformsControls != null) {
            containingDocument.getXFormsControls().refreshForModel(pipelineContext, this);
        }

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        getDeferredActionContext().refresh = false;
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
        if (deferredActionContext == null)
            deferredActionContext = new DeferredActionContext();
        return deferredActionContext;
    }

    public void startOutmostActionHandler() {
        getDeferredActionContext().setAllDeferredFlags(false);
    }

    public void endOutmostActionHandler(PipelineContext pipelineContext) {
        // Process deferred behavior
        final DeferredActionContext deferredActionContext = getDeferredActionContext();
        if (deferredActionContext.rebuild)
            containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(this));
        if (deferredActionContext.recalculate)
            containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(this));
        if (deferredActionContext.revalidate)
            containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this));
        if (deferredActionContext.refresh)
            containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(this));
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
