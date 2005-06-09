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
package org.orbeon.oxf.xforms;

import orbeon.apache.xml.utils.NamespaceSupport2;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.element.XFormsElement;
import org.xml.sax.ContentHandler;

import java.util.*;

/**
 * Context in which control elements are executed.
 */
public class XFormsElementContext extends XFormsControls {

    private PipelineContext pipelineContext;
    private ContentHandler contentHandler;

    private Map repeatIdToIndex = new HashMap();
    private Stack elements = new Stack();

    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();

    private String encryptionPassword;

    public XFormsElementContext(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, ContentHandler contentHandler) {

        super(containingDocument, null);
        super.initialize(pipelineContext);

        this.pipelineContext = pipelineContext;
        this.contentHandler = contentHandler;
        this.encryptionPassword = XFormsUtils.getEncryptionKey();
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
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

    public String getEncryptionPassword() {
        return encryptionPassword;
    }

    public void pushBinding(String ref, String nodeset, String model, String bind) {
        super.pushBinding(pipelineContext, ref, nodeset, model, bind, null, getCurrentPrefixToURIMap());
    }

    public void setRepeatIdIndex(String repeatId, int index) {
        // Update current element of nodeset in stack
        popBinding();
        List newNodeset = new ArrayList();
        newNodeset.add(getCurrentNodeset().get(index - 1));
        contextStack.push(new Context(getCurrentContext().model, newNodeset, true, null));//TODO: check this

        if (repeatId != null)
            repeatIdToIndex.put(repeatId, new Integer(index));
    }

    public void endRepeatId(String repeatId) {
        if (repeatId != null)
            repeatIdToIndex.remove(repeatId);
        popBinding();
    }

    public void startRepeatId(String repeatId) {
        contextStack.push(null);
    }

    public Map getRepeatIdToIndex() {
        return repeatIdToIndex;
    }

    public Map getCurrentPrefixToURIMap() {
        Map prefixToURI = new HashMap();
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            String prefix = (String) e.nextElement();
            prefixToURI.put(prefix, namespaceSupport.getURI(prefix));
        }
        return prefixToURI;
    }

    public NamespaceSupport2 getNamespaceSupport() {
        return namespaceSupport;
    }

    /**
     * Returns the text value of the currently referenced node in the instance.
     */
    public String getRefValue() {
        Node node = getCurrentSingleNode(getCurrentContext());
        return node instanceof Element ? ((Element) node).getStringValue()
                : node instanceof Attribute ? ((Attribute) node).getValue()
                : null;
    }
}
