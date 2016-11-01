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
import org.orbeon.oxf.xforms.processor.handlers.xml._
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsProperties}
import org.orbeon.oxf.xml._


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
      val isHTMLDocument = xfcd.getStaticState.isHTMLDocument

      import org.orbeon.oxf.xforms.XFormsConstants.{XBL_NAMESPACE_URI, XFORMS_NAMESPACE_URI ⇒ XF, XXFORMS_NAMESPACE_URI ⇒ XXF}
      import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI ⇒ XH}

      if (isHTMLDocument) {
        register(classOf[XHTMLHeadHandler], XH, "head")
        register(classOf[XHTMLBodyHandler], XH, "body")
      } else {
        register(classOf[XFormsDefaultControlHandler], XF, "input",    any = true)
        register(classOf[XFormsDefaultControlHandler], XF, "secret",   any = true)
        register(classOf[XFormsDefaultControlHandler], XF, "range",    any = true)
        register(classOf[XFormsDefaultControlHandler], XF, "textarea", any = true)
        register(classOf[XFormsDefaultControlHandler], XF, "output",   any = true)
        register(classOf[XFormsDefaultControlHandler], XF, "trigger",  any = true)
        register(classOf[XFormsDefaultControlHandler], XF, "submit",   any = true)
        register(classOf[XFormsSelectHandler],         XF, "select",   any = true)
        register(classOf[XFormsSelectHandler],         XF, "select1",  any = true)
        register(classOf[XFormsGroupHandler],          XF, "group",    any = true)
        register(classOf[XFormsCaseHandler],           XF, "case",     any = true)
        register(classOf[XFormsRepeatHandler],         XF, "repeat",   any = true)
      }

      // Register a handler for AVTs on HTML elements
      if (XFormsProperties.isHostLanguageAVTs) {
        register(classOf[XXFormsAttributeHandler], XXF, "attribute")

        if (isHTMLDocument)
          register(classOf[XHTMLElementHandler], XH)

        for (additionalAvtElementNamespace ← XFormsProperties.getAdditionalAvtElementNamespaces)
          register(classOf[ElementHandlerXML], additionalAvtElementNamespace)
      }

      // Swallow XForms elements that are unknown
      if (isHTMLDocument) {
        register(classOf[NullHandler], XF)
        register(classOf[NullHandler], XXF)
        register(classOf[NullHandler], XBL_NAMESPACE_URI)
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
