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
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.Iterator;

public class XMLProcessorRegistry extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(XMLProcessorRegistry.class);

    public static final String PROCESSOR_REGISTRY_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/processor/processor-registry-config";

    public XMLProcessorRegistry() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, PROCESSOR_REGISTRY_CONFIG_NAMESPACE_URI));
    }

    public void start(final PipelineContext ctxt) {
        try {
            final Node cfg = readInputAsDOM4J(ctxt, INPUT_CONFIG);

            for (Iterator i = XPathUtils.selectIterator(cfg, "/processors//processor"); i.hasNext();) { // support multiple nesting levels
                Element processorElement = (Element) i.next();

                // Extract processor name
                final QName processorQName = extractProcessorQName(processorElement);
                final String processorURI = extractProcessorURI(processorElement);// [BACKWARD COMPATIBILITY]
                if (processorQName == null && processorURI == null)
                    throw new OXFException("Missing or empty processor name!");

                if (processorQName != null)
                    logger.debug("Binding name: " + Dom4jUtils.qNameToExplodedQName(processorQName));
                if (processorURI != null)
                    logger.debug("Binding name: " + processorURI);

                // Defined as a class
                Node classDef = XPathUtils.selectSingleNode(processorElement, "class");
                if (classDef != null) {
                    final String className = XPathUtils.selectStringValueNormalize(classDef, "@name");
                    if (logger.isDebugEnabled())
                        logger.debug("To class: " + className);

                    final String defaultName = (processorQName != null) ? Dom4jUtils.qNameToExplodedQName(processorQName) : processorURI;
                    final QName defaultQName = (processorQName != null) ? processorQName : new QName(processorURI);

                    ProcessorFactory processorFactory = new ProcessorFactory() {
                        public Processor createInstance() {
                            try {
                                Processor processor = (Processor) Class.forName(className).newInstance();
                                processor.setName(defaultQName);
                                return processor;
                            } catch (ClassNotFoundException e) {
                                throw new OXFException("Cannot load processor '" + defaultName
                                        + "' because the class implementing this processor ('"
                                        + className + "') cannot be found");
                            } catch (NoClassDefFoundError e) {
                                throw new OXFException("Cannot load processor '" + defaultName
                                        + "' because it needs a class that cannot be loaded: '"
                                        + e.getMessage() + "'");
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    };

                    if (processorQName != null)
                        ProcessorFactoryRegistry.bind(processorQName, processorFactory);
                    if (processorURI != null)
                        ProcessorFactoryRegistry.bind(processorURI, processorFactory);
                }

                // Defined based on an other processor (instantiation)
                final Element instantiationDef = (Element) XPathUtils.selectSingleNode(processorElement, "instantiation");
                if (instantiationDef != null) {

                    ProcessorFactory processorFactory = new ProcessorFactory() {
                        public Processor createInstance() {
                            try {
                                // Find base processor
                                final QName processorQName = extractProcessorQName(instantiationDef);
                                final String processorURI = extractProcessorURI(instantiationDef);// [BACKWARD COMPATIBILITY]
                                if (processorQName == null && processorURI == null)
                                    throw new OXFException("Missing or empty processor name!");

                                if (processorQName != null)
                                    logger.debug("Binding name: " + Dom4jUtils.qNameToExplodedQName(processorQName));
                                if (processorURI != null)
                                    logger.debug("Binding name: " + processorURI);

                                final QName defaultQName = (processorQName != null) ? processorQName : new QName(processorURI);

                                Processor baseProcessor = ((processorQName != null)
                                        ? ProcessorFactoryRegistry.lookup(processorQName)
                                        : ProcessorFactoryRegistry.lookup(processorURI)).createInstance();
                                // Override the name - can this have unexpected consequences?
                                baseProcessor.setName(defaultQName);

                                for (Iterator j = XPathUtils.selectIterator(instantiationDef, "input"); j.hasNext();) {
                                    final Element inputElement = (Element) j.next();
                                    final String name = XPathUtils.selectStringValueNormalize(inputElement, "@name");
                                    final String href = XPathUtils.selectStringValueNormalize(inputElement, "@href");

                                    if (href != null) {
                                        // Connect to URL generator
                                        Processor urlGenerator = PipelineUtils.createURLGenerator(href);
                                        PipelineUtils.connect(urlGenerator, OUTPUT_DATA, baseProcessor, name);
                                    } else {
                                        final ProcessorInput processorConfigInput = getInputByName(INPUT_CONFIG);
                                        final Object processorConfigValidity = getInputValidity(ctxt, processorConfigInput);
                                        // We must have some XML in the <input> tag
                                        final Element childElement = (Element) inputElement.elements().get(0);
                                        final String sid = Dom4jUtils.makeSystemId(childElement);
                                        final DOMGenerator domGenerator = PipelineUtils.createDOMGenerator
                                                (childElement, "input from registry", processorConfigValidity, sid);
                                        PipelineUtils.connect(domGenerator, OUTPUT_DATA, baseProcessor, name);
                                    }
                                }
                                return baseProcessor;
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    };

                    if (processorQName != null)
                        ProcessorFactoryRegistry.bind(processorQName, processorFactory);
                    if (processorURI != null)
                        ProcessorFactoryRegistry.bind(processorURI, processorFactory);
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static QName extractProcessorQName(Element processorElement) {
        return Dom4jUtils.extractAttributeValueQName(processorElement, "name");
    }

    public static String extractProcessorURI(Element processorElement) {
        // [BACKWARD COMPATIBILITY] Extract URI
        String uri = XPathUtils.selectStringValueNormalize(processorElement, "@uri");
        if (uri == null || uri.trim().length() == 0)
            return null;
        else
            return uri.trim();
    }
}
