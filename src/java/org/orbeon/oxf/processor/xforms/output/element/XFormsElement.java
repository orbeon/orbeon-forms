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

import org.jaxen.JaxenException;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.NamespaceContext;
import org.jaxen.expr.DefaultXPathFactory;
import org.jaxen.expr.FunctionCallExpr;
import org.jaxen.expr.LiteralExpr;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.xml.JaxenXPathRewrite;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.xpath.XPathExpression;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;

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
                 "range", "select", "select1", "output"};
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
                        "boolean(" + test + ")", prefixToURI, context.getRepeatIdToIndex());
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
                        addExtensionAttribute(newAttributes, "name", context.getRefName(true));
                        addExtensionAttribute(newAttributes, "value", context.getRefValue());
                    }
                }

                // Rewrite XPath attributes into full XPath expressions
                if (attributes.getIndex("", "ref") != -1)
                    addExtensionAttribute(newAttributes, "ref-xpath", rewriteXPath(context, attributes.getValue("ref")));
                if (attributes.getIndex("", "nodeset") != -1)
                    addExtensionAttribute(newAttributes, "nodeset-xpath", rewriteXPath(context, attributes.getValue("nodeset")));
                if (attributes.getIndex("", "at") != -1)
                    addExtensionAttribute(newAttributes, "at-xpath", rewriteXPath(context, attributes.getValue("at")));
                if (attributes.getIndex("", "value") != -1)
                    addExtensionAttribute(newAttributes, "value-xpath", rewriteXPath(context, attributes.getValue("value")));
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

    /**
     * Rewrites the XPath expression, replacing index('set') by the current
     * index in the given set.
     */
    private String rewriteXPath(final XFormsElementContext context, final String xpath) {
        final LocationData locationData = new LocationData(context.getLocator());
        String result = JaxenXPathRewrite.rewrite(xpath, null, null, "index", 1, locationData, new JaxenXPathRewrite.Rewriter() {
            public void rewrite(FunctionCallExpr expr) {
                try {
                    if (! (expr.getParameters().get(0) instanceof LiteralExpr))
                        throw new ValidationException("Literal expression expected as argument to index function", locationData);
                    String repeatId = ((LiteralExpr) expr.getParameters().get(0)).getLiteral();
                    int index = context.getRepeatIdIndex(repeatId, locationData);
                    expr.setFunctionName("identity");
                    expr.getParameters().set(0, new DefaultXPathFactory().createNumberExpr(index));
                } catch (JaxenException e) {
                    throw new OXFException(e.getMessage() + " while parsing XPath expression '" + xpath + "'");
                }
            }
        });
        return XPathUtils.putNamespacesInPath(context.getNamespaceSupport(), result);
    }
}
