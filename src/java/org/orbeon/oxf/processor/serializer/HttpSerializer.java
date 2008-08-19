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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;

import java.io.OutputStream;

/**
 * This serializer is a generic HTTP serializer able to serialize text as well as binary.
 */
public class HttpSerializer extends HttpSerializerBase {

    public static final String HTTP_SERIALIZER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/http-serializer";

    protected String getDefaultContentType() {
        return BinaryTextContentHandler.DEFAULT_BINARY_CONTENT_TYPE;
    }

    protected String getConfigSchemaNamespaceURI() {
        return HTTP_SERIALIZER_CONFIG_NAMESPACE_URI;
    }

    protected void readInput(PipelineContext context, ExternalContext.Response response, ProcessorInput input, Object config, final OutputStream outputStream) {
        try {
            final Config httpConfig = (Config) config;

            readInputAsSAX(context, input, new BinaryTextContentHandler(response, outputStream, true,
                    httpConfig.forceContentType, httpConfig.contentType, httpConfig.ignoreDocumentContentType,
                    httpConfig.forceEncoding, httpConfig.encoding, httpConfig.ignoreDocumentEncoding));

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
