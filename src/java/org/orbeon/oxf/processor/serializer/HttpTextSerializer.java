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
import org.orbeon.oxf.processor.ProcessorInput;

import java.io.OutputStream;
import java.io.Writer;

public abstract class HttpTextSerializer extends HttpSerializer {
    protected final void readInput(PipelineContext context, ProcessorInput input, Object _config, OutputStream outputStream) {
        Config config = (Config) _config;
        readInput(context, input, config, getWriter(outputStream, config));
    }

    protected abstract void readInput(PipelineContext context, ProcessorInput input, Config config, Writer writer);
}