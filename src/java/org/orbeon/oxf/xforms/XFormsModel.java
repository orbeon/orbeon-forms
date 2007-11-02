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
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.style.StandardNames;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.ValidationErrorValue;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.BuiltInSchemaFactory;

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
    private Map eventHandlers;

    // Submission information
    private Map submissions;

    // Binds
    private List binds;

    // Schema validation
    private XFormsModelSchemaValidator schemaValidator;

    // Containing document
    private XFormsContainingDocument containingDocument;

    // For legacy XForms engine
    private InstanceConstructListener instanceConstructListener;

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
                    final String instanceId = XFormsInstance.getInstanceId(instanceContainer);
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
        eventHandlers = XFormsEventHandlerImpl.extractEventHandlersObserver(containingDocument, this, modelElement);
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
        if (instancesMap != null) {
            final XFormsInstance instance = (XFormsInstance) instancesMap.get(id);
            if (instance != null)
                return instance;
        }

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
     *
     * @return  XFormsInstance or null
     */
    public XFormsInstance getDefaultInstance() {
        return (XFormsInstance) ((instances != null && instances.size() > 0) ? instances.get(0) : null);
    }

    /**
     * Return the id of the default instance for this model. Return null if there is no isntance in this model.
     *
     * @return  instance id or null
     */
    public String getDefaultInstanceId() {
        return (instanceIds != null && instanceIds.size() > 0) ? (String) instanceIds.get(0) : null;
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
                if (currentInstance.getDocumentInfo().isSameNodeInfo(documentInfo))
                    return currentInstance;
            }
        }

        return null;
    }

    /**
     * Set an instance document for this model. There may be multiple instance documents. Each instance document may
     * have an associated id that identifies it.
     */
    public XFormsInstance setInstanceDocument(Object instanceDocument, String modelId, String instanceId, String instanceSourceURI, String username, String password, boolean shared, long timeToLive, String validation) {
        // Initialize containers if needed
        if (instances == null) {
            instances = Arrays.asList(new XFormsInstance[instanceIds.size()]);
            instancesMap = new HashMap(instanceIds.size());
        }
        // Prepare and set instance
        final int instancePosition = instanceIds.indexOf(instanceId);
        final XFormsInstance newInstance;
        {
            if (instanceDocument instanceof Document)
                newInstance = new XFormsInstance(modelId, instanceId, (Document) instanceDocument, instanceSourceURI, username, password, shared, timeToLive, validation);
            else if (instanceDocument instanceof DocumentInfo)
                newInstance = new SharedXFormsInstance(modelId, instanceId, (DocumentInfo) instanceDocument, instanceSourceURI, username, password, shared, timeToLive, validation);
            else
                throw new OXFException("Invalid type for instance document: " + instanceDocument.getClass().getName());
        }
        instances.set(instancePosition, newInstance);

        // Create mapping instance id -> instance
        if (instanceId != null)
            instancesMap.put(instanceId, newInstance);

        return newInstance;
    }

    /**
     * Set an instance. The id of the instance must exist in the model.
     *
     * @param instance          XFormsInstance to set
     * @param replaced          whether this is an instance replacement (as result of a submission)
     */
    public void setInstance(XFormsInstance instance, boolean replaced) {

        // Mark the instance as replaced if needed
        instance.setReplaced(replaced);

        // Initialize containers if needed
        if (instances == null) {
            instances = Arrays.asList(new XFormsInstance[instanceIds.size()]);
            instancesMap = new HashMap(instanceIds.size());
        }
        // Prepare and set instance
        final String instanceId = instance.getEffectiveId();
        final int instancePosition = instanceIds.indexOf(instanceId);

        instances.set(instancePosition, instance);

        // Create mapping instance id -> instance
        if (instanceId != null)
            instancesMap.put(instanceId, instance);
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
                final XFormsInstance defaultInstance = getDefaultInstance();
                if (defaultInstance == null)
                    throw new ValidationException("No default instance found for xforms:bind element with id: " + modelBind.getId(), modelBind.getLocationData());
                
                try {
                    modelBind.setCurrentNodeInfo(defaultInstance.getInstanceRootElementInfo());
                    bindRunner.applyBind(modelBind);
                } catch (final Exception e) {
                    throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms binds", modelBind.getBindElement()));
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
                        final String stringResult = XPathCache.evaluateAsString(pipelineContext, nodeInfo, modelBind.getCalculate(), modelBind.getNamespaceMap(), null,
                            XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData());

                        // TODO: Detect if we have already handled this node and dispatch xforms-binding-exception
                        XFormsInstance.setValueForNodeInfo(pipelineContext, nodeInfo, stringResult, null);

                    } catch (Exception e) {
                        throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms calculate bind",
                                modelBind.getBindElement(), new String[] { "expression", modelBind.getCalculate() }));
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
                        // Get MIP value
                        final String xpath = "boolean(" + modelBind.getRequired() + ")";
                        final boolean required = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData())).booleanValue();

                        // Update node with MIP value
                        InstanceData.setRequired(nodeInfo, required);
                    } catch (Exception e) {
                        throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms required bind",
                                modelBind.getBindElement(), new String[] { "expression", modelBind.getRequired() }));
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
                        boolean relevant = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData())).booleanValue();
                        // Mark node
                        InstanceData.setRelevant(nodeInfo, relevant);
                    } catch (Exception e) {
                        throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms relevant bind",
                                modelBind.getBindElement(), new String[] { "expression", modelBind.getRelevant() }));
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
                        boolean readonly = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData())).booleanValue();

                        // Mark node
                        InstanceData.setReadonly(nodeInfo, readonly);
                    } catch (Exception e) {
                        throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms readonly bind",
                                modelBind.getBindElement(), new String[] { "expression", modelBind.getReadonly() }));
                    }
                }
            });
        } else if (modelBind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {
                    // Mark node
                    InstanceData.setReadonly(nodeInfo, true);
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
                        boolean xxformsExternalize = ((Boolean) XPathCache.evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData())).booleanValue();

                        // Mark node
                        InstanceData.setXXFormsExternalize(nodeInfo, xxformsExternalize);
                    } catch (Exception e) {
                        throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms xxforms:externalize bind",
                                modelBind.getBindElement(), new String[] { "expression", modelBind.getXXFormsExternalize() }));
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
                        // Get MIP value
                        final String xpath = "boolean(" + modelBind.getConstraint() + ")";
                        final Boolean valid = (Boolean) XPathCache.evaluateSingle(pipelineContext,
                            nodeInfo, xpath, modelBind.getNamespaceMap(), null,
                            XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData());

                        // Update node with MIP value
                        InstanceData.updateConstraint(nodeInfo, valid.booleanValue());
                    } catch (Exception e) {
                        throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms constraint bind",
                                modelBind.getBindElement(), new String[] { "expression", modelBind.getConstraint() }));
                    }
                }
            });
        }

        // Handle type MIP
        if (modelBind.getType() != null) {

            // Need an evaluator to check and convert type below
            final XPathEvaluator xpathEvaluator;
            try {
                xpathEvaluator = new XPathEvaluator();
                // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
                final IndependentContext context = xpathEvaluator.getStaticContext();
                for (Iterator j = modelBind.getNamespaceMap().keySet().iterator(); j.hasNext();) {
                    final String prefix = (String) j.next();
                    context.declareNamespace(prefix, (String) modelBind.getNamespaceMap().get(prefix));
                }
            } catch (Exception e) {
                throw ValidationException.wrapException(e, modelBind.getLocationData());
            }

            iterateNodeSet(pipelineContext, modelBind, new NodeHandler() {
                public void handleNode(NodeInfo nodeInfo) {

                    {
                        // Get type namespace and local name
                        final String typeQName = modelBind.getType();
                        final String typeNamespaceURI;
                        final String typeLocalname;
                        {
                            final int prefixPosition = typeQName.indexOf(':');
                            if (prefixPosition > 0) {
                                final String prefix = typeQName.substring(0, prefixPosition);
                                typeNamespaceURI = (String) modelBind.getNamespaceMap().get(prefix);
                                if (typeNamespaceURI == null)
                                    throw new ValidationException("Namespace not declared for prefix '" + prefix + "'",
                                            modelBind.getLocationData());

                                typeLocalname = typeQName.substring(prefixPosition + 1);
                            } else {
                                typeNamespaceURI = null;
                                typeLocalname = typeQName;
                            }
                        }


                        // Get value to validate
                        final String nodeValue = XFormsInstance.getValueForNodeInfo(nodeInfo);

                        // TODO: XForms 1.1: "The type model item property is not applied to instance nodes that contain child elements"

                        // TODO: "[...] these datatypes can be used in the type model item property without the
                        // addition of the XForms namespace qualifier if the namespace context has the XForms namespace
                        // as the default namespace."

                        final boolean isBuiltInSchemaType = XMLConstants.XSD_URI.equals(typeNamespaceURI);
                        final boolean isBuiltInXFormsType = XFormsConstants.XFORMS_NAMESPACE_URI.equals(typeNamespaceURI);

                        if (isBuiltInXFormsType && nodeValue.length() == 0) {
                            // Don't consider the node invalid if the string is empty
                        } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
                            // Built-in schema or XForms type

                            // Use XML Schema namespace URI as Saxon doesn't know anytyhing about XForms types
                            final String newTypeNamespaceURI = XMLConstants.XSD_URI;

                            // Get type information
                            final int requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname);
                            if (requiredTypeFingerprint == -1) {
                                throw new ValidationException("Invalid schema type '" + modelBind.getType() + "'", modelBind.getLocationData());
                            }

                            // Try to perform casting
                            // TODO: Should we actually perform casting? This for example removes leading and trailing space around tokens. Is that expected?
                            final StringValue stringValue = new StringValue(nodeValue);
                            final XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getStaticContext().getConfiguration());
                            final AtomicValue result = stringValue.convertPrimitive((BuiltInAtomicType) BuiltInSchemaFactory.getSchemaType(requiredTypeFingerprint), true, xpContext);

                            // Set error on node if necessary
                            if (result instanceof ValidationErrorValue) {
                                InstanceData.updateValueValid(nodeInfo, false, modelBind.getId());
                            }

                        } else if (schemaValidator != null) {
                            // Other type and there is a schema

                            // There are possibly types defined in the schema
                            final String validationError
                                    = schemaValidator.validateDatatype(nodeInfo, nodeValue, typeNamespaceURI, typeLocalname, typeQName, modelBind.getLocationData(), modelBind.getId());

                            // Set error on node if necessary
                            if (validationError != null) {
                                InstanceData.addSchemaError(nodeInfo, validationError, nodeValue, modelBind.getId());
                            }
                        } else {
                            throw new ValidationException("Invalid schema type '" + modelBind.getType() + "'", modelBind.getLocationData());
                        }

                        // Set type on node
                        InstanceData.setType(nodeInfo, XMLUtils.buildExplodedQName(typeNamespaceURI, typeLocalname));
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, bindRunner);
    }

    private void handleChildrenBinds(final PipelineContext pipelineContext, final ModelBind modelBind, BindRunner bindRunner) {
        // Handle children binds
        final List nodeset = XPathCache.evaluate(pipelineContext,
                modelBind.getCurrentNodeInfo(),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData());
        try {
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo nodeInfo = (NodeInfo) j.next();
                for (Iterator childIterator = modelBind.getChildren().iterator(); childIterator.hasNext();) {
                    final ModelBind child = (ModelBind) childIterator.next();
                    child.setCurrentNodeInfo(nodeInfo);
                    bindRunner.applyBind(child);
                }
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "evaluating XForms children binds",
                    modelBind.getBindElement(), new String[] { "node-set", modelBind.getNodeset() }));
        }
    }

    private void iterateNodeSet(PipelineContext pipelineContext, ModelBind modelBind, NodeHandler nodeHandler) {
        try {
            final List nodeset = XPathCache.evaluate(pipelineContext,
                modelBind.getCurrentNodeInfo(),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, modelBind.getLocationData().getSystemID(), modelBind.getLocationData());
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo nodeInfo = (NodeInfo) j.next();
                nodeHandler.handleNode(nodeInfo);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(modelBind.getLocationData(), "iterating XForms bind",
                    modelBind.getBindElement(), new String[] { "node-set", modelBind.getNodeset() }));
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

    public List getBindNodeset(PipelineContext pipelineContext, ModelBind modelBind) {
        // Get a list of parents, ordered by grandfather first
        final List parents = new ArrayList();
        {
            parents.add(modelBind);
            ModelBind parent = modelBind;
            while ((parent = parent.getParent()) != null) {
                parents.add(parent);
            }
            Collections.reverse(parents);
        }

        final List nodeset = new ArrayList();

        // Find the root node
        final XFormsInstance defaultInstance = getDefaultInstance();
        if (defaultInstance == null)
            throw new ValidationException("No default instance found for xforms:bind element with id: " + modelBind.getId(), modelBind.getLocationData());
        nodeset.add(defaultInstance.getInstanceRootElementInfo());

        for (Iterator i = parents.iterator(); i.hasNext();) {
            final ModelBind current = (ModelBind) i.next();
            final List currentModelBindResults = new ArrayList();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                final NodeInfo node = (NodeInfo) j.next();
                // Execute XPath expresssion
                currentModelBindResults.addAll(XPathCache.evaluate(pipelineContext, node, current.getNodeset(),
                        current.getNamespaceMap(), null, XFormsContainingDocument.getFunctionLibrary(), XFormsModel.this, current.getLocationData().getSystemID(), current.getLocationData()));
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
            schemaValidator.loadSchemas(pipelineContext);
        }
    }

    private void applySchemasIfNeeded() {
        // Don't do anything if there is no schema
        if (schemaValidator != null) {
            // Apply schemas to all instances
            if (getInstances() != null) {
                for (Iterator i = getInstances().iterator(); i.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) i.next();
                    // Currently we don't support validating read-only instances
                    if (!currentInstance.isReadOnly())
                        schemaValidator.validateInstance(currentInstance);
                }
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
            // TODO: support inline schemas
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
                // Build initial instance documents
                final List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
                final XFormsStaticState staticState = containingDocument.getStaticState();
                final Map staticStateInstancesMap = (staticState != null && staticState.isInitialized()) ? staticState.getInstancesMap() : null;
                if (instanceContainers.size() > 0) {
                    // Iterate through all instances
                    int instancePosition = 0;
                    for (Iterator i = instanceContainers.iterator(); i.hasNext(); instancePosition++) {

                        final Element instanceContainerElement = (Element) i.next();
                        final LocationData locationData = (LocationData) instanceContainerElement.getData();
                        final String instanceId = XFormsInstance.getInstanceId(instanceContainerElement);

                        // Handle read-only hints
                        final boolean isReadonlyHint = XFormsInstance.isReadonlyHint(instanceContainerElement);
                        final boolean isApplicationSharedHint = XFormsInstance.isApplicationSharedHint(instanceContainerElement);
                        final long xxformsTimeToLive = XFormsInstance.getTimeToLive(instanceContainerElement);

                        // Skip processing in case somebody has already set this particular instance
                        if (instances.get(instancePosition) != null)
                            continue;

                        // Get instance from static state if possible
                        if (staticStateInstancesMap != null) {
                            final XFormsInstance staticStateInstance = (XFormsInstance) staticStateInstancesMap.get(instanceId);
                            if (staticStateInstance != null) {
                                // The instance is already available in the static state

                                if (staticStateInstance.getDocumentInfo() == null) {
                                    // Instance is not initialized yet

                                    // This means that the instance was application shared
                                    if (!staticStateInstance.isApplicationShared())
                                        throw new ValidationException("Non-initialized instance has to be application shared for id: " + staticStateInstance.getEffectiveId(),
                                                (LocationData) instanceContainerElement.getData());

                                    if (XFormsServer.logger.isDebugEnabled())
                                        XFormsServer.logger.debug("XForms - using instance from application shared instance cache (instance from static state was not initialized): " + staticStateInstance.getEffectiveId());

                                    final SharedXFormsInstance sharedInstance
                                            = XFormsServerSharedInstancesCache.instance().find(pipelineContext, staticStateInstance.getEffectiveId(), staticStateInstance.getModelId(), staticStateInstance.getSourceURI(), staticStateInstance.getTimeToLive(), staticStateInstance.getValidation());
                                    setInstance(sharedInstance, false);

                                } else {
                                    // Instance is initialized, just use it

                                    if (XFormsServer.logger.isDebugEnabled())
                                        XFormsServer.logger.debug("XForms - using initialized instance from static state: " + staticStateInstance.getEffectiveId());

                                    setInstance(staticStateInstance, false);
                                }

                                continue;
                            }
                        }

                        // Did not get the instance from static state
                        final Object instanceDocument;// Document or DocumentInfo
                        final String instanceSourceURI;
                        final String xxformsUsername;
                        final String xxformsPassword;
                        final String xxformsValidation = instanceContainerElement.attributeValue(XFormsConstants.XXFORMS_VALIDATION_QNAME);

                        final long startTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;
                        final String instanceResource;
                        {
                            final String srcAttribute = instanceContainerElement.attributeValue("src");
                            final String resourceAttribute = instanceContainerElement.attributeValue("resource");
                            if (srcAttribute != null)
                                instanceResource = XFormsUtils.encodeHRRI(srcAttribute, true);
                            else if (resourceAttribute != null)
                                instanceResource = XFormsUtils.encodeHRRI(resourceAttribute, true);
                            else
                                instanceResource = null;
                        }
                        if (instanceResource == null) {
                            // Inline instance
                            final List children = instanceContainerElement.elements();
                            if (children == null || children.size() != 1) {
                                final Throwable throwable = new ValidationException("xforms:instance element must contain exactly one child element",
                                        new ExtendedLocationData(locationData, "processing inline XForms instance", instanceContainerElement));
                                containingDocument.dispatchEvent(pipelineContext, new XFormsLinkExceptionEvent(XFormsModel.this, null, instanceContainerElement, throwable));
                                break;
                            }
                            if (!isReadonlyHint) {
                                instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) children.get(0));
                            } else {
                                instanceDocument = TransformerUtils.dom4jToTinyTree(Dom4jUtils.createDocumentCopyParentNamespaces((Element) children.get(0)));
                            }
                            instanceSourceURI = null;
                            xxformsUsername = null;
                            xxformsPassword = null;
                        } else if (!instanceResource.trim().equals("")) {

                            // External instance
                            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                            // NOTE: Optimizing with include() for servlets doesn't allow detecting errors caused by
                            // the included resource, so we don't allow this for now. Furthermore, we are forced to
                            // "optimize" for portlet access.

//                            final boolean optimize = !NetUtils.urlHasProtocol(srcAttribute)
//                               && (externalContext.getRequest().getContainerType().equals("portlet")
//                                    || (externalContext.getRequest().getContainerType().equals("servlet")
//                                        && XFormsUtils.isOptimizeLocalInstanceLoads()));

                            final boolean optimizeForPortlets = !NetUtils.urlHasProtocol(instanceResource)
                                                        && externalContext.getRequest().getContainerType().equals("portlet");

                            final XFormsModelSubmission.ConnectionResult connectionResult;
                            if (optimizeForPortlets) {
                                // Use optimized local mode

                                final URI resolvedURI = XFormsUtils.resolveXMLBase(instanceContainerElement, instanceResource);

                                if (XFormsServer.logger.isDebugEnabled())
                                    XFormsServer.logger.debug("XForms - getting document from optimized URI for: " + resolvedURI.toString());

                                connectionResult = XFormsSubmissionUtils.doOptimized(pipelineContext, externalContext, null, "get", resolvedURI.toString(), null, false, null, null);

                                instanceSourceURI = resolvedURI.toString();
                                xxformsUsername = null;
                                xxformsPassword = null;

                                try {
                                    try {
                                        // Handle connection errors
                                        if (connectionResult.resultCode != 200) {
                                            throw new OXFException("Got invalid return code while loading instance: " + instanceResource + ", " + connectionResult.resultCode);
                                        }

                                        // TODO: Handle validating and handleXInclude!

                                        // Read result as XML
                                        if (!isReadonlyHint) {
                                            instanceDocument = TransformerUtils.readDom4j(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                        } else {
                                            instanceDocument = TransformerUtils.readTinyTree(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                        }
                                    } catch (Exception e) {
                                        final LocationData extendedLocationData = new ExtendedLocationData(locationData, "reading external XForms instance (optimized)", instanceContainerElement);
                                        dispatchXFormsLinkExceptionEvent(pipelineContext, connectionResult, instanceResource, e, instanceContainerElement, extendedLocationData);
                                        break;
                                    }
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

                                final URL absoluteResolvedURL;
                                final String absoluteResolvedURLString;
                                {
                                    final String resolvedURL = XFormsUtils.resolveResourceURL(pipelineContext, instanceContainerElement, instanceResource);
                                    final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(resolvedURL);
                                    if (inputName != null) {
                                        // URL is input:*, keep it as is
                                        absoluteResolvedURL = null;
                                        absoluteResolvedURLString = resolvedURL;
                                    } else {
                                        // URL is regular URL, make sure it is absolute
                                        absoluteResolvedURL = XFormsSubmissionUtils.createAbsoluteURL(resolvedURL, null, externalContext);
                                        absoluteResolvedURLString = absoluteResolvedURL.toExternalForm();
                                    }
                                }

                                // Get instance from shared cache if possible
                                if (isApplicationSharedHint) {
                                    final SharedXFormsInstance sharedXFormsInstance = XFormsServerSharedInstancesCache.instance().find(pipelineContext, instanceId, modelId, absoluteResolvedURLString, xxformsTimeToLive, xxformsValidation);
                                    setInstance(sharedXFormsInstance, false);
                                    continue;
                                }

                                if (containingDocument.getURIResolver() == null || isApplicationSharedHint) {
                                    // Connect directly if there is no resolver or if the instance is globally shared

                                    if (XFormsServer.logger.isDebugEnabled())
                                        XFormsServer.logger.debug("XForms - getting document from URI for: " + absoluteResolvedURLString);

                                    connectionResult = XFormsSubmissionUtils.doRegular(externalContext,
                                            "get", absoluteResolvedURL, xxformsUsername, xxformsPassword, null, null, null, null);

                                    try {
                                        try {
                                            // Handle connection errors
                                            if (connectionResult.resultCode != 200) {
                                                throw new OXFException("Got invalid return code while loading instance: " + instanceResource + ", " + connectionResult.resultCode);
                                            }

                                            // TODO: Handle validating and handleXInclude!

                                            // Read result as XML
                                            if (!isReadonlyHint) {
                                                instanceDocument = TransformerUtils.readDom4j(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                            } else {
                                                instanceDocument = TransformerUtils.readTinyTree(connectionResult.getResultInputStream(), connectionResult.resourceURI);
                                            }
                                        } catch (Exception e) {
                                            final LocationData extendedLocationData = new ExtendedLocationData(locationData, "reading external instance (no resolver)", instanceContainerElement);
                                            dispatchXFormsLinkExceptionEvent(pipelineContext, connectionResult, instanceResource, e, instanceContainerElement, extendedLocationData);
                                            break;
                                        }
                                    } finally {
                                        // Clean-up
                                        if (connectionResult != null)
                                            connectionResult.close();
                                    }

                                } else {
                                    // Optimized case that uses the provided resolver
                                    if (XFormsServer.logger.isDebugEnabled())
                                        XFormsServer.logger.debug("XForms - getting document from resolver for: " + absoluteResolvedURLString);

                                    try {
                                        // TODO: Handle validating and handleXInclude!

                                        if (!isReadonlyHint) {
                                            instanceDocument = containingDocument.getURIResolver().readURLAsDocument(absoluteResolvedURLString, xxformsUsername, xxformsPassword);
                                        } else {
                                            instanceDocument = containingDocument.getURIResolver().readURLAsDocumentInfo(absoluteResolvedURLString, xxformsUsername, xxformsPassword);
                                        }
                                    } catch (Exception e) {
                                        final LocationData extendedLocationData = new ExtendedLocationData(locationData, "reading external instance (resolver)", instanceContainerElement);
                                        dispatchXFormsLinkExceptionEvent(pipelineContext, new XFormsModelSubmission.ConnectionResult(absoluteResolvedURLString), instanceResource, e, instanceContainerElement, extendedLocationData);
                                        break;
                                    }
                                }

                                instanceSourceURI = absoluteResolvedURLString;
                            }
                        } else {
                            // Got a blank src attribute, just dispatch xforms-link-exception
                            final LocationData extendedLocationData = new ExtendedLocationData(locationData, "processing XForms instance", instanceContainerElement);
                            final Throwable throwable = new ValidationException("Invalid blank URL specified for instance: " + instanceId, extendedLocationData);
                            containingDocument.dispatchEvent(pipelineContext, new XFormsLinkExceptionEvent(XFormsModel.this, instanceResource, instanceContainerElement, throwable));
                            break;
                        }

                        if (XFormsServer.logger.isDebugEnabled()) {
                            final long submissionTime = System.currentTimeMillis() - startTime;
                            XFormsServer.logger.debug("XForms - instance loading time for instance '" + instanceId + "' (including handling returned body): " + submissionTime);
                        }

                        // Set instance and associated information if everything went well
                        setInstanceDocument(instanceDocument, modelId, instanceId, instanceSourceURI, xxformsUsername, xxformsPassword, isApplicationSharedHint, xxformsTimeToLive, xxformsValidation);
                    }
                }
            }

            // Call special listener to update instance
            if (instanceConstructListener != null && getInstances() != null) {
                int position = 0;
                final InstanceConstructListener listener = instanceConstructListener;
                // Make sure we don't keep a reference on this in case this is cache (legacy XForms engine)
                instanceConstructListener = null;
                // Use listener to update instances
                for (Iterator i = getInstances().iterator(); i.hasNext(); position++) {
                    listener.updateInstance(position, (XFormsInstance) i.next());
                }
            }

            // 3. P3P (N/A)

            // 4. Instance data is constructed. Evaluate binds:
            //    a. Evaluate nodeset
            //    b. Apply model item properties on nodes
            //    c. Throws xforms-binding-exception if the node has already model item property with same name
            // TODO: a, b, c

            // 5. xforms-rebuild, xforms-recalculate, xforms-revalidate
            doRebuild(pipelineContext);
            doRecalculate(pipelineContext);
            doRevalidate(pipelineContext);

            synchronizeInstanceDataEventState();

        } else if (XFormsEvents.XXFORMS_READY.equals(eventName)) {

            // This is called after xforms-ready events have been dispatched to all models

            final XFormsStaticState staticState = containingDocument.getStaticState();

            if (staticState != null && !staticState.isInitialized()) {
                // The static state is open to adding instances

                final boolean modelHasReset = false;// TODO: containingDocument[0].hasReset(modelId) or xformsEngineStaticState.hasReset(modelId);

                if (getInstances() != null) {
                    for (Iterator instanceIterator = getInstances().iterator(); instanceIterator.hasNext();) {
                        final XFormsInstance currentInstance = (XFormsInstance) instanceIterator.next();

                        if (currentInstance instanceof SharedXFormsInstance) {

                            // NOTE: We add all shared instances, even the globally shared ones, and the static state
                            // decides of the amount of information to actually store
                            if (XFormsServer.logger.isDebugEnabled())
                                XFormsServer.logger.debug("XForms - adding read-only instance to static state: " + currentInstance.getEffectiveId());
                            staticState.addInstance((SharedXFormsInstance) currentInstance);
                        } else if (modelHasReset) {
                            if (XFormsServer.logger.isDebugEnabled())
                                XFormsServer.logger.debug("XForms - adding reset instance to static state: " + currentInstance.getEffectiveId());
                            staticState.addInstance(currentInstance.createSharedInstance());
                        }
                    }
                }
            }

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
                = ValidationException.wrapException(e, new ExtendedLocationData(new LocationData(connectionResult.resourceURI, -1, -1),
                    "reading external instance", instanceContainerElement));
            validationException.addLocationData(locationData);
            throwable = validationException;
        } else {
            throwable = ValidationException.wrapException(e, locationData);
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
                    public void walk(NodeInfo nodeInfo) {
                        InstanceData.clearOtherState(nodeInfo);
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
                    public void walk(NodeInfo nodeInfo) {
                        InstanceData.clearValidationState(nodeInfo);
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
        if (xformsControls == null || xformsControls.getCurrentControlsState() == null)
            return;

        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - performing refresh for model: " + getEffectiveId());

        // If this is the first refresh we mark nodes to dispatch MIP events
        final boolean isMustMarkMIPEvents = containingDocument.isInitializationFirstRefreshClear();

        // Rebuild controls if needed
        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

        // Obtain global information about event handlers. This is a rough optimization so we can avoid sending certain
        // types of events below.
        final boolean isMustSendValueChangedEvents = xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_VALUE_CHANGED);
        final boolean isMustSendRequiredEvents = xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_REQUIRED) || xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_OPTIONAL);
        final boolean isMustSendRelevantEvents = xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_ENABLED) || xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_DISABLED);
        final boolean isMustSendReadonlyEvents = xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_READONLY) || xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_READWRITE);
        final boolean isMustSendValidEvents = xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_VALID) || xformsControls.hasHandlerForEvent(XFormsEvents.XFORMS_INVALID);

        final boolean isMustSendUIEvents = isMustSendValueChangedEvents || isMustSendRequiredEvents || isMustSendRelevantEvents || isMustSendReadonlyEvents || isMustSendValidEvents;
        if (isMustSendUIEvents) {
            // There are potentially event handlers for UI events, so do the whole processing

            // Build list of events to send
            final Map relevantBindingEvents = xformsControls.getCurrentControlsState().getEventsToDispatch();
            final List eventsToDispatch = new ArrayList();

            // Iterate through controls and check the nodes they are bound to
            xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
                public void startVisitControl(XFormsControl xformsControl) {

                    // If control is not bound (e.g. xforms:group[not(@ref) and not(@bind)]) no events are sent
                    final boolean isControlBound = xformsControl.getBindingContext().isNewBind();
                    if (!isControlBound)
                        return;

                    // This can happen if control is not bound to anything
                    final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                    if (currentNodeInfo == null)
                        return;

                    // We only dispatch events for controls bound to a mutable document
                    if (!(currentNodeInfo instanceof NodeWrapper))
                        return;

                    // Check if value has changed
                    final boolean isValueControl = XFormsControls.isValueControl(xformsControl.getName());
                    final boolean valueChanged = isValueControl && InstanceData.isValueChanged(currentNodeInfo);

                    final String effectiveId = xformsControl.getEffectiveId();
                    final EventSchedule existingEventSchedule = (relevantBindingEvents == null) ? null : (EventSchedule) relevantBindingEvents.get(effectiveId);

                    if (valueChanged && isMustSendValueChangedEvents) {
                        // Value change takes care of everything
                        // NOTE: isValueControl is implied
                        addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.ALL);
                    } else if (valueChanged) {
                        // Must do "as if" we send all the MIP events
                        // NOTE: isValueControl is implied
                        if (isMustSendRequiredEvents)
                            addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.REQUIRED);
                        if (isMustSendRelevantEvents)
                            addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.RELEVANT);
                        if (isMustSendReadonlyEvents)
                            addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.READONLY);
                        if (isMustSendValidEvents)
                            addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.VALID);
                    } else {
                        // Dispatch xforms-optional/xforms-required if needed
                        if (isValueControl && isMustSendRequiredEvents) { // do this only for value controls
                            if (isMustMarkMIPEvents) {
                                // Send in all cases
                                addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.REQUIRED);
                            } else {
                                // Send only when value has changed
                                final boolean previousRequiredState = InstanceData.getPreviousRequiredState(currentNodeInfo);
                                final boolean newRequiredState = InstanceData.getRequired(currentNodeInfo);

                                if ((previousRequiredState && !newRequiredState) || (!previousRequiredState && newRequiredState)) {
                                    addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.REQUIRED);
                                }
                            }
                        }
                        // Dispatch xforms-enabled/xforms-disabled if needed
                        if (isMustSendRelevantEvents) {

                            if (isMustMarkMIPEvents) {
                                // Send in all cases
                                addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.RELEVANT);
                            } else {
                                // Send only when value has changed
                                final boolean previousRelevantState = InstanceData.getPreviousInheritedRelevantState(currentNodeInfo);
                                final boolean newRelevantState = InstanceData.getInheritedRelevant(currentNodeInfo);

                                if ((previousRelevantState && !newRelevantState) || (!previousRelevantState && newRelevantState)) {
                                    addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.RELEVANT);
                                }
                            }
                        }
                        // Dispatch xforms-readonly/xforms-readwrite if needed
                        if (isMustSendReadonlyEvents) {
                            if (isMustMarkMIPEvents) {
                                // Send in all cases
                                addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.READONLY);
                            } else {
                                final boolean previousReadonlyState = InstanceData.getPreviousInheritedReadonlyState(currentNodeInfo);
                                final boolean newReadonlyState = InstanceData.getInheritedReadonly(currentNodeInfo);

                                if ((previousReadonlyState && !newReadonlyState) || (!previousReadonlyState && newReadonlyState)) {
                                    addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.READONLY);
                                }
                            }
                        }

                        // Dispatch xforms-valid/xforms-invalid if needed

                        // NOTE: There is no mention in the spec that these events should be displatched automatically
                        // when the value has changed, contrary to the other events above.
                        if (isValueControl && isMustSendValidEvents) { // do this only for value controls
                            if (isMustMarkMIPEvents) {
                                // Send in all cases
                                addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.VALID);
                            } else {
                                final boolean previousValidState = InstanceData.getPreviousValidState(currentNodeInfo);
                                final boolean newValidState = InstanceData.getValid(currentNodeInfo);

                                if ((previousValidState && !newValidState) || (!previousValidState && newValidState)) {
                                    addEventToSchedule(existingEventSchedule, effectiveId, EventSchedule.VALID);
                                }
                            }
                        }
                    }
                }

                public void endVisitControl(XFormsControl XFormsControl) {
                }

                private void addEventToSchedule(EventSchedule eventSchedule, String effectiveControlId, int type) {
                    if (eventSchedule == null)
                        eventsToDispatch.add(new EventSchedule(effectiveControlId, type));
                    else
                        eventSchedule.updateType(type);
                }
            });

            // Clear InstanceData event state
            synchronizeInstanceDataEventState();
            xformsControls.getCurrentControlsState().setEventsToDispatch(null);

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            if (deferredActionContext != null)
                deferredActionContext.refresh = false;

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

                final XFormsControl xformsControl = (XFormsControl) xformsControls.getObjectById(controlInfoId);

                if (!isRelevantBindingEvent) {
                    // Regular type of event

                    if (xformsControl == null) {
                        // In this case, the algorithm in the spec is not clear. Many thing can have happened between the
                        // initial determination of a control bound to a changing node, and now, including many events and
                        // actions.
                        continue;
                    }

                    // If control is not bound (e.g. xforms:group[not(@ref) and not(@bind)]) no events are sent
                    final boolean isControlBound = xformsControl.getBindingContext().isNewBind();
                    if (!isControlBound)
                        continue;

                    // Re-obtain node to which control is bound, in case things have changed
                    final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                    if (currentNodeInfo == null) {
                        // See comment above about things that can have happened since.
                        continue;
                    }

                    // We only dispatch events for controls bound to a mutable document
                    if (!(currentNodeInfo instanceof NodeWrapper))
                        continue;

                    // Is this a value control?
                    final boolean isValueControl = XFormsControls.isValueControl(xformsControl.getName());

                    // "The XForms processor is not considered to be executing an outermost action handler at the time that it
                    // performs deferred update behavior for XForms models. Therefore, event handlers for events dispatched to
                    // the user interface during the deferred refresh behavior are considered to be new outermost action
                    // handler."

                    if (isValueControl && isMustSendValueChangedEvents && (type & EventSchedule.VALUE) != 0) { // do this only for value controls
                        containingDocument.dispatchEvent(pipelineContext, new XFormsValueChangeEvent(xformsControl));
                    }
                    // TODO: after each event, we should get a new reference to the control as it may have changed
                    if (currentNodeInfo != null && currentNodeInfo instanceof NodeWrapper) {
                        if (isValueControl && isMustSendRequiredEvents && (type & EventSchedule.REQUIRED) != 0) { // do this only for value controls
                            final boolean currentRequiredState = InstanceData.getRequired(currentNodeInfo);
                            if (currentRequiredState) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsRequiredEvent(xformsControl));
                            } else {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(xformsControl));
                            }
                        }
                        if (isMustSendRelevantEvents && (type & EventSchedule.RELEVANT) != 0) {
                            final boolean currentRelevantState = InstanceData.getInheritedRelevant(currentNodeInfo);
                            if (currentRelevantState) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsEnabledEvent(xformsControl));
                            } else {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsDisabledEvent(xformsControl));
                            }
                        }
                        if (isMustSendReadonlyEvents && (type & EventSchedule.READONLY) != 0) {
                            final boolean currentReadonlyState = InstanceData.getInheritedReadonly(currentNodeInfo);
                            if (currentReadonlyState) {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(xformsControl));
                            } else {
                                containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(xformsControl));
                            }
                        }
                        if (isValueControl && isMustSendValidEvents && (type & EventSchedule.VALID) != 0) { // do this only for value controls
                            final boolean currentValidState = InstanceData.getValid(currentNodeInfo);
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

                    if (xformsControl != null) {

                        // If control is not bound (e.g. xforms:group[not(@ref) and not(@bind)]) no events are sent
                        final boolean isControlBound = xformsControl.getBindingContext().isNewBind();
                        if (!isControlBound)
                            continue;

                        // Is this a value control?
                        final boolean isValueControl = XFormsControls.isValueControl(xformsControl.getName());

                        // Re-obtain node to which control is bound, in case things have changed
                        final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                        if (currentNodeInfo != null) {

                            // We only dispatch value-changed and other events for controls bound to a mutable document
                            if (!(currentNodeInfo instanceof NodeWrapper))
                                continue;

                            final boolean currentRelevantState = InstanceData.getInheritedRelevant(currentNodeInfo);
                            if (currentRelevantState) {
                                // The control is newly bound to a relevant node
                                if (isMustSendRelevantEvents) {
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsEnabledEvent(xformsControl));
                                }

                                // Also send other MIP events
                                if (isMustSendRequiredEvents && isValueControl) { // do this only for value controls
                                    final boolean currentRequiredState = InstanceData.getRequired(currentNodeInfo);
                                    if (currentRequiredState) {
                                        containingDocument.dispatchEvent(pipelineContext, new XFormsRequiredEvent(xformsControl));
                                    } else {
                                        containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(xformsControl));
                                    }
                                }

                                if (isMustSendReadonlyEvents) {
                                    final boolean currentReadonlyState = InstanceData.getInheritedReadonly(currentNodeInfo);
                                    if (currentReadonlyState) {
                                        containingDocument.dispatchEvent(pipelineContext, new XFormsReadonlyEvent(xformsControl));
                                    } else {
                                        containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(xformsControl));
                                    }
                                }

                                if (isMustSendValidEvents && isValueControl) { // do this only for value controls
                                    final boolean currentValidState = InstanceData.getValid(currentNodeInfo);
                                    if (currentValidState) {
                                        containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(xformsControl));
                                    } else {
                                        containingDocument.dispatchEvent(pipelineContext, new XFormsInvalidEvent(xformsControl));
                                    }
                                }
                            }
                        } else {
                            // The control is not bound to a node
                            sendDefaultEventsForDisabledControl(pipelineContext, xformsControl, isValueControl,
                                    isMustSendRequiredEvents, isMustSendRelevantEvents, isMustSendReadonlyEvents, isMustSendValidEvents);
                        }
                    } else {
                        // The control no longer exists
                        if (eventSchedule.getXFormsControl() != null) {
                            // Is this a value control?
                            final boolean isValueControl = XFormsControls.isValueControl(eventSchedule.getXFormsControl().getName());
                            sendDefaultEventsForDisabledControl(pipelineContext, eventSchedule.getXFormsControl(), isValueControl,
                                    isMustSendRequiredEvents, isMustSendRelevantEvents, isMustSendReadonlyEvents, isMustSendValidEvents);
                        }
                    }
                }
            }
        } else {
            // No UI events to send because there is no event handlers for any of them
            XFormsServer.logger.debug("XForms - skipping sending of UI events because no listener was found.");

            synchronizeInstanceDataEventState();
            xformsControls.getCurrentControlsState().setEventsToDispatch(null);

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            if (deferredActionContext != null)
                deferredActionContext.refresh = false;
        }

        // "5. The user interface reflects the state of the model, which means that all forms
        // controls reflect for their corresponding bound instance data:"
        if (xformsControls != null) {
            xformsControls.refreshForModel(pipelineContext, this);
        }
    }

    private void sendDefaultEventsForDisabledControl(PipelineContext pipelineContext, XFormsControl xformsControl, boolean isValueControl,
                                                     boolean isMustSendRequiredEvents, boolean isMustSendRelevantEvents,
                                                     boolean isMustSendReadonlyEvents, boolean isMustSendValidEvents) {

        // Control is disabled
        if (isMustSendRelevantEvents)
            containingDocument.dispatchEvent(pipelineContext, new XFormsDisabledEvent(xformsControl));

        // Send events for default MIP values
        if (isMustSendRequiredEvents && isValueControl) // do this only for value controls
            containingDocument.dispatchEvent(pipelineContext, new XFormsOptionalEvent(xformsControl));

        if (isMustSendReadonlyEvents)
            containingDocument.dispatchEvent(pipelineContext, new XFormsReadwriteEvent(xformsControl));

        if (isMustSendValidEvents && isValueControl) // do this only for value controls
            containingDocument.dispatchEvent(pipelineContext, new XFormsValidEvent(xformsControl));
    }

    /**
     * Handle events related to externally updating one or more instance documents.
     */
    public void handleNewInstanceDocuments(PipelineContext pipelineContext, final XFormsInstance newInstance) {

        // Set the instance on this model
        setInstance(newInstance, true);

        // The controls will be dirty
        containingDocument.getXFormsControls().markDirty();

        // NOTE: The current spec specifies direct calls, but it might be updated to require setting flags instead.
        setAllDeferredFlags(true);

        // Mark new instance nodes to which controls are bound for event dispatching
        if (!newInstance.isReadOnly()) {// replacing a read-only instance does not cause value change events at the moment

            final XFormsControls xformsControls = containingDocument.getXFormsControls();
            if (xformsControls != null && xformsControls.getCurrentControlsState() != null) {// this just handles the legacy XForms engine which doesn't use the controls

                if (XFormsServer.logger.isDebugEnabled())
                    XFormsServer.logger.debug("XForms - marking nodes for value change following instance replacement: " + newInstance.getEffectiveId());

                // Rebuild controls if needed
                // NOTE: This requires recalculate and revalidate to take place for 1) relevance handling and 2) type handling
                doRebuild(pipelineContext);
                doRecalculate(pipelineContext);
                doRevalidate(pipelineContext);
                xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

                // Mark all nodes to which value controls are bound
                xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
                    public void startVisitControl(XFormsControl xformsControl) {

                        // Don't do anything if it's not a value control
                        final boolean isValueControl = XFormsControls.isValueControl(xformsControl.getName());
                        if (!isValueControl)
                            return;

                        // If control is not bound (e.g. xforms:group[not(@ref) and not(@bind)]) no events are sent
                        final boolean isControlBound = xformsControl.getBindingContext().isNewBind();
                        if (!isControlBound)
                            return;

                        // This can happen if control is not bound to anything
                        final NodeInfo currentNodeInfo = xformsControl.getBoundNode();
                        if (currentNodeInfo == null)
                            return;

                        // We only mark nodes in mutable documents
                        if (!(currentNodeInfo instanceof NodeWrapper))
                            return;

                        // We only mark nodes in the replaced instance
                        if (getInstanceForNode(currentNodeInfo) != newInstance)
                            return;

                        // Finally, mark node
                        InstanceData.markValueChanged(currentNodeInfo);
                    }

                    public void endVisitControl(XFormsControl xformsControl) {
                    }
                });
            }
        }
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

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return this.containingDocument;
    }

    /**
     * Return the List of XFormsEventHandler objects within this object.
     */
    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return (eventHandlers == null) ? null : (List) eventHandlers.get(getEffectiveId());
    }

    /**
     * Return the List of XFormsEventHandler objects for the given child instance.
     *
     * @param instanceId    event handlers for instance
     * @return              List of XFormsEventHandler, null if not found
     */
    public List getEventHandlersForInstance(String instanceId) {
        return (eventHandlers == null) ? null : (List) eventHandlers.get(instanceId);
    }
}
