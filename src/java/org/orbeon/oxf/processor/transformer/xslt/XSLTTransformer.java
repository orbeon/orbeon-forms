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
import org.dom4j.Document;
import org.dom4j.Node;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.processor.transformer.URIResolverListener;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.ConstantLocator;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.StaticError;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.*;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.TransformerHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public abstract class XSLTTransformer extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(XSLTTransformer.class);

    public static final String XSLT_URI = "http://www.w3.org/1999/XSL/Transform";
    public static final String XSLT_TRANSFORMER_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xslt-transformer-config";
    public static final String XSLT_PREFERENCES_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xslt-preferences-config";

    // This input determines the JAXP transformer factory class to use
    private static final String INPUT_TRANSFORMER = "transformer";
    // This input determines attributes to set on the TransformerFactory
    private static final String INPUT_ATTRIBUTES = "attributes";

    public XSLTTransformer(String schemaURI) {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, schemaURI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_TRANSFORMER, XSLT_TRANSFORMER_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ATTRIBUTES, XSLT_PREFERENCES_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
                TemplatesInfo templatesInfo = null;
                TransformerHandler transformerHandler = null;
                try {
                    // Get URI references from cache
                    KeyValidity configKeyValidity = getInputKeyValidity(pipelineContext, INPUT_CONFIG);
                    URIReferences uriReferences = getURIReferences(pipelineContext, configKeyValidity);

                    // Get transformer from cache
                    if (uriReferences != null) {
                        // FIXME: this won't depend on the transformer input.
                        KeyValidity stylesheetKeyValidity = createStyleSheetKeyValidity(pipelineContext, configKeyValidity, uriReferences);
                        if (stylesheetKeyValidity != null)
                            templatesInfo = (TemplatesInfo) ObjectCache.instance()
                                    .findValid(pipelineContext, stylesheetKeyValidity.key, stylesheetKeyValidity.validity);
                    }

                    // Get transformer attributes if any
                    Map attributes = null;
                    {
                        // Read attributes input only if connected
                        if (getConnectedInputs().get(INPUT_ATTRIBUTES) != null) {
                            // Read input as an attribute Map and cache it
                            attributes = (Map) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ATTRIBUTES), new CacheableInputReader() {
                                public Object read(PipelineContext context, ProcessorInput input) {
                                    Document preferencesDocument = readInputAsDOM4J(context, input);
                                    OXFPropertiesSerializer.PropertyStore propertyStore = OXFPropertiesSerializer.createPropertyStore(preferencesDocument);
                                    OXFProperties.PropertySet propertySet = propertyStore.getGlobalPropertySet();
                                    return propertySet.getObjectMap();
                                }
                            });
                        }
                    }

                    // Create transformer if we did not find one in cache
                    if (templatesInfo == null) {
                        // Get transformer configuration
                        Node config = readCacheInputAsDOM4J(pipelineContext, INPUT_TRANSFORMER);
                        String transformerClass = XPathUtils.selectStringValueNormalize(config, "/config/class");
                        // Create transformer
                        templatesInfo = createTransformer(pipelineContext, transformerClass, attributes);
                    }

                    // Create transformer handler and set output writer for Saxon
                    StringWriter saxonStringWriter = null;
                    StringErrorListener errorListener = new StringErrorListener(logger);
                    transformerHandler = TransformerUtils.getTransformerHandler(templatesInfo.templates, templatesInfo.transformerClass, attributes);

                    transformerHandler.getTransformer().setURIResolver(new TransformerURIResolver(XSLTTransformer.this, pipelineContext, INPUT_DATA, URLGenerator.DEFAULT_HANDLE_XINCLUDE));
                    transformerHandler.getTransformer().setErrorListener(errorListener);
                    String transformerClassName = transformerHandler.getTransformer().getClass().getName();
                    if (transformerClassName.equals("net.sf.saxon.Controller") || transformerClassName.equals("org.orbeon.saxon.Controller")) {
                        saxonStringWriter = new StringWriter();
                        Object saxonTransformer = transformerHandler.getTransformer();
                        Method getMessageEmitter = saxonTransformer.getClass().getMethod("getMessageEmitter", new Class[]{});
                        Object messageEmitter = getMessageEmitter.invoke(saxonTransformer, new Object[]{});
                        if (messageEmitter == null) {
                            Method makeMessageEmitter = saxonTransformer.getClass().getMethod("makeMessageEmitter", new Class[]{});
                            messageEmitter = makeMessageEmitter.invoke(saxonTransformer, new Object[]{});
                        }
                        Method setWriter = messageEmitter.getClass().getMethod("setWriter", new Class[]{Writer.class});
                        setWriter.invoke(messageEmitter, new Object[]{saxonStringWriter});
                    }

                    final LocationData locDat = getLocationData();
                    final SAXResult sr = new SAXResult(new SimpleForwardingContentHandler(contentHandler) {
                        
                        Locator locator;
                        
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
                        public void setDocumentLocator( final Locator loc ) {
                            locator = loc;
                        }
                        public void startDocument() throws SAXException {
                            if ( ( locator == null || locator.getSystemId() == null ) 
                                  && locDat != null ) {
                                final Locator loc = new ConstantLocator( locDat );
                                super.setDocumentLocator( loc );
                            }
                            super.startDocument();
                        }
                    });
                    if ( locDat != null ) {
                        final String sysID = locDat.getSystemID();
                        sr.setSystemId( sysID );
                        transformerHandler.setSystemId( sysID );
                    }
                    transformerHandler.setResult( sr );

                    // Execute transformation
                    try {
                        if (XSLTTransformer.this.getConnectedInputs().size() > 3) {
                            // When other inputs are connected, they can be read
                            // with the doc() function in XSLT. Reading those
                            // documents might happen before the whole input
                            // document is read, which is not compatible with
                            // our processing model. So in this case, we first
                            // read the data in a SAX store.
                            SAXStore dataSaxStore = new SAXStore();
                            readInputAsSAX(pipelineContext, INPUT_DATA, dataSaxStore);
                            dataSaxStore.replay(transformerHandler);
                        } else {
                            readInputAsSAX(pipelineContext, INPUT_DATA, transformerHandler);
                        }
                    } finally {
                        // Log message from Saxon
                        if (saxonStringWriter != null) {
                            String message = saxonStringWriter.toString();
                            if (message.length() > 0)
                                logger.info(message);
                        }
                    }

                    // Check whether some errors were added
                    if (errorListener.hasErrors()) {
                        final List errors = errorListener.getErrors();
                        if (errors != null) {
                            ValidationException ve = null;
                            for (Iterator i = errors.iterator(); i.hasNext();) {
                                final LocationData currentLocationData = (LocationData) i.next();

                                if (ve == null)
                                    ve = new ValidationException("Errors while executing transformation", currentLocationData);
                                else
                                    ve.addLocationData(currentLocationData);
                            }
                        }
                    }
                } catch (ValidationException e) {
                    throw e;
                } catch (TransformerException e) {
                    final ExtendedLocationData extendedLocationData
                            = StringErrorListener.getTransformerExceptionLocationData(e, templatesInfo.systemId);
                    throw new ValidationException(e, extendedLocationData);
                } catch (Exception e) {
                    if (templatesInfo != null && templatesInfo.systemId != null) {
                        throw new ValidationException(e, new LocationData(templatesInfo.systemId, -1, -1));
                    } else {
                        throw new OXFException(e);
                    }
                }
            }

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            protected CacheKey getLocalKey(PipelineContext context) {
                try {
                    KeyValidity configKeyValidity = getInputKeyValidity(context, INPUT_CONFIG);
                    URIReferences uriReferences = getURIReferences(context, configKeyValidity);
                    if (uriReferences == null || uriReferences.hasDynamicDocumentReferences)
                        return null;
                    List keys = new ArrayList();
                    keys.add(configKeyValidity.key);
                    List allURIReferences = new ArrayList();
                    allURIReferences.addAll(uriReferences.stylesheetReferences);
                    allURIReferences.addAll(uriReferences.documentReferences);
                    for (Iterator i = allURIReferences.iterator(); i.hasNext();) {
                        URIReference uriReference = (URIReference) i.next();
                        keys.add(new InternalCacheKey(XSLTTransformer.this, "xsltURLReference", URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm()));
                    }
                    return new InternalCacheKey(XSLTTransformer.this, keys);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            }

            protected Object getLocalValidity(PipelineContext context) {
                try {
                    KeyValidity configKeyValidity = getInputKeyValidity(context, INPUT_CONFIG);
                    URIReferences uriReferences = getURIReferences(context, configKeyValidity);
                    if (uriReferences == null || uriReferences.hasDynamicDocumentReferences)
                        return null;
                    List validities = new ArrayList();
                    validities.add(configKeyValidity.validity);
                    List allURIReferences = new ArrayList();
                    allURIReferences.addAll(uriReferences.stylesheetReferences);
                    allURIReferences.addAll(uriReferences.documentReferences);
                    for (Iterator i = allURIReferences.iterator(); i.hasNext();) {
                        URIReference uriReference = (URIReference) i.next();
                        Processor urlGenerator = new URLGenerator(URLFactory.createURL(uriReference.context, uriReference.spec));
                        validities.add(((ProcessorOutputImpl) urlGenerator.createOutput(OUTPUT_DATA)).getValidity(context));
                    }
                    return validities;
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private URIReferences getURIReferences(PipelineContext context, KeyValidity configKeyValidity) {
                if (configKeyValidity == null)
                    return null;
                return (URIReferences) ObjectCache.instance().findValid(context, configKeyValidity.key, configKeyValidity.validity);
            }

            private KeyValidity createStyleSheetKeyValidity(PipelineContext context, KeyValidity configKeyValidity, URIReferences uriReferences) {
                try {
                    if (configKeyValidity == null)
                        return null;

                    List keys = new ArrayList();
                    List validities = new ArrayList();
                    keys.add(configKeyValidity.key);
                    validities.add(configKeyValidity.validity);
                    for (Iterator i = uriReferences.stylesheetReferences.iterator(); i.hasNext();) {
                        URIReference uriReference = (URIReference) i.next();
                        URL url = URLFactory.createURL(uriReference.context, uriReference.spec);
                        keys.add(new InternalCacheKey(XSLTTransformer.this, "xsltURLReference", url.toExternalForm()));
                        Processor urlGenerator = new URLGenerator(url);
                        validities.add(((ProcessorOutputImpl) urlGenerator.createOutput(OUTPUT_DATA)).getValidity(context));//FIXME: can we do better? See URL generator.
                    }

                    return new KeyValidity(new InternalCacheKey(XSLTTransformer.this, keys), validities);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            }

            /**
             * Reads the input and creates the JAXP Templates object (wrapped in a
             * Transformer object). While reading the input, figures out the direct
             * dependencies on other files (URIReferences object), and stores
             * these two mappings in cache:
             * <p/>
             * <pre>
             * configKey        -> uriReferences
             * uriReferencesKey -> transformer
             * </pre>
             */
            private TemplatesInfo createTransformer(PipelineContext context, String transformerClass, Map attributes) {
                StringErrorListener errorListener = new StringErrorListener(logger);
                final StylesheetForwardingContentHandler topStylesheetContentHandler = new StylesheetForwardingContentHandler();
                try {
                    // Create transformer
                    final TemplatesInfo templatesInfo = new TemplatesInfo();
                    final List xsltContentHandlers = new ArrayList();
                    {
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
                        templatesInfo.templates = TransformerUtils.getTemplates(stylesheetSAXSource, transformerClass, attributes, errorListener,
                                new TransformerURIResolver(XSLTTransformer.this, context, INPUT_DATA, URLGenerator.DEFAULT_HANDLE_XINCLUDE));
                        TransformerUtils.removeURIResolverListener();
                        templatesInfo.transformerClass = transformerClass;
                        templatesInfo.systemId = topStylesheetContentHandler.getSystemId();
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
                        KeyValidity configKeyValidty = getInputKeyValidity(context, INPUT_CONFIG);
                        if (configKeyValidty != null)
                            ObjectCache.instance().add(context, configKeyValidty.key, configKeyValidty.validity, uriReferences);

                        // Put in cache: (configKey, uriReferences.stylesheetReferences) -> transformer
                        KeyValidity stylesheetKeyValidity = createStyleSheetKeyValidity(context, configKeyValidty, uriReferences);
                        if (stylesheetKeyValidity != null)
                            ObjectCache.instance().add(context, stylesheetKeyValidity.key, stylesheetKeyValidity.validity, templatesInfo);
                    }

                    return templatesInfo;

                } catch (TransformerException e) {
                    final ExtendedLocationData extendedLocationData
                            = StringErrorListener.getTransformerExceptionLocationData(e, topStylesheetContentHandler.getSystemId());

                    final ValidationException ve = new ValidationException(e, extendedLocationData);

                    // Append location data gathered from error listener
                    if (errorListener.hasErrors()) {
                        final List errors = errorListener.getErrors();
                        if (errors != null) {
                            for (Iterator i = errors.iterator(); i.hasNext();) {
                                final LocationData currentLocationData = (LocationData) i.next();
                                ve.addLocationData(currentLocationData);
                            }
                        }
                    }

                    throw ve;
                } catch (Exception e) {
                    if (topStylesheetContentHandler.getSystemId() != null) {
                        if (errorListener.hasErrors()) {
                            // TODO: Check this: will this ever be called?
                            throw new ValidationException(errorListener.getMessages(),
                                    new LocationData(topStylesheetContentHandler.getSystemId(), 0, 0));
                        } else {
                            throw new ValidationException(e,
                                    new LocationData(topStylesheetContentHandler.getSystemId(), 0, 0));
                        }
                    } else {
                        if (errorListener.hasErrors()) {
                            // TODO: Check this: will this ever be called?
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
        private StandaloneContext dummySaxonXPathContext;
        private final NamePool namePool = new NamePool();

        private void initDummySaxonXPathContext() {
            Configuration config = new Configuration();
            config.setHostLanguage(Configuration.XSLT);
            config.setNamePool(namePool);
            dummySaxonXPathContext = new StandaloneContext(config) {
                {
                    // Dummy Function lib that accepts any name
                    setFunctionLibrary(new FunctionLibrary() {
                        public Expression bind(final int nameCode, String uri, String local, final Expression[] staticArgs)  {
                            return new FunctionCall() {
                                {
                                    this.argument = staticArgs;
                                    this.setFunctionNameCode(nameCode);
                                }
                                protected void checkArguments(StaticContext env) {};

                                protected int computeCardinality() {
                                    return this.argument.length;
                                }

                                public ItemType getItemType() {
                                    return Type.BOOLEAN_TYPE;
                                }


                            };
                        }

                        public boolean isAvailable(int fingerprint, String uri, String local, int arity) {
                            return true;
                        }
                    });


                }

                public boolean isAvailable(int fingerprint, String uri, String local, int arity) {
                    return true;
                }


                public String getURIForPrefix(String prefix) {
                    return namespaces.getURI(prefix);
                }

                public boolean isImportedSchema(String namespace) { return true; }

                // Dummy var decl to allow any name
                public VariableDeclaration bindVariable(int fingerprint) throws StaticError {
                    try {
                        return declareVariable("dummy", "dummy");
                    }catch(XPathException e) {
                        throw new StaticError(e);
                    }
                }
            };
        }

        private Locator locator;
        private URIReferences uriReferences = new URIReferences();
        private String systemId;
        private final NamespaceSupport3 namespaces = new NamespaceSupport3();

        public StylesheetForwardingContentHandler() {
            super();
            initDummySaxonXPathContext();
        }

        public StylesheetForwardingContentHandler(ContentHandler contentHandler) {
            super(contentHandler);
            initDummySaxonXPathContext();
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


        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            namespaces.startPrefixMapping(prefix, uri);
            super.startPrefixMapping(prefix, uri);
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            namespaces.startElement();
            // Save system id
            if (systemId == null && locator != null)
                systemId = locator.getSystemId();

            // Handle possible include
            if (XSLT_URI.equals(uri)) {

                // <xsl:include> or <xsl:import>
                if ("include".equals(localname) || "import".equals(localname)) {
                    String href = attributes.getValue("href");
                    URIReference uriReference = new URIReference();
                    uriReference.context = systemId;
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
                        Expression expression = ExpressionTool.make(xpathString, dummySaxonXPathContext, 0, -1, 0);
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


        public void endElement(String uri, String localname, String qName) throws SAXException {
            super.endElement(uri, localname, qName);
            namespaces.endElement();
        }

        public void endDocument() throws SAXException {
            super.endDocument();
        }

        public void startDocument() throws SAXException {
            super.startDocument();
        }

        private void visitExpression(Expression expression) {
            Iterator subExpressionsIterator = expression.iterateSubExpressions();
            boolean foundDocFunction = false;
            if (expression instanceof FunctionCall) {
                String functionName = ((FunctionCall) expression).getDisplayName(namePool);
                if ("doc".equals(functionName) || "document".equals(functionName)) {
                    foundDocFunction = true;
                    // Call to doc(...)
                    if (subExpressionsIterator.hasNext()) {
                        Object value = subExpressionsIterator.next();
                        if (value instanceof StringValue) {
                            // doc(...) call just contains a string, record the URI
                            String uri = ((StringValue) value).getStringValue();
                            // We don't need to worry here about reference to the processor inputs
                            if (!isProcessorInputScheme(uri)) {
                                URIReference uriReference = new URIReference();
                                uriReference.context = systemId;
                                uriReference.spec = uri;
                                uriReferences.documentReferences.add(uriReference);
                            }
                        }
                    } else {
                        // doc(...) call contains something more complex
                        uriReferences.hasDynamicDocumentReferences = true;
                    }
                }
            }

            if (!foundDocFunction) {
                // Recurse in subexpressions
                for (Iterator i = expression.iterateSubExpressions(); i.hasNext();) {
                    visitExpression((Expression) i.next());
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

    private static class TemplatesInfo {
        public Templates templates;
        public String transformerClass;
        public String systemId;
    }
}
