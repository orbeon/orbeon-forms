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
package org.orbeon.oxf.processor.converter;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;
import org.orbeon.oxf.xml.NamespaceSupport3;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * The QName converter converts elements' local names and/or namespace URIs.
 *
 * NOTE: This implementation probably needs to do more work to detect and adjust to namespace
 * declarations conflicts.
 *
 * TODO: attributes should probably also be updated, or removed!
 */
public class QNameConverter extends ProcessorImpl {

    public static final String QNAME_CONVERTER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/converter/qname";

    public QNameConverter() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, QNAME_CONVERTER_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(QNameConverter.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                // Read config input
                final Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                    public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                        Config result = new Config();

                        Element configElement = readInputAsDOM4J(context, input).getRootElement();

                        {
                            Element matchURIElement = configElement.element("match").element("uri");
                            result.matchURI = (matchURIElement == null) ? null : matchURIElement.getStringValue();
                            Element matchPrefixElement = configElement.element("match").element("prefix");
                            result.matchPrefix = (matchPrefixElement == null) ? null : matchPrefixElement.getStringValue();
                        }

                        {
                            Element replaceURIElement = configElement.element("replace").element("uri");
                            result.replaceURI = (replaceURIElement == null) ? null : replaceURIElement.getStringValue();
                            Element replacePrefixElement = configElement.element("replace").element("prefix");
                            result.replacePrefix = (replacePrefixElement == null) ? null : replacePrefixElement.getStringValue();
                        }

                        return result;
                    }
                });

                // Do the conversion
                readInputAsSAX(context, INPUT_DATA, new ForwardingXMLReceiver(xmlReceiver) {

                    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

                    @Override
                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                        namespaceSupport.startElement();
                        if (config.matchURI == null || config.matchURI.equals(uri)) {
                            int colonIndex = qName.indexOf(':');
                            String prefix = (colonIndex == -1) ? "" : qName.substring(0, colonIndex);
                            if (config.matchPrefix == null || config.matchPrefix.equals(prefix)) {

                                // Match: replace prefix or URI or both
                                String newURI = (config.replaceURI == null) ? uri : config.replaceURI;
                                String newQName;
                                if (config.replacePrefix == null) {
                                    newQName = qName;
                                } else if (colonIndex == -1) {
                                    final StringBuilder sb= new StringBuilder(config.replacePrefix.length() + qName.length() + 1);
                                    sb.append(config.replacePrefix);
                                    sb.append(':');
                                    sb.append(qName);
                                    newQName = sb.toString();
                                } else {
                                    final StringBuilder sb= new StringBuilder(config.replacePrefix.length() + qName.length());
                                    sb.append(config.replacePrefix);
                                    sb.append(qName.substring(colonIndex));
                                    newQName = sb.toString();
                                }

                                checkNamespace(uri, newURI, prefix, (config.replacePrefix == null) ? prefix : config.replacePrefix, true);

                                super.startElement(newURI, localname, newQName, attributes);
                            } else {
                                // No match
                                super.startElement(uri, localname, qName, attributes);
                            }
                        } else {
                            // No match
                            super.startElement(uri, localname, qName, attributes);
                        }
                    }

                    @Override
                    public void endElement(String uri, String localname, String qName) throws SAXException {
                        if (config.matchURI == null || config.matchURI.equals(uri)) {
                            int colonIndex = qName.indexOf(':');
                            String prefix = (colonIndex == -1) ? "" : qName.substring(0, colonIndex);
                            if (config.matchPrefix == null || config.matchPrefix.equals(prefix)) {

                                // Match: replace prefix or URI or both
                                String newURI = (config.replaceURI == null) ? uri : config.replaceURI;
                                String newQName;
                                if (config.replacePrefix == null) {
                                    newQName = qName;
                                } else if (colonIndex == -1) {
                                    final StringBuilder sb= new StringBuilder(config.replacePrefix.length() + qName.length() + 1);
                                    sb.append(config.replacePrefix);
                                    sb.append(':');
                                    sb.append(qName);
                                    newQName = sb.toString();
                                } else {
                                    final StringBuilder sb= new StringBuilder(config.replacePrefix.length() + qName.length());
                                    sb.append(config.replacePrefix);
                                    sb.append(qName.substring(colonIndex));
                                    newQName = sb.toString();
                                }

                                super.endElement(newURI, localname, newQName);

                                checkNamespace(uri, newURI, prefix, (config.replacePrefix == null) ? prefix : config.replacePrefix, false);
                            } else {
                                // No match
                                super.endElement(uri, localname, qName);
                            }
                        } else {
                            // No match
                            super.endElement(uri, localname, qName);
                        }
                        namespaceSupport.endElement();
                    }

                    @Override
                    public void startPrefixMapping(String prefix, String uri) throws SAXException {
                        namespaceSupport.startPrefixMapping(prefix, uri);
                        super.startPrefixMapping(prefix, uri);
                    }

                    @Override
                    public void endPrefixMapping(String prefix) throws SAXException {
                        super.endPrefixMapping(prefix);
                    }

                    private void checkNamespace(String matchURI, String newURI, String matchPrefix, String newPrefix, boolean start) throws SAXException {
                        if (matchURI.equals(newURI) && !matchPrefix.equals(newPrefix)) {
                            // Changing prefixes but keeping URI
                            if (!isURIInScopeForPrefix(newPrefix, newURI)) {
                                // new prefix -> URI not in scope
                                if (start) {
                                    super.startPrefixMapping(newPrefix, newURI);
                                    if (namespaceSupport.getURI(newPrefix) == null)
                                        namespaceSupport.declarePrefix(newPrefix, newURI);
                                } else {
                                    super.endPrefixMapping(newPrefix);
                                }
                            }
                        }
                    }

                    private boolean isURIInScopeForPrefix(String prefix, String uri) {
                        String inScopeURIForPrefix = namespaceSupport.getURI(prefix);
                        return (inScopeURIForPrefix != null) && inScopeURIForPrefix.equals(uri);
                    }
                });
            }
        };
        addOutput(name, output);
        return output;
    }

    private static class Config {

        public String matchPrefix; // null means all prefixes are matched
        public String matchURI;    // null means all URIs are matched

        public String replacePrefix; // null means don't replace the prefix
        public String replaceURI;    // null means don't replace the URI
    }
}
