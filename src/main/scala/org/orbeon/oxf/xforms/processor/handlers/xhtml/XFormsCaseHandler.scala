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
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import java.{lang ⇒ jl}

/**
 * Handle xf:case.
 *
 * TODO: This currently is based on delimiters. This is wrong: should be the case, like group, only around <tr>, etc.
 */
class XFormsCaseHandler extends XFormsControlLifecyleHandler(false, true) {

  private var currentSavedOutput: DeferredXMLReceiver = _
  private var currentOutputInterceptor: OutputInterceptor = _
  private var isVisible  = false
  private var isMustGenerateBeginEndDelimiters = false

  // If we are the top-level of a full update, output a delimiter anyway
  protected override def isMustOutputContainerElement =
    handlerContext.isFullUpdateTopLevelControl(getEffectiveId)

  protected def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl): Unit = {

    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")

    // Determine whether this case is visible
    val caseControl = containingDocument.getControlByEffectiveId(effectiveId).asInstanceOf[XFormsCaseControl]

    // This case is visible if it is selected or if the switch is read-only and we display read-only as static
    isVisible =
      if (! handlerContext.isTemplate && (caseControl ne null))
        caseControl.isVisible
      else
        false

    val selectedClasses = if (isVisible) "xforms-case-selected" else "xforms-case-deselected"

    val controller = handlerContext.getController
    currentSavedOutput = controller.getOutput

    // Place interceptor if needed
    if (! handlerContext.isNoScript) {
      isMustGenerateBeginEndDelimiters = ! handlerContext.isFullUpdateTopLevelControl(effectiveId)

      // Classes on top-level elements and characters and on the first delimiter
      val elementClasses = {
        val classes = new jl.StringBuilder
        appendControlUserClasses(attributes, control, classes)
        // Don't add MIP classes as they can conflict with classes of nested content if used outside <tr>, etc.
        classes.toString
      }

      currentOutputInterceptor =
        new OutputInterceptor(
          currentSavedOutput,
          spanQName,
          new OutputInterceptor.Listener {

            // Classes on first delimiter
            private val firstDelimiterClasses = {
              val classes = new jl.StringBuilder("xforms-case-begin-end " + selectedClasses)
              if (elementClasses.nonEmpty) {
                classes.append(' ')
                classes.append(elementClasses)
              }
              classes.toString
            }

            // Delimiter: begin case
            def generateFirstDelimiter(outputInterceptor: OutputInterceptor): Unit =
              if (isMustGenerateBeginEndDelimiters)
                outputInterceptor.outputDelimiter(
                  currentSavedOutput,
                  firstDelimiterClasses,
                  "xforms-case-begin-" + XFormsUtils.namespaceId(containingDocument, effectiveId)
                )

          },
          XFormsControl.appearances(elementAnalysis.parent.get)(XFormsConstants.XXFORMS_SEPARATOR_APPEARANCE_QNAME)
        )

      val controlClasses = {
        val classes = new jl.StringBuilder(selectedClasses)
        if (elementClasses.nonEmpty) {
          classes.append(' ')
          classes.append(elementClasses)
        }
        classes.toString
      }
      currentOutputInterceptor.setAddedClasses(controlClasses)
      controller.setOutput(new DeferredXMLReceiverImpl(currentOutputInterceptor))
    } else if (! isVisible)
      // Case not visible, set output to a black hole
      controller.setOutput(new DeferredXMLReceiverAdapter)

    handlerContext.pushCaseContext(isVisible)
  }

  protected override def handleControlEnd(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl): Unit = {
    handlerContext.popCaseContext()

    if (! handlerContext.isNoScript) {
      currentOutputInterceptor.flushCharacters(true, true)
      if (isMustGenerateBeginEndDelimiters) {
        // Make sure first delimiter was output
        currentOutputInterceptor.generateFirstDelimitersIfNeeded()

        // Output end delimiter
        currentOutputInterceptor.outputDelimiter(currentSavedOutput, "xforms-case-begin-end", "xforms-case-end-" + XFormsUtils.namespaceId(containingDocument, effectiveId))
      }
    }

    handlerContext.getController.setOutput(currentSavedOutput)
  }

  // Don't output any LHHA
  override def handleLabel() = ()
  override def handleHint()  = ()
  override def handleHelp()  = ()
  override def handleAlert() = ()
}