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
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.xpath.XPathException;
import org.dom4j.Node;

import java.util.Map;
import java.util.Iterator;
import java.util.List;

public class Itemset extends XFormsElement {

    private List nodelist;
    private String labelRef;
    private String copyRef;
    private Map labelPrefixToURI;
    private Map copyPrefixToURI;

    public void start(XFormsElementContext context, String uri, String localname,
                      String qname, Attributes attributes) throws SAXException {
        context.getContentHandler().startElement(uri, localname, qname, attributes);
        nodelist = context.getCurrentNodeset();
        context.startRepeatId(null);
        context.setRepeatIdIndex(null, 1);
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        context.endRepeatId(null);
        for (Iterator i = nodelist.iterator(); i.hasNext();) {
            Node node = (Node) i.next();
            context.getContentHandler().startElement(Constants.XFORMS_NAMESPACE_URI, "item",
                    Constants.XFORMS_PREFIX + ":item", XMLUtils.EMPTY_ATTRIBUTES);
            sendElement(context, node, "label", labelRef, labelPrefixToURI);
            sendElement(context, node, "value", copyRef, copyPrefixToURI);
            context.getContentHandler().endElement(Constants.XFORMS_NAMESPACE_URI, "item",
                    Constants.XFORMS_PREFIX + ":item");
        }
        context.getContentHandler().endElement(uri, localname, qname);
    }

    private void sendElement(XFormsElementContext context, Node node, String localname,
                             String ref, Map prefixToURI) throws SAXException {
        PooledXPathExpression expression = XPathCache.getXPathExpression(context.getPipelineContext(),
                context.getDocumentWrapper().wrap(node), "string(" + ref +  ")",
                prefixToURI, context.getRepeatIdToIndex());
        try {
            String value = (String) expression.evaluateSingle();
            context.getContentHandler().startElement(Constants.XFORMS_NAMESPACE_URI, localname,
                    Constants.XFORMS_PREFIX + ":" + localname, XMLUtils.EMPTY_ATTRIBUTES);
            context.getContentHandler().characters(value.toCharArray(), 0, value.length());
            context.getContentHandler().endElement(Constants.XFORMS_NAMESPACE_URI, localname,
                    Constants.XFORMS_PREFIX + ":" + localname);
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (expression != null) expression.returnToPool();
        }
    }

    public void setLabelRef(String labelRef, Map labelPrefixToURI) {
        this.labelRef = labelRef;
        this.labelPrefixToURI = labelPrefixToURI;
    }

    public void setCopyRef(String copyRef, Map copyPrefixToURI) {
        this.copyRef = copyRef;
        this.copyPrefixToURI = copyPrefixToURI;
    }
}
