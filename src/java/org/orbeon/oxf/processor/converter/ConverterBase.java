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
package org.orbeon.oxf.processor.converter;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XPathUtils;

/**
 * Base class for converters.
 */
public abstract class ConverterBase extends ProcessorImpl {

    public static final String STANDARD_TEXT_CONVERTER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/converter/standard-text";

    public static final String DEFAULT_ENCODING = TransformerUtils.DEFAULT_OUTPUT_ENCODING;
    public static final boolean DEFAULT_OMIT_XML_DECLARATION = false;
    public static final boolean DEFAULT_INDENT = true;
    public static final int DEFAULT_INDENT_AMOUNT = 0;

    //private static Logger logger = LoggerFactory.createLogger(ConverterBase.class);

    protected ConverterBase() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, getConfigSchemaNamespaceURI()));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    /**
     * Return the default content type for this converter. Must be overridden by subclasses.
     */
    protected abstract String getDefaultContentType();

    /**
     * Return the namespace URI of the schema validating the config input. Must be overridden by subclasses.
     */
    protected abstract String getConfigSchemaNamespaceURI();

    protected Config readConfig(PipelineContext pipelineContext) {
        return (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG),
            new CacheableInputReader() {
                public Object read(PipelineContext pipelineContext, ProcessorInput input) {
                    final Element configElement = readInputAsDOM4J(pipelineContext, input).getRootElement();
                    try {
                        final Config config = new Config();

                        config.method = XPathUtils.selectStringValueNormalize(configElement, "/config/method");
                        config.contentType = XPathUtils.selectStringValueNormalize(configElement, "/config/content-type");
                        config.encoding = XPathUtils.selectStringValueNormalize(configElement, "/config/encoding");
                        config.version = XPathUtils.selectStringValueNormalize(configElement, "/config/version");
                        config.publicDoctype = XPathUtils.selectStringValueNormalize(configElement, "/config/public-doctype");
                        config.systemDoctype = XPathUtils.selectStringValueNormalize(configElement, "/config/system-doctype");
                        config.omitXMLDeclaration = ProcessorUtils.selectBooleanValue(configElement, "/config/omit-xml-declaration", DEFAULT_OMIT_XML_DECLARATION);
                        String standaloneString = XPathUtils.selectStringValueNormalize(configElement, "/config/standalone");
                        config.standalone = (standaloneString == null) ? null : new Boolean(standaloneString);
                        config.indent = ProcessorUtils.selectBooleanValue(configElement, "/config/indent", DEFAULT_INDENT);
                        final Integer indentAmount = XPathUtils.selectIntegerValue(configElement, "/config/indent-amount");
                        if (indentAmount != null) config.indentAmount = indentAmount.intValue();

                        return config;
                    } catch (Exception e) {
                        throw new OXFException(e);
                    }
                }
            });
    }

    /**
     * Represent the complete converter configuration.
     */
    protected static class Config {
        public String method;
        public String contentType;
        public String encoding = DEFAULT_ENCODING;
        public String version;
        public String publicDoctype;
        public String systemDoctype;
        public boolean omitXMLDeclaration = DEFAULT_OMIT_XML_DECLARATION;
        public Boolean standalone;
        public boolean indent = DEFAULT_INDENT;
        public int indentAmount = DEFAULT_INDENT_AMOUNT;
    }

    /**
     * Implement the content type determination algorithm.
     *
     * @param config               current HTTP converter configuration
     * @param defaultContentType   content type to return if none can be found
     * @return content type determined
     */
    protected static String getContentType(Config config, String defaultContentType) {
        return (config.contentType != null) ? config.contentType : defaultContentType;
    }

    /**
     * Implement the encoding determination algorithm.
     *
     * @param config               current HTTP converter configuration
     * @param defaultEncoding      encoding to return if none can be found
     * @return encoding determined
     */
    protected static String getEncoding(Config config, String defaultEncoding) {
        return (config.encoding != null) ? config.encoding : defaultEncoding;
    }
}
