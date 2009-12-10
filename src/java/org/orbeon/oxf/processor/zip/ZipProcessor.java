/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.processor.zip;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipProcessor extends ProcessorImpl {

    public ZipProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {

            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    // Create temporary zip file
                    final FileItem fileItem = NetUtils.prepareFileItem(context, NetUtils.REQUEST_SCOPE);
                    fileItem.getOutputStream().close();
                    final File temporaryZipFile = ((DiskFileItem) fileItem).getStoreLocation();
                    temporaryZipFile.createNewFile();
                    final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(temporaryZipFile));

                    try {
                        // Read list of files and write to zip output stream as we go
                        readInputAsSAX(context, INPUT_DATA, new ContentHandlerAdapter() {

                            String name;
                            StringBuffer uri;

                            // Get the file name, store it
                            public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
                                if ("file".equals(localName)) {
                                    name = atts.getValue("name");
                                    uri = new StringBuffer();
                                }
                            }

                            // Get the URI to the file, store it
                            public void characters(char ch[], int start, int length) throws SAXException {
                                if (uri != null)
                                    uri.append(ch, start, length);
                            }

                            // Process file
                            public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
                                try {
                                    if ("file".equals(localName)) {
                                        zipOutputStream.putNextEntry(new ZipEntry(name));
                                        InputStream fileInputStream = new FileInputStream(new File(new URI(uri.toString())));
                                        try {
                                            NetUtils.copyStream(fileInputStream, zipOutputStream);
                                        } finally {
                                            fileInputStream.close();
                                        }
                                    }
                                } catch (IOException e) {
                                    throw new OXFException(e);
                                } catch (URISyntaxException e) {
                                    throw new OXFException(e);
                                }
                            }
                        });
                    } finally {
                        zipOutputStream.close();
                    }

                    // Generate an Orbeon binary document with the content of the zip file
                    FileInputStream zipInputStream = new FileInputStream(temporaryZipFile);
                    try {
                        ProcessorUtils.readBinary(zipInputStream, contentHandler, "multipart/x-gzip", null, -1);
                    } finally {
                        zipInputStream.close();
                    }
                } catch (FileNotFoundException e) {
                    throw new OXFException(e);
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            // We could assume that we cache if the input document hasn't changed. But this would be unsafe as some of
            // the files referenced from the input document could have changed. So to be on the safe side, and also
            // because the cases where caching could happen are rather rare, we just don't cache.
        };
        addOutput(name, output);
        return output;
    }
}
