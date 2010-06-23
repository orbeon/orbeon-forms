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
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.xml.SimpleForwardingXMLReceiver;
import org.orbeon.oxf.xml.TransformerUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamResult;
import java.io.Writer;

/**
 * Converts XML into text according to the XSLT Text output method.
 *
 * See http://www.w3.org/TR/xslt#section-Text-Output-Method
 */
public class TextConverter extends TextConverterBase {

    public static String DEFAULT_CONTENT_TYPE = "text/plain";
    public static String DEFAULT_METHOD = "text";

    public TextConverter() {
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected boolean readInput(PipelineContext context, final ContentHandler contentHandler, ProcessorInput input, Config config, Writer writer) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : DEFAULT_METHOD,
                null,
                null,
                null,
                getEncoding(config, DEFAULT_ENCODING),
                true,
                null,
                false,
                DEFAULT_INDENT_AMOUNT);
        identity.setResult(new StreamResult(writer));
        final boolean[] didEndDocument = new boolean[1];
        readInputAsSAX(context, INPUT_DATA,  new SimpleForwardingXMLReceiver(identity) {
            public void endDocument() throws SAXException {
                super.endDocument();
                sendEndDocument(contentHandler);
                didEndDocument[0] = true;
            }
        });

        return didEndDocument[0];
    }
}
