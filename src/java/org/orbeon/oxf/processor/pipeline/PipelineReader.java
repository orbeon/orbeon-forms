/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.processor.pipeline;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oro.text.regex.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.XMLProcessorRegistry;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.processor.pipeline.foreach.AbstractForEachProcessor;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.*;

public class PipelineReader extends ProcessorImpl {

    private static final Map<String, String> PREFIXES = new HashMap<String, String>();
    private static Pattern IDENTIFIER;
    private static Pattern END;
    private static Pattern ID_REFERENCE;
    private static Pattern FUNCTION_CALL;
    private static Pattern ROOT_ELEMENT_WITHOUT_NS;
    private static Pattern ROOT_ELEMENT_WITH_NS;
    private static Pattern FUNCTION_END;
    private static Pattern FUNCTION_PARAMETER;
    private static Pattern URL;
    private static Pattern XPOINTER;

    static {
        PREFIXES.put("p", PipelineProcessor.PIPELINE_NAMESPACE_URI);

        // Used for regexp pattern initialization
        Perl5Compiler compiler = new Perl5Compiler();
        String SEPARATOR_REGEXP = "[ \\t]*";
        String IDENTIFIER_REGEXP = "[_A-Za-z][_A-Za-z\\-0-9.]*";

        // Initialize regexp patterns
        try {
            IDENTIFIER = compiler.compile("^" + IDENTIFIER_REGEXP + "$");
            END = compiler.compile("^" + SEPARATOR_REGEXP + "$");
            ID_REFERENCE = compiler.compile("^" + SEPARATOR_REGEXP + "#(" + IDENTIFIER_REGEXP + ")");
            FUNCTION_CALL = compiler.compile("^" + SEPARATOR_REGEXP + "(" + IDENTIFIER_REGEXP + ")" + SEPARATOR_REGEXP + "\\(");
            ROOT_ELEMENT_WITHOUT_NS = compiler.compile("^" + SEPARATOR_REGEXP + "'(" + IDENTIFIER_REGEXP + ")'");
            ROOT_ELEMENT_WITH_NS = compiler.compile("^" + SEPARATOR_REGEXP + "'(" + IDENTIFIER_REGEXP + ":" + IDENTIFIER_REGEXP + ")'");
            FUNCTION_END = compiler.compile("^" + SEPARATOR_REGEXP + "\\)");
            FUNCTION_PARAMETER = compiler.compile("^" + SEPARATOR_REGEXP + ",");
            URL = compiler.compile("^" + SEPARATOR_REGEXP + "([^ \\t,\\)#]+)");
            XPOINTER = compiler.compile("^#xpointer\\(");
        } catch (MalformedPatternException e) {
            throw new OXFException(e);
        }
    }

    private ASTPipeline pipeline; // TODO Bug no instance variables

    public PipelineReader() {
        addInputInfo(new ProcessorInputOutputInfo("pipeline"));
    }

    public void start(PipelineContext context) {
        final Document pipelineDocument = readInputAsDOM4J(context, "pipeline");
        pipeline = readPipeline(pipelineDocument, getInputValidity(context, getInputByName("pipeline")));
    }

    public static ASTPipeline readPipeline(Document pipelineDocument, Object validity) {

        final Element rootElement = pipelineDocument.getRootElement();

        final ASTPipeline ast = new ASTPipeline();
        ast.setValidity(validity);

        List<ASTParam> params = new ArrayList<ASTParam>();

        // Read params
        for (final Iterator i = XPathUtils.selectIterator(rootElement, "p:param", PREFIXES); i.hasNext();) {
            final Element paramElement = (Element) i.next();
            final ASTParam param = new ASTParam();

            param.setNode(paramElement);
            param.setName(paramElement.attributeValue("name"));
            param.setType(paramElement.attributeValue("type").equals("input") ? ASTParam.INPUT : ASTParam.OUTPUT);
            param.setDebug(paramElement.attributeValue("debug"));
            param.setSchemaHref(paramElement.attributeValue("schema-href"));
            param.setSchemaUri(paramElement.attributeValue("schema-uri"));
            params.add(param);
        }

        ast.setNode(rootElement);
        ast.getParams().addAll(params);

        // Read and add all statements
        ast.getStatements().addAll(readStatements(rootElement));

        return ast;
    }

