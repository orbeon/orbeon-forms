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
package org.orbeon.oxf.processor.serializer;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.orbeon.dom.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.file.FileProcessor;
import org.orbeon.oxf.processor.serializer.store.ResultStore;
import org.orbeon.oxf.processor.serializer.store.ResultStoreOutputStream;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.processor.XFormsAssetServerRoute;
import org.orbeon.oxf.xml.SAXUtils;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The File Serializer serializes text and binary documents to files on disk.
 *
 * TODO: 2017-07-07: The only reason this is in the `xforms` module is that this depends on `XFormsAssetServer`.
 * See https://github.com/orbeon/orbeon-forms/issues/3292.
 */
public class FileSerializer extends ProcessorImpl {

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(FileSerializer.class);

    public static final String FILE_SERIALIZER_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/file-serializer-config";

    // NOTE: Those are also in HttpSerializerBase
    private static final boolean DEFAULT_FORCE_CONTENT_TYPE = false;
    private static final boolean DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE = false;

    private static final boolean DEFAULT_FORCE_ENCODING = false;
    private static final boolean DEFAULT_IGNORE_DOCUMENT_ENCODING = false;

    private static final boolean DEFAULT_APPEND = false;
    private static final boolean DEFAULT_MAKE_DIRECTORIES = false;

    static {
        try {
            // Create factory
            DocumentBuilderFactory documentBuilderFactory = (DocumentBuilderFactory) Class.forName("org.orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl").newInstance();
            // Configure factory
            documentBuilderFactory.setNamespaceAware(true);
        }
        catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public FileSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, FILE_SERIALIZER_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        // We don't declare the "data" output here, as this is an optional output.
        // If we declare it, we'll the XPL engine won't be happy when don't connect anything to that output.
    }

    private static class Config {

        private final String directory;
        private final String file;
        private final String scope;
        private final boolean proxyResult;
        private final String url;
        private final boolean append;
        private final boolean makeDirectories;

        private final boolean cacheUseLocalCache;

        private final boolean forceContentType;
        private final String requestedContentType;
        private final boolean ignoreDocumentContentType;

        private final boolean forceEncoding;
        private final String requestedEncoding;
        private final boolean ignoreDocumentEncoding;

        public Config(Document document) {
            // Directory and file
            directory = XPathUtils.selectStringValueNormalize(document, "/config/directory");
            file = XPathUtils.selectStringValueNormalize(document, "/config/file");

            // Scope
            scope = XPathUtils.selectStringValueNormalize(document, "/config/scope");
            // Proxy result
            proxyResult = ProcessorUtils.selectBooleanValue(document, "/config/proxy-result", false);
            // URL
            url = XPathUtils.selectStringValueNormalize(document, "/config/url");

            // Cache control
            cacheUseLocalCache = ProcessorUtils.selectBooleanValue(document, "/config/cache-control/use-local-cache", CachedSerializer.DefaultCacheUseLocalCache());

            // Whether to append or not
            append = ProcessorUtils.selectBooleanValue(document, "/config/append", DEFAULT_APPEND);

            // Whether to append or not
            makeDirectories = ProcessorUtils.selectBooleanValue(document, "/config/make-directories", DEFAULT_MAKE_DIRECTORIES);

            // Content-type and Encoding
            requestedContentType = XPathUtils.selectStringValueNormalize(document, "/config/content-type");

            forceContentType = ProcessorUtils.selectBooleanValue(document, "/config/force-content-type", DEFAULT_FORCE_CONTENT_TYPE);
            // TODO: We don't seem to be using the content type in the file serializer.
            // Maybe this is something that was left over from the days when the file serializer was also serializing XML.
            if (forceContentType)
                throw new OXFException("The force-content-type element requires a content-type element.");
            ignoreDocumentContentType = ProcessorUtils.selectBooleanValue(document, "/config/ignore-document-content-type", DEFAULT_IGNORE_DOCUMENT_CONTENT_TYPE);

            requestedEncoding = XPathUtils.selectStringValueNormalize(document, "/config/encoding");
            forceEncoding = ProcessorUtils.selectBooleanValue(document, "/config/force-encoding", DEFAULT_FORCE_ENCODING);
            if (forceEncoding && (requestedEncoding == null || requestedEncoding.equals("")))
                throw new OXFException("The force-encoding element requires an encoding element.");
            ignoreDocumentEncoding = ProcessorUtils.selectBooleanValue(document, "/config/ignore-document-encoding", DEFAULT_IGNORE_DOCUMENT_ENCODING);
        }
    }

