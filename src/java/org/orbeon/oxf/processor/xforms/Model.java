/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xforms;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;
import org.dom4j.util.UserDataDocumentFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.BooleanModelItemProperty;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.processor.xforms.output.XFormsFunctionLibrary;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.XPathException;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents information from the XForms model.
 */
public class Model {

    private static final String DEFAULT_MODEL_ID = "wsrp_rewrite_xforms";

    private PipelineContext pipelineContext;
    private String id;
    private String schema;
    private String method;
    private String action;
    private String encoding;
    private List binds = new ArrayList();
    private Document initialInstance;

    private FunctionLibrary xformsFunctionLibrary = new XFormsFunctionLibrary();

    public Model(PipelineContext pipelineContext, Document modelDocument) {
        try {
            this.pipelineContext = pipelineContext;
            Element modelElement = modelDocument.getRootElement();

            // Basic check trying to make sure this is an XForms model
            {
                String rootNamespaceURI = modelElement.getNamespaceURI();
                if (!rootNamespaceURI.equals(Constants.XFORMS_NAMESPACE_URI))
                    throw new ValidationException("Root element of XForms model must be in namespace '"
                            + Constants.XFORMS_NAMESPACE_URI + "'. Found instead: '" + rootNamespaceURI + "'",
                            (LocationData) modelElement.getData());
            }

            // Get values from attributes on root element
            id = modelElement.attributeValue("id");
            schema = modelElement.attributeValue("schema");
            if (schema != null) {
                String systemID = ((LocationData) modelElement.getData()).getSystemID();
                schema = URLFactory.createURL(systemID, schema).toString();
            }

            // Get info from <xforms:submission> element
            {
                Element submissionElement = modelElement.element(new QName("submission", Constants.XFORMS_NAMESPACE));
                if (submissionElement != null) {
                    method = submissionElement.attributeValue("method");
                    action = submissionElement.attributeValue("action");
                    encoding = submissionElement.attributeValue("encoding");
                }
            }

            // Get info from <xforms:bind> elements
            handleBindContainer(modelElement, null);

            // Get initial instance
            {
                Element instanceContainer = modelElement.element(new QName("instance", Constants.XFORMS_NAMESPACE));
                if (instanceContainer != null) {
                    Element initialInstanceRoot = (Element)
                            Dom4jUtils.cloneNode((Element) instanceContainer.elements().get(0));
                    initialInstance = DocumentHelper.createDocument();
                    initialInstance.setRootElement(initialInstanceRoot);
                }
            }

        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    private void handleBindContainer(Element container, ModelBind parent) {
        for (Iterator i = container.elements(new QName("bind", Constants.XFORMS_NAMESPACE)).iterator(); i.hasNext();) {
            Element bind = (Element) i.next();
            ModelBind modelBind = new ModelBind(bind.attributeValue("id"), bind.attributeValue("nodeset"),
                    bind.attributeValue("relevant"), bind.attributeValue("calculate"), bind.attributeValue("type"),
                    bind.attributeValue("constraint"), bind.attributeValue("required"), bind.attributeValue("readonly"),
                    XMLUtils.getNamespaceContext(bind), (LocationData) bind.getData());
            if (parent != null) {
                parent.addChild(modelBind);
                modelBind.setParent(parent);
            }
            binds.add(modelBind);
            handleBindContainer(bind, modelBind);
        }
    }

    /**
     * Updates the instance according to information in the &lt;bind&gt;
     * elements.
     */
    public void applyInputOutputBinds(Document instance) {
        for (Iterator i = binds.iterator(); i.hasNext();) {
            ModelBind modelBind = (ModelBind) i.next();
            try {
                // Create XPath evaluator for this bind
                final DocumentWrapper documentWrapper = new DocumentWrapper(instance, null);
                applyInputOutputBinds(documentWrapper, modelBind);

            } catch (Exception e) {
                throw new ValidationException(e, modelBind.getLocationData());
            }
        }

        reconciliate(instance.getRootElement());
    }

    // Worker
    private void applyInputOutputBinds(final DocumentWrapper documentWrapper, final ModelBind modelBind)
            throws XPathException {
        // Handle relevant
        if (modelBind.getRelevant() !=  null) {
            iterateNodeSet(documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                            // Evaluate "relevant" XPath expression on this node
                            String xpath = "boolean(" + modelBind.getRelevant() + ")";
                            PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                    xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                            try {
                                boolean relevant = ((Boolean)expr.evaluateSingle()).booleanValue();
                                // Mark node
                                InstanceData instanceData = XFormsUtils.getInstanceData((Node) node);
                                instanceData.getRelevant().set(relevant);
                            } catch (XPathException e) {
                                throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                            } finally {
                                if(expr != null)
                                    expr.returnToPool();
                            }
                        }
                    });
                }

                // Handle calculate
                if (modelBind.getCalculate() != null) {
                    iterateNodeSet(documentWrapper, modelBind, new NodeHandler() {
                    	public void handleNode(Node node) {
                            if (node instanceof Element) {
                                // Compute calculated value
                                PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                        documentWrapper.wrap(node), modelBind.getCalculate(), modelBind.getNamespaceMap(), null,
                                        xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                                try {
                                    List result = expr.evaluate();
                                    // Place in element
                                    Element elementNode = (Element) node;
                                    Dom4jUtils.clearElementContent(elementNode);
                                    for (Iterator k = result.iterator(); k.hasNext();) {
                                        Object resultItem = k.next();
                                        if (resultItem instanceof Node) {
                                            elementNode.add(Dom4jUtils.cloneNode(elementNode));
                                        } else if(resultItem instanceof Item) {
                                            elementNode.add(DocumentFactory.getInstance().createText(((Item)resultItem).getStringValue()));
                                        } else {
                                            elementNode.add(DocumentFactory.getInstance().createText(resultItem.toString()));
                                        }
                                    }
                                } catch (XPathException e) {
                                    throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getCalculate() + "'", modelBind.getLocationData());
                                } finally {
                                    if(expr != null)
                                        expr.returnToPool();
                                }

                            } else {
                                // Compute calculated value and place in attribute
                                String xpath =  "string(" + modelBind.getCalculate() + ")";
                                PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                        documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                        xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                                try {
                                    String value = (String) expr.evaluateSingle();
                                    XFormsUtils.fillNode(node, value);
                                } catch (XPathException e) {
                                    throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                                } finally {
                                    if(expr != null)
                                        expr.returnToPool();
                                }
                            }
                        }
                    });
                }

