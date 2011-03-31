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
package org.orbeon.oxf.processor.zip;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipProcessor extends ProcessorImpl {

    public ZipProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(ZipProcessor.this, name) {

            String fileName = null;
            int statusCode = -1;

            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    // Create temporary zip file
                    final FileItem fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE);
                    fileItem.getOutputStream().close();
                    final File temporaryZipFile = ((DiskFileItem) fileItem).getStoreLocation();
                    temporaryZipFile.createNewFile();
                    final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(temporaryZipFile));

                    try {
                        // Read list of files and write to zip output stream as we go
                        readInputAsSAX(context, INPUT_DATA, new XMLReceiverAdapter() {

                            String name;
                            StringBuffer uri;

                            // Get the file name, store it
                            @Override
                            public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
                                if ("file".equals(localName)) {
                                    name = atts.getValue("name");
                                    uri = new StringBuffer();
                                } else if ("files".equals(localName)) {
                                    fileName = atts.getValue("filename");
                                    String value = atts.getValue("status-code");
                                    if (value != null ) {
                                        statusCode = Integer.parseInt(value);
                                    }
                                }
                            }

                            // Get the URI to the file, store it
                            @Override
                            public void characters(char ch[], int start, int length) throws SAXException {
                                if (uri != null)
                                    uri.append(ch, start, length);
                            }

                            // Process file
                            @Override
                            public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
                                try {
                                    if ("file".equals(localName)) {
                                        zipOutputStream.putNextEntry(new ZipEntry(name));
                                        final LocationData locationData = getLocationData();
                                        final URL fullURL;
                                        final String realPath;
                                        try {
                                            fullURL = (locationData != null && locationData.getSystemID() != null)
                                                                            ? URLFactory.createURL(locationData.getSystemID(), uri.toString())
                                                                            : URLFactory.createURL(uri.toString());

                                            if (fullURL.getProtocol().equals("oxf")) {
                                                // Get real path to resource path if possible
                                                realPath = ResourceManagerWrapper.instance().getRealPath(fullURL.getFile());
                                                if (realPath == null)
                                                    throw new OXFException("Zip processor is unable to obtain the real path of the file using the oxf: protocol for the base-directory property: " + uri.toString());
                                            } else if (fullURL.getProtocol().equals("file")) {
                                                String host = fullURL.getHost();
                                                realPath = host + (host.length() > 0 ? ":" : "") + fullURL.getFile();
                                            } else {
                                                throw new OXFException("Zip processor only supports the file: and oxf: protocols for the base-directory property: " + uri.toString());
                                            }

                                        } catch (MalformedURLException e) {
                                            throw new OXFException(e);
                                        }

                                        InputStream fileInputStream = new FileInputStream(new File(realPath));
                                        try {
                                            NetUtils.copyStream(fileInputStream, zipOutputStream);
                                        } finally {
                                            fileInputStream.close();
                                        }
                                    }
                                } catch (IOException e) {
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
                        ProcessorUtils.readBinary(zipInputStream, xmlReceiver, "multipart/x-gzip", null, statusCode, fileName);
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
