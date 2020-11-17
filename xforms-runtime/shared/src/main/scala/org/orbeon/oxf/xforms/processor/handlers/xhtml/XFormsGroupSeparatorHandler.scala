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

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, OutputInterceptor}
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes


// Group within `xhtml:table|xhtml:tbody|xhtml:thead|xhtml:tfoot|xhtml:tr`.
class XFormsGroupSeparatorHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends
  XFormsGroupHandler(
    uri,
    localname,
    qName,
    localAtts,
    matched,
    handlerContext
  ) {

  private var currentSavedOutput: DeferredXMLReceiver = null
  private var outputInterceptor : OutputInterceptor   = null

  // If we are the top-level of a full update, output a delimiter anyway
  override def isMustOutputContainerElement: Boolean =
    handlerContext.isFullUpdateTopLevelControl(getEffectiveId)

  override def handleControlStart(): Unit = {

    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    val groupElementName = getContainingElementName
    val groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName)

    val controller = handlerContext.controller

    // Place interceptor on output
    // NOTE: Strictly, we should be able to do without the interceptor. We use it here because it
    // automatically handles ids and element names
    currentSavedOutput = controller.output

    val isMustGenerateBeginEndDelimiters = ! handlerContext.isFullUpdateTopLevelControl(getEffectiveId)

    // Classes on top-level elements and characters and on the first delimiter
    val elementClasses = {

      implicit val classes: jl.StringBuilder = new jl.StringBuilder

      appendControlUserClasses(attributes, currentControl)

      // NOTE: Could also use getInitialClasses(uri, localname, attributes, control), but then we get the
      // xforms-group-appearance-xxforms-separator class. Is that desirable?
      // as of August 2009, actually only need the marker class as well as `xforms-disabled` if the group is non-relevant
      handleMIPClasses(getPrefixedId, currentControl)

      classes.toString
    }

    outputInterceptor =
      new OutputInterceptor(
        currentSavedOutput,
        groupElementQName,
        outputInterceptor => {

          // Classes on first delimiter
          val firstDelimiterClasses = {
            val classes = new jl.StringBuilder("xforms-group-begin-end")
            if (elementClasses.nonEmpty) {
              classes.append(' ')
              classes.append(elementClasses)
            }
            classes.toString
          }

          // Delimiter: begin group
          if (isMustGenerateBeginEndDelimiters)
            outputInterceptor.outputDelimiter(currentSavedOutput, firstDelimiterClasses, "group-begin-" + containingDocument.namespaceId(getEffectiveId))
        },
        isAroundTableOrListElement = true
    )
    controller.output = new DeferredXMLReceiverImpl(outputInterceptor)

    // Set control classes
    outputInterceptor.setAddedClasses(elementClasses)

    // Don't support label, help, alert, or hint and other appearances, only the content!
  }

  override def handleControlEnd(): Unit = {

    val controller = handlerContext.controller

    // Restore output
    controller.output = currentSavedOutput

    // Delimiter: end repeat
    outputInterceptor.flushCharacters(finalFlush = true, topLevelCharacters = true)
    val isMustGenerateBeginEndDelimiters = ! handlerContext.isFullUpdateTopLevelControl(getEffectiveId)
    if (isMustGenerateBeginEndDelimiters)
      outputInterceptor.outputDelimiter(currentSavedOutput, "xforms-group-begin-end", "group-end-" + containingDocument.namespaceId(getEffectiveId))
  }

  // Don't output any LHHA
  override def handleLabel(): Unit = ()
  override def handleHint() : Unit = ()
  override def handleAlert(): Unit = ()
  override def handleHelp() : Unit = ()
}