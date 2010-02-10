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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xml.DeferredContentHandler;
import org.orbeon.oxf.xml.DeferredContentHandlerImpl;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Handle xforms:repeat.
 */
public class XFormsRepeatHandler extends XFormsBaseHandler {

    public XFormsRepeatHandler() {
        // This is a repeating element
        super(true, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String effectiveId = handlerContext.getEffectiveId(attributes);

        final boolean isTopLevelRepeat = handlerContext.countParentRepeats() == 0;
        final boolean isRepeatSelected = handlerContext.isRepeatSelected() || isTopLevelRepeat;
        final boolean isMustGenerateTemplate = handlerContext.isTemplate() || isTopLevelRepeat;
        final int currentIteration = handlerContext.getCurrentIteration();

        final XFormsRepeatControl repeatControl = handlerContext.isTemplate() ? null : (XFormsRepeatControl) containingDocument.getObjectByEffectiveId(effectiveId);
        final boolean isConcreteControl = repeatControl != null;

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        // Place interceptor on output
        final DeferredContentHandler savedOutput = handlerContext.getController().getOutput();
        final OutputInterceptor outputInterceptor = handlerContext.isNoScript() ? null : new OutputInterceptor(savedOutput, spanQName, new OutputInterceptor.Listener() {
            public void generateFirstDelimiter(OutputInterceptor outputInterceptor) throws SAXException {
                // Delimiter: begin repeat
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end", "repeat-begin-" + effectiveId);
                outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                        outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-delimiter", null);
            }
        });
        // TODO: is the use of XFormsElementFilterContentHandler necessary now?
        if (outputInterceptor != null)
            handlerContext.getController().setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(outputInterceptor)));

        if (isConcreteControl && (isTopLevelRepeat || !isMustGenerateTemplate)) {

            final int currentRepeatIndex = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : repeatControl.getIndex();
            final int currentRepeatIterations = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : repeatControl.getSize();

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
                final StringBuilder addedClasses;
                {
                    addedClasses = new StringBuilder(200);
                    if (isCurrentIterationSelected && !isStaticReadonly(repeatControl)) {
                        addedClasses.append("xforms-repeat-selected-item-");
                        addedClasses.append(Integer.toString((numberOfParentRepeats % 4) + 1));
                    }
                    if (!isCurrentIterationRelevant)
                        addedClasses.append(" xforms-disabled");
                    // Add classes such as DnD classes, etc.
                    addRepeatClasses(addedClasses, attributes);
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
            addRepeatClasses(addedClasses, attributes);

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
        if (outputInterceptor != null)
            outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI(),
                outputInterceptor.getDelimiterPrefix(), outputInterceptor.getDelimiterLocalName(), "xforms-repeat-begin-end", "repeat-end-" + effectiveId);
    }

    private static void addRepeatClasses(StringBuilder sb, Attributes attributes) {

        final String dndAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd");
        if (dndAttribute != null && !dndAttribute.equals("none")) {

            sb.append(" xforms-dnd");

            if (dndAttribute.equals("vertical"))
                sb.append(" xforms-dnd-vertical");
            if (dndAttribute.equals("horizontal"))
                sb.append(" xforms-dnd-horizontal");

            if (attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd-over") != null)
                sb.append(" xforms-dnd-over");
        }
    }
}
