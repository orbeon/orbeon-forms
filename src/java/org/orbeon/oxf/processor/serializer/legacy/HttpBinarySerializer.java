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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.processor.serializer.HttpSerializerBase;
import org.orbeon.oxf.util.ContentHandlerOutputStream;

import java.io.OutputStream;

/**
 * Legacy HTTP binary serializer. This is deprecated by HttpSerializer.
 */
public abstract class HttpBinarySerializer extends HttpSerializerBase {

    protected final void readInput(PipelineContext pipelineContext, ExternalContext.Response response, ProcessorInput input, Object _config, OutputStream outputStream) {

        Config config = (Config) _config;

        // Set content type
        if (response != null) {
            String contentType = getContentType(config, null, getDefaultContentType());
            if (contentType != null)
                response.setContentType(contentType);
        }

        // Read input into an OutputStream
        readInput(pipelineContext, input, (Config) config, outputStream);
    }

    /**
     * This must be overridden by subclasses.
     */
    protected abstract void readInput(PipelineContext context, ProcessorInput input, Config config, OutputStream outputStream);

    /**
     * This method is use when the legacy serializer is used in the new converter mode. In this
     * case, the converter exposes a "data" output, and the processor's start() method is not
     * called.
     */
    @Override
    public ProcessorOutput createOutput(String name) {
        if (!name.equals(OUTPUT_DATA))
            throw new OXFException("Invalid output created: " + name);
        final ProcessorOutput output = new CacheableTransformerOutputImpl(HttpBinarySerializer.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                // Create OutputStream that converts to Base64
                ContentHandlerOutputStream outputStream = new ContentHandlerOutputStream(xmlReceiver, true);

                // Read configuration input
                Config config = readConfig(pipelineContext);
                String contentType = getContentType(config, null, getDefaultContentType());

                try {
                    // Start document
                    outputStream.setContentType(contentType);

                    // Write content
                    readInput(pipelineContext, getInputByName(INPUT_DATA), config, outputStream);

                    // End document and close
                    outputStream.close();

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
