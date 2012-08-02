/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.XFormsControl
import org.dom4j.Document
import org.xml.sax.{Locator, Attributes}
import org.orbeon.oxf.xml.{XMLUtils, EmbeddedDocumentXMLReceiver, TransformerUtils, ElementHandlerController}
import java.lang.StringBuilder


class XXFormsComponentHandler extends XFormsControlLifecyleHandler(false) {

    private var elementName: String = _
    private var elementQName: String = _

    protected override def getContainingElementName = elementName
    protected override def getContainingElementQName = elementQName

    private lazy val binding    = containingDocument.getStaticOps.getBinding(getPrefixedId) getOrElse (throw new IllegalStateException)
    private lazy val handleLHHA = binding.abstractBinding.modeLHHA && ! binding.abstractBinding.modeLHHACustom

    override def init(uri: String, localname: String, qName: String, attributes: Attributes, matched: AnyRef): Unit = {
        super.init(uri, localname, qName, attributes, matched)

        elementName = binding.abstractBinding.containerElementName
        elementQName = XMLUtils.buildQName(handlerContext.findXHTMLPrefix, elementName)
    }

    protected override def addCustomClasses(classes: StringBuilder, control: XFormsControl): Unit = {
        if (classes.length != 0)
            classes.append(' ')

        classes.append("xbl-component xbl-" + binding.abstractBinding.cssName)
    }

    protected def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl): Unit = {

        val prefixedId = getPrefixedId
        val controller = handlerContext.getController

        handlerContext.pushComponentContext(prefixedId)

        // Process shadow content if present
        val shadowTree = binding.fullShadowTree
        if (shadowTree ne null)
            XXFormsComponentHandler.processShadowTree(controller, shadowTree)
    }

    protected override def handleControlEnd(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl) =
        handlerContext.popComponentContext()

    protected override def handleLabel() = if (handleLHHA) super.handleLabel()
    protected override def handleAlert() = if (handleLHHA) super.handleAlert()
    protected override def handleHint()  = if (handleLHHA) super.handleHint()
    protected override def handleHelp()  = if (handleLHHA) super.handleHelp()

    // Don't use @for as we ae not pointing to an HTML control
    override def getForEffectiveId(effectiveId: String) = null
}

object XXFormsComponentHandler {

    def processShadowTree(controller: ElementHandlerController, shadowTree: Document): Unit = {
        // Tell the controller we are providing a new body
        controller.startBody()

        // Forward shadow content to handler
        // TODO: would be better to handle inclusion and namespaces using XIncludeProcessor facilities instead of custom code
        TransformerUtils.writeDom4j(shadowTree, new EmbeddedDocumentXMLReceiver(controller) {

            var level = 0

            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) {

                if (level != 0)
                    super.startElement(uri, localname, qName, attributes)

                level += 1
            }

            override def endElement(uri: String, localname: String, qName: String) {

                level -= 1

                if (level != 0)
                    super.endElement(uri, localname, qName)
            }

            override def setDocumentLocator(locator: Locator) {
                // NOP for now. In the future, we should push/pop the locator on ElementHandlerController
            }
        })

        // Tell the controller we are done with the new body
        controller.endBody()
    }
}