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

import org.apache.commons.fileupload.DefaultFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.util.NetUtils;

import java.util.Map;
import java.util.Iterator;
import java.io.*;

/**
 * Build a "multipart/form-data" stream out of form parameters and uploaded files, while allowing
 * parameters to be filtered. This is useful for the portlet container, which must rewrite parameter
 * names submitted for upload.
 */
public class MultipartFormDataBuilder {

    private String contentType;

    private String boundary;
    private Map parameters;
    private ParameterNameFilter parameterNameFilter;

    private int maxMemorySize = 0;

    private FileItem fileItem;

    private static final String contentDispositionFormString1 = "Content-Disposition: form-data; name=\"";
    private static final String contentDispositionFormString2 = "\"";
    private static final String contentDispositionFileString1 = "Content-Disposition: form-data; name=\"";
    private static final String contentDispositionFileString2 = "\"; filename=\"";
    private static final String contentDispositionFileString3 = "\"";
    private static final String contentTypeString = "Content-Type: ";

    private static final String BOUNDARY_STRING = "boundary=";

    public MultipartFormDataBuilder(String contentType, Map parameters, ParameterNameFilter parameterNameFilter) {
        if (!contentType.startsWith("multipart/form-data"))
            throw new IllegalArgumentException("contentType must be multipart/form-data");

        this.contentType = contentType;
        this.boundary = contentType.substring(contentType.indexOf(BOUNDARY_STRING) + BOUNDARY_STRING.length()).trim();
        this.parameters = parameters;
        this.parameterNameFilter = parameterNameFilter;
    }

    public InputStream getInputStream() throws IOException {

        // Create the temporary file if needed
        if (fileItem == null)
            createTemporaryFile();

        // Return input stream on the temporary file
        return fileItem.getInputStream();
    }

    public long getContentLength() {
        long size = 0;
        for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            Object value = parameters.get(key);

            String newKey = (parameterNameFilter != null) ? parameterNameFilter.filterParameterName(key) : key;

            if (newKey != null) {
                if (value instanceof FileItem) {
                    // File item
                    FileItem f = (FileItem) value;
                    size += 2 + boundary.length() + 2; // starting boundary + CRLF
                    if (f.getName() != null) {
                        size += contentDispositionFileString1.length() + contentDispositionFileString2.length() + contentDispositionFileString3.length() + 2; // Content-Disposition + CRLF
                        size += f.getName().length(); // file name
                    } else {
                        size += contentDispositionFormString1.length() + contentDispositionFormString2.length() + 2; // Content-Disposition + CRLF
                    }
                    size += newKey.length(); // parameter name

                    size += contentTypeString.length() + 2; // Content-Type + CRLF
                    size += f.getContentType() != null ? f.getContentType().length() : "application/octet-stream".length(); // content type

                    size += 2; // blank line
                    size += f.getSize(); // file size
                    size += 2; // blank line
                } else {
                    // Other form parameter
                    String[] s = (String[]) value;
                    for (int j = 0; j < s.length; j++) {
                        size += 2 + boundary.length() + 2; // starting boundary + CRLF
                        size += contentDispositionFormString1.length() + contentDispositionFormString2.length() + 2; // Content-Disposition + CRLF
                        size += newKey.length(); // parameter name
                        size += 2; // blank line
                        size += s[j].length(); // value FIXME: FORM_ENCODING
                        size += 2; // blank line
                    }
                }
            }
        }
        size += 2 + boundary.length() + 2 + 2; // final boundary + CRLF

        return size;
    }

    /**
     * Save a temporary file with all the values according to RFC 2388.
     */
    private void createTemporaryFile() throws IOException {
        fileItem = new DefaultFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory()).createItem("dummy", "dummy", false, null);
        OutputStream outputStream = new BufferedOutputStream(fileItem.getOutputStream());

        // Write content
        {
            Writer writer = new OutputStreamWriter(outputStream, "iso-8859-1");

            for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
                String key = (String) i.next();
                Object value = parameters.get(key);

                String newKey = (parameterNameFilter != null) ? parameterNameFilter.filterParameterName(key) : key;

                if (newKey != null) {
                    if (value instanceof FileItem) {
                        // File item
                        FileItem f = (FileItem) value;

                        writer.write("--");
                        writer.write(boundary);
                        writer.write("\r\n");

                        if (f.getName() != null) {
                            writer.write(contentDispositionFileString1);
                            writer.write(newKey);
                            writer.write(contentDispositionFileString2);
                            writer.write(f.getName());
                            writer.write(contentDispositionFileString3);
                            writer.write("\r\n");
                        } else {
                            writer.write(contentDispositionFormString1);
                            writer.write(newKey); // we assume that the key is only ASCII; it should probably be encoded as per RFC 2047
                            writer.write(contentDispositionFormString2);
                            writer.write("\r\n");
                        }

                        writer.write(contentTypeString);
                        writer.write(f.getContentType() != null ? f.getContentType() : "application/octet-stream");
                        writer.write("\r\n");
                        writer.write("\r\n");

                        writer.flush();
                        NetUtils.copyStream(f.getInputStream(), outputStream);
                        outputStream.flush();

                        writer.write("\r\n");
                    } else {
                        // Other form parameter
                        String[] s = (String[]) value;
                        for (int j = 0; j < s.length; j++) {
                            writer.write("--");
                            writer.write(boundary);
                            writer.write("\r\n");
                            writer.write(contentDispositionFormString1);
                            writer.write(newKey); // we assume that the key is only ASCII; it should probably be encoded as per RFC 2047
                            writer.write(contentDispositionFormString2);
                            writer.write("\r\n");
                            writer.write("\r\n");
                            writer.write(s[j]); // FIXME: FORM_ENCODING we probably need to set an encoding here, otherwise parameter values in utf-8, for example, won't work
                            writer.write("\r\n");
                        }
                    }
                }
            }
            writer.write("--");
            writer.write(boundary);
            writer.write("--");
            writer.write("\r\n");
            writer.flush();

            outputStream.close();

            long contentLength = getContentLength();
            if (contentLength != fileItem.getSize())
                throw new IllegalStateException("Precomputed size differs from actual size: " + contentLength + " != " + fileItem.getSize());
        }
    }

    public void delete() {
        if (fileItem != null)
            fileItem.delete();
    }

    public String getContentType() {
        return contentType;
    }

    public interface ParameterNameFilter {
        /**
         * Filter the parameter name. Can return null if the parameter must be ignored.
         */
        public String filterParameterName(String name);
    }
}
