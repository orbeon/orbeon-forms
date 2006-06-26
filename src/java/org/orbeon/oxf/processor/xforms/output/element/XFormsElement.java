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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsElementContext;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

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

        if (("if".equals(localname) || "when".equals(localname)) && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            String test = attributes.getValue("test");
            final FunctionLibrary fncLib = context.getFunctionLibrary();
            final NodeInfo contextNode = context.getCurrentSingleNode();
            if (contextNode == null)
                throw new ValidationException("null context node for boolean 'test' expression: " + test, new LocationData(context.getLocator())); 
            final Boolean value = (Boolean) context.getCurrentInstance().getEvaluator().evaluateSingle(context.getPipelineContext(), contextNode,
                    "boolean(" + test + ")", prefixToURI, context.getRepeatIdToIndex(), fncLib, null);

            addExtensionAttribute(newAttributes, "value", Boolean.toString(value.booleanValue()));
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
            boolean positionPresent = attributes.getIndex(XFormsConstants.XXFORMS_NAMESPACE_URI, "position") != -1;

            if (refPresent || bindPresent || nodesetPresent || positionPresent) {

                final NodeInfo contextNodeInfo = context.getCurrentSingleNode();
//                if (contextNode == null)
//                    throw new ValidationException("null context node", new LocationData(context.getLocator()));

                {
                    final InstanceData currentNodeInstanceData = XFormsUtils.getInstanceDataUpdateInherited(contextNodeInfo);
                    if (currentNodeInstanceData != null) { // will be null for /
                        final String typeAsString = currentNodeInstanceData.getType().getAsString();
                        if (typeAsString != null)
                            addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME, typeAsString);
                        addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                Boolean.toString(currentNodeInstanceData.getInheritedReadonly().get()));
                        addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                Boolean.toString(currentNodeInstanceData.getInheritedRelevant().get()));
                        addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                Boolean.toString(currentNodeInstanceData.getRequired().get()));
                        addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                Boolean.toString(currentNodeInstanceData.getValid().get()));
                        if (currentNodeInstanceData.getInvalidBindIds() != null)
                            addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_NAME, currentNodeInstanceData.getInvalidBindIds());
                        if (DATA_CONTROLS.containsKey(localname)) {
                            // Must use local instance data to perform modifications
                            final InstanceData currentNodeLocalInstanceData = XFormsUtils.getLocalInstanceData(contextNodeInfo);
                            currentNodeLocalInstanceData.setGenerated(true);
                            String id = Integer.toString(currentNodeLocalInstanceData.getId());
                            if (XFormsUtils.isNameEncryptionEnabled())
                                id = SecureUtils.encrypt(context.getPipelineContext(), context.getEncryptionPassword(), id);

                            // Check if node value must be externalized
                            final boolean externalize = currentNodeInstanceData.getXXFormsExternalize().get();
                            final String namePrefix = externalize ? "$extnode^" : "$node^";
                            addExtensionAttribute(newAttributes, "name", namePrefix + id);

                            final String valueToSet;
                            if (externalize) {
                                // In this case, the value is empty or an URL that we must dereference
                                final String url = context.getRefValue();

                                if (!url.trim().equals("")){

                                    // There is non-blank content, decode
                                    try {
                                        final String fileName = new URL(url).getPath();
                                        final Reader reader = new InputStreamReader(new FileInputStream(new File(fileName)), "utf-8");
                                        final StringWriter writer = new StringWriter();
                                        try {
                                            NetUtils.copyStream(reader, writer);
                                        } finally {
                                            reader.close();
                                        }
                                        valueToSet = writer.toString();
                                    } catch (Exception e) {
                                        throw new OXFException(e);
                                    }
                                } else {
                                    // No non-blank content, keep value as is
                                    valueToSet = context.getRefValue();
                                }
                            } else {
                                // Use value as is
                                valueToSet = context.getRefValue();
                            }

                            addExtensionAttribute(newAttributes, "value", valueToSet);
                        } else if (ACTION_CONTROLS.containsKey(localname)) {
                            addExtensionAttribute(newAttributes, "value", context.getRefValue());
                        }
                    }
                }

                if (!positionPresent) {
                    // Get ids of node
                    StringBuffer ids = new StringBuffer();
                    boolean first = true;
                    final List currentNodeSet = context.getCurrentNodeset();
                    if (currentNodeSet != null) {
                        for (Iterator i = currentNodeSet.iterator(); i.hasNext();) {
                            final NodeInfo nodeInfo = (NodeInfo) i.next();
                            if (!first) ids.append(' '); else first = false;
                            final InstanceData currentNodeInstanceData = XFormsUtils.getLocalInstanceData(nodeInfo);
                            if (currentNodeInstanceData != null) {
                                String id = Integer.toString(currentNodeInstanceData.getId());
                                if (XFormsUtils.isNameEncryptionEnabled())
                                    id = SecureUtils.encrypt(context.getPipelineContext(), context.getEncryptionPassword(), id);
                                ids.append(id);
                            }
                        }
                    }
                    addExtensionAttribute(newAttributes, XFormsConstants.XXFORMS_NODE_IDS_ATTRIBUTE_NAME, ids.toString());
                }
            }

            if (attributes.getIndex("", "at") != -1) {
                // Evaluate "at" as a number

                final String atExpression = attributes.getValue("at");
                final NodeInfo contextNode = context.getCurrentSingleNode();
                if (contextNode == null)
                    throw new ValidationException("null context node for number 'at' expression: " + atExpression, new LocationData(context.getLocator()));

                final Object at = context.getCurrentInstance().getEvaluator().evaluateSingle(context.getPipelineContext(), context.getCurrentSingleNode(),
                        "round(" + atExpression + ")", context.getCurrentPrefixToURIMap(), null, context.getFunctionLibrary(), null);

                if (!(at instanceof Number))
                    throw new ValidationException("'at' expression must return a number",
                            new LocationData(context.getLocator()));
                String atString = at.toString();
                if (XFormsUtils.isNameEncryptionEnabled())
                    atString = SecureUtils.encrypt(context.getPipelineContext(),
                            context.getEncryptionPassword(), atString);
                addExtensionAttribute(newAttributes, "at-value", atString);
            }
            if (attributes.getIndex("", "value") != -1) {
                // Evaluate "value" as a string

                final String valueExpression = attributes.getValue("value");
                final NodeInfo contextNode = context.getCurrentSingleNode();
                if (contextNode == null)
                    throw new ValidationException("null context node for string 'value' expression: " + valueExpression, new LocationData(context.getLocator()));

                Object value = context.getCurrentInstance().getEvaluator().evaluateSingle(context.getPipelineContext(), context.getCurrentSingleNode(),
                        "string(" + valueExpression + ")", context.getCurrentPrefixToURIMap(), null, context.getFunctionLibrary(), null);

                if (!(value instanceof String))
                    throw new ValidationException("'value' expression must return a string",
                            new LocationData(context.getLocator()));

                addExtensionAttribute(newAttributes, "value-value", (String) value);
            }
            // Linking attribute: load content to xxforms:src-value
            if (attributes.getIndex("", "src") != -1 && LINKING_CONTROLS.containsKey(localname)) {
                try {
                    final String val;
                    String src = attributes.getValue("src");
                    if ("orbeon:xforms:schema:errors".equals(src)) {
                        final NodeInfo contextNode = context.getCurrentSingleNode();
//                        if (contextNode == null)
//                            throw new ValidationException("null context node", new LocationData(context.getLocator()));

                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(contextNode);
                        final Iterator iterator = instanceData.getSchemaErrorsMsgs();
                        val = StringUtils.join(iterator, "\n");
                    } else {
                        val = XFormsUtils.retrieveSrcValue(src);

                    }
                    addExtensionAttribute(newAttributes, "src-value", val);
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
        newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, name,
                XFormsConstants.XXFORMS_PREFIX + ":" + name, "CDATA", value);
    }
}
