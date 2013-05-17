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
package org.orbeon.oxf.test.old;

import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

public class XalanHTMLSerializerTest {

    private static final String XALAN_INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";
    private static final String XALAN_CONTENT_HANDLER = "{http://xml.apache.org/xslt}content-handler";

    private static void applyHTMLOutputProperties(Transformer transformer) {
        transformer.setOutputProperty(OutputKeys.METHOD, "html");
        transformer.setOutputProperty(OutputKeys.VERSION, "4.0");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//W3C//DTD HTML 4.0 Transitional//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://www.w3.org/TR/html4/loose.dtd");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "iso-8859-1");
        transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/html");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(XALAN_INDENT_AMOUNT, "0");
        transformer.setOutputProperty(XALAN_CONTENT_HANDLER, "orbeon.apache.xml.serializer.ToHTMLStream");
    }

    public static void main(String[] args) throws Exception {

        TransformerHandler identity = new org.apache.xalan.processor.TransformerFactoryImpl().newTransformerHandler();
        applyHTMLOutputProperties(identity.getTransformer());
        identity.setResult(new StreamResult(System.out));

        identity.startDocument();
        identity.startElement("", "html", "html", new AttributesImpl());
        identity.startElement("", "body", "body", new AttributesImpl());

        identity.startPrefixMapping("e", "urn:example");
        identity.startElement("", "p", "p", new AttributesImpl());
        identity.endElement("", "p", "p");
        identity.endPrefixMapping("e");

        identity.endElement("", "body", "body");
        identity.endElement("", "html", "html");
        identity.endDocument();
    }

}