                // Handle type constraint
                if (modelBind.getType() != null) {

                    // Need an evaluator to check and convet type below
                    final XPathEvaluator xpathEvaluator = new XPathEvaluator(documentWrapper);
                    StandaloneContext context = (StandaloneContext) xpathEvaluator.getStaticContext();
                    for (Iterator j = modelBind.getNamespaceMap().keySet().iterator(); j.hasNext();) {
                        String prefix = (String) j.next();
                        context.declareNamespace(prefix, (String) modelBind.getNamespaceMap().get(prefix));
                    }

                    iterateNodeSet(documentWrapper, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            if (XFormsUtils.getInstanceData(node).getValid().get()) {

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

                                // Try to perform casting
                                String nodeStringValue = node.getStringValue();
                                if (XFormsUtils.getInstanceData(node).getRequired().get() || nodeStringValue.length() != 0) {
                                    try {
                                        StringValue stringValue = new StringValue(nodeStringValue);
                                        XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getStaticContext().getConfiguration());
                                        stringValue.convert(requiredType, xpContext);
                                        markValidity(true, node, modelBind.getId());
                                    } catch (XPathException e) {
                                        markValidity(false, node, modelBind.getId());
                                    }
                                }
                            }
                        }
                    });
                }

                // Handle XPath constraint
                if (modelBind.getConstraint() != null) {
                    iterateNodeSet(documentWrapper, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            // Evaluate constraint
                            String xpath = "boolean(" + modelBind.getConstraint() + ")";
                            PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                    xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                            try {
                                Boolean valid = (Boolean)expr.evaluateSingle();
                                markValidity(valid.booleanValue(), node, modelBind.getId());
                            } catch (XPathException e) {
                                throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                            } finally {
                                if(expr != null)
                                    expr.returnToPool();
                            }
                        }
                    });
                }

                // Handle required
                if (modelBind.getRequired() != null) {
                    iterateNodeSet(documentWrapper, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            // Evaluate "required" XPath expression on this node
                            String xpath = "boolean(" + modelBind.getRequired() + ")";
                            PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                    xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                            try {
                                boolean required = ((Boolean)expr.evaluateSingle()).booleanValue();
                                // Mark node
                                InstanceData instanceData = XFormsUtils.getInstanceData((Node) node);
                                instanceData.getRequired().set(required);

                                // If required, check the string value is not empty
                                markValidity(!required || node.getStringValue().length() > 0, node, modelBind.getId());
                            } catch (XPathException e) {
                                throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                            } finally {
                                if(expr != null)
                                    expr.returnToPool();
                            }
                        }
                    });
                }

                // Handle read only
                if (modelBind.getReadonly() != null) {
                    iterateNodeSet(documentWrapper, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            // Evaluate "readonly" XPath expression on this node
                            String xpath = "boolean(" + modelBind.getReadonly() + ")";
                            PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                    xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                            try {
                                boolean readonly = ((Boolean)expr.evaluateSingle()).booleanValue();

                                // Mark node
                                InstanceData instanceData = XFormsUtils.getInstanceData((Node) node);
                                instanceData.getReadonly().set(readonly);
                            } catch (XPathException e) {
                                throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                            } finally {
                                if(expr != null)
                                    expr.returnToPool();
                            }
                        }
                    });
                }

        // Handle children binds
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                modelBind.getCurrentNode() == null ? documentWrapper : documentWrapper.wrap(modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
        try {
            List  nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                for(Iterator childIterator = modelBind.getChildrenIterator(); childIterator.hasNext();) {
                    ModelBind child = (ModelBind)childIterator.next();
                    child.setCurrentNode(node);
                    applyInputOutputBinds(documentWrapper, child);
                }
            }
        } catch (XPathException e) {
            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getNodeset() + "'", modelBind.getLocationData());
        } finally {
            if(expr != null)
                expr.returnToPool();
        }



    }

    /**
     * Reconciliate "DOM InstanceData annotations" with "attribute annotations"
     */
    private void reconciliate(Element element) {
        InstanceData instanceData = (InstanceData) element.getData();

        // Reconcile invalid bind ids
        {
            Attribute attribute = element.attribute(Constants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_QNAME);
            if (instanceData.getInvalidBindIds() != null || attribute != null) {
                // Merge
                String invalidBinds = "";
                {
                    Map invalidBindsMap = new HashMap();
                    if (instanceData.getInvalidBindIds() != null) {
                        invalidBindsMap.put(instanceData.getInvalidBindIds(), null);
                    }
                    if (attribute != null) {
                        if (attribute.getValue().length() > 0) {
                            invalidBindsMap.put(attribute.getValue(), null);
                        }
                    }
                    for(Iterator i=invalidBindsMap.keySet().iterator(); i.hasNext();)
                        invalidBinds += (String) i.next();
                }

                // Put in DOM and attribute
                instanceData.setInvalidBindIds(invalidBinds);
                updateAttribute(element, attribute, Constants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_QNAME, invalidBinds);
            }
        }

        // Reconcile boolean model item properties
        reconcileBoolean(instanceData.getReadonly(), element, Constants.XXFORMS_READONLY_ATTRIBUTE_QNAME);
        reconcileBoolean(instanceData.getRelevant(), element, Constants.XXFORMS_RELEVANT_ATTRIBUTE_QNAME);
        reconcileBoolean(instanceData.getRequired(), element, Constants.XXFORMS_REQUIRED_ATTRIBUTE_QNAME);
        reconcileBoolean(instanceData.getValid(), element, Constants.XXFORMS_VALID_ATTRIBUTE_QNAME);

        // Recurse
        for (Iterator i = element.elements().iterator(); i.hasNext();)
            reconciliate((Element) i.next());
    }

    private void reconcileBoolean(BooleanModelItemProperty property, Element element, QName qname) {
        Attribute attribute = element.attribute(qname);
        if (property.isSet() || attribute != null) {
            // Merge
            boolean outcome =
                    property.isSet() && attribute != null ? property.get() || Boolean.getBoolean(attribute.getValue())
                    : property.isSet() ? property.get()
                    : Boolean.valueOf(attribute.getValue()).booleanValue();

            // Set on DOM and attribute
            property.set(outcome);
            updateAttribute(element, attribute, qname, Boolean.toString(outcome));
        }
    }

    private void updateAttribute(Element element, Attribute attribute, QName qname, String value) {
        if (attribute == null) {
            // Add a namespace declaration if necessary
            Namespace namespace = element.getNamespaceForPrefix(qname.getNamespacePrefix());
            if (namespace == null) {
                element.addNamespace(qname.getNamespacePrefix(), qname.getNamespaceURI());
            } else if (!namespace.getURI().equals(qname.getNamespaceURI())) {
                throw new ValidationException("Cannot add attribute to node with 'xxforms' prefix"
                        + " as the prefix is already mapped to another URI",
                        XFormsUtils.getInstanceData(element).getLocationData());
            }

            // Add attribute
            attribute = UserDataDocumentFactory.getInstance().createAttribute(element, qname, value);
            attribute.setData(new InstanceData((LocationData) attribute.getData()));
            element.add(attribute);
        } else {
            attribute.setValue(value);
        }
    }

    private void iterateNodeSet(DocumentWrapper documentWrapper,
                                ModelBind modelBind, NodeHandler nodeHandler) {
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                modelBind.getCurrentNode() == null ? documentWrapper : documentWrapper.wrap(modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary);
        try {
            List  nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                nodeHandler.handleNode(node);
            }
        } catch (XPathException e) {
            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getNodeset() + "'", modelBind.getLocationData());
        } finally {
            if(expr != null)
                expr.returnToPool();
        }

    }
    
    private interface NodeHandler {
    	void handleNode(Node node);
    }
    
    /**
     * Marks the given node as invalid by:
     * <ul>
     *     <li>setting invalid flag on the node InstanceData</li>
     *     <li>adding an attribute xxforms:error="message"</li>
     * </ul>
     */
    private void markValidity(boolean valid, Node node, String id) {
        InstanceData instanceData = XFormsUtils.getInstanceData(node);
        if (instanceData.getValid().get() || !valid) {
            instanceData.getValid().set(valid);
        }
        if (id != null && !valid)
            instanceData.setInvalidBindIds(instanceData.getInvalidBindIds() == null 
                    ? id : instanceData.getInvalidBindIds() + " "  + id);
    }

    public String getId() {
        return id == null ? DEFAULT_MODEL_ID : id;
    }

    public String getSchema() {
        return schema;
    }

    public String getMethod() {
        return method;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEncoding() {
        return encoding;
    }

    public Document getInitialInstance() {
        return initialInstance;
    }

    public List getBindNodeset(PipelineContext context, ModelBind bind, DocumentWrapper wrapper, Document instance) {
        // get a list of parents, orderd by grand father first
        List parents = new ArrayList();
        parents.add(bind);
        ModelBind parent = bind;
        while( (parent = parent.getParent()) != null) {
            parents.add(parent);
        }
        Collections.reverse(parents);

        // find the final node
        List nodeset = new ArrayList();
        nodeset.add(instance);
        for(Iterator i = parents.iterator(); i.hasNext();) {
            ModelBind current = (ModelBind)i.next();
            List currentModelBindResults = new ArrayList();
            for(Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node)j.next();
                PooledXPathExpression expr = XPathCache.getXPathExpression(context, wrapper.wrap(node),
                        current.getNodeset(), current.getNamespaceMap(), null, xformsFunctionLibrary, current.getLocationData().getSystemID());
                try {
                    currentModelBindResults.addAll(expr.evaluate());
                }catch(XPathException e) {
                    throw new OXFException(e);
                }finally{
                    if(expr != null)
                        expr.returnToPool();
                }
            }
            nodeset.addAll(currentModelBindResults);
            // last iteration of i: remove all except last
            if(!i.hasNext())
                nodeset.retainAll(currentModelBindResults);

        }
        return nodeset;
    }

    public ModelBind getModelBindById(String id) {
        for(Iterator i = binds.iterator(); i.hasNext();) {
            ModelBind bind = (ModelBind)i.next();
            ModelBind result = getModelBindByIdWorker(bind, id);
            if(result != null)
                return result;
        }
        return null;
    }

    private ModelBind getModelBindByIdWorker(ModelBind parent, String id) {
        if(id.equals(parent.getId()))
            return parent;
        // Look in children
        for(Iterator j = parent.getChildrenIterator(); j.hasNext();) {
            ModelBind child = (ModelBind)j.next();
            ModelBind bind = getModelBindByIdWorker(child, id);
            if(bind != null)
                return bind;
        }
        return null;
    }
}
