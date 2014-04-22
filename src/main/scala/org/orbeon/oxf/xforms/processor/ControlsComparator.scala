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
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsContainerControl, XFormsControl}
import org.orbeon.oxf.xforms.control.controls.{XXFormsDynamicControl, XFormsRepeatControl}
import org.orbeon.oxf.xforms.processor.handlers._
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLElementHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XXFormsAttributeHandler
import org.orbeon.oxf.xml._
import org.xml.sax.helpers.AttributesImpl
import scala.collection.JavaConverters._
import scala.util.control.Breaks

class ControlsComparator(containingDocument: XFormsContainingDocument, valueChangeControlIds: collection.Set[String], isTestMode: Boolean) {
    
    def this(containingDocument: XFormsContainingDocument, valueChangeControlIds: ju.Set[String], isTestMode: Boolean) =
        this(containingDocument, Option(valueChangeControlIds) map (_.asScala) getOrElse Set.empty[String], isTestMode)

    private val FullUpdateThreshold = XFormsProperties.getAjaxFullUpdateThreshold(containingDocument)

    private val breaks = new Breaks
    import breaks._

    def diffJava(receiver: XMLReceiverHelper, left: ju.List[XFormsControl], right: ju.List[XFormsControl]) =
        diff(if (left ne null) left.asScala else Nil, if (right ne null) right.asScala else Nil, bufferingForFullUpdate = false)(receiver)

    def diff(left: Seq[XFormsControl], right: Seq[XFormsControl], bufferingForFullUpdate: Boolean)(receiver: XMLReceiverHelper): Unit =
        if (right.nonEmpty) {

            assert(left.size == right.size || left.isEmpty, "illegal state when comparing controls")

            for {
                (control1OrNull, control2) ← left.iterator.zipAll(right.iterator, null, null)
                control1Opt                = Option(control1OrNull)
            } locally {

                outputSingleControlDiffIfNeeded(control1Opt, control2)(receiver)

                if (reachedFullUpdateThreshold(bufferingForFullUpdate)(receiver))
                    break()

                def getMark(control: XFormsControl) =
                    containingDocument.getStaticOps.getMark(control.getPrefixedId)

                def getFullUpdateMark(control: XFormsControl) =
                    if (bufferingForFullUpdate || control2.isInstanceOf[XXFormsDynamicControl] || control2.isInstanceOf[XFormsComponentControl])
                        None
                    else
                        getMark(control)

                if (control2.hasStructuralChange) {
                    // xxf:dynamic proper OR top-level XBL within xxf:dynamic

                    assert(! bufferingForFullUpdate, "xxf:dynamic or XBL full update nested within full update are not supported")

                    // Force immediate full update for this control
                    val mark = getMark(control2).ensuring(_.isDefined, "missing mark").get
                    processFullUpdateForContent(mark, control2)(receiver)
                } else
                    getFullUpdateMark(control2) match {
                        case Some(mark) ⇒
                            // Found xxf:update="full"
                            val buffer = new XMLReceiverHelper(new SAXStore)
                            tryBreakable {
                                outputDescendantControlsDiffs(control1Opt, control2, bufferingForFullUpdate = true)(buffer)
                                // Incremental updates did not trigger full updates, replay the output
                                buffer.getXmlReceiver.asInstanceOf[SAXStore].replay(receiver.getXmlReceiver)
                            } catchBreak {
                                // Incremental updates did trigger full updates
                                processFullUpdateForContent(mark, control2)(receiver)
                            }

                        case None ⇒
                            // Regular update
                            outputDescendantControlsDiffs(control1Opt, control2, bufferingForFullUpdate)(receiver)
                    }
            }
        } else {
            assert(left.isEmpty, "illegal state when comparing controls")
        }
    
    private def isValueChangeControl(control: XFormsControl) =
        valueChangeControlIds(control.getEffectiveId)
    
    private def outputSingleControlDiffIfNeeded(control1Opt: Option[XFormsControl], control2: XFormsControl)(receiver: XMLReceiverHelper): Unit =
        // Don't send anything if nothing has changed, but we force a change for controls whose values changed in the request
        if (control2.supportAjaxUpdates && (isValueChangeControl(control2) || ! control2.equalsExternal(control1Opt.orNull))) {
            // Q: Do we need a distinction between new iteration AND control just becoming relevant?
            control2.outputAjaxDiff(receiver, control1Opt.orNull, new AttributesImpl, isNewlyVisibleSubtree = control1Opt.isEmpty)
        }

