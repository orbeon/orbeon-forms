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
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.xml.TransformerUtils;

import javax.xml.transform.stream.StreamResult;
import java.io.Writer;

public class TextSerializer extends HttpTextSerializer {

    public static String DEFAULT_CONTENT_TYPE = "text/plain";
    public static String DEFAULT_METHOD = "text";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, Writer writer) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : DEFAULT_METHOD,
                null,
                null,
                null,
                getEncoding(config, null, DEFAULT_ENCODING),
                true,
                null,
                false,
                DEFAULT_INDENT_AMOUNT);
        identity.setResult(new StreamResult(writer));
        readInputAsSAX(context, INPUT_DATA, new SerializerXMLReceiver(identity, writer, isSerializeXML11()));
    }
}
