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
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.XPathException;

import java.util.*;
import java.net.URI;

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
            final ModelBind modelBind = new ModelBind(bindElement.attributeValue("id"), bindElement.attributeValue("nodeset"),
                    bindElement.attributeValue("relevant"), bindElement.attributeValue("calculate"), bindElement.attributeValue("type"),
                    bindElement.attributeValue("constraint"), bindElement.attributeValue("required"), bindElement.attributeValue("readonly"),
                    Dom4jUtils.getNamespaceContextNoDefault(bindElement),
                    new ExtendedLocationData((LocationData) bindElement.getData(), "xforms:bind element", bindElement), parent);
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
    public XFormsInstance getInstanceForNode(Node node) {
        final Document document = node.getDocument();

        if (instances != null) {
            for (Iterator i = instances.iterator(); i.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) i.next();
                if (currentInstance.getDocument() == document)
                    return currentInstance;
            }
        }

        return null;
    }

    /**
     * Set an instance document for this model. There may be multiple instance documents. Each
     * instance document may have an associated id that identifies it.
     */
    public void setInstanceDocument(PipelineContext pipelineContext, int instancePosition, Document instanceDocument) {
        // Initialize containers if needed
        if (instances == null) {
            instances = Arrays.asList(new XFormsInstance[instanceIds.size()]);
            instancesMap = new HashMap(instanceIds.size());
        }
        // Prepare and set instance
        final String instanceId = (String) instanceIds.get(instancePosition);
        XFormsInstance newInstance = new XFormsInstance(pipelineContext, instanceId, instanceDocument, this);
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
            public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                handleCalculateBinds(pipelineContext, modelBind, documentWrapper, this);
            }
        });
    }

    /**
     * Apply required, relevant and readonly binds.
     */
    public void applyComputedExpressionBinds(final PipelineContext pipelineContext) {
        applyBinds(new BindRunner() {
            public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                handleComputedExpressionBinds(pipelineContext, modelBind, documentWrapper, this);
            }
        });
    }

    private static interface BindRunner {
        public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper);
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
                    // Create XPath evaluator for this bind
                    final DocumentWrapper documentWrapper = new DocumentWrapper(getDefaultInstance().getDocument(), null);
                    bindRunner.applyBind(modelBind, documentWrapper);
                } catch (final Exception e) {
                    throw new ValidationException(e, modelBind.getLocationData());
                }
            }
        }
    }

    private void handleCalculateBinds(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {
        // Handle calculate MIP
        if (modelBind.getCalculate() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Compute calculated value
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), "string(" + modelBind.getCalculate() + ")", modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                    try {
                        final Object result = expr.evaluateSingle();
                        final String stringResult = result.toString(); // even with string(), the result may not be a Java String object
                        // Place in element
                        XFormsInstance.setValueForNode(pipelineContext, node, stringResult, null);
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getCalculate() + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, documentWrapper, bindRunner);
    }

    private void handleComputedExpressionBinds(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {

        // Handle required MIP
        if (modelBind.getRequired() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate "required" XPath expression on this node
                    String xpath = "boolean(" + modelBind.getRequired() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                    try {
                        // Mark node
                        final boolean required = ((Boolean) expr.evaluateSingle()).booleanValue();
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);
                        instanceData.updateRequired(required, node, modelBind.getId());
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        // Handle relevant MIP
        if (modelBind.getRelevant() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate "relevant" XPath expression on this node
                    String xpath = "boolean(" + modelBind.getRelevant() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                    try {
                        boolean relevant = ((Boolean) expr.evaluateSingle()).booleanValue();
                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);
                        instanceData.getRelevant().set(relevant);
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        // Handle readonly MIP
        if (modelBind.getReadonly() != null) {
            // The bind has a readonly attribute
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate "readonly" XPath expression on this node
                    String xpath = "boolean(" + modelBind.getReadonly() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                    try {
                        boolean readonly = ((Boolean) expr.evaluateSingle()).booleanValue();

                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);
                        instanceData.getReadonly().set(readonly);
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        } else if (modelBind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Mark node
                    InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                    instanceData.getReadonly().set(true);
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, documentWrapper, bindRunner);
    }

    private void handleValidationBind(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {

        // Handle XPath constraint MIP
        if (modelBind.getConstraint() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate constraint
                    String xpath = "boolean(" + modelBind.getConstraint() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                    try {
                        final Boolean valid = (Boolean) expr.evaluateSingle();
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                        instanceData.updateConstraint(valid.booleanValue(), node, modelBind.getId());
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        // Handle type MIP
        if (modelBind.getType() != null) {

            // Need an evaluator to check and convert type below
            final XPathEvaluator xpathEvaluator;
            try {
                xpathEvaluator= new XPathEvaluator(documentWrapper);
                StandaloneContext context = (StandaloneContext) xpathEvaluator.getStaticContext();
                for (Iterator j = modelBind.getNamespaceMap().keySet().iterator(); j.hasNext();) {
                    String prefix = (String) j.next();
                    context.declareNamespace(prefix, (String) modelBind.getNamespaceMap().get(prefix));
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }

            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {

                    // Get type information
                    int requiredType = -1;
                    boolean foundType = false;
                    {
                        String type = modelBind.getType();
                        int prefixPosition = type.indexOf(':');
                        if (prefixPosition > 0) {
                            String prefix = type.substring(0, prefixPosition);
                            String namespace = (String) modelBind.getNamespaceMap().get(prefix);
                            if (namespace == null)
                                throw new ValidationException("Namespace not declared for prefix '" + prefix + "'",
                                        modelBind.getLocationData());
                            ItemType itemType = Type.getBuiltInItemType((String) modelBind.getNamespaceMap().get(prefix),
                                    type.substring(prefixPosition + 1));
                            if (itemType != null) {
                                requiredType = itemType.getPrimitiveType();
                                foundType = true;
                            }
                        }
                    }
                    if (!foundType)
                        throw new ValidationException("Invalid type '" + modelBind.getType() + "'",
                                modelBind.getLocationData());

                    // Pass-through the type value
                    InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                    instanceData.getType().set(requiredType);

                    // Try to perform casting
                    final String nodeStringValue = node.getStringValue().trim();
                    if (XFormsUtils.getLocalInstanceData(node).getRequired().get() || nodeStringValue.length() != 0) {
                        try {
                            StringValue stringValue = new StringValue(nodeStringValue);
                            XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getStaticContext().getConfiguration());
                            // TODO: we don't do anything with the result value here?
                            stringValue.convert(requiredType, xpContext);
                            instanceData.updateValueValid(true, node, modelBind.getId());
                        } catch (XPathException e) {
                            instanceData.updateValueValid(false, node, modelBind.getId());
                        }
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, documentWrapper, bindRunner);
    }

    private void handleChildrenBinds(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {
        // Handle children binds
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                documentWrapper.wrap(modelBind.getCurrentNode() == null
                        ? ((Document) documentWrapper.getUnderlyingNode()).getRootElement()
                        : modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
        try {
            List nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                for (Iterator childIterator = modelBind.getChildren().iterator(); childIterator.hasNext();) {
                    ModelBind child = (ModelBind) childIterator.next();
                    child.setCurrentNode(node);
                    bindRunner.applyBind(child, documentWrapper);
                }
            }
        } catch (XPathException e) {
            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getNodeset() + "'", modelBind.getLocationData());
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    private void iterateNodeSet(PipelineContext pipelineContext, DocumentWrapper documentWrapper,
                                ModelBind modelBind, NodeHandler nodeHandler) {
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                documentWrapper.wrap(modelBind.getCurrentNode() == null
                        ? ((Document) documentWrapper.getUnderlyingNode()).getRootElement()
                        : modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary);
        try {
            List nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                nodeHandler.handleNode(node);
            }
        } catch (XPathException e) {
            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getNodeset() + "'", modelBind.getLocationData());
        } finally {
            if (expr != null)
                expr.returnToPool();
        }

    }

    private interface NodeHandler {
        void handleNode(Node node);
    }

    public String getId() {
        return modelId;
    }

    public LocationData getLocationData() {
        return (LocationData) modelDocument.getRootElement().getData();
    }

    public List getBindNodeset(PipelineContext pipelineContext, ModelBind bind) {
        // Get a list of parents, ordered by grandfather first
        List parents = new ArrayList();
        parents.add(bind);
        ModelBind parent = bind;
        while ((parent = parent.getParent()) != null) {
            parents.add(parent);
        }
        Collections.reverse(parents);

        // Find the final node
        final List nodeset = new ArrayList();
        final XFormsInstance defaultInstance = getDefaultInstance();
        nodeset.add(defaultInstance.getDocument());
        for (Iterator i = parents.iterator(); i.hasNext();) {
            ModelBind current = (ModelBind) i.next();
            List currentModelBindResults = new ArrayList();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                // Execute XPath expresssion
                currentModelBindResults.addAll(defaultInstance.evaluateXPath(pipelineContext, node, current.getNodeset(),
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
            schemaValidator = new XFormsModelSchemaValidator();
            schemaValidator.loadSchemas(pipelineContext, containingDocument, modelElement);
        }
    }

    private void applySchemasIfNeeded() {
        // Don't do anything if there is no schema
        if (schemaValidator != null) {
            // Apply schemas to all instances
            for (Iterator i = getInstances().iterator(); i.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) i.next();
                schemaValidator.applySchema(currentInstance);
            }
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

    public void performDefaultAction(final PipelineContext pipelineContext, XFormsEvent event) {
        final String eventName = event.getEventName();
        if (XFormsEvents.XXFORMS_INITIALIZE_STATE.equals(eventName)) {
            // Internal event to restore state

            loadSchemasIfNeeded(pipelineContext);
            applyComputedExpressionBinds(pipelineContext);
            containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this, false));
            clearInstanceDataEventState();

        } else if (XFormsEvents.XFORMS_MODEL_CONSTRUCT.equals(eventName)) {
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

            if (instances == null) {
                // Build initial instance document
                final List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
                if (instanceContainers.size() > 0) {
                    // Iterate through all instances
                    int instancePosition = 0;
                    for (Iterator i = instanceContainers.iterator(); i.hasNext(); instancePosition++) {
                        final Element instanceContainerElement = (Element) i.next();
                        final String srcAttribute = instanceContainerElement.attributeValue("src");
                        final Document instanceDocument;
                        //= ProcessorUtils.createDocumentFromEmbeddedOrHref(Element element, String urlString) {
                        if (srcAttribute == null) {
                            // Inline instance
                            final List children = instanceContainerElement.elements();
                            if (children == null || children.size() == 0)
                                throw new OXFException("xforms:instance element must contain exactly one child element");// TODO: Throw XForms event?
                            instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) children.get(0));
                        } else {
                            // External instance
                            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                            final boolean optimize = !NetUtils.urlHasProtocol(srcAttribute)
                               && (externalContext.getRequest().getContainerType().equals("portlet")
                                    || (externalContext.getRequest().getContainerType().equals("servlet")
                                        && XFormsUtils.isOptimizeLocalInstanceLoads()));

                            final XFormsModelSubmission.ConnectionResult connectionResult;
                            if (optimize) {
                                // Use optimized local mode
                                final URI resolvedURI = XFormsUtils.resolveURI(instanceContainerElement, srcAttribute);
                                connectionResult = XFormsSubmissionUtils.doOptimized(pipelineContext, externalContext, null, "get", resolvedURI.toString(), null, false, null, null);
                            } else {
                                // Connect using actual external protocol
                                final String resolvedURL = XFormsUtils.resolveURL(containingDocument, pipelineContext, instanceContainerElement, false, srcAttribute);
                                connectionResult = XFormsSubmissionUtils.doRegular(pipelineContext, externalContext, "get", resolvedURL, null, false, null, null);
                            }

                            try {
                                // Handle connection errors
                                if (connectionResult.resultCode != 200)
                                    throw new OXFException("Got invalid return code while loading instance: " + srcAttribute + ", " + connectionResult.resultCode);

                                // Read result as XML
                                instanceDocument = Dom4jUtils.read(connectionResult.resultInputStream, connectionResult.resourceURI);
                            } catch (Exception e) {
                                throw new OXFException(e);
                            } finally {
                                // Clean-up
                                if (connectionResult != null)
                                    connectionResult.close();
                            }
                        }
                        setInstanceDocument(pipelineContext, instancePosition, instanceDocument);
                    }
                }
            }
            // TODO: throw exception event

            // Call special listener to update instance
            if (instanceConstructListener != null) {
                final InstanceConstructListener listener = instanceConstructListener;
                // Make sure we don't keep a reference on this in case this is cache (legacy XForms engine)
                instanceConstructListener = null;
                // Use listener to update instances
                for (Iterator i = getInstances().iterator(); i.hasNext();) {
                    listener.updateInstance((XFormsInstance) i.next());
                }
            }

            // 3. P3P (N/A)

            // 4. Instance data is constructed. Evaluate binds:
            //    a. Evaluate nodeset
            //    b. Apply model item properties on nodes
            //    c. Throws xforms-binding-exception if the node has already model item property with same name
            // TODO: a, b, c xxx

            // 5. xforms-rebuild, xforms-recalculate, xforms-revalidate
            containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(this));
            containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(this, false));
            containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this, false));
//            clearInstanceDataEventState();

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
            containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(XFormsModel.this, true));
            containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(XFormsModel.this, true));
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

        private int type;
        private Node node;

        public EventSchedule(Node node, int type) {
            this.node = node;
            this.type = type;
        }

        public Node getNode() {
            return node;
        }

        public int getType() {
            return type;
        }
    }

    private void clearInstanceDataEventState() {
        if (instances != null) {
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                    public void walk(Node node, InstanceData localInstanceData, InstanceData inheritedInstanceData) {
                        if (localInstanceData != null) {
                            localInstanceData.clearInstanceDataEventState();
                        }
                    }
                }, false);
            }
        }
    }

    public void doRebuild(PipelineContext pipelineContext) {
        // TODO: rebuild computational dependency data structures
    }

    public void doRecalculate(PipelineContext pipelineContext) {
        if (instances != null) {
            // NOTE: we do not correctly handle computational dependencies, but it doesn't hurt
            // to evaluate "calculate" binds before the other binds.

            // Clear state
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                    public void walk(Node node, InstanceData localInstanceData, InstanceData inheritedInstanceData) {
                        if (localInstanceData != null) {
                            localInstanceData.clearOtherState();
                        }
                    }
                }, true);
            }

            // Apply calculate binds
            applyCalculateBinds(pipelineContext);

            // Update computed expression binds
            applyComputedExpressionBinds(pipelineContext);
        }
    }

    public void doRevalidate(final PipelineContext pipelineContext) {
        if (instances != null) {

            // Clear validation state
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                    public void walk(Node node, InstanceData localInstanceData, InstanceData inheritedInstanceData) {
                        if (localInstanceData != null) {
                            localInstanceData.clearValidationState();
                        }
                    }
                }, true);
            }

            // Run validation
            applySchemasIfNeeded();
            applyBinds(new BindRunner() {
                public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                    handleValidationBind(pipelineContext, modelBind, documentWrapper, this);
                }
            });
        }
    }

    public void doRefresh(PipelineContext pipelineContext) {
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
        if (xformsControls != null) {

            // Build list of events to send
            // TODO: should reverse way this is doing, and iterate through controls instead of iterating through instance nodes
            final List eventsToDispatch = new ArrayList();
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.iterateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {

                    public void walk(Node node, InstanceData localInstanceData, InstanceData inheritedInstanceData) {

                        // Check if value has changed
                        final boolean valueChanged = inheritedInstanceData.isValueChanged();

                        if (valueChanged) {
                            // Value change takes care of everything
                            eventsToDispatch.add(new EventSchedule(node, EventSchedule.ALL));
                        } else {
                            // Dispatch xforms-optional/xforms-required if needed
                            {
                                final boolean previousRequiredState = inheritedInstanceData.getPreviousRequiredState();
                                final boolean newRequiredState = inheritedInstanceData.getRequired().get();

                                if ((previousRequiredState && !newRequiredState) || (!previousRequiredState && newRequiredState))
                                    eventsToDispatch.add(new EventSchedule(node, EventSchedule.REQUIRED));
                            }
                            // Dispatch xforms-enabled/xforms-disabled if needed
                            {
                                final boolean previousRelevantState = inheritedInstanceData.getPreviousRelevantState();
                                final boolean newRelevantState = inheritedInstanceData.getRelevant().get();

                                if ((previousRelevantState && !newRelevantState) || (!previousRelevantState && newRelevantState))
                                    eventsToDispatch.add(new EventSchedule(node, EventSchedule.RELEVANT));
                            }
                            // Dispatch xforms-readonly/xforms-readwrite if needed
                            {
                                final boolean previousReadonlyState = inheritedInstanceData.getPreviousReadonlyState();
                                final boolean newReadonlyState = inheritedInstanceData.getReadonly().get();

                                if ((previousReadonlyState && !newReadonlyState) || (!previousReadonlyState && newReadonlyState))
                                    eventsToDispatch.add(new EventSchedule(node, EventSchedule.READONLY));
                            }

                            // Dispatch xforms-valid/xforms-invalid if needed

                            // NOTE: There is no mention in the spec that these events should be
                            // displatched automatically when the value has changed, contrary to the
                            // other events above.
                            {
                                final boolean previousValidState = inheritedInstanceData.getPreviousValidState();
                                final boolean newValidState = inheritedInstanceData.getValid().get();

                                if ((previousValidState && !newValidState) || (!previousValidState && newValidState))
                                    eventsToDispatch.add(new EventSchedule(node, EventSchedule.VALID));
                            }
                        }
                    }
                }, false);
            }

            // Clear InstanceData event state
            clearInstanceDataEventState();

            // Send events and (try to) make sure the event corresponds to the current instance data
            // NOTE: deferred behavior is broken in XForms 1.0; 1.1 should introduce better
            // behavior. Also, event order and the exact steps to take are under-specified in 1.0.
            for (Iterator i = eventsToDispatch.iterator(); i.hasNext();) {
                final EventSchedule eventSchedule = (XFormsModel.EventSchedule) i.next();
                final Node node = eventSchedule.getNode();

                // Get list of bound controls (if the node has been removed it won't be matched)
                final List boundControlIds = xformsControls.findBoundControlIds(pipelineContext, node);

                if (boundControlIds != null) {
                    final int type = eventSchedule.getType();

                    for (Iterator j = boundControlIds.iterator(); j.hasNext();) {
                        final String controlId = (String) j.next();
                        final XFormsControls.ControlInfo controlInfo = (XFormsControls.ControlInfo) xformsControls.getObjectById(controlId);
                        if (controlInfo != null) {
                            if ((type & EventSchedule.VALUE) != 0) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsValueChangeEvent(controlInfo));
                            }
                            if ((type & EventSchedule.REQUIRED) != 0) {
                                final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(node);
                                final boolean currentRequiredState = instanceData.getRequired().get();
                                if (currentRequiredState) {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsRequiredEvent(controlInfo));
                                } else {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(controlInfo));
                                }
                            }
                            if ((type & EventSchedule.RELEVANT) != 0) {
                                final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(node);
                                final boolean currentRelevantState = instanceData.getRelevant().get();
                                if (currentRelevantState) {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsEnabledEvent(controlInfo));
                                } else {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsDisabledEvent(controlInfo));
                                }
                            }
                            if ((type & EventSchedule.READONLY) != 0) {
                                final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(node);
                                final boolean currentReadonlyState = instanceData.getRelevant().get();
                                if (currentReadonlyState) {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(controlInfo));
                                } else {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(controlInfo));
                                }
                            }
                            if ((type & EventSchedule.VALID) != 0) {
                                final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(node);
                                final boolean currentValidState = instanceData.getValid().get();
                                if (currentValidState) {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(controlInfo));
                                } else {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsInvalidEvent(controlInfo));
                                }
                            }
                        }
                    }
                }
            }

            // "5. The user interface reflects the state of the model, which means that all forms
            // controls reflect for their corresponding bound instance data:"
            if (xformsControls != null) {
                containingDocument.getXFormsControls().refreshForModel(pipelineContext, this);
            }
        }
    }

    /**
     * This class is cloneable.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static interface InstanceConstructListener {
        public void updateInstance(XFormsInstance instance);
    }

    public void setInstanceConstructListener(InstanceConstructListener instanceConstructListener) {
        this.instanceConstructListener = instanceConstructListener;
    }
}
