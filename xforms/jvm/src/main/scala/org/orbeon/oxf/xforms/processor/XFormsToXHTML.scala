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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsServer}
import org.orbeon.oxf.xforms.processor.handlers.{XHTMLOutput, XMLOutput}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.*


/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
class XFormsToXHTML extends XFormsProcessorBase {

  import org.orbeon.oxf.xforms.processor.XFormsToXHTML.*

  protected def produceOutput(
    pipelineContext    : PipelineContext,
    outputName         : String,
    template           : AnnotatedTemplate,
    containingDocument : XFormsContainingDocument,
  )(implicit
    xmlReceiver        : XMLReceiver,
    externalContext    : ExternalContext,
    indentedLogger     : IndentedLogger
  ): Unit =
    if (outputName == "document")
      outputResponseDocument(
        template,
        containingDocument,
      )
    else
      testOutputResponseState(
        containingDocument
      )
}

private object XFormsToXHTML {

  private def outputResponseDocument(
    template           : AnnotatedTemplate,
    containingDocument : XFormsContainingDocument
  )(implicit
    xmlReceiver        : XMLReceiver,
    externalContext    : ExternalContext,
    indentedLogger     : IndentedLogger
  ): Unit =
    XFormsAPI.withContainingDocument(containingDocument) { // scope because dynamic properties can cause lazy XPath evaluations

      val nonJavaScriptLoads =
        containingDocument.getNonJavaScriptLoadsToRun

      if (containingDocument.isGotSubmissionReplaceAll) {
        // 1. Got a submission with replace="all"
        // NOP: Response already sent out by a submission
        debug("handling response for submission with `replace=\"all\"`")
      } else if (nonJavaScriptLoads.nonEmpty) {
        // 2. Got at least one xf:load which is not a JavaScript call

        // This is the "load upon initialization in Servlet container, embedded or not" case.
        // See `XFormsLoadAction` for details.
        val location = nonJavaScriptLoads.head.resource
        debug("handling redirect response for `xf:load`", List("url" -> location))
        externalContext.getResponse.sendRedirect(location, isServerSide = false, isExitPortal = false)

        // Set isNoRewrite to true, because the resource is either a relative path or already contains the servlet context
        SAXUtils.streamNullDocument(xmlReceiver)
      } else {
        // 3. Regular case: produce a document
        containingDocument.hostLanguage match {
          case "xhtml" =>
            XHTMLOutput.send(containingDocument, template, externalContext)(xmlReceiver)
          case "xml" =>
            XMLOutput.send(containingDocument, template, externalContext)(xmlReceiver)
          case unknown =>
            throw new OXFException(s"Unknown host language specified: $unknown")
        }
      }
      containingDocument.afterInitialResponse()
    }

  private def testOutputResponseState(
    containingDocument : XFormsContainingDocument
  )(implicit
    xmlReceiver        : XMLReceiver,
    externalContext    : ExternalContext,
    indentedLogger     : IndentedLogger
  ): Unit =
    if (! containingDocument.isGotSubmissionReplaceAll)
      XFormsServer.outputAjaxResponse(
        containingDocument        = containingDocument,
        eventFindings             = ClientEvents.EmptyEventsFindings,
        allEvents                 = false,
        beforeFocusedControlIdOpt = None,
        repeatHierarchyOpt        = None,
        requestParametersForAll   = null,
        testOutputAllActions      = true
      )
}