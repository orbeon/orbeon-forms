/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.element.XFormsElement;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.xml.sax.Locator;

import java.util.*;

/**
 * Context in which control elements are executed.
 */
public class XFormsControls {

    private Locator locator;
    private Stack elements = new Stack();

    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();
    private Map repeatIdToIndex = new HashMap();

    XFormsContainingDocument containingDocument;
    private Document controlsDocument;

    private Stack contextStack = new Stack();

    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);

    public XFormsControls(XFormsContainingDocument containingDocument, Document controlsDocument) {
        this.containingDocument = containingDocument;
        this.controlsDocument = controlsDocument;
    }

    public void initialize() {

        // Clear existing stack
        contextStack.clear();

        // Push the default context
        final XFormsModel defaultModel = containingDocument.getModel("");
        final List defaultNodeset = Arrays.asList(new Object[] { defaultModel.getInstance().getDocument() });
        contextStack.push(new Context(defaultModel, defaultNodeset));
    }

    public Document getControlsDocument() {
        return controlsDocument;
    }

    /**
     * Set the specified element with binding at the top of the stack and build the stack for the
     * parents.
     */
    public void setBinding(PipelineContext pipelineContext, Element bindingElement) {
        // Reinitialize context stack
        initialize();

        // Create ancestors-or-self list
        final List ancestorsOrSelf = new ArrayList();
        Element currentElement = bindingElement;
        while (currentElement != null) {
            ancestorsOrSelf.add(currentElement);
            currentElement = currentElement.getParent();
        }

        // Bind up to the specified element
        Collections.reverse(ancestorsOrSelf);
        for (Iterator i = ancestorsOrSelf.iterator(); i.hasNext();) {
            pushBinding(pipelineContext, (Element) i.next());
        }
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     */
    public void pushBinding(PipelineContext pipelineContext, Element bindingElement) {
        pushBinding(pipelineContext, bindingElement.attributeValue("ref"), bindingElement.attributeValue("nodeset"),
                bindingElement.attributeValue("model"), bindingElement.attributeValue("bind"));
    }

    public void pushBinding(PipelineContext pipelineContext, String ref, String nodeset, String model, String bind) {

        // Determine current context
        Context currentContext = getCurrentContext();

        // Handle model
        final XFormsModel newModel;
        if (model != null) {
            newModel = containingDocument.getModel(model);
        } else {
            newModel = currentContext.model;
        }

        // Handle nodeset
        final List newNodeset;
        {
            if (bind != null) {
                // Resolve the bind id to a node
                newNodeset = newModel.getBindNodeset(pipelineContext, newModel.getModelBindById(bind));
            } else if (ref != null || nodeset != null) {
                // Evaluate new XPath in context of current node
                newNodeset = newModel.getInstance().evaluateXPath(pipelineContext, getCurrentSingleNode(currentContext),
                        ref != null ? ref : nodeset, getCurrentPrefixToURIMap(), null, functionLibrary, null);

                if (ref != null && newNodeset.isEmpty())
                    throw new ValidationException("Single-node binding expression '"
                            + ref + "' returned an empty nodeset", new LocationData(locator));
            } else {
                // No change to current nodeset
                newNodeset = currentContext.nodeset;
            }
        }

        // Push new context
        contextStack.push(new Context(newModel, newNodeset));
    }

    private Context getCurrentContext() {
        return (Context) contextStack.peek();
    }

    public Node getCurrentSingleNode(Context currentContext) {
        if (currentContext.nodeset.size() == 0)
            throw new ValidationException("Single node binding to unexistant node in instance", new LocationData(locator));

        return (Node) currentContext.nodeset.get(0);
    }

    public Node getCurrentSingleNode() {
        return getCurrentSingleNode(getCurrentContext());
    }

    public List getCurrentNodeset() {
        return getCurrentContext().nodeset;
    }

    public Map getCurrentPrefixToURIMap() {
        Map prefixToURI = new HashMap();
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            String prefix = (String) e.nextElement();
            prefixToURI.put(prefix, namespaceSupport.getURI(prefix));
        }
        return prefixToURI;
    }

    public void popBinding() {
        if (contextStack.size() == 1)
            throw new OXFException("Attempt to clear XForms controls context stack.");
        contextStack.pop();
    }

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
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

    public void startRepeatId(String repeatId) {
        contextStack.push(null);
    }

    public void setRepeatIdIndex(String repeatId, int index) {
        // Update current element of nodeset in stack
        popBinding();
        List newNodeset = new ArrayList();
        newNodeset.add(getCurrentNodeset().get(index - 1));
        contextStack.push(new Context(getCurrentContext().model, newNodeset));

        if (repeatId != null)
            repeatIdToIndex.put(repeatId, new Integer(index));
    }

    public void endRepeatId(String repeatId) {
        if (repeatId != null)
            repeatIdToIndex.remove(repeatId);
        popBinding();
    }

    public int getRepeatIdIndex(String repeatId, LocationData locationData) {
        Object index = repeatIdToIndex.get(repeatId);
        if (index == null)
            throw new ValidationException("Function index uses repeat id '" + repeatId
                    + "' which it not in scope", locationData);
        return ((Integer) index).intValue();
    }

    public Map getRepeatIdToIndex() {
        return repeatIdToIndex;
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

    public XFormsModel getCurrentModel() {
        return getCurrentContext().model;
    }

    public XFormsInstance getCurrentInstance() {
        return getCurrentContext().model.getInstance();
    }

    protected static class Context {
        public List nodeset;
        public XFormsModel model;

        public Context(XFormsModel model, List nodeSet) {
            this.model = model;
            this.nodeset = nodeSet;
        }
    }

    public void dispatchEvent(PipelineContext pipelineContext, String eventName, Element actionElement) {
        if (XFormsEvents.XFORMS_DOM_FOCUS_OUT.equals(eventName)) {
            // 4.4.9 The DOMFocusOut Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

        } else if (XFormsEvents.XFORMS_VALUE_CHANGED.equals(eventName)) {
            // 4.4.2 The xforms-value-changed Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

        } else {
            throw new OXFException("Invalid action requested: " + eventName);
        }
    }
}
