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
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.pipeline.PipelineConfig;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.processor.serializer.legacy.HTMLSerializer;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.transformer.xupdate.XUpdateConstants;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.*;

import java.net.MalformedURLException;
import java.util.*;

public class PageFlowControllerProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(PageFlowControllerProcessor.class);

    public final static String INPUT_CONTROLLER = "controller";
    public final static String CONTROLLER_NAMESPACE_URI = "http://www.orbeon.com/oxf/controller";
    private final static Document TRUE_DOCUMENT = new NonLazyUserDataDocument();
    private final static Document FALSE_DOCUMENT = new NonLazyUserDataDocument();
    private final static NamespaceMapping NAMESPACES_WITH_XSI_AND_XSLT;
    public final static String EXTRACT_INSTANCE_XPATH
            = "/*/*[local-name() = 'instance' and namespace-uri() = '" + XFormsConstants.XFORMS_NAMESPACE_URI + "']/*[1]";

    // External resources
    private final static String REVERSE_SETVALUES_XSL = "oxf:/ops/pfc/setvalue-reverse.xsl";
    private final static String REWRITE_XSL = "oxf:/ops/pfc/rewrite.xsl";
    private final static String XFORMS_XML_SUBMISSION_XPL = "oxf:/ops/pfc/xforms-xml-submission.xpl";

    // Instance passing configuration
    private final static String INSTANCE_PASSING_REDIRECT = "redirect";
    private final static String INSTANCE_PASSING_FORWARD = "forward";
    private final static String INSTANCE_PASSING_REDIRECT_PORTAL = "redirect-exit-portal";
    private final static String DEFAULT_INSTANCE_PASSING = INSTANCE_PASSING_REDIRECT;

    // Attributes
    private final static String VERSIONED_ATTRIBUTE = "versioned";

    // Properties
    private static final String INSTANCE_PASSING_PROPERTY_NAME = "instance-passing";
    private static final String EPILOGUE_PROPERTY_NAME = "epilogue";
    private static final String NOT_FOUND_PROPERTY_NAME = "not-found";
    private static final String XFORMS_SUBMISSION_MODEL_PROPERTY_NAME = "xforms-submission-model";

    public static final String XFORMS_SUBMISSION_PATH_PROPERTY_NAME = "xforms-submission-path";
    public static final String XFORMS_SUBMISSION_PATH_DEFAULT_VALUE = "/xforms-server-submit";
    public static final String PATH_MATCHERS = "path-matchers"; // used by PageFlowController

    static {
        Element trueConfigElement = new NonLazyUserDataElement( "config");
        trueConfigElement.setText("true");
        TRUE_DOCUMENT.setRootElement(trueConfigElement);

        Element falseConfigElement = new NonLazyUserDataElement("config");
        falseConfigElement.setText("false");
        FALSE_DOCUMENT.setRootElement(falseConfigElement);

        final Map<String, String> mapping = new HashMap<String, String>();
        mapping.put(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
        mapping.put(XMLConstants.XSLT_PREFIX, XMLConstants.XSLT_NAMESPACE_URI);

        NAMESPACES_WITH_XSI_AND_XSLT = new NamespaceMapping(mapping);
    }

    public PageFlowControllerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONTROLLER, CONTROLLER_NAMESPACE_URI));
    }

    public void start(PipelineContext pipelineContext) {
        final PageFlow pageFlow = (PageFlow) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONTROLLER), new CacheableInputReader() {
            public Object read(final PipelineContext context, ProcessorInput input) {
                final Document controllerDocument = readInputAsDOM4J(context, INPUT_CONTROLLER);
                final Object controllerValidity = getInputValidity(context, getInputByName(INPUT_CONTROLLER));
                final StepProcessorContext stepProcessorContext = new StepProcessorContext(controllerValidity);
                LocationData locationData = (LocationData) controllerDocument.getRootElement().getData();
                final String controllerContext = locationData == null ? null : locationData.getSystemID();

                // Get global "parameters" of the controller, fall back to properties
                final String globalInstancePassing; {
                    final String attributeValue = controllerDocument.getRootElement().attributeValue(INSTANCE_PASSING_PROPERTY_NAME);
                    globalInstancePassing = attributeValue != null ? attributeValue
                            : getPropertySet().getString(INSTANCE_PASSING_PROPERTY_NAME, DEFAULT_INSTANCE_PASSING);
                }
                final boolean globalIsVersioned; {
                    final String attributeValue = controllerDocument.getRootElement().attributeValue(VERSIONED_ATTRIBUTE);
                    // NOTE: We use a global property, not an oxf:page-flow scoped one
                    globalIsVersioned = attributeValue != null ? Boolean.valueOf(attributeValue) : URLRewriterUtils.isResourcesVersioned();
                }
                final String epilogueURL;
                final Element epilogueElement;
                {
                    epilogueElement = controllerDocument.getRootElement().element("epilogue");
                    epilogueURL = epilogueElement != null ? epilogueElement.attributeValue("url")
                        : getPropertySet().getString(EPILOGUE_PROPERTY_NAME);
                }
                final String notFoundPipeline = getPropertySet().getString(NOT_FOUND_PROPERTY_NAME);
                final String notFoundPageId;
                {
                    final Element notFoundHandlerElement = controllerDocument.getRootElement().element("not-found-handler");
                    notFoundPageId = notFoundHandlerElement != null ? notFoundHandlerElement.attributeValue("page") : null;
                }
//                final String errorPageId; {
//                    Element errorHandlerElement = controllerDocument.getRootElement().element("error-handler");
//                    errorPageId = errorHandlerElement != null ? errorHandlerElement.attributeValue("page") : null;
//                }

                final List<URLRewriterUtils.PathMatcher> pathMatchers = new ArrayList<URLRewriterUtils.PathMatcher>();

                // XForms Submission page
                {
                    final String xformsSubmissionPath = getPropertySet().getString(XFORMS_SUBMISSION_PATH_PROPERTY_NAME, XFORMS_SUBMISSION_PATH_DEFAULT_VALUE);
                    final String xformsSubmissionModel = getPropertySet().getStringOrURIAsString(XFORMS_SUBMISSION_MODEL_PROPERTY_NAME);
                    if ((xformsSubmissionPath == null && xformsSubmissionModel != null) || (xformsSubmissionPath != null && xformsSubmissionModel == null)) {
                        throw new OXFException("Only one of properties " + XFORMS_SUBMISSION_PATH_PROPERTY_NAME + " and " + XFORMS_SUBMISSION_MODEL_PROPERTY_NAME + " is set.");
                    }
                    if (xformsSubmissionPath != null) {
                        final Element firstPageElement = controllerDocument.getRootElement().element("page");
                        if (firstPageElement != null) {
                            final List<Element> allElements = Dom4jUtils.elements(controllerDocument.getRootElement());
                            final int firstPageElementIndex = allElements.indexOf(firstPageElement);
                            final Element newElement = Dom4jUtils.createElement("page", CONTROLLER_NAMESPACE_URI);
                            newElement.addAttribute("path-info", xformsSubmissionPath);
                            newElement.addAttribute("model", xformsSubmissionModel);
                            allElements.add(firstPageElementIndex, newElement);
                        }
                    }
                }

                // Go through all pages to get mapping
                final Map<String, Element> pageIdToPageElement = new HashMap<String, Element>();
                final Map<String, String> pageIdToPathInfo = new HashMap<String, String>();
                final Map<String, String> pageIdToXFormsModel = new HashMap<String, String>();
                final Map<String, Document> pageIdToSetvaluesDocument = new HashMap<String, Document>();
                final int pageCount = controllerDocument.getRootElement().elements("page").size();

                for (Object o: controllerDocument.getRootElement().elements("page")) {
                    Element pageElement = (Element) o;
                    String pathInfo = pageElement.attributeValue("path-info");
                    String xformsModel = pageElement.attributeValue("xforms");
                    String id = pageElement.attributeValue("id");
                    if (id != null) {
                        pageIdToPageElement.put(id, pageElement);
                        pageIdToPathInfo.put(id, pathInfo);
                        if (xformsModel != null)
                            pageIdToXFormsModel.put(id, xformsModel);

                        final Document setvaluesDocument = getSetValuesDocument(pageElement);
                        if (setvaluesDocument != null) {
                            pageIdToSetvaluesDocument.put(id, setvaluesDocument);
                        }
                    }
                }

                ASTPipeline astPipeline = new ASTPipeline() {{

                    setValidity(controllerValidity);

                    // Generate request path
                    final ASTOutput request = new ASTOutput("data", "request");
                    addStatement(new ASTProcessorCall(XMLConstants.REQUEST_PROCESSOR_QNAME) {{
                        final Document config;
                        try {
                            config = Dom4jUtils.readDom4j("<config><include>/request/request-path</include><include>/request/method</include></config>");
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                        addInput(new ASTInput("config", config));
                        addOutput(request);
                    }});

                    // Dummy matcher output
                    final ASTOutput dummyMatcherOutput = new ASTOutput("data", "dummy-matcher");
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                        addOutput(dummyMatcherOutput);
                    }});

                    // Is the previous <files> or <page> using simple matching
                    boolean previousIsSimple = true;
                    boolean previousIsFile = false;
                    boolean isFirst = true;
                    ASTChoose currentChoose = null;
                    int matcherCount = 0;

                    final ASTOutput epilogueData = new ASTOutput(null, "html");
                    final ASTOutput epilogueModelData = new ASTOutput(null, "epilogue-model-data");
                    final ASTOutput epilogueInstance = new ASTOutput(null, "epilogue-instance");
                    for (Object o: controllerDocument.getRootElement().elements()) {
                        Element element = (Element) o;
                        if ("files".equals(element.getName()) || "page".equals(element.getName())) {

                            // Extract matcher URI or QName
                            // URI is supported for backward compatibility only. We use a poor
                            // heuristic to detect whether a QName is present.
                            final String matcherURI;
                            final QName matcherQName;
                            {
                                final String matcherAttribute = element.attributeValue("matcher");
                                if (matcherAttribute != null && matcherAttribute.indexOf(':') != -1) {
                                    matcherQName = Dom4jUtils.extractAttributeValueQName(element, "matcher");
                                    matcherURI = null;
                                } else {
                                    matcherQName = null;
                                    matcherURI = matcherAttribute;
                                }
                            }

                            final String mimeType = element.attributeValue("mime-type");
                            final String pathInfo = element.attributeValue("path-info");

                            final boolean currentIsFile = "files".equals(element.getName());
                            final boolean currentFileIsVersioned;
                            if (currentIsFile) {
                                // If this is a file, then check "versioned" property, local and then global
                                final String currentIsVersionedAttribute = element.attributeValue(VERSIONED_ATTRIBUTE);
                                currentFileIsVersioned = (currentIsVersionedAttribute != null) ? "true".equals(currentIsVersionedAttribute) : globalIsVersioned;
                            } else {
                                currentFileIsVersioned = false;
                            }

                            // Remember this FilesInfo if needed
                            if (currentFileIsVersioned) {
                                pathMatchers.add(new URLRewriterUtils.PathMatcher(pathInfo, matcherQName, mimeType, true));
                            }

                            // Can we just add this condition to the previous "when" statement?
                            boolean canAddToPreviousWhen =
                                    previousIsFile && currentIsFile &&
                                            previousIsSimple && matcherURI == null && matcherQName == null && mimeType == null;

                            // Create <p:when>
                            ASTWhen when;
                            final ASTOutput matcherOutput;
                            if (matcherURI == null && matcherQName == null) {
                                // There is no matcher, use basic rules

                                matcherOutput = dummyMatcherOutput;

                                // Add <p:choose> / <p:otherwise> when necessary
                                if (!previousIsSimple) {
                                    // Add a <p:otherwise><p:choose ref="request">
                                    previousIsSimple = mimeType == null;
                                    ASTWhen otherwise = new ASTWhen();
                                    currentChoose.getWhen().add(otherwise);
                                    currentChoose = new ASTChoose(new ASTHrefId(request));
                                    otherwise.getStatements().add(currentChoose);
                                } else if (isFirst) {
                                    // Add a <p:choose ref="request">
                                    isFirst = false;
                                    currentChoose = new ASTChoose(new ASTHrefId(request));
                                    addStatement(currentChoose);
                                }

                                // Compute test
                                final String test;
                                if (pathInfo.startsWith("*")) {
                                    // Extension match
                                    test = "ends-with(/request/request-path, '"
                                            + pathInfo.substring(1)
                                            + "' )";
                                } else if (pathInfo.endsWith("*")) {
                                    // Partial match
                                    final int len = pathInfo.length() - 1;
                                    test = "starts-with(/request/request-path, '"
                                            + pathInfo.substring(0, len)
                                            + "' )";
                                } else {
                                    // Exact match
                                    test = "(/request/request-path = '" + pathInfo + "')";
                                }

                                // Add <p:when>
                                when = new ASTWhen(test);
                            } else {
                                // Use matcher

                                previousIsSimple = false;

                                // List if statements where we add the new <p:choose>
                                List<ASTStatement> statements;
                                if (isFirst) {
                                    isFirst = false;
                                    statements = getStatements();
                                } else {
                                    ASTWhen otherwise = new ASTWhen();
                                    currentChoose.getWhen().add(otherwise);
                                    statements = otherwise.getStatements();
                                }

                                // Execute regexp
                                final ASTOutput realMatcherOutput = new ASTOutput("data", "matcher-" + (++matcherCount));
                                matcherOutput = realMatcherOutput;
                                statements.add(new ASTProcessorCall(matcherQName, matcherURI) {{
                                    Document config = new NonLazyUserDataDocument(new NonLazyUserDataElement("regexp"));
                                    config.getRootElement().addText(pathInfo);
                                    addInput(new ASTInput("config", config));
                                    addInput(new ASTInput("data", new ASTHrefXPointer(new ASTHrefId(request), "/request/request-path")));
                                    addOutput(realMatcherOutput);
                                }});

                                // Add <p:choose>
                                currentChoose = new ASTChoose(new ASTHrefId(matcherOutput));
                                statements.add(currentChoose);
                                when = new ASTWhen("/result/matches = 'true'");
                            }

                            if (canAddToPreviousWhen) {
                                // Do not create new "when", add current condition to previous "when"
                                final ASTWhen previousWhen = currentChoose.getWhen().get(currentChoose.getWhen().size() - 1);
                                previousWhen.setTest(previousWhen.getTest() + " or " + when.getTest());
                            } else {
                                // Create new "when"
                                currentChoose.getWhen().add(when);
                                if (currentIsFile) {
                                    // Handle file
                                    previousIsFile = true;

                                    // For files, enforce GET method
                                    if (matcherURI == null && matcherQName == null)// TODO: should do this when we have matchers as well, but the condition applies to the #matcher-* input
                                        when.setTest("/request/method = 'GET' and (" + when.getTest() + ")");

                                    handleFile(when, request, mimeType, epilogueData, epilogueModelData, epilogueInstance);
                                } else if ("page".equals(element.getName())) {
                                    // Handle page
                                    previousIsFile = false;
                                    // Get unique page number
                                    int pageNumber = element.getParent().elements().indexOf(element);
                                    handlePage(stepProcessorContext, controllerContext, when.getStatements(), element,
                                            pageNumber, matcherOutput, epilogueData, epilogueModelData,
                                            epilogueInstance, pageIdToPathInfo, pageIdToXFormsModel, pageIdToSetvaluesDocument,
                                            globalInstancePassing);
                                }
                            }
                        }
                    }

                    // Create "not found" page
                    if (notFoundPipeline != null || notFoundPageId != null) {

                        // Determine where we insert out statements
                        List<ASTStatement> statementsList;
                        if (currentChoose == null) {
                            statementsList = getStatements();
                        } else {
                            ASTWhen otherwise = new ASTWhen();
                            currentChoose.getWhen().add(otherwise);
                            statementsList = otherwise.getStatements();
                        }

                        if (notFoundPageId != null) {
                            // Handle not-found page
                            // FIXME: We do not support not-found pages with a matcher output for now.
                            Element notFoundPageElement = pageIdToPageElement.get(notFoundPageId);
                            if (notFoundPageElement == null)
                                throw new OXFException("Cannot find \"not found\" page with id '" + notFoundPageId + "' in page flow");
                            // Create an artificial page number (must be different from the other page numbers)
                            handlePage(stepProcessorContext, controllerContext, statementsList, notFoundPageElement,
                                    pageCount, dummyMatcherOutput, epilogueData, epilogueModelData,
                                    epilogueInstance, pageIdToPathInfo, pageIdToXFormsModel, pageIdToSetvaluesDocument,
                                    globalInstancePassing);
                        } else {
                            // [BACKWARD COMPATIBILITY] - Execute simple "not-found" page coming from properties
                            final ASTOutput notFoundHTML = new ASTOutput(null, "not-found-html");
                            statementsList.add(new StepProcessorCall(stepProcessorContext, controllerContext, notFoundPipeline, "not-found") {{
                                addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("instance", Dom4jUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("xforms-model", Dom4jUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("matcher", Dom4jUtils.NULL_DOCUMENT));
                                final ASTOutput dataOutput = new ASTOutput("data", notFoundHTML);
                                dataOutput.setLocationData(new ExtendedLocationData((LocationData) controllerDocument.getRootElement().getData(),
                                        "executing not found pipeline", new String[] { "pipeline", notFoundPipeline}, true)); // use root element location data
                                addOutput(dataOutput);
                            }});

                            // There is no model data to send to the epilogue
                            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                                addOutput(new ASTOutput("data", epilogueModelData));
                            }});

                            // Send not-found through epilogue
                            handleEpilogue(controllerContext, statementsList, epilogueURL, epilogueElement, notFoundHTML, epilogueModelData, epilogueInstance, 404);

                            // Notify final epilogue that there is nothing to send
                            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                                addOutput(new ASTOutput("data", epilogueData));
                            }});
                            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                                addOutput(new ASTOutput("data", epilogueInstance));
                            }});
                        }
                    }

                    // Handle view, if there was one
                    addStatement(new ASTChoose(new ASTHrefId(epilogueData)) {{
                        addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {{
                            setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                            handleEpilogue(controllerContext, getStatements(), epilogueURL, epilogueElement,
                                    epilogueData, epilogueModelData, epilogueInstance, 200);
                        }});
                        addWhen(new ASTWhen() {{
                            // Make sure we execute the model if there is a model but no view
                            addStatement(new ASTProcessorCall(XMLConstants.NULL_SERIALIZER_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", new ASTHrefId(epilogueModelData)));
                            }});
                        }});
                    }});
                }};

                // For debugging
                if (logger.isDebugEnabled()) {
                    ASTDocumentHandler astDocumentHandler = new ASTDocumentHandler();
                    astPipeline.walk(astDocumentHandler);
                    logger.debug("Page Flow Controller pipeline:\n"
                            + Dom4jUtils.domToString(astDocumentHandler.getDocument()));
                }

                return new PageFlow(new PipelineProcessor(astPipeline), Collections.unmodifiableList(pathMatchers));
            }
        });

        // If required, store information about resources to rewrite in the pipeline context for downstream use, e.g. by
        // oxf:xhtml-rewrite. This allows consumers who would like to rewrite resources into versioned resources to
        // actually know what a "resource" is.
        final List<URLRewriterUtils.PathMatcher> pathMatchers = pageFlow.getPathMatchers();
        if (pathMatchers != null && pathMatchers.size() > 0) {
            final List<URLRewriterUtils.PathMatcher> existingPathMatchers = (List<URLRewriterUtils.PathMatcher>) pipelineContext.getAttribute(PATH_MATCHERS);
            if (existingPathMatchers == null) {
                // Set if we are the first
                pipelineContext.setAttribute(PATH_MATCHERS, pathMatchers);
            } else {
                // Add if we come after others (in case of nested page flows)
                final List<URLRewriterUtils.PathMatcher> allMatchers = new ArrayList<URLRewriterUtils.PathMatcher>() {{
                    addAll(existingPathMatchers);
                    addAll(pathMatchers);
                }};
                pipelineContext.setAttribute(PATH_MATCHERS, Collections.unmodifiableList(allMatchers));
            }
        }

        // Launch pipeline
        final PipelineProcessor pipelineProcessor = pageFlow.getPipelineProcessor();
        pipelineProcessor.reset(pipelineContext);
        pipelineProcessor.start(pipelineContext);
    }

    private static void handleEpilogue(final String controllerContext, List<ASTStatement> statements, final String epilogueURL, final Element epilogueElement,
                                       final ASTOutput epilogueData, final ASTOutput epilogueModelData, final ASTOutput epilogueInstance,
                                       final int defaultStatusCode) {
        // Send result through epilogue
        if (epilogueURL == null) {
            // Run HTML serializer
            statements.add(new ASTChoose(new ASTHrefId(epilogueData)) {{
                addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {{
                    setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                    // The epilogue did not do the serialization
                    addStatement(new ASTProcessorCall(XMLConstants.HTML_SERIALIZER_PROCESSOR_QNAME) {{
                        Document config = new NonLazyUserDataDocument(new NonLazyUserDataElement("config"));
                        Element rootElement = config.getRootElement();
                        rootElement.addElement("status-code").addText(Integer.toString(defaultStatusCode));
                        if (HTMLSerializer.DEFAULT_PUBLIC_DOCTYPE != null)
                            rootElement.addElement("public-doctype").addText(HTMLSerializer.DEFAULT_PUBLIC_DOCTYPE);
                        if (HTMLSerializer.DEFAULT_SYSTEM_DOCTYPE != null)
                            rootElement.addElement("system-doctype").addText(HTMLSerializer.DEFAULT_SYSTEM_DOCTYPE);
                        if (HTMLSerializer.DEFAULT_VERSION != null)
                            rootElement.addElement("version").addText(HTMLSerializer.DEFAULT_VERSION);
                        addInput(new ASTInput("config", config));
                        addInput(new ASTInput("data", new ASTHrefId(epilogueData)));
                        //setLocationData(TODO);
                    }});
                }});
                addWhen(new ASTWhen());
            }});
        } else {
            statements.add(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                final String url;
                try {
                    url = URLFactory.createURL(controllerContext, epilogueURL).toExternalForm();
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
                addInput(new ASTInput("config", new ASTHrefURL(url)));
                addInput(new ASTInput("data", new ASTHrefId(epilogueData)));
                addInput(new ASTInput("model-data", new ASTHrefId(epilogueModelData)));
                addInput(new ASTInput("instance", new ASTHrefId(epilogueInstance)));
                final String[] locationParams = new String[] { "pipeline",  epilogueURL };
                setLocationData(new ExtendedLocationData((LocationData) epilogueElement.getData(),
                    "executing epilogue", epilogueElement, locationParams, true));
            }});
        }
    }

    private Document getSetValuesDocument(final Element pageElement) {
        final List paramElements = pageElement.elements("param");
        final List setValueElements = pageElement.elements("setvalue");
        final Document setvaluesDocument;
        if (!paramElements.isEmpty() || !setValueElements.isEmpty()) {
            // Create document with setvalues
            setvaluesDocument = new NonLazyUserDataDocument(new NonLazyUserDataElement("params"));
            // Deprecated <param> elements
            if (!paramElements.isEmpty()) {
                for (Object paramElement1: paramElements) {
                    final Element paramElement = (Element) paramElement1;
                    setvaluesDocument.getRootElement().add((Element) paramElement.clone());
                }
            }
            // New <setvalue> elements
            if (!setValueElements.isEmpty()) {
                for (Object setValueElement1: setValueElements) {
                    final Element setValueElement = (Element) setValueElement1;
                    setvaluesDocument.getRootElement().add((Element) setValueElement.clone());
                }
            }
        } else {
            setvaluesDocument = null;
        }
        return setvaluesDocument;
    }

    /**
     * Handle <page>
     */
    private void handlePage(final StepProcessorContext stepProcessorContext, final String controllerContext,
                            List<ASTStatement> statementsList, final Element pageElement, final int pageNumber,
                            final ASTOutput matcherOutput,
                            final ASTOutput viewData, final ASTOutput epilogueModelData, final ASTOutput viewInstance,
                            final Map<String, String> pageIdToPathInfo,
                            final Map<String, String> pageIdToXFormsModel,
                            final Map<String, Document> pageIdToSetvaluesDocument,
                            final String instancePassing) {

        // Get page attributes
        final String modelAttribute = pageElement.attributeValue("model");
        final String viewAttribute = pageElement.attributeValue("view");
        final String defaultSubmissionAttribute = pageElement.attributeValue("default-submission");

        // Get setvalues document
        final Document setvaluesDocument = getSetValuesDocument(pageElement);

        // Get actions
        final List actionElements = pageElement.elements("action");

        // Handle initial instance
        final ASTOutput defaultSubmission = new ASTOutput("data", "default-submission");
        if (defaultSubmissionAttribute != null) {
            statementsList.add(new ASTProcessorCall(XMLConstants.URL_GENERATOR_PROCESSOR_QNAME) {{
                final String url;
                try {
                    url = URLFactory.createURL(controllerContext, defaultSubmissionAttribute).toExternalForm();
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
                final Document configDocument = new NonLazyUserDataDocument(new NonLazyUserDataElement("config"));
                configDocument.getRootElement().addText(url);

                addInput(new ASTInput("config", configDocument));
                addOutput(defaultSubmission);
            }});
        }

        // XForms Input
        final ASTOutput isRedirect = new ASTOutput(null, "is-redirect");

        // Always hook up XForms or XML submission
        final ASTOutput xformedInstance = new ASTOutput("instance", "xformed-instance");
        {
            final LocationData locDat = Dom4jUtils.getLocationData();
            xformedInstance.setLocationData(locDat);
        }

        // Use XML Submission pipeline
        statementsList.add(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
            addInput(new ASTInput("config", new ASTHrefURL(XFORMS_XML_SUBMISSION_XPL)));
            if (setvaluesDocument != null) {
                addInput(new ASTInput("setvalues", setvaluesDocument));
                addInput(new ASTInput("matcher-result", new ASTHrefId(matcherOutput)));
            } else {
                addInput(new ASTInput("setvalues", Dom4jUtils.NULL_DOCUMENT));
                addInput(new ASTInput("matcher-result", Dom4jUtils.NULL_DOCUMENT));
            }
            if (defaultSubmissionAttribute != null) {
                addInput(new ASTInput("default-submission", new ASTHrefId(defaultSubmission)));
            } else {
                addInput(new ASTInput("default-submission", Dom4jUtils.NULL_DOCUMENT));
            }
            addOutput(xformedInstance);
        }});

        // Make sure the xformed-instance id is used for p:choose
        statementsList.add(new ASTProcessorCall(XMLConstants.NULL_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", new ASTHrefId(xformedInstance)));
        }});

        // Execute actions
        final ASTOutput xupdatedInstance = new ASTOutput(null, "xupdated-instance");
        final ASTOutput actionData = new ASTOutput(null, "action-data");
        final int[] actionNumber = new int[] { 0 };
        final boolean[] foundActionWithoutWhen = new boolean[] { false };
        final ASTChoose actionsChoose = new ASTChoose(new ASTHrefId(xformedInstance)) {{

            // Always add a branch to test on whether the XML submission asked to bypass actions, model, view, and epilogue
            // Use of this <bypass> document is arguably a HACK 
            addWhen(new ASTWhen() {{

                setTest("/bypass[@xsi:nil = 'true']");
                setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);

                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", xupdatedInstance));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    final Document config = new NonLazyUserDataDocument(new NonLazyUserDataElement("is-redirect"));
                    config.getRootElement().addText("true");
                    addInput(new ASTInput("data", config));
                    addOutput(new ASTOutput("data", isRedirect));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", actionData));
                }});
            }});

            for (Object actionElement1: actionElements) {

                // Get info about action
                actionNumber[0]++;
                final Element actionElement = (Element) actionElement1;
                final String whenAttribute = actionElement.attributeValue("when");
                final String actionAttribute = actionElement.attributeValue("action");

                // Execute action
                addWhen(new ASTWhen() {{

                    // Add condition, remember that we found an <action> without a when
                    if (whenAttribute != null) {
                        if (foundActionWithoutWhen[0])
                            throw new ValidationException("Unreachable <action>", (LocationData) actionElement.getData());
                        setTest(whenAttribute);
                        setNamespaces(new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(actionElement)));
                        setLocationData((LocationData) actionElement.getData());
                    } else {
                        foundActionWithoutWhen[0] = true;
                    }

                    final boolean resultTestsOnActionData =
                            // Must have an action, in the first place
                            actionAttribute != null &&
                                    // More than one <result>: so at least the first one must have a "when"
                                    actionElement.elements("result").size() > 1;

                    final ASTOutput internalActionData = actionAttribute == null ? null :
                            new ASTOutput(null, "internal-action-data-" + pageNumber + "-" + actionNumber[0]);
                    if (actionAttribute != null) {
                        // TODO: handle passing and modifications of action data in model, and view, and pass to instance
                        addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, actionAttribute, "action") {{
                            addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                            addInput(new ASTInput("instance", new ASTHrefId(xformedInstance)));
                            addInput(new ASTInput("xforms-model", Dom4jUtils.NULL_DOCUMENT));
                            addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                            final ASTOutput dataOutput = new ASTOutput("data", internalActionData);
                            final String[] locationParams =
                                    new String[]{"pipeline", actionAttribute, "page id", pageElement.attributeValue("id"), "when", whenAttribute};
                            dataOutput.setLocationData(new ExtendedLocationData((LocationData) actionElement.getData(), "reading action data output", pageElement, locationParams, true));
                            addOutput(dataOutput);
                            setLocationData(new ExtendedLocationData((LocationData) actionElement.getData(), "executing action", pageElement, locationParams, true));
                        }});

                        // Force execution of action if no <result> is reading it
                        if (!resultTestsOnActionData) {
                            addStatement(new ASTProcessorCall(XMLConstants.NULL_SERIALIZER_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", new ASTHrefId(internalActionData)));
                            }});
                        }

                        // Export internal-action-data as action-data
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(internalActionData)));
                            addOutput(new ASTOutput("data", actionData));
                        }});
                    } else {
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                            addOutput(new ASTOutput("data", actionData));
                        }});
                    }

                    // Choose result
                    if (actionElement.elements("result").size() > 1
                            || (actionElement.element("result") != null
                            && actionElement.element("result").attributeValue("when") != null)) {

                        // Help diagnose missing action/@action
                        if (internalActionData == null) {
                            throw new OXFException("Found <result when=\"...\"> but <action> element is missing an action attribute.");
                        }

                        // Test based on action
                        addStatement(new ASTChoose(new ASTHrefId(internalActionData)) {{
                            for (Object o: actionElement.elements("result")) {
                                final Element resultElement = (Element) o;
                                final String resultWhenAttribute = resultElement.attributeValue("when");

                                // Execute result
                                addWhen(new ASTWhen() {{
                                    if (resultWhenAttribute != null) {
                                        setTest(resultWhenAttribute);
                                        setNamespaces(new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(resultElement)));
                                        final String[] locationParams =
                                                new String[]{"page id", pageElement.attributeValue("id"), "when", resultWhenAttribute};
                                        setLocationData(new ExtendedLocationData((LocationData) resultElement.getData(), "executing result", resultElement, locationParams, true));
                                    }
                                    executeResult(stepProcessorContext, controllerContext, this, pageIdToXFormsModel,
                                            pageIdToPathInfo, pageIdToSetvaluesDocument,
                                            xformedInstance, resultElement, internalActionData,
                                            isRedirect, xupdatedInstance, instancePassing);
                                }});
                            }

                            // Continue when all results fail
                            addWhen(new ASTWhen() {{
                                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                    addInput(new ASTInput("data", new NonLazyUserDataDocument(new NonLazyUserDataElement
                                            ("is-redirect") {{
                                            setText("false");
                                        }})));
                                    addOutput(new ASTOutput("data", isRedirect));
                                }});
                                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                    addInput(new ASTInput("data", new ASTHrefId(xformedInstance)));
                                    addOutput(new ASTOutput("data", xupdatedInstance));
                                }});
                            }});
                        }});

                    } else {

                        // If we are not performing tests on the result from the action
                        final Element resultElement = actionElement.element("result");
                        executeResult(stepProcessorContext, controllerContext, this, pageIdToXFormsModel,
                                pageIdToPathInfo, pageIdToSetvaluesDocument, xformedInstance, resultElement,
                                internalActionData, isRedirect, xupdatedInstance, instancePassing);
                    }
                }});
            }

            if (!foundActionWithoutWhen[0]) {
                // Defaul branch for when all actions fail
                addWhen(new ASTWhen() {{
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(xformedInstance)));
                        addOutput(new ASTOutput("data", xupdatedInstance));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        final Document config = new NonLazyUserDataDocument(new NonLazyUserDataElement("is-redirect"));
                        config.getRootElement().addText("false");
                        addInput(new ASTInput("data", config));
                        addOutput(new ASTOutput("data", isRedirect));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", actionData));
                    }});
                }});
            }
        }};

        // Add choose statement
        statementsList.add(actionsChoose);

        // Only continue if there was no redirect
        statementsList.add(new ASTChoose(new ASTHrefId(isRedirect)) {{
            addWhen(new ASTWhen("/is-redirect = 'false'") {{

                // Handle page model
                final ASTOutput modelData = new ASTOutput(null, "model-data");
                final ASTOutput modelInstance = new ASTOutput(null, "model-instance");
                if (modelAttribute != null) {
                    // There is a model
                    addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, modelAttribute, "model") {{
                        addInput(new ASTInput("data", new ASTHrefId(actionData)));
                        addInput(new ASTInput("instance", new ASTHrefId(xupdatedInstance)));
                        addInput(new ASTInput("xforms-model", Dom4jUtils.NULL_DOCUMENT));
                        addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                        final String[] locationParams =
                                new String[] { "page id", pageElement.attributeValue("id"), "model", modelAttribute };
                        {
                            final ASTOutput dataOutput = new ASTOutput("data", modelData);
                            dataOutput.setLocationData(new ExtendedLocationData((LocationData) pageElement.getData(), "reading page model data output", pageElement, locationParams, true));
                            addOutput(dataOutput);
                        }
                        {
                            final ASTOutput instanceOutput = new ASTOutput("instance", modelInstance);
                            addOutput(instanceOutput);
                            instanceOutput.setLocationData(new ExtendedLocationData((LocationData) pageElement.getData(), "reading page model instance output", pageElement, locationParams, true));
                        }
                        setLocationData(new ExtendedLocationData((LocationData) pageElement.getData(), "executing page model", pageElement, locationParams, true));
                    }});
                } else if (viewAttribute != null) {
                    // There is no model but there is a view
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(actionData)));
                        addOutput(new ASTOutput("data", modelData));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(xupdatedInstance)));
                        addOutput(new ASTOutput("data", modelInstance));
                    }});
                }

                // Handle page view
                if (viewAttribute != null) {
                    // There is a view
                    addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, viewAttribute, "view") {{
                        addInput(new ASTInput("data", new ASTHrefId(modelData)));
                        addInput(new ASTInput("instance", new ASTHrefId(modelInstance)));
                        addInput(new ASTInput("xforms-model", Dom4jUtils.NULL_DOCUMENT));
                        addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                        final String[] locationParams =
                                new String[] { "page id", pageElement.attributeValue("id"), "view", viewAttribute };
                        {
                            final ASTOutput dataOutput = new ASTOutput("data", viewData);
                            dataOutput.setLocationData(new ExtendedLocationData((LocationData) pageElement.getData(), "reading page view data output", pageElement, locationParams, true));
                            addOutput(dataOutput);
                        }
                        {
                            final ASTOutput instanceOutput = new ASTOutput("instance", viewInstance);
                            instanceOutput.setLocationData(new ExtendedLocationData((LocationData) pageElement.getData(), "reading page view instance output", pageElement, locationParams, true));
                            addOutput(instanceOutput);
                        }
                        setLocationData(new ExtendedLocationData((LocationData) pageElement.getData(), "executing page view", pageElement, locationParams, true));
                    }});
                } else {
                    // There is no view, send nothing to epilogue
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", viewData));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", viewInstance));
                    }});
                }

                if (modelAttribute != null && viewAttribute == null) {
                    // With XForms NG we want lazy evaluation of the instance, so we should not force a
                    // read on the instance. We just connect the output.
                    addStatement(new ASTProcessorCall(XMLConstants.NULL_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelInstance)));
                    }});
                }

                if (modelAttribute == null && viewAttribute == null) {
                    // Send out epilogue model data as a null document
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", epilogueModelData));
                    }});
                } else {
                    // Send out epilogue model data as produced by the model or used by the view
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelData)));
                        addOutput(new ASTOutput("data", epilogueModelData));
                    }});
                }

            }});
            addWhen(new ASTWhen() {{
                // There is a redirection due to the action

                // With XForms NG we want lazy evaluation of the instance, so we should not force a
                // read on the instance. We just connect the output.
                addStatement(new ASTProcessorCall(XMLConstants.NULL_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(xupdatedInstance)));
                }});
                // Just connect the output
                addStatement(new ASTProcessorCall(XMLConstants.NULL_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(actionData)));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", viewData));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", epilogueModelData));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", viewInstance));
                }});
            }});
        }});
    }

    private void executeResult(StepProcessorContext stepProcessorContext, final String controllerContext, ASTWhen when,
                               final Map<String, String> pageIdToXFormsModel, final Map<String, String> pageIdToPathInfo, final Map<String, Document> pageIdToSetvaluesDocument,
                               final ASTOutput paramedInstance, final Element resultElement,
                               final ASTOutput actionData, final ASTOutput redirect, final ASTOutput xupdatedInstance,
                               String instancePassing) {

        // Instance to update: either current, or instance from other page
        final String resultPageId = resultElement == null ? null : resultElement.attributeValue("page");
        Attribute instancePassingAttribute = resultElement == null ? null : resultElement.attribute("instance-passing");
        final String _instancePassing = instancePassingAttribute == null ? instancePassing : instancePassingAttribute.getValue();
        final String otherXForms = pageIdToXFormsModel.get(resultPageId);

        // Whether we use the legacy XUpdate transformation
        final boolean useLegacyTransformation =
            (resultElement != null && !resultElement.elements().isEmpty() && resultElement.attribute("transform") == null);

        // Whether we use the destination page's instance
        final boolean useCurrentPageInstance = resultPageId == null || otherXForms == null || !useLegacyTransformation;

        final ASTOutput instanceToUpdate;
        if (useCurrentPageInstance) {
            // We use the current page's submitted instance, if any.
            instanceToUpdate = paramedInstance;
        } else {
            // DEPRECATED: We use the resulting page's instance if possible (deprecated since xforms attribute on <page> is deprecated)
            instanceToUpdate = new ASTOutput("data", "other-page-instance");
            // Run the other page's XForms model
            final ASTOutput otherPageXFormsModel = new ASTOutput("data", "other-page-model");
            when.addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, otherXForms, "xforms-model") {{
                addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
                addInput(new ASTInput("instance", Dom4jUtils.NULL_DOCUMENT));
                addInput(new ASTInput("xforms-model", Dom4jUtils.NULL_DOCUMENT));
                addInput(new ASTInput("matcher", Dom4jUtils.NULL_DOCUMENT));
                final ASTOutput dataOutput = new ASTOutput("data", otherPageXFormsModel);
                final String[] locationParams =
                            new String[] { "result page id", resultPageId, "result page XForms model", otherXForms };
                dataOutput.setLocationData(new ExtendedLocationData((LocationData) resultElement.getData(),
                        "reading other page XForms model data output", resultElement, locationParams, true));
                addOutput(dataOutput);
                setLocationData(new ExtendedLocationData((LocationData) resultElement.getData(),
                        "executing other page XForms model", resultElement, locationParams, true));
            }});
            when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                addInput(new ASTInput("data", new ASTHrefXPointer(new ASTHrefId(otherPageXFormsModel), EXTRACT_INSTANCE_XPATH)));
                addOutput(new ASTOutput("data", instanceToUpdate));
            }});
        }

        // Create resulting instance
        final ASTOutput internalXUpdatedInstance;
        final boolean isTransformedInstance;
        if (resultElement != null && resultElement.attribute("transform") != null && !resultElement.elements().isEmpty()) {
            // Generic transform mechanism
            internalXUpdatedInstance = new ASTOutput("data", "internal-xupdated-instance");
            isTransformedInstance = true;

            final Document transformConfig = Dom4jUtils.createDocumentCopyParentNamespaces((Element) resultElement.elements().get(0));
            final QName transformQName = Dom4jUtils.extractAttributeValueQName(resultElement, "transform");

            // Run transform
            final String resultTraceAttribute = resultElement.attributeValue("trace");
            when.addStatement(new ASTProcessorCall(transformQName) {{
                addInput(new ASTInput("config", transformConfig));// transform
                addInput(new ASTInput("instance", new ASTHrefId(paramedInstance)));// source-instance
                addInput(new ASTInput("data", new ASTHrefId(instanceToUpdate)));// destination-instance
                //addInput(new ASTInput("request-instance", new ASTHrefId(requestInstance)));// params-instance TODO
                if (actionData != null)
                    addInput(new ASTInput("action", new ASTHrefId(actionData)));// action
                else
                    addInput(new ASTInput("action",  Dom4jUtils.NULL_DOCUMENT));// action
                addOutput(new ASTOutput("data", internalXUpdatedInstance) {{ setDebug(resultTraceAttribute);}});// updated-instance
            }});

        } else if (resultElement != null && !resultElement.elements().isEmpty()) {
            // Legacy transform mechanism (built-in XUpdate)
            internalXUpdatedInstance = new ASTOutput("data", "internal-xupdated-instance");
            isTransformedInstance = true;

            // Create XUpdate config
            // The code in this branch should be equivalent to the previous code doing the same
            // thing, except it will add the default namespace as well. I think there should be
            // the default namespace as well.
            final Document xupdateConfig = Dom4jUtils.createDocumentCopyParentNamespaces(resultElement);
            xupdateConfig.getRootElement().setQName(new QName("modifications", XUpdateConstants.XUPDATE_NAMESPACE));

            // Run XUpdate
            final String resultTraceAttribute = resultElement.attributeValue("trace");
            when.addStatement(new ASTProcessorCall(XMLConstants.XUPDATE_PROCESSOR_QNAME) {{
                addInput(new ASTInput("config", xupdateConfig));
                addInput(new ASTInput("data", new ASTHrefId(instanceToUpdate)));
                addInput(new ASTInput("instance", new ASTHrefId(paramedInstance)));
                if (actionData != null)
                    addInput(new ASTInput("action", new ASTHrefId(actionData)));
                addOutput(new ASTOutput("data", internalXUpdatedInstance) {{ setDebug(resultTraceAttribute);}});
            }});
        } else {
            internalXUpdatedInstance = instanceToUpdate;
            isTransformedInstance = false;
        }

        // Do redirect if we are going to a new page (NOTE: even if the new page has the same id as the current page)
        if (resultPageId != null) {
            final String forwardPathInfo = pageIdToPathInfo.get(resultPageId);
            if (forwardPathInfo == null)
                throw new OXFException("Cannot find page with id '" + resultPageId + "'");

            final Document setvaluesDocument = pageIdToSetvaluesDocument.get(resultPageId);
            final boolean doServerSideRedirect = _instancePassing != null && _instancePassing.equals(INSTANCE_PASSING_FORWARD);
            final boolean doRedirectExitPortal = _instancePassing != null && _instancePassing.equals(INSTANCE_PASSING_REDIRECT_PORTAL);

            // TODO: we should probably optimize all the redirect handling below with a dedicated processor
            {
                // Do redirect passing parameters from internalXUpdatedInstance without modifying URL
                final ASTOutput parametersOutput;
                if (isTransformedInstance) {
                    parametersOutput = new ASTOutput(null, "parameters");
                    // Pass parameters only if needed
                    final QName instanceToParametersProcessor = XMLConstants.INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME;
                    when.addStatement(new ASTProcessorCall(instanceToParametersProcessor) {{
                        addInput(new ASTInput("instance", new ASTHrefId(internalXUpdatedInstance)));
                        addInput(new ASTInput("filter", (setvaluesDocument != null) ? setvaluesDocument : Dom4jUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", parametersOutput));
                    }});
                } else {
                    parametersOutput = null;
                }
                // Handle path info
                final ASTOutput forwardPathInfoOutput = new ASTOutput(null, "forward-path-info");
                when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new NonLazyUserDataDocument
                            (new NonLazyUserDataElement("path-info") {{ setText(forwardPathInfo); }} )));
                    addOutput(new ASTOutput("data", forwardPathInfoOutput));
                }});
                // Handle server-side redirect and exit portal redirect
                final ASTOutput isServerSideRedirectOutput = new ASTOutput(null, "is-server-side-redirect");
                when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new NonLazyUserDataDocument
                            (new NonLazyUserDataElement("server-side") {{ setText(Boolean.toString(doServerSideRedirect)); }} )));
                    addOutput(new ASTOutput("data", isServerSideRedirectOutput));
                }});
                final ASTOutput isRedirectExitPortal = new ASTOutput(null, "is-redirect-exit-portal");
                when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new NonLazyUserDataDocument
                            (new NonLazyUserDataElement("exit-portal") {{ setText(Boolean.toString(doRedirectExitPortal)); }} )));
                    addOutput(new ASTOutput("data", isRedirectExitPortal));
                }});
                // Aggregate redirect-url config
                final ASTHref redirectURLData;
                if (setvaluesDocument != null && isTransformedInstance) {
                    // Setvalues document - things are little more complicated, so we delegate
                    final ASTOutput redirectDataOutput = new ASTOutput(null, "redirect-data");

                    final ASTHrefAggregate redirectDataAggregate = new ASTHrefAggregate("redirect-url", new ASTHrefId(forwardPathInfoOutput),
                            new ASTHrefId(isServerSideRedirectOutput), new ASTHrefId(isRedirectExitPortal));
                    redirectDataAggregate.getHrefs().add(new ASTHrefId(parametersOutput));

                    when.addStatement(new ASTProcessorCall(XMLConstants.UNSAFE_XSLT_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("config", new ASTHrefURL(REVERSE_SETVALUES_XSL)));
                        addInput(new ASTInput("data", redirectDataAggregate));
                        addInput(new ASTInput("instance", new ASTHrefId(internalXUpdatedInstance)));
                        addInput(new ASTInput("setvalues", setvaluesDocument));
                        addOutput(new ASTOutput("data", redirectDataOutput));
                    }});
                    redirectURLData = new ASTHrefId(redirectDataOutput);
                } else {
                    // No setvalues document, we can simply aggregate with XPL
                    final ASTHrefAggregate redirectDataAggregate = new ASTHrefAggregate("redirect-url", new ASTHrefId(forwardPathInfoOutput),
                            new ASTHrefId(isServerSideRedirectOutput), new ASTHrefId(isRedirectExitPortal));
                    if (isTransformedInstance) // Pass parameters only if needed
                        redirectDataAggregate.getHrefs().add(new ASTHrefId(parametersOutput));
                    redirectURLData = redirectDataAggregate;
                }
                // Execute the redirect
                when.addStatement(new ASTProcessorCall(XMLConstants.REDIRECT_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", redirectURLData));// {{setDebug("redirect 2");}}
                    final String[] locationParams =
                            new String[] { "result page id", resultPageId  };
                    setLocationData(new ExtendedLocationData((LocationData) resultElement.getData(),
                            "page redirection", resultElement, locationParams, true));
                }});
            }
        }

        // Signal if we did a redirect
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", new NonLazyUserDataDocument(new NonLazyUserDataElement
                    ("is-redirect") {{ setText(Boolean.toString(resultPageId != null)); }})));
            addOutput(new ASTOutput("data", redirect));
        }});

        // Export XUpdated instance from this branch
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", new ASTHrefId(internalXUpdatedInstance)));
            addOutput(new ASTOutput("data", xupdatedInstance));
        }});
    }

    /**
     * Handle <files>
     */
    private void handleFile(ASTWhen when, final ASTOutput request, final String mimeType,
                            final ASTOutput epilogueData, final ASTOutput epilogueModelData,
                            final ASTOutput epilogueInstance) {
        when.addStatement(new ASTProcessorCall(XMLConstants.RESOURCE_SERVER_PROCESSOR_QNAME) {{

            addInput(new ASTInput("config", new ASTHrefAggregate("path", new ASTHrefXPointer(new ASTHrefId(request), "string(/request/request-path)"))));

            if (mimeType == null) {
                addInput(new ASTInput(ResourceServer.MIMETYPE_INPUT, new ASTHrefURL("oxf:/oxf/mime-types.xml")));
            } else {
                final Document mimeTypeConfig = new NonLazyUserDataDocument(new NonLazyUserDataElement("mime-types") {{
                    add(new NonLazyUserDataElement("mime-type") {{
                        add(new NonLazyUserDataElement("name") {{
                            addText(mimeType);
                        }});
                        add(new NonLazyUserDataElement("pattern") {{
                            addText("*");
                        }});
                    }});
                }});
                addInput(new ASTInput(ResourceServer.MIMETYPE_INPUT, mimeTypeConfig));
            }
        }});
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
            addOutput(new ASTOutput("data", epilogueData));
        }});
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
            addOutput(new ASTOutput("data", epilogueModelData));
        }});
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", Dom4jUtils.NULL_DOCUMENT));
            addOutput(new ASTOutput("data", epilogueInstance));
        }});
    }

    /**
     * Creates a single StepProcessor. This should be called only once for a given Page Flow
     * configuration. Then the same StepProcessor should be used for each step.
     */
    private static class StepProcessorContext {

        private PipelineConfig pipelineConfig;

        private StepProcessorContext(final Object controllerValidity) {
            this.pipelineConfig = PipelineProcessor.createConfigFromAST(new ASTPipeline()  {{
                setValidity(controllerValidity);

                final ASTParam stepURLInput = addParam(new ASTParam(ASTParam.INPUT, "step-url"));
                final ASTParam stepTypeInput = addParam(new ASTParam(ASTParam.INPUT, "step-type"));
                final ASTParam dataInput = addParam(new ASTParam(ASTParam.INPUT, "data"));
                final ASTParam instanceInput = addParam(new ASTParam(ASTParam.INPUT, "instance"));
                final ASTParam xformsModelInput = addParam(new ASTParam(ASTParam.INPUT, "xforms-model"));
                final ASTParam matcherInput = addParam(new ASTParam(ASTParam.INPUT, "matcher"));
                final ASTParam dataOutput = addParam(new ASTParam(ASTParam.OUTPUT, "data"));
                final ASTParam instanceOutput = addParam(new ASTParam(ASTParam.OUTPUT, "instance"));

                // Rewrite the URL if needed
                final ASTOutput rewroteStepURL = new ASTOutput(null, "rewrote-step-url");
                addStatement(new ASTChoose(new ASTHrefId(stepURLInput)) {{
                    addWhen(new ASTWhen("contains(/config/url, '${')") {{
                        addStatement(new ASTProcessorCall(XMLConstants.XSLT_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefAggregate("root",
                                    new ASTHrefId(stepURLInput), new ASTHrefId(matcherInput))));
                            addInput(new ASTInput("config", new ASTHrefURL(REWRITE_XSL)));
                            addOutput(new ASTOutput("data", rewroteStepURL));
                        }});
                    }});
                    addWhen(new ASTWhen() {{
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(stepURLInput)));
                            addOutput(new ASTOutput("data", rewroteStepURL));
                        }});
                    }});
                }});

                final ASTOutput contentXIncluded = new ASTOutput("data", "content-xincluded");
                {
                    // Read file to "execute"
                    final ASTOutput content = new ASTOutput("data", "content");
                    addStatement(new ASTProcessorCall(XMLConstants.URL_GENERATOR_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("config", new ASTHrefId(rewroteStepURL)));
                        addOutput(content);
                    }});

                    // Insert XInclude processor to process content with XInclude
                    addStatement(new ASTProcessorCall(XMLConstants.XINCLUDE_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("config", new ASTHrefId(content)));
                        addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                        addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                        final ASTOutput contentOutput = new ASTOutput("data", contentXIncluded);
                        addOutput(contentOutput);
                    }});
                }

                final ASTOutput resultData = new ASTOutput(null, "result-data");
                final ASTOutput resultInstance = new ASTOutput(null, "result-instance");

                // Perform verifications on input/output of XPL
                addStatement(new ASTChoose(new ASTHrefId(stepTypeInput)) {{
                    addWhen(new ASTWhen("/step-type = 'view'") {{
                        // We are dealing with a view
                        addStatement(new ASTChoose(new ASTHrefId(contentXIncluded)) {{
                            addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                                "and count(/*/*[local-name() = 'param' and @type = 'output' and @name = 'data']) = 0") {{
                                // The XPL has not data output
                                addStatement(new ASTProcessorCall(XMLConstants.ERROR_PROCESSOR_QNAME) {{
                                    final Document errorDocument = new NonLazyUserDataDocument(new NonLazyUserDataElement("error"));
                                    errorDocument.getRootElement().addText("XPL view must have a 'data' output");
                                    addInput(new ASTInput("config", errorDocument));
                                }});
                            }});
                        }});
                    }});
                }});

                addStatement(new ASTChoose(new ASTHrefId(contentXIncluded)) {{

                    // XPL file with instance & data output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'data'] " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'instance']") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            final ASTOutput datOut = new ASTOutput( "data", resultData );
                            addOutput( datOut );
                            final ASTOutput instOut = new ASTOutput( "instance", resultInstance );
                            addOutput( instOut );
                        }});
                    }});

                    // XPL file with only data output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'data']") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            final ASTOutput datOut = new ASTOutput( "data", resultData );
                            addOutput( datOut );
                        }});
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput datOut = new ASTOutput( "data", resultInstance );
                            addOutput( datOut );
                        }});
                    }});

                    // XPL file with only instance output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'instance']") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            final ASTOutput instOut = new ASTOutput( "instance", resultInstance );
                            addOutput( instOut );
                        }});
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            final ASTOutput resDatOut = new ASTOutput( "data", resultData );
                            addOutput( resDatOut );
                        }});
                    }});

                    // XPL file with no output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline'") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                        }});
                        // Simply bypass data and instance channels
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            final ASTOutput resDatOut = new ASTOutput( "data", resultData );
                            addOutput( resDatOut );
                        }});
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput( "data", resultInstance );
                            addOutput( resInstOut );
                        }});
                    }});

                    // PFC file (should only work as model)
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/controller'") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PAGE_FLOW_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("controller", new ASTHrefId(contentXIncluded)));
                        }});
                        // Simply bypass data and instance channels
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            final ASTOutput resDatOut = new ASTOutput( "data", resultData );
                            addOutput( resDatOut );
                        }});
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput( "data", resultInstance );
                            addOutput( resInstOut );
                        }});
                    }});

                    // XSLT file (including XSLT 2.0 "Simplified Stylesheet Modules")
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.w3.org/1999/XSL/Transform' or /*/@xsl:version = '2.0'") {{
                        setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);

                        // Copy the instance as is
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput( "data", resultInstance );
                            addOutput( resInstOut );
                        }});

                        // Process XInclude