    private static List<ASTStatement> readStatements(Element containerElement) {

        List<ASTStatement> result = new ArrayList<ASTStatement>();
        PatternMatcher matcher = new Perl5Matcher();
        for (Iterator i = containerElement.elementIterator(); i.hasNext();) {
            final Element element = (Element) i.next();
            if (element.getName().equals("processor")) {
                // Processor
                ASTProcessorCall processorCall = new ASTProcessorCall(XMLProcessorRegistry.extractProcessorQName(element), element.attributeValue("uri")); {
                    result.add(processorCall);
                    processorCall.setNode(element);
                    processorCall.setId(element.attributeValue("id"));
                    processorCall.setEncapsulation(element.attributeValue("encapsulation"));

                    // Inputs/outputs
                    for (Iterator j = element.elementIterator(); j.hasNext();) {
                        final Element inputOutputElement = (Element) j.next();

                        // Read common attributes
                        class readCommonAttributes {
                            readCommonAttributes(ASTInputOutput inputOutput) {
                                inputOutput.setNode(inputOutputElement);
                                inputOutput.setName(inputOutputElement.attributeValue("name"));
                                inputOutput.setSchemaHref(inputOutputElement.attributeValue("schema-href"));
                                inputOutput.setSchemaUri(inputOutputElement.attributeValue("schema-uri"));
                                inputOutput.setDebug(inputOutputElement.attributeValue("debug"));
                                Iterator childrenIterator = inputOutputElement.elementIterator();
                                if(childrenIterator.hasNext())
                                    inputOutput.setContent((Element) childrenIterator.next());
                            }
                        }

                        // Read attributes specific to input or output
                        if (inputOutputElement.getName().equals("input")) {
                            ASTInput input = new ASTInput(); {
                                processorCall.addInput(input);
                                input.setHref(readHref(inputOutputElement, inputOutputElement.attributeValue("href")));
                                input.setTransform(Dom4jUtils.extractAttributeValueQName(inputOutputElement, "transform"));
                                new readCommonAttributes(input);
                            }
                        } else {
                            ASTOutput output = new ASTOutput(); {
                                processorCall.addOutput(output);
                                String id = inputOutputElement.attributeValue("id");
                                if (id != null) {
                                    if (! matcher.contains(id, IDENTIFIER))
                                        throw new ValidationException("Invalid identifier '" + id
                                                + "' in 'id' attribute",
                                                (LocationData) (inputOutputElement).getData());
                                    output.setId(id);
                                }
                                String ref = inputOutputElement.attributeValue("ref");
                                if (ref != null) {
                                    if (! matcher.contains(ref, IDENTIFIER))
                                        throw new ValidationException("Invalid identifier '" + ref
                                                + "' in 'id' attribute",
                                                (LocationData) (inputOutputElement).getData());
                                    output.setRef(ref);
                                }
                                new readCommonAttributes(output);
                            }
                        }
                    }
                }
            } else if (element.getName().equals("choose")) {

                // ASTChoose
                ASTChoose choose = new ASTChoose(); {
                    result.add(choose);
                    choose.setNode(element);
                    choose.setHref(readHref(element, element.attributeValue("href")));
                    choose.setSchemaHref(element.attributeValue("schema-href"));
                    choose.setSchemaUri(element.attributeValue("schema-uri"));
                    choose.setDebug(element.attributeValue("debug"));

                    for (Iterator j = element.elementIterator(); j.hasNext();) {
                        final Element whenElement = (Element) j.next();
                        ASTWhen when = new ASTWhen(); {
                            choose.addWhen(when);
                            when.setNode(whenElement);
                            when.setTest(whenElement.attributeValue("test"));
                            when.getStatements().addAll(readStatements(whenElement));
                        }
                    }
                }
            } else  if (element.getName().equals("for-each")) {

                // ASTForEach
                ASTForEach forEach = new ASTForEach(); {
                    result.add(forEach);
                    forEach.setNode(element);
                    forEach.setHref(readHref(element, element.attributeValue("href")));
                    forEach.setSelect(element.attributeValue("select"));
                    forEach.setId(element.attributeValue("id"));
                    forEach.setRef(element.attributeValue("ref"));
                    forEach.setRoot(element.attributeValue("root"));
                    forEach.setInputSchemaHref(element.attributeValue("input-schema-href"));
                    forEach.setInputSchemaUri(element.attributeValue("input-schema-uri"));
                    forEach.setInputDebug(element.attributeValue("input-debug"));
                    forEach.setOutputSchemaHref(element.attributeValue("output-schema-href"));
                    forEach.setOutputSchemaUri(element.attributeValue("output-schema-uri"));
                    forEach.setOutputDebug(element.attributeValue("output-debug"));
                    forEach.getStatements().addAll(readStatements(element));
                }
            }
        }
        return result;
    }

