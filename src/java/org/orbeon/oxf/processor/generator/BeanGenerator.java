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
package org.orbeon.oxf.processor.generator;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.exolab.castor.mapping.Mapping;
import org.exolab.castor.xml.Marshaller;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.ParserAdapter;

import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BeanGenerator extends ProcessorImpl {

    public static final String BEAN_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/bean";
    public static final String INPUT_MAPPING = "mapping";

    private static Logger logger = LoggerFactory.createLogger(BeanGenerator.class);

    private static Long DEFAULT_VALIDITY = new Long(0);
    private InternalCacheKey DEFAULT_LOCAL_KEY = new InternalCacheKey(this, "constant", "constant");

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
                            mapping.loadMapping(new InputSource(new StringReader(XMLUtils.domToString(dom))));

                            return mapping;
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }
                });
    }

    protected void readBean(PipelineContext context, Config config, Mapping mapping, ContentHandler contentHandler) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context in BeanGenerator");
        try {
            contentHandler.startDocument();
            String rootElementName = "beans";
            contentHandler.startElement("", rootElementName, rootElementName, XMLUtils.EMPTY_ATTRIBUTES);

            // Initialize castor
            ParserAdapter adapter = new ParserAdapter(XMLUtils.newSAXParser().getParser());
            adapter.setContentHandler(contentHandler);
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
                    contentHandler.startElement("", attName, attName, XMLUtils.EMPTY_ATTRIBUTES);
                    contentHandler.endElement("", attName, attName);
                } else if (bean instanceof org.w3c.dom.Document) {
                    // W3C Document: send as-is
                    Transformer identity = TransformerUtils.getIdentityTransformer();
                    identity.transform(new DOMSource((org.w3c.dom.Document) bean), new SAXResult(new ForwardingContentHandler(contentHandler) {
                        public void startDocument() {}
                        public void endDocument() {}
                    }));
                } else {
                    // Serialize with Castor
                    if (logger.isDebugEnabled())
                        logger.debug("Serializing bean" + attName + " value=" + bean);
                    marshaller.setRootElement(attName);
                    marshaller.marshal(bean);
                }
            }
            contentHandler.endElement("", rootElementName, rootElementName);
            contentHandler.endDocument();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    State state = (State) getState(context);
                    if (state.beanStore == null)
                        computeState(context, state);

                    state.beanStore.replay(contentHandler);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            protected CacheKey getLocalKey(PipelineContext context) {
                return DEFAULT_LOCAL_KEY;
            }

            protected Object getLocalValidity(PipelineContext context) {
                State state = (State) getState(context);
                if (state.beanStore == null)
                    computeState(context, state);

                return state.validity;
            }

            private void computeState(PipelineContext context, State state) {
                state.beanStore = new SAXStore();
                XMLUtils.DigestContentHandler dch = new XMLUtils.DigestContentHandler("MD5");
                TeeContentHandler tee = new TeeContentHandler(Arrays.asList(new Object[]{state.beanStore, dch}));
                readBean(context, getConfig(context), getMapping(context), tee);
                state.digest = dch.getResult();

                // Compute validity if possible
                OutputCacheKey outputCacheKey = getKeyImpl(context);
                if (outputCacheKey != null) {
                    Cache cache = ObjectCache.instance();
                    DigestValidity digestValidity = (DigestValidity) cache.findValid(context, outputCacheKey, DEFAULT_VALIDITY);
                    if (digestValidity != null && state.digest.equals(digestValidity.digest)) {
                        state.validity = digestValidity.lastModified;
                    } else {
                        Long currentValidity = new Long(System.currentTimeMillis());
                        cache.add(context, outputCacheKey, DEFAULT_VALIDITY, new DigestValidity(state.digest, currentValidity));
                        state.validity = currentValidity;
                    }
                }
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

    /**
     * We store in the state the request document (output of this processor) and
     * its key. This information is stored to be reused by readImpl() after a
     * getKeyImpl() in the same pipeline context, or vice versa.
     */
    private static class State {
        public SAXStore beanStore;
        public byte[] digest;
        public Object validity;
    }

    private static class DigestValidity {
        public DigestValidity(byte[] digest, Long lastModified) {
            this.digest = digest;
            this.lastModified = lastModified;
        }

        public byte[] digest;
        public Long lastModified;
    }
}