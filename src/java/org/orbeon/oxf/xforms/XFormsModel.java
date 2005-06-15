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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
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

/**
 * Represents an XForms model.
 */
public class XFormsModel implements XFormsEventTarget, Cloneable {

    private Document modelDocument;

    // Model attributes
    private String modelId;

    // Instances
    private List instanceIds;
    private List instances;
    private Map instancesMap;

    private InstanceConstructListener instanceConstructListener;

    // Submission information
    private Map submissions;

    // Binds
    private List binds;
    private FunctionLibrary xformsFunctionLibrary = new XFormsFunctionLibrary(this);

    // Schema validation
    private XFormsModelSchemaValidator schemaValidator;

    // Containing document
    private XFormsContainingDocument xformsContainingDocument;

    public XFormsModel(Document modelDocument) {
        this.modelDocument = modelDocument;

        // Basic check trying to make sure this is an XForms model
        // TODO: should rather use schema here or when obtaining document passed to this constructor
        Element modelElement = modelDocument.getRootElement();
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

        // Get <xforms:submission> elements (may be missing)
        {
            for (Iterator i = modelElement.elements(new QName("submission", XFormsConstants.XFORMS_NAMESPACE)).iterator(); i.hasNext();) {
                final Element submissionElement = (Element) i.next();
                String submissionId = submissionElement.attributeValue("id");
                if (submissionId == null)
                    submissionId = "";

                if (submissions == null)
                    submissions = new HashMap();
                submissions.put(submissionId, new XFormsModelSubmission(submissionElement, this));
            }
        }
    }

    public void setContainingDocument(XFormsContainingDocument xFormsContainingDocument) {
        this.xformsContainingDocument = xFormsContainingDocument;
    }

