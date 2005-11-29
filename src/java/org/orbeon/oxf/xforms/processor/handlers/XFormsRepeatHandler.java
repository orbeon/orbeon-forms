/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Map;

/**
 * Handle xforms:repeat.
 */
public class XFormsRepeatHandler extends HandlerBase {

    public XFormsRepeatHandler() {
        // This is a repeating element
        super(true, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String repeatId = attributes.getValue("id");
        final String effectiveId = handlerContext.getEffectiveId(attributes);

        final boolean isTopLevelRepeat = handlerContext.countParentRepeats() == 0;
        final boolean isRepeatSelected = handlerContext.isRepeatSelected() || isTopLevelRepeat;
        final boolean isGenerateTemplate = handlerContext.isGenerateTemplate() || isTopLevelRepeat;
        final int currentIteration = handlerContext.getCurrentIteration();

        final XFormsControls.ControlsState currentControlState = containingDocument.getXFormsControls().getCurrentControlsState();
        final Map effectiveRepeatIdToIterations = currentControlState.getEffectiveRepeatIdToIterations();
        final Map repeatIdToIndex = currentControlState.getRepeatIdToIndex();

        final int currentRepeatIndex = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : ((Integer) repeatIdToIndex.get(repeatId)).intValue();
//        System.out.println("Effective id: " + effectiveId + "; effectiveRepeatIdToIterations null: " + (effectiveRepeatIdToIterations == null));
        final int currentRepeatIterations = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : ((Integer) effectiveRepeatIdToIterations.get(effectiveId)).intValue();

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        // Place interceptor on output
        final DeferredContentHandler savedOutput = handlerContext.getController().getOutput();
        final OutputInterceptor outputInterceptor = new OutputInterceptor(savedOutput, effectiveId, spanQName);
        handlerContext.getController().setOutput(new DeferredContentHandlerImpl(outputInterceptor));

        setContentHandler(handlerContext.getController().getOutput());

        if (isTopLevelRepeat || !isGenerateTemplate) {
            // Unroll repeat

            final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);
            for (int i = 1; i <= currentRepeatIterations; i++) {
                if (i > 1) {
                    // Delimiter: between repeat entries
                    outputRepeatDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(), outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), null);
                }

                // Is the current iteration selected?
                final boolean isCurrentRepeatSelected = isRepeatSelected && i == currentRepeatIndex;
                final boolean isCurrentRepeatRelevant = ((XFormsControls.RepeatIterationInfo) repeatControlInfo.getChildren().get(i - 1)).isRelevant();
                final int numberParentRepeat = handlerContext.countParentRepeats();

                // Determine classes to add on root elements and around root characters
                final StringBuffer addedClasses;
                {
                    addedClasses = new StringBuffer();
                    if (isCurrentRepeatSelected) {
                        addedClasses.append("xforms-repeat-selected-item-");
                        addedClasses.append((numberParentRepeat % 2 == 0) ? '1' : '2');
                    }
                    if (!isCurrentRepeatRelevant)
                        addedClasses.append(" xforms-disabled");
                }
                outputInterceptor.setAddedClasses(addedClasses);

                // Apply the content of the body for this iteration
                handlerContext.pushRepeatContext(false, i, false, isCurrentRepeatSelected);
                handlerContext.getController().repeatBody();
                outputInterceptor.flushCharacters(true);
                handlerContext.popRepeatContext();
            }
        }

        if (isGenerateTemplate) {
            // Generate template

//            if (currentRepeatIterations > 0)
//                mustGenerateFirstDelimiters[0] = true;

            if (!outputInterceptor.isMustGenerateFirstDelimiters()) {
                // Delimiter: between repeat entries
                outputRepeatDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(), outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), null);
            }

            // Determine classes to add on root elements and around root characters
            outputInterceptor.setAddedClasses(new StringBuffer(isTopLevelRepeat ? "xforms-repeat-template" : ""));

            // Apply the content of the body for this iteration
            handlerContext.pushRepeatContext(true, 0, false, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true);
            handlerContext.popRepeatContext();
        }

        if (outputInterceptor.getDelimiterNamespaceURI() == null) {
            // No delimiter has been generated, try to find one!

            outputInterceptor.setForward(false); // prevent interceptor to output anything

            handlerContext.pushRepeatContext(true, 0, false, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true);
            handlerContext.popRepeatContext();
        }

        // Restore output
        handlerContext.getController().setOutput(savedOutput);
        setContentHandler(savedOutput);

        // Delimiter: end repeat
        outputRepeatDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(), outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "repeat-end-" + effectiveId);
    }

    private void outputRepeatDelimiter(ContentHandler contentHandler, String delimiterNamespaceURI, String delimiterPrefix, String delimiterLocalName, String id) throws SAXException {

        reusableAttributes.clear();
        if (id != null) {
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, id);
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-repeat-begin-end");
        } else {
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-repeat-delimiter");
        }

        final String delimiterQName = XMLUtils.buildQName(delimiterPrefix, delimiterLocalName);
        contentHandler.startElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName, reusableAttributes);
        contentHandler.endElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
    }

    private class OutputInterceptor extends ForwardingContentHandler {

        private String effectiveId;
        private String spanQName;

        private String delimiterNamespaceURI;
        private String delimiterPrefix;
        private String delimiterLocalName;

        private StringBuffer addedClasses;

        private boolean mustGenerateFirstDelimiters = true ;

        private int level;
        private boolean isCharacters;
        private StringBuffer currentCharacters = new StringBuffer();

        public OutputInterceptor(ContentHandler output, String effectiveId, String spanQName) {
            super(output);
            this.effectiveId = effectiveId;
            this.spanQName = spanQName;
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            flushCharacters(false);

            // The first element received determines the type of separator
            checkDelimiters(uri, qName);

            // Add or update classes on element if needed
            super.startElement(uri, localname, qName, (level == 0) ? getAttributesWithClass(attributes) : attributes);

            level++;
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {
            level--;

            flushCharacters(false);
            super.endElement(uri, localname, qName);
        }

        public void characters(char[] chars, int start, int length) {
            currentCharacters.append(chars, start, length);
            isCharacters = true;
        }

        public void flushCharacters(boolean finalFlush) throws SAXException {

            if (currentCharacters.length() > 0) {

                final String currentString = currentCharacters.toString();
                final char[] chars = currentString.toCharArray();
                if (XMLUtils.isBlank(currentString) || level > 0) {
                    // Just output whitespace as is
                    super.characters(chars, 0, chars.length);
                } else {

                    // The first element received determines the type of separator
                    checkDelimiters(XMLConstants.XHTML_NAMESPACE_URI, spanQName);

                    // Wrap any other text within an xhtml:span
                    super.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, getAttributesWithClass(XMLUtils.EMPTY_ATTRIBUTES));
                    super.characters(chars, 0, chars.length);
                    super.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }

                isCharacters = false;
                currentCharacters.setLength(0);
            }

            if (finalFlush)
                checkDelimiters(XMLConstants.XHTML_NAMESPACE_URI, spanQName);
        }

        private void checkDelimiters(String uri, String qName) throws SAXException {

            if (level == 0 && delimiterNamespaceURI == null) {
                delimiterNamespaceURI = uri;
                delimiterPrefix = XMLUtils.prefixFromQName(qName);
                delimiterLocalName = XMLUtils.localNameFromQName(qName);
            }

            if (mustGenerateFirstDelimiters) {
                // Delimiter: begin repeat
                outputRepeatDelimiter(getContentHandler(), delimiterNamespaceURI, delimiterPrefix, delimiterLocalName, "repeat-begin-" + effectiveId);
                outputRepeatDelimiter(getContentHandler(), delimiterNamespaceURI, delimiterPrefix, delimiterLocalName, null);

                mustGenerateFirstDelimiters = false;
            }
        }

        private Attributes getAttributesWithClass(Attributes originalAttributes) {
            String newClassAttribute = originalAttributes.getValue("class");

            if (addedClasses != null && addedClasses.length() > 0) {
                if (newClassAttribute == null) {
                    newClassAttribute = addedClasses.toString();
                } else {
                    newClassAttribute += " " + addedClasses;
                }
            }

            if (newClassAttribute != null)
                return XMLUtils.addOrReplaceAttribute(originalAttributes, "", "", "class", newClassAttribute);
            else
                return originalAttributes;
        }

        public String getDelimiterNamespaceURI() {
            return delimiterNamespaceURI;
        }

        public String getDelimiterPrefix() {
            return delimiterPrefix;
        }

        public String getDelimiterLocalName() {
            return delimiterLocalName;
        }

        public StringBuffer getAddedClasses() {
            return addedClasses;
        }

        public boolean isMustGenerateFirstDelimiters() {
            return mustGenerateFirstDelimiters;
        }

        public void setAddedClasses(StringBuffer addedClasses) {
            this.addedClasses = addedClasses;
        }
    }
}
