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

import org.dom4j.*;
import org.dom4j.util.UserDataDocumentFactory;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.BooleanModelItemProperty;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.processor.xforms.output.XFormsFunctionLibrary;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.helpers.NamespaceSupport;

import java.net.MalformedURLException;
import java.util.*;

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

    private FunctionLibrary xformsFunctionLibrary = new XFormsFunctionLibrary();

    public Model(PipelineContext pipelineContext) {
        this.pipelineContext = pipelineContext;
    }

    /**
     * The XForms model must be sent to the returned content handler.
     */
    public ContentHandler getContentHandlerForModel() {

        return new ForwardingContentHandler() {
            private int level = 0;
            private NamespaceSupport namespaceSupport = new NamespaceSupport();
            Locator locator;
            Stack bindStack = new Stack();

            public void startElement(String uri, String localname, String qName, Attributes attributes) {
                try {
                    level++;
                    if (level == 1) {
                        if (!Constants.XFORMS_NAMESPACE_URI.equals(uri))
                            throw new ValidationException("Root element of XForms model must be in namespace "
                                    + Constants.XFORMS_NAMESPACE_URI, new LocationData(locator));
                        id = attributes.getValue("id");
                        schema = attributes.getValue("schema");
                        if (schema != null)
                            schema = URLFactory.createURL(locator.getSystemId(), schema).toString();
                    } else if (level >= 2 && Constants.XFORMS_NAMESPACE_URI.equals(uri)) {
                        if ("bind".equals(localname)) {
                            Map namespaces = new HashMap();
                            for (Enumeration i = namespaceSupport.getPrefixes(); i.hasMoreElements();) {
                                String prefix = (String) i.nextElement();
                                namespaces.put(prefix, namespaceSupport.getURI(prefix));
                            }
                            ModelBind bind = new ModelBind(attributes.getValue("id"), attributes.getValue("nodeset"),
                                    attributes.getValue("relevant"),
                                    attributes.getValue("calculate"), attributes.getValue("type"),
                                    attributes.getValue("constraint"), attributes.getValue("required"),
                                    attributes.getValue("readonly"),
                                    namespaces, new LocationData(locator));
                            if(!bindStack.empty()) {
                                ModelBind parent = (ModelBind)bindStack.peek();
                                parent.addChild(bind);
                                bind.setParent(parent);
                            } else
                                binds.add(bind);

                            bindStack.push(bind);
                        } else if ("submission".equals(localname)) {
                            method = attributes.getValue("method");
                            action = attributes.getValue("action");
                            encoding = attributes.getValue("encoding");
                        }
                    }
                } catch (MalformedURLException e) {
                    throw new ValidationException(e, new LocationData(locator));
                }
            }

            public void endElement(String uri, String localname, String qName) {
                level--;
                if("bind".equals(localname))
                    bindStack.pop();
            }

            public void startPrefixMapping(String prefix, String uri) {
                namespaceSupport.pushContext();
                // HACK: why do we get a empty string URI?
                if (!"".equals(uri))
                    namespaceSupport.declarePrefix(prefix, uri);
            }

            public void endPrefixMapping(String prefix) {
                namespaceSupport.popContext();
            }

            public void setDocumentLocator(Locator locator) {
                this.locator = locator;
            }
        };
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
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null, xformsFunctionLibrary);
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
                                        documentWrapper.wrap(node), modelBind.getCalculate(), modelBind.getNamespaceMap(), null, xformsFunctionLibrary);
                                try {
                                    List result = expr.evaluate();
                                    // Place in element
                                    Element elementNode = (Element) node;
                                    elementNode.clearContent();
                                    for (Iterator k = result.iterator(); k.hasNext();) {
                                        Object resultItem = k.next();
                                        if (resultItem instanceof Node) {
                                            elementNode.add(((Node) elementNode.clone()));
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
                                        documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null, xformsFunctionLibrary);
                                try {
                                    String value = ((String)expr.evaluateSingle());
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
                            if (XFormsUtils.getInstanceData(node).getValid().get()) {
                                // Evaluate constraint
                                String xpath = "boolean(" + modelBind.getConstraint() + ")";
                                PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                        documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null, xformsFunctionLibrary);

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
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null, xformsFunctionLibrary);

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
                                    expr.returnToPool();;
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
                                    documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null, xformsFunctionLibrary);

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
                null, xformsFunctionLibrary);
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
                        invalidBinds = invalidBinds + (String)i.next();
                }

                // Put in DOM and attribute
                instanceData.setInvalidBindIds(invalidBinds);
                if (attribute == null) {
                    attribute = UserDataDocumentFactory.getInstance().createAttribute
                            (element, Constants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_QNAME, invalidBinds);
                    attribute.setData(new InstanceData((LocationData) attribute.getData()));
                    element.add(attribute);
                } else {
                    attribute.setValue(invalidBinds);
                }
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
            if (attribute == null) {
                attribute = UserDataDocumentFactory.getInstance().createAttribute
                        (element, qname, Boolean.toString(outcome));
                attribute.setData(new InstanceData((LocationData) attribute.getData()));
                element.add(attribute);
            } else {
                attribute.setValue(Boolean.toString(outcome));
            }
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
    	public void handleNode(Node node);
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
                        current.getNodeset(), current.getNamespaceMap(), null, xformsFunctionLibrary);
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

    private void getBindNodesetWorker(PipelineContext context, List nodeset, DocumentWrapper wrapper, Node currentNode) {

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
