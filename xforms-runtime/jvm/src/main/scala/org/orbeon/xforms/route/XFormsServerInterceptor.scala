package org.orbeon.xforms.route

import org.orbeon.oxf.controller.PageFlowInterceptor
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{HttpMethod, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger}
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xforms.event.XFormsServer
import org.orbeon.oxf.xforms.state.RequestParameters
import org.orbeon.xforms.Constants


object XFormsServerInterceptor extends PageFlowInterceptor {

  private val XFormsServerRegex = "/xforms-server(/.*)?".r

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Boolean = {

    implicit val indentedLogger: IndentedLogger = Loggers.newIndentedLogger("server")

    val req = ec.getRequest

    def matchesMediatypes: Boolean = {
      val mediatypeOpt = ContentTypes.getContentTypeMediaType(req.getContentType)
      mediatypeOpt.contains(ContentTypes.ApplicationXWwwFormUrlencoded) ||
      mediatypeOpt.contains(ContentTypes.MultipartFormDataContentType)
    }

    def containsSubmissionParams: Boolean =
      req.getFirstParamAsString(Constants.UuidFieldName).nonEmpty &&
      req.getFirstParamAsString(Constants.SubmissionIdFieldName).nonEmpty

    (req.getRequestPath, req.getMethod) match {
      case (XFormsServerRegex(subPath), method) =>
        (subPath, method) match {
          case ("/upload", HttpMethod.POST) =>
            XFormsUploadRoute.process()
          case ("/upload", _) =>
            ec.getResponse.setStatus(StatusCode.MethodNotAllowed)
          case (_, HttpMethod.POST) =>
            XFormsServerRoute.process()
          case (_, HttpMethod.HEAD | HttpMethod.GET) =>
            XFormsAssetServerRoute.process()
          case _ =>
            ec.getResponse.setStatus(StatusCode.MethodNotAllowed)
        }
        true
      case (_, HttpMethod.POST) if matchesMediatypes && containsSubmissionParams =>
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
        true
      case _ =>
        false
    }
  }
}
