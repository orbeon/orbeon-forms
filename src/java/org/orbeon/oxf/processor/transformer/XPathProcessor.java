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
package org.orbeon.oxf.processor.transformer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class XPathProcessor extends ProcessorImpl {

    private LocationData locationData;

    public XPathProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {

               Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                    public Object read(final org.orbeon.oxf.pipeline.api.PipelineContext context, final ProcessorInput input) {
                        Document config = readInputAsDOM4J(context, INPUT_CONFIG);

                        // Get declared namespaces
                        Map namespaces = new HashMap();
                        for (Iterator i = config.getRootElement().selectNodes("/config/namespace").iterator(); i.hasNext();) {
                            Element namespaceElement = (Element) i.next();
                            namespaces.put(namespaceElement.attributeValue("prefix"),
                                    namespaceElement.attributeValue("uri"));
                        }

                        // Create xpath object   (Jaxen)
//                        XPath xpath = XPathCache.createCacheXPath(context, (String) config.selectObject("string(/config/xpath)"));
//                        xpath.setNamespaceContext(new SimpleNamespaceContext(namespaces));
//                        xpath.setFunctionContext(new OXFFunctionContext());
//                        return xpath;

                        return  new Config(namespaces, (String) config.selectObject("string(/config/xpath)"));
                    }
                });

//                List results = xpath.selectNodes(readCacheInputAsDOM4J(context, INPUT_DATA));
                DocumentWrapper wrapper = new DocumentWrapper(readCacheInputAsDOM4J(context, INPUT_DATA), null);
                PooledXPathExpression xpath = null;
                try {
                    xpath = XPathCache.getXPathExpression(context, wrapper, config.getExpression(), config.getNamespaces());
                    List results = xpath.evaluate();
                    contentHandler.startDocument();
                    // WARNING: Here we break the rule that processors must output valid XML documents, because
                    // we potentially output several root nodes. This works because the XPathProcessor is always
                    // connected to an aggregator, which adds a new root node.
                    for (Iterator i = results.iterator(); i.hasNext();) {
                        Object result = i.next();
                        if ( result == null ) continue;
                        final String strVal;
                        if ( result instanceof org.dom4j.Element || result instanceof org.dom4j.Document ) {
                            final org.dom4j.Element elt = result instanceof org.dom4j.Element 
                                              ? ( org.dom4j.Element )result 
                                              : ( ( org.dom4j.Document )result ).getRootElement();
                            final String sid = Dom4jUtils.makeSystemId( elt  );
                            final DOMGenerator domGenerator = new DOMGenerator
                                ( elt, "xpath result doc", DOMGenerator.ZeroValidity, sid );
                            final ProcessorOutput domOutput = domGenerator.createOutput( OUTPUT_DATA );
                            domOutput.read(context, new ForwardingContentHandler(contentHandler) {
                                public void startDocument() {
                                }

                                public void endDocument() {
                                }
                            });
                            continue;
                        } else if ( result instanceof String ) {
                            strVal = ( String )result;
                        } else if ( result instanceof Double ) {
                            final double d = ( ( Double )result ).doubleValue();
                            strVal = XMLUtils.removeScientificNotation( d );
                        } else {
                            String message = "Unsupported type returned by XPath expression: "
                                    + (result == null ? "null" : result.getClass().getName());
                            throw new ValidationException(message, locationData);
                        }
                        final char[] ch = strVal.toCharArray();
                        final int len = strVal.length();
                        contentHandler.characters( ch, 0, len );
                    }
                    contentHandler.endDocument();
                }catch (XPathException xpe) {
                    throw new OXFException(xpe);
                } catch (SAXException e) {
                    throw new ValidationException(e, locationData);
                }finally{
                    if(xpath != null)
                        xpath.returnToPool();
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    protected static class Config {
        private Map namespaces;
        private String expression;

        public Config(Map namespaces, String expression) {
            this.namespaces = namespaces;
            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public Map getNamespaces() {
            return namespaces;
        }

        public void setNamespaces(Map namespaces) {
            this.namespaces = namespaces;
        }

    }
}