    @Override
    public void start(PipelineContext context) {
        try {
            // Read config
            final Config config = readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader<Config>() {
                public Config read(PipelineContext context, ProcessorInput input) {
                    return new Config(readInputAsOrbeonDom(context, input));
                }
            });

            final ProcessorInput dataInput = getInputByName(INPUT_DATA);

            // Get file object
            final String directory = config.directory != null ? config.directory : getPropertySet().getString(FileProcessor.DIRECTORY_PROPERTY);
            final File file = NetUtils.getFile(directory, config.file, config.url, getLocationData(), config.makeDirectories);

            // NOTE: Caching here is broken, so we never cache. This is what we should do in case
            // we want caching:
            // - for a given file, store a hash of the content stored (or the input key?)
            // - then when we check whether we need to modify the file, check against the key
            //   AND the validity

            // Delete file if it exists, unless we append
            if (!config.append && file.exists()) {
                final boolean deleted = file.delete();
                // We test on file.exists() here again so we don't complain that the file can't be deleted if it got
                // deleted just between our last test and the delete operation.
                if (!deleted && file.exists())
                    throw new OXFException("Can't delete file: " + file);
            }

            // Create file if needed
            file.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(file, config.append);
            writeToFile(context, config, dataInput, fileOutputStream);

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private void writeToFile(PipelineContext context, final Config config, ProcessorInput dataInput, final OutputStream fileOutputStream) throws IOException {
        try {
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
                            readInputAsSAX(context, input, new BinaryTextXMLReceiver(null, null, resultStoreOutputStream, true,
                                    config.forceContentType, config.requestedContentType, config.ignoreDocumentContentType,
                                    config.forceEncoding, config.requestedEncoding, config.ignoreDocumentEncoding, null));
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
                readInputAsSAX(context, dataInput, new BinaryTextXMLReceiver(null, null, fileOutputStream, true,
                        config.forceContentType, config.requestedContentType, config.ignoreDocumentContentType,
                        config.forceEncoding, config.requestedEncoding, config.ignoreDocumentEncoding, null));

                fileOutputStream.close();
            }
        } finally {
            if (fileOutputStream != null)
                fileOutputStream.close();
        }
    }


    /**
     * Case where a response must be generated.
     */
    @Override
    public ProcessorOutput createOutput(String name) {

        final ProcessorOutput output = new ProcessorOutputImpl(FileSerializer.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                OutputStream fileOutputStream = null;
                try {
                    //Get the input and config
                    final Config config = getConfig(pipelineContext);
                    final ProcessorInput dataInput = getInputByName(INPUT_DATA);

                    // Determine scope
                    final int scope;
                    if ("request".equals(config.scope)) {
                        scope = NetUtils.REQUEST_SCOPE;
                    } else if ("session".equals(config.scope)) {
                        scope = NetUtils.SESSION_SCOPE;
                    } else if ("application".equals(config.scope)) {
                        scope = NetUtils.APPLICATION_SCOPE;
                    } else {
                        throw new OXFException("Invalid context requested: " + config.scope);
                    }

                    // We use the commons fileupload utilities to write to file
                    final FileItem fileItem = NetUtils.prepareFileItem(scope, logger);
                    fileOutputStream = fileItem.getOutputStream();
                    writeToFile(pipelineContext, config, dataInput, fileOutputStream);

                    // Create file if it doesn't exist
                    final File storeLocation = ((DiskFileItem) fileItem).getStoreLocation();
                    storeLocation.createNewFile();

                    // Get the url of the file
                    final String resultURL;
                    {
                        final String localURL = ((DiskFileItem) fileItem).getStoreLocation().toURI().toString();
                        if ("session".equals(config.scope) && config.proxyResult)
                            resultURL = XFormsAssetServerRoute.jProxyURI(localURL, config.requestedContentType);
                        else
                            resultURL = localURL;
                    }

                    xmlReceiver.startDocument();
                    xmlReceiver.startElement("", "url", "url", SAXUtils.EMPTY_ATTRIBUTES);
                    xmlReceiver.characters(resultURL.toCharArray(), 0, resultURL.length());
                    xmlReceiver.endElement("", "url", "url");
                    xmlReceiver.endDocument();
                }
                catch (SAXException e) {
                    throw new OXFException(e);
                }
                catch (IOException e) {
                    throw new OXFException(e);
                }
                finally {
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        }
                        catch (IOException e) {
                            throw new OXFException(e);
                        }
                    }
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    protected Config getConfig(PipelineContext pipelineContext) {
        // Read config
        return readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader<Config>() {
            public Config read(PipelineContext context, ProcessorInput input) {
                return new Config(readInputAsOrbeonDom(context, input));
            }
        });
    }
}
