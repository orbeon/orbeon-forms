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
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UnzipProcessor extends ProcessorImpl {

    public UnzipProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(UnzipProcessor.this, name) {

            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    // Read input in a temporary file
                    final File temporaryZipFile;
                    {
                        final FileItem fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE);
                        final OutputStream fileOutputStream = fileItem.getOutputStream();
                        readInputAsSAX(context, getInputByName(INPUT_DATA), new BinaryTextXMLReceiver(null, fileOutputStream, true, false, null, false, false, null, false));
                        temporaryZipFile = ((DiskFileItem) fileItem).getStoreLocation();
                    }

                    xmlReceiver.startDocument();
                    // <files>
                    xmlReceiver.startElement("", "files", "files", XMLUtils.EMPTY_ATTRIBUTES);
                    ZipFile zipFile = new ZipFile(temporaryZipFile);
                    for (Enumeration entries = zipFile.entries(); entries.hasMoreElements();) {
                        // Go through each entry in the zip file
                        ZipEntry zipEntry = (ZipEntry) entries.nextElement();
                        // Get file name
                        String fileName = zipEntry.getName();
                        long fileSize = zipEntry.getSize();
                        String fileTime = ISODateUtils.XS_DATE_TIME.format(new Date(zipEntry.getTime()));

                        InputStream entryInputStream = zipFile.getInputStream(zipEntry);
                        String uri = NetUtils.inputStreamToAnyURI(entryInputStream, NetUtils.REQUEST_SCOPE);
                        // <file name="filename.ext">uri</file>
                        AttributesImpl fileAttributes = new AttributesImpl();
                        fileAttributes.addAttribute("", "name", "name", "CDATA", fileName);
                        fileAttributes.addAttribute("", "size", "size", "CDATA", Long.toString(fileSize));
                        fileAttributes.addAttribute("", "dateTime", "dateTime", "CDATA", fileTime);
                        xmlReceiver.startElement("", "file", "file", fileAttributes);
                        xmlReceiver.characters(uri.toCharArray(), 0, uri.length());
                        // </file>
                        xmlReceiver.endElement("", "file", "file");
                    }
                    // </files>
                    xmlReceiver.endElement("", "files", "files");
                    xmlReceiver.endDocument();

                } catch (IOException e) {
                    throw new OXFException(e);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            // We don't do any caching here since the file we produce are temporary. So we don't want a processor
            // downstream to keep a reference to a document that contains temporary URI that have since been deleted.

        };
        addOutput(name, output);
        return output;
    }
}
