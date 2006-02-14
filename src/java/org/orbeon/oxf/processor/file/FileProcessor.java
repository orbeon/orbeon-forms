/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.processor.file;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.FileSerializer;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.File;

/**
 * The File Processor allows performing some operations on files.
 *
 * For now, just allow for one "delete" operation. In the future, this can be enhanced to support
 * multiple actions, including delete, create, "touch", rename, move, etc.
 */
public class FileProcessor extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(FileProcessor.class);

    public static final String FILE_PROCESSOR_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/file-processor-config";

    public static final String DIRECTORY_PROPERTY = "directory";

    public FileProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, FILE_PROCESSOR_CONFIG_NAMESPACE_URI));
    }

    private static class Config {

        private String directory;
        private String file;

        public Config(Document document) {
            // Directory and file
            directory = XPathUtils.selectStringValueNormalize(document, "/config/delete[1]/directory");
            file = XPathUtils.selectStringValueNormalize(document, "/config/delete[1]/file");
        }

        public String getDirectory() {
            return directory;
        }

        public String getFile() {
            return file;
        }
    }

    public void start(PipelineContext context) {
        try {
            // Read config
            final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    return new Config(readInputAsDOM4J(context, input));
                }
            });

            // Get file object
            final File file = FileSerializer.getFile(config.getDirectory(), config.getFile(), getPropertySet());

            // Delete file if it exists
            if (file.exists() && file.canWrite()) {
                final boolean deleted = file.delete();
                if (!deleted)
                    throw new OXFException("Can't delete file: " + file);
            }

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
