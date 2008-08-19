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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.xml.TransformerUtils;

import java.io.OutputStream;

public abstract class CachedSerializer extends ProcessorImpl {

    public static final String SERIALIZER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/serializer";
    public static final String DEFAULT_ENCODING = TransformerUtils.DEFAULT_OUTPUT_ENCODING;
    public static final boolean DEFAULT_INDENT = true;
    public static final int DEFAULT_INDENT_AMOUNT = 1;// changed to 1 because Saxon fails with 0
    public static final int DEFAULT_ERROR_CODE = 0;
    public static final boolean DEFAULT_EMPTY = false;
    public static final boolean DEFAULT_CACHE_USE_LOCAL_CACHE = true;
    public static final boolean DEFAULT_OMIT_XML_DECLARATION = false;


    protected CachedSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    /**
     * This must be implemented by subclasses.
     *
     * @param context       pipeline context
     * @param response      response to use only to set some headers; should be used carefully
     * @param input         processor input being read
     * @param config        current serializer configuration
     * @param outputStream  OutputStream to write to; do not use the response parameter
     */
    protected abstract void readInput(PipelineContext context, ExternalContext.Response response, ProcessorInput input, Object config, OutputStream outputStream);
}