    private def outputDescendantControlsDiffs(control1Opt: Option[XFormsControl], control2: XFormsControl, bufferingForFullUpdate: Boolean)(receiver: XMLReceiverHelper): Unit =
        control2 match {
            case containerControl2: XFormsContainerControl ⇒ 

                val children1 = control1Opt collect { case c: XFormsContainerControl ⇒ c.children } getOrElse Nil
                val children2 = containerControl2.children
                
                control2 match {
                    case repeatControl: XFormsRepeatControl if children1.nonEmpty ⇒
                        val size1 = children1.size
                        val size2 = children2.size
                        if (size1 == size2) {
                            diff(children1, children2, bufferingForFullUpdate)(receiver)
                        } else if (size2 > size1) {
                            outputCopyRepeatTemplate(repeatControl, size1 + 1, size2)(receiver)
                            diff(children1, children2.view(0, size1), bufferingForFullUpdate)(receiver)
                            diff(Nil, children2.view(size1, children2.size), bufferingForFullUpdate)(receiver)
                        } else if (size2 < size1) {
                            outputDeleteRepeatTemplate(control2, size1 - size2)(receiver)
                            diff(children1.view(0, size2), children2, bufferingForFullUpdate)(receiver)
                        }
                    case repeatControl: XFormsRepeatControl if control1Opt.isEmpty ⇒
                        // Handle new sub-xf:repeat
        
                        // Copy template instructions
                        val size2 = children2.size
                        if (size2 > 1) {
                            // don't copy the first template, which is already copied when the parent is copied
                            outputCopyRepeatTemplate(repeatControl, 2, size2)(receiver)
                        } else if (size2 == 1) {
                            // NOP, the client already has the template copied
                        } else if (size2 == 0) {
                            // Delete first template
                            outputDeleteRepeatTemplate(control2, 1)(receiver)
                        }

                        diff(Nil, children2, bufferingForFullUpdate)(receiver)
                        
                    case repeatControl: XFormsRepeatControl if children1.isEmpty ⇒
                        val size2 = children2.size
                        if (size2 > 0) {
                            outputCopyRepeatTemplate(repeatControl, 1, size2)(receiver)
                            diff(Nil, children2, bufferingForFullUpdate)(receiver)
                        }
                    case _ ⇒
                        // Other grouping control
                        diff(children1, children2, bufferingForFullUpdate)(receiver)
                }
            case _ ⇒
                // NOP, not a grouping control 
        }

    private def reachedFullUpdateThreshold(bufferingForFullUpdate: Boolean)(receiver: XMLReceiverHelper) =
        bufferingForFullUpdate && receiver.getXmlReceiver.asInstanceOf[SAXStore].getAttributesCount >= FullUpdateThreshold

    private def processFullUpdateForContent(mark: SAXStore#Mark, control: XFormsControl)(receiver: XMLReceiverHelper): Unit = {

        val controller = new ElementHandlerController
        XHTMLBodyHandler.registerHandlers(controller, containingDocument)

        // Register handlers on controller
        locally {
            val hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs

            // Register a handler for AVTs on HTML elements
            // TODO: this should be obtained per document, but we only know about this in the extractor
            if (hostLanguageAVTs) {
                controller.registerHandler(classOf[XXFormsAttributeHandler].getName, XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", XHTMLBodyHandler.ANY_MATCHER)
                controller.registerHandler(classOf[XHTMLElementHandler].getName, XMLConstants.XHTML_NAMESPACE_URI)
            }

            // Swallow XForms elements that are unknown
            controller.registerHandler(classOf[NullHandler].getName, XFormsConstants.XFORMS_NAMESPACE_URI)
            controller.registerHandler(classOf[NullHandler].getName, XFormsConstants.XXFORMS_NAMESPACE_URI)
            controller.registerHandler(classOf[NullHandler].getName, XFormsConstants.XBL_NAMESPACE_URI)
        }

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
        controller.setOutput(new DeferredXMLReceiverImpl(
            new XHTMLRewrite().getRewriteXMLReceiver(
                externalContext.getResponse,
                new HTMLFragmentSerializer(new ContentHandlerWriter(receiver.getXmlReceiver), true),
                true)))

        // We know we serialize to plain HTML so unlike during initial page show, we don't need a particular prefix
        val handlerContext = new HandlerContext(controller, containingDocument, externalContext, control.getEffectiveId) {
            override def findXHTMLPrefix = ""
        }

        // Replay into SAX pipeline
        handlerContext.restoreContext(control)
        controller.setElementHandlerContext(handlerContext)
        val attributesImpl = new AttributesImpl
        attributesImpl.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, XFormsUtils.namespaceId(containingDocument, control.getEffectiveId))
        receiver.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "inner-html", attributesImpl)
        locally {
            controller.startDocument()
            mark.replay(controller)
            controller.endDocument()
        }
        receiver.endElement()
    }

    protected def outputDeleteRepeatTemplate(xformsControl2: XFormsControl, count: Int)(receiver: XMLReceiverHelper): Unit =
        if (! isTestMode) {
            val repeatControlId = xformsControl2.getEffectiveId
            val indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_SEPARATOR)
            val templateId = if (indexOfRepeatHierarchySeparator == -1) repeatControlId else repeatControlId.substring(0, indexOfRepeatHierarchySeparator)
            val parentIndexes = if (indexOfRepeatHierarchySeparator == -1) "" else repeatControlId.substring(indexOfRepeatHierarchySeparator + 1)

            receiver.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements", Array(
                "id", XFormsUtils.namespaceId(containingDocument, templateId),
                "parent-indexes", parentIndexes,
                "count", "" + count))
        }

    protected def outputCopyRepeatTemplate(repeatControl: XFormsRepeatControl, startSuffix: Int, endSuffix: Int)(receiver: XMLReceiverHelper): Unit =
        if (! isTestMode) {
            val repeatControlId = repeatControl.getEffectiveId
            val indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_SEPARATOR)
            val parentIndexes = if (indexOfRepeatHierarchySeparator == -1) "" else repeatControlId.substring(indexOfRepeatHierarchySeparator + 1)

            // Get prefixed id without suffix as templates are global
            receiver.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template", Array(
                "id", XFormsUtils.namespaceId(containingDocument, repeatControl.getPrefixedId),
                "parent-indexes", parentIndexes,
                "start-suffix", Integer.toString(startSuffix),
                "end-suffix", Integer.toString(endSuffix)))
        }
}