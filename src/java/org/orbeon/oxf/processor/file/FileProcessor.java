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
package org.orbeon.oxf.processor.file;

import org.apache.log4j.Logger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.ssh.Scp;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.FileSerializer;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.File;
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

    private static Logger logger = LoggerFactory.createLogger(FileProcessor.class);

    public static final String FILE_PROCESSOR_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/file-processor-config";

    public FileProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, FILE_PROCESSOR_CONFIG_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        try {
            // Read config
            final Document config = readCacheInputAsDOM4J(context, INPUT_CONFIG);

            for (Iterator i = XPathUtils.selectIterator(config, "/*/*"); i.hasNext();) {
                final Element currentElement = (Element) i.next();
                if (currentElement.getName().equals("delete")) {
                    // delete operation

                    // Directory and file
                    final String directoryString = XPathUtils.selectStringValueNormalize(currentElement, "directory");
                    final String fileString = XPathUtils.selectStringValueNormalize(currentElement, "file");

                    // Get file object
                    final File file = FileSerializer.getFile(directoryString, fileString, false, getPropertySet());

                    // Delete file if it exists
                    if (file.exists() && file.canWrite()) {
                        final boolean deleted = file.delete();
                        if (!deleted)
                            throw new OXFException("Can't delete file: " + file);
                    }
                } else if (currentElement.getName().equals("scp")) {
                    // scp operation

                    // Create ant task
                    final Scp scp = new Scp() {
                        @Override
                        public void log(String msg, int msgLevel) {
                            switch (msgLevel) {
                                case Project.MSG_ERR:
                                    logger.error(msg);
                                    break;
                                case Project.MSG_WARN:
                                    logger.warn(msg);
                                    break;
                                case Project.MSG_INFO:
                                    logger.info(msg);
                                    break;
                                case Project.MSG_VERBOSE:
                                case Project.MSG_DEBUG:
                                    logger.debug(msg);
                                    break;
                            }
                        }

                        private final Project project = new Project() {
                            @Override
                            public File getBaseDir() {
                                return super.getBaseDir();
                            }
                        };

                        @Override
                        public Project getProject() {
                            return project;
                        }
                    };
                    scp.init();

                    // Set it up
                    setupScp(scp, currentElement);

                    // Execute it
                    scp.execute();
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private void setupScp(Scp scp, Element currentElement) {
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@file");
            if (value != null)
                scp.setFile(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@todir");
            if (value != null)
                scp.setTodir(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@localFile");
            if (value != null)
                scp.setLocalFile(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@remoteFile");
            if (value != null)
                scp.setRemoteFile(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@localTodir");
            if (value != null)
                scp.setLocalTodir(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@remoteTodir");
            if (value != null)
                scp.setRemoteTodir(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@localTofile");
            if (value != null)
                scp.setLocalTofile(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@remoteTofile");
            if (value != null)
                scp.setRemoteTofile(value);
        }

        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@failonerror");
            if (value != null)
                scp.setFailonerror(Boolean.parseBoolean(value));
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@verbose");
            if (value != null)
                scp.setVerbose(Boolean.parseBoolean(value));
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@password");
            if (value != null)
                scp.setPassword(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@keyfile");
            if (value != null)
                scp.setKeyfile(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@passphrase");
            if (value != null)
                scp.setPassphrase(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@knownhost");
            if (value != null)
                scp.setKnownhosts(value);
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@trust");
            if (value != null)
                scp.setTrust(Boolean.parseBoolean(value));
        }
        {
            final String value = XPathUtils.selectStringValueNormalize(currentElement, "@port");
            if (value != null)
                scp.setPort(Integer.parseInt(value));
        }
    }
}
