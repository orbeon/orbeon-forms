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
package org.orbeon.oxf.xforms.processor

import java.{util ⇒ ju}
import org.orbeon.oxf.processor.converter.XHTMLRewrite
import org.orbeon.oxf.util.ContentHandlerWriter
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.XFormsUtils.namespaceId
import org.orbeon.oxf.xforms.control.controls.{XXFormsDynamicControl, XFormsRepeatControl}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsContainerControl, XFormsControl}
import org.orbeon.oxf.xforms.processor.handlers._
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLElementHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XXFormsAttributeHandler
import org.orbeon.oxf.xml._
import org.xml.sax.helpers.AttributesImpl
import scala.collection.JavaConverters._
import scala.util.control.Breaks

class ControlsComparator(
  document              : XFormsContainingDocument,
  valueChangeControlIds : collection.Set[String],
  isTestMode            : Boolean
) extends XMLReceiverSupport {

  // For Java callers
  def this(
    containingDocument    : XFormsContainingDocument,
    valueChangeControlIds : ju.Set[String],
    isTestMode            : Boolean
  ) = this(
    containingDocument,
    Option(valueChangeControlIds) map (_.asScala) getOrElse Set.empty[String],
    isTestMode
  )

  // For Java callers
  def diffJava(
    receiver : XMLReceiver,
    left     : ju.List[XFormsControl],
    right    : ju.List[XFormsControl]
  ) = diffChildren(
    if (left  ne null) left.asScala  else Nil,
    if (right ne null) right.asScala else Nil,
    None)(
    receiver
  )

  private val FullUpdateThreshold = document.getAjaxFullUpdateThreshold

  private val breaks = new Breaks
  import breaks._

  def diffChildren(
    left             : Seq[XFormsControl],
    right            : Seq[XFormsControl],
    fullUpdateBuffer : Option[SAXStore])(implicit
    receiver         : XMLReceiver
  ): Unit = {

    if (right.nonEmpty) {

      assert(left.size == right.size || left.isEmpty, "illegal state when comparing controls")

      for {
        (control1OrNull, control2) ← left.iterator.zipAll(right.iterator, null, null)
        control1Opt                = Option(control1OrNull)
      } locally {

        // 1: Diffs for current control
        outputSingleControlDiffIfNeeded(control1Opt, control2)

        if (fullUpdateBuffer exists (_.getAttributesCount >= FullUpdateThreshold))
          break()

        // 2: Diffs for descendant controls if any
        def getMark(control: XFormsControl) =
          document.getStaticOps.getMark(control.getPrefixedId)

        // Custom extractor to make match below nicer
        object ControlWithMark {
          def unapply(c: XFormsControl) = if (fullUpdateBuffer.isEmpty) getMark(c) else None
        }

        // Some controls require special processing, as well as xxf:update="full"
        val specificProcessingTookPlace =
          control2 match {
            case c: XXFormsDynamicControl ⇒
              if (c.hasStructuralChange) {
                assert(fullUpdateBuffer.isEmpty, "xxf:dynamic within full update is not supported")

                def replay(r: XMLReceiver) =
                  element("dynamic", uri = XXFORMS_NAMESPACE_URI, atts = List("id" → c.getId))(r)

                processFullUpdateForContent(c, replay)
                true
              } else
                false
            case c: XFormsComponentControl ⇒
              if (c.hasStructuralChange) {
                assert(fullUpdateBuffer.isEmpty, "XBL full update within full update is not supported")

                val mark =
                  getMark(c).ensuring(_.isDefined, "missing mark").get

                processFullUpdateForContent(c, mark.replay)
                true
              } else
                false
            case c @ ControlWithMark(mark) ⇒
              tryBreakable {
                // Output to buffer
                val buffer = new SAXStore
                outputDescendantControlsDiffs(control1Opt, c, Some(buffer))(buffer)
                // Incremental updates did not trigger full updates, replay the output
                buffer.replay(receiver)
              } catchBreak {
                // Incremental updates did trigger full updates
                processFullUpdateForContent(c, mark.replay)
              }
              true
            case _ ⇒
              false
          }

        if (! specificProcessingTookPlace)
          outputDescendantControlsDiffs(control1Opt, control2, fullUpdateBuffer)
      }
    } else
      assert(left.isEmpty, "illegal state when comparing controls")
  }

  private def outputSingleControlDiffIfNeeded(
    control1Opt : Option[XFormsControl],
    control2    : XFormsControl)(implicit
    receiver    : XMLReceiver
  ): Unit = {
    // Force a change for controls whose values changed in the request
    // Q: Do we need a distinction between new iteration AND control just becoming relevant?
    if (control2.supportAjaxUpdates &&
        (valueChangeControlIds(control2.effectiveId) || ! control2.equalsExternal(control1Opt.orNull)))
      control2.outputAjaxDiff(
        new XMLReceiverHelper(receiver),
        control1Opt.orNull,
        new AttributesImpl,
        isNewlyVisibleSubtree = control1Opt.isEmpty
      )
  }

  private def outputDescendantControlsDiffs(
    control1Opt      : Option[XFormsControl],
    control2         : XFormsControl,
    fullUpdateBuffer : Option[SAXStore])(implicit
    receiver         : XMLReceiver
  ): Unit = {

    control2 match {
      case containerControl2: XFormsContainerControl ⇒

        val children1 = control1Opt collect { case c: XFormsContainerControl ⇒ c.children } getOrElse Nil
        val children2 = containerControl2.children

        control2 match {
          case repeatControl: XFormsRepeatControl if children1.nonEmpty ⇒
            val size1 = children1.size
            val size2 = children2.size
            if (size1 == size2) {
              diffChildren(children1, children2, fullUpdateBuffer)
            } else if (size2 > size1) {
              outputCopyRepeatTemplate(repeatControl, size1 + 1, size2)
              diffChildren(children1, children2.view(0, size1), fullUpdateBuffer)
              diffChildren(Nil, children2.view(size1, children2.size), fullUpdateBuffer)
            } else if (size2 < size1) {
              outputDeleteRepeatTemplate(control2, size1 - size2)
              diffChildren(children1.view(0, size2), children2, fullUpdateBuffer)
            }
          case repeatControl: XFormsRepeatControl if control1Opt.isEmpty ⇒
            // New nested xf:repeat
            val size2 = children2.size
            if (size2 > 1) {
              // don't copy the first template, which is already copied when the parent is copied
              outputCopyRepeatTemplate(repeatControl, 2, size2)
            } else if (size2 == 1) {
              // NOP, the client already has the template copied
            } else if (size2 == 0) {
              // Delete first template
              outputDeleteRepeatTemplate(control2, 1)
            }
            diffChildren(Nil, children2, fullUpdateBuffer)

          case repeatControl: XFormsRepeatControl if children1.isEmpty ⇒
            val size2 = children2.size
            if (size2 > 0) {
              outputCopyRepeatTemplate(repeatControl, 1, size2)
              diffChildren(Nil, children2, fullUpdateBuffer)
            }
          case _ ⇒
            // Other grouping control
            diffChildren(children1, children2, fullUpdateBuffer)
        }
      case _ ⇒
        // NOP, not a grouping control
    }
  }

  private def processFullUpdateForContent(
    control  : XFormsControl,
    replay   : XMLReceiver ⇒ Unit)(implicit
    receiver : XMLReceiver
  ): Unit = {

    def setupController = {

      implicit val controller = new ElementHandlerController

      import XHTMLOutput.register

      XHTMLBodyHandler.registerHandlers(controller, document)

      // AVTs on HTML elements
      if (XFormsProperties.isHostLanguageAVTs) {
        register(classOf[XXFormsAttributeHandler], XXFORMS_NAMESPACE_URI, "attribute", any = true)
        register(classOf[XHTMLElementHandler], XMLConstants.XHTML_NAMESPACE_URI)
      }

      // Swallow XForms elements that are unknown
      register(classOf[NullHandler], XFORMS_NAMESPACE_URI)
      register(classOf[NullHandler], XXFORMS_NAMESPACE_URI)
      register(classOf[NullHandler], XBL_NAMESPACE_URI)

      controller
    }

    def setupOutputPipeline(controller: ElementHandlerController) = {
      // Create the output SAX pipeline:
      //
      // - perform URL rewriting
      // - serialize to String
      //
      // NOTE: we could possibly hook-up the standard epilogue here, which would:
      //
      // - perform URL rewriting
      // - apply the theme
      // - serialize
      //
      // But this would raise some issues:
      //
      // - epilogue must match on xhtml:* instead of xhtml:html
      // - themes must be modified to support XHTML fragments
      // - serialization must output here, not to the ExternalContext OutputStream
      //
      // So for now, perform simple steps here, and later this can be revisited.
      //
      val externalContext = NetUtils.getExternalContext

      controller.setOutput(
        new DeferredXMLReceiverImpl(
          new XHTMLRewrite().getRewriteXMLReceiver(
            externalContext.getResponse,
            HTMLFragmentSerializer.create(new ContentHandlerWriter(receiver), skipRootElement = true),
            true
          )
        )
      )

      // We know we serialize to plain HTML so unlike during initial page show, we don't need a particular prefix
      val handlerContext = new HandlerContext(controller, document, externalContext, control.effectiveId) {
        override def findXHTMLPrefix = ""
      }

      handlerContext.restoreContext(control)
      controller.setElementHandlerContext(handlerContext)
    }

    // Setup everything and replay
    withElement(
      "inner-html",
      prefix = "xxf",
      uri    = XXFORMS_NAMESPACE_URI,
      atts   = List("id" → namespaceId(document, control.effectiveId))
    ) {
      val controller = setupController
      setupOutputPipeline(controller)

      controller.startDocument()
      replay(controller)
      controller.endDocument()
    }
  }

  private def repeatDetails(id: String) =
    id.indexOf(REPEAT_SEPARATOR) match {
      case -1    ⇒ (id, "")
      case index ⇒ (id.substring(0, index), id.substring(index + 1))
    }

  private def outputDeleteRepeatTemplate(
    control  : XFormsControl,
    count    : Int)(implicit
    receiver : XMLReceiver
  ): Unit =
    if (! isTestMode) {

      val (templateId, parentIndexes) = repeatDetails(control.effectiveId)

      element(
        "delete-repeat-elements",
        prefix = "xxf",
        uri    = XXFORMS_NAMESPACE_URI,
        atts   = List(
          "id"             → namespaceId(document, templateId),
          "parent-indexes" → parentIndexes,
          "count"          → count.toString
        )
      )
    }

  private def outputCopyRepeatTemplate(
    control     : XFormsControl,
    startSuffix : Int,
    endSuffix   : Int)(implicit
    receiver    : XMLReceiver
  ): Unit =
    if (! isTestMode) {

      val (_, parentIndexes) = repeatDetails(control.effectiveId)

      element(
        "copy-repeat-template",
        prefix = "xxf",
        uri    = XXFORMS_NAMESPACE_URI,
        atts   = List(
          "id"             → namespaceId(document, control.prefixedId), // templates are global
          "parent-indexes" → parentIndexes,
          "start-suffix"   → startSuffix.toString,
          "end-suffix"     → endSuffix.toString
        )
      )
    }
}