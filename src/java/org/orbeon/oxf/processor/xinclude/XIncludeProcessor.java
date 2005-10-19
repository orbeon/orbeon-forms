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

import orbeon.apache.xml.utils.NamespaceSupport2;
import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.sax.SAXSource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * XInclude processor.
 *
 * This processor reads a document on its "config" input that may contain XInclude directives. It
 * produces on its output a resulting document with the XInclude directives processed.
 *
 * For now, this processor only supports <xi:include href="..." parse="xml"/> with no xpointer,
 * encoding, accept, or accept-language attribute. <xi:fallback> is not supported.
 *
 * TODO: Implement caching!
 * TODO: Merge caching with URL generator, possibly XSLT transformer.
 * TODO: Allow including a file with root element <xi:include href="..."/> (doesn't work right now)
 */
public class XIncludeProcessor extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(XIncludeProcessor.class);

    private ConfigURIReferences localConfigURIReferences;

    public XIncludeProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new URIProcessorOutputImpl(getClass(), name, INPUT_CONFIG) {
            public void readImpl(final PipelineContext pipelineContext, final ContentHandler contentHandler) {
//                final ContentHandler debugContentHandler = new SAXDebuggerProcessor.DebugContentHandler(contentHandler);
                readInputAsSAX(pipelineContext, INPUT_CONFIG, new XIncludeContentHandler(pipelineContext, contentHandler));
            }

            // TODO: implement helpers for URIProcessorOutputImpl
        };
        addOutput(name, output);
        return output;
    }

    private class XIncludeContentHandler extends ForwardingContentHandler {

        private PipelineContext pipelineContext;
        private boolean topLevelContentHandler;
        private String xmlBase;
        private NamespaceSupport2 paremtNamespaceSupport;

        private Locator locator;
        private TransformerURIResolver uriResolver;
        private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();

        private int level;
        private boolean inInclude;
        private int includeLevel;

        public XIncludeContentHandler(PipelineContext pipelineContext, ContentHandler contentHandler) {
            this(pipelineContext, contentHandler, true, null, null);
        }

        public XIncludeContentHandler(PipelineContext pipelineContext, ContentHandler contentHandler, String xmlBase, NamespaceSupport2 paremtNamespaceSupport) {
            this(pipelineContext, contentHandler, false, xmlBase, paremtNamespaceSupport);
        }

        private XIncludeContentHandler(PipelineContext pipelineContext, ContentHandler contentHandler, boolean topLevelContentHandler, String xmlBase, NamespaceSupport2 paremtNamespaceSupport) {
            super(contentHandler);
            this.pipelineContext = pipelineContext;
            this.uriResolver = new TransformerURIResolver(XIncludeProcessor.this, pipelineContext, INPUT_CONFIG);
            this.topLevelContentHandler = topLevelContentHandler;
            this.xmlBase = xmlBase;
            this.paremtNamespaceSupport = paremtNamespaceSupport;
        }

        public void startDocument() throws SAXException {
            if (topLevelContentHandler)
                super.startDocument();
        }

        public void endDocument() throws SAXException {
            if (topLevelContentHandler)
                super.endDocument();
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

            namespaceSupport.pushContext();

            if (!topLevelContentHandler && level == 0) {
                // Clean-up namespace mappings
                sendStartPrefixMappings();
                // Add xml:base attribute
                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                newAttributes.addAttribute(XMLConstants.XML_URI, "base", "xml:base", ContentHandlerHelper.CDATA, xmlBase);
                attributes = newAttributes;
            }

            if (XMLConstants.XINCLUDE_URI.equals(uri) || XMLConstants.OLD_XINCLUDE_URI.equals(uri)) {
                // Found XInclude namespace

                if (XMLConstants.OLD_XINCLUDE_URI.equals(uri))
                    logger.warn("Using incorrect XInclude namespace URI: '" + uri + "'; should use '" + XMLConstants.XINCLUDE_URI + "' at " + new LocationData(locator).toString());

                if ("include".equals(localname)) {
                    // Start inclusion

                    inInclude = true;
                    includeLevel = level;

                    final String href = attributes.getValue("href");
                    final String parse = attributes.getValue("parse");

                    if (parse != null && !parse.equals("xml"))
                        throw new ValidationException("Invalid 'parse' attribute value: " + parse, new LocationData(locator));

                    try {
                        // Get SAXSource
                        System.out.println("href = " + href);
                        System.out.println("systemid = " + locator.getSystemId());
                        final SAXSource source = (SAXSource) uriResolver.resolve(href, locator.getSystemId());
                        final XMLReader xmlReader = source.getXMLReader();
                        xmlReader.setContentHandler(new XIncludeContentHandler(pipelineContext, getContentHandler(), source.getSystemId(), namespaceSupport));

                        // Read document
                        xmlReader.parse(new InputSource()); // Yeah, the SAX API doesn't make much sense

                    } catch (Exception e) {
                        // Resource error, must go to fallback if possible
                        throw new OXFException(e);
                    }

                } else if ("fallback".equals(localname)) {
                    // TODO
                } else {
                    throw new ValidationException("Invalid XInclude element: " + localname, new LocationData(locator));
                }

            } else {
                super.startElement(uri, localname, qName, attributes);
            }

            level++;
        }

        private void sendStartPrefixMappings() throws SAXException {
            for (Enumeration e = paremtNamespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml"))
                    super.startPrefixMapping(namespacePrefix, "");
            }

            final String defaultNS = paremtNamespaceSupport.getURI("");
            if (defaultNS != null && defaultNS.length() > 0)
                super.startPrefixMapping("", "");
        }

        private void sendEndPrefixMappings() throws SAXException {
            for (Enumeration e = paremtNamespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml"))
                    super.endPrefixMapping(namespacePrefix);
            }

            final String defaultNS = paremtNamespaceSupport.getURI("");
            if (defaultNS != null && defaultNS.length() > 0)
                super.endPrefixMapping("");
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {

            level--;

            namespaceSupport.popContext();

            if (XMLConstants.XINCLUDE_URI.equals(uri) || XMLConstants.OLD_XINCLUDE_URI.equals(uri)) {

            } else {
                super.endElement(uri, localname, qName);
            }

            if (!topLevelContentHandler && level == 0) {
                sendEndPrefixMappings();
            }
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            namespaceSupport.declarePrefix(prefix, uri);
            super.startPrefixMapping(prefix, uri);
        }

        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
            super.setDocumentLocator(locator);
        }
    }

    public abstract class URIProcessorOutputImpl extends ProcessorImpl.ProcessorOutputImpl {

        private String configInputName;

        public URIProcessorOutputImpl(Class clazz, String name, String configInputName) {
            super(clazz, name);
            this.configInputName = configInputName;
        }

        public OutputCacheKey getKeyImpl(PipelineContext context) {

            if (true) {
                // TEMP, disable caching
                return null;
            } else {
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
        }

        ;
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
