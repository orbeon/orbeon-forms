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
package org.orbeon.oxf.servlet;

import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.SystemUtils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class handles reading a request input stream, and making sure it can be replayed later. This
 * is most useful to allow file uploads to be handled by portlets.
 *
 * NOTE: The current implementation just clones the InputStream so that it can be replayed. Since
 * uploaded files are saved anyway, this causes potentially a double amount of data to be saved on
 * disk. It would be more optimal to be able to reconstitute a stream from the saved files and
 * request parameters.
 */
public class ServletInputStreamRepeater {

    private String contentType;
    private int contentLength;
    private String characterEncoding;
    private InputStream inputStream;

    private int maxMemorySize = 0;

    private SavingInputStream savingInputStream;
    private FileItem fileItem;
    private OutputStream outputStream;
    private int byteCount = 0;

    public ServletInputStreamRepeater(ExternalContext.Request request) throws IOException {
        this.contentType = request.getContentType();
        this.contentLength = request.getContentLength();
        this.characterEncoding = request.getCharacterEncoding();
        this.inputStream = request.getInputStream();
    }

    public void setMaxMemorySize(int maxMemorySize) {
        this.maxMemorySize = maxMemorySize;
    }

    public String getContentType() {
        return contentType;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    private class SavingInputStream extends FilterInputStream {

        public SavingInputStream(InputStream in) throws IOException {
            super(in);

            fileItem = new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory()).createItem("dummy", "dummy", false, null);
            outputStream = fileItem.getOutputStream();
        }

        public int read() throws IOException {
            int c = super.read();
            if (c != -1) {
                outputStream.write(c);
                byteCount++;
            }
            return c;
        }

        public int read(byte b[]) throws IOException {
            int count = super.read(b);
            if (count != -1) {
                outputStream.write(b, 0, count);
                byteCount += count;
            }
            return count;
        }

        public int read(byte b[], int off, int len) throws IOException {
            int count = super.read(b, off, len);
            if (count != -1) {
                outputStream.write(b, off, count);
                byteCount += count;
            }
            return count;
        }

    };

    public InputStream getInputStream() throws IOException {
        // Create interceptor if from within a servlet only and enabled
        if (savingInputStream == null)
            savingInputStream = new SavingInputStream(inputStream);

        return savingInputStream;
    }

    public void finishReading() {
        try {
            if (savingInputStream != null) {
                // Just read until the end of the stream
                byte[] buffer = new byte[1024];
                while (savingInputStream.read(buffer) != -1) {
                    // Do nothing
                }
                outputStream.close();
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public int getActualByteCount() {
        return byteCount;
    }

    public InputStream getSavedInputStream() throws IOException {
        return (fileItem != null) ? fileItem.getInputStream() : null;
    }

    public void delete() {
        if (fileItem != null)
            fileItem.delete();
    }
}
