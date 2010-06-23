/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.converter;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.xml.SimpleForwardingXMLReceiver;
import org.orbeon.oxf.xml.TransformerUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamResult;
import java.io.Writer;

/**
 * Converts XML into text according to the XSLT HTML output method.
 *
 * http://www.w3.org/TR/xslt#section-HTML-Output-Method
 */
public class HTMLConverter extends TextConverterBase {

    public static final String DEFAULT_CONTENT_TYPE = "text/html";
    public static final String DEFAULT_METHOD = "html";

    public static final String DEFAULT_PUBLIC_DOCTYPE = "-//W3C//DTD HTML 4.01 Transitional//EN";
    public static final String DEFAULT_SYSTEM_DOCTYPE = null;
    public static final String DEFAULT_VERSION = "4.01";

    public HTMLConverter() {
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected boolean readInput(PipelineContext context, final ContentHandler contentHandler, ProcessorInput input, Config config, Writer writer) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : DEFAULT_METHOD,
                config.version != null ? config.version : null,
                config.publicDoctype != null ? config.publicDoctype : null,
                config.systemDoctype != null ? config.systemDoctype : null,
                getEncoding(config, DEFAULT_ENCODING),
                config.omitXMLDeclaration,
                config.standalone,
                config.indent,
                config.indentAmount);

        identity.setResult(new StreamResult(writer));
        final boolean[] didEndDocument = new boolean[1];
        readInputAsSAX(context, input,  new StripNamespaceXMLReceiver(identity) {
            public void endDocument() throws SAXException {
                super.endDocument();
                sendEndDocument(contentHandler);
                didEndDocument[0] = true;
            }
        });

        return didEndDocument[0];
    }

    /**
     * This makes the HTML output clear of namespace declarations. It is not clear whether this is meant to be done this
     * way.
     */
    protected static class StripNamespaceXMLReceiver extends SimpleForwardingXMLReceiver {
        public StripNamespaceXMLReceiver(XMLReceiver xmlReceiver) {
            super(xmlReceiver);
        }

        public void startPrefixMapping(String s, String s1) throws SAXException {
            // Do nothing
        }

        public void endPrefixMapping(String s) throws SAXException {
            // Do nothing
        }
    }
}
