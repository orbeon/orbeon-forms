/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xinclude;

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.resources.URLFactory;
import org.xml.sax.ContentHandler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * XInclude processor.
 *
 * This processor reads a document on its data input that may contain XInclude directives. It
 * produces on its output a resulting document with the XInclude directives processed.
 */
public class XIncludeProcessor extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(XIncludeProcessor.class);

    private ConfigURIReferences localConfigURIReferences;

    public XIncludeProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new URIProcessorOutputImpl(getClass(), name, INPUT_DATA) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
                // TODO: read and process!
            }

            // TODO: implement helpers for URIProcessorOutputImpl
        };
        addOutput(name, output);
        return output;
    }

    public abstract class URIProcessorOutputImpl extends ProcessorImpl.ProcessorOutputImpl {

        private String configInputName;

        public URIProcessorOutputImpl(Class clazz, String name, String configInputName) {
            super(clazz, name);
            this.configInputName = configInputName;
        }

        public OutputCacheKey getKeyImpl(PipelineContext context) {
            try {
                ConfigURIReferences configURIReferences = getConfigURIReferences(context);
                if (configURIReferences == null)
                    return null;

                List keys = new ArrayList();

                // Handle config if read as input
                if (localConfigURIReferences == null) {
                    KeyValidity configKeyValidity = getInputKeyValidity(context, configInputName);
                    if (configKeyValidity == null) return null;
                    keys.add(configKeyValidity.key);
                }
                // Handle main document and config
                keys.add(new SimpleOutputCacheKey(getProcessorClass(), getName(), configURIReferences.config.toString()));// TODO: check this
                // Handle dependencies if any
                if (configURIReferences.uriReferences != null) {
                    for (Iterator i = configURIReferences.uriReferences.references.iterator(); i.hasNext();) {
                        URIReference uriReference = (URIReference) i.next();
                        keys.add(new InternalCacheKey(XIncludeProcessor.this, "urlReference", URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm()));
                    }
                }
                final CacheKey[] outKys = new CacheKey[keys.size()];
                keys.toArray(outKys);
                return new CompoundOutputCacheKey(getProcessorClass(), getName(), outKys);
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }
        }

        public Object getValidityImpl(PipelineContext context) {
            return null;
//            try {
//                ConfigURIReferences configURIReferences = getConfigURIReferences(context);
//                if (configURIReferences == null)
//                    return null;
//
//                List validities = new ArrayList();
//
//                // Handle config if read as input
//                if (localConfigURIReferences == null) {
//                    KeyValidity configKeyValidity = getInputKeyValidity(context, configInputName);
//                    if (configKeyValidity == null)
//                        return null;
//                    validities.add(configKeyValidity.validity);
//                }
//                // Handle main document and config
//                validities.add(getHandlerValidity(configURIReferences.config.getURL()));
//                // Handle dependencies if any
//                if (configURIReferences.uriReferences != null) {
//                    for (Iterator i = configURIReferences.uriReferences.references.iterator(); i.hasNext();) {
//                        URIReference uriReference = (URIReference) i.next();
//                        validities.add(getHandlerValidity(URLFactory.createURL(uriReference.context, uriReference.spec)));
//                    }
//                }
//                return validities;
//            } catch (IOException e) {
//                throw new OXFException(e);
//            }
        }

        private Object getHandlerValidity(URL url) {
            return null;
//            try {
//                ResourceHandler handler = Handler.PROTOCOL.equals(url.getProtocol())
//                        ? (ResourceHandler) new OXFResourceHandler(new Config(url))
//                        : (ResourceHandler) new URLResourceHandler(new Config(url));
//                try {
//                    // FIXME: this can potentially be very slow with some URLs
//                    return handler.getValidity();
//                } finally {
//                    if (handler != null)
//                        handler.destroy();
//                }
//            } catch (Exception e) {
//                // If the file no longer exists, for example, we don't want to throw, just to invalidate
//                // An exception will be thrown if necessary when the document is actually read
//                return null;
//            }
        }


        private ConfigURIReferences getConfigURIReferences(PipelineContext context) {
            // Check if config is external
            if (localConfigURIReferences != null)
                return localConfigURIReferences;

            // Make sure the config input is cacheable
            KeyValidity keyValidity = getInputKeyValidity(context, configInputName);
            if (keyValidity == null)
                return null;

            // Try to find resource manager key in cache
            ConfigURIReferences config = (ConfigURIReferences) ObjectCache.instance().findValid(context, keyValidity.key, keyValidity.validity);
            if (logger.isDebugEnabled()) {
                if (config != null)
                    logger.debug("Config found: " + config.toString());
                else
                    logger.debug("Config not found");
            }
            return config;
        };
    }

    private static class Config {

    }

    private static class ConfigURIReferences {
        public ConfigURIReferences(Config config) {
            this.config = config;
        }

        public Config config;
        public URIReferences uriReferences;
    }

    private static class URIReference {
        public URIReference(String context, String spec) {
            this.context = context;
            this.spec = spec;
        }

        public String context;
        public String spec;
    }

    private static class URIReferences {
        public List references = new ArrayList();
    }
}
