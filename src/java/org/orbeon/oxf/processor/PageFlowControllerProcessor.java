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
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.pipeline.PipelineConfig;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.processor.serializer.HTMLSerializer;
import org.orbeon.oxf.processor.xforms.input.Instance;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.transformer.xupdate.Constants;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.DocumentDelegate;
import org.orbeon.oxf.xml.dom4j.ElementDelegate;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PageFlowControllerProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(PageFlowControllerProcessor.class);
    public final static String INPUT_CONTROLER = "controller";
    public final static String CONTROLLER_NAMESPACE_URI = "http://www.orbeon.com/oxf/controller";
    private final static Document TRUE_DOCUMENT = DocumentHelper.createDocument();
    private final static Document FALSE_DOCUMENT = DocumentHelper.createDocument();
    private final static Map NAMESPACES_WITH_XSI_AND_XSLT = new HashMap();
    public final static String EXTRACT_INSTANCE_XPATH
            = "/*/*[local-name() = 'instance' and namespace-uri() = '" + org.orbeon.oxf.processor.xforms.Constants.XFORMS_NAMESPACE_URI + "']/*[1]";

    // Instance passing configuration
    private final static String INSTANCE_PASSING_REDIRECT = "redirect";
    private final static String INSTANCE_PASSING_FORWARD = "forward";
    private final static String INSTANCE_PASSING_REDIRECT_PORTAL = "redirect-exit-portal";
    private final static String DEFAULT_INSTANCE_PASSING = INSTANCE_PASSING_REDIRECT;

    // Properties
    private static final String INSTANCE_PASSING_PROPERTY_NAME = "instance-passing";
    private static final String EPILOGUE_PROPERTY_NAME = "epilogue";
    private static final String NOT_FOUND_PROPERTY_NAME = "not-found";

    static {
        Element trueConfigElement = DocumentHelper.createElement("config");
        trueConfigElement.setText("true");
        TRUE_DOCUMENT.setRootElement(trueConfigElement);

        Element falseConfigElement = DocumentHelper.createElement("config");
        falseConfigElement.setText("false");
        FALSE_DOCUMENT.setRootElement(falseConfigElement);

        NAMESPACES_WITH_XSI_AND_XSLT.put(XMLUtils.XSI_PREFIX, XMLUtils.XSI_NAMESPACE);
        NAMESPACES_WITH_XSI_AND_XSLT.put(XMLUtils.XSLT_PREFIX, XMLUtils.XSLT_NAMESPACE);
    }

    public PageFlowControllerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONTROLER, CONTROLLER_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        PipelineProcessor pipelineProcessor = (PipelineProcessor) readCacheInputAsObject(context, getInputByName(INPUT_CONTROLER), new CacheableInputReader() {
            public Object read(final PipelineContext context, ProcessorInput input) {
                final Document controllerDocument = readInputAsDOM4J(context, INPUT_CONTROLER);
                final Object controllerValidity = getInputValidity(context, getInputByName(INPUT_CONTROLER));
                final StepProcessorContext stepProcessorContext = new StepProcessorContext(controllerValidity);
                LocationData locationData = (LocationData) controllerDocument.getRootElement().getData();
                final String controllerContext = locationData == null ? null : locationData.getSystemID();

                // Get global "parameters" of the controller, fall back to properties
                final String _instancePassing; {
                    String attributeValue = controllerDocument.getRootElement().attributeValue("instance-passing");
                    _instancePassing = attributeValue != null ? attributeValue
                            : getPropertySet().getString(INSTANCE_PASSING_PROPERTY_NAME, DEFAULT_INSTANCE_PASSING);
                }
                final String instancePassing = _instancePassing;
                final String _epilogue; {
                    Element epilogueElement = controllerDocument.getRootElement().element("epilogue");
                    _epilogue = epilogueElement != null ? epilogueElement.attributeValue("url")
                        : getPropertySet().getString(EPILOGUE_PROPERTY_NAME);
                }
                final String epilogue = _epilogue;
                final String notFoundPipeline = getPropertySet().getString(NOT_FOUND_PROPERTY_NAME);
                final String _notFoundPageId; {
                    Element notFoundHandlerElement = controllerDocument.getRootElement().element("not-found-handler");
                    _notFoundPageId = notFoundHandlerElement != null ? notFoundHandlerElement.attributeValue("page") : null;
                }
                final String notFoundPageId = _notFoundPageId;
                final String errorPageId; {
                    Element errorHandlerElement = controllerDocument.getRootElement().element("error-handler");
                    errorPageId = errorHandlerElement != null ? errorHandlerElement.attributeValue("page") : null;
                }

                // Go through all pages to get mapping
                final Map pageIdToPageElement = new HashMap();
                final Map pageIdToPathInfo = new HashMap();
                final Map pageIdToXFormsModel = new HashMap();
                final Map pageIdToParamsDocument = new HashMap();
                final int pageCount = controllerDocument.getRootElement().elements("page").size();
                for (Iterator i = controllerDocument.getRootElement().elements("page").iterator(); i.hasNext();) {
                    Element pageElement = (Element) i.next();
                    String pathInfo = pageElement.attributeValue("path-info");
                    String xformsModel = pageElement.attributeValue("xforms");
                    String id = pageElement.attributeValue("id");
                    if (id != null) {
                        pageIdToPageElement.put(id, pageElement);
                        pageIdToPathInfo.put(id, pathInfo);
                        if (xformsModel != null)
                            pageIdToXFormsModel.put(id, xformsModel);
                    }
                    List params = pageElement.elements("param");
                    if (!params.isEmpty()) {
                        Document paramsDocument = DocumentHelper.createDocument(DocumentHelper.createElement("params"));
                        for (Iterator j = params.iterator(); j.hasNext();) {
                            Node node = (Node) j.next();
                            paramsDocument.getRootElement().add((Node) node.clone());
                        }
                        pageIdToParamsDocument.put(id, paramsDocument);
                    }
                }

                ASTPipeline astPipeline = new ASTPipeline() {{

                    setValidity(controllerValidity);

                    // Generate request path
                    final ASTOutput request = new ASTOutput("data", "request");
                    addStatement(new ASTProcessorCall(XMLConstants.REQUEST_PROCESSOR_QNAME) {{
                        Document config = null;
                        try {
                            config = DocumentHelper.parseText
                                    ("<config><include>/request/request-path</include></config>");
                        } catch (DocumentException e) {
                            throw new OXFException(e);
                        }
                        addInput(new ASTInput("config", config));
                        addOutput(request);
                    }});

                    // Generate request parameters
                    // Do this separately, so that we do not "tee" (and store in memory) the request parameters
                    final ASTOutput requestWithParameters = new ASTOutput("data", "request-with-parameters");// {{setDebug("request params");}};
                    addStatement(new ASTProcessorCall(XMLConstants.REQUEST_PROCESSOR_QNAME) {{
                        Document config = null;
                        try {
                            config = DocumentHelper.parseText
                                    ("<config><include>/request/parameters</include></config>");
//                                    ("<config xmlns:xs='http://www.w3.org/2001/XMLSchema' stream-type='xs:anyURI'><include>/request/parameters</include></config>");
                        } catch (DocumentException e) {
                            throw new OXFException(e);
                        }
                        addInput(new ASTInput("config", config));
                        addOutput(requestWithParameters);
                    }});

                    // Dummy matcher output
                    final ASTOutput dummyMatcherOutput = new ASTOutput("data", "dummy-matcher");
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                        addOutput(dummyMatcherOutput);
                    }});

                    // Is the previous <files> or <page> using simple matching
                    boolean previousIsSimple = true;
                    boolean previousIsFile = false;
                    boolean isFirst = true;
                    ASTChoose currentChoose = null;
                    int matcherCount = 0;

                    final ASTOutput html = new ASTOutput(null, "html");
                    final ASTOutput epilogueInstance = new ASTOutput(null, "epilogue-instance");
                    final ASTOutput epilogueXFormsModel = new ASTOutput(null, "epilogue-xforms-model");
                    for (Iterator i = controllerDocument.getRootElement().elements().iterator(); i.hasNext();) {
                        Element element = (Element) i.next();
                        if ("files".equals(element.getName()) || "page".equals(element.getName())) {

                            // Extract matcher URI or QName
                            // URI is supported for backward compatibility only. We use a poor
                            // heuristic to detect whether a QName is present.
                            String matcherURI = element.attributeValue("matcher");
                            QName matcherQName = (matcherURI != null && matcherURI.indexOf(':') != -1)
                                ? XMLUtils.extractAttributeValueQName(element, "matcher") : null;
                            if (matcherQName != null)
                                matcherURI = null;

                            String mimeType = element.attributeValue("mime-type");
                            final String pathInfo = element.attributeValue("path-info");

                            // Can we just add this condition to the previous "when" statement?
                            boolean canAddToPreviousWhen =
                                    previousIsFile  && "files".equals(element.getName()) &&
                                    previousIsSimple && matcherURI == null && matcherQName == null && mimeType ==null;

                            // Create <p:when>
                            ASTWhen when;
                            final ASTOutput _matcherOutput;
                            if (matcherURI == null && matcherQName == null) {
                                _matcherOutput = dummyMatcherOutput;

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
                                String test;
                                if (pathInfo.startsWith("*")) {
                                    test = "(string-length(/request/request-path) >= " + (pathInfo.length() - 1)
                                            + " and substring(/request/request-path, string-length(/request/request-path) - "
                                            + (pathInfo.length() - 2) + ", " + (pathInfo.length() - 1) + ") = '"
                                            + pathInfo.substring(1) + "')";
                                } else if (pathInfo.endsWith("*")) {
                                    test = "(string-length(/request/request-path) >= " + (pathInfo.length() - 1)
                                            + "and substring(/request/request-path, 1, " + (pathInfo.length() - 1)
                                            + ") = '" + pathInfo.substring(0, pathInfo.length() - 1) + "')";
                                } else {
                                    test = "(/request/request-path = '" + pathInfo + "')";
                                }

                                // Add <p:when>
                                when = new ASTWhen(test);
                            } else {

                                previousIsSimple = false;

                                // List if statements where we add the new <p:choose>
                                List statements;
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
                                _matcherOutput = realMatcherOutput;
                                statements.add(new ASTProcessorCall(matcherQName, matcherURI) {{
                                    Document config = DocumentHelper.createDocument(DocumentHelper.createElement("regexp"));
                                    config.getRootElement().addText(pathInfo);
                                    addInput(new ASTInput("config", config));
                                    addInput(new ASTInput("data", new ASTHrefXPointer(new ASTHrefId(request), "/request/request-path")));
                                    addOutput(realMatcherOutput);
                                }});

                                // Add <p:choose>
                                currentChoose = new ASTChoose(new ASTHrefId(_matcherOutput));
                                statements.add(currentChoose);
                                when = new ASTWhen("/result/matches = 'true'");
                            }
                            final ASTOutput matcherOutput = _matcherOutput;

                            if (canAddToPreviousWhen) {
                                // Do not create new "when", add current condition to previous "when"
                                ASTWhen previousWhen = (ASTWhen) currentChoose.getWhen().get(currentChoose.getWhen().size() - 1);
                                previousWhen.setTest(previousWhen.getTest() + " or " + when.getTest());
                            } else {
                                // Create new "when"
                                currentChoose.getWhen().add(when);
                                if ("files".equals(element.getName())) {
                                    previousIsFile = true;
                                    handleFile(when, request, mimeType, html, epilogueInstance, epilogueXFormsModel);
                                } else if ("page".equals(element.getName())) {
                                    previousIsFile = false;
                                    // Get unique page number
                                    int pageNumber = element.getParent().elements().indexOf(element);
                                    handlePage(stepProcessorContext, controllerContext, when.getStatements(), element,
                                            pageNumber, requestWithParameters, matcherOutput, html,
                                            epilogueInstance, epilogueXFormsModel, pageIdToPathInfo, pageIdToXFormsModel, pageIdToParamsDocument,
                                            instancePassing);
                                }
                            }
                        }
                    }

                    // Create "not found" page
                    if (notFoundPipeline != null || notFoundPageId != null) {

                        // Determine where we insert out statements
                        List statementsList;
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
                            Element notFoundPageElement = (Element) pageIdToPageElement.get(notFoundPageId);
                            if (notFoundPageElement == null)
                                throw new OXFException("Cannot find \"not found\" page with id '" + notFoundPageId + "' in page flow");
                            // Create an artificial page number (must be different from the other page numbers)
                            handlePage(stepProcessorContext, controllerContext, statementsList, notFoundPageElement,
                                    pageCount, requestWithParameters, dummyMatcherOutput, html,
                                    epilogueInstance, epilogueXFormsModel, pageIdToPathInfo, pageIdToXFormsModel, pageIdToParamsDocument,
                                    instancePassing);
                        } else {
                            // [BACKWARD COMPATIBILITY] - Execute simple "not-found" page
                            final ASTOutput notFoundHTML = new ASTOutput(null, "not-found-html");
                            statementsList.add(new StepProcessorCall(stepProcessorContext, controllerContext, notFoundPipeline) {{
                                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("instance", XMLUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("xforms-model", XMLUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("matcher", XMLUtils.NULL_DOCUMENT));
                                addInput(new ASTInput("can-be-serializer", FALSE_DOCUMENT));
                                addOutput(new ASTOutput("data", notFoundHTML));
                            }});

                            // Send not-found through epilogue
                            handleEpilogue(stepProcessorContext, controllerContext, statementsList, epilogue, notFoundHTML, epilogueInstance, epilogueXFormsModel, 404);

                            // Notify final epilogue that there is nothing to send
                            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                                addOutput(new ASTOutput("data", html));
                            }});
                            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                                addOutput(new ASTOutput("data", epilogueInstance));
                            }});
                            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                                addOutput(new ASTOutput("data", epilogueXFormsModel));
                            }});
                        }
                    }

                    // Handle view, if there was one
                    addStatement(new ASTChoose(new ASTHrefId(html)) {{
                        addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {{
                            setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                            handleEpilogue(stepProcessorContext, controllerContext, getStatements(), epilogue,
                                    html, epilogueInstance, epilogueXFormsModel, 200);
                        }});
                        addWhen(new ASTWhen());
                    }});
                }};

                // For debugging
                if (logger.isDebugEnabled()) {
                    ASTDocumentHandler astDocumentHandler = new ASTDocumentHandler();
                    astPipeline.walk(astDocumentHandler);
                    logger.debug("Page Flow Controller pipeline:\n"
                            + XMLUtils.domToString(astDocumentHandler.getDocument()));
                }

                return new PipelineProcessor(astPipeline);
            }
        });
        pipelineProcessor.reset(context);
        pipelineProcessor.start(context);
    }

    private static void handleEpilogue(final StepProcessorContext stepProcessorContext, final String controllerContext,
                                       List statements, final String epilogue, final ASTOutput html,
                                       final ASTOutput epilogueInstance, final ASTOutput epilogueXFormsModel,
                                       final int defaultStatusCode) {
        // Send result through epilogue
        final ASTOutput epilogueOutput = epilogue == null ? html : new ASTOutput("data", "epilogue");
        if (epilogue != null) {
            statements.add(new StepProcessorCall(stepProcessorContext, controllerContext, epilogue) {{
                addInput(new ASTInput("data", new ASTHrefId(html)));
                addInput(new ASTInput("instance", new ASTHrefId(epilogueInstance)));
                addInput(new ASTInput("xforms-model", new ASTHrefId(epilogueXFormsModel)));
                addInput(new ASTInput("matcher", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("can-be-serializer", TRUE_DOCUMENT));
                addOutput(epilogueOutput);
            }});
        }

        // Run HTML serializer
        statements.add(new ASTChoose(new ASTHrefId(epilogueOutput)) {{
            addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {{
                setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                // The epilogue did not do the serialization
                addStatement(new ASTProcessorCall(XMLConstants.HTML_SERIALIZER_PROCESSOR_QNAME) {{
                    Document config = DocumentHelper.createDocument(DocumentHelper.createElement("config"));
                    Element rootElement = config.getRootElement();
                    rootElement.addElement("status-code").addText(Integer.toString(defaultStatusCode));
                    if (HTMLSerializer.DEFAULT_PUBLIC_DOCTYPE != null)
                        rootElement.addElement("public-doctype").addText(HTMLSerializer.DEFAULT_PUBLIC_DOCTYPE);
                    if (HTMLSerializer.DEFAULT_SYSTEM_DOCTYPE != null)
                        rootElement.addElement("system-doctype").addText(HTMLSerializer.DEFAULT_SYSTEM_DOCTYPE);
                    if (HTMLSerializer.DEFAULT_VERSION != null)
                        rootElement.addElement("version").addText(HTMLSerializer.DEFAULT_VERSION);
                    addInput(new ASTInput("config", config));
                    addInput(new ASTInput("data", new ASTHrefId(epilogueOutput)));
                }});
            }});
            addWhen(new ASTWhen());
        }});
    }

    /**
     * Handle &lt;page&gt;
     */
    private void handlePage(final StepProcessorContext stepProcessorContext, final String controllerContext,
                            List statementsList, Element pageElement, final int pageNumber,
                            final ASTOutput requestWithParameters, final ASTOutput matcherOutput,
                            final ASTOutput html, final ASTOutput epilogueInstance, final ASTOutput epilogueXFormsModel,
                            final Map pageIdToPathInfo,
                            final Map pageIdToXFormsModel,
                            final Map pageIdToParamsDocument,
                            final String instancePassing) {

        // Get page / xforms / model / view attributes
        //final String idAttribute = element.attributeValue("id");
        final String xformsAttribute = pageElement.attributeValue("xforms");
        final String modelAttribute = pageElement.attributeValue("model");
        final String viewAttribute = pageElement.attributeValue("view");

        // Get actions
        final List actionElements = pageElement.elements("action");

        // XForms Input
        final ASTOutput isRedirect = new ASTOutput(null, "is-redirect");
        final ASTOutput xformsModel = new ASTOutput("data", "xforms-model");
        xformsModel.setSchemaUri(org.orbeon.oxf.processor.xforms.Constants.XFORMS_NAMESPACE_URI + "/model");
        final ASTOutput[] xformedInstance = new ASTOutput[1];

        if (xformsAttribute != null) {

            // Get XForms model
            statementsList.add(new StepProcessorCall(stepProcessorContext, controllerContext, xformsAttribute) {{
                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("instance", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("xforms-model", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                addInput(new ASTInput("can-be-serializer", FALSE_DOCUMENT));
                addOutput(xformsModel);
            }});

            // Modify instance using params
            final ASTOutput[] paramedInstance = new ASTOutput[1];
            final Document[] paramsDocument = new Document[1];
            final List params = pageElement.elements("param");
            if (!params.isEmpty()) {
                paramsDocument[0] = DocumentHelper.createDocument(DocumentHelper.createElement("params"));
                final Document _paramsDocument = paramsDocument[0];
                for (Iterator j = params.iterator(); j.hasNext();) {
                    Node node = (Node) j.next();
                    _paramsDocument.getRootElement().add((Node) node.clone());
                }
                paramedInstance[0] = new ASTOutput("data", "paramed-instance");
                statementsList.add(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("instance", new ASTHrefId(xformsModel)));
                    addInput(new ASTInput("config", new ASTHrefURL("oxf:/oxf/private/page-flow/params.xpl")));
                    addInput(new ASTInput("matcher-result", new ASTHrefId(matcherOutput)));
                    addInput(new ASTInput("params", _paramsDocument));
                    addOutput(new ASTOutput("instance", paramedInstance[0]));
                }});
            } else {
                paramedInstance[0] = xformsModel;
            }

            // Execute XForms Input
            xformedInstance[0] = new ASTOutput("instance", "xformed-instance");
            statementsList.add(new ASTProcessorCall(XMLConstants.XFORMS_INPUT_PROCESSOR_QNAME) {{
                addInput(new ASTInput("model", new ASTHrefId(xformsModel)));
                if(!params.isEmpty()) {
                    addInput(new ASTInput("instance", new ASTHrefId(paramedInstance[0])));
                    addInput(new ASTInput("filter", paramsDocument[0]));
                } else {
                    addInput(new ASTInput("instance", new ASTHrefXPointer(new ASTHrefId(xformsModel), EXTRACT_INSTANCE_XPATH)));
                    addInput(new ASTInput("filter", XMLUtils.NULL_DOCUMENT));
                }
                addInput(new ASTInput("request", new ASTHrefId(requestWithParameters)));
                addOutput(xformedInstance[0]);
            }});

        } else {
            xformedInstance[0] = new ASTOutput("data", "paramed-instance");
            statementsList.add(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                addOutput(xformedInstance[0]);
            }});
        }

        // Execute actions
        final ASTOutput xupdatedInstance = new ASTOutput(null, "xupdated-instance");
        final ASTOutput actionData = new ASTOutput(null, "action-data");
        statementsList.add(new ASTChoose(new ASTHrefId(xformedInstance[0])) {{
            final boolean[] foundActionWithoutWhen = new boolean[] {false};

            int _actionNumber = 0;
            for (Iterator j = actionElements.iterator(); j.hasNext();) {

                // Get info about action
                final int actionNumber = ++_actionNumber;
                final Element actionElement = (Element) j.next();
                final String whenAttribute = actionElement.attributeValue("when");
                final String actionAttribute = actionElement.attributeValue("action");

                // Execute action
                addWhen(new ASTWhen() {{

                    // Add condition, remember that we found an <action> without a when
                    if (whenAttribute != null) {
                        if (foundActionWithoutWhen[0])
                            throw new ValidationException("Unreachable <action>", (LocationData) actionElement.getData());
                        setTest(whenAttribute);
                    } else {
                        foundActionWithoutWhen[0] = true;
                    }

                    final boolean resultTestsOnActionData =
                            // Must have an action, in the first place
                            actionAttribute != null &&
                            // More than one <result>: so at least the first one must have a "when"
                            actionElement.elements("result").size() > 1;

                    final ASTOutput internalActionData = actionAttribute == null ? null :
                            new ASTOutput(null, "internal-action-data-" + pageNumber + "-" + actionNumber);
                    if (actionAttribute != null) {
                        addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, actionAttribute) {{
                            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                            addInput(new ASTInput("instance", new ASTHrefId(xformedInstance[0])));
                            addInput(new ASTInput("xforms-model", XMLUtils.NULL_DOCUMENT));
                            addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                            addInput(new ASTInput("can-be-serializer", FALSE_DOCUMENT));
                            addOutput(new ASTOutput("data", internalActionData));
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
                            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                            addOutput(new ASTOutput("data", actionData));
                        }});
                    }

                    // Choose result
                    if (actionElement.elements("result").size() > 1
                            || (actionElement.element("result") != null
                            && actionElement.element("result").attributeValue("when") != null)) {

                        // Test based on action
                        addStatement(new ASTChoose(new ASTHrefId(internalActionData)) {{
                            for (Iterator k = actionElement.elements("result").iterator(); k.hasNext();) {
                                final Element resultElement = (Element) k.next();
                                final String resultWhenAttribute = resultElement.attributeValue("when");

                                // Execute result
                                addWhen(new ASTWhen() {{
                                    if (resultWhenAttribute != null)
                                        setTest(resultWhenAttribute);
                                    executeResult(stepProcessorContext, controllerContext, this, pageIdToXFormsModel,
                                            pageIdToPathInfo, pageIdToParamsDocument,
                                            xformedInstance, resultElement, internalActionData,
                                            isRedirect, xupdatedInstance, instancePassing);
                                }});
                            }

                            // Continue when all results fail
                            addWhen(new ASTWhen() {{
                                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                    addInput(new ASTInput("data", new DocumentDelegate(new ElementDelegate
                                            ("is-redirect") {{ setText("false"); }})));
                                    addOutput(new ASTOutput("data", isRedirect));
                                }});
                                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                                    addInput(new ASTInput("data", new ASTHrefId(xformedInstance[0])));
                                    addOutput(new ASTOutput("data", xupdatedInstance));
                                }});
                            }});
                        }});

                    } else {

                        // If we are not performing tests on the result from the action
                        final Element resultElement = actionElement.element("result");
                        executeResult(stepProcessorContext, controllerContext, this, pageIdToXFormsModel,
                                pageIdToPathInfo, pageIdToParamsDocument, xformedInstance, resultElement,
                                internalActionData,isRedirect, xupdatedInstance, instancePassing);
                    }
                }});
            }

            // When all actions fail
            if (!foundActionWithoutWhen[0]) {
                addWhen(new ASTWhen() {{
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(xformedInstance[0])));
                        addOutput(new ASTOutput("data", xupdatedInstance));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        Document config = DocumentHelper.createDocument(DocumentHelper.createElement("is-redirect"));
                        config.getRootElement().addText("false");
                        addInput(new ASTInput("data", config));
                        addOutput(new ASTOutput("data", isRedirect));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", actionData));
                    }});
                }});
            }
        }});

        // Only continue if there was no redirect
        statementsList.add(new ASTChoose(new ASTHrefId(isRedirect)) {{
            addWhen(new ASTWhen("/is-redirect = 'false'") {{

                // Model
                final ASTOutput modelData = new ASTOutput(null, "model-data");
                final ASTOutput modelInstance = new ASTOutput(null, "model-instance");
                if (modelAttribute != null) {
                    addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, modelAttribute) {{
                        addInput(new ASTInput("data", new ASTHrefId(actionData)));
                        addInput(new ASTInput("instance", new ASTHrefId(xupdatedInstance)));
                        addInput(new ASTInput("xforms-model", XMLUtils.NULL_DOCUMENT));
                        addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                        addInput(new ASTInput("can-be-serializer", FALSE_DOCUMENT));
                        addOutput(new ASTOutput("data", modelData));
                        addOutput(new ASTOutput("instance", modelInstance));
                    }});
                } else if (viewAttribute != null) {
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(actionData)));
                        addOutput(new ASTOutput("data", modelData));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(xupdatedInstance)));
                        addOutput(new ASTOutput("data", modelInstance));
                    }});
                }

                if (viewAttribute != null) {
                    // View
                    addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, viewAttribute) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelData)));
                        addInput(new ASTInput("instance", new ASTHrefId(modelInstance)));
                        addInput(new ASTInput("xforms-model", XMLUtils.NULL_DOCUMENT));
                        addInput(new ASTInput("matcher", new ASTHrefId(matcherOutput)));
                        addInput(new ASTInput("can-be-serializer", FALSE_DOCUMENT));
                        addOutput(new ASTOutput("data", html));
                        if (xformsAttribute != null)
                            addOutput(new ASTOutput("instance", epilogueInstance));
                    }});
                    if (xformsAttribute == null) {
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                            addOutput(new ASTOutput("data", epilogueInstance));
                        }});
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                            addOutput(new ASTOutput("data", epilogueXFormsModel));
                        }});
                    } else {
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(xformsModel)));
                            addOutput(new ASTOutput("data", epilogueXFormsModel));
                        }});
                    }
                } else {
                    // Send nothing to prologue
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", html));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", epilogueInstance));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", epilogueXFormsModel));
                    }});
                }

                if (modelAttribute != null && viewAttribute == null) {
                    // Make sure we execute the model
                    addStatement(new ASTProcessorCall(XMLConstants.NULL_SERIALIZER_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelData)));
                    }});
                    addStatement(new ASTProcessorCall(XMLConstants.NULL_SERIALIZER_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelInstance)));
                    }});
                }
            }});
            addWhen(new ASTWhen() {{
                addStatement(new ASTProcessorCall(XMLConstants.NULL_SERIALIZER_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(xupdatedInstance)));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.NULL_SERIALIZER_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(actionData)));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", html));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", epilogueInstance));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                    addOutput(new ASTOutput("data", epilogueXFormsModel));
                }});
            }});
        }});
    }

    private void executeResult(StepProcessorContext stepProcessorContext, final String controllerContext, ASTWhen when,
                               final Map pageIdToXFormsModel, final Map pageIdToPathInfo, final Map pageIdToParamsDocument,
                               final ASTOutput[] paramedInstance, final Element resultElement,
                               final ASTOutput actionData, final ASTOutput redirect, final ASTOutput xupdatedInstance,
                               String instancePassing) {

        // Instance to update: either current, or instance from other page
        final String resultPageId = resultElement == null ? null : resultElement.attributeValue("page");
        Attribute instancePassingAttribute = resultElement == null ? null : resultElement.attribute("instance-passing");
        final String _instancePassing = instancePassingAttribute == null ? instancePassing : instancePassingAttribute.getValue();
        final String otherXForms = (String) pageIdToXFormsModel.get(resultPageId);
        final boolean useCurrentPageInstance = resultPageId == null || otherXForms == null;
        final ASTOutput instanceToUpdate = useCurrentPageInstance ? paramedInstance[0]
                : new ASTOutput("data", "other-page-instance");
        if (!useCurrentPageInstance) {
            final ASTOutput otherPageXFormsModel = new ASTOutput("data", "other-page-model");
            when.addStatement(new StepProcessorCall(stepProcessorContext, controllerContext, otherXForms) {{
                addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("instance", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("xforms-model", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("matcher", XMLUtils.NULL_DOCUMENT));
                addInput(new ASTInput("can-be-serializer", FALSE_DOCUMENT));
                addOutput(new ASTOutput("data", otherPageXFormsModel));
            }});
            when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                addInput(new ASTInput("data", new ASTHrefXPointer(new ASTHrefId(otherPageXFormsModel), EXTRACT_INSTANCE_XPATH)));
                addOutput(new ASTOutput("data", instanceToUpdate));
            }});
        }

        // XUpdate the instance
        final ASTOutput internalXUpdatedInstance = resultElement == null || resultElement.elements().isEmpty()
                ? instanceToUpdate : new ASTOutput("data", "internal-xupdated-instance");
        if (resultElement != null && !resultElement.elements().isEmpty()) {

            // Create XUpdate config
            final Document xupdateConfig = DocumentHelper.createDocument(DocumentHelper.createElement
                    (new QName("xupdate", new Namespace
                            ("xupdate", Constants.XUPDATE_NAMESPACE_URI))));
            for (Iterator l = resultElement.elements().iterator(); l.hasNext();) {
                Element xupdateElement = (Element) l.next();
                xupdateConfig.getRootElement().add(xupdateElement.createCopy());
            }

            // Run XUpdate
            when.addStatement(new ASTProcessorCall(XMLConstants.XUPDATE_PROCESSOR_QNAME) {{
                addInput(new ASTInput("config", xupdateConfig));
                addInput(new ASTInput("data", new ASTHrefId(instanceToUpdate)));
                addInput(new ASTInput("instance", new ASTHrefId(paramedInstance[0])));
                if (actionData != null)
                    addInput(new ASTInput("action", new ASTHrefId(actionData)));
                addOutput(new ASTOutput("data", internalXUpdatedInstance));
            }});
        }

        // Do redirect
        if (resultPageId != null) {
            final String forwardPathInfo = (String) pageIdToPathInfo.get(resultPageId);
            if (forwardPathInfo == null)
                throw new OXFException("Cannot find page with id '" + resultPageId + "'");

            final Document paramsDocument = (Document) pageIdToParamsDocument.get(resultPageId);
            final boolean doServerSideRedirect = _instancePassing != null && _instancePassing.equals(INSTANCE_PASSING_FORWARD);
            final boolean doRedirectExitPortal = _instancePassing != null && _instancePassing.equals(INSTANCE_PASSING_REDIRECT_PORTAL);

            if (otherXForms != null && paramsDocument != null) {
                // Do redirect passing parameters from internalXUpdatedInstance and modify URL if needed
                // FIXME: This does not handle server-side or portal-exit redirects
                final ASTOutput parametersOutput = new ASTOutput(null, "parameters");
                when.addStatement(new ASTProcessorCall(XMLConstants.INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("instance", new ASTHrefId(internalXUpdatedInstance)));
                    addInput(new ASTInput("filter", paramsDocument));
                    addOutput(new ASTOutput("data", parametersOutput));
                }});
                final ASTOutput redirectDataOutput = new ASTOutput(null, "redirect-data");
                when.addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("config", new ASTHrefURL("oxf:/oxf/private/page-flow/reverse-params.xpl")));
                    addInput(new ASTInput("instance", new ASTHrefId(internalXUpdatedInstance)));
                    addInput(new ASTInput("instance-params", new ASTHrefId(parametersOutput)));
                    addInput(new ASTInput("params", paramsDocument));
                    addInput(new ASTInput("path-info", new DocumentDelegate
                            (new ElementDelegate("path-info") {{ setText(forwardPathInfo); }} )));
                    addOutput(new ASTOutput("redirect-data", redirectDataOutput));
                }});

                when.addStatement(new ASTProcessorCall(XMLConstants.REDIRECT_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(redirectDataOutput)));// {{setDebug("redirect 1");}}
                }});
            } else {
                // Do redirect passing parameters from internalXUpdatedInstance without modifying URL
                final ASTOutput parametersOutput = (otherXForms != null) ? new ASTOutput(null, "parameters") : null;
                if (otherXForms != null) {
                    // Pass parameters only if needed
                    when.addStatement(new ASTProcessorCall(XMLConstants.INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("instance", new ASTHrefId(internalXUpdatedInstance)));
                        addInput(new ASTInput("filter", XMLUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", parametersOutput));
                    }});
                }
                // Handle path info
                final ASTOutput forwardPathInfoOutput = new ASTOutput(null, "forward-path-info");
                when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new DocumentDelegate
                            (new ElementDelegate("path-info") {{ setText(forwardPathInfo); }} )));
                    addOutput(new ASTOutput("data", forwardPathInfoOutput));
                }});
                // Handle server-side redirect and exit portal redirect
                final ASTOutput isServerSideRedirectOutput = new ASTOutput(null, "is-server-side-redirect");
                when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new DocumentDelegate
                            (new ElementDelegate("server-side") {{ setText(Boolean.toString(doServerSideRedirect)); }} )));
                    addOutput(new ASTOutput("data", isServerSideRedirectOutput));
                }});
                final ASTOutput isRedirectExitPortal = new ASTOutput(null, "is-redirect-exit-portal");
                when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new DocumentDelegate
                            (new ElementDelegate("exit-portal") {{ setText(Boolean.toString(doRedirectExitPortal)); }} )));
                    addOutput(new ASTOutput("data", isRedirectExitPortal));
                }});
                // Serialize the instance into the request if we are doing a server-side redirect
                if (doServerSideRedirect) {
                    when.addStatement(new ASTProcessorCall(XMLConstants.SCOPE_SERIALIZER_PROCESSOR_QNAME) {{
                        Document config = null;
                        try {
                            config = DocumentHelper.parseText
                                    ("<config><key>" + Instance.REQUEST_INSTANCE_DOCUMENT + "</key><scope>request</scope></config>");
                        } catch (DocumentException e) {
                            throw new OXFException(e);
                        }
                        addInput(new ASTInput("data", new ASTHrefId(internalXUpdatedInstance)));
                        addInput(new ASTInput("config", config));
                    }});
                }
                // Aggregate redirect-url config
                final ASTHrefAggregate redirectURLAggregate = new ASTHrefAggregate("redirect-url", new ASTHrefId(forwardPathInfoOutput),
                        new ASTHrefId(isServerSideRedirectOutput), new ASTHrefId(isRedirectExitPortal));
                if (otherXForms != null) // Pass parameters only if needed
                    redirectURLAggregate.getHrefs().add(new ASTHrefId(parametersOutput));
                // Execute the redirect
                when.addStatement(new ASTProcessorCall(XMLConstants.REDIRECT_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", redirectURLAggregate));// {{setDebug("redirect 2");}}
                }});
            }
        }

        // Signal if we did a redirect
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", new DocumentDelegate(new ElementDelegate
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
     * Handle &lt;files>
     */
    private void handleFile(ASTWhen when, final ASTOutput request, final String mimeType,
                            final ASTOutput html, final ASTOutput epilogueInstance, final ASTOutput epilogueXFormsModel) {
        when.addStatement(new ASTProcessorCall(XMLConstants.RESOURCE_SERVER_PROCESSOR_QNAME) {{
            addInput(new ASTInput("config", new ASTHrefAggregate("path", new ASTHrefXPointer
                    (new ASTHrefId(request), "string(/request/request-path)"))));
            if (mimeType == null) {
                addInput(new ASTInput(ResourceServer.MIMETYPE_INPUT, new ASTHrefURL("oxf:/oxf/mime-types.xml")));
            } else {
                Document mimeTypeConfig = new DocumentDelegate(new ElementDelegate("mime-types") {{
                    add(new ElementDelegate("mime-type") {{
                        add(new ElementDelegate("name") {{
                            addText(mimeType);
                        }});
                        add(new ElementDelegate("pattern") {{
                            addText("*");
                        }});
                    }});
                }});
                addInput(new ASTInput(ResourceServer.MIMETYPE_INPUT, mimeTypeConfig));
            }
        }});
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
            addOutput(new ASTOutput("data", html));
        }});
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
            addOutput(new ASTOutput("data", epilogueInstance));
        }});
        when.addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
            addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
            addOutput(new ASTOutput("data", epilogueXFormsModel));
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
                final ASTParam canBeSerializerInput = addParam(new ASTParam(ASTParam.INPUT, "can-be-serializer"));
                final ASTParam dataInput = addParam(new ASTParam(ASTParam.INPUT, "data"));
                final ASTParam instanceInput = addParam(new ASTParam(ASTParam.INPUT, "instance"));
                final ASTParam xformsModelInput = addParam(new ASTParam(ASTParam.INPUT, "xforms-model"));
                final ASTParam matcherInput = addParam(new ASTParam(ASTParam.INPUT, "matcher"));
                final ASTParam dataOutput = addParam(new ASTParam(ASTParam.OUTPUT, "data"));
                final ASTParam instanceOutput = addParam(new ASTParam(ASTParam.OUTPUT, "instance"));

                // Rewrite the URL if needed
                final ASTOutput rewroteStepURL = new ASTOutput(null, "rewrote-step-url");
                addStatement(new ASTChoose(new ASTHrefId(stepURLInput)) {{
                    addWhen(new ASTWhen("contains(/config, '${')") {{
                        addStatement(new ASTProcessorCall(XMLConstants.XSLT_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefAggregate("root",
                                    new ASTHrefId(stepURLInput), new ASTHrefId(matcherInput))));
                            addInput(new ASTInput("config", new ASTHrefURL("oxf:/oxf/private/page-flow/rewrite.xsl")));
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

                // Read file to "execute"
                final ASTOutput content = new ASTOutput("data", "content");
                addStatement(new ASTProcessorCall(XMLConstants.URL_GENERATOR_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("config", new ASTHrefId(rewroteStepURL)));
                    addOutput(content);
                }});

                final ASTOutput resultData = new ASTOutput(null, "result-data");
                final ASTOutput resultInstance = new ASTOutput(null, "result-instance");
                addStatement(new ASTChoose(new ASTHrefId(content)) {{

                    // XPL file with instance & data output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'data'] " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'instance']") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(content)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            addOutput(new ASTOutput("data", resultData));
                            addOutput(new ASTOutput("instance", resultInstance));
                        }});
                    }});

                    // XPL file with only data output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'data']") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(content)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            addOutput(new ASTOutput("data", resultData));
                        }});
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            addOutput(new ASTOutput("data", resultInstance));
                        }});
                    }});

                    // XPL file with only instance output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'instance']") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(content)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            addOutput(new ASTOutput("instance", resultInstance));
                        }});
                        outputNullIfStepCanBeSerializer(getStatements(), canBeSerializerInput, resultData, dataInput);
                    }});

                    // XPL file with no output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline'") {{
                        addStatement(new ASTProcessorCall(XMLConstants.PIPELINE_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("config", new ASTHrefId(content)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                        }});
                        outputNullIfStepCanBeSerializer(getStatements(), canBeSerializerInput, resultData, dataInput);
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            addOutput(new ASTOutput("data", resultInstance));
                        }});
                    }});

                    // XSLT file (including XSLT 2.0 "Simplified Stylesheet Modules")
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.w3.org/1999/XSL/Transform' or /*/@xsl:version = '2.0'") {{
                        setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);

                        // Copy the instance as is
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            addOutput(new ASTOutput("data", resultInstance));
                        }});

                        addStatement(new ASTChoose(new ASTHrefId(content)) {

                            private void addDataWhen(final ASTChoose astChoose, final String condition, final QName processorQName, final ASTParam input1, final ASTParam input2) {
                                astChoose.addWhen(new ASTWhen(condition) {{
                                    setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                                    addStatement(new ASTProcessorCall(processorQName) {{
                                        addInput(new ASTInput("config", new ASTHrefId(content)));
                                        addInput(new ASTInput("data", new ASTHrefId(input1)));
                                        addInput(new ASTInput("instance", new ASTHrefId(input2)));
                                        addOutput(new ASTOutput("data", resultData));
                                    }});
                                }});
                            }

                            private void addXSLTWhen(final String condition, final QName processorQName) {
                                addWhen(new ASTWhen(condition) {{
                                    setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                                    addStatement(new ASTChoose(new ASTHrefId(dataInput)) {{
                                        addDataWhen(this, "not(/*/@xsi:nil = 'true')", processorQName, dataInput, instanceInput);
                                        addDataWhen(this, "/*/@xsi:nil = 'true'", processorQName, instanceInput, instanceInput);
                                    }});
                                }});
                            }

                            {
                                // XSLT 1.0: There is no xsl:version = '2.0' attribute (therefore the namespace of the
                                //           root element is xsl as per the condition above) and the version attribute
                                //           is exactly '1.0'
                                addXSLTWhen("not(/*/@xsl:version = '2.0') and /*/@version = '1.0'", XMLConstants.XSLT10_PROCESSOR_QNAME);

                                // XSLT 2.0: There is an xsl:version = '2.0' attribute or the namespace or the root
                                //           element is xsl and the version is different from '1.0'
                                addXSLTWhen(null, XMLConstants.XSLT20_PROCESSOR_QNAME);
                        }});
                    }});

                    // XML file
                    addWhen(new ASTWhen() {{

                        // Copy the instance as is
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            addOutput(new ASTOutput("data", resultInstance));
                        }});

                        // Copy XML file as is
                        addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                            addInput(new ASTInput("data", new ASTHrefId(content)));
                            addOutput(new ASTOutput("data", resultData));
                        }});
                    }});
                }});

                // Connect results
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(resultData)));
                    addOutput(new ASTOutput("data", dataOutput));
                }});
                addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                    addInput(new ASTInput("data", new ASTHrefId(resultInstance)));
                    addOutput(new ASTOutput("data", instanceOutput));
                }});
            }});
        }

        /**
         * Send null in data output is step can be a serializer
         */
        private void outputNullIfStepCanBeSerializer(List statements, final ASTParam canBeSerializerInput, final ASTOutput resultData, final ASTParam dataInput) {
            statements.add(new ASTChoose(new ASTHrefId(canBeSerializerInput)) {{
                addWhen(new ASTWhen("/config = 'true'") {{
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", XMLUtils.NULL_DOCUMENT));
                        addOutput(new ASTOutput("data", resultData));
                    }});
                }});
                addWhen(new ASTWhen() {{
                    addStatement(new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME) {{
                        addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                        addOutput(new ASTOutput("data", resultData));
                    }});
                }});
            }});
        }

        public PipelineConfig getPipelineConfig() {
            return pipelineConfig;
        }
    }

    private static class StepProcessorCall extends ASTProcessorCall {
        public StepProcessorCall(StepProcessorContext stepProcessorContext, String controllerContext, String uri) {
            super(new PipelineProcessor(stepProcessorContext.getPipelineConfig()));
            try {
                final String url = URLFactory.createURL(controllerContext, uri).toExternalForm();
                final Document configDocument = DocumentHelper.createDocument(DocumentHelper.createElement("config"));
                configDocument.getRootElement().addText(url);
                addInput(new ASTInput("step-url", configDocument));
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }
        }
    }

//    private static class ExceptionProcessor extends ProcessorImpl {
//
//        private String message;
//        private LocationData locationData;
//
//        public ExceptionProcessor(String message, LocationData locationData) {
//            this.message = message;
//            this.locationData = locationData;
//        }
//
//        public void start(PipelineContext context) {
//            throw new ValidationException(message, locationData);
//        }
//    }
}
