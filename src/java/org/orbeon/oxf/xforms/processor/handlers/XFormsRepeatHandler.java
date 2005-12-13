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
import org.orbeon.oxf.xml.DeferredContentHandler;
import org.orbeon.oxf.xml.DeferredContentHandlerImpl;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
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
        final int currentRepeatIterations = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : ((Integer) effectiveRepeatIdToIterations.get(effectiveId)).intValue();

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        // Place interceptor on output
        final DeferredContentHandler savedOutput = handlerContext.getController().getOutput();
        final OutputInterceptor outputInterceptor = new OutputInterceptor(savedOutput, spanQName, new OutputInterceptor.Listener() {
            public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                // Delimiter: begin repeat
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end", "repeat-begin-" + effectiveId);
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
            }
        });
        handlerContext.getController().setOutput(new DeferredContentHandlerImpl(outputInterceptor));
        setContentHandler(handlerContext.getController().getOutput());

        if (isTopLevelRepeat || !isGenerateTemplate) {
            // Unroll repeat

            final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);
            for (int i = 1; i <= currentRepeatIterations; i++) {
                if (i > 1) {
                    // Delimiter: between repeat entries
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
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
                outputInterceptor.flushCharacters(true, true);
                handlerContext.popRepeatContext();
            }
        }

        if (isGenerateTemplate) {
            // Generate template

            if (!outputInterceptor.isMustGenerateFirstDelimiters()) {
                // Delimiter: between repeat entries
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
            }

            // Determine classes to add on root elements and around root characters
            outputInterceptor.setAddedClasses(new StringBuffer(isTopLevelRepeat ? "xforms-repeat-template" : ""));

            // Apply the content of the body for this iteration
            handlerContext.pushRepeatContext(true, 0, false, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true, true);
            handlerContext.popRepeatContext();
        }

        if (outputInterceptor.getDelimiterNamespaceURI() == null) {
            // No delimiter has been generated, try to find one!

            outputInterceptor.setForward(false); // prevent interceptor to output anything

            handlerContext.pushRepeatContext(true, 0, false, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true, true);
            handlerContext.popRepeatContext();
        }

        // Restore output
        handlerContext.getController().setOutput(savedOutput);
        setContentHandler(savedOutput);

        // Delimiter: end repeat
        outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end", "repeat-end-" + effectiveId);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
    }
}