    public XFormsContainingDocument getContainingDocument() {
        return xformsContainingDocument;
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
            Element bindElement = (Element) i.next();
            ModelBind modelBind = new ModelBind(bindElement.attributeValue("id"), bindElement.attributeValue("nodeset"),
                    bindElement.attributeValue("relevant"), bindElement.attributeValue("calculate"), bindElement.attributeValue("type"),
                    bindElement.attributeValue("constraint"), bindElement.attributeValue("required"), bindElement.attributeValue("readonly"),
                    Dom4jUtils.getNamespaceContextNoDefault(bindElement), (LocationData) bindElement.getData());
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
        return (XFormsInstance) ((instances.size() > 0) ? instances.get(0) : null);
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

        for (Iterator i = instances.iterator(); i.hasNext();) {
            final XFormsInstance currentInstance = (XFormsInstance) i.next();
            if (currentInstance.getDocument() == document)
                return currentInstance;
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
        XFormsInstance newInstance = new XFormsInstance(pipelineContext, instanceDocument, this);
        instances.set(instancePosition, newInstance);

        // Create mapping instance id -> instance
        final String instanceId = (String) instanceIds.get(instancePosition);
        if (instanceId != null)
            instancesMap.put(instanceId, newInstance);
    }

    /**
     * Apply relevant and readonly binds only.
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

        for (Iterator i = binds.iterator(); i.hasNext();) {
            final ModelBind modelBind = (ModelBind) i.next();
            try {
                // Create XPath evaluator for this bind
                final DocumentWrapper documentWrapper = new DocumentWrapper(getDefaultInstance().getDocument(), null);
                bindRunner.applyBind(modelBind, documentWrapper);
            } catch (final Exception e) {
                throw new ValidationException(e, modelBind.getLocationData());
            }
        }
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
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
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
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
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
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
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

    private void handleCalculateBind(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {

        // Handle calculate MIP
        if (modelBind.getCalculate() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    if (node instanceof Element) {
                        // Compute calculated value
                        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                documentWrapper.wrap(node), "string(" + modelBind.getCalculate() + ")", modelBind.getNamespaceMap(), null,
                                xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                        try {
                            final Object result = expr.evaluateSingle();
                            final String stringResult = result.toString(); // even with string(), the result may not be a Java String object
                            // Place in element
                            Element elt = (Element) node;
                            Dom4jUtils.clearElementContent(elt);
                            elt.add(Dom4jUtils.createText(stringResult));
                        } catch (XPathException e) {
                            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getCalculate() + "'", modelBind.getLocationData());
                        } finally {
                            if (expr != null)
                                expr.returnToPool();
                        }

                    } else {
                        // Compute calculated value and place in attribute
                        String xpath = "string(" + modelBind.getCalculate() + ")";
                        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                        try {
                            String value = (String) expr.evaluateSingle();
                            XFormsInstance.setValueForNode(node, value);
                        } catch (XPathException e) {
                            throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                        } finally {
                            if (expr != null)
                                expr.returnToPool();
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
                modelBind.getCurrentNode() == null ? documentWrapper : documentWrapper.wrap(modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
        try {
            List nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                for (Iterator childIterator = modelBind.getChildrenIterator(); childIterator.hasNext();) {
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
                modelBind.getCurrentNode() == null ? documentWrapper : documentWrapper.wrap(modelBind.getCurrentNode()),
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

    public String getModelId() {
        return modelId;
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
        for (Iterator j = parent.getChildrenIterator(); j.hasNext();) {
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
        if (modelElement.attributeValue("schema") != null) {
            schemaValidator = new XFormsModelSchemaValidator();
            schemaValidator.loadSchemas(pipelineContext, modelElement);
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

    public void dispatchEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        final String eventName = xformsEvent.getEventName();
        if (XFormsEvents.XXFORMS_INITIALIZE_STATE.equals(eventName)) {
            // Internal event to restore state

            loadSchemasIfNeeded(pipelineContext);
            applyComputedExpressionBinds(pipelineContext);
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this, false));

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

            // TODO: support external instance
            if (instances == null) {
                // Build initial instance document
                List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
                if (instanceContainers.size() > 0) {
                    // Support multiple instances
                    int instancePosition = 0;
                    for (Iterator i = instanceContainers.iterator(); i.hasNext(); instancePosition++) {
                        Element instanceContainer = (Element) i.next();
                        Document instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) instanceContainer.elements().get(0));
                        setInstanceDocument(pipelineContext, instancePosition, instanceDocument);
                    }
                }
            }
            // TODO: throw exception event

            // Call special listener to update instance
            if (instanceConstructListener != null) {
                for (Iterator i = getInstances().iterator(); i.hasNext();) {
                    instanceConstructListener.updateInstance((XFormsInstance) i.next());
                }
            }

            // 3. P3P (N/A)
            // 4. Instance data is constructed. Evaluate binds:
            //    a. Evaluate nodeset
            //    b. Apply model item properties on nodes
            //    c. Throws xforms-binding-exception if the node has already model item property with same name
            // TODO: a, b, c xxx

            // 5. xforms-rebuild, xforms-recalculate, xforms-revalidate
            dispatchEvent(pipelineContext, new XFormsRebuildEvent(this));
            dispatchEvent(pipelineContext, new XFormsRecalculateEvent(this, false));
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this, false));

        } else if (XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE.equals(eventName)) {
            // 4.2.2 The xforms-model-construct-done Event
            // Bubbles: Yes / Cancelable: No / Context Info: None

            // TODO: if instance exists (for now it does!), check that controls can bind, otherwise control must be "irrelevant"
            // TODO: implicit lazy instance construction

        } else if (XFormsEvents.XFORMS_READY.equals(eventName)) {
            // 4.2.3 The xforms-ready Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None
        } else if (XFormsEvents.XFORMS_MODEL_DESTRUCT.equals(eventName)) {
            // 4.2.4 The xforms-model-destruct Event
            // Bubbles: No / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None
        } else if (XFormsEvents.XFORMS_REBUILD.equals(eventName)) {
            // 4.3.7 The xforms-rebuild Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // TODO: rebuild computational dependency data structures
        } else if (XFormsEvents.XFORMS_RECALCULATE.equals(eventName)) {
            // 4.3.6 The xforms-recalculate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            final XFormsRecalculateEvent xformsRecalculateEvent = (XFormsRecalculateEvent) xformsEvent;

            // Clear all existing computed expression binds
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.updateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                    public void walk(Node node, InstanceData instanceData) {
                        if (instanceData != null)
                            instanceData.clearComputedExpressionBinds();
                    }
                });
            }

            applyBinds(new BindRunner() {
                public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                    handleCalculateBind(pipelineContext, modelBind, documentWrapper, this);
                }
            });

            // Here we assume that we update those after recaculate, because recalculate is always
            // called after values are changed in the instance - may have to be changed...
            applyComputedExpressionBinds(pipelineContext);

            // Send events if needed
            if (xformsRecalculateEvent.isSendEvents() && xformsContainingDocument.getXFormsControls() != null) {
                final XFormsControls xformsControls = xformsContainingDocument.getXFormsControls();
                for (Iterator i = instances.iterator(); i.hasNext();) {
                    XFormsUtils.updateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                        public void walk(Node node, InstanceData instanceData) {
                            // Dispatch xforms-optional/xforms-required
                            {
                                final boolean previousRequiredState = instanceData.getPreviousRequiredState();
                                final boolean newRequiredState = instanceData.getRequired().get();
                                if (previousRequiredState && !newRequiredState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsOptionalEvent(null));// TODO: find bound control
                                } else if (!previousRequiredState && newRequiredState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsRequiredEvent(null));// TODO: find bound control
                                }
                            }
                            // Dispatch xforms-enabled/xforms-disabled
                            {
                                final boolean previousRelevantState = instanceData.getPreviousRelevantState();
                                final boolean newRelevantState = instanceData.getRelevant().get();
                                if (previousRelevantState && !newRelevantState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsDisabledEvent(null));// TODO: find bound control
                                } else if (!previousRelevantState && newRelevantState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsEnabledEvent(null));// TODO: find bound control
                                }
                            }
                            // Dispatch xforms-readonly/xforms-readwrite
                            {
                                final boolean previousReadonlyState = instanceData.getPreviousReadonlyState();
                                final boolean newReadonlyState = instanceData.getReadonly().get();
                                if (previousReadonlyState && !newReadonlyState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(null));// TODO: find bound control
                                } else if (!previousReadonlyState && newReadonlyState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(null));// TODO: find bound control
                                }
                            }
                        }
                    });
                }
            }

        } else if (XFormsEvents.XFORMS_REVALIDATE.equals(eventName)) {
            // 4.3.5 The xforms-revalidate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            final XFormsRevalidateEvent xformsRevalidateEvent = (XFormsRevalidateEvent) xformsEvent;

            // Clear all existing validation binds
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.updateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                    public void walk(Node node, InstanceData instanceData) {
                        if (instanceData != null)
                            instanceData.clearValidationBinds();
                    }
                });
            }

            // Run validation
            applySchemasIfNeeded();
            applyBinds(new BindRunner() {
                public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                    handleValidationBind(pipelineContext, modelBind, documentWrapper, this);
                }
            });

            // Send events if needed
            if (xformsRevalidateEvent.isSendEvents() && xformsContainingDocument.getXFormsControls() != null) {
                final XFormsControls xformsControls = xformsContainingDocument.getXFormsControls();
                for (Iterator i = instances.iterator(); i.hasNext();) {
                    XFormsUtils.updateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                        public void walk(Node node, InstanceData instanceData) {
                            // Dispatch xforms-valid/xforms-invalid
                            {
                                final boolean previousValidState = instanceData.getPreviousValidState();
                                final boolean newValidState = instanceData.getValid().get();
                                if (previousValidState && !newValidState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsInvalidEvent(null));// TODO: find bound control
                                } else if (!previousValidState && newValidState) {
//                                    xformsControls.dispatchEvent(pipelineContext, new XFormsValidEvent(null));// TODO: find bound control
                                }
                            }
                        }
                    });
                }
            }

        } else if (XFormsEvents.XFORMS_REFRESH.equals(eventName)) {
            // 4.3.4 The xforms-refresh Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // Must ask controls to refresh for this model
            if (xformsContainingDocument.getXFormsControls() != null) {
                xformsContainingDocument.getXFormsControls().refreshForModel(pipelineContext, this);
            }

        } else if (XFormsEvents.XFORMS_RESET.equals(eventName)) {
            // 4.3.8 The xforms-reset Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // TODO
            // "The instance data is reset to the tree structure and values it had immediately
            // after having processed the xforms-ready event."

            // "Then, the events xforms-rebuild, xforms-recalculate, xforms-revalidate and
            // xforms-refresh are dispatched to the model element in sequence."
            dispatchEvent(pipelineContext, new XFormsRebuildEvent(XFormsModel.this));
            dispatchEvent(pipelineContext, new XFormsRecalculateEvent(XFormsModel.this, true));
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(XFormsModel.this, true));
            dispatchEvent(pipelineContext, new XFormsRefreshEvent(XFormsModel.this));

        } else if (XFormsEvents.XFORMS_LINK_ERROR.equals(eventName)) {
            // 4.5.2 The xforms-link-error Event
            // Bubbles: Yes / Cancelable: No / Context Info: The URI that failed to load (xsd:anyURI)

            //callEventHandlers(pipelineContext, xformsEvent, eventName, xformsEvent.getControlElement());

            // The default action for this event results in the following: None; notification event only.
            //XFormsLinkError xFormsLinkError = (XFormsLinkError) xformsEvent;

            // TODO

        } else if (XFormsEvents.XFORMS_COMPUTE_EXCEPTION.equals(eventName)) {
            // 4.5.4 The xforms-compute-exception Event
            // Bubbles: Yes / Cancelable: No / Context Info: Implementation-specific error string.
            // The default action for this event results in the following: Fatal error.

            // TODO

        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
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
