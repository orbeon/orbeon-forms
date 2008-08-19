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
package org.orbeon.oxf.processor.serializer.legacy;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.util.Base64ContentHandler;

import java.io.OutputStream;

/**
 * Legacy HTTP binary serializer that serializes all character content to the output, assuming a
 * Base64 encoding. This is deprecated by HttpSerializer.
 */
public class BinarySerializer extends HttpBinarySerializer {

    public static String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, OutputStream outputStream) {
        try {
            readInputAsSAX(context, (input != null) ? input : getInputByName(INPUT_DATA), new Base64ContentHandler(outputStream));
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
