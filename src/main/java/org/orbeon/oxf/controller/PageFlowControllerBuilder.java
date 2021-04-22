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
package org.orbeon.oxf.controller;

import org.orbeon.datatypes.LocationData;
import org.orbeon.dom.Attribute;
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.QName;
import org.orbeon.io.CharsetNames;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.ProcessorSupport;
import org.orbeon.oxf.processor.XPLConstants;
import org.orbeon.oxf.processor.pipeline.PipelineConfig;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom.Extensions;
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData;
import org.orbeon.xml.NamespaceMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// PageFlowControllerProcessor code that hasn't been migrated to Scala yet
public class PageFlowControllerBuilder {

    public final static NamespaceMapping NAMESPACES_WITH_XSI_AND_XSLT;

    // External resources
    private final static String REVERSE_SETVALUES_XSL = "oxf:/ops/pfc/setvalue-reverse.xsl";
    private final static String REWRITE_XSL = "oxf:/ops/pfc/rewrite.xsl";
    private final static String XFORMS_XML_SUBMISSION_XPL = "oxf:/ops/pfc/xforms-xml-submission.xpl";

    // Instance passing configuration
    public final static String INSTANCE_PASSING_REDIRECT = "redirect";
    public final static String INSTANCE_PASSING_FORWARD = "forward";
    public final static String INSTANCE_PASSING_REDIRECT_PORTAL = "redirect-exit-portal";

    static {
        final Map<String, String> mapping = new HashMap<String, String>();
        mapping.put(XMLConstants.XSI_PREFIX(), XMLConstants.XSI_URI());
        mapping.put(XMLConstants.XSLT_PREFIX(), XMLConstants.XSLT_NAMESPACE_URI());

        NAMESPACES_WITH_XSI_AND_XSLT = NamespaceMapping.apply(mapping);
    }

