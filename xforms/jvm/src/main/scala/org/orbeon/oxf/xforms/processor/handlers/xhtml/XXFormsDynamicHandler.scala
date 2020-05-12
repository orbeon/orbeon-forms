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

import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl
import org.orbeon.oxf.xforms.processor.ScriptBuilder
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes

class XXFormsDynamicHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsBaseHandler(uri, localname, qName, localAtts, matched, handlerContext, false, false) {

  private var elementName: String = _
  private var elementQName: String = _

  override def start(): Unit = {

    val controller = xformsHandlerContext.getController
    val contentHandler = controller.getOutput

    // TODO: Duplicated with `XFormsBaseHandlerXHTML`. Use mixin?
    val prefixedId = xformsHandlerContext.getPrefixedId(attributes)
    val effectiveId = xformsHandlerContext.getEffectiveId(attributes)

    val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix

    this.elementName = "div"
    this.elementQName = XMLUtils.buildQName(xhtmlPrefix, elementName)

    val classes = "xxforms-dynamic-control"
    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName, getIdClassXHTMLAttributes(attributes, classes, effectiveId))
    xformsHandlerContext.pushComponentContext(prefixedId)

    containingDocument.getControlByEffectiveId(effectiveId) match {
      case control: XXFormsDynamicControl =>
        // Output new scripts upon update if any
        // NOTE: Not implemented as of 2016-01-18.
        if (! containingDocument.isInitializing && control.newScripts.nonEmpty && containingDocument.isServeInlineResources) {
          implicit val helper = new XMLReceiverHelper(contentHandler)
          helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))
          // NOTE: As of 2018-05-03, this is still not functional, so there is no impact
          // for https://github.com/orbeon/orbeon-forms/issues/3565
          ScriptBuilder.writeScripts(control.newScripts, s => helper.text(ScriptBuilder.escapeJavaScriptInsideScript(s)))
          helper.endElement()
          control.clearNewScripts()
        }
        // Output new markup
        control.nested foreach { nested =>
          xformsHandlerContext.pushPartAnalysis(nested.partAnalysis)
          processShadowTree(controller, nested.template)

          // Add part globals for top-level part only (see comments in PartAnalysisImpl)
          if (nested.partAnalysis.isTopLevel)
            nested.partAnalysis.getGlobals foreach { global =>
              XXFormsComponentHandler.processShadowTree(xformsHandlerContext.getController, global.templateTree)
            }

          xformsHandlerContext.popPartAnalysis()
        }

      case _ =>
    }
  }

  def processShadowTree(controller: ElementHandlerController, shadowTree: SAXStore): Unit = {
    controller.startBody()

    // Replay content of body
    shadowTree.replay(new ForwardingXMLReceiver(controller) {

      setForward(false)

      var level = 0

      // Filter out start/end doc
      override def startDocument() = ()
      override def endDocument() = ()

      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
        super.startElement(uri, localname, qName, attributes)

        // Entering body
        if (level == 1 && localname == "body")
          setForward(true)

        level += 1
      }

      override def endElement(uri: String, localname: String, qName: String): Unit = {
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

  override def end(): Unit = {
    xformsHandlerContext.popComponentContext()
    val controller = xformsHandlerContext.getController
    val contentHandler = controller.getOutput
    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, elementQName)
  }
}