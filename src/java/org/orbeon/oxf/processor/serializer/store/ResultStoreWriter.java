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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

/**
 * Result store for character data (e.g. HTML pages)
 */
public class ResultStoreWriter extends Writer implements ResultStore {

    private Writer out;
    private StringBuffer sb = new StringBuffer();

    public ResultStoreWriter(Writer out) {
        this.out = out;
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
        sb.append(cbuf, off, len);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        if (out != null)
            out.close();
        out = null;
    }

    public int length(PipelineContext context) {
        try {
            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            return sb.toString().getBytes(externalContext.getResponse().getCharacterEncoding()).length;
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    public void replay(PipelineContext context) {
        try {
            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            replay(externalContext.getResponse().getWriter());
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public void replay(Writer writer) {
        try {
            writer.write(sb.toString());
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public void replay(OutputStream os) {
        throw new UnsupportedOperationException();
    }
}
