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
import org.orbeon.oxf.xforms.processor.handlers.xhtml.{XHTMLBodyHandler, XHTMLElementHandler, XHTMLHeadHandler, XXFormsAttributeHandler}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsProperties}
import org.orbeon.oxf.xml._
import org.orbeon.xforms.Namespaces


object XHTMLOutput {

  def send(
    xfcd            : XFormsContainingDocument,
    template        : AnnotatedTemplate,
    externalContext : ExternalContext)(implicit
    xmlReceiver     : XMLReceiver
  ): Unit = {
    implicit val controller = new ElementHandlerController

    // Register handlers on controller (the other handlers are registered by the body handler)
    locally {
      val isHTMLDocument = xfcd.staticState.isHTMLDocument

      import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI => XH}
      import org.orbeon.xforms.XFormsNames.{XFORMS_NAMESPACE_URI => XF, XXFORMS_NAMESPACE_URI => XXF}

      if (isHTMLDocument) {
        register(classOf[XHTMLHeadHandler], XH, "head")
        register(classOf[XHTMLBodyHandler], XH, "body")
      } else {
        throw new NotImplementedError("XML handlers are not implemented yet")
      }

      // Register a handler for AVTs on HTML elements
      if (XFormsProperties.isHostLanguageAVTs) {
        register(classOf[XXFormsAttributeHandler], XXF, "attribute")

        if (isHTMLDocument)
          register(classOf[XHTMLElementHandler], XH)
      }

      // Swallow XForms elements that are unknown
      if (isHTMLDocument) {
        register(classOf[NullHandler], XF)
        register(classOf[NullHandler], XXF)
        register(classOf[NullHandler], Namespaces.XBL)
      }
    }

    // Set final output and handler context
    controller.setOutput(new DeferredXMLReceiverImpl(xmlReceiver))
    controller.setElementHandlerContext(new HandlerContext(controller, xfcd, externalContext, null))

    // Process the entire input
    template.saxStore.replay(new ExceptionWrapperXMLReceiver(controller, "converting XHTML+XForms document to XHTML"))
  }

  def register[T](
    clazz               : Class[T],
    ns                  : String,
    elementName         : String = null,
    any                 : Boolean = false)(
    implicit controller : ElementHandlerController
  ): Unit =
    controller.registerHandler(
      clazz.getName,
      ns,
      elementName,
      if (any) XHTMLBodyHandler.ANY_MATCHER else null
    )
}
