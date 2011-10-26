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
package org.orbeon.oxf.xforms.processor.handlers

import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLHeadHandler
import java.lang.String
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandler

class XXFormsDynamicHandler extends XFormsBaseHandler(false, false) {

    private var elementName: String = _
    private var elementQName: String = _

    override def start(uri: String, localname: String, qName: String, attributes: Attributes) {

        val controller = handlerContext.getController
        val contentHandler = controller.getOutput

        val staticId = handlerContext.getId(attributes)
        val prefixedId = handlerContext.getIdPrefix + staticId
        val effectiveId = handlerContext.getEffectiveId(attributes)

        val xhtmlPrefix = handlerContext.findXHTMLPrefix

        this.elementName = "div"
        this.elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName)

        val classes = "xxforms-dynamic-control"
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName, getAttributes(attributes, classes, effectiveId))
        handlerContext.pushComponentContext(prefixedId)

        if (!handlerContext.isTemplate) {
            containingDocument.getObjectByEffectiveId(effectiveId) match {
                case control: XXFormsDynamicControl =>
                    // Output new scripts upon update if any
                    if (!containingDocument.isInitializing && control.newScripts.nonEmpty) {
                        val helper = new ContentHandlerHelper(contentHandler)
                        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))
                        XHTMLHeadHandler.outputScripts(helper, control.newScripts)
                        helper.endElement()
                        control.newScripts = Seq.empty
                    }
                    // Output new markup
                    control.nested foreach
                        (n => processShadowTree(controller, n.template))

                case _ =>
            }
        }
    }

    def processShadowTree(controller: ElementHandlerController, shadowTree: SAXStore) {
        controller.startBody()

        // Replay content of body   
        shadowTree.replay(new ForwardingXMLReceiver(controller) {

            setForward(false)

            var level = 0

            // Filter out start/end doc
            override def startDocument() = ()
            override def endDocument() = ()

            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) {
                super.startElement(uri, localname, qName, attributes)

                // Entering body
                if (level == 1 && localname == "body")
                    setForward(true)

                level += 1
            }

            override def endElement(uri: String, localname: String, qName: String) {
                level -= 1

                // Exiting body
                if (level == 1 && localname == "body")
                    setForward(false)

                super.endElement(uri, localname, qName)
            }

            // Let prefix mappings go through no matter what so that mappings on html/body work
            override def startPrefixMapping(prefix: String, uri: String) =
                getXMLReceiver.startPrefixMapping(prefix, uri)

            override def endPrefixMapping(prefix: String) =
                getXMLReceiver.endPrefixMapping(prefix)
        })
        controller.endBody()
    }

    override def end(uri: String, localname: String, qName: String) {
        handlerContext.popComponentContext()
        val controller = handlerContext.getController
        val contentHandler = controller.getOutput
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName)
    }
}