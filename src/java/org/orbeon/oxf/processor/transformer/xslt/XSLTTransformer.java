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
package org.orbeon.oxf.processor.transformer.xslt;

import org.apache.log4j.Logger;
import org.dom4j.Node;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.processor.transformer.URIResolverListener;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.type.BuiltInSchemaFactory;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.SchemaType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.*;

import javax.xml.transform.Templates;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.TransformerHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public abstract class XSLTTransformer extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(XSLTTransformer.class);

    public static final String XSLT_URI = "http://www.w3.org/1999/XSL/Transform";
    public static final String XSLT_TRANSFORMER_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xslt-transformer-config";

    private static final String INPUT_TRANSFORMER_CONFIG = "transformer";
    private static final String DEFAULT_TRANSFORMER_CLASS_STRING = "DEFAULT";

    public static final String TRANSFORMER_PROPERTY = "transformer";

    public XSLTTransformer(String schemaURI) {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, schemaURI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_TRANSFORMER_CONFIG, XSLT_TRANSFORMER_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                Transformer transformer = null;
                TransformerHandler transformerHandler = null;
                try {

                    // Get uri references from cache
                    KeyValidity configKeyValidity = getConfigKeyValidity(context);
                    URIReferences uriReferences = getURIReferences(context, configKeyValidity);

                    // Get transformer from cache
                    if (uriReferences != null) {
                        KeyValidity stylesheetKeyValidity = createStyleSheetKeyValidity(context, configKeyValidity, uriReferences);
                        if (stylesheetKeyValidity.key != null && stylesheetKeyValidity.validity != null)
                            transformer = (Transformer) ObjectCache.instance()
                                    .findValid(context, stylesheetKeyValidity.key, stylesheetKeyValidity.validity);
                    }

                    // Create transformer if we did not find one in cache
                    if (transformer == null) {
                        // Get transformer configuration
                        Node config = readCacheInputAsDOM4J(context, INPUT_TRANSFORMER_CONFIG);
                        String transformerClass = XPathUtils.selectStringValueNormalize(config, "/config/class");
                        // Create transformer
                        transformer = createTransformer(context, transformerClass);
                    }

                    // Create transformer handler and set output writer for Saxon
                    StringWriter saxonStringWriter = null;
                    StringErrorListener errorListener = new StringErrorListener(logger);
                    transformerHandler = TransformerUtils.getTransformerHandler(transformer.templates, transformer.transformerType);
                    transformerHandler.getTransformer().setURIResolver(new TransformerURIResolver(XSLTTransformer.this, context));
                    transformerHandler.getTransformer().setErrorListener(errorListener);
                    String transformerClassName = transformerHandler.getTransformer().getClass().getName();
                    if (transformerClassName.equals("net.sf.saxon.Controller") || transformerClassName.equals("org.orbeon.saxon.Controller")) {
                        saxonStringWriter = new StringWriter();
                        Object saxonTransformer = transformerHandler.getTransformer();
                        Method getMessageEmitter = saxonTransformer.getClass().getMethod("getMessageEmitter", new Class[] {});
                        Object messageEmitter = getMessageEmitter.invoke(saxonTransformer, new Object[] {});
                        if (messageEmitter == null) {
                            Method makeMessageEmitter = saxonTransformer.getClass().getMethod("makeMessageEmitter", new Class[] {});
                            messageEmitter = makeMessageEmitter.invoke(saxonTransformer, new Object[] {});
                        }
                        Method setWriter = messageEmitter.getClass().getMethod("setWriter", new Class[] {Writer.class});
                        setWriter.invoke(messageEmitter, new Object[] {saxonStringWriter});
                    }

                    transformerHandler.setResult(new SAXResult(new SimpleForwardingContentHandler(contentHandler) {
                        // Saxon happens to issue such prefix mappings from time to time. Those
                        // cause issues later down the chain, and anyway serialize to incorrect XML
                        // if xmlns:xmlns="..." gets generated. This appears to happen when Saxon
                        // uses the Copy() instruction. It may be that the source is then
                        // incorrect, but we haven't traced this further. It may also simply be a
                        // bug in Saxon.
                        public void startPrefixMapping(String s, String s1) throws SAXException {
                            if ("xmlns".equals(s)) {
                                return;
                            }
                            super.startPrefixMapping(s, s1);
                        }
                    }));

                    // Execute transformation
                    try {
                        if (XSLTTransformer.this.getInputs().size() > 3) {
                            // When other inputs are connected, they can be read
                            // with the doc() function in XSLT. Reading those
                            // documents might happen before the whole input
                            // document is read, which is not compatible with
                            // our processing model. So in this case, we first
                            // read the data in a SAX store.
                            SAXStore dataSaxStore = new SAXStore();
                            readInputAsSAX(context, INPUT_DATA, dataSaxStore);
                            dataSaxStore.replay(transformerHandler);
                        } else {
                            readInputAsSAX(context, INPUT_DATA, transformerHandler);
                        }
                    } finally {
                        // Log message from Saxon
                        if (saxonStringWriter != null) {
                            String message =  saxonStringWriter.toString();
                            if (message.length() > 0)
                                logger.info(message);
                        }
                    }
                } catch (ValidationException e) {
                    throw e;
                } catch (Exception e) {
                    if (transformer != null && transformer.systemId != null) {
                        throw new ValidationException(e, new LocationData(transformer.systemId, 0, 0));
                    } else {
                        throw new OXFException(e);
                    }
                }
            }

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            protected InternalCacheKey getLocalKey(PipelineContext context) {
                try {
                    KeyValidity configKeyValidity = getConfigKeyValidity(context);
                    URIReferences uriReferences = getURIReferences(context, configKeyValidity);
                    if (uriReferences == null || uriReferences.hasDynamicDocumentReferences || configKeyValidity.key == null)
                        return null;
                    List keys = new ArrayList();
                    keys.add(configKeyValidity.key);
                    List allURIReferences = new ArrayList();
                    allURIReferences.addAll(uriReferences.stylesheetReferences);
                    allURIReferences.addAll(uriReferences.documentReferences);
                    for (Iterator i = allURIReferences.iterator(); i.hasNext();) {
                        URIReference uriReference = (URIReference) i.next();
                        keys.add(new InternalCacheKey(XSLTTransformer.this, "urlDocument", URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm()));
                    }
                    return new InternalCacheKey(XSLTTransformer.this, keys);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            }

            protected Object getLocalValidity(PipelineContext context) {
                try {
                    KeyValidity configKeyValidity = getConfigKeyValidity(context);
                    URIReferences uriReferences = getURIReferences(context, configKeyValidity);
                    if (uriReferences == null || uriReferences.hasDynamicDocumentReferences || configKeyValidity.validity == null)
                        return null;
                    List validities = new ArrayList();
                    validities.add(configKeyValidity.validity);
                    List allURIReferences = new ArrayList();
                    allURIReferences.addAll(uriReferences.stylesheetReferences);
                    allURIReferences.addAll(uriReferences.documentReferences);
                    for (Iterator i = allURIReferences.iterator(); i.hasNext();) {
                        URIReference uriReference = (URIReference) i.next();
                        Processor urlGenerator = new URLGenerator(URLFactory.createURL(uriReference.context, uriReference.spec));
                        validities.add(((ProcessorOutputImpl)urlGenerator.createOutput(OUTPUT_DATA)).getValidity(context));
                    }
                    return validities;
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private KeyValidity getConfigKeyValidity(PipelineContext context) {
                KeyValidity result = new KeyValidity();
                ProcessorInput input = getInputByName(INPUT_CONFIG);
                Cacheable cacheable = (Cacheable) input.getOutput();
                result.key = cacheable.getKey(context) == null ? null :
                        new InputCacheKey(input, cacheable.getKey(context));
                result.validity = (result.key != null) ? cacheable.getValidity(context) : null;
                return result;
            }

            private URIReferences getURIReferences(PipelineContext context, KeyValidity configKeyValidity) {
                return configKeyValidity.key != null && configKeyValidity.validity != null ?
                        (URIReferences) ObjectCache.instance().findValid
                        (context, configKeyValidity.key, configKeyValidity.validity) : null;
            }

            private KeyValidity createStyleSheetKeyValidity(PipelineContext context, KeyValidity configKeyValidity, URIReferences uriReferences) {
                try {
                    KeyValidity result = new KeyValidity();
                    if (configKeyValidity.key != null && configKeyValidity.validity != null) {
                        List keys = new ArrayList();
                        List validities = new ArrayList();
                        keys.add(configKeyValidity.key);
                        validities.add(configKeyValidity.validity);
                        for (Iterator i = uriReferences.stylesheetReferences.iterator(); i.hasNext();) {
                            URIReference uriReference = (URIReference) i.next();
                            URL url = URLFactory.createURL(uriReference.context, uriReference.spec);
                            keys.add(new InternalCacheKey(XSLTTransformer.this, "urlDocument", url.toExternalForm()));
                            Processor urlGenerator = new URLGenerator(url);
                            validities.add(((ProcessorOutputImpl)urlGenerator.createOutput(OUTPUT_DATA)).getValidity(context));
                        }
                        result.key = new InternalCacheKey(XSLTTransformer.this, keys);
                        result.validity = validities;
                    }
                    return result;
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            }

            /**
             * Reads the input and creates the JAXP Templates object (wrapped in a
             * Transformer object). While reading the input, figures out the direct
             * dependencies on other files (URIReferences object), and stores
             * these two mappings in cache:
             *
             * <pre>
             * configKey        -> uriReferences
             * uriReferencesKey -> transformer
             * </pre>
             */
            private Transformer createTransformer(PipelineContext context, String transformerClass) {
                StringErrorListener errorListener = new StringErrorListener(logger);
                final StylesheetForwardingContentHandler topStylesheetContentHandler = new StylesheetForwardingContentHandler();
                try {
                    // Create transformer
                    final Transformer transformer = new Transformer();
                    final List xsltContentHandlers = new ArrayList();
                    {
                        // Transformer type
                        final String transformerType;
                        {
                            if (DEFAULT_TRANSFORMER_CLASS_STRING.equals(transformerClass)) {
                                // Default is requested, try property
                                String defaultTransformerType = getPropertySet().getString(TRANSFORMER_PROPERTY);
                                // For backward compatibility, we accept the string "interpreter" in addition to class names
                                if (defaultTransformerType == null || "interpreter".equals(defaultTransformerType))
                                    transformerType = TransformerUtils.DEFAULT_TYPE;
                                else
                                    transformerType = defaultTransformerType;
                            } else {
                                // This must be a class name
                                transformerType = transformerClass;
                            }
                        }

                        // Create SAXSource adding our forwarding content handler
                        final SAXSource stylesheetSAXSource;
                        {
                            xsltContentHandlers.add(topStylesheetContentHandler);
                            XMLReader xmlReader = new ProcessorOutputXMLReader(context, getInputByName(INPUT_CONFIG).getOutput()) {
                                public void setContentHandler(ContentHandler handler) {
                                    super.setContentHandler(new TeeContentHandler(Arrays.asList(new Object[]{
                                        topStylesheetContentHandler, handler})));
                                }
                            };
                            stylesheetSAXSource = new SAXSource(xmlReader, new InputSource());
                        }

                        // Put listener in context that will be called by URI resolved
                        context.setAttribute(PipelineContext.XSLT_STYLESHEET_URI_LISTENER, new URIResolverListener() {
                            public ContentHandler getContentHandler() {
                                StylesheetForwardingContentHandler contentHandler = new StylesheetForwardingContentHandler();
                                xsltContentHandlers.add(contentHandler);
                                return contentHandler;
                            }
                        });
                        transformer.templates = TransformerUtils.getTemplates(stylesheetSAXSource, transformerType, errorListener,
                                new TransformerURIResolver(XSLTTransformer.this, context));
                        TransformerUtils.removeURIResolverListener();
                        transformer.transformerType = transformerType;
                        transformer.systemId = topStylesheetContentHandler.getSystemId();
                    }

                    // Update cache
                    {
                        // Create uriReferences
                        URIReferences uriReferences = new URIReferences();
                        for (Iterator i = xsltContentHandlers.iterator(); i.hasNext();) {
                            StylesheetForwardingContentHandler contentHandler = (StylesheetForwardingContentHandler) i.next();
                            uriReferences.hasDynamicDocumentReferences = uriReferences.hasDynamicDocumentReferences
                                    || contentHandler.getURIReferences().hasDynamicDocumentReferences;
                            uriReferences.stylesheetReferences.addAll
                                    (contentHandler.getURIReferences().stylesheetReferences);
                            uriReferences.documentReferences.addAll
                                    (contentHandler.getURIReferences().documentReferences);
                        }

                        // Put in cache: configKey -> uriReferences
                        KeyValidity configKeyValidty = getConfigKeyValidity(context);
                        if (configKeyValidty.key != null && configKeyValidty.validity != null)
                            ObjectCache.instance().add(context, configKeyValidty.key, configKeyValidty.validity, uriReferences);

                        // Put in cache: (configKey, uriReferences.stylesheetReferences) -> transformer
                        KeyValidity stylesheetKeyValidity = createStyleSheetKeyValidity(context, configKeyValidty, uriReferences);
                        if (stylesheetKeyValidity.key != null && stylesheetKeyValidity.validity != null)
                            ObjectCache.instance().add(context, stylesheetKeyValidity.key, stylesheetKeyValidity.validity, transformer);
                    }

                    return transformer;

                } catch (Exception e) {
                    if (topStylesheetContentHandler.getSystemId() != null) {
                        if (errorListener.hasErrors()) {
                            throw new ValidationException(errorListener.getMessages(),
                                    new LocationData(topStylesheetContentHandler.getSystemId(), 0, 0));
                        } else {
                            throw new ValidationException(e,
                                    new LocationData(topStylesheetContentHandler.getSystemId(), 0, 0));
                        }
                    } else {
                        if (errorListener.hasErrors()) {
                            throw new OXFException(errorListener.getMessages());
                        } else {
                            throw new OXFException(e);
                        }
                    }
                }
            }

        };
        addOutput(name, output);
        return output;
    }

    /**
     * This forwarding content handler intercepts all the references to external
     * resources from the XSLT stylesheet. There can be external references in
     * an XSLT stylesheet when the &lt;xsl:include&gt; or &lt;xsl:import&gt;
     * elements are used, or when there is an occurrence of the
     * <code>document()</code> function in an XPath expression.
     *
     * @see #getURIReferences()
     */
    private static class StylesheetForwardingContentHandler extends ForwardingContentHandler {

        /**
         * This is context that will resolve any prefix, function, and variable.
         * It is just used to parse XPath expression and get an AST.
         */
        static final StandaloneContext dummySaxonXPathContext;
        static {
            Configuration config = new Configuration();
            config.setTargetNamePool(new NamePool() {
                public synchronized int allocate(String prefix, String uri, String localName) { return 1; }
                public SchemaType getSchemaType(int fingerprint) { return BuiltInSchemaFactory.getSchemaType(Type.STRING); }
            });
            dummySaxonXPathContext = new StandaloneContext(config) {
                public String getURIForPrefix(String prefix) { return "dummy"; }
                public boolean isImportedSchema(String namespace) { return true; }
                public Expression bindFunction(final String qname, final Expression[] arguments) throws XPathException {
                    return new FunctionCall() {
                        { setArguments(arguments); }
                        protected void checkArguments(StaticContext env) {}
                        public String getName() { return qname; }
                        protected int computeCardinality() { return arguments.length; }
                        public ItemType getItemType() { return null; }
                    };
                }
                public VariableDeclaration bindVariable(int fingerprint) throws XPathException {
                    return declareVariable("dummy", "dummy");
                }
            };
        }

        private Locator locator;
        private URIReferences uriReferences = new URIReferences();
        private String systemId;

        public StylesheetForwardingContentHandler() {
            super();
        }

        public StylesheetForwardingContentHandler(ContentHandler contentHandler) {
            super(contentHandler);
        }

        public URIReferences getURIReferences() {
            return uriReferences;
        }

        public String getSystemId() {
            return systemId;
        }

        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
            super.setDocumentLocator(locator);
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            // Save system id
            if (systemId == null && locator != null)
                systemId = locator.getSystemId();

            // Handle possible include
            if (XSLT_URI.equals(uri)) {

                // <xsl:include> or <xsl:import>
                if ("include".equals(localname) || "import".equals(localname)) {
                    String href = attributes.getValue("href");
                    URIReference uriReference = new URIReference();
                    uriReference.context = locator.getSystemId();
                    uriReference.spec = href;
                    uriReferences.stylesheetReferences.add(uriReference);
                }

                // Find XPath expression on current element
                String xpathString;
                {
                    xpathString = attributes.getValue("test");
                    if (xpathString == null)
                        xpathString = attributes.getValue("select");
                }

                // Analyze XPath expression to find dependencies on URIs
                if (xpathString != null) {
                    try {
                        Expression expression = ExpressionTool.make(xpathString, dummySaxonXPathContext, 0, -1);
                        visitExpression(expression);
                    } catch (XPathException e) {
                        logger.error("Original exception", e);
                        throw new ValidationException("XPath syntax exception (" + e.getMessage() + ") for expression: "
                                + xpathString, new LocationData(locator));
                    }
                }
            }
            super.startElement(uri, localname, qName, attributes);
        }

        private void visitExpression(Expression expression) {
            Expression[] subExpressions = expression.getSubExpressions();
            boolean foundDocFunction = false;
            if (expression instanceof FunctionCall) {
                String functionName = ((FunctionCall) expression).getName();
                if ("doc".equals(functionName) || "document".equals(functionName)) {
                    foundDocFunction = true;
                    // Call to doc(...)
                    if (subExpressions.length == 1 && subExpressions[0] instanceof StringValue) {
                        // doc(...) call just contains a string, record the URI
                        String uri = ((StringValue) subExpressions[0]).getStringValue();
                        // We don't need to worry here about reference to the processor inputs
                        if (!(uri.startsWith("oxf:") && !uri.startsWith("oxf:/"))) {
                            URIReference uriReference = new URIReference();
                            uriReference.context = locator.getSystemId();
                            uriReference.spec = uri;
                            uriReferences.documentReferences.add(uriReference);
                        }
                    } else {
                        // doc(...) call contains something more complex
                        uriReferences.hasDynamicDocumentReferences = true;
                    }
                }
            }

            if (!foundDocFunction) {
                // Recurse in subexpressions
                for (int i = 0; i < subExpressions.length; i++) {
                    visitExpression(subExpressions[i]);
                }
            }
        }
    }

    private static class URIReference {
        public String context;
        public String spec;
    }

    private static class URIReferences {
        public List stylesheetReferences = new ArrayList();
        public List documentReferences = new ArrayList();

        /**
         * Is true if and only if an XPath expression with a call to the
         * <code>document()</code> function was found and the value of the
         * attribute to the <code>document()</code> function call cannot be
         * determined without executing the stylesheet. When this happens, the
         * result of the stylesheet execution cannot be cached.
         */
        public boolean hasDynamicDocumentReferences = false;
    }

    private static class Transformer {
        public Templates templates;
        public String transformerType;
        public String systemId;
    }

    private static class KeyValidity {
        public CacheKey key;
        public Object validity;
    }

}