    private static ASTHref readHref(Node node, String href) {

        LocationData locationData = (LocationData) ((Element) node).getData();
        HrefResult result = readHrefWorker(locationData, href);

        // Make sure that everything was consumed
        PatternMatcher matcher = new Perl5Matcher();
        if (! matcher.contains(result.rest, END))
            throw new ValidationException("Can't parse \"" + result.rest + "\" in href", locationData);
        return result.astHref;
    }

    private static HrefResult readHrefWorker(LocationData locationData, String href) {

        HrefResult result = new HrefResult();
        PatternMatcher matcher = new Perl5Matcher();

        if (href == null) {
            // href="..." is not always mandatory
        } else if (matcher.contains(href, ID_REFERENCE)) {
            // Reference to an id
            String id = matcher.getMatch().group(1);
            ASTHrefId hrefId = new ASTHrefId();
            hrefId.setId(id);
            result.astHref = hrefId;
            result.rest = href.substring(matcher.getMatch().endOffset(0));
        } else if (matcher.contains(href, FUNCTION_CALL)) {
            // Try to parse this as a "function call"

            // Parse function name ("aggregate")
            String functionName = matcher.getMatch().group(1);
            if ("aggregate".equals(functionName)) {
                href = href.substring(matcher.getMatch().endOffset(0));

                // Parse first argument (root element)
                if (!matcher.contains(href, ROOT_ELEMENT_WITHOUT_NS))
                    if (!matcher.contains(href, ROOT_ELEMENT_WITH_NS))
                        throw new ValidationException("Invalid element name in \"" + href + "\"", locationData);
                String rootElementName = matcher.getMatch().group(1);
                href = href.substring(matcher.getMatch().endOffset(0));

                // Parse parameters
                List<ASTHref> hrefParameters = new ArrayList<ASTHref>();
                while (true) {
                    if (matcher.contains(href, FUNCTION_END)) {
                        // We are at the end of the function call
                        ASTHrefAggregate hrefAggregate = new ASTHrefAggregate();
                        hrefAggregate.setRoot(rootElementName);
                        hrefAggregate.setHrefs(hrefParameters);
                        result.rest = href.substring(matcher.getMatch().endOffset(0));
                        result.astHref = hrefAggregate;
                        break;
                    } else if (matcher.contains(href, FUNCTION_PARAMETER)) {
                        // We've got an other parameter
                        href = href.substring(matcher.getMatch().endOffset(0));
                        HrefResult parameterResult = readHrefWorker(locationData, href);
                        hrefParameters.add(parameterResult.astHref);
                        href = parameterResult.rest;
                    } else {
                        throw new ValidationException("Can't find \")\" or other href parameter in \""
                            + href + "\"", locationData);
                    }
                }
            } else if ("current".equals(functionName)) {
                href = href.substring(matcher.getMatch().endOffset(0));
                if (!matcher.contains(href, FUNCTION_END))
                    throw new ValidationException("Expected ')' in current() function call", locationData);
                ASTHrefId hrefId = new ASTHrefId();
                hrefId.setId(AbstractForEachProcessor.FOR_EACH_CURRENT_INPUT);
                result.astHref = hrefId;
                result.rest = href.substring(matcher.getMatch().endOffset(0));
            } else {
                throw new ValidationException("Unsupported function \"" + functionName + "\"", locationData);
            }
        } else if (matcher.contains(href, URL)) {
            // URL
            ASTHrefURL hrefURL = new ASTHrefURL();
            hrefURL.setURL(matcher.getMatch().group(1));
            result.astHref = hrefURL;
            result.rest = href.substring(matcher.getMatch().endOffset(0));
        } else {
            throw new ValidationException("Can't find id, URL or function call in \"" +
                    href + "\"", locationData);
        }

        // Handle optional XPointer expression
        if (matcher.contains(result.rest, XPOINTER)) {
            int parenthesisDepth = 0;
            boolean inString = false;
            char quoteType = 0;

            String rest = result.rest.substring(matcher.getMatch().endOffset(0));
            StringBuffer xpath = new StringBuffer();

            while(true) {
                if (inString) {
                    // Look for end of this string
                    int position = rest.indexOf(quoteType);
                    if (position == -1)
                        throw new ValidationException("Unterminated string", locationData);
                    xpath.append(rest.substring(0, position + 1));
                    rest = rest.substring(position + 1);
                    inString = false;
                } else {
                    int firstSingleQuote = rest.indexOf('\'');
                    int firstDoubleQuote = rest.indexOf('"');
                    int firstOpeningParenthesis = rest.indexOf('(');
                    int firstClosingParenthesis = rest.indexOf(')');
                    if (firstSingleQuote == -1) firstSingleQuote = Integer.MAX_VALUE;
                    if (firstDoubleQuote == -1) firstDoubleQuote = Integer.MAX_VALUE;
                    if (firstOpeningParenthesis == -1) firstOpeningParenthesis = Integer.MAX_VALUE;
                    if (firstClosingParenthesis == -1) firstClosingParenthesis = Integer.MAX_VALUE;

                    if (firstSingleQuote < firstDoubleQuote && firstSingleQuote < firstOpeningParenthesis
                            && firstSingleQuote < firstClosingParenthesis) {
                        // Start single quoted string
                        quoteType = '\'';
                        inString = true;
                        xpath.append(rest.substring(0, firstSingleQuote + 1));
                        rest = rest.substring(firstSingleQuote + 1);
                    } else if (firstDoubleQuote < firstSingleQuote && firstDoubleQuote < firstOpeningParenthesis
                            && firstDoubleQuote < firstClosingParenthesis) {
                        // Start double quoted string
                        quoteType = '"';
                        inString = true;
                        xpath.append(rest.substring(0, firstDoubleQuote + 1));
                        rest = rest.substring(firstDoubleQuote + 1);
                    } else if (firstOpeningParenthesis < firstSingleQuote && firstOpeningParenthesis < firstDoubleQuote
                            && firstOpeningParenthesis < firstClosingParenthesis) {
                        // Opening parenthesis
                        parenthesisDepth++;
                        xpath.append(rest.substring(0, firstOpeningParenthesis + 1));
                        rest = rest.substring(firstOpeningParenthesis + 1);
                    } else if (firstClosingParenthesis < firstSingleQuote && firstClosingParenthesis < firstDoubleQuote
                            && firstClosingParenthesis < firstOpeningParenthesis) {
                        // Closing parenthesis
                        if (parenthesisDepth == 0) {
                            // We're at the end of the XPointer expression
                            xpath.append(rest.substring(0, firstClosingParenthesis));
                            ASTHrefXPointer hrefXPointer = new ASTHrefXPointer();
                            hrefXPointer.setHref(result.astHref);
                            hrefXPointer.setXpath(xpath.toString());
                            HrefResult xpointerResult = new HrefResult();
                            xpointerResult.astHref = hrefXPointer;
                            xpointerResult.rest = rest.substring(firstClosingParenthesis + 1);
                            result = xpointerResult;
                            break;
                        } else {
                            parenthesisDepth--;
                            xpath.append(rest.substring(0, firstClosingParenthesis + 1));
                            rest = rest.substring(firstClosingParenthesis + 1);
                        }
                    } else {
                        throw new ValidationException("Expected single quote, double quote, opening parenthesis "
                                + "or closing parenthesis in XPointer expression: \"" + rest + "\"", locationData);
                    }
                }
            }
        }

        return result;
    }

    private static class HrefResult {
        public ASTHref astHref;
        public String rest = "";
    }

    public ASTPipeline getPipeline() {
        return pipeline;
    }
}
