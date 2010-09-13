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
package org.orbeon.oxf.processor.serializer.store;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.io.*;

/**
 * Result store for binary data (e.g. PDF document)
 */
public class ResultStoreOutputStream extends ByteArrayOutputStream implements ResultStore {

    OutputStream out;

    public ResultStoreOutputStream(OutputStream out) {
        this.out = out;
    }

    public synchronized void write(int b) {
        try {
            out.write(b);
            super.write(b);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public synchronized void write(byte b[], int off, int len) {
        try {
            out.write(b, off, len);
            super.write(b, off, len);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public void close() throws IOException {
        if (out != null)
            out.close();
        out = null;
    }

    public void replay(PipelineContext context) {
        try {
            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            replay(externalContext.getResponse().getOutputStream());
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public void replay(Writer writer) {
        throw new UnsupportedOperationException();
    }

    public void replay(OutputStream os) {
        try {
            writeTo(os);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public int length(PipelineContext context) {
        return size();
    }
}
