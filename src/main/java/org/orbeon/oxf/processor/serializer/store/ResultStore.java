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
package org.orbeon.oxf.processor.serializer.store;

import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.io.OutputStream;
import java.io.Writer;

/**
 * Stores the final result of a request. Instances of classes implementing this
 * interface are stored in cache, and if still in cache in a subsequent request,
 * they can be "replayed" without having to execute the whole pipeline.
 */
public interface ResultStore {

    /**
     * Length of the request, in bytes
     */
    public int length(PipelineContext context);

    /**
     * Replay to the response stored in the context, getting the output stream
     * or writer, as required
     */
    public void replay(PipelineContext context);

    /**
     * Replay to a write (this method might not be supported)
     */
    public void replay(Writer writer);

    /**
     * Replay to an output stream  (this method might not be supported)
     */
    public void replay(OutputStream outputStream);
}
