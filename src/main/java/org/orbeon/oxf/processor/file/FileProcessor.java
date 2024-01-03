/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.file;

import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;

/**
 * The File Processor allows performing some operations on files.
 *
 * For now, just allow for one "delete" and "scp" operations.
 *
 * In the future, this can be enhanced to support multiple actions, including delete, create, "touch", rename, move,
 * etc.
 */
public class FileProcessor extends ProcessorImpl {

    public static final String DIRECTORY_PROPERTY = "directory";

    private static final boolean DEFAULT_MAKE_DIRECTORIES = false;

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(FileProcessor.class);

    public static final String FILE_PROCESSOR_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/file-processor-config";

    public FileProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, FILE_PROCESSOR_CONFIG_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        try {
            // Read config
            final Document config = readCacheInputAsOrbeonDom(context, INPUT_CONFIG);

            for (Iterator i = XPathUtils.selectNodeIterator(config, "/*/*"); i.hasNext();) {
                final Element currentElement = (Element) i.next();
                if (currentElement.getName().equals("delete")) {
                    // delete operation


                    // Get file object
                    final File file = NetUtils.getFile(
                            getDirectory(currentElement, "directory"),
                            XPathUtils.selectStringValueNormalize(currentElement, "file"),
                            XPathUtils.selectStringValueNormalize(currentElement, "url"),
                            getLocationData(),
                            false
                    );

                    // Delete file if it exists
                    if (file.exists() && file.canWrite()) {
                        final boolean deleted = file.delete();
                        if (!deleted)
                            throw new OXFException("Can't delete file: " + file);
                    }
                } else if (currentElement.getName().equals("move")) {
                    // Move operation

                    // From
                    final File fromFile = NetUtils.getFile(
                            getDirectory(currentElement, "from/directory"),
                            XPathUtils.selectStringValueNormalize(currentElement, "from/file"),
                            XPathUtils.selectStringValueNormalize(currentElement, "from/url"),
                            getLocationData(),
                            false
                            );

                    if (!fromFile.exists() || ! fromFile.canRead()) {
                        throw new OXFException("Can't move file: " + fromFile);
                    }

                    // To
                    final File toFile = NetUtils.getFile(
                            getDirectory(currentElement, "to/directory"),
                            XPathUtils.selectStringValueNormalize(currentElement, "to/file"),
                            XPathUtils.selectStringValueNormalize(currentElement, "to/url"),
                            getLocationData(),
                            ProcessorUtils.selectBooleanValue(currentElement, "to/make-directories", DEFAULT_MAKE_DIRECTORIES)
                            );

                    if (! (toFile.exists() || toFile.createNewFile() )) {
                        throw new OXFException("Can't create file: " + toFile);
                    }

                    // Move
                    if (! fromFile.renameTo(toFile)) {
                        // If for whatever reason renameTo fails, try to copy and delete it
                        copyFile(fromFile, toFile);
                        final boolean deleted = fromFile.delete();
                        if (!deleted)
                            throw new OXFException("Can't delete file " + fromFile + " after copying it to " + toFile);

                    }


                } else if (currentElement.getName().equals("copy")) {
                    // Copy operation

                    // From
                    final File fromFile = NetUtils.getFile(
                            getDirectory(currentElement, "from/directory"),
                            XPathUtils.selectStringValueNormalize(currentElement, "from/file"),
                            XPathUtils.selectStringValueNormalize(currentElement, "from/url"),
                            getLocationData(),
                            false
                            );

                    if (!fromFile.exists() || ! fromFile.canRead()) {
                        throw new OXFException("Can't copy file: " + fromFile);
                    }

                    // To
                    final File toFile = NetUtils.getFile(
                            getDirectory(currentElement, "to/directory"),
                            XPathUtils.selectStringValueNormalize(currentElement, "to/file"),
                            XPathUtils.selectStringValueNormalize(currentElement, "to/url"),
                            getLocationData(),
                            ProcessorUtils.selectBooleanValue(currentElement, "to/make-directories", DEFAULT_MAKE_DIRECTORIES)
                            );

                    if (! (toFile.exists() || toFile.createNewFile() )) {
                        throw new OXFException("Can't create file: " + toFile);
                    }

                    // Copy
                    copyFile(fromFile, toFile);
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private String getDirectory(Element currentElement, String elementPath) {
        final String configDirectory = XPathUtils.selectStringValueNormalize(currentElement, elementPath);
        return configDirectory != null ? configDirectory : getPropertySet().getString(DIRECTORY_PROPERTY);
    }

    public static void copyFile(File sourceFile, File destFile)  {
        try {
        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
        }
        catch (IOException e) {
            throw new OXFException("Cannot copy file" + sourceFile + " to " + destFile, e);
        }
    }
}
