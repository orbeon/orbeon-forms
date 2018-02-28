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

import java.{lang ⇒ jl}

import org.orbeon.oxf.processor.converter.XHTMLRewrite
import org.orbeon.oxf.util.{ContentHandlerWriter, NetUtils}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils.namespaceId
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls.{XFormsRepeatControl, XXFormsDynamicControl}
import org.orbeon.oxf.xforms.processor.handlers._
import org.orbeon.oxf.xforms.processor.handlers.xhtml.{XHTMLBodyHandler, XHTMLElementHandler, XHTMLHeadHandler, XXFormsAttributeHandler}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsProperties}
import org.orbeon.oxf.xml._

import scala.collection.{immutable ⇒ i}
import scala.util.control.Breaks

class ControlsComparator(
  document                       : XFormsContainingDocument,
  valueChangeControlIdsAndValues : i.Map[String, String],
  isTestMode                     : Boolean
) extends XMLReceiverSupport {

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

  // Q: Do we need a distinction between new iteration AND control just becoming relevant?
  private def outputSingleControlDiffIfNeeded(
    control1Opt : Option[XFormsControl],
    control2    : XFormsControl)(implicit
    receiver    : XMLReceiver
  ): Unit =
    if (control2.supportAjaxUpdates)
      control2 match {
        case c: XFormsValueControl ⇒
          // See https://github.com/orbeon/orbeon-forms/issues/2442
          val clientValueOpt   = valueChangeControlIdsAndValues.get(c.effectiveId)
          val controlValue1Opt = control1Opt.asInstanceOf[Option[XFormsValueControl]]
          if (! c.compareExternalMaybeClientValue(clientValueOpt, controlValue1Opt))
            c.outputAjaxDiffMaybeClientValue(
              clientValueOpt,
              controlValue1Opt
            )
        case c ⇒
          if (! c.compareExternalMaybeClientValue(None, control1Opt))
            c.outputAjaxDiff(
              previousControlOpt = control1Opt,
              content            = None)(
              ch                 = new XMLReceiverHelper(receiver)
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

    withElement(
      "inner-html",
      prefix = "xxf",
      uri    = XXFORMS_NAMESPACE_URI,
      atts   = List("id" → namespaceId(document, control.effectiveId))
    ) {

      // Setup everything and replay
      withElement(
        "value",
        prefix = "xxf",
        uri    = XXFORMS_NAMESPACE_URI
      ) {
        val controller = setupController
        setupOutputPipeline(controller)

        controller.startDocument()
        replay(controller)
        controller.endDocument()
      }

      val controlsToInitialize = XHTMLHeadHandler.gatherJavaScriptInitializations(control)
      if (controlsToInitialize.nonEmpty) {
        val sb = new jl.StringBuilder
        sb.append('{')
        XHTMLHeadHandler.buildJavaScriptInitializations(
          containingDocument   = document,
          prependComma         = false,
          controlsToInitialize = controlsToInitialize,
          sb                   = sb
        )
        sb.append('}')

        element(
          "init",
          prefix = "xxf",
          uri    = XXFORMS_NAMESPACE_URI,
          text   = sb.toString
        )
      }
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