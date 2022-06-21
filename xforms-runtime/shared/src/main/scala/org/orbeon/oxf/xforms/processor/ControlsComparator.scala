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

import cats.syntax.option._
import org.orbeon.oxf.rewrite.Rewrite
import org.orbeon.oxf.util.ContentHandlerWriter
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.processor.handlers._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml._
import org.orbeon.xforms.Constants.RepeatSeparator
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{XFormsCrossPlatformSupport, rpc}
import shapeless.syntax.typeable._

import scala.collection.{immutable => i}
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
    left             : Iterable[XFormsControl],
    right            : Iterable[XFormsControl],
    fullUpdateBuffer : Option[SAXStore])(implicit
    receiver         : XMLReceiver
  ): Unit = {

    if (right.nonEmpty) {

      assert(left.size == right.size || left.isEmpty, "illegal state when comparing controls")

      for {
        (control1OrNull, control2) <- left.iterator.zipAll(right.iterator, null, null)
        control1Opt                = Option(control1OrNull)
      } locally {

        // 1: Diffs for current control
        outputSingleControlDiffIfNeeded(control1Opt, control2)

        if (fullUpdateBuffer exists (_.getAttributesCount >= FullUpdateThreshold))
          break()

        // 2: Diffs for descendant controls if any

        // Custom extractor to make match below nicer
        object ControlWithMark {
          def unapply(c: XFormsControl): Option[SAXStore#Mark] = if (fullUpdateBuffer.isEmpty) getMark(c) else None
        }

        // Some controls require special processing, as well as `xxf:update="full"`
        val specificProcessingTookPlace =
          control2 match {
            case c: XXFormsDynamicControl =>
              if (c.hasStructuralChange) {
                assert(fullUpdateBuffer.isEmpty, "xxf:dynamic within full update is not supported")

                def replay(r: XMLReceiver): Unit =
                  element("dynamic", uri = XXFORMS_NAMESPACE_URI, atts = List("id" -> c.getId))(r)

                processFullUpdateForContent(c, None, replay)
                true
              } else
                false
            case c: XFormsSwitchControl =>
              val otherSwitchControlOpt = control1Opt.asInstanceOf[Option[XFormsSwitchControl]]
              if (c.staticControl.hasFullUpdate && c.getSelectedCaseEffectiveId != c.getOtherSelectedCaseEffectiveId(otherSwitchControlOpt)) {

                val prevSelectedCaseOpt = otherSwitchControlOpt flatMap (_.selectedCaseIfRelevantOpt)

                c.selectedCaseIfRelevantOpt match {
                  case Some(selectedCase) => processFullUpdateForContent(c, prevSelectedCaseOpt, getMarkOrThrow(selectedCase).replay)
                  case None               => processFullUpdateForContent(c, prevSelectedCaseOpt, _ => ())
                }
                true
              } else
                false
            case c: XFormsCaseControl =>
              // See https://github.com/orbeon/orbeon-forms/issues/3509 and
              // https://github.com/orbeon/orbeon-forms/issues/3510.
              // Also do not handle descendants if we are a hidden full update case
              if (c.getSwitch.staticControl.hasFullUpdate && c.effectiveId != c.getSwitch.getSelectedCaseEffectiveId) {
                true
              } else
                false
            case c: XFormsComponentControl =>
              if (c.hasStructuralChange) {
                assert(fullUpdateBuffer.isEmpty, "XBL full update within full update is not supported")
                // TODO: Consider passing `c.some` for updates within Form Builder. But is this necessary?
                //processFullUpdateForContent(c, c.some, getMarkOrThrow(c).replay)
                processFullUpdateForContent(c, None, getMarkOrThrow(c).replay)
                true
              } else
                false
            case _: XFormsRepeatControl =>
              // Repeat iterations are handled separately
              false
            case c @ ControlWithMark(mark) =>
              tryBreakable {
                // Output to buffer
                val buffer = new SAXStore
                outputDescendantControlsDiffs(control1Opt, c, Some(buffer))(buffer)
                // Incremental updates did not trigger full updates, replay the output
                buffer.replay(receiver)
              } catchBreak {
                // Incremental updates did trigger full updates

                val controlAdjustedForSwitch =
                  c match {
                    case c: XFormsCaseControl => c.parent
                    case c                    => c
                  }

                processFullUpdateForContent(controlAdjustedForSwitch, None, mark.replay)
              }
              true
            case _ =>
              false
          }

        if (! specificProcessingTookPlace)
          outputDescendantControlsDiffs(control1Opt, control2, fullUpdateBuffer)

        def outputInit(
          effectiveId    : String,
          relevant       : Option[Boolean],
          readonly       : Option[Boolean],
          valueChangeOpt : Option[String]
        ): Unit = {

          val atts =
            relevant.map("relevant" -> _.toString) ++:
            readonly.map("readonly" -> _.toString) ++:
            List("id" -> document.namespaceId(effectiveId))

          withElement(
            "init",
            prefix = "xxf",
            uri    = XXFORMS_NAMESPACE_URI,
            atts   = atts
          ) {
            valueChangeOpt foreach { value =>
              element(
                "value",
                prefix = "xxf",
                uri    = XXFORMS_NAMESPACE_URI,
                text   = value
              )
            }
          }
        }

        def findValueChange(vcc1: XFormsValueComponentControl, vcc2: XFormsValueComponentControl): Option[String] = {

          val mustOutputValueChange =
            vcc2.mustOutputAjaxValueChange(
              previousValue   = valueChangeControlIdsAndValues.get(vcc2.effectiveId) orElse Some(vcc1.getExternalValue()),
              previousControl = Some(vcc1)
            )

          mustOutputValueChange option vcc2.getEscapedExternalValue
        }

        // Tell the client to make lifecycle changes after the nested markup is handled
        // - https://github.com/orbeon/orbeon-forms/issues/3888
        // - https://github.com/orbeon/orbeon-forms/issues/3909
        // - https://github.com/orbeon/orbeon-forms/issues/3957
        control2 match {
          case cc2: XFormsComponentControl if cc2.staticControl.commonBinding.modeJavaScriptLifecycle =>

            control1Opt.asInstanceOf[Option[XFormsComponentControl]] match {
              case None =>
                if (cc2.isRelevant) {

                  outputInit(
                    effectiveId    = cc2.effectiveId,
                    relevant       = None,
                    readonly       = None,
                    valueChangeOpt =
                      for {
                        vcc2 <- cc2.narrowTo[XFormsValueComponentControl]
                        if vcc2.staticControl.commonBinding.modeExternalValue
                      } yield
                        vcc2.getEscapedExternalValue
                  )
                }
              case Some(cc1) =>

                val relevantChanged = cc1.isRelevant != cc2.isRelevant
                val readonlyChanged = cc1.isReadonly != cc2.isReadonly

                val valueChangeOpt =
                  for {
                    vcc2        <- cc2.narrowTo[XFormsValueComponentControl]
                    if vcc2.staticControl.commonBinding.modeExternalValue
                    valueChange <- findValueChange(cc1.asInstanceOf[XFormsValueComponentControl], vcc2)
                  } yield
                    valueChange

                if (relevantChanged || readonlyChanged || valueChangeOpt.isDefined)
                  outputInit(
                    effectiveId    = cc2.effectiveId,
                    relevant       = relevantChanged option cc2.isRelevant,
                    readonly       = readonlyChanged option cc2.isReadonly,
                    valueChangeOpt = valueChangeOpt
                  )
            }

          case vcc2: XFormsValueComponentControl if vcc2.staticControl.commonBinding.modeExternalValue =>

            // NOTE: `modeExternalValue` can be used without `modeJavaScriptLifecycle`, although that is an uncommon use case.

            val valueChangeOpt =
              for {
                c1          <- control1Opt
                valueChange <- findValueChange(c1.asInstanceOf[XFormsValueComponentControl], vcc2)
              } yield
                valueChange

            if (valueChangeOpt.isDefined)
              outputInit(
                effectiveId    = vcc2.effectiveId,
                relevant       = None,
                readonly       = None,
                valueChangeOpt = valueChangeOpt
              )

          case _ =>
        }
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
        case c: XFormsValueControl =>
          // See https://github.com/orbeon/orbeon-forms/issues/2442
          val clientValueOpt   = valueChangeControlIdsAndValues.get(c.effectiveId)
          val valueControl1Opt = control1Opt.asInstanceOf[Option[XFormsValueControl]]
          if (! c.compareExternalMaybeClientValue(clientValueOpt, valueControl1Opt))
            c.outputAjaxDiffMaybeClientValue(
              clientValueOpt,
              valueControl1Opt
            )
        case c =>
          if (! c.compareExternalMaybeClientValue(None, control1Opt))
            c.outputAjaxDiff(
              previousControlOpt = control1Opt,
              content            = None)(
              ch                 = new XMLReceiverHelper(receiver)
            )
      }

  private def getMark(control: XFormsControl): Option[SAXStore#Mark] =
    document.staticOps.getMark(control.getPrefixedId)

  private def getMarkOrThrow(control: XFormsControl): SAXStore#Mark =
    getMark(control).ensuring(_.isDefined, "missing mark").get

  private def outputDescendantControlsDiffs(
    control1Opt      : Option[XFormsControl],
    control2         : XFormsControl,
    fullUpdateBuffer : Option[SAXStore])(implicit
    receiver         : XMLReceiver
  ): Unit = {

    control2 match {
      case containerControl2: XFormsContainerControl =>

        val children1 = control1Opt collect { case c: XFormsContainerControl => c.children } getOrElse Nil
        val children2 = containerControl2.children

        containerControl2 match {
          case repeatControl2: XFormsRepeatControl =>

            // `xf:repeat` needs special treatment to handle adding and removing iterations
            // See https://github.com/orbeon/orbeon-forms/issues/4011

            val size1 = children1.size
            val size2 = children2.size

            val commonSize = size1 min size2

            if (commonSize > 0)
              diffChildren(children1.view(0, commonSize), children2.view(0, commonSize), fullUpdateBuffer)

            if (size2 > size1) {

              val mark = getMarkOrThrow(containerControl2)

              if (control1Opt.isDefined)
                for (newIteration <- children2.view(size1, size2))
                  processFullUpdateForContent(newIteration, None, receiver => mark.replay(new SkipRootElement(receiver)))
              else
                diffChildren(Nil, children2.view(size1, size2), fullUpdateBuffer) // test mode

            } else if (size2 < size1) {
              outputDeleteRepeatElements(repeatControl2, size1 - size2)
            }

          case componentControl2: XFormsComponentControl =>

            // When iterations have moved and there is no structural change, the content of lazy binding components can be
            // incompatible. This should happen only when relevance changes.
            // NOTE: `control1Opt` must be defined, otherwise we would be in a new iteration.
            // See https://github.com/orbeon/orbeon-forms/issues/4035
            if (componentControl2.staticControl.hasLazyBinding && control1Opt.exists(_.isRelevant != componentControl2.isRelevant))
              processFullUpdateForContent(componentControl2, None, getMarkOrThrow(componentControl2).replay)
            else
              diffChildren(children1, children2, fullUpdateBuffer)

          case _ =>
            // Other grouping control
            diffChildren(children1, children2, fullUpdateBuffer)
        }
      case _ =>
        // NOP, not a grouping control
    }
  }

  private def processFullUpdateForContent(
    control           : XFormsControl,
    previousControlOpt: Option[XFormsControl],
    replay            : XMLReceiver => Unit)(implicit
    receiver          : XMLReceiver
  ): Unit = {

    val repeatIterationControlOpt = control.narrowTo[XFormsRepeatIterationControl]

    def setupController: ElementHandlerController[HandlerContext] =
      new ElementHandlerController[HandlerContext](
        XHTMLOutput.fullUpdatePf.orElse(XHTMLOutput.bodyPf),
        XHTMLOutput.defaultPf
      )

    def setupOutputPipeline(ehc: ElementHandlerController[HandlerContext]): Unit = {
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
      val externalContext = XFormsCrossPlatformSupport.externalContext

      ehc.output =
        new DeferredXMLReceiverImpl(
          Rewrite.getRewriteXMLReceiver(
            externalContext.getResponse,
            XFormsCrossPlatformSupport.createHTMLFragmentXmlReceiver(
              new ContentHandlerWriter(receiver, supportFlush = false),
              skipRootElement = true
            ),
            fragment = true,
            XMLConstants.XHTML_NAMESPACE_URI
          )
        )

      // We know we serialize to plain HTML so unlike during initial page show, we don't need a particular prefix
      val handlerContext = new HandlerContext(ehc, document, externalContext, control.effectiveId.some) {
        override def findXHTMLPrefix = ""
      }

      handlerContext.restoreContext(control)

      // Special case for a repeat iteration
      repeatIterationControlOpt foreach { c =>
        handlerContext.pushRepeatContext(
          c.iterationIndex,
          c.repeat.getIndex == c.iterationIndex
        )
      }

      ehc.handlerContext = handlerContext
    }

    withElement(
      "inner-html",
      prefix = "xxf",
      uri    = XXFORMS_NAMESPACE_URI,
      atts   = List("id" -> document.namespaceId(control.effectiveId))
    ) {

      def outputInitOrDestroy(control: XFormsControl, isInit: Boolean): Unit = {
        val controlsToInitialize = ScriptBuilder.gatherJavaScriptInitializations(control, isInit)
        if (controlsToInitialize.nonEmpty) {

          import io.circe.generic.auto._
          import io.circe.syntax._

          val controls =
            controlsToInitialize map { case (id, value) => rpc.Control(document.namespaceId(id), value) }

          element(
            if (isInit) "init" else "destroy",
            prefix = "xxf",
            uri    = XXFORMS_NAMESPACE_URI,
            text   = controls.asJson.noSpaces
          )
        }
      }

      previousControlOpt foreach (outputInitOrDestroy(_, isInit = false))

      withElement(
        "value",
        prefix = "xxf",
        uri    = XXFORMS_NAMESPACE_URI
      ) {
        // Setup everything and replay
        val ehc = setupController
        setupOutputPipeline(ehc)

        ehc.startDocument()
        repeatIterationControlOpt foreach { _ =>
          ehc.startElement("", "root", "root", XMLReceiverSupport.EmptyAttributes)
        }
        replay(ehc)
        repeatIterationControlOpt foreach { _ =>
          ehc.endElement("", "root", "root")
        }
        ehc.endDocument()
      }

      outputInitOrDestroy(control, isInit = true)
    }
  }

  private def repeatDetails(id: String) =
    id.indexOf(RepeatSeparator) match {
      case -1    => (id, "")
      case index => (id.substring(0, index), id.substring(index + 1))
    }

  private def outputDeleteRepeatElements(
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
          "id"             -> document.namespaceId(templateId),
          "parent-indexes" -> parentIndexes,
          "count"          -> count.toString
        )
      )
    }
}