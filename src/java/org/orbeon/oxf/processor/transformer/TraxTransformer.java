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

import org.dom4j.Node;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.xml.ProcessorOutputXMLReader;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;
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

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                try {
                    // Inputs
                    ProcessorInput configInput = getInputByName(INPUT_CONFIG);
                    ProcessorInput typeInput = getInputByName(INPUT_TRANSFORMER_CONFIG);
                    ProcessorInput dataInput = getInputByName(INPUT_DATA);

                    // Create combined key for (transformer type, config)
                    CacheKey typeCacheKey = transformerFactory == null
                            ? (CacheKey) getInputKey(context, typeInput)
                            : (CacheKey) new InternalCacheKey(TraxTransformer.this, "transformerType",
                                    transformerFactory.getClass().getName());
                    CacheKey configCacheKey = getInputKey(context, configInput);
                    InternalCacheKey combinedInputCacheKey = typeCacheKey == null || configCacheKey == null ? null :
                            new InternalCacheKey(TraxTransformer.this,
                            Arrays.asList(new CacheKey[] {typeCacheKey, configCacheKey}));

                    // Create combined validity for (transformer type, config)
                    Object typeValidity = transformerFactory == null
                            ? getInputValidity(context, typeInput) : new Long(0);
                    Object configValidity = getInputValidity(context, configInput);
                    Object combinedInputValidity = typeValidity == null || configValidity == null ? null
                            : Arrays.asList(new Object[] {typeValidity, configValidity});

                    // Get templates from cache, or create it
                    Templates templates = (Templates) ObjectCache.instance().findValid
                            (context, combinedInputCacheKey, combinedInputValidity);
                    if (templates == null) {

                        // Create template handler
                        TemplatesHandler templatesHandler;
                        if (transformerFactory == null) {
                            Node config = readCacheInputAsDOM4J(context, INPUT_TRANSFORMER_CONFIG);
                            final String transformerClass = XPathUtils.selectStringValueNormalize(config, "/config/class");
                            transformerFactory = TransformerUtils.getFactory(transformerClass);
                        }
                        // TODO: If we were to use setURIResolver(), be careful to null it afterwards so that no ref to TransformerURIResolver occurs
                        templatesHandler = transformerFactory.newTemplatesHandler();

                        // Create template
                        readInputAsSAX(context, configInput, templatesHandler);
                        templates = templatesHandler.getTemplates();

                        // Save template in cache
                        ObjectCache.instance().add(context, combinedInputCacheKey, combinedInputValidity, templates);
                    }

                    // Perform transformation
                    Transformer transformer = templates.newTransformer();
                    transformer.setURIResolver(new TransformerURIResolver(TraxTransformer.this, context, INPUT_DATA, URLGenerator.DEFAULT_HANDLE_XINCLUDE));
                    transformer.transform(new SAXSource
                            (new ProcessorOutputXMLReader(context, dataInput.getOutput()), new InputSource()),
                             new SAXResult(contentHandler));
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