    public static void handleEpilogue(final String controllerContext, List<ASTStatement> statements, final String epilogueURL, final Element epilogueElement,
            final ASTOutput epilogueData, final ASTOutput epilogueModelData, final ASTOutput epilogueInstance) {
        if (epilogueURL == null) {
            // Run HTML serializer if no epilogue is specified
            statements.add(new ASTChoose(new ASTHrefId(epilogueData)) {{
                addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {{
                    setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);
                    // The epilogue did not do the serialization
                    addStatement(new ASTProcessorCall(XPLConstants.HTML_SERIALIZER_PROCESSOR_QNAME()) {{
                        Document config = Document.apply("config");
                        Element rootElement = config.getRootElement();
                        rootElement.addElement("version").addText("5.0");
                        rootElement.addElement("encoding").addText(CharsetNames.Utf8());
                        addInput(new ASTInput("config", config));
                        addInput(new ASTInput("data", new ASTHrefId(epilogueData)));
                        //setLocationData(TODO);
                    }});
                }});
                addWhen(new ASTWhen());
            }});
        } else {
            // Send result through epilogue
            statements.add(new ASTProcessorCall(XPLConstants.PIPELINE_PROCESSOR_QNAME()) {{
                final String url = URLFactory.createURL(controllerContext, epilogueURL).toExternalForm();

                addInput(new ASTInput("config", new ASTHrefURL(url)));
                addInput(new ASTInput("data", new ASTHrefId(epilogueData)));
                addInput(new ASTInput("model-data", new ASTHrefId(epilogueModelData)));
                addInput(new ASTInput("instance", new ASTHrefId(epilogueInstance)));
                final String[] locationParams = new String[] { "pipeline",  epilogueURL };
                setLocationData(XmlExtendedLocationData.apply((LocationData) epilogueElement.getData(),
                        "executing epilogue", epilogueElement, locationParams));
            }});
        }
    }

    public static Document getSetValuesDocument(final Element pageElement) {
        final List setValueElements = pageElement.jElements("setvalue");
        final Document setvaluesDocument;
        if (!setValueElements.isEmpty()) {
            // Create document with setvalues
            setvaluesDocument = Document.apply("params");
            // New <setvalue> elements
            if (!setValueElements.isEmpty()) {
                for (Object setValueElement1: setValueElements) {
                    final Element setValueElement = (Element) setValueElement1;
                    setvaluesDocument.getRootElement().add((Element) setValueElement.deepCopy());
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
    public static void handlePage(final StepProcessorContext stepProcessorContext, final String urlBase,
            List<ASTStatement> statementsList, final Element pageElement,
            final String matcherOutputOrParamId,
            final ASTOutput viewData, final ASTOutput epilogueModelData, final ASTOutput viewInstance,
            final Map<String, String> pageIdToPathInfo,
            final Map<String, Document> pageIdToSetvaluesDocument,
            final String instancePassing) {

        // Get page attributes
        final String modelAttribute = pageElement.attributeValue("model");
        final String viewAttribute = pageElement.attributeValue("view");
        final String defaultSubmissionAttribute = pageElement.attributeValue("default-submission");

        // Get setvalues document
        final Document setvaluesDocument = getSetValuesDocument(pageElement);

        // Get actions
        final List actionElements = pageElement.jElements("action");

        // Handle initial instance
        final ASTOutput defaultSubmission = new ASTOutput("data", "default-submission");
        if (defaultSubmissionAttribute != null) {
            statementsList.add(new ASTProcessorCall(XPLConstants.URL_GENERATOR_PROCESSOR_QNAME()) {{
                final String url = URLFactory.createURL(urlBase, defaultSubmissionAttribute).toExternalForm();

                final Document configDocument = Document.apply("config");
                configDocument.getRootElement().addText(url);

                addInput(new ASTInput("config", configDocument));
                addOutput(defaultSubmission);
            }});
        }

        // XForms Input
        final ASTOutput isRedirect = new ASTOutput(null, "is-redirect");

        // Always hook up XML submission
        final ASTOutput xformedInstance = new ASTOutput("instance", "xformed-instance");
        {
            final LocationData locDat = ProcessorSupport.getLocationData();
            xformedInstance.setLocationData(locDat);
        }

        // Use XML Submission pipeline
        statementsList.add(new ASTProcessorCall(XPLConstants.PIPELINE_PROCESSOR_QNAME()) {{
            addInput(new ASTInput("config", new ASTHrefURL(XFORMS_XML_SUBMISSION_XPL)));
            if (setvaluesDocument != null) {
                addInput(new ASTInput("setvalues", setvaluesDocument));
                addInput(new ASTInput("matcher-result", new ASTHrefId(matcherOutputOrParamId)));
            } else {
                addInput(new ASTInput("setvalues", ProcessorSupport.NullDocument()));
                addInput(new ASTInput("matcher-result", ProcessorSupport.NullDocument()));
            }
            if (defaultSubmissionAttribute != null) {
                addInput(new ASTInput("default-submission", new ASTHrefId(defaultSubmission)));
            } else {
                addInput(new ASTInput("default-submission", ProcessorSupport.NullDocument()));
            }
            addOutput(xformedInstance);
        }});

        // Make sure the xformed-instance id is used for p:choose
        statementsList.add(new ASTProcessorCall(XPLConstants.NULL_PROCESSOR_QNAME()) {{
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

                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                    addOutput(new ASTOutput("data", xupdatedInstance));
                }});
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    final Document config = Document.apply("is-redirect");
                    config.getRootElement().addText("true");
                    addInput(new ASTInput("data", config));
                    addOutput(new ASTOutput("data", isRedirect));
                }});
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                    addOutput(new ASTOutput("data", actionData));
                }});
            }});

            for (Object actionElement1: actionElements) {

                // Get info about action
                actionNumber[0]++;
                final Element actionElement = (Element) actionElement1;
                final String whenAttribute; {
                    // NOTE: Prior to 2012-06-27, the XSD schema would put a default value of true()
                    final String tempWhen = actionElement.attributeValue("when");
                    if (tempWhen == null)
                        whenAttribute = "true()";
                    else
                        whenAttribute = tempWhen;
                };
                final String actionAttribute = actionElement.attributeValue("action");

                // Execute action
                addWhen(new ASTWhen() {{

                    // Add condition, remember that we found an <action> without a when
                    if (whenAttribute != null) {
                        if (foundActionWithoutWhen[0])
                            throw new ValidationException("Unreachable <action>", (LocationData) actionElement.getData());
                        setTest(whenAttribute);
                        setNamespaces(NamespaceMapping.apply(Extensions.getNamespaceContextNoDefaultJava(actionElement)));
                        setLocationData((LocationData) actionElement.getData());
                    } else {
                        foundActionWithoutWhen[0] = true;
                    }

                    final boolean resultTestsOnActionData =
                            // Must have an action, in the first place
                            actionAttribute != null &&
                                    // More than one <result>: so at least the first one must have a "when"
                                    actionElement.jElements("result").size() > 1;

                    final ASTOutput internalActionData = actionAttribute == null ? null :
                            new ASTOutput(null, "internal-action-data-" + actionNumber[0]);
                    if (actionAttribute != null) {
                        // TODO: handle passing and modifications of action data in model, and view, and pass to instance
                        addStatement(new StepProcessorCall(stepProcessorContext, urlBase, actionAttribute, "action") {{
                            addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                            addInput(new ASTInput("instance", new ASTHrefId(xformedInstance)));
                            addInput(new ASTInput("xforms-model", ProcessorSupport.NullDocument()));
                            addInput(new ASTInput("matcher", new ASTHrefId(matcherOutputOrParamId)));
                            final ASTOutput dataOutput = new ASTOutput("data", internalActionData);
                            final String[] locationParams =
                                    new String[]{"pipeline", actionAttribute, "page id", pageElement.attributeValue("id"), "when", whenAttribute};
                            dataOutput.setLocationData(XmlExtendedLocationData.apply((LocationData) actionElement.getData(), "reading action data output", pageElement, locationParams));
                            addOutput(dataOutput);
                            setLocationData(XmlExtendedLocationData.apply((LocationData) actionElement.getData(), "executing action", pageElement, locationParams));
                        }});

                        // Force execution of action if no <result> is reading it
                        if (!resultTestsOnActionData) {
                            addStatement(new ASTProcessorCall(XPLConstants.NULL_SERIALIZER_PROCESSOR_QNAME()) {{
                                addInput(new ASTInput("data", new ASTHrefId(internalActionData)));
                            }});
                        }

                        // Export internal-action-data as action-data
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(internalActionData)));
                            addOutput(new ASTOutput("data", actionData));
                        }});
                    } else {
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                            addOutput(new ASTOutput("data", actionData));
                        }});
                    }

                    if (actionElement.jElements("result").size() > 0 && internalActionData != null) {
                        // At least one result testing on action

                        // Test based on action
                        addStatement(new ASTChoose(new ASTHrefId(internalActionData)) {{
                            for (Object o: actionElement.jElements("result")) {
                                final Element resultElement = (Element) o;
                                final String resultWhenAttribute; {
                                    // NOTE: Prior to 2012-06-27, the XSD schema would put a default value of true()
                                    final String tempWhen = resultElement.attributeValue("when");
                                    if (tempWhen == null)
                                        resultWhenAttribute = "true()";
                                    else
                                        resultWhenAttribute = tempWhen;
                                };

                                // Execute result
                                addWhen(new ASTWhen() {{
                                    if (resultWhenAttribute != null) {
                                        setTest(resultWhenAttribute);
                                        setNamespaces(NamespaceMapping.apply(Extensions.getNamespaceContextNoDefaultJava(resultElement)));
                                        final String[] locationParams =
                                                new String[]{"page id", pageElement.attributeValue("id"), "when", resultWhenAttribute};
                                        setLocationData(XmlExtendedLocationData.apply((LocationData) resultElement.getData(), "executing result", resultElement, locationParams));
                                    }
                                    executeResult(this, pageIdToPathInfo, pageIdToSetvaluesDocument,
                                        xformedInstance, resultElement, internalActionData,
                                        isRedirect, xupdatedInstance, instancePassing);
                                }});
                            }

                            // Continue when all results fail
                            addWhen(new ASTWhen() {{
                                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                                    addInput(new ASTInput("data", Document.apply(PageFlowControllerProcessor.createElementWithText("is-redirect", "false"))));
                                    addOutput(new ASTOutput("data", isRedirect));
                                }});
                                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                                    addInput(new ASTInput("data", new ASTHrefId(xformedInstance)));
                                    addOutput(new ASTOutput("data", xupdatedInstance));
                                }});
                            }});
                        }});

                    } else {
                        // No result or result not depending on action
                        final Element resultElement = actionElement.element("result");
                        executeResult(this, pageIdToPathInfo, pageIdToSetvaluesDocument, xformedInstance, resultElement,
                            internalActionData, isRedirect, xupdatedInstance, instancePassing);
                    }
                }});
            }

            if (!foundActionWithoutWhen[0]) {
                // Default branch for when all actions fail
                addWhen(new ASTWhen() {{
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", new ASTHrefId(xformedInstance)));
                        addOutput(new ASTOutput("data", xupdatedInstance));
                    }});
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        final Document config = Document.apply("is-redirect");
                        config.getRootElement().addText("false");
                        addInput(new ASTInput("data", config));
                        addOutput(new ASTOutput("data", isRedirect));
                    }});
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
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
                    addStatement(new StepProcessorCall(stepProcessorContext, urlBase, modelAttribute, "model") {{
                        addInput(new ASTInput("data", new ASTHrefId(actionData)));
                        addInput(new ASTInput("instance", new ASTHrefId(xupdatedInstance)));
                        addInput(new ASTInput("xforms-model", ProcessorSupport.NullDocument()));
                        addInput(new ASTInput("matcher", new ASTHrefId(matcherOutputOrParamId)));
                        final String[] locationParams =
                                new String[] { "page id", pageElement.attributeValue("id"), "model", modelAttribute };
                        {
                            final ASTOutput dataOutput = new ASTOutput("data", modelData);
                            dataOutput.setLocationData(XmlExtendedLocationData.apply((LocationData) pageElement.getData(), "reading page model data output", pageElement, locationParams));
                            addOutput(dataOutput);
                        }
                        {
                            final ASTOutput instanceOutput = new ASTOutput("instance", modelInstance);
                            addOutput(instanceOutput);
                            instanceOutput.setLocationData(XmlExtendedLocationData.apply((LocationData) pageElement.getData(), "reading page model instance output", pageElement, locationParams));
                        }
                        setLocationData(XmlExtendedLocationData.apply((LocationData) pageElement.getData(), "executing page model", pageElement, locationParams));
                    }});
                } else if (viewAttribute != null) {
                    // There is no model but there is a view
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", new ASTHrefId(actionData)));
                        addOutput(new ASTOutput("data", modelData));
                    }});
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", new ASTHrefId(xupdatedInstance)));
                        addOutput(new ASTOutput("data", modelInstance));
                    }});
                }

                // Handle page view
                if (viewAttribute != null) {
                    // There is a view
                    addStatement(new StepProcessorCall(stepProcessorContext, urlBase, viewAttribute, "view") {{
                        addInput(new ASTInput("data", new ASTHrefId(modelData)));
                        addInput(new ASTInput("instance", new ASTHrefId(modelInstance)));
                        addInput(new ASTInput("xforms-model", ProcessorSupport.NullDocument()));
                        addInput(new ASTInput("matcher", new ASTHrefId(matcherOutputOrParamId)));
                        final String[] locationParams =
                                new String[] { "page id", pageElement.attributeValue("id"), "view", viewAttribute };
                        {
                            final ASTOutput dataOutput = new ASTOutput("data", viewData);
                            dataOutput.setLocationData(XmlExtendedLocationData.apply((LocationData) pageElement.getData(), "reading page view data output", pageElement, locationParams));
                            addOutput(dataOutput);
                        }
                        {
                            final ASTOutput instanceOutput = new ASTOutput("instance", viewInstance);
                            instanceOutput.setLocationData(XmlExtendedLocationData.apply((LocationData) pageElement.getData(), "reading page view instance output", pageElement, locationParams));
                            addOutput(instanceOutput);
                        }
                        setLocationData(XmlExtendedLocationData.apply((LocationData) pageElement.getData(), "executing page view", pageElement, locationParams));
                    }});
                } else {
                    // There is no view, send nothing to epilogue
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                        addOutput(new ASTOutput("data", viewData));
                    }});
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                        addOutput(new ASTOutput("data", viewInstance));
                    }});
                }

                if (modelAttribute != null && viewAttribute == null) {
                    // With XForms NG we want lazy evaluation of the instance, so we should not force a
                    // read on the instance. We just connect the output.
                    addStatement(new ASTProcessorCall(XPLConstants.NULL_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelInstance)));
                    }});
                }

                if (modelAttribute == null && viewAttribute == null) {
                    // Send out epilogue model data as a null document
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                        addOutput(new ASTOutput("data", epilogueModelData));
                    }});
                } else {
                    // Send out epilogue model data as produced by the model or used by the view
                    addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("data", new ASTHrefId(modelData)));
                        addOutput(new ASTOutput("data", epilogueModelData));
                    }});
                }

            }});
            addWhen(new ASTWhen() {{
                // There is a redirection due to the action

                // With XForms NG we want lazy evaluation of the instance, so we should not force a
                // read on the instance. We just connect the output.
                addStatement(new ASTProcessorCall(XPLConstants.NULL_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", new ASTHrefId(xupdatedInstance)));
                }});
                // Just connect the output
                addStatement(new ASTProcessorCall(XPLConstants.NULL_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", new ASTHrefId(actionData)));
                }});
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                    addOutput(new ASTOutput("data", viewData));
                }});
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                    addOutput(new ASTOutput("data", epilogueModelData));
                }});
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", ProcessorSupport.NullDocument()));
                    addOutput(new ASTOutput("data", viewInstance));
                }});
            }});
        }});
    }

    private static void executeResult(ASTWhen when,
            final Map<String, String> pageIdToPathInfo, final Map<String, Document> pageIdToSetvaluesDocument,
            final ASTOutput instanceToUpdate, final Element resultElement,
            final ASTOutput actionData, final ASTOutput redirect, final ASTOutput xupdatedInstance,
            String instancePassing) {

        // Instance to update: either current, or instance from other page
        final String resultPageId = resultElement == null ? null : resultElement.attributeValue("page");
        Attribute instancePassingAttribute = resultElement == null ? null : resultElement.attribute("instance-passing");
        final String _instancePassing = instancePassingAttribute == null ? instancePassing : instancePassingAttribute.getValue();

        // Create resulting instance
        final ASTOutput internalXUpdatedInstance;
        final boolean isTransformedInstance;
        if (resultElement != null && resultElement.attribute("transform") != null && !resultElement.jElements().isEmpty()) {
            // Generic transform mechanism
            internalXUpdatedInstance = new ASTOutput("data", "internal-xupdated-instance");
            isTransformedInstance = true;

            final Document transformConfig = Extensions.createDocumentCopyParentNamespacesJava(resultElement.jElements().get(0), false);
            final QName transformQName = Extensions.resolveAttValueQNameJava(resultElement, "transform");

            // Run transform
            final String resultTraceAttribute = resultElement.attributeValue("trace");
            when.addStatement(new ASTProcessorCall(transformQName) {{
                addInput(new ASTInput("config", transformConfig));// transform
                addInput(new ASTInput("instance", new ASTHrefId(instanceToUpdate)));// source-instance
                addInput(new ASTInput("data", new ASTHrefId(instanceToUpdate)));// destination-instance
                //addInput(new ASTInput("request-instance", new ASTHrefId(requestInstance)));// params-instance TODO
                if (actionData != null)
                    addInput(new ASTInput("action", new ASTHrefId(actionData)));// action
                else
                    addInput(new ASTInput("action",  ProcessorSupport.NullDocument()));// action
                addOutput(new ASTOutput("data", internalXUpdatedInstance) {{ setDebug(resultTraceAttribute);}});// updated-instance
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
                    when.addStatement(new ASTProcessorCall(XPLConstants.INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("instance", new ASTHrefId(internalXUpdatedInstance)));
                        addInput(new ASTInput("filter", (setvaluesDocument != null) ? setvaluesDocument : ProcessorSupport.NullDocument()));
                        addOutput(new ASTOutput("data", parametersOutput));
                    }});
                } else {
                    parametersOutput = null;
                }
                // Handle path info
                final ASTOutput forwardPathInfoOutput = new ASTOutput(null, "forward-path-info");
                when.addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", Document.apply(PageFlowControllerProcessor.createElementWithText("path-info", forwardPathInfo))));
                    addOutput(new ASTOutput("data", forwardPathInfoOutput));
                }});
                // Handle server-side redirect and exit portal redirect
                final ASTOutput isServerSideRedirectOutput = new ASTOutput(null, "is-server-side-redirect");
                when.addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", Document.apply(PageFlowControllerProcessor.createElementWithText("server-side", Boolean.toString(doServerSideRedirect)))));
                    addOutput(new ASTOutput("data", isServerSideRedirectOutput));
                }});
                final ASTOutput isRedirectExitPortal = new ASTOutput(null, "is-redirect-exit-portal");
                when.addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", Document.apply(PageFlowControllerProcessor.createElementWithText("exit-portal", Boolean.toString(doRedirectExitPortal)))));
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

                    when.addStatement(new ASTProcessorCall(XPLConstants.UNSAFE_XSLT_PROCESSOR_QNAME()) {{
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
                when.addStatement(new ASTProcessorCall(XPLConstants.REDIRECT_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", redirectURLData));// {{setDebug("redirect 2");}}
                    final String[] locationParams =
                            new String[] { "result page id", resultPageId  };
                    setLocationData(XmlExtendedLocationData.apply((LocationData) resultElement.getData(),
                            "page redirection", resultElement, locationParams));
                }});
            }
        }

        // Signal if we did a redirect
        when.addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
            addInput(new ASTInput("data", Document.apply(PageFlowControllerProcessor.createElementWithText("is-redirect", Boolean.toString(resultPageId != null)))));
            addOutput(new ASTOutput("data", redirect));
        }});

        // Export XUpdated instance from this branch
        when.addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
            addInput(new ASTInput("data", new ASTHrefId(internalXUpdatedInstance)));
            addOutput(new ASTOutput("data", xupdatedInstance));
        }});
    }

    /**
     * Creates a single StepProcessor. This should be called only once for a given Page Flow
     * configuration. Then the same StepProcessor should be used for each step.
     */
    public static class StepProcessorContext {

        private PipelineConfig pipelineConfig;

        public StepProcessorContext(final Object controllerValidity) {
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
                        addStatement(new ASTProcessorCall(XPLConstants.XSLT_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefAggregate("root",
                                    new ASTHrefId(stepURLInput), new ASTHrefId(matcherInput))));
                            addInput(new ASTInput("config", new ASTHrefURL(REWRITE_XSL)));
                            addOutput(new ASTOutput("data", rewroteStepURL));
                        }});
                    }});
                    addWhen(new ASTWhen() {{
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(stepURLInput)));
                            addOutput(new ASTOutput("data", rewroteStepURL));
                        }});
                    }});
                }});

                final ASTOutput contentXIncluded = new ASTOutput("data", "content-xincluded");
                {
                    // Read file to "execute"
                    final ASTOutput content = new ASTOutput("data", "content");
                    addStatement(new ASTProcessorCall(XPLConstants.URL_GENERATOR_PROCESSOR_QNAME()) {{
                        addInput(new ASTInput("config", new ASTHrefId(rewroteStepURL)));
                        addOutput(content);
                    }});

                    // Insert XInclude processor to process content with XInclude
                    addStatement(new ASTProcessorCall(XPLConstants.XINCLUDE_PROCESSOR_QNAME()) {{
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
                                addStatement(new ASTProcessorCall(XPLConstants.ERROR_PROCESSOR_QNAME()) {{
                                    final Document errorDocument = Document.apply("error");
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
                        addStatement(new ASTProcessorCall(XPLConstants.PIPELINE_PROCESSOR_QNAME()) {{
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
                        addStatement(new ASTProcessorCall(XPLConstants.PIPELINE_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            final ASTOutput datOut = new ASTOutput( "data", resultData );
                            addOutput( datOut );
                        }});
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput datOut = new ASTOutput( "data", resultInstance );
                            addOutput( datOut );
                        }});
                    }});

                    // XPL file with only instance output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline' " +
                            "and /*/*[local-name() = 'param' and @type = 'output' and @name = 'instance']") {{
                        addStatement(new ASTProcessorCall(XPLConstants.PIPELINE_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                            final ASTOutput instOut = new ASTOutput( "instance", resultInstance );
                            addOutput( instOut );
                        }});
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            final ASTOutput resDatOut = new ASTOutput( "data", resultData );
                            addOutput( resDatOut );
                        }});
                    }});

                    // XPL file with no output
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/pipeline'") {{
                        addStatement(new ASTProcessorCall(XPLConstants.PIPELINE_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("config", new ASTHrefId(contentXIncluded)));
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
                            addInput(new ASTInput("xforms-model", new ASTHrefId(xformsModelInput)));
                        }});
                        // Simply bypass data and instance channels
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            final ASTOutput resDatOut = new ASTOutput( "data", resultData );
                            addOutput( resDatOut );
                        }});
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput( "data", resultInstance );
                            addOutput( resInstOut );
                        }});
                    }});

                    // PFC file (should only work as model)
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.orbeon.com/oxf/controller'") {{
                        addStatement(new ASTProcessorCall(XPLConstants.PAGE_FLOW_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("controller", new ASTHrefId(contentXIncluded)));
                        }});
                        // Simply bypass data and instance channels
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
                            final ASTOutput resDatOut = new ASTOutput( "data", resultData );
                            addOutput( resDatOut );
                        }});
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput( "data", resultInstance );
                            addOutput( resInstOut );
                        }});
                    }});

                    // XSLT file (including XSLT 2.0 "Simplified Stylesheet Modules")
                    addWhen(new ASTWhen("namespace-uri(/*) = 'http://www.w3.org/1999/XSL/Transform' or /*/@xsl:version = '2.0'") {{
                        setNamespaces(NAMESPACES_WITH_XSI_AND_XSLT);

                        // Copy the instance as is
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput( "data", resultInstance );
                            addOutput( resInstOut );
                        }});

                        // Process XInclude
