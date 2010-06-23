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
package org.orbeon.oxf.processor.xinclude;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.*;

import javax.xml.transform.sax.SAXSource;
import java.util.Enumeration;
import java.util.Stack;

/**
 * XInclude processor.
 *
 * This processor reads a document on its "config" input that may contain XInclude directives. It
 * produces on its output a resulting document with the XInclude directives processed.
 *
 * For now, this processor only supports <xi:include href="..." parse="xml"/> with no xpointer,
 * encoding, accept, or accept-language attribute. <xi:fallback> is not supported.
 *
 * TODO: Merge caching with URL generator, possibly XSLT transformer. See also XFormsToXHTML processor.
 */
public class XIncludeProcessor extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(XIncludeProcessor.class);

    public XIncludeProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(final String name) {
        final ProcessorOutput output = new URIProcessorOutputImpl(XIncludeProcessor.this, name, INPUT_CONFIG) {
            public void readImpl(final PipelineContext pipelineContext, final XMLReceiver xmlReceiver) {
                /**
                 * The code below reads the input in a SAX store, before replaying the SAX store to the
                 * XIncludeContentHandler.
                 *
                 * This may seem inefficient, but it is necessary (unfortunately) so the URI resolver which will be
                 * called by the XInclude content handler has the right parents in the stack. When we do
                 * readInputAsSAX() here, this might run a processor PR which might be outside of the current
                 * pipeline PI that executed the XInclude processor. When PR run, PI might not be in the processor
                 * stack anymore, because it is possible for PR to be outside of PI. When PR calls a SAX method of
                 * the XInclude handler, there is no executeChildren() that runs, so when the XInclude handler
                 * method is called the parent stack might not include PI, and so reading an input of PI will fail.
                 *
                 * The general rule is that when you receive SAX events, you might not be in the right context.
                 * While the readInput...() method is running you can't rely on having the right context, so you
                 * shouldn't call other readInput...() methods or do anything that relies on the context.
                 *
                 * We don't have this problem with Saxon, because Saxon will first read the input stylesheet and
                 * then processes it. So when the processing happens, the readInput...() methods that reads the
                 * stylesheet has returned.
                 */
//                        final ContentHandler debugContentHandler = new SAXLoggerProcessor.DebugContentHandler(contentHandler);
                final TransformerURIResolver uriResolver = new TransformerURIResolver(XIncludeProcessor.this, pipelineContext, INPUT_CONFIG, false);

                // Try to cache URI references
                // NOTE: Always be careful not to cache refs to TransformerURIResolver. We seem to be fine here.
                final boolean[] wasRead = { false };
                readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        final URIReferences uriReferences = new URIReferences();

                        final SAXStore saxStore = new SAXStore();
                        // TODO: Should be smarter and only buffer when we find a read of input:* (maybe pipeline API should do this automatically)
                        readInputAsSAX(pipelineContext, INPUT_CONFIG, saxStore);
                        try {
                            saxStore.replay(new XIncludeXMLReceiver(pipelineContext, xmlReceiver, uriReferences, uriResolver));
                        } catch (SAXException e) {
                            throw new OXFException(e);
                        }

                        wasRead[0] = true;
                        return uriReferences;
                    }
                });

                // Read if not already read
                if (!wasRead[0]) {
                    final SAXStore saxStore = new SAXStore();
                    // TODO: Should be smarter and only buffer when we find a read of input:* (maybe pipeline API should do this automatically)
                    readInputAsSAX(pipelineContext, INPUT_CONFIG, saxStore);
                    try {
                        saxStore.replay(new XIncludeXMLReceiver(pipelineContext, xmlReceiver, null, uriResolver));
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    public static class XIncludeXMLReceiver extends ForwardingXMLReceiver {

        private boolean processXInclude;
        private PipelineContext pipelineContext;
        private URIProcessorOutputImpl.URIReferences uriReferences;
        private boolean topLevelContentHandler;
        private String xmlBase;
        private NamespaceSupport3 parentNamespaceSupport;

        private Locator currentLocator;
        private OutputLocator outputLocator;
        private TransformerURIResolver uriResolver;
        private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

        private int level;
        private int includeLevel = -1;

        private boolean generateXMLBase;

        public XIncludeXMLReceiver(PipelineContext pipelineContext, XMLReceiver xmlReceiver, URIProcessorOutputImpl.URIReferences uriReferences, TransformerURIResolver uriResolver) {
            this(true, pipelineContext, xmlReceiver, uriReferences, uriResolver, true, null, null, true, new OutputLocator());
        }

        public XIncludeXMLReceiver(PipelineContext pipelineContext, XMLReceiver xmlReceiver, URIProcessorOutputImpl.URIReferences uriReferences, TransformerURIResolver uriResolver, String xmlBase, NamespaceSupport3 parentNamespaceSupport, boolean generateXMLBase, OutputLocator outputLocator) {
            this(true, pipelineContext, xmlReceiver, uriReferences, uriResolver, false, xmlBase, parentNamespaceSupport, generateXMLBase, outputLocator);
        }

        private XIncludeXMLReceiver(boolean processXInclude, PipelineContext pipelineContext, XMLReceiver xmlReceiver, URIProcessorOutputImpl.URIReferences uriReferences, TransformerURIResolver uriResolver, boolean topLevelContentHandler, String xmlBase, NamespaceSupport3 paremtNamespaceSupport, boolean generateXMLBase, OutputLocator outputLocator) {
            super(xmlReceiver);
            this.processXInclude = processXInclude;
            this.pipelineContext = pipelineContext;
            this.uriReferences = uriReferences;
            this.uriResolver = uriResolver;
            this.topLevelContentHandler = topLevelContentHandler;
            this.xmlBase = xmlBase;
            this.parentNamespaceSupport = paremtNamespaceSupport;
            this.generateXMLBase = generateXMLBase;
            this.outputLocator = outputLocator;
        }

        public void startDocument() throws SAXException {
            // Update locator stack
            outputLocator.pushLocator(currentLocator);
            // Make sure only once startDocument() is produced
            if (topLevelContentHandler) {
                super.startDocument();
            }
        }

        public void endDocument() throws SAXException {
            // Make sure only once endDocument() is produced
            if (topLevelContentHandler) {
                super.endDocument();
            }
            // Restore locator stack
            outputLocator.popLocator();
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

            namespaceSupport.startElement();

            if (!topLevelContentHandler && level == 0) {
                // Clean-up namespace mappings
                sendClearStartPrefixMappings();
            }

            if (!topLevelContentHandler && level == 0 && generateXMLBase) {
                // Add or replace xml:base attribute
                attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", xmlBase);
            }

            if (processXInclude) {
                if (XMLConstants.XINCLUDE_URI.equals(uri) || XMLConstants.OLD_XINCLUDE_URI.equals(uri)) {
                    // Found XInclude namespace

                    // Warn upon obsolete namespace URI
                    if (XMLConstants.OLD_XINCLUDE_URI.equals(uri))
                        logger.warn("Using incorrect XInclude namespace URI: '" + uri + "'; should use '" + XMLConstants.XINCLUDE_URI + "' at " + new LocationData(outputLocator).toString());

                    if ("include".equals(localname)) {
                        // Start inclusion

    //                    inInclude = true;

                        final String href = attributes.getValue("href");
                        final String parse = attributes.getValue("parse");

                        // Whether to create/update xml:base attribute or not
                        final boolean generateXMLBase;
                        {
                            final String disableXMLBase = attributes.getValue(XMLConstants.XXINCLUDE_NAMESPACE_URI, "omit-xml-base");
                            generateXMLBase = !"true".equals(disableXMLBase);
                        }

                        if (parse != null && !parse.equals("xml"))
                            throw new ValidationException("Invalid 'parse' attribute value: " + parse, new LocationData(outputLocator));

                        String systemId = null;
                        try {
                            // Get SAXSource
                            final String base = outputLocator == null ? null : outputLocator.getSystemId();
                            final SAXSource source = (SAXSource) uriResolver.resolve(href, base);
                            final XMLReader xmlReader = source.getXMLReader();
                            xmlReader.setContentHandler(new XIncludeXMLReceiver(pipelineContext, getXMLReceiver(), uriReferences, uriResolver, source.getSystemId(), namespaceSupport, generateXMLBase, outputLocator));

                            // Keep URI reference
                            if (uriReferences != null)
                                uriReferences.addReference(base, href, null, null, null, null);

                            // Read document
                            systemId = source.getSystemId();
                            xmlReader.parse(new InputSource(systemId)); // Yeah, the SAX API doesn't make much sense

                        } catch (Exception e) {
                            // Resource error, must go to fallback if possible
                            if (systemId != null)
                                throw new OXFException("Error while parsing: " + systemId, e);
                            else
                                throw new OXFException(e);
                        }

                    } else if ("fallback".equals(localname)) {
                        // TODO
                    } else {
                        throw new ValidationException("Invalid XInclude element: " + localname, new LocationData(outputLocator));
                    }

                } else if (includeLevel != -1 && level == includeLevel) {
                    // We are starting a sibling of an included element

                    // Send adjusted prefix mappings
                    sendRestoreStartPrefixMappings();

                    super.startElement(uri, localname, qName, attributes);
                } else {
                    super.startElement(uri, localname, qName, attributes);
                }
            } else {
                // No XInclude processing
                super.startElement(uri, localname, qName, attributes);
            }

            level++;
        }

        private void sendClearStartPrefixMappings() throws SAXException {
            // NOTE: This is called before handling start of root element of included document
            for (Enumeration e = parentNamespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals("") && namespaceSupport.getURI(namespacePrefix) == null) // handle only if not declared again on root element
                    super.startPrefixMapping(namespacePrefix, "");
            }

            final String defaultNS = parentNamespaceSupport.getURI("");
            if (defaultNS != null && defaultNS.length() > 0 && namespaceSupport.getURI("") == null) // handle only if not declared again on root element
                super.startPrefixMapping("", "");
        }

        private void sendClearEndPrefixMappings() throws SAXException {
            // NOTE: This is called after handling end of root element of included document
            for (Enumeration e = parentNamespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals("") && namespaceSupport.getURI(namespacePrefix) == null) // handle only if not declared again on root element
                    super.endPrefixMapping(namespacePrefix);
            }

            final String defaultNS = parentNamespaceSupport.getURI("");
            if (defaultNS != null && defaultNS.length() > 0 && namespaceSupport.getURI("") == null) // handle only if not declared again on root element
                super.endPrefixMapping("");
        }

        private void sendRestoreStartPrefixMappings() throws SAXException {
            for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                    super.startPrefixMapping(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
            }

            final String defaultNS = namespaceSupport.getURI("");
            if (defaultNS != null && defaultNS.length() > 0)
                super.startPrefixMapping("", defaultNS);
        }

        private void sendRestoreEndPrefixMappings() throws SAXException {
            for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                    super.endPrefixMapping(namespacePrefix);
            }

            final String defaultNS = namespaceSupport.getURI("");
            if (defaultNS != null && defaultNS.length() > 0)
                super.endPrefixMapping("");
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {

            level--;

            final boolean isEndingInclude;
            if (processXInclude && (XMLConstants.XINCLUDE_URI.equals(uri) || XMLConstants.OLD_XINCLUDE_URI.equals(uri))) {
                if ("include".equals(localname)) {
                    isEndingInclude = true;
                } else {
                    isEndingInclude = false;
                }
            } else {
                isEndingInclude = false;
                super.endElement(uri, localname, qName);
            }

            if (isEndingInclude) {
                // Remember that we included at this level
                includeLevel = level;
            } else if (includeLevel != -1) {
                if (level == includeLevel) {
                    // Clear adjusted namespace mappings
                    sendRestoreEndPrefixMappings();
                } else if (level < includeLevel) {
                    // Clear include level indicator
                    includeLevel = -1;
                } else if (!topLevelContentHandler && level == 0) {
                    // Clean-up namespace mappings around root element
                    sendClearEndPrefixMappings();
                }
            }

            namespaceSupport.endElement();
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {

            namespaceSupport.startPrefixMapping(prefix, uri);
            super.startPrefixMapping(prefix, uri);
        }

        public void setDocumentLocator(Locator locator) {

            // Keep track of current locator
            this.currentLocator = locator;

            // Set output locator to be our own locator if we are at the top-level
            if (topLevelContentHandler) {
                super.setDocumentLocator(outputLocator);
            }
        }
    }

    /**
     * This is the Locator object passed to the output. It supports a stack of input Locator objects in order to
     * correctly report location information of the included documents.
     */
    public static class OutputLocator implements Locator {

        private Stack<Locator> locators = new Stack<Locator>();

        public OutputLocator() {
        }

        public void pushLocator(Locator locator) {
            locators.push(locator);
        }

        public void popLocator() {
            locators.pop();
        }

        public String getPublicId() {
            final Locator locator = getCurrentLocator();
            return (locator == null) ? null : locator.getPublicId();
        }

        public String getSystemId() {
            final Locator locator = getCurrentLocator();
            return (locator == null) ? null : locator.getSystemId();
        }

        public int getLineNumber() {
            final Locator locator = getCurrentLocator();
            return (locator == null) ? -1 : locator.getLineNumber();
        }

        public int getColumnNumber() {
            final Locator locator = getCurrentLocator();
            return (locator == null) ? -1 : locator.getColumnNumber();
        }

        private Locator getCurrentLocator() {
            return (locators.size() == 0) ? null : locators.peek();
        }

        public int getSize() {
            return locators.size();
        }
    }
}
