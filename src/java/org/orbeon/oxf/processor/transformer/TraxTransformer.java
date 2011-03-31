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
package org.orbeon.oxf.processor.transformer;

import org.dom4j.Node;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.InputSource;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import java.util.Arrays;

/**
 * Generic TrAX transformer. The XSLTTransformer should be used for XSLT,
 * because it handles includes and caching better.
 */
public class TraxTransformer extends ProcessorImpl {

    public static final String TRAX_TRANSFORMER_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/trax-transformer-config";
    private static final String INPUT_TRANSFORMER_CONFIG = "transformer";
    private SAXTransformerFactory transformerFactory;

    /**
     * It is possible to derive from this class and set a schema for the config
     * input. The default is no schema.
     */
    public TraxTransformer() {
        this(null);
    }

    public TraxTransformer(String schemaURI) {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, schemaURI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_TRANSFORMER_CONFIG, TRAX_TRANSFORMER_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void setTransformerFactory(SAXTransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new CacheableTransformerOutputImpl(TraxTransformer.this, name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    // Inputs
                    final ProcessorInput configInput = getInputByName(INPUT_CONFIG);
                    final ProcessorInput typeInput = getInputByName(INPUT_TRANSFORMER_CONFIG);
                    final ProcessorInput dataInput = getInputByName(INPUT_DATA);

                    // Create combined key for (transformer type, config)
                    final CacheKey typeCacheKey = transformerFactory == null
                            ? (CacheKey) getInputKey(context, typeInput)
                            : (CacheKey) new InternalCacheKey(TraxTransformer.this, "transformerType",
                                    transformerFactory.getClass().getName());
                    final CacheKey configCacheKey = getInputKey(context, configInput);
                    final InternalCacheKey combinedInputCacheKey = typeCacheKey == null || configCacheKey == null ? null :
                            new InternalCacheKey(TraxTransformer.this,
                            Arrays.asList(typeCacheKey, configCacheKey));

                    // Create combined validity for (transformer type, config)
                    final Object typeValidity = transformerFactory == null
                            ? getInputValidity(context, typeInput) : new Long(0);
                    final Object configValidity = getInputValidity(context, configInput);
                    final Object combinedInputValidity = typeValidity == null || configValidity == null ? null
                            : Arrays.asList(typeValidity, configValidity);

                    // Get templates from cache, or create it
                    Templates templates = (Templates) ObjectCache.instance().findValid
                            (combinedInputCacheKey, combinedInputValidity);
                    if (templates == null) {

                        // Create template handler
                        if (transformerFactory == null) {
                            final Node config = readCacheInputAsDOM4J(context, INPUT_TRANSFORMER_CONFIG);
                            final String transformerClass = XPathUtils.selectStringValueNormalize(config, "/config/class");
                            transformerFactory = TransformerUtils.getFactory(transformerClass);
                        }
                        // TODO: If we were to use setURIResolver(), be careful to null it afterwards so that no ref to TransformerURIResolver occurs
                        final TemplatesHandler templatesHandler = transformerFactory.newTemplatesHandler();

                        // Create template
                        readInputAsSAX(context, configInput, new ForwardingXMLReceiver(templatesHandler));
                        templates = templatesHandler.getTemplates();

                        // Save template in cache
                        ObjectCache.instance().add(combinedInputCacheKey, combinedInputValidity, templates);
                    }

                    // Perform transformation
                    final Transformer transformer = templates.newTransformer();
                    transformer.setURIResolver(new TransformerURIResolver(TraxTransformer.this, context, INPUT_DATA, XMLUtils.ParserConfiguration.PLAIN));

                    final SAXResult saxResult = new SAXResult(xmlReceiver);
                    saxResult.setLexicalHandler(xmlReceiver);

                    transformer.transform(new SAXSource(new ProcessorOutputXMLReader(context, dataInput.getOutput()), new InputSource()), saxResult);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