//                        final ASTOutput xincludedContent = new ASTOutput("data", "xincluded-content");
//                        addStatement(new ASTProcessorCall(XPLConstants.XINCLUDE_PROCESSOR_QNAME) {{
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
                                addXSLTWhen("not(/*/@xsl:version = '2.0') and /*/@version = '1.0'", XPLConstants.PFC_XSLT10_PROCESSOR_QNAME());

                                // XSLT 2.0: There is an xsl:version = '2.0' attribute or the namespace or the root
                                //           element is xsl and the version is different from '1.0'
                                addXSLTWhen(null, XPLConstants.PFC_XSLT20_PROCESSOR_QNAME());
                        }});
                    }});

                    // XML file
                    addWhen(new ASTWhen() {{

                        // Copy the instance as is
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(instanceInput)));
                            final ASTOutput resInstOut = new ASTOutput("data", resultInstance);
                            addOutput(resInstOut);
                        }});

                        // Copy the data as is
                        addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                            addInput(new ASTInput("data", new ASTHrefId(contentXIncluded)));
                            final ASTOutput resDatOut = new ASTOutput("data", resultData);
                            addOutput(resDatOut);
                        }});

                        // Insert XInclude processor to process static XML file with XInclude
//                        addStatement(new ASTProcessorCall(XPLConstants.XINCLUDE_PROCESSOR_QNAME) {{
//                            addInput(new ASTInput("config", new ASTHrefId(content)));
//                            addInput(new ASTInput("data", new ASTHrefId(dataInput)));
//                            addInput(new ASTInput("instance", new ASTHrefId(instanceInput)));
//                            final ASTOutput resDatOut = new ASTOutput("data", resultData);
//                            addOutput(resDatOut);
//                        }});
                    }});
                }});

                // Connect results
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
                    addInput(new ASTInput("data", new ASTHrefId(resultData)));
                    addOutput(new ASTOutput("data", dataOutput));
                }});
                addStatement(new ASTProcessorCall(XPLConstants.IDENTITY_PROCESSOR_QNAME()) {{
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

            // Create document and input for URI
            final String url = URLFactory.createURL(controllerContext, uri).toExternalForm();
            final Document configDocument;
            {
                configDocument = Document.apply("config");
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
            final Document stepTypeDocument = Document.apply("step-type");
            stepTypeDocument.getRootElement().addText(stepType);
            addInput(new ASTInput("step-type", stepTypeDocument));
        }
    }
}
