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
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import java.{lang ⇒ jl}

import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.xforms.analysis.controls.RepeatControl
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML.appendWithSpace
import org.orbeon.oxf.xforms.processor.handlers.{OutputInterceptor, XFormsBaseHandler}
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.xml.sax.Attributes

import scala.util.control.NonFatal

/**
 * Handle xf:repeat.
 */
class XFormsRepeatHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, attributes, matched, handlerContext, repeating = true, forwarding = true) {

  // Compute user classes only once for all iterations
  private val userClasses = {
    val sb = new jl.StringBuilder
    appendControlUserClasses(attributes, currentControlOrNull)(sb)
    sb.toString
  }

  override def isMustOutputContainerElement = xformsHandlerContext.isFullUpdateTopLevelControl(getEffectiveId)

  override protected def handleControlStart(): Unit = {

    val isTopLevelRepeat = xformsHandlerContext.countParentRepeats == 0
    val isRepeatSelected = xformsHandlerContext.isRepeatSelected || isTopLevelRepeat
    val isMustGenerateTemplate = (isTemplate || isTopLevelRepeat) && ! xformsHandlerContext.isNoScript // don't generate templates in noscript mode as they won't be used
    val isMustGenerateDelimiters = ! xformsHandlerContext.isNoScript
    val isMustGenerateBeginEndDelimiters = isMustGenerateDelimiters && ! xformsHandlerContext.isFullUpdateTopLevelControl(getEffectiveId)
    val namespacedId = XFormsUtils.namespaceId(containingDocument, getEffectiveId)

    val repeatControl = if (isTemplate) null else containingDocument.getObjectByEffectiveId(getEffectiveId).asInstanceOf[XFormsRepeatControl]
    val isConcreteControl = repeatControl != null

    val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")

    // Place interceptor on output
    val savedOutput = xformsHandlerContext.getController.getOutput

    var mustOutputFirstDelimiter = isMustGenerateDelimiters
    var outputDelimiter: (String, String) ⇒ Unit = null // initialized further below

    val outputInterceptor =
      if (! isMustGenerateDelimiters)
        null
      else
        new OutputInterceptor(
          savedOutput,
          spanQName,
          new OutputInterceptor.Listener {
            def generateFirstDelimiter(outputInterceptor: OutputInterceptor): Unit = {
              if (isMustGenerateBeginEndDelimiters) {

                def firstDelimiterClasses = "xforms-repeat-begin-end" + (if (userClasses.nonEmpty) " " + userClasses else "")

                // Delimiter: begin repeat
                outputDelimiter(firstDelimiterClasses, "repeat-begin-" + namespacedId)

                // Delimiter: before repeat entries, unless disabled (disabled in case the repeat is completely empty)
                if (mustOutputFirstDelimiter)
                  outputDelimiter("xforms-repeat-delimiter", null)
              }
            }
          },
          elementAnalysis.asInstanceOf[RepeatControl].isAroundTableOrListElement
        )

    // Shortcut function to output the delimiter
    outputDelimiter = outputInterceptor.outputDelimiter(savedOutput, _, _)

    def appendClasses(classes: String)(implicit sb: jl.StringBuilder): Unit =
      if (classes.nonEmpty)
        appendWithSpace(classes)

    def addDnDClasses()(implicit sb: jl.StringBuilder): Unit = {
      val dndAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd")
      if (Set("vertical", "horizontal")(dndAttribute)) {

        appendClasses("xforms-dnd xforms-dnd-" + dndAttribute)

        if (attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "dnd-over") != null)
          appendClasses("xforms-dnd-over")
      }
    }

    var bodyRepeated = false

    def repeatBody(iteration: Int, generateTemplate: Boolean, repeatSelected: Boolean)(implicit sb: jl.StringBuilder): Unit = {

      if (isMustGenerateDelimiters) {
        // User and DnD classes
        appendClasses(userClasses)
        addDnDClasses()

        outputInterceptor.setAddedClasses(sb.toString)
      }

      xformsHandlerContext.pushRepeatContext(generateTemplate, iteration, repeatSelected)
      try {
        xformsHandlerContext.getController.repeatBody()
        if (isMustGenerateDelimiters)
          outputInterceptor.flushCharacters(true, true)
      } catch {
        case NonFatal(t) ⇒
          throw OrbeonLocationException.wrapException(
            t,
            new ExtendedLocationData(repeatControl.getLocationData, "unrolling xf:repeat control", repeatControl.element)
          )
      }
      xformsHandlerContext.popRepeatContext()

      bodyRepeated = true
    }

    if (isMustGenerateDelimiters)
      xformsHandlerContext.getController.setOutput(new DeferredXMLReceiverImpl(outputInterceptor))

    // 1. Unroll repeat if needed
    if (isConcreteControl) {

      val repeatIndex = repeatControl.getIndex
      val selectedClass = "xforms-repeat-selected-item-" + ((xformsHandlerContext.countParentRepeats % 4) + 1)
      val staticReadonly = XFormsBaseHandler.isStaticReadonly(repeatControl)

      implicit val addedClasses = new jl.StringBuilder(200)
      for (i ← 1 to repeatControl.getSize) {
        // Delimiter: before repeat entries, except the first one which is output by generateFirstDelimiter()
        if (isMustGenerateDelimiters && i > 1)
          outputDelimiter("xforms-repeat-delimiter", null)

        // Determine classes to add on root elements and around root characters
        addedClasses.setLength(0)

        // Selected iteration
        val selected = isRepeatSelected && i == repeatIndex && ! staticReadonly
        if (selected)
          addedClasses append selectedClass

        // MIP classes
        // Q: Could use handleMIPClasses()?
        val relevant = repeatControl.children(i - 1).isRelevant
        if (! relevant)
          appendClasses("xforms-disabled")

        // Apply the content of the body for this iteration
        repeatBody(i, generateTemplate = false, repeatSelected = selected)
      }
    }

    // 2. Generate template if needed
    if (isMustGenerateTemplate) {
      // Delimiter: before repeat template
      if (isMustGenerateDelimiters && ! outputInterceptor.isMustGenerateFirstDelimiters)
        outputDelimiter("xforms-repeat-delimiter", null)

      // Determine classes to add on root elements and around root characters
      implicit val addedClasses = new jl.StringBuilder(if (isTopLevelRepeat) "xforms-repeat-template" else "")

      // Apply the content of the body for this iteration
      repeatBody(0, generateTemplate = true, repeatSelected = false)
    }

    // 3. Handle case where no delimiter was output by repeat iterations or template
    if (isMustGenerateDelimiters && ! bodyRepeated) {
      // What we do here is replay the body to /dev/null in order to find and output the begin delimiter (but not
      // the other delimiters)
      outputInterceptor.setForward(false)
      mustOutputFirstDelimiter = false
      repeatBody(0, generateTemplate = true, repeatSelected = false)(new jl.StringBuilder)
    }

    // Restore output
    xformsHandlerContext.getController.setOutput(savedOutput)

    // 4. Delimiter: end repeat
    if (isMustGenerateBeginEndDelimiters)
      outputDelimiter("xforms-repeat-begin-end", "repeat-end-" + namespacedId)
  }

  // Don't output any LHHA
  override def handleLabel() = ()
  override def handleHint()  = ()
  override def handleHelp()  = ()
  override def handleAlert() = ()
}
