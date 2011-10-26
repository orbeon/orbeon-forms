/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsControlLifecyleHandler
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.xml.sax.Attributes
import java.lang.{StringBuilder => JStringBuilder}

/**
 * Handle xforms:repeat.
 */
class XFormsRepeatHandler extends XFormsControlLifecyleHandler(true, true) { // This is a repeating element

    override def isMustOutputContainerElement = handlerContext.isFullUpdateTopLevelControl(getEffectiveId)

    def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, staticId: String, effectiveId: String, control: XFormsControl) {

        val isTopLevelRepeat = handlerContext.countParentRepeats == 0
        val isRepeatSelected = handlerContext.isRepeatSelected || isTopLevelRepeat
        val isMustGenerateTemplate = (handlerContext.isTemplate || isTopLevelRepeat) && !handlerContext.isNoScript // don't generate templates in noscript mode as they won't be used
        val isMustGenerateDelimiters = !handlerContext.isNoScript
        val isMustGenerateBeginEndDelimiters = isMustGenerateDelimiters && !handlerContext.isFullUpdateTopLevelControl(effectiveId)

        val currentIteration = handlerContext.getCurrentIteration

        val repeatControl = if (handlerContext.isTemplate) null else containingDocument.getObjectByEffectiveId(effectiveId).asInstanceOf[XFormsRepeatControl]
        val isConcreteControl = repeatControl != null

        val xhtmlPrefix = handlerContext.findXHTMLPrefix
        val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")

        // Compute user classes only once for all iterations
        val userClasses = appendControlUserClasses(attributes, control, new JStringBuilder).toString

        // Place interceptor on output
        val savedOutput = handlerContext.getController.getOutput

        var mustOutputFirstDelimiter = isMustGenerateDelimiters
        var outputDelimiter: (String, String) => Unit = null // initialized further below

        val outputInterceptor = if (!isMustGenerateDelimiters) null else new OutputInterceptor(savedOutput, spanQName, new OutputInterceptor.Listener {
                def generateFirstDelimiter(outputInterceptor: OutputInterceptor) {

                    if (isMustGenerateBeginEndDelimiters) {

                        def firstDelimiterClasses = "xforms-repeat-begin-end" + (if (userClasses.nonEmpty) (" " + userClasses) else "")

                        // Delimiter: begin repeat
                        outputDelimiter(firstDelimiterClasses, "repeat-begin-" + XFormsUtils.namespaceId(containingDocument, effectiveId))

                        // Delimiter: before repeat entries, unless disabled (disabled in case the repeat is completely empty)
                        if (mustOutputFirstDelimiter)
                            outputDelimiter("xforms-repeat-delimiter", null)
                    }
                }
            })

        // Shortcut function to output the delimiter
        outputDelimiter =
            outputInterceptor.outputDelimiter(savedOutput, outputInterceptor.getDelimiterNamespaceURI,
                outputInterceptor.getDelimiterPrefix, outputInterceptor.getDelimiterLocalName, _, _)

        def appendClasses(sb: StringBuilder, classes: String) {
            if (classes.nonEmpty) {
                if (sb.nonEmpty)
                    sb += ' '
                sb append classes
            }
        }

        def addDnDClasses(sb: StringBuilder) {
            val dndAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd")
            if (Set("vertical", "horizontal")(dndAttribute)) {

                appendClasses(sb, "xforms-dnd xforms-dnd-" + dndAttribute)

                if (attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd-over") != null)
                    appendClasses(sb, "xforms-dnd-over")
            }
        }
        
        def repeatBody(generateTemplate: Boolean, iteration: Int, repeatSelected: Boolean, classes: StringBuilder) {

            if (isMustGenerateDelimiters) {
                // User and DnD classes
                appendClasses(classes, userClasses)
                addDnDClasses(classes)
                
                outputInterceptor.setAddedClasses(classes.toString)
            }

            handlerContext.pushRepeatContext(generateTemplate, iteration, repeatSelected)
            try {
                handlerContext.getController.repeatBody()
                if (isMustGenerateDelimiters)
                    outputInterceptor.flushCharacters(true, true)
            } catch {
                case e: Exception =>
                    throw ValidationException.wrapException(e, new ExtendedLocationData(repeatControl.getLocationData, "unrolling xforms:repeat control", repeatControl.getControlElement))
            }
            handlerContext.popRepeatContext()
        }

        if (isMustGenerateDelimiters)
            handlerContext.getController.setOutput(new DeferredXMLReceiverImpl(outputInterceptor))

        // 1. Unroll repeat if needed
        if (isConcreteControl) {

            val repeatIndex = repeatControl.getIndex
            val selectedClass = "xforms-repeat-selected-item-" + ((handlerContext.countParentRepeats % 4) + 1)
            val isStaticReadonly = super.isStaticReadonly(repeatControl)

            val addedClasses = new StringBuilder(200)
            for (i <- 1 to repeatControl.getSize) {
                // Delimiter: before repeat entries, except the first one which is output by generateFirstDelimiter()
                if (isMustGenerateDelimiters && i > 1)
                    outputDelimiter("xforms-repeat-delimiter", null)

                // Determine classes to add on root elements and around root characters
                addedClasses.setLength(0)

                // Selected iteration
                val selected = isRepeatSelected && i == repeatIndex && !isStaticReadonly
                if (selected)
                    addedClasses append selectedClass

                // MIP classes
                // Q: Could use handleMIPClasses()?
                val relevant = repeatControl.getChildren.get(i - 1).isRelevant
                if (!relevant)
                    appendClasses(addedClasses, "xforms-disabled")

                // Apply the content of the body for this iteration
                repeatBody(false, i, selected, addedClasses)
            }
        }

        // 2. Generate template if needed
        if (isMustGenerateTemplate) {
            // Delimiter: before repeat template
            if (isMustGenerateDelimiters && !outputInterceptor.isMustGenerateFirstDelimiters)
                outputDelimiter("xforms-repeat-delimiter", null)

            // Determine classes to add on root elements and around root characters
            val addedClasses = new StringBuilder(if (isTopLevelRepeat) "xforms-repeat-template" else "")

            // Apply the content of the body for this iteration
            repeatBody(true, 0, false, addedClasses)
        }

        // 3. Handle case where no delimiter was output by repeat iterations or template
        if (isMustGenerateDelimiters && outputInterceptor.getDelimiterNamespaceURI == null) {

            // This happens if:
            // o there was no repeat iteration
            // o AND no template was generated

            assert(!isConcreteControl || repeatControl.getSize == 0)
            assert(!isMustGenerateTemplate)

            // What we do here is replay the body to /dev/null in order to find and output the begin delimiter (but not
            // the other delimiters)
            outputInterceptor.setForward(false)
            mustOutputFirstDelimiter = false
            repeatBody(true, 0, false, new StringBuilder)
        }

        // Restore output
        handlerContext.getController.setOutput(savedOutput)

        // 4. Delimiter: end repeat
        if (isMustGenerateBeginEndDelimiters)
            outputDelimiter("xforms-repeat-begin-end", "repeat-end-" + XFormsUtils.namespaceId(containingDocument, effectiveId))
    }

    // Don't output any LHHA
    override def handleLabel() = ()
    override def handleHint() = ()
    override def handleAlert() = ()
    override def handleHelp() = ()
}
