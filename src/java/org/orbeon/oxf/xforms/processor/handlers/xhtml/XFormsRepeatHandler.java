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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor;
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor.Listener;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Handle xforms:repeat.
 */
public class XFormsRepeatHandler extends XFormsControlLifecyleHandler {

    public XFormsRepeatHandler() {
        // This is a repeating element
        super(true, true);
    }

    @Override
    protected boolean isMustOutputContainerElement() {
        // If we are the top-level of a full update, output a delimiter anyway
        return handlerContext.isFullUpdateTopLevelControl(getEffectiveId());
    }

    @Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId,
                                      final String effectiveId, XFormsControl control) throws SAXException {

        final boolean isTopLevelRepeat = handlerContext.countParentRepeats() == 0;
        final boolean isRepeatSelected = handlerContext.isRepeatSelected() || isTopLevelRepeat;
        final boolean isMustGenerateTemplate = handlerContext.isTemplate() || isTopLevelRepeat;
        final boolean isMustGenerateDelimiters = !handlerContext.isNoScript();
        final boolean isMustGenerateBeginEndDelimiters = !handlerContext.isFullUpdateTopLevelControl(effectiveId);
        
        final int currentIteration = handlerContext.getCurrentIteration();

        final XFormsRepeatControl repeatControl = handlerContext.isTemplate() ? null : (XFormsRepeatControl) containingDocument.getObjectByEffectiveId(effectiveId);
        final boolean isConcreteControl = repeatControl != null;

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        // Compute user classes only once for all iterations
        final String userClasses; {
            final StringBuilder sb = new StringBuilder();
            appendControlUserClasses(attributes, control, sb);
            userClasses = sb.toString();
        }

        // Place interceptor on output
        final DeferredXMLReceiver savedOutput = handlerContext.getController().getOutput();
        final OutputInterceptor outputInterceptor = !isMustGenerateDelimiters ? null : new OutputInterceptor(savedOutput, spanQName, new OutputInterceptor.Listener() {

            // Classes on first delimiter
            private final String firstDelimiterClasses;
            {
                final StringBuilder classes = new StringBuilder("xforms-repeat-begin-end");
                if (userClasses.length() > 0) {
                    classes.append(' ');
                    classes.append(userClasses);
                }
                firstDelimiterClasses = classes.toString();
            }

            public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                // Delimiter: begin repeat
                if (isMustGenerateBeginEndDelimiters) {
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), firstDelimiterClasses,
                            "repeat-begin-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
                }
            }
        });
        // TODO: is the use of XFormsElementFilterContentHandler necessary now?
        if (outputInterceptor != null)
            handlerContext.getController().setOutput(new DeferredXMLReceiverImpl(new XFormsElementFilterXMLReceiver(outputInterceptor)));

        if (isConcreteControl && (isTopLevelRepeat || !isMustGenerateTemplate)) {

            final int currentRepeatIndex = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : repeatControl.getIndex();
            final int currentRepeatIterations = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : repeatControl.getSize();

            final StringBuilder addedClasses = new StringBuilder(200);

            // Unroll repeat
            for (int i = 1; i <= currentRepeatIterations; i++) {
                if (outputInterceptor != null && i > 1) {
                    // Delimiter: between repeat entries
                    outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                            outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
                }

                // Is the current iteration selected?
                final boolean isCurrentIterationSelected = isRepeatSelected && i == currentRepeatIndex;
                final boolean isCurrentIterationRelevant = ((XFormsRepeatIterationControl) repeatControl.getChildren().get(i - 1)).isRelevant();
                final int numberOfParentRepeats = handlerContext.countParentRepeats();

                // Determine classes to add on root elements and around root characters
                {
                    addedClasses.setLength(0);

                    // Selected iteration
                    if (isCurrentIterationSelected && !isStaticReadonly(repeatControl)) {
                        addedClasses.append("xforms-repeat-selected-item-");
                        addedClasses.append(Integer.toString((numberOfParentRepeats % 4) + 1));
                    }
                    // User classes
                    if (userClasses.length() > 0) {
                        if (addedClasses.length() > 0)
                            addedClasses.append(' ');
                        addedClasses.append(userClasses);
                    }
                    // MIP classes
                    // Q: Could use handleMIPClasses()?
                    if (!isCurrentIterationRelevant) {
                        if (addedClasses.length() > 0)
                            addedClasses.append(' ');
                        addedClasses.append("xforms-disabled");
                    }
                    // DnD classes
                    addDnDClasses(addedClasses, attributes);
                }
                if (outputInterceptor != null)
                    outputInterceptor.setAddedClasses(addedClasses.toString());

                // Apply the content of the body for this iteration
                handlerContext.pushRepeatContext(false, i, isCurrentIterationSelected);
                try {
                    handlerContext.getController().repeatBody();
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, new ExtendedLocationData(repeatControl.getLocationData(), "unrolling xforms:repeat control", repeatControl.getControlElement()));
                }
                if (outputInterceptor != null)
                    outputInterceptor.flushCharacters(true, true);
                handlerContext.popRepeatContext();
            }
        }

        // Generate template
        if (isMustGenerateTemplate && !handlerContext.isNoScript()) {// don't generate templates in noscript mode as they won't be used

            if (outputInterceptor != null && !outputInterceptor.isMustGenerateFirstDelimiters()) {
                // Delimiter: between repeat entries
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
            }

            // Determine classes to add on root elements and around root characters
            final StringBuilder addedClasses = new StringBuilder(isTopLevelRepeat ? "xforms-repeat-template" : "");

            // Add classes such as DnD classes, etc.
            addDnDClasses(addedClasses, attributes);

            if (outputInterceptor != null)
                outputInterceptor.setAddedClasses(addedClasses.toString());

            // Apply the content of the body for this iteration
            handlerContext.pushRepeatContext(true, 0, false);
            handlerContext.getController().repeatBody();
            if (outputInterceptor != null)
                outputInterceptor.flushCharacters(true, true);
            handlerContext.popRepeatContext();
        }

        // If no delimiter has been generated, try to find one!
        if (outputInterceptor != null && outputInterceptor.getDelimiterNamespaceURI() == null) {

            outputInterceptor.setForward(false); // prevent interceptor to output anything

            handlerContext.pushRepeatContext(true, 0, false);
            handlerContext.getController().repeatBody();
            outputInterceptor.flushCharacters(true, true);
            handlerContext.popRepeatContext();
        }

        // Restore output
        handlerContext.getController().setOutput(savedOutput);

        // Delimiter: end repeat
        if (outputInterceptor != null && isMustGenerateBeginEndDelimiters) {
            outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end",
                    "repeat-end-" + XFormsUtils.namespaceId(containingDocument, effectiveId));
        }
    }

    private static void addDnDClasses(StringBuilder sb, Attributes attributes) {

        final String dndAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd");
        if (dndAttribute != null && !dndAttribute.equals("none")) {

            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-dnd");

            if (dndAttribute.equals("vertical"))
                sb.append(" xforms-dnd-vertical");
            if (dndAttribute.equals("horizontal"))
                sb.append(" xforms-dnd-horizontal");

            if (attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd-over") != null)
                sb.append(" xforms-dnd-over");
        }
    }

    @Override
    protected void handleLabel() throws SAXException {
        // Don't output
    }

    @Override
    protected void handleHint() throws SAXException {
        // Don't output
    }

    @Override
    protected void handleAlert() throws SAXException {
        // Don't output
    }

    @Override
    protected void handleHelp() throws SAXException {
        // Don't output
    }
}