//                        final ASTOutput xincludedContent = new ASTOutput("data", "xincluded-content");
//                        addStatement(new ASTProcessorCall(XMLConstants.XINCLUDE_PROCESSOR_QNAME) {{
//                            addInput(new ASTInput("config", new ASTHrefId(content)));
////                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
////                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
//                            addOutput(xincludedContent);
//                        }});

                        addStatement(new ASTChoose(new ASTHrefId(contentXIncluded)) {

                            private void addXSLTWhen(final String condition, final QName processorQName) {
                                addWhen(new ASTWhen(condition) {{
                                    setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);

                                    // Changed from <= 2.8 behavior
                                    addStatement(new ASTProcessorCall(processorQName) {{
                                        addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                                        addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                                        addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                                        final ASTOutput resDatOut
                                            = new ASTOutput( "data", resultData );
                                        addOutput( resDatOut );
                                    }});
                                }});
                            }

                            {
                                // XSLT 1.0: There is no xsl:version = '2.0' attribute (therefore the namespace of the
                                //           root element is xsl as per the condition above) and the version attribute
                                //           is exactly '1.0'
                                addXSLTWhen("not(/*/@xsl:version = '2.0') and /*/@version = '1.0'", XMLConstants.PFC_XSLT10_PROCESSOR_QNAME);

                                // XSLT 2.0: There is an xsl:version = '2.0' attribute or the namespace or the root
                                //           element is xsl and the version is different from '1.0'
                                addXSLTWhen(null, XMLConstants.PFC_XSLT20_PROCESSOR_QNAME);
                        }});
                    }});

                    // XML file
                    addWhen(new ASTWhen() {{

                        // Copy the instance as is
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput("data", resultInstance);
                            addOutput(resInstOut);
                        }});

                        // Copy the data as is
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(contentXIncluded)));
                            final ASTOutput resDatOut = new ASTOutput("data", resultData);
                            addOutput(resDatOut);
                        }});

                        // Insert XInclude processor to process static XML file with XInclude
