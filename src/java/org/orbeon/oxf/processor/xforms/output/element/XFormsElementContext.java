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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.XFormsModel;
import org.orbeon.oxf.processor.xforms.output.XFormsFunctionLibrary;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;

import java.util.*;

/**
 * Context in which elements are executed
 */
public class XFormsElementContext {

    private ContentHandler contentHandler;
    private XFormsModel model;
    private Locator locator;
    private Stack elements = new Stack();
    private Stack nodesetStack = new Stack();
    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();
    private Map repeatIdToIndex = new HashMap();
    private PipelineContext pipelineContext;
    private DocumentWrapper documentWrapper;
    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);
    private String encryptionPassword;

    public XFormsElementContext(PipelineContext pipelineContext, ContentHandler contentHandler, XFormsModel model) {
        this.pipelineContext = pipelineContext;
        this.contentHandler = contentHandler;
        this.model = model;
        this.documentWrapper = new DocumentWrapper(model.getInstanceDocument(), null);
        this.encryptionPassword = SecureUtils.generateRandomPassword();
    }

    public void pushRelativeXPath(String bind, String ref, String nodeset) {
        PooledXPathExpression expr = null;
        try {
            if (bind != null) {
                // Resolve the bind id to a node
                nodesetStack.push(model.getBindNodeset(pipelineContext, model.getModelBindById(bind), documentWrapper));
            } else if (ref != null || nodeset != null) {
                // Evaluate new xpath in context of current node
                expr = XPathCache.getXPathExpression(pipelineContext, documentWrapper.wrap(getCurrentSingleNode()),
                        ref != null ? ref : nodeset, getCurrentPrefixToURIMap(), null, functionLibrary);
                List newNodeset = expr.evaluate();
                if (ref != null && newNodeset.isEmpty())
                    throw new ValidationException("Single-node binding expression '"
                            + ref + "' returned an empty nodeset", new LocationData(locator));
                nodesetStack.push(newNodeset);
            } else {
                // No change to current node
                nodesetStack.push(getCurrentNodeset());
            }
        } catch (XPathException e) {
            throw new ValidationException(e, new LocationData(locator));
        } finally {
            if(expr != null)
                expr.returnToPool();
        }
    }

    public Node getCurrentSingleNode() {
        if (nodesetStack.isEmpty()) {
            return model.getInstanceDocument();
        } else {
            List nodeset = getCurrentNodeset();
            if (nodeset.size() == 0)
                throw new ValidationException("Single node binding to unexistant node in instance",
                        new LocationData(locator));
            else
                return (Node) nodeset.get(0);
        }
    }

    public List getCurrentNodeset() {
        return nodesetStack.isEmpty() ? Arrays.asList(new Object[] { model.getInstanceDocument() })
            : (List) nodesetStack.peek();
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

    /**
     * Returns the text value of the currently referenced node in the instance.
     */
    public String getRefValue() {
        Node node = getCurrentSingleNode();
        return node instanceof Element ? ((Element) node).getStringValue()
            : node instanceof Attribute ? ((Attribute) node).getValue()
            : null;
    }

    public void startRepeatId(String repeatId) {
        nodesetStack.push(null);
    }

    public void setRepeatIdIndex(String repeatId, int index) {
        // Update current element of nodeset in stack
        nodesetStack.pop();
        List newNodeset = new ArrayList();
        newNodeset.add(getCurrentNodeset().get(index - 1));
        nodesetStack.push(newNodeset);

        if (repeatId != null)
            repeatIdToIndex.put(repeatId, new Integer(index));
    }

    public void endRepeatId(String repeatId) {
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

    public DocumentWrapper getDocumentWrapper() {
        return documentWrapper;
    }

    public Map getRepeatIdToIndex() {
        return repeatIdToIndex;
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

    public XFormsElement getParentElement(int level) {
        return elements.size() > level + 1 ? (XFormsElement) elements.get(elements.size() - (level + 2)) : null;
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    public Document getInstance() {
        return model.getInstanceDocument();
    }

    public String getEncryptionPassword() {
        return encryptionPassword;
    }
}
