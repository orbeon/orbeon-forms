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
package org.orbeon.oxf.processor.generator;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.exolab.castor.mapping.Mapping;
import org.exolab.castor.xml.Marshaller;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.DigestState;
import org.orbeon.oxf.processor.impl.DigestTransformerOutputImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.ParserAdapter;

import javax.xml.transform.dom.DOMSource;
import java.io.StringReader;
import java.util.*;

/**
 * The Bean generator generates an XML document based on:
 *
 * o a JavaBean
 * o a configuration (config and mapping inputs)
 *
 * NOTE: This processor is deprecated. Use ScopeGenerator instead.
 */
public class BeanGenerator extends ProcessorImpl {

    public static final String BEAN_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/bean";
    public static final String INPUT_MAPPING = "mapping";

    private static Logger logger = LoggerFactory.createLogger(BeanGenerator.class);

    public BeanGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, BEAN_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MAPPING));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    protected Config getConfig(PipelineContext context) {
        return (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG),
                new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Document dom = readInputAsDOM4J(context, input);
                        try {
                            Config config2 = new Config();
                            for (Iterator i = XPathUtils.selectIterator(dom, "/config/attribute"); i.hasNext();) {
                                Element el = (Element) i.next();
                                config2.addAttribute(el.getTextTrim());
                            }

                            for (Iterator i = XPathUtils.selectIterator(dom, "/config/source"); i.hasNext();) {
                                String s = ((Element) i.next()).getTextTrim();
                                if (s.equalsIgnoreCase("request"))
                                    config2.addSource(Config.REQUEST);
                                else if (s.equalsIgnoreCase("session"))
                                    config2.addSource(Config.SESSION);
                                else
                                    throw new OXFException("Wrong source type: must be either request or session");
                            }
                            return config2;
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }
                });
    }

    protected Mapping getMapping(PipelineContext context) {
        return (Mapping) readCacheInputAsObject(context, getInputByName(INPUT_MAPPING),
                new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        try {
//                            TransformerHandler identity = TransformerUtils.getXMLIdentityTransformerHandler();
//                            Result result = new StreamResult();
//                            identity.setResult(result);
//
//                            readInputAsSAX(context, input, identity);
//                            logger.warn(result.toString());

                            Document dom = readInputAsDOM4J(context, input);

                            Mapping mapping = new Mapping();
                            mapping.loadMapping(new InputSource(new StringReader(Dom4jUtils.domToString(dom))));

                            return mapping;
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }
                });
    }

    protected void readBean(PipelineContext context, Config config, Mapping mapping, XMLReceiver xmlReceiver) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context in BeanGenerator");
        try {
            xmlReceiver.startDocument();
            String rootElementName = "beans";
            xmlReceiver.startElement("", rootElementName, rootElementName, XMLUtils.EMPTY_ATTRIBUTES);

            // Initialize Castor
            ParserAdapter adapter = new ParserAdapter(XMLUtils.newSAXParser(XMLUtils.ParserConfiguration.PLAIN).getParser());
            adapter.setContentHandler(xmlReceiver);
            Marshaller marshaller = new Marshaller(adapter);
            marshaller.setMarshalAsDocument(false);
            marshaller.setMapping(mapping);

            for (Iterator atts = config.getAttributesIterator(); atts.hasNext();) {
                String attName = (String) atts.next();
                Object bean = getBean(attName, config.getSourcesIterator(), externalContext);
                if (bean == null) {
                    // Create empty element
                    if (logger.isInfoEnabled())
                        logger.info("Bean " + attName + " is null");
                    xmlReceiver.startElement("", attName, attName, XMLUtils.EMPTY_ATTRIBUTES);
                    xmlReceiver.endElement("", attName, attName);
                } else if (bean instanceof org.w3c.dom.Document) {
                    // W3C Document: send as-is
                    TransformerUtils.sourceToSAX(new DOMSource((org.w3c.dom.Document) bean), new EmbeddedDocumentXMLReceiver(xmlReceiver));
                } else {
                    // Serialize with Castor
                    if (logger.isDebugEnabled())
                        logger.debug("Serializing bean" + attName + " value=" + bean);
                    marshaller.setRootElement(attName);
                    marshaller.marshal(bean);
                }
            }
            xmlReceiver.endElement("", rootElementName, rootElementName);
            xmlReceiver.endDocument();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new DigestTransformerOutputImpl(BeanGenerator.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                try {
                    State state = (State) getFilledOutState(pipelineContext);
                    state.beanStore.replay(xmlReceiver);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            protected byte[] computeDigest(PipelineContext pipelineContext, DigestState digestState) {
                if (digestState.digest == null) {
                    fillOutState(pipelineContext, digestState);
                }
                return digestState.digest;
            }

            protected boolean fillOutState(PipelineContext pipelineContext, DigestState digestState) {
                State state = (State) digestState;
                if (state.beanStore == null) {
                    state.beanStore = new SAXStore();
                    final XMLUtils.DigestContentHandler digester = new XMLUtils.DigestContentHandler("MD5");
                    final TeeXMLReceiver tee = new TeeXMLReceiver(Arrays.asList(state.beanStore, digester));
                    readBean(pipelineContext, getConfig(pipelineContext), getMapping(pipelineContext), tee);
                    state.digest = digester.getResult();
                }
                return true;
            }
        };
        addOutput(name, output);
        return output;
    }

    private Object getBean(String name, Iterator sources, ExternalContext externalContext) {
        Object bean = null;
        ExternalContext.Request request = externalContext.getRequest();
        for (; sources.hasNext();) {
            Integer source = (Integer) sources.next();
            if (Config.REQUEST.equals(source))
                bean = request.getAttributesMap().get(name);
            else if (Config.SESSION.equals(source))
                bean = externalContext.getSession(true).getAttributesMap().get(name);
            if (bean != null)
                return bean;
        }
        // If sources is empty, try the request by default
        if (bean == null)
            return request.getAttributesMap().get(name);
        else
            return bean;
    }


    private static class Config {
        public static final Integer REQUEST = new Integer(0);
        public static final Integer SESSION = new Integer(1);

        List attributes;
        List sources;

        public Config() {
            this.attributes = new ArrayList();
            this.sources = new ArrayList();
        }

        public Config(List attributes, List sources) {
            this.attributes = attributes;
            this.sources = sources;
        }

        public void addAttribute(String attribute) {
            attributes.add(attribute);
        }

        public void addSource(Integer source) {
            if (REQUEST.equals(source) || SESSION.equals(source))
                sources.add(source);
            else
                throw new OXFException("Adding wrong value to source list");
        }

        public Iterator getAttributesIterator() {
            return attributes.iterator();
        }

        public Iterator getSourcesIterator() {
            return sources.iterator();
        }

    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State extends DigestState {
        public SAXStore beanStore;
    }
}