//                        addStatement(new ASTProcessorCall(XMLConstants.XINCLUDE_PROCESSOR_QNAME) {{
//                            addInput(new ASTInput("config", new ASTHrefId(content)));
//                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
//                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
//                            final ASTOutput resDatOut = new ASTOutput("data", resultData);
//                            addOutput(resDatOut);
//                        }});
                    }});
                }});

                // Connect results
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(resultData)));
                    addOutput(new ASTOutput("data", dataOutput));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(resultInstance)));
                    final ASTOutput resDatOut = new ASTOutput( "data", instanceOutput );
                    addOutput( resDatOut );
                }});
            }});
        }

        public PipelineConfig getPipelineConfig() {
            return pipelineConfig;
        }
    }

    private static class StepProcessorCall extends ASTProcessorCall {
        public StepProcessorCall(StepProcessorContext stepProcessorContext, String controllerContext, String uri, String stepType) {
            super(new PipelineProcessor(stepProcessorContext.getPipelineConfig()));
            try {
                // Create document and input for URI
                final String url = URLFactory.createURL(controllerContext, uri).toExternalForm();
                final Document configDocument;
                {
                    configDocument = new NonLazyUserDataDocument(new NonLazyUserDataElement("config"));
                    final Element urlElement = configDocument.getRootElement().addElement("url");
                    urlElement.addText(url);
                    final Element handleXIncludeElement = configDocument.getRootElement().addElement("handle-xinclude");
                    handleXIncludeElement.addText("false");
                    // Allow external entities in document
                    final Element externalEntitiesElement = configDocument.getRootElement().addElement("external-entities");
                    externalEntitiesElement.addText("true");
                }

                addInput(new ASTInput("step-url", configDocument));
                // Create document and input for step type
                final Document stepTypeDocument = new NonLazyUserDataDocument(new NonLazyUserDataElement("step-type"));
                stepTypeDocument.getRootElement().addText(stepType);
                addInput(new ASTInput("step-type", stepTypeDocument));
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }
        }
    }

    private static class PageFlow {
        private PipelineProcessor pipelineProcessor;
        private List<URLRewriterUtils.PathMatcher> pathMatchers;

        public PageFlow(PipelineProcessor pipelineProcessor, List<URLRewriterUtils.PathMatcher> pathMatchers) {
            this.pipelineProcessor = pipelineProcessor;
            this.pathMatchers = pathMatchers;
        }

        public PipelineProcessor getPipelineProcessor() {
            return pipelineProcessor;
        }

        public List<URLRewriterUtils.PathMatcher> getPathMatchers() {
            return pathMatchers;
        }
    }
}
