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
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.DeferredFileOutputStream;
import org.apache.commons.fileupload.DefaultFileItem;
import org.apache.commons.fileupload.DefaultFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.xml.sax.SAXException;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.serializer.store.ResultStore;
import org.orbeon.oxf.processor.serializer.store.ResultStoreOutputStream;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xml.XMLUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import org.xml.sax.ContentHandler;
import javax.xml.parsers.DocumentBuilderFactory;

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

    private static final boolean DEFAULT_APPEND = false;
    private static final boolean DEFAULT_MAKE_DIRECTORIES = false;

    private static FileItemFactory fileItemFactory;
    private static DocumentBuilderFactory documentBuilderFactory;


  static {
        try {
            // Create factory
            documentBuilderFactory = (DocumentBuilderFactory) Class.forName("orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl").newInstance();
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

        private String directory;
        private String file;
        private String scope;
        private String url;
        private boolean append;
        private boolean makeDirectories;

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

            //Scope
            scope = XPathUtils.selectStringValueNormalize(document, "/config/scope");
            //URL
            url = XPathUtils.selectStringValueNormalize(document, "/config/url");

            // Cache control
            cacheUseLocalCache = ProcessorUtils.selectBooleanValue(document, "/config/cache-control/use-local-cache", CachedSerializer.DEFAULT_CACHE_USE_LOCAL_CACHE);

            // Whether to append or not
            append = ProcessorUtils.selectBooleanValue(document, "/config/append", DEFAULT_APPEND);

            // Whether to append or not
            makeDirectories = ProcessorUtils.selectBooleanValue(document, "/config/make-directories", DEFAULT_MAKE_DIRECTORIES);

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

        public String getScope() {
            return scope;
        }

        public String getUrl() {
          return url;
        }

        public boolean isAppend() {
            return append;
        }

        public boolean isMakeDirectories() {
            return makeDirectories;
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
            final File file = getFile(config.getDirectory(), config.getFile(), config.isMakeDirectories(), getPropertySet());

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

            // Delete file if it exists, unless we append
            if (!config.isAppend() && file.exists()) {
                final boolean deleted = file.delete();
                // We test on file.exists() here again so we don't complain that the file can't be deleted if it got
                // deleted just between our last test and the delete operation.
                if (!deleted && file.exists())
                    throw new OXFException("Can't delete file: " + file);
            }

            // Create file if needed
            file.createNewFile();
//            if (!file.createNewFile())
//                throw new OXFException("Can't create file: " + file);

            // Create Writer and make sure it is closed when the pipeline terminates
            final OutputStream fileOutputStream = new FileOutputStream(file, config.isAppend());
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


    /**
     * Case where a response must be generated.
     */
    public ProcessorOutput createOutput(String name) {

      final ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
        public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
          OutputStream fileOutputStream = null;
          try {
            //Get the input and config
            final Config config = getConfig(pipelineContext);
            final ProcessorInput dataInput = getInputByName(INPUT_DATA);
            final Document configDocument = readInputAsDOM4J(pipelineContext,dataInput);
            final String text = XPathUtils.selectStringValueNormalize(configDocument, "/text");
            // We use the commons fileupload utilities to write to file
            if (fileItemFactory == null)
              fileItemFactory = new DefaultFileItemFactory(0,SystemUtils.getTemporaryDirectory());
            final FileItem fileItem = fileItemFactory.createItem("dummy", "dummy", false, null);

            if("request".equals(config.getScope())) {
              deleteFileOnRequestEnd(pipelineContext, fileItem);
            }
            else if("session".equals(config.getScope())) {
              deleteFileOnSessionTermination(pipelineContext,fileItem);
            }
            else if("application".equals(config.getScope())) {
              deleteFileOnContextDestroyed(pipelineContext,fileItem);
            }

            fileOutputStream = fileItem.getOutputStream();
            fileOutputStream.write(text.getBytes());
            fileOutputStream.flush();
            // Create file if it doesn't exist
            final File storeLocation = ( (DefaultFileItem) fileItem).getStoreLocation();
            storeLocation.createNewFile();
            // Get the url of the file
            final String url = ( (DefaultFileItem) fileItem).getStoreLocation().toURI().toString();
            contentHandler.startDocument();
            contentHandler.startElement("", "url", "url", XMLUtils.EMPTY_ATTRIBUTES);
            contentHandler.characters(url.toCharArray(), 0, url.length());
            contentHandler.endElement("", "url", "url");
            contentHandler.endDocument();
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
      final Config config = (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
          public Object read(PipelineContext context, ProcessorInput input) {
              return new Config(readInputAsDOM4J(context, input));
          }
        });
      return config;
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed at the end of request
     * @param pipelineContext PipelineContext
     * @param fileItem FileItem
     */
    private void deleteFileOnRequestEnd(PipelineContext pipelineContext, final FileItem fileItem) {
        // Make sure the file is deleted at the end of request
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
            public void contextDestroyed(boolean success) {
                try {
                    // Log when we delete files
                    if (logger.isDebugEnabled()) {
                        final String temporaryFileName = ((DeferredFileOutputStream) fileItem.getOutputStream()).getFile().getAbsolutePath();
                        logger.debug("Deleting temporary file: " + temporaryFileName);
                    }
                    fileItem.delete();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        });
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed on session destruction
     * @param pipelineContext PipelineContext
     * @param fileItem FileItem
     */
    private void deleteFileOnSessionTermination(PipelineContext pipelineContext, final FileItem fileItem) {
        // Try to delete the file on exit and on session termination
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final ExternalContext.Session session = externalContext.getSession(false);
        if (session != null) {
          session.addListener(new ExternalContext.Session.SessionListener() {
            public void sessionDestroyed() {
              try {
                if (logger.isDebugEnabled()) {
                final String temporaryFileName = ((DeferredFileOutputStream) fileItem.getOutputStream()).getFile().getAbsolutePath();
                logger.debug("Deleting temporary Session file: " + temporaryFileName);
                }
                fileItem.delete();
              }
              catch (IOException e) {
                throw new OXFException(e);
              }
            }
          });
        }
        else {
          XFormsServer.logger.debug("XForms - no existing session found so cannot register temporary file deletion upon session destruction: " + fileItem.getName());
        }
    }

    /**
     * Add listener to fileItem which is going to be automatically destroyed when the servlet is destroyed
     * @param pipelineContext PipelineContext
     * @param fileItem FileItem
     */
    private void deleteFileOnContextDestroyed(PipelineContext pipelineContext, final FileItem fileItem) {
        // Try to delete the file on exit and on session termination
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        ExternalContext.Application application = externalContext.getApplication();
        if (application != null) {
          application.addListener(new ExternalContext.Application.ApplicationListener() {
            public void servletDestroyed() {
              try {
                if (logger.isDebugEnabled()) {
                  final String temporaryFileName = ( (DeferredFileOutputStream) fileItem.getOutputStream()).getFile().getAbsolutePath();
                  logger.debug("Deleting temporary Application file: " + temporaryFileName);
                }
                fileItem.delete();
              }
              catch (IOException e) {
                throw new OXFException(e);
              }
            }
          });
        }
        else {
          XFormsServer.logger.debug("XForms - no Application object found so cannot register temporary file deletion upon session destruction: " +
                                    fileItem.getName());
        }
      }

    public static File getFile(String configDirectory, String configFile, boolean makeDirectories, OXFProperties.PropertySet propertySet) {
        final File file;
        final String directoryProperty = (propertySet != null) ? propertySet.getString(DIRECTORY_PROPERTY) : null;
        if (directoryProperty == null && configDirectory == null) {
            // No base directory specified
            file = new File(configFile);
        } else {
            // Base directory specified
            final File baseDirectory = (configDirectory != null) ? new File(configDirectory) : new File(directoryProperty);

            // Make directories if needed
            if (makeDirectories) {
                if (!baseDirectory.exists()) {
                    if (!baseDirectory.mkdirs())
                        throw new OXFException("Directory '" + baseDirectory + "' could not be created.");
                }
            }

            if (!baseDirectory.isDirectory() || !baseDirectory.canWrite())
                throw new OXFException("Directory '" + baseDirectory + "' is not a directory or is not writeable.");

            file = new File(baseDirectory, configFile);
        }
        // Make directories if needed
        if (makeDirectories) {
            if (!file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs())
                    throw new OXFException("Directory '" + file.getParentFile() + "' could not be created.");
            }
        }

        return file;
    }

    private interface ResourceHandler {
       public Object getValidity() throws IOException;

       public String getResourceContentType() throws IOException;

       public String getConnectionEncoding() throws IOException;

       public void destroy() throws IOException;

       public void readText(ContentHandler output, String contentType) throws IOException;

       public void readXML(PipelineContext pipelineContext, ContentHandler output) throws IOException;


       public void readBinary(ContentHandler output, String contentType) throws IOException;
   }

   private static class OXFResourceHandler implements ResourceHandler {
       private Config config;
       private String resourceManagerKey;
       private InputStream inputStream;

       public OXFResourceHandler(Config config) {
           this.config = config;
       }

       public String getResourceContentType() throws IOException {
           // We generally don't know the "connection" content-type
           return null;
       }

       public String getConnectionEncoding() throws IOException {
           // We generally don't know the "connection" encoding
           // NOTE: We could know, if the underlying protocol was for example HTTP. But we may
           // want to abstract that anyway, so that the behavior is consistent whatever the sandbox
           // is.
           return null;
       }

       public Object getValidity() throws IOException {
           getKey();
           if (logger.isDebugEnabled())
               logger.debug("OXF Protocol: Using ResourceManager for key " + getKey());

           long result = ResourceManagerWrapper.instance().lastModified(getKey(), false);
           // Zero and negative values often have a special meaning, make sure to normalize here
           return (result <= 0) ? null : new Long(result);
       }

       public void destroy() throws IOException {
           if (inputStream != null) {
               inputStream.close();
           }
       }

       public void readText(ContentHandler output, String contentType) throws IOException {
           inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
           ProcessorUtils.readText(inputStream, null, output, contentType);
       }

       public void readXML(PipelineContext pipelineContext, ContentHandler output) throws IOException {
         // Regular case, the resource manager does the job and autodetects the encoding
         ResourceManagerWrapper.instance().getContentAsSAX(getKey(), output, true, false);
       }

       public void readBinary(ContentHandler output, String contentType) throws IOException {
           inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
           ProcessorUtils.readBinary(inputStream, output, contentType);
       }

       private String getKey() {
           if (resourceManagerKey == null)
               resourceManagerKey = config.getUrl();
             System.err.println("resourceManagerKey="+resourceManagerKey);
           return resourceManagerKey;
       }
   }

}
