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
package org.orbeon.oxf.processor.xforms.output.element;

import orbeon.apache.xml.utils.NamespaceSupport2;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.Model;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.processor.xforms.output.XFormsFunctionLibrary;
import org.orbeon.oxf.processor.xforms.output.XFormsOutputConfig;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.xpath.Variable;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;

import java.util.*;

/**
 * Context in which elements are executed
 */
public class XFormsElementContext {

    private Document instance;
    private Model model;
    private XFormsOutputConfig config;
    private ContentHandler contentHandler;
    private ContentHandlerHelper contentHandlerHelper;
    private Locator locator;
    private Stack refStack = new Stack();
    private Stack elements = new Stack();
    private Stack nodesetStack = new Stack();
    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();
    private Map repeatIdToIndex = new HashMap();
    private PipelineContext pipelineContext;
    private DocumentWrapper documentWrapper;
    private Map variablesValue = new HashMap();
    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);

    public XFormsElementContext(PipelineContext pipelineContext, Model model, Document instance,
                                XFormsOutputConfig config, ContentHandler contentHandler) {
        this.pipelineContext = pipelineContext;
        this.model = model;
        this.instance = instance;
        this.config = config;
        this.contentHandler = contentHandler;
        this.contentHandlerHelper = new ContentHandlerHelper(contentHandler);
        this.documentWrapper = new DocumentWrapper(instance, null);
    }

    public void pushRelativeXPath(String xpath, boolean single) {
        PooledXPathExpression expr = null;
        try {
            if (xpath == null) {
                // No change to current node
                nodesetStack.push(getCurrentNode());
            } else {
                // Evaluate new xpath in context of current node
                expr = XPathCache.getXPathExpression(pipelineContext,
                        documentWrapper.wrap(getCurrentNode()),
                        xpath,
                        getCurrentPrefixToURIMap(),
                        null,
                        functionLibrary);
                if (single) {
                    Node node = (Node) expr.evaluateSingle();
                    if (node == null)
                        throw new ValidationException("Single binding expression '" + xpath
                                + "' must return at least one node", new LocationData(locator));
                    nodesetStack.push(node);
                } else {
                    nodesetStack.push(expr.evaluate());
                }
            }
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if(expr != null)
                expr.returnToPool();
        }

    }

    public Node getCurrentNode() {
        if (nodesetStack.isEmpty()) {
            return instance;
        } else {
            Object current = nodesetStack.peek();
            if (!(current instanceof Node))
                throw new ValidationException("Current context is a nodelist, a node is expected",
                        new LocationData(locator));
            return (Node) nodesetStack.peek();
        }
    }

    public List getCurrentNodeset() {
        Object current = nodesetStack.peek();
        if (!(current instanceof List))
            throw new ValidationException("Current context is a node, a nodelist is expected",
                    new LocationData(locator));
        return (List) current;
    }

    public Map getCurrentPrefixToURIMap() {
        Map prefixToURI = new HashMap();
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            String prefix = (String) e.nextElement();
            prefixToURI.put(prefix, namespaceSupport.getURI(prefix));
        }
        return prefixToURI;
    }

    public void popRelativeXPath() {
        nodesetStack.pop();
    }

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    public void pushGroupRef(String ref) {
        refStack.push(ref);
    }

    public void popGroupRef() {
        refStack.pop();
    }

    /**
     * Returns a name used in the output, which is an encoding of an XPath
     * expression that can be evaluated on the instance.
     *
     * @param annotateElement
     */
    public String getRefName(boolean annotateElement) {
        PooledXPathExpression xpath = null;
        try {
            xpath = getXPathExpression(getRefXPath());
            xpath.setContextNode(getDocumentWrapper().wrap(getRefNode()));
            Object value = xpath.evaluateSingle();
            if (!(value instanceof Element) && !(value instanceof Attribute))
                throw new OXFException("Expression '" + getRefXPath() + "' must reference an element or an attribute");
            return getRefName((Node) value, annotateElement);
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpath != null)
                xpath.returnToPool();
        }
    }

    public String getRefName(Node value, boolean annotateElement) {
        return XFormsUtils.getNameForNode((Node) value, annotateElement);
    }

    /**
     * Returns a map from name to value, for all the elements and attributes for
     * which the method <code>getRefName</code> have been called (i.e. that has
     * not been referenced by a form control.
     */
    public Map getNonReferencedNameValueMap() {
        Map result = new HashMap();
        getNonReferencedNameValueMap(instance.getRootElement(), result);
        return result;
    }

    /**
     * Worker method for <code>getNonReferencedNameValueMap</code>
     */
    private void getNonReferencedNameValueMap(Element element, Map map) {

        // Add attribute values
        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            if (!attribute.getNamespaceURI().equals(Constants.XXFORMS_NAMESPACE_URI)
                    && !((InstanceData) attribute.getData()).isGenerated())
                map.put(XFormsUtils.getNameForNode(attribute, true), attribute.getValue());
        }

        List children = element.elements();
        if (children.isEmpty()) {
            // Add value of this node
            if (!((InstanceData) element.getData()).isGenerated())
                map.put(XFormsUtils.getNameForNode(element, true), element.getText());
        } else {
            // Recurse through children
            for (Iterator i = children.iterator(); i.hasNext();) {
                Element child = (Element) i.next();
                getNonReferencedNameValueMap(child, map);
            }
        }
    }

    /**
     * Returns an XPath expression that can used on the instance to return the
     * current context node.
     */
    public String getRefXPath() {
        if (refStack.isEmpty()) {
            return "";
        } else {
            return (String) refStack.peek();
        }
    }

    /**
     * Returns the text value of the currently referenced node in the instance.
     */
    public String getRefValue() {
        return getRefValue(getRefXPath());
    }

    public String getRefValue(Node node) {
        if (node instanceof Element)
            return ((Element) node).getStringValue();
        else
            return ((Attribute) node).getValue();
    }

    private String getRefValue(String refXPath) {
        List list = getRefNodeList(refXPath);
        if (list.size() != 1)
            throw new OXFException("Expression '" + refXPath
                    + "' must return exactly element or an attribute");
        Node node = (Node) list.get(0);
        return getRefValue(node);
    }


    public InstanceData getRefInstanceData() {
        return getRefInstanceData(getRefNode());
    }

    public InstanceData getRefInstanceData(Node node) {
        return (InstanceData) (node instanceof Element
                ? ((Element) node).getData()
                : ((Attribute) node).getData());
    }

    public List getRefNodeList() {
        return getRefNodeList(getRefXPath());
    }

    private List getRefNodeList(String refXPath) {
        PooledXPathExpression expr= null;
        try {
            expr = getXPathExpression(refXPath);
//            for(Iterator i = variablesValue.keySet().iterator(); i.hasNext();) {
//                String name = (String)i.next();
//                Integer value = (Integer)variablesValue.get(name);
//                expr.setVariable(name, value);
//            }
            List result = expr.evaluate();
            if (result == null)
                throw new OXFException("Expression '" + refXPath
                        + "' must return an element, an attribute or a nodeset");
            return result;
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    private PooledXPathExpression getXPathExpression(String refXPath) {
        return getXPathExpression(refXPath, variablesValue);
    }

    private PooledXPathExpression getXPathExpression(String refXPath, Map variables) {
        // Create string version of XPath expression
        Map prefixToURIMap = new HashMap();
        String xpath = XPathUtils.xpathWithFullURIString(refXPath, prefixToURIMap);

        return XPathCache.getXPathExpression(getPipelineContext(), getDocumentWrapper(), xpath, prefixToURIMap, variables, functionLibrary);
    }

    public Node getRefNode() {
        List refNodeList = getRefNodeList();
        Node node = refNodeList.size() == 0 ? null :
                refNodeList.get(0) instanceof Node ? (Node) refNodeList.get(0) : null;
        if (node == null)
            throw new OXFException("Expression '" + getRefXPath() + "' must return a non-empty nodeset");
        return node;
    }

    public void addRepeatId(String repeatId) {
        nodesetStack.push(null);
    }

    public void setRepeatIdIndex(String repeatId, String variableName, int index) {
        // Update current element of nodeset in stack
        nodesetStack.pop();
        nodesetStack.push(((List) nodesetStack.peek()).get(index - 1));

        Integer indexObj = new Integer(index);
        // Store index for current repeat id, if one is specified
        if (repeatId != null)
            repeatIdToIndex.put(repeatId, indexObj);

        // variableName is null when iterating over an xf:itemset
        if(variableName != null)
            variablesValue.put(variableName, indexObj);
    }

    public void removeRepeatId(String repeatId) {
        if (repeatId != null)
            repeatIdToIndex.remove(repeatId);
        nodesetStack.pop();
    }

    public int getRepeatIdIndex(String repeatId, LocationData locationData) {
        Object index = repeatIdToIndex.get(repeatId);
        if (index == null)
            throw new ValidationException("Function index uses repeat id '" + repeatId
                    + "' which it not in scope", locationData);
        return ((Integer) index).intValue();
    }

    public Document getInstance() {
        return instance;
    }

    public DocumentWrapper getDocumentWrapper() {
        return documentWrapper;
    }

    public Map getRepeatIdToIndex() {
        return repeatIdToIndex;
    }

    public Model getModel() {
        return model;
    }

    public XFormsOutputConfig getConfig() {
        return config;
    }

    public ContentHandlerHelper getContentHandlerHelper() {
        return contentHandlerHelper;
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    public Locator getLocator() {
        return locator;
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }

    public NamespaceSupport2 getNamespaceSupport() {
        return namespaceSupport;
    }

    public void pushElement(XFormsElement element) {
        elements.push(element);
    }

    public XFormsElement popElement() {
        return (XFormsElement) elements.pop();
    }

    public XFormsElement peekElement() {
        return (XFormsElement) elements.peek();
    }

    public int getElementDepth() {
        return elements.size();
    }

    public XFormsElement getParentElement(int level) {
        return elements.size() > level + 1 ? (XFormsElement) elements.get(elements.size() - (level + 2)) : null;
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }
}
