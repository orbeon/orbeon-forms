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
import org.orbeon.oxf.processor.xforms.output.BooleanModelItemProperty;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.XPathException;
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

    private String id;
    private String schema;
    private String method;
    private String action;
    private String encoding;
    private List binds = new ArrayList();

    /**
     * The XForms model must be sent to the returned content handler.
     */
    public ContentHandler getContentHandlerForModel() {

        return new ForwardingContentHandler() {
            private int level = 0;
            private NamespaceSupport namespaceSupport = new NamespaceSupport();
            Locator locator;

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
                    } else if (level == 2 && Constants.XFORMS_NAMESPACE_URI.equals(uri)) {
                        if ("bind".equals(localname)) {
                            Map namespaces = new HashMap();
                            for (Enumeration i = namespaceSupport.getPrefixes(); i.hasMoreElements();) {
                                String prefix = (String) i.nextElement();
                                namespaces.put(prefix, namespaceSupport.getURI(prefix));
                            }
                            binds.add(new ModelBind(attributes.getValue("id"), attributes.getValue("nodeset"),
                                    attributes.getValue("relevant"),
                                    attributes.getValue("calculate"), attributes.getValue("type"),
                                    attributes.getValue("constraint"), attributes.getValue("required"),
                                    attributes.getValue("readonly"),
                                    namespaces, new LocationData(locator)));
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
        XFormsUtils.setInitialDecoration(instance.getDocument().getRootElement());
        for (Iterator i = binds.iterator(); i.hasNext();) {
            final ModelBind modelBind = (ModelBind) i.next();

            try {
                // Create XPath evaluator for this bind
                DocumentWrapper documentWrapper = new DocumentWrapper(instance, null);
                final XPathEvaluator xpathEvaluator = new XPathEvaluator(documentWrapper);
                StandaloneContext context = (StandaloneContext) xpathEvaluator.getStaticContext();
                for (Iterator j = modelBind.getNamespaceMap().keySet().iterator(); j.hasNext();) {
                    String prefix = (String) j.next();
                    context.declareNamespace(prefix, (String) modelBind.getNamespaceMap().get(prefix));
                }

                // Handle relevant
                if (modelBind.getRelevant() !=  null) {
                    iterateNodeSet(documentWrapper, xpathEvaluator, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            // Evaluate "relevant" XPath expression on this node
                            boolean relevant = ((Boolean) XPathUtils.evaluateSingle
                                    (xpathEvaluator, "boolean(" + modelBind.getRelevant() + ")", 
                                    modelBind.getLocationData())).booleanValue();

                            // Mark node
                            InstanceData instanceData = XFormsUtils.getInstanceData((Node) node);
                            instanceData.getRelevant().set(relevant);
                        }
                    });
                }

                // Handle calculate
                if (modelBind.getCalculate() != null) {
                    iterateNodeSet(documentWrapper, xpathEvaluator, modelBind, new NodeHandler() {
                    	public void handleNode(Node node) {
                            if (node instanceof Element) {
                                // Compute calculated value
                                List result = XPathUtils.evaluate(xpathEvaluator, modelBind.getCalculate(), 
                                        modelBind.getLocationData());

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

                            } else {
                                // Compute calculated value and place in attribute
                                XFormsUtils.fillNode(node, (String) XPathUtils.evaluateSingle(xpathEvaluator,
                                        "string(" + modelBind.getCalculate() + ")", modelBind.getLocationData()));
                            }
                        }
                    });
                }

                // Handle type constraint
                if (modelBind.getType() != null) {
                    iterateNodeSet(documentWrapper, xpathEvaluator, modelBind, new NodeHandler() {
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
                                        new StringValue(nodeStringValue).convert(requiredType);
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
                    iterateNodeSet(documentWrapper, xpathEvaluator, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            if (XFormsUtils.getInstanceData(node).getValid().get()) {
                                // Evaluate constraint
                                Boolean valid = (Boolean) XPathUtils.evaluateSingle(xpathEvaluator, 
                                        "boolean(" + modelBind.getConstraint() + ")", modelBind.getLocationData());
                                markValidity(valid.booleanValue(), node, modelBind.getId());
                            }
                        }   
                    });
                }

                // Handle required
                if (modelBind.getRequired() != null) {
                    iterateNodeSet(documentWrapper, xpathEvaluator, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            // Evaluate "required" XPath expression on this node
                            boolean required = ((Boolean) (XPathUtils.evaluateSingle
                                    (xpathEvaluator, "boolean(" + modelBind.getRequired() + ")", 
                                            modelBind.getLocationData()))).booleanValue();

                            // Mark node
                            InstanceData instanceData = XFormsUtils.getInstanceData((Node) node);
                            instanceData.getRequired().set(required);

                            // If required, check the string value is not empty
                            markValidity(!required || node.getStringValue().length() > 0, node, modelBind.getId());
                        }
                    });
                }

                // Handle read only
                if (modelBind.getReadonly() != null) {
                    iterateNodeSet(documentWrapper, xpathEvaluator, modelBind, new NodeHandler() {
                        public void handleNode(Node node) {
                            // Evaluate "readonly" XPath expression on this node
                            boolean readonly = ((Boolean) (XPathUtils.evaluateSingle
                                    (xpathEvaluator, "boolean(" + modelBind.getReadonly() + ")", 
                                            modelBind.getLocationData()))).booleanValue();

                            // Mark node
                            InstanceData instanceData = XFormsUtils.getInstanceData((Node) node);
                            instanceData.getReadonly().set(readonly);
                        }
                    });
                }
            } catch (Exception e) {
                throw new ValidationException(e, modelBind.getLocationData());
            }
        }

        reconciliate(instance.getRootElement());
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
                final String invalidBinds;
                {
                    StringBuffer invalidBindsBuffer = new StringBuffer();
                    if (instanceData.getInvalidBindIds() != null) {
                        invalidBindsBuffer.append(instanceData.getInvalidBindIds());
                    }
                    if (attribute != null) {
                        if (attribute.getValue().length() > 0) {
                            invalidBindsBuffer.append(' ');
                            invalidBindsBuffer.append(attribute.getValue());
                        }
                    }
                    invalidBinds = invalidBindsBuffer.toString();
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

    private void iterateNodeSet(DocumentWrapper documentWrapper, XPathEvaluator xpathEvaluator,
                                ModelBind modelBind, NodeHandler nodeHandler) {
        List nodeset = XPathUtils.evaluate(xpathEvaluator, modelBind.getNodeset(), modelBind.getLocationData());
        for (Iterator j = nodeset.iterator(); j.hasNext();) {
            Node node = (Node) j.next();
            xpathEvaluator.setContextNode(documentWrapper.wrap(node));
            nodeHandler.handleNode(node);
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
}
