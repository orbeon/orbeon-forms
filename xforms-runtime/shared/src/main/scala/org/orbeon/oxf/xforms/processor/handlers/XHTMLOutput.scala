/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{AppearanceTrait, ComponentControl, OutputControl}
import org.orbeon.oxf.xforms.control.{LHHASupport, XFormsControl}
import org.orbeon.oxf.xforms.processor.handlers.xhtml._
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI => XH}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsGlobalProperties}
import org.orbeon.oxf.xml._
import org.orbeon.xforms.Namespaces._
import org.orbeon.xforms.XFormsNames._
import org.xml.sax.Attributes


object XHTMLOutput {

  import XFormsGlobalProperties.isHostLanguageAVTs

  type BasicHandlerInput   = (String, String, String, Attributes, HandlerContext)
  type ElementHandlerInput = (String, String, String, Attributes, ElementAnalysis, HandlerContext)

  def send(
    xfcd            : XFormsContainingDocument,
    template        : AnnotatedTemplate,
    externalContext : ExternalContext)(implicit
    xmlReceiver     : XMLReceiver
  ): Unit = {

    if (! xfcd.staticState.isHTMLDocument)
      throw new NotImplementedError("XML handlers are not implemented yet")

    val ehc = new ElementHandlerController[HandlerContext](rootPf, defaultPf)

    // Set final output and handler context
    ehc.output = new DeferredXMLReceiverImpl(xmlReceiver)
    ehc.handlerContext = new HandlerContext(ehc, xfcd, externalContext, topLevelControlEffectiveId = None)

    // Process the entire input
    template.saxStore.replay(new ExceptionWrapperXMLReceiver(ehc, "converting XHTML+XForms document to XHTML"))
  }

  private object ComposeFcnOps {
    // Scala 2.13 has this built-oin
    // See https://blog.genuine.com/2019/12/composing-partial-functions-in-scala/
    implicit class PartialCompose[A, B](pf: PartialFunction[A, B]) {
      def andThenPF[C](that: PartialFunction[B, C]): PartialFunction[A, C] =
        Function.unlift(x => pf.lift(x).flatMap(that.lift))
    }
  }

  import ComposeFcnOps._

  val defaultPf: PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = {
    case (ns @ XF,  ln              , qn, atts, hc)                       => new NullHandler        (ns, ln, qn, atts, hc)
    case (ns @ XXF, ln              , qn, atts, hc)                       => new NullHandler        (ns, ln, qn, atts, hc)
    case (ns @ XBL, ln              , qn, atts, hc)                       => new NullHandler        (ns, ln, qn, atts, hc)
  }

  private val attributesPf: PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = {
    case (ns @ XXF, ln @ "attribute", qn, atts, hc) if isHostLanguageAVTs => new NullHandler        (ns, ln, qn, atts, hc)
    case (ns @ XH,  ln              , qn, atts, hc) if isHostLanguageAVTs => new XHTMLElementHandler(ns, ln, qn, atts, hc)
  }

  private val headBodyPf: PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = {
    case (ns @ XH,  ln @ "head"     , qn, atts, hc)                       => new XHTMLHeadHandler   (ns, ln, qn, atts, hc)
    case (ns @ XH,  ln @ "body"     , qn, atts, hc)                       => new XHTMLBodyHandler   (ns, ln, qn, atts, hc)
  }

  val rootPf      : PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = headBodyPf.orElse(attributesPf)
  val fullUpdatePf: PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = attributesPf

  // This just adds `xxf:text` for `xh:title`
  val headPf: PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = {
    case (ns @ XXF,  ln @ "text", qn, atts, hc) => new XXFormsTextHandler(ns, ln, qn, atts, hc)
  }

  private def isFieldSet(hc: HandlerContext, atts: Attributes, c: ElementAnalysis): Boolean =
    XFormsControl.appearances(c).contains(XXFORMS_FIELDSET_APPEARANCE_QNAME) ||
      LHHASupport.hasLabel(hc.containingDocument, hc.getPrefixedId(atts))

