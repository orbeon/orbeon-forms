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
package org.orbeon.oxf.processor.serializer;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.common.OXFException;

import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

/**
 * Legacy HTTP text serializer. This is deprecated by HttpSerializer.
 */
public abstract class HttpTextSerializer extends HttpSerializerBase {
    protected final void readInput(PipelineContext context, ExternalContext.Response response, ProcessorInput input, Object _config, OutputStream outputStream) {
        Config config = (Config) _config;

        String encoding = getEncoding(config, null, DEFAULT_ENCODING);
        if (response != null) {
            // Set content-type and encoding
            String contentType = getContentType(config, null, getDefaultContentType());
            response.setContentType(contentType + "; charset=" + encoding);
        }

        // Read input into a Writer
        try {
            Writer writer = new OutputStreamWriter(outputStream, encoding);
            readInput(context, input, config, writer);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    protected abstract void readInput(PipelineContext context, ProcessorInput input, Config config, Writer writer);
}