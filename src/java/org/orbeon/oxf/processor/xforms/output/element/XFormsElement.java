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

import org.apache.commons.lang.StringUtils;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.style.StandardNames;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * MISSING:
 * - Adding annotations: type, invalid-bind-ids, invalid-instance-ids
 */
public class XFormsElement {

    private static final int BUFFER_SIZE = 1024;

    /**
     * Controls that contain the referenced value (which means that we won't
     * have to generate hidden fields for those).
     */
    static final Map DATA_CONTROLS = new HashMap();
    static final Map LINKING_CONTROLS = new HashMap();
    static final Map ACTION_CONTROLS = new HashMap();

    static final Map CONTROLS_ANNOTATIONS = new HashMap();

    static {
        String[] controlNames =
                {"input", "secret", "textarea", "upload", "filename", "mediatype", "size",
                 "range", "select", "select1", "output", "hidden"};
        for (int i = 0; i < controlNames.length; i++)
            DATA_CONTROLS.put(controlNames[i], null);

        String[] linkingNames =
                {"message", "label", "help", "hint", "alert"};
        for (int i = 0; i < linkingNames.length; i++)
            LINKING_CONTROLS.put(linkingNames[i], null);

        String[] actionNames =
                {"message", "label", "help", "hint", "alert"};
        for (int i = 0; i < actionNames.length; i++)
            ACTION_CONTROLS.put(actionNames[i], null);
    }

    public boolean repeatChildren() {
        return false;
    }

    public boolean nextChildren(XFormsElementContext context) throws SAXException {
        return false;
    }

    public void start(XFormsElementContext context, String uri, String localname,
                      String qname, Attributes attributes) throws SAXException {
        final AttributesImpl newAttributes = new AttributesImpl(attributes);
        Map prefixToURI = context.getCurrentPrefixToURIMap();

        if (("if".equals(localname) || "when".equals(localname)) && Constants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            String test = attributes.getValue("test");
            PooledXPathExpression expr = XPathCache.getXPathExpression(context.getPipelineContext(),
                    context.getDocumentWrapper().wrap(context.getCurrentSingleNode()),
                    "boolean(" + test + ")", prefixToURI, context.getRepeatIdToIndex());
            try {
                Boolean value = (Boolean) expr.evaluateSingle();
                addExtensionAttribute(newAttributes, "value", Boolean.toString(value.booleanValue()));
            } catch (XPathException e) {
                throw new OXFException(e);
            } finally {
                if (expr != null)
                    expr.returnToPool();
            }
        } else if (context.getParentElement(0) instanceof Itemset
                && ("copy".equals(localname) || "label".equals(localname))) {
            // Pass information about the "ref" on the element to the parent "itemset"
            Itemset itemset = (Itemset) context.getParentElement(0);
            if ("copy".equals(localname)) {
                itemset.setCopyRef(attributes.getValue("ref"), prefixToURI);
            } else {
                itemset.setLabelRef(attributes.getValue("ref"), prefixToURI);
            }
        } else {
            // Add annotations about referenced element
            boolean bindPresent = attributes.getIndex("", "bind") != -1;
            boolean refPresent = attributes.getIndex("", "ref") != -1;
            boolean nodesetPresent = attributes.getIndex("", "nodeset") != -1;
            boolean positionPresent = attributes.getIndex(Constants.XXFORMS_NAMESPACE_URI, "position") != -1;
            if (refPresent || bindPresent || nodesetPresent || positionPresent) {
                InstanceData currentNodeInstanceData = XFormsUtils.getInstanceData(context.getCurrentSingleNode());

                int typeCode = currentNodeInstanceData.getType().get();
                if(typeCode != 0)
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_TYPE_ATTRIBUTE_NAME,
                            StandardNames.getPrefix(typeCode) + ":" + StandardNames.getLocalName(typeCode));
                addExtensionAttribute(newAttributes, Constants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                        Boolean.toString(currentNodeInstanceData.getReadonly().get()));
                addExtensionAttribute(newAttributes, Constants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                        Boolean.toString(currentNodeInstanceData.getRelevant().get()));
                addExtensionAttribute(newAttributes, Constants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                        Boolean.toString(currentNodeInstanceData.getRequired().get()));
                addExtensionAttribute(newAttributes, Constants.XXFORMS_VALID_ATTRIBUTE_NAME,
                        Boolean.toString(currentNodeInstanceData.getValid().get()));
                if (currentNodeInstanceData.getInvalidBindIds() != null)
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_NAME, currentNodeInstanceData.getInvalidBindIds());
                if (DATA_CONTROLS.containsKey(localname)) {
                    currentNodeInstanceData.setGenerated(true);
                    String id = Integer.toString(currentNodeInstanceData.getId());
                    if (XFormsUtils.isNameEncryptionEnabled())
                        id = SecureUtils.encrypt(context.getPipelineContext(), context.getEncryptionPassword(), id);
                    addExtensionAttribute(newAttributes, "name", "$node^" + id);
                    addExtensionAttribute(newAttributes, "value", context.getRefValue());
                } else  if (ACTION_CONTROLS.containsKey(localname)) {
                    addExtensionAttribute(newAttributes, "value", context.getRefValue());
                }

                if(!positionPresent) {
                    // Get ids of node
                    StringBuffer ids = new StringBuffer();
                    boolean first = true;
                    for (Iterator i = context.getCurrentNodeset().iterator(); i.hasNext();) {
                        Node node = (Node) i.next();
                        if (!first) ids.append(' '); else first = false;
                        String id = Integer.toString(XFormsUtils.getInstanceData(node).getId());
                        if (XFormsUtils.isNameEncryptionEnabled())
                            id = SecureUtils.encrypt(context.getPipelineContext(), context.getEncryptionPassword(), id);
                        ids.append(id);
                    }
                    addExtensionAttribute(newAttributes, Constants.XXFORMS_NODE_IDS_ATTRIBUTE_NAME, ids.toString());

                }
            }

