/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.processor.xforms.output.element;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.jaxen.NamespaceContext;

import java.util.Map;
import java.util.Iterator;
import java.util.List;

public class Itemset extends XFormsElement {

    private List nodelist;
    private String labelRef;
    private String copyRef;
    private NamespaceContext labelNamespaceContext;
    private NamespaceContext copyNamespaceContext;

    public void start(XFormsElementContext context, String uri, String localname,
                      String qname, Attributes attributes) throws SAXException {
        context.getContentHandler().startElement(uri, localname, qname, attributes);
        nodelist = context.getRefNodeList();
        context.addRepeatId(null);
        context.setRepeatIdIndex(null, null, 1);
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        context.removeRepeatId(null);
        for (Iterator i = nodelist.iterator(); i.hasNext();) {
            Node node = (Node) i.next();
            context.getContentHandler().startElement(Constants.XFORMS_NAMESPACE_URI, "item",
                    Constants.XFORMS_PREFIX + ":item", XMLUtils.EMPTY_ATTRIBUTES);
            sendElement(context, node, "label", labelRef, labelNamespaceContext);
            sendElement(context, node, "value", copyRef, copyNamespaceContext);
            context.getContentHandler().endElement(Constants.XFORMS_NAMESPACE_URI, "item",
                    Constants.XFORMS_PREFIX + ":item");
        }
        context.getContentHandler().endElement(uri, localname, qname);
    }

    private void sendElement(XFormsElementContext context, Node node, String localname,
                             String ref, NamespaceContext namespaceContext) throws SAXException {
        XPath labelXPath = node.createXPath("string(" + ref + ")");
        labelXPath.setNamespaceContext(namespaceContext);
        String value = (String) labelXPath.evaluate(node);
        context.getContentHandler().startElement(Constants.XFORMS_NAMESPACE_URI, localname,
                Constants.XFORMS_PREFIX + ":" + localname, XMLUtils.EMPTY_ATTRIBUTES);
        context.getContentHandler().characters(value.toCharArray(), 0, value.length());
        context.getContentHandler().endElement(Constants.XFORMS_NAMESPACE_URI, localname,
                Constants.XFORMS_PREFIX + ":" + localname);
    }

    public void setLabelRef(String labelRef, NamespaceContext labelNamespaceContext) {
        this.labelRef = labelRef;
        this.labelNamespaceContext = labelNamespaceContext;
    }

    public void setCopyRef(String copyRef, NamespaceContext copyNamespaceContext) {
        this.copyRef = copyRef;
        this.copyNamespaceContext = copyNamespaceContext;
    }
}
