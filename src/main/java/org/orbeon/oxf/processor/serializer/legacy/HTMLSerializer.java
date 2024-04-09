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
package org.orbeon.oxf.processor.serializer.legacy;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLReceiver;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamResult;
import java.io.Writer;

public class HTMLSerializer extends HttpTextSerializer {

    public static final String DEFAULT_CONTENT_TYPE = "text/html";
    public static final String DEFAULT_METHOD = "html";

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, Writer writer) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        TransformerUtils.applyOutputProperties(identity.getTransformer(),
            config.methodOr(DEFAULT_METHOD),
            config.versionOr(null),
            config.publicDoctypeOrNull(),
            config.systemDoctypeOrNull(),
            config.encodingOrDefaultOrNull(DEFAULT_ENCODING),
            config.omitXMLDeclaration(),
            config.standaloneOrNull(),
            config.indent(),
            config.indentAmount()
        );

        identity.setResult(new StreamResult(writer));
        ProcessorImpl.readInputAsSAX(context, input, new StripNamespaceXMLReceiver(identity, writer, isSerializeXML11()));
    }

    protected static class StripNamespaceXMLReceiver extends SerializerXMLReceiver {
        public StripNamespaceXMLReceiver(XMLReceiver xmlReceiver, Writer writer, boolean serializeXML11) {
            super(xmlReceiver, writer, serializeXML11);
        }

        public void startPrefixMapping(String s, String s1) throws SAXException {
            // Do nothing
        }

        public void endPrefixMapping(String s) throws SAXException {
            // Do nothing
        }
    }

//    protected
    public String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }
}
