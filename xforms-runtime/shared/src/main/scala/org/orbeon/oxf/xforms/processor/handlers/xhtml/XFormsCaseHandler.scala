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

import java.{lang => jl}
import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, SwitchControl}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, OutputInterceptor}
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml._
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes

/**
 * Handle xf:case.
 *
 * TODO: This currently is based on delimiters. This is wrong: should be the case, like group, only around <tr>, etc.
 */
class XFormsCaseHandler(
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsControlLifecycleHandler(
    uri,
    localname,
    qName,
    localAtts,
    elementAnalysis,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) {

  private var currentSavedOutput: DeferredXMLReceiver = _
  private var currentOutputInterceptorOpt: Option[OutputInterceptor] = None
  private var isVisible  = false
  private var isMustGenerateBeginEndDelimiters = false

  // If we are the top-level of a full update, output a delimiter anyway
  protected override def isMustOutputContainerElement: Boolean =
    handlerContext.isFullUpdateTopLevelControl(getEffectiveId)

  override protected def handleControlStart(): Unit = {

    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")

    // Determine whether this case is visible
    val caseControl = currentControl.asInstanceOf[XFormsCaseControl]

    val switchHasFullUpdate =
      elementAnalysis.parent.get.asInstanceOf[SwitchControl].hasFullUpdate

    // This case is visible if it is selected or if the switch is read-only and we display read-only as static
    isVisible =
      if (caseControl ne null)
        caseControl.isCaseVisible
      else
        false

    val selectedClasses = if (isVisible) "xforms-case-selected" else "xforms-case-deselected"

    val controller = handlerContext.controller
    currentSavedOutput = controller.output

    // Classes on top-level elements and characters and on the first delimiter
    val elementClasses = {
      implicit val classes: jl.StringBuilder = new jl.StringBuilder
      appendControlUserClasses(localAtts, currentControl)
      // Don't add MIP classes as they can conflict with classes of nested content if used outside <tr>, etc.
      classes.toString
    }

    val controlClasses = {
      val classes = new jl.StringBuilder(selectedClasses)
      if (elementClasses.nonEmpty) {
        classes.append(' ')
        classes.append(elementClasses)
      }
      classes.toString
    }

    currentOutputInterceptorOpt =
      if (switchHasFullUpdate) {
        // No need for interceptor

        isMustGenerateBeginEndDelimiters = false

        if (isVisible) {

          reusableAttributes.clear()
          reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, controlClasses)

          // `ControlsComparator` skips the root element, so we create a dummy one to be skipped
          if (handlerContext.hasFullUpdateTopLevelControl)
            currentSavedOutput.startElement(XHTML_NAMESPACE_URI, "span", spanQName, XMLReceiverSupport.EmptyAttributes)

          currentSavedOutput.startElement(XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes)

        } else {
          controller.output = new DeferredXMLReceiverAdapter
        }

        None

      } else {
        // Place interceptor if needed
        isMustGenerateBeginEndDelimiters =
          ! handlerContext.isFullUpdateTopLevelControl(getEffectiveId)

        val newOutputInterceptor =
          new OutputInterceptor(
            currentSavedOutput,
            spanQName,
            outputInterceptor => {

              // Classes on first delimiter
              val firstDelimiterClasses = {
                val classes = new jl.StringBuilder("xforms-case-begin-end " + selectedClasses)
                if (elementClasses.nonEmpty) {
                  classes.append(' ')
                  classes.append(elementClasses)
                }
                classes.toString
              }

              // Delimiter: begin case
              if (isMustGenerateBeginEndDelimiters)
                outputInterceptor.outputDelimiter(
                  currentSavedOutput,
                  firstDelimiterClasses,
                  "xforms-case-begin-" + containingDocument.namespaceId(getEffectiveId)
                )
            },
            XFormsControl.appearances(elementAnalysis.parent.get)(XFormsNames.XXFORMS_SEPARATOR_APPEARANCE_QNAME)
          )

        newOutputInterceptor.setAddedClasses(controlClasses)
        controller.output = new DeferredXMLReceiverImpl(newOutputInterceptor)

        Some(newOutputInterceptor)
      }

    handlerContext.pushCaseContext(isVisible)
  }

  protected override def handleControlEnd(): Unit = {

    handlerContext.popCaseContext()

    currentOutputInterceptorOpt match {
      case None =>
        if (isVisible) {
          val xhtmlPrefix = handlerContext.findXHTMLPrefix
          val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")
          currentSavedOutput.endElement(XHTML_NAMESPACE_URI, "span", spanQName)

          if (handlerContext.hasFullUpdateTopLevelControl)
            currentSavedOutput.endElement(XHTML_NAMESPACE_URI, "span", spanQName)
        }
      case Some(currentOutputInterceptor) =>
        currentOutputInterceptor.flushCharacters(finalFlush = true, topLevelCharacters = true)
        if (isMustGenerateBeginEndDelimiters) {
          // Make sure first delimiter was output
          currentOutputInterceptor.generateFirstDelimitersIfNeeded()

          // Output end delimiter
          currentOutputInterceptor.outputDelimiter(currentSavedOutput, "xforms-case-begin-end", "xforms-case-end-" + containingDocument.namespaceId(getEffectiveId))
        }
    }

    handlerContext.controller.output = currentSavedOutput
  }

  // Don't output any LHHA
  override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit = ()
  override def handleHint(lhhaAnalysis: LHHAAnalysis) : Unit = ()
  override def handleHelp(lhhaAnalysis: LHHAAnalysis) : Unit = ()
  override def handleAlert(lhhaAnalysis: LHHAAnalysis): Unit = ()
}