  // The efficiency of this depends on how pattern-matching is implemented in Scala.
  // Dotty has an optimized pattern matcher, which implies that Scala 2 doesn't, but we need to
  // benchmark to have an idea of how this fares compared to the older method.
  private val findHandlerTakeElementAnalysisPf: PartialFunction[ElementHandlerInput, ElementHandler[HandlerContext]] = {
    case (ns,       ln                     , qn, atts, c: ComponentControl, hc)                                                      => new XXFormsComponentHandler     (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "output"          , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_TEXT_APPEARANCE_QNAME)      => new XFormsOutputTextHandler     (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "output"          , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_DOWNLOAD_APPEARANCE_QNAME)  => new XFormsOutputDownloadHandler (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "output"          , qn, atts, c: OutputControl   , hc) if c.isImageMediatype                                => new XFormsOutputImageHandler    (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "output"          , qn, atts, c: OutputControl   , hc) if c.isHtmlMediatype                                 => new XFormsOutputHTMLHandler     (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "output"          , qn, atts, c                  , hc)                                                      => new XFormsOutputDefaultHandler  (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "group"           , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)  => new TransparentHandler          (ns, ln, qn, atts,    hc)
    case (ns @ XF,  ln @ "group"           , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_SEPARATOR_APPEARANCE_QNAME) => new XFormsGroupSeparatorHandler (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "group"           , qn, atts, c                  , hc) if isFieldSet(hc, atts, c)                           => new XFormsGroupFieldsetHandler  (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "group"           , qn, atts, c                  , hc)                                                      => new XFormsGroupDefaultHandler   (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "switch"          , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_SEPARATOR_APPEARANCE_QNAME) => new XFormsGroupSeparatorHandler (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "switch"          , qn, atts, c                  , hc)                                                      => new XFormsGroupDefaultHandler   (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "input"           , qn, atts, c                  , hc)                                                      => new XFormsInputHandler          (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "trigger"         , qn, atts, c: AppearanceTrait , hc) if c.appearances(XFORMS_MINIMAL_APPEARANCE_QNAME)    => new XFormsTriggerMinimalHandler (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "trigger"         , qn, atts, c                  , hc)                                                      => new XFormsTriggerFullHandler    (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "submit"          , qn, atts, c: AppearanceTrait , hc) if c.appearances(XFORMS_MINIMAL_APPEARANCE_QNAME)    => new XFormsTriggerMinimalHandler (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "submit"          , qn, atts, c                  , hc)                                                      => new XFormsTriggerFullHandler    (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "case"            , qn, atts, c                  , hc)                                                      => new XFormsCaseHandler           (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "repeat"          , qn, atts, c                  , hc)                                                      => new XFormsRepeatHandler         (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "repeat-iteration", qn, atts, _                  , hc)                                                      => new TransparentHandler          (ns, ln, qn, atts,    hc)
    case (ns @ XF,  ln @ "secret"          , qn, atts, c                  , hc)                                                      => new XFormsSecretHandler         (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "upload"          , qn, atts, c                  , hc)                                                      => new XFormsUploadHandler         (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "range"           , qn, atts, c                  , hc)                                                      => new XFormsRangeHandler          (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "textarea"        , qn, atts, c                  , hc)                                                      => new XFormsTextareaHandler       (ns, ln, qn, atts, c, hc)
    case (ns @ XXF, ln @ "dialog"          , qn, atts, c                  , hc)                                                      => new XXFormsDialogHandler        (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "select"          , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)  => new NullHandler                 (ns, ln, qn, atts,    hc)
    case (ns @ XF,  ln @ "select1"         , qn, atts, c: AppearanceTrait , hc) if c.appearances(XXFORMS_INTERNAL_APPEARANCE_QNAME)  => new NullHandler                 (ns, ln, qn, atts,    hc)
    case (ns @ XF,  ln @ "select"          , qn, atts, c                  , hc)                                                      => new XFormsSelectHandler         (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "select1"         , qn, atts, c                  , hc)                                                      => new XFormsSelect1Handler        (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "label"           , qn, atts, c                  , hc)                                                      => new XFormsLHHAHandler           (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "help"            , qn, atts, c                  , hc)                                                      => new XFormsLHHAHandler           (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "hint"            , qn, atts, c                  , hc)                                                      => new XFormsLHHAHandler           (ns, ln, qn, atts, c, hc)
    case (ns @ XF,  ln @ "alert"           , qn, atts, c                  , hc)                                                      => new XFormsLHHAHandler           (ns, ln, qn, atts, c, hc)
    case (ns @ XXF, ln @ "dynamic"         , qn, atts, _                  , hc)                                                      => new XXFormsDynamicHandler       (ns, ln, qn, atts,    hc)
  }

  val bodyPf: PartialFunction[BasicHandlerInput, ElementHandler[HandlerContext]] = {

    val withElementAnalysis: BasicHandlerInput => Option[ElementHandlerInput] = {
      case (uri, localname, qName, atts, hc) =>
        hc.containingDocument.staticOps.findControlAnalysis(hc.getPrefixedId(atts)) map ((uri, localname, qName, atts, _, hc))
    }

    val findElementAnalysisPf: PartialFunction[BasicHandlerInput, ElementHandlerInput] =
      new PartialFunction[BasicHandlerInput, ElementHandlerInput] {

        def isDefinedAt(x: BasicHandlerInput): Boolean       = withElementAnalysis(x).isDefined
        def apply(x: BasicHandlerInput): ElementHandlerInput = withElementAnalysis(x).get

        override def applyOrElse[A1 <: BasicHandlerInput, B1 >: ElementHandlerInput](x: A1, default: A1 => B1): B1 =
          withElementAnalysis(x).getOrElse(default(x))
      }

    findElementAnalysisPf.andThenPF(findHandlerTakeElementAnalysisPf)
  }
}
