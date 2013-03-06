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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.xml.TransformerUtils;

import javax.xml.transform.stream.StreamResult;
import java.io.Writer;

public class XMLSerializer extends HttpTextSerializer {

    public static final String DEFAULT_CONTENT_TYPE = "application/xml";
    public static final String DEFAULT_METHOD = "xml";
    public static final String DEFAULT_VERSION = "1.0";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, Writer writer) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();

        if(config.publicDoctype != null && config.systemDoctype == null)
            throw new OXFException("XML Serializer must have a system doctype if a public doctype is present");

        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : DEFAULT_METHOD,
                config.version != null ? config.version : DEFAULT_VERSION,
                config.publicDoctype != null ? config.publicDoctype : null,
                config.systemDoctype != null ? config.systemDoctype : null,
                getEncoding(config, null, DEFAULT_ENCODING),
                config.omitXMLDeclaration,
                config.standalone,
                config.indent,
                config.indentAmount);

        identity.setResult(new StreamResult(writer));
        ProcessorImpl.readInputAsSAX(context, input, new SerializerXMLReceiver(identity, writer, isSerializeXML11()));
    }
}
