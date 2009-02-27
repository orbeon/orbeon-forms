package org.orbeon.oxf.processor.zip;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.serializer.BinaryTextContentHandler;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;
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

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {

            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    // Read input in a temporary file
                    final File temporaryZipFile;
                    {
                        final FileItem fileItem = NetUtils.prepareFileItem(context, NetUtils.REQUEST_SCOPE);
                        final OutputStream fileOutputStream = fileItem.getOutputStream();
                        readInputAsSAX(context, getInputByName(INPUT_DATA), new BinaryTextContentHandler(null, fileOutputStream, true, false, null, false, false, null, false));
                        temporaryZipFile = ((DiskFileItem) fileItem).getStoreLocation();
                    }

                    contentHandler.startDocument();
                    // <files>
                    contentHandler.startElement("", "files", "files", XMLUtils.EMPTY_ATTRIBUTES);
                    ZipFile zipFile = new ZipFile(temporaryZipFile);
                    for (Enumeration entries = zipFile.entries(); entries.hasMoreElements();) {
                        // Go through each entry in the zip file
                        ZipEntry zipEntry = (ZipEntry) entries.nextElement();
                        // Get file name
                        String fileName = zipEntry.getName();
                        long fileSize = zipEntry.getSize();
                        String fileTime = ISODateUtils.XS_DATE_TIME.format(new Date(zipEntry.getTime()));

                        InputStream entryInputStream = zipFile.getInputStream(zipEntry);
                        String uri = NetUtils.inputStreamToAnyURI(context, entryInputStream, NetUtils.REQUEST_SCOPE);
                        // <file name="filename.ext">uri</file>
                        AttributesImpl fileAttributes = new AttributesImpl();
                        fileAttributes.addAttribute("", "name", "name", "CDATA", fileName);
                        fileAttributes.addAttribute("", "size", "size", "CDATA", Long.toString(fileSize));
                        fileAttributes.addAttribute("", "dateTime", "dateTime", "CDATA", fileTime);
                        contentHandler.startElement("", "file", "file", fileAttributes);
                        contentHandler.characters(uri.toCharArray(), 0, uri.length());
                        // </file>
                        contentHandler.endElement("", "file", "file");
                    }
                    // </files>
                    contentHandler.endElement("", "files", "files");
                    contentHandler.endDocument();

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
