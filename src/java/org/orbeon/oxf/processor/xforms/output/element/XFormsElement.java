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

import org.dom4j.Node;
import org.jaxen.JaxenException;
import org.jaxen.NamespaceContext;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.expr.DefaultXPathFactory;
import org.jaxen.expr.FunctionCallExpr;
import org.jaxen.expr.LiteralExpr;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.JaxenXPathRewrite;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.xpath.XPathExpression;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * MISSING:
 * - Adding annotations: type, invalid-bind-ids, invalid-instance-ids
 */
public class XFormsElement {

    /**
     * Controls that contain the referenced value (which means that we won't
     * have to generate hidden fields for those).
     */
    static final Map DATA_CONTROLS = new HashMap();
    static final Map CONTROLS_ANNOTATIONS = new HashMap();
    static {
        String[] controlNames =
                {"input", "secret", "textarea", "upload", "filename", "mediatype", "size",
                 "range", "select", "select1", "output", "hidden"};
        for (int i = 0; i < controlNames.length; i++)
            DATA_CONTROLS.put(controlNames[i], null);
    }

    public boolean repeatChildren() { return false; }
    public boolean nextChildren(XFormsElementContext context) throws SAXException { return false; }

    public void start(XFormsElementContext context, String uri, String localname,
                      String qname, Attributes attributes) throws SAXException {
        try {
            final AttributesImpl newAttributes = new AttributesImpl(attributes);
            Map prefixToURI = new HashMap();
            {
                for (Enumeration e = context.getNamespaceSupport().getDeclaredPrefixes(); e.hasMoreElements();) {
                    String prefix = (String) e.nextElement();
                    prefixToURI.put(prefix, context.getNamespaceSupport().getURI(prefix));
                }
            }

            if(("if".equals(localname) || "when".equals(localname)) && Constants.XXFORMS_NAMESPACE_URI.equals(uri)) {
                String test = attributes.getValue("test");
                XPathExpression xpathExpression = XPathCache.createCacheXPath20(context.getPipelineContext(),
                        context.getDocumentWrapper(), context.getDocumentWrapper().wrap(context.getRefNode()),
                        "boolean(" + test + ")", prefixToURI, context.getRepeatIdToIndex(), null);
                Boolean value = (Boolean) xpathExpression.evaluateSingle();
                addExtensionAttribute(newAttributes, "value", Boolean.toString(value.booleanValue()));
            } else if (context.getParentElement(0) instanceof Itemset
                    && ("copy".equals(localname) || "label".equals(localname))) {
                // Pass information about the "ref" on the element to the parent "itemset"
                Itemset itemset = (Itemset) context.getParentElement(0);
                NamespaceContext namespaceContext = new SimpleNamespaceContext(prefixToURI);
                if ("copy".equals(localname)) {
                    itemset.setCopyRef(attributes.getValue("ref"), namespaceContext);
                } else {
                    itemset.setLabelRef(attributes.getValue("ref"), namespaceContext);
                }
            } else {
                // Add annotations about referenced element
                if (attributes.getIndex("", "ref") != -1
                        || attributes.getIndex(Constants.XXFORMS_NAMESPACE_URI, "position") != -1) {
                    InstanceData instanceData  = context.getRefInstanceData();
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_READONLY_ATTRIBUTE_NAME, Boolean.toString(instanceData.getReadonly().get()));
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_RELEVANT_ATTRIBUTE_NAME, Boolean.toString(instanceData.getRelevant().get()));
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_REQUIRED_ATTRIBUTE_NAME, Boolean.toString(instanceData.getRequired().get()));
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_VALID_ATTRIBUTE_NAME, Boolean.toString(instanceData.getValid().get()));
                    if (instanceData.getInvalidBindIds() != null)
                        addExtensionAttribute(newAttributes, Constants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_NAME, instanceData.getInvalidBindIds());
                    addExtensionAttribute(newAttributes, "ref-xpath", context.getRefXPath());
                    if (DATA_CONTROLS.containsKey(localname)) {
                        Node node = context.getRefNode();
                        addExtensionAttribute(newAttributes, "name", context.getRefName(node, true));
                        addExtensionAttribute(newAttributes, "value", context.getRefValue(node));
                    }
                }

                // Handle attributes used by XForms actions
                if (attributes.getIndex("", "ref") != -1) {
                    // Get id of referenced node
                    addExtensionAttribute(newAttributes, "ref-id",
                            Integer.toString(XFormsUtils.getInstanceData(context.getCurrentNode()).getId()));
                }
                if (attributes.getIndex("", "nodeset") != -1) {
                    // Get ids of node in "nodeset"
                    StringBuffer ids = new StringBuffer();
                    boolean first = true;
                    for (Iterator i = context.getCurrentNodeset().iterator(); i.hasNext();) {
                        Node node = (Node) i.next();
                        if (!first) ids.append(' '); else first = false;
                        ids.append(XFormsUtils.getInstanceData(node).getId());
                    }
                    addExtensionAttribute(newAttributes, "nodeset-ids", ids.toString());
                }
                if (attributes.getIndex("", "at") != -1) {
                    // Evaluate "at" as a number
                    NodeInfo contextNode = context.getDocumentWrapper().wrap((Node) context.getRefNodeList().get(0));
                    Object at = XPathCache.createCacheXPath20(context.getPipelineContext(),
                            context.getDocumentWrapper(), contextNode,
                            "round(" + attributes.getValue("at") + ")", context.getCurrentPrefixToURIMap(),
                            null, context.getFunctionLibrary()).evaluateSingle();
                    if (!(at instanceof Number))
                        throw new ValidationException("'at' expression must return a number",
                                new LocationData(context.getLocator()));
                    addExtensionAttribute(newAttributes, "at-value", at.toString());
                }
                if (attributes.getIndex("", "value") != -1) {
                    // Evaluate "value" as a string
                    String value = (String) XPathCache.createCacheXPath20(context.getPipelineContext(),
                            context.getDocumentWrapper(), context.getDocumentWrapper().wrap(context.getCurrentNode()),
                            "string(" + attributes.getValue("value") + ")",
                            context.getCurrentPrefixToURIMap(), null, null).evaluateSingle();
                    addExtensionAttribute(newAttributes, "value-value", value);
                }
            }
            context.getContentHandler().startElement(uri, localname, qname, newAttributes);
        } catch (XPathException e) {
            throw new OXFException(e);
        }
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        context.getContentHandler().endElement(uri, localname, qname);
    }

    private void addExtensionAttribute(AttributesImpl newAttributes, String name, String value) {
        newAttributes.addAttribute(Constants.XXFORMS_NAMESPACE_URI, name,
                Constants.XXFORMS_PREFIX + ":" + name, "CDATA", value);
    }
}