            if (attributes.getIndex("", "at") != -1) {
                // Evaluate "at" as a number
                NodeInfo contextNode = context.getDocumentWrapper().wrap(context.getCurrentSingleNode());
                PooledXPathExpression expr = XPathCache.getXPathExpression(context.getPipelineContext(),
                        contextNode,
                        "round(" + attributes.getValue("at") + ")",
                        context.getCurrentPrefixToURIMap(),
                        null,
                        context.getFunctionLibrary());
                try {
                    Object at = expr.evaluateSingle();
                    if (!(at instanceof Number))
                        throw new ValidationException("'at' expression must return a number",
                                new LocationData(context.getLocator()));
                    String atString = at.toString();
                    if (XFormsUtils.isNameEncryptionEnabled())
                        atString = SecureUtils.encrypt(context.getPipelineContext(),
                                context.getEncryptionPassword(), atString);
                    addExtensionAttribute(newAttributes, "at-value", atString);
                } catch (XPathException e) {
                    throw new OXFException(e);
                } finally {
                    if (expr != null)
                        expr.returnToPool();
                }
            }
            if (attributes.getIndex("", "value") != -1) {
                // Evaluate "value" as a string
                PooledXPathExpression expr = XPathCache.getXPathExpression(context.getPipelineContext(),
                        context.getDocumentWrapper().wrap(context.getCurrentSingleNode()),
                        "string(" + attributes.getValue("value") + ")",
                        context.getCurrentPrefixToURIMap(),
                        null,
                        context.getFunctionLibrary());
                try {
                    Object value = expr.evaluateSingle();
                    if (!(value instanceof String))
                        throw new ValidationException("'value' expression must return a string",
                                new LocationData(context.getLocator()));

                    addExtensionAttribute(newAttributes, "value-value", (String) value);
                } catch (XPathException e) {
                    throw new OXFException(e);
                } finally {
                    if(expr != null)
                        expr.returnToPool();
                }
            }
            // Linking attribute: load content to xxforms:src-value
            if(attributes.getIndex("", "src") != -1 && LINKING_CONTROLS.containsKey(localname)) {
                try {
                    final String val;
                    String src = attributes.getValue("src");
                    if ( "orbeon:xforms:schema:errors".equals( src ) ) {
                        final org.dom4j.Node nd = context.getCurrentSingleNode();
                        final InstanceData instDat = XFormsUtils.getInstanceData( nd );
                        final java.util.Iterator itr = instDat.getSchemaErrorsMsgs();
                        val = StringUtils.join( itr, "\n" );
                    } else {
                        URL url = URLFactory.createURL(src);

                        // Load file into buffer
                        InputStreamReader stream = new InputStreamReader(url.openStream());
                        StringBuffer value = new StringBuffer();
                        char[] buff = new char[BUFFER_SIZE];
                        int c = 0;
                        while( (c = stream.read(buff, 0, BUFFER_SIZE-1)) != -1)
                            value.append(buff, 0, c);
                        val = value.toString();
                    }
                    addExtensionAttribute(newAttributes, "src-value", val );
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                } catch (IOException ioe) {
                    throw new OXFException(ioe);
                }
            }
        }
        context.getContentHandler().startElement(uri, localname, qname, newAttributes);
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        context.getContentHandler().endElement(uri, localname, qname);
    }

    private void addExtensionAttribute(AttributesImpl newAttributes, String name, String value) {
        newAttributes.addAttribute(Constants.XXFORMS_NAMESPACE_URI, name,
                Constants.XXFORMS_PREFIX + ":" + name, "CDATA", value);
    }
}
