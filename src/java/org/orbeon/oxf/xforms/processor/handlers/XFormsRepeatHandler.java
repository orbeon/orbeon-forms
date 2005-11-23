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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.DeferredContentHandler;
import org.orbeon.oxf.xml.DeferredContentHandlerImpl;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

/**
 * Handle xforms:repeat.
 */
public class XFormsRepeatHandler extends HandlerBase {

    private Attributes elementAttributes;

    public XFormsRepeatHandler(HandlerContext context) {
        // This is a repeating element
        super(context, true);
        setDoForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        addAllControlHandlers();

        elementAttributes = new AttributesImpl(attributes);

//        final ContentHandler contentHandler = handlerContext.getOutput();
        final String repeatId = attributes.getValue("id");
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);

        final boolean isTopLevelRepeat = handlerContext.isTopLevelRepeat();
        final boolean isRepeatSelected = handlerContext.isRepeatSelected();

        final XFormsControls.ControlsState currentControlState = containingDocument.getXFormsControls().getCurrentControlsState();
        final Map effectiveRepeatIdToIterations = currentControlState.getEffectiveRepeatIdToIterations();
        final Map repeatIdToIndex = currentControlState.getRepeatIdToIndex();

        final int currentRepeatIndex = ((Integer) repeatIdToIndex.get(repeatId)).intValue();
        final int currentRepeatIteration = ((Integer) effectiveRepeatIdToIterations.get(effectiveId)).intValue();

        // Delimiter: begin repeat
        // TODO
        // xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, concat('repeat-begin-', $id))
        // xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, ())

        if (isTopLevelRepeat || !handlerContext.isGenerateTemplate()) {

            // Place interceptor on output
            final DeferredContentHandler savedOutput = handlerContext.getOutput();
            handlerContext.setOutput(new DeferredContentHandlerImpl(new ForwardingContentHandler(savedOutput) {
                // TODO
                public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                    super.startElement(uri, localname, qName, attributes);
                }

                public void endElement(String uri, String localname, String qName) throws SAXException {
                    super.endElement(uri, localname, qName);
                }

                public void characters(char[] chars, int start, int length) throws SAXException {
                    super.characters(chars, start, length);
                }
            }));
            final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);
            for (int i = 1; i <= currentRepeatIteration; i++) {
                if (i > 1) {
                    // Delimiter: between repeat entries
                    // TODO
                    // xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, ())
                }

                // Is the current iteration selected?
                final boolean isCurrentRepeatSelected = isRepeatSelected && i == currentRepeatIndex;
                final boolean isCurrentRepeatRelevant = ((XFormsControls.RepeatIterationInfo) repeatControlInfo.getChildren().get(i - 1)).isRelevant();
                final int numberParentRepeat = handlerContext.countParentRepeats();
                final StringBuffer addedClasses = new StringBuffer();

                if (isCurrentRepeatSelected) {
                    addedClasses.append("xforms-repeat-selected-item-");
                    addedClasses.append((numberParentRepeat % 2 == 0) ? '1' : '2');
                }

                if (!isCurrentRepeatRelevant)
                    addedClasses.append(" xforms-disabled");

                // Apply the content of the body for this iteration
                handlerContext.pushRepeatContext(false, i, false, isCurrentRepeatSelected);
                repeatBody();
                handlerContext.popRepeatContext();
            }

            // Restore output
            handlerContext.setOutput(savedOutput);
        }

        if (handlerContext.isGenerateTemplate()) {
            // TODO
        }

        // Delimiter: end repeat
        // TODO
        //xxforms:repeat-delimiter($delimiter-namespace-uri, $delimiter-local-name, concat('repeat-end-', $id))
    }

    public void end(String uri, String localname, String qName) throws SAXException {

//        final ContentHandler contentHandler = handlerContext.getOutput();
//        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
//        final XFormsControls.ControlInfo controlInfo = handlerContext.isGenerateTemplate()
//                ? null : (XFormsControls.ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);
//
//        // xforms:label
//        handleLabelHintHelpAlert(effectiveId, "label", controlInfo);
//
//        final AttributesImpl newAttributes;
//        {
//            final StringBuffer classes = new StringBuffer("xforms-control xforms-secret");
//            if (!handlerContext.isGenerateTemplate()) {
//
//                handleReadOnlyClass(classes, controlInfo);
//                handleRelevantClass(classes, controlInfo);
//
//                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
//                handleReadOnlyAttribute(newAttributes, controlInfo);
//            } else {
//                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
//            }
//        }
//
//        // Create xhtml:input
//        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
//        final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
//        {
//            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "password");
//            newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);
//            newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA,
//                    handlerContext.isGenerateTemplate() ? "" : controlInfo.getValue());
//
//            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, newAttributes);
//            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
//        }
//
//        // xforms:help
//        handleLabelHintHelpAlert(effectiveId, "help", controlInfo);
//
//        // xforms:alert
//        handleLabelHintHelpAlert(effectiveId, "alert", controlInfo);
//
//        // xforms:hint
//        handleLabelHintHelpAlert(effectiveId, "hint", controlInfo);
    }
}
