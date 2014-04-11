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

import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xforms.processor.handlers.NullHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLBodyHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLElementHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLHeadHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XXFormsAttributeHandler
import org.orbeon.oxf.xforms.processor.handlers.xml._
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml._
import scala.collection.JavaConverters._

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
class XFormsToXHTML extends XFormsToSomething {
    
    import XFormsToXHTML._
    
    protected def produceOutput(
            pipelineContext      : PipelineContext,
            outputName           : String,
            externalContext      : ExternalContext,
            indentedLogger       : IndentedLogger,
            stage2CacheableState : XFormsToSomething.Stage2CacheableState,
            containingDocument   : XFormsContainingDocument,
            xmlReceiver          : XMLReceiver) =
        if (outputName == "document")
            outputResponseDocument(externalContext, indentedLogger, stage2CacheableState.template, containingDocument, xmlReceiver)
        else
            testOutputResponseState(containingDocument, indentedLogger, xmlReceiver)

    private def testOutputResponseState(containingDocument: XFormsContainingDocument, indentedLogger: IndentedLogger, xmlReceiver: XMLReceiver): Unit =
        if (! containingDocument.isGotSubmissionReplaceAll)
            XFormsServer.outputAjaxResponse(containingDocument, indentedLogger, null, null, null, null, null, xmlReceiver, false, true)
}

object XFormsToXHTML {

    def outputResponseDocument(
            externalContext    : ExternalContext,
            indentedLogger     : IndentedLogger,
            template           : AnnotatedTemplate,
            containingDocument : XFormsContainingDocument,
            xmlReceiver        : XMLReceiver): Unit = {

        val nonJavaScriptLoads =
            containingDocument.getLoadsToRun.asScala filterNot (_.getResource.startsWith("javascript:"))

        if (containingDocument.isGotSubmissionReplaceAll) {
            // 1. Got a submission with replace="all"
            // NOP: Response already sent out by a submission
            indentedLogger.logDebug("", "handling response for submission with replace=\"all\"")
        } else if (nonJavaScriptLoads.nonEmpty) {
            // 2. Got at least one xf:load which is not a JavaScript call

            // Send redirect on first one
            val location = nonJavaScriptLoads.head.getResource
            indentedLogger.logDebug("", "handling redirect response for xf:load", "url", location)
            externalContext.getResponse.sendRedirect(location, false, false)
            
            // Set isNoRewrite to true, because the resource is either a relative path or already contains the servlet context
            XMLUtils.streamNullDocument(xmlReceiver)
        } else {
            // 3. Regular case: produce an XHTML document out
            val controller = new ElementHandlerController
            
            // Register handlers on controller (the other handlers are registered by the body handler)
            locally {
                val isHTMLDocument = containingDocument.getStaticState.isHTMLDocument

                import XMLConstants.{XHTML_NAMESPACE_URI ⇒ XH}
                import XFormsConstants.{XFORMS_NAMESPACE_URI⇒ XF, XXFORMS_NAMESPACE_URI ⇒ XXF, XBL_NAMESPACE_URI}

                def register[T](clazz: Class[T], ns: String, elementName: String = null, any: Boolean = false) =
                    controller.registerHandler(clazz.getName, ns, elementName, if (any) XHTMLBodyHandler.ANY_MATCHER else null)
                
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
            controller.setElementHandlerContext(new HandlerContext(controller, containingDocument, externalContext, null))
            
            // Process the entire input
            template.saxStore.replay(new ExceptionWrapperXMLReceiver(controller, "converting XHTML+XForms document to XHTML"))
        }
        containingDocument.afterInitialResponse()
    }
}