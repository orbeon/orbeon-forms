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

import java.{lang ⇒ jl}

import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl
import org.orbeon.oxf.xforms.processor.handlers.OutputInterceptor
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes

/**
 * Handle xf:case.
 *
 * TODO: This currently is based on delimiters. This is wrong: should be the case, like group, only around <tr>, etc.
 */
class XFormsCaseHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, attributes, matched, handlerContext, repeating = false, forwarding = true) {

  private var currentSavedOutput: DeferredXMLReceiver = _
  private var currentOutputInterceptor: OutputInterceptor = _
  private var isVisible  = false
  private var isMustGenerateBeginEndDelimiters = false

  // If we are the top-level of a full update, output a delimiter anyway
  protected override def isMustOutputContainerElement =
    xformsHandlerContext.isFullUpdateTopLevelControl(getEffectiveId)

  override protected def handleControlStart(): Unit = {

    val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")

    // Determine whether this case is visible
    val caseControl = containingDocument.getControlByEffectiveId(getEffectiveId).asInstanceOf[XFormsCaseControl]

    // This case is visible if it is selected or if the switch is read-only and we display read-only as static
    isVisible =
      if (! isTemplate && (caseControl ne null))
        caseControl.isVisible
      else
        false

    val selectedClasses = if (isVisible) "xforms-case-selected" else "xforms-case-deselected"

    val controller = xformsHandlerContext.getController
    currentSavedOutput = controller.getOutput

    // Place interceptor if needed
    locally {
      isMustGenerateBeginEndDelimiters = ! xformsHandlerContext.isFullUpdateTopLevelControl(getEffectiveId)

      // Classes on top-level elements and characters and on the first delimiter
      val elementClasses = {
        implicit val classes = new jl.StringBuilder
        appendControlUserClasses(attributes, currentControlOrNull)
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
                  "xforms-case-begin-" + XFormsUtils.namespaceId(containingDocument, getEffectiveId)
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
    }

    xformsHandlerContext.pushCaseContext(isVisible)
  }

  protected override def handleControlEnd(): Unit = {
    xformsHandlerContext.popCaseContext()

    locally {
      currentOutputInterceptor.flushCharacters(true, true)
      if (isMustGenerateBeginEndDelimiters) {
        // Make sure first delimiter was output
        currentOutputInterceptor.generateFirstDelimitersIfNeeded()

        // Output end delimiter
        currentOutputInterceptor.outputDelimiter(currentSavedOutput, "xforms-case-begin-end", "xforms-case-end-" + XFormsUtils.namespaceId(containingDocument, getEffectiveId))
      }
    }

    xformsHandlerContext.getController.setOutput(currentSavedOutput)
  }

  // Don't output any LHHA
  override def handleLabel() = ()
  override def handleHint()  = ()
  override def handleHelp()  = ()
  override def handleAlert() = ()
}