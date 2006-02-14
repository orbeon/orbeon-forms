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
package org.orbeon.oxf.processor.serializer;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.serializer.store.ResultStore;
import org.orbeon.oxf.processor.serializer.store.ResultStoreOutputStream;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.resources.OXFProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The File Serializer serializes text and binary documents to files on disk.
 */
public class FileSerializer extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(FileSerializer.class);

    public static final String FILE_SERIALIZER_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/file-serializer-config";

    public static final String DIRECTORY_PROPERTY = "directory";

    // NOTE: Those are also in HttpSerializerBase
    private static final boolean DEFAULT_FORCE_CONTENT_TYPE = false;
    private static final boolean DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE = false;

    private static final boolean DEFAULT_FORCE_ENCODING = false;
    private static final boolean DEFAULT_IGNORE_DOCUMENT_ENCODING = false;

    public FileSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, FILE_SERIALIZER_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    private static class Config {

        private String directory;
        private String file;
        private boolean cacheUseLocalCache;

        private boolean forceContentType;
        private String requestedContentType;
        private boolean ignoreDocumentContentType;

        private boolean forceEncoding;
        private String requestedEncoding;
        private boolean ignoreDocumentEncoding;


        public Config(Document document) {
            // Directory and file
            directory = XPathUtils.selectStringValueNormalize(document, "/config/directory");
            file = XPathUtils.selectStringValueNormalize(document, "/config/file");

            // Cache control
            String cacheUseLocalCacheString = XPathUtils.selectStringValueNormalize(document, "/config/cache-control/use-local-cache");
            if (cacheUseLocalCacheString == null)
                cacheUseLocalCache = CachedSerializer.DEFAULT_CACHE_USE_LOCAL_CACHE;
            else
                cacheUseLocalCache = new Boolean(cacheUseLocalCacheString).booleanValue();

            // Content-type and Encoding
            requestedContentType = XPathUtils.selectStringValueNormalize(document, "/config/content-type");

            forceContentType = ProcessorUtils.selectBooleanValue(document, "/config/force-content-type", DEFAULT_FORCE_CONTENT_TYPE);
            if (forceContentType && (document == null || document.equals("")))
                throw new OXFException("The force-content-type element requires a content-type element.");
            ignoreDocumentContentType = ProcessorUtils.selectBooleanValue(document, "/config/ignore-document-content-type", DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE);

            requestedEncoding = XPathUtils.selectStringValueNormalize(document, "/config/encoding");
            forceEncoding = ProcessorUtils.selectBooleanValue(document, "/config/force-encoding", DEFAULT_FORCE_ENCODING);
            if (forceEncoding && (requestedEncoding == null || requestedEncoding.equals("")))
                throw new OXFException("The force-encoding element requires an encoding element.");
            ignoreDocumentEncoding = ProcessorUtils.selectBooleanValue(document, "/config/ignore-document-encoding", DEFAULT_IGNORE_DOCUMENT_ENCODING);
        }


        public String getDirectory() {
            return directory;
        }

        public String getFile() {
            return file;
        }

        public boolean isCacheUseLocalCache() {
            return cacheUseLocalCache;
        }

        public boolean isForceContentType() {
            return forceContentType;
        }

        public boolean isForceEncoding() {
            return forceEncoding;
        }

        public boolean isIgnoreDocumentContentType() {
            return ignoreDocumentContentType;
        }

        public boolean isIgnoreDocumentEncoding() {
            return ignoreDocumentEncoding;
        }

        public String getRequestedContentType() {
            return requestedContentType;
        }

        public String getRequestedEncoding() {
            return requestedEncoding;
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

            final ProcessorInput dataInput = getInputByName(INPUT_DATA);

            // Get file object
            final File file = getFile(config.getDirectory(), config.getFile(), getPropertySet());

            // NOTE: Caching here is broken, so we never cache. This is what we should do in case
            // we want caching:
            // o for a given file, store a hash of the content stored (or the input key?)
            // o then when we check whether we need to modify the file, check against the key
            //   AND the validity

            // Compute last modified
//            Object validity = getInputValidity(context, dataInput);
//            long now = System.currentTimeMillis();
//            long lastModified = (validity != null) ? findLastModified(validity) : now;
//            boolean cacheable = validity != null && lastModified != 0;
//            if (lastModified == 0)
//                lastModified = now;
//
//            if (logger.isDebugEnabled())
//                logger.debug("Last modified: " + lastModified);
//
//            // Check lastModified and don't return content if condition is met
//            if (cacheable && (lastModified <= (file.lastModified() + 1000))) {
//                if (logger.isDebugEnabled())
//                    logger.debug("File doesn't need rewrite");
//                return;
//            }

            // Delete file if it exists
            if (file.exists() && file.canWrite()) {
                final boolean deleted = file.delete();
                if (!deleted)
                    throw new OXFException("Can't delete file: " + file);
            }

            // Create file
            if (!file.createNewFile())
                throw new OXFException("Can't create file: " + file);

            // Create Writer and make sure it is closed when the pipeline terminates
            final OutputStream fileOutputStream = new FileOutputStream(file);
            context.addContextListener(new PipelineContext.ContextListenerAdapter() {
                public void contextDestroyed(boolean success) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
            });

            if (config.cacheUseLocalCache) {
                // If caching of the data is enabled, use the caching API
                // We return a ResultStore
                final boolean[] read = new boolean[1];
                ResultStore filter = (ResultStore) readCacheInputAsObject(context, dataInput, new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        read[0] = true;
                        if (logger.isDebugEnabled())
                            logger.debug("Output not cached");
                        try {
                            ResultStoreOutputStream resultStoreOutputStream = new ResultStoreOutputStream(fileOutputStream);

                            readInputAsSAX(context, input, new BinaryTextContentHandler(null, resultStoreOutputStream,
                                    config.forceContentType, config.requestedContentType, config.ignoreDocumentContentType,
                                    config.forceEncoding, config.requestedEncoding, config.ignoreDocumentEncoding));

                            resultStoreOutputStream.close();
                            return resultStoreOutputStream;
                        } catch (IOException e) {
                            throw new OXFException(e);
                        }
                    }
                });

                // If the output was obtained from the cache, just write it
                if (!read[0]) {
                    if (logger.isDebugEnabled())
                        logger.debug("Serializer output cached");
                    filter.replay(fileOutputStream);
                }
            } else {
                // Caching is not enabled
                readInputAsSAX(context, dataInput, new BinaryTextContentHandler(null, fileOutputStream,
                        config.forceContentType, config.requestedContentType, config.ignoreDocumentContentType,
                        config.forceEncoding, config.requestedEncoding, config.ignoreDocumentEncoding));

                fileOutputStream.close();
            }

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static File getFile(String configDirectory, String configFile, OXFProperties.PropertySet propertySet) {
        final File file;
        final String directoryProperty = (propertySet != null) ? propertySet.getString(DIRECTORY_PROPERTY) : null;
        if (directoryProperty == null && configDirectory == null) {
            // No base directory specified
            file = new File(configFile);
        } else {
            // Base directory specified
            final File baseDirectory = (configDirectory != null) ? new File(configDirectory) : new File(directoryProperty);
            if (!baseDirectory.isDirectory() || !baseDirectory.canWrite())
                throw new OXFException("Directory '" + baseDirectory + "' is not a directory or is not writeable.");

            file = new File(baseDirectory, configFile);
        }
        return file;
    }
}
