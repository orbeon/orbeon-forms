package org.orbeon.oxf.fr.route

import org.orbeon.connection.StreamedContent
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.fr.FormRunnerPath
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.{ExternalContext, SafeRequestContext, UrlRewriteMode}
import org.orbeon.oxf.fr.process.FormRunnerExternalMode.Slf4JLogger
import org.orbeon.oxf.fr.process.{FormRunnerExternalMode, SimpleProcess}
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunnerExternalModeToken}
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.SLF4JLogging.*
import org.orbeon.oxf.xforms.submission.{BaseSubmission, XFormsModelSubmissionSupport}

import java.net.URI
import scala.util.{Failure, Success, Try}


object CallbackRoute extends NativeRoute {

  override def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    implicit val detailsLogger   : IndentedLogger           = new IndentedLogger(FormRunnerExternalMode.Logger)
    implicit val resourceResolver: Option[ResourceResolver] = None
    implicit val safeRequestCtx  : SafeRequestContext       = SafeRequestContext(ec)

    val (token, modeState) = (
      for {
        token     <- ec.getRequest.getFirstParamAsString("token")
        _         <- FormRunnerExternalModeToken.decryptTokenPayloadCheckExpiration((), token).toOption
        modeState <- FormRunnerExternalMode.retrieveStateForToken(token)
      } yield
        token -> modeState
    ).getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))

    val queryParams =
      SimpleProcess.buildPublicStateParams(
        lang        = modeState.publicMetadata.lang,
        embeddable  = modeState.publicMetadata.embeddable,
        formVersion = modeState.publicMetadata.appFormVersion._2,
      ) :::
      SimpleProcess.buildUserAndStandardParamsForModeChange(
        userParams          = Nil, // xxx: anything custom to pass? params should have been saved with `fr:save-state()`
        dataFormatVersion   = DataFormatVersion.Edge,
        privateModeMetadata = modeState.privateMetadata,
      )

    val absoluteResolvedURL =
      URI.create(
        URLRewriterUtils.rewriteServiceURL(
          ec.getRequest,
          FormRunnerPath.formRunnerPath(
            app        = modeState.publicMetadata.appFormVersion._1.app,
            form       = modeState.publicMetadata.appFormVersion._1.form,
            mode       = modeState.publicMetadata.mode.publicName,
            documentId = modeState.publicMetadata.documentId,
            query      = Some(PathUtils.encodeSimpleQuery(queryParams))
          ),
          UrlRewriteMode.Absolute
        )
      )

    val headers =
      Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
        url              = absoluteResolvedURL,
        method           = HttpMethod.POST,
        hasCredentials   = false,
        mediatypeOpt     = Some(ContentTypes.XmlContentType),
        encodingForSOAP  = CharsetNames.Utf8, // won't be used
        customHeaders    = Map.empty,         // xxx anything custom to pass? params should have been saved with `fr:save-state()`
        headersToForward = Connection.headersToForwardFromProperty,
        getHeader        = Connection.getHeaderFromRequest(ec.getRequest)
      )

    val navigationResult  =
      Try {
        val cxr =
          Connection.connectNow(
            method      = HttpMethod.POST,
            url         = absoluteResolvedURL,
            credentials = None,
            content     = Some(StreamedContent.fromBytes(modeState.data, Some(ContentTypes.XmlContentType))),
            headers     = headers,
            loadState   = true,
            saveState   = true,
            logBody     = BaseSubmission.isLogBody
          )

        IOUtils.useAndClose(cxr) { cxr =>
          XFormsModelSubmissionSupport.forwardResultToResponse(cxr, ec.getResponse)
        }
      }

    // We don't want an external actor calling the callback URL multiple times with the same token, therefore resulting
    // in multiple restarts of the Form Runner workflow.
    //
    // - When we successfully navigate to the continuation mode, the state must be removed. There is no reason to try it again.
    // - In case of failure, we could support a new attempt.
    //
    // For now, we simply remove the state in all cases.
    FormRunnerExternalMode.removeStateForTokenDoNotThrow(token)

    // Throw in the unlikely case we get a non-HTTP-related error in the handling of the mode navigation
    navigationResult match {
      case Success(_) =>
        debug("successfully navigated to continuation mode")
      case Failure(t) =>
        error(s"failure navigating to continuation mode: ${OrbeonFormatter.getThrowableMessage(t)}")
        throw t
    }
  }
}
