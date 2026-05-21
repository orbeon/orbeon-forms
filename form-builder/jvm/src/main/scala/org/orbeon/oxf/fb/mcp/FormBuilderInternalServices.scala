package org.orbeon.oxf.fb.mcp

import org.orbeon.connection.StreamedContent
import org.orbeon.oxf.externalcontext.{ExternalContext, SafeRequestContext, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunnerPersistence.DataXml
import org.orbeon.oxf.fr.{AppForm, FormRunner}
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.state.XFormsStateManager

import java.net.URI


private[mcp] object FormBuilderInternalServices {

  def readLocalFormBuilderDocument(documentId: String)(implicit ec: ExternalContext): Array[Byte] = {
    val path = FormRunner.createFormDataBasePath(AppForm.FormBuilder.app, AppForm.FormBuilder.form, isDraft = false, documentId) + DataXml
    val cxr  = connect(HttpMethod.GET, path, None, Map.empty)
    if (cxr.statusCode != StatusCode.Ok)
      throw HttpStatusCodeException(cxr.statusCode)
    NetUtils.inputStreamToByteArray(cxr.content.stream)
  }

  def putLocalFormBuilderDocument(documentId: String, bytes: Array[Byte])(implicit ec: ExternalContext): Unit = {
    val path    = FormRunner.createFormDataBasePath(AppForm.FormBuilder.app, AppForm.FormBuilder.form, isDraft = false, documentId) + DataXml
    val content = StreamedContent.fromBytes(bytes, Some(ContentTypes.XmlContentType))
    val headers = Map(
      Headers.ContentType                                -> List(ContentTypes.XmlContentType),
      org.orbeon.oxf.fr.Version.OrbeonFormDefinitionVersion -> List("1")
    )
    val cxr = connect(HttpMethod.PUT, path, Some(content), headers)
    if (! StatusCode.isSuccessCode(cxr.statusCode))
      throw HttpStatusCodeException(cxr.statusCode)
  }

  def readToolbox(appForm: AppForm)(implicit ec: ExternalContext): Array[Byte] = {
    val path = PathUtils.appendQueryString(
      "/fr/service/custom/orbeon/builder/toolbox",
      PathUtils.encodeSimpleQuery(List("application" -> appForm.app, "form" -> appForm.form))
    )
    val cxr = connect(HttpMethod.GET, path, None, Map.empty)
    if (cxr.statusCode != StatusCode.Ok)
      throw HttpStatusCodeException(cxr.statusCode)
    NetUtils.inputStreamToByteArray(cxr.content.stream)
  }

  private def connect(
    method : HttpMethod,
    path   : String,
    content: Option[StreamedContent],
    headers: Map[String, List[String]]
  )(implicit ec: ExternalContext): org.orbeon.connection.ConnectionResult = {
    implicit val logger          : IndentedLogger            = XFormsStateManager.newIndentedLogger
    implicit val safeRequestCtx  : SafeRequestContext       = SafeRequestContext(ec)
    implicit val resourceResolver: Option[ResourceResolver] = None
    val url = URI.create(URLRewriterUtils.rewriteServiceURL(ec.getRequest, path, UrlRewriteMode.Absolute))
    val allHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = url,
        hasCredentials   = false,
        customHeaders    = headers,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = Connection.getHeaderFromRequest(ec.getRequest)
      )

    Connection.connectNow(method, url, None, content, allHeaders, loadState = true, saveState = true, logBody = false)
  }
}
