package org.orbeon.oxf.processor.pdf

import com.openhtmltopdf.pdfboxout.{PdfBoxOutputDevice, PdfBoxUserAgent}
import com.openhtmltopdf.resource.ImageResource
import com.openhtmltopdf.swing.NaiveUserAgent
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.TryUtils.TryOps
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupportTrait, ExpirationScope, FileItemSupport, IndentedLogger, URLRewriterUtils}

import java.io.InputStream
import java.net.URI

class CustomUserAgentOHTP(outputDevice: PdfBoxOutputDevice, pipelineContext          : PipelineContext)(implicit
                                                                                                        externalContext          : ExternalContext,
                                                                                                        indentedLogger           : IndentedLogger,
                                                                                                        coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
) extends PdfBoxUserAgent(outputDevice) {
  import Private._
  //  override def getImageResource(originalUriString: String): ImageResource = {
  //    val resolvedUriString = resolveURI(originalUriString)
  //    val localUriString =
  //      FileItemSupport.inputStreamToAnyURI(
  //        openStream(resolvedUriString),
  //        ExpirationScope.Request)(
  //        XHTMLToPDFProcessor.logger.logger
  //      )._1.toString
  //
  //    super.getImageResource(localUriString)
  //  }

  override def resolveURI(uri: String): String = {

    // All resources we care about here are resource URLs. The PDF pipeline makes sure that the servlet
    // URL rewriter processes the XHTML output to rewrite resource URLs to absolute paths, including
    // the servlet context and version number if needed. In addition, CSS resources must either use
    // relative paths when pointing to other CSS files or images, or go through the XForms CSS rewriter,
    // which also generates absolute paths.
    // So all we need to do here is rewrite the resulting path to an absolute URL.
    // NOTE: We used to call rewriteResourceURL() here as the PDF pipeline did not do URL rewriting.
    // However this caused issues, for example resources like background images referred by CSS files
    // could be rewritten twice: once by the XForms resource rewriter, and a second time here.
    indentedLogger.logDebug("pdf", "before resolving URL", "url", uri)

    val resolved =
      URLRewriterUtils.rewriteServiceURL(
        requestOpt.orNull,
        uri,
        UrlRewriteMode.AbsoluteNoContext
      )

    indentedLogger.logDebug("pdf", "after resolving URL", "url", resolved)
    resolved
  }

  // Called by:
  // - getCSSResource
  // - getImageResource below
  // - getBinaryResource (not sure when called)
  // - getXMLResource (not sure when called)
  override protected def openStream(uri: String): InputStream = {

    val resolvedURI = resolveURI(uri)

    // TODO: Use xf:submission code instead
    // Tell callee we are loading that we are a servlet environment, as in effect we act like
    // a browser retrieving resources directly, not like a portlet. This is the case also if we are
    // called by the proxy portlet or if we are directly within a portlet.

    val url = URI.create(resolvedURI)
    val headers =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = url,
        hasCredentials   = false,
        customHeaders    = Map(Headers.OrbeonClient -> List("servlet")),
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = name => requestOpt flatMap (r => Connection.getHeaderFromRequest(r)(name))
      )

    val cxr =
      Connection.connectNow(
        method          = HttpMethod.GET,
        url             = url,
        credentials     = None,
        content         = None,
        headers         = headers,
        loadState       = true,
        saveState   = true,
        logBody         = false)(
        logger          = indentedLogger,
        externalContext = externalContext
      )

    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = false)(identity) doEitherWay {
      pipelineContext.addContextListener((_: Boolean) => cxr.close())
    } get
  }

  private object Private {

    val requestOpt = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))
  }
}