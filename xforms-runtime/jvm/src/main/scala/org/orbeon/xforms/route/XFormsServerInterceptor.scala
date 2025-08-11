package org.orbeon.xforms.route

import org.orbeon.oxf.controller.PageFlowInterceptor
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger}
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xforms.event.XFormsServer
import org.orbeon.oxf.xforms.state.RequestParameters
import org.orbeon.xforms.Constants


object XFormsServerInterceptor extends PageFlowInterceptor {

  def matches(req: ExternalContext.Request): Boolean = {

    def isNotXFormsServer: Boolean = {
      val path = req.getRequestPath
      path != "/xforms-server" && ! path.startsWith("/xforms-server/")
    }

    def matchesMediatypes: Boolean = {
      val mediatypeOpt = ContentTypes.getContentTypeMediaType(req.getContentType)
      mediatypeOpt.contains(ContentTypes.ApplicationXWwwFormUrlencoded) ||
      mediatypeOpt.contains(ContentTypes.MultipartFormDataContentType)
    }

    def containsSubmissionParams: Boolean =
      req.getFirstParamAsString(Constants.UuidFieldName).nonEmpty &&
      req.getFirstParamAsString(Constants.SubmissionIdFieldName).nonEmpty

    isNotXFormsServer                  &&
      req.getMethod == HttpMethod.POST &&
      matchesMediatypes                &&
      containsSubmissionParams
  }

  def process(matchResult: RegexpMatcher.MatchResult)(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    implicit val indentedLogger: IndentedLogger = Loggers.newIndentedLogger("server")

    val req = ec.getRequest

    XFormsServer.processEvents(
      logRequestResponse      = Loggers.isDebugEnabled("server-body"),
      requestParameters       = RequestParameters(
        uuid                         = req.getFirstParamAsString(Constants.UuidFieldName).get,
        sequenceOpt                  = None,
        submissionIdOpt              = req.getFirstParamAsString(Constants.SubmissionIdFieldName),
        encodedClientStaticStateOpt  = None,
        encodedClientDynamicStateOpt = None,
      ),
      requestParametersForAll = throw new IllegalStateException,
      extractedEvents         = Nil,
      xmlReceiverOpt          = None,
      responseForReplaceAll   = ec.getResponse,
      beforeProcessRequest    = _ => (),
      extractWireEvents       = _ => Nil,
      trustEvents             = false
    )
  }
}
