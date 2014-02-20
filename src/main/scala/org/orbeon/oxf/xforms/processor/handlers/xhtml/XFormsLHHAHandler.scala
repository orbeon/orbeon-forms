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

import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xml.XMLUtils
import org.xml.sax.Attributes
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.LHHAC
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext

/**
 * Handler for label, help, hint and alert when those are placed outside controls.
 */
class XFormsLHHAHandler extends XFormsBaseHandlerXHTML(false, false) {

    override def start(uri: String, localname: String, qName: String, attributes: Attributes) {

        // Find control ids based on @for attribute
        val lhhaPrefixedId = handlerContext.getPrefixedId(attributes)
        val lhhaEffectiveId = handlerContext.getEffectiveId(attributes)

        containingDocument.getStaticOps.getControlAnalysis(lhhaPrefixedId) match {
            case lhhaAnalysis: LHHAAnalysis if ! lhhaAnalysis.isLocal ⇒

                lhhaAnalysis.targetControl match {
                    case Some(targetControl) ⇒

                        val isTemplate = handlerContext.isTemplate

                        // Find concrete control if possible, to retrieve the concrete LHHA values
                        val xformsControl =
                            if (! isTemplate) {
                                Option(containingDocument.getControls.resolveObjectById(lhhaEffectiveId, targetControl.staticId, null)) match {
                                    case Some(control: XFormsControl) ⇒ Some(control)
                                    case Some(otherObject) ⇒
                                        // TODO: Better/more consistent error handling
                                        containingDocument.getControls.getIndentedLogger.logWarning(
                                            "", "Object pointed by @for attribute is not a control",
                                            "class", otherObject.getClass.getName)
                                        return
                                    case None ⇒
                                        // TODO: Better/more consistent error handling
                                        containingDocument.getControls.getIndentedLogger.logWarning(
                                            "", "Object pointed by @for attribute not found")
                                        return
                                }
                            } else
                                None

                        // Here we statically determine the effective id of the target control. This assumes that we
                        // don't cross repeat boundaries.
                        // In the future, we want to be more flexible, see:
                        // https://github.com/orbeon/orbeon-forms/issues/241
                        val targetControlEffectiveId = XFormsUtils.getRelatedEffectiveId(lhhaEffectiveId, targetControl.staticId)

                        val lhhaType = LHHAC.valueOf(localname.toUpperCase)

                        val forEffectiveId =
                            if (lhhaType == LHHAC.LABEL)
                                XFormsLHHAHandler.findTargetControlFor(handlerContext, targetControl, targetControlEffectiveId)
                            else
                                None

                        handleLabelHintHelpAlert(lhhaAnalysis, targetControlEffectiveId, forEffectiveId.orNull, lhhaType, xformsControl.orNull, isTemplate, true)

                    case _ ⇒
                        // Don't output markup for the LHHA
                        // Can happen if the @for points to a non-existing control
                }
            case _ ⇒
                // Don't output markup for the LHHA
                // This can happen if the author forgot a @for attribute, but also for xf:group/xf:label[not(@for)]
                // Q: Can this also happen if we don't find the LHHAAnalysis?
        }
    }
}

object XFormsLHHAHandler {

    def findTargetControlFor(handlerContext: HandlerContext, targetControl: ElementAnalysis, targetControlEffectiveId: String): Option[String] = {

        // NOTE: This is funky. We do this because for some controls, @for does not point to the standard foo$bar$$c.1-2-3
        // id, but to another id. So we try to guess which handler is being used. However, this will not produce the
        // correct result all the time as matching is not only based on QName! A much better solution would be to always
        // use the foo$bar$$c.1-2-3 scheme for the @for id of a control. Also note that some controls don't support @for.

        val controlElement = targetControl.element

        handlerContext.getController.getHandler(controlElement) match {
            case handler: XFormsControlLifecyleHandler ⇒

                // Perform minimal handler initialization because we just want to use it to get the effective id
                handler.setContext(handlerContext)

                handler.init(
                    controlElement.getNamespaceURI,
                    controlElement.getName,
                    XMLUtils.buildQName(controlElement.getNamespacePrefix, controlElement.getName),
                    XMLUtils.getSAXAttributes(controlElement),
                    targetControl)

                Some(handler.getForEffectiveId(targetControlEffectiveId))
            case _ ⇒
                None
        }
    }
}