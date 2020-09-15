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

import java.{lang => jl}

import cats.syntax.option._
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.controls.RepeatControl
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML.appendWithSpace
import org.orbeon.oxf.xforms.processor.handlers.{OutputInterceptor, XFormsBaseHandler}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.xml.sax.Attributes

import scala.util.control.NonFatal

/**
 * Handle xf:repeat.
 */
class XFormsRepeatHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends
  XFormsControlLifecyleHandler(
    uri,
    localname,
    qName,
    localAtts,
    matched,
    handlerContext,
    repeating  = true,
    forwarding = true
  ) {

  // Compute user classes only once for all iterations
  private val userClasses = {
    val sb = new jl.StringBuilder
    appendControlUserClasses(attributes, currentControl)(sb)
    sb.toString
  }

  override def isMustOutputContainerElement: Boolean = xformsHandlerContext.isFullUpdateTopLevelControl(getEffectiveId)

  override protected def handleControlStart(): Unit = {

    val isMustGenerateBeginEndDelimiters = ! xformsHandlerContext.isFullUpdateTopLevelControl(getEffectiveId)
    val namespacedId = XFormsUtils.namespaceId(containingDocument, getEffectiveId)

    val repeatControl = containingDocument.getObjectByEffectiveId(getEffectiveId).asInstanceOf[XFormsRepeatControl]

    if (repeatControl.getSize > 0) {

      // Unroll repeat

      val isTopLevelRepeat = xformsHandlerContext.countParentRepeats == 0
      val isRepeatSelected = xformsHandlerContext.isRepeatSelected || isTopLevelRepeat

      val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix
      val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")

      // Place interceptor on output
      val savedOutput = xformsHandlerContext.getController.getOutput

      var outputDelimiter: (String, String) => Unit = null // initialized further below

      val outputInterceptor =
        new OutputInterceptor(
          savedOutput,
          spanQName,
          _ => {
            if (isMustGenerateBeginEndDelimiters) {

              val firstDelimiterClasses = "xforms-repeat-begin-end" + (if (userClasses.nonEmpty) " " + userClasses else "")

              // Delimiter: begin repeat
              outputDelimiter(firstDelimiterClasses, "repeat-begin-" + namespacedId)

              // Delimiter: before repeat entries, unless disabled (disabled in case the repeat is completely empty)
              outputDelimiter("xforms-repeat-delimiter", null)
            }
          },
          elementAnalysis.asInstanceOf[RepeatControl].isAroundTableOrListElement
        )

      // Shortcut function to output the delimiter
      outputDelimiter = outputInterceptor.outputDelimiter(savedOutput, _, _)

      def appendClasses(classes: String)(implicit sb: jl.StringBuilder): Unit =
        if (classes.nonEmpty)
          appendWithSpace(classes)

      def repeatBody(iteration: Int, repeatSelected: Boolean)(implicit sb: jl.StringBuilder): Unit = {

        // User and DnD classes
        appendClasses(userClasses)
        staticControlOpt flatMap (_.asInstanceOf[RepeatControl].dndClasses) foreach appendClasses

        outputInterceptor.setAddedClasses(sb.toString)

        xformsHandlerContext.pushRepeatContext(iteration, repeatSelected)
        try {
          xformsHandlerContext.getController.repeatBody()
          outputInterceptor.flushCharacters(finalFlush = true, topLevelCharacters = true)
        } catch {
          case NonFatal(t) =>
            throw OrbeonLocationException.wrapException(
              t,
              XmlExtendedLocationData(
                repeatControl.getLocationData,
                "unrolling xf:repeat control".some,
                element = repeatControl.element.some
              )
            )
        }
        xformsHandlerContext.popRepeatContext()
      }

      xformsHandlerContext.getController.setOutput(new DeferredXMLReceiverImpl(outputInterceptor))

      val repeatIndex = repeatControl.getIndex
      val selectedClass = "xforms-repeat-selected-item-" + ((xformsHandlerContext.countParentRepeats % 4) + 1)
      val staticReadonly = XFormsBaseHandler.isStaticReadonly(repeatControl)

      implicit val addedClasses = new jl.StringBuilder(200)
      for (i <- 1 to repeatControl.getSize) {
        // Delimiter: before repeat entries, except the first one which is output by `generateFirstDelimiter()`
        if (i > 1)
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
        repeatBody(i, repeatSelected = selected)
      }

      // Restore output
      xformsHandlerContext.getController.setOutput(savedOutput)

      // Delimiter: end repeat
      if (isMustGenerateBeginEndDelimiters)
        outputDelimiter("xforms-repeat-begin-end", "repeat-end-" + namespacedId)

    } else if (isMustGenerateBeginEndDelimiters) {

      // If there are no repeat iterations, we need to output the start/end delimiters

      def outputBeginEndDelimiters(localName: String, prefix: String, uri: String): Unit =
        for (infix <- List("begin", "end"))
          element(
            localName = localName,
            prefix    = prefix,
            uri       = uri,
            atts      = List("id" -> s"repeat-$infix-$namespacedId", "class" -> "xforms-repeat-begin-end"))(
            receiver  = xformsHandlerContext.getController.getOutput
          )

      xformsHandlerContext.getController.findFirstHandlerOrElem match {

        case Some(Left(handler: XFormsControlLifecyleHandler)) =>

          outputBeginEndDelimiters(
            localName = handler.getContainingElementName,
            prefix    = xformsHandlerContext.findXHTMLPrefix,
            uri       = XMLConstants.XHTML_NAMESPACE_URI
          )

        case Some(Left(handler: XHTMLElementHandler)) =>

          outputBeginEndDelimiters(
            localName = handler.localname,
            prefix    = xformsHandlerContext.findXHTMLPrefix,
            uri       = XMLConstants.XHTML_NAMESPACE_URI
          )

        case Some(Right(structuredQName)) => // no handler for the element (shouldn't happen as we have `XHTMLElementHandler`)

          outputBeginEndDelimiters(
            localName = structuredQName.getLocalName,
            prefix    = structuredQName.getPrefix,
            uri       = structuredQName.getNamespaceURI
          )

        case Some(Left(_)) | None  => // handler is not one we support or no element was output

          outputBeginEndDelimiters(
            localName = "span",
            prefix    = xformsHandlerContext.findXHTMLPrefix,
            uri       = XMLConstants.XHTML_NAMESPACE_URI
          )
      }
    }
  }

  // Don't output any LHHA
  override def handleLabel(): Unit = ()
  override def handleHint() : Unit = ()
  override def handleAlert(): Unit = ()
  override def handleHelp() : Unit = ()
}
