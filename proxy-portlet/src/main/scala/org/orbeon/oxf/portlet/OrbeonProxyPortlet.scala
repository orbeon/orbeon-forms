/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.portlet

import javax.portlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.orbeon.errorified.Exceptions
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.WSRPURLRewriter
import org.orbeon.oxf.fr.embedding._
import org.orbeon.oxf.http.{ApacheHttpClient, HttpClient, HttpClientSettings, StreamedContent}
import org.orbeon.oxf.portlet.liferay.{LiferayAPI, LiferaySupport}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.util.control.NonFatal

/**
 * Orbeon Forms Form Runner proxy portlet.
 *
 * This portlet allows access to a remote Form Runner instance.
 */
class OrbeonProxyPortlet extends GenericPortlet with ProxyPortletEdit with BufferedPortlet {

  import OrbeonProxyPortlet._

  private case class PortletSettings(
    forwardHeaders         : Map[String, String], // lowercase name → original name
    forwardParams          : Set[String],
    forwardProperties      : Map[String, String], // lowercase name → original name
    keepParams             : Set[String],
    useShortNamespaces     : Boolean,
    resourcesRegex         : String,
    httpClient             : HttpClient
   ) {
    val FormRunnerResourcePath = resourcesRegex.r
  }

  private class ProxyPortletEmbeddingContextWithResponse(
    settings           : PortletSettings,
    context            : PortletContext,
    request            : PortletRequest,
    response           : MimeResponse,
    httpClient         : HttpClient,
    useShortNamespaces : Boolean
  ) extends PortletEmbeddingContextWithResponse(
    context,
    request,
    response,
    httpClient,
    useShortNamespaces
  ) {
    // Modified version which adds specified portal parameters to the decoded URL
    override def decodeURL(encoded: String): String = {

      if (settings.keepParams.nonEmpty) {

        val (path, originalParams) = splitQueryDecodeParams(super.decodeURL(encoded))

        val newParamsIt =
          findOriginalServletRequest(request) match {
            case Some(httpServletRequest) ⇒
              for {
                (key, values) ← httpServletRequest.getParameterMap.asScala.iterator
                if settings.keepParams(key)
                value ← values
              } yield
                key → value
            case None ⇒
              Iterator.empty
          }

        recombineQuery(path, originalParams.iterator ++ newParamsIt)
      } else {
        super.decodeURL(encoded)
      }
    }
  }

  // For BufferedPortlet
  def findTitle(request: RenderRequest) = Option(getTitle(request))

  private var settingsOpt: Option[PortletSettings] = None

  override def init(config: PortletConfig): Unit = {
    APISupport.Logger.info("initializing Form Runner proxy portlet")
    super.init(config)
    settingsOpt = Some(
      PortletSettings(
        forwardHeaders     = stringToSet(config.getInitParameter("forward-headers")).map(name ⇒ name.toLowerCase → name)(breakOut),
        forwardParams      = stringToSet(config.getInitParameter("forward-parameters")),
        forwardProperties  = stringToSet(config.getInitParameter("forward-properties")).map(name ⇒ name.toLowerCase → name)(breakOut),
        keepParams         = stringToSet(config.getInitParameter("keep-parameters")),
        useShortNamespaces = config.getInitParameter("use-short-namespaces") != "false",
        resourcesRegex     = Option(config.getInitParameter("resources-regex")) getOrElse APISupport.DefaultFormRunnerResourcePath,
        httpClient         = new ApacheHttpClient(HttpClientSettings(config.getInitParameter))
      )
    )
  }

  override def destroy(): Unit = {
    APISupport.Logger.info("destroying Form Runner proxy portlet")
    settingsOpt foreach (_.httpClient.shutdown())
    settingsOpt = None
    super.destroy()
  }

  // Try to find `getHttpServletRequest` only the first time this is accessed
  private lazy val getHttpServletRequest =
    try Some(LiferayAPI.getHttpServletRequest _)
    catch { case _: NoClassDefFoundError | _: ClassNotFoundException ⇒ None }

  private lazy val getOriginalServletRequest =
    try Some(LiferayAPI.getOriginalServletRequest _)
    catch { case _: NoClassDefFoundError | _: ClassNotFoundException ⇒ None }

  private def findServletRequest(request: PortletRequest): Option[HttpServletRequest] =
    for {
      f1      ← getHttpServletRequest
      httpReq ← Option(f1(request))
    } yield
      httpReq

  private def findOriginalServletRequest(request: PortletRequest): Option[HttpServletRequest] =
    for {
      f1              ← getHttpServletRequest
      httpReq         ← Option(f1(request))
      f2              ← getOriginalServletRequest
      originalHttpReq ← Option(f2(httpReq))
    } yield
      originalHttpReq

  // Portlet render
  override def doView(request: RenderRequest, response: RenderResponse): Unit =
    settingsOpt foreach { settings ⇒
      withRootException("view render", new PortletException(_)) {

        implicit val ctx = new ProxyPortletEmbeddingContextWithResponse(
          settings,
          getPortletContext,
          request,
          response,
          settings.httpClient,
          settings.useShortNamespaces
        )

        bufferedRender(
          request,
          response,
          APISupport.callService(createRequestDetails(settings, request, response.getNamespace))._1
        )
      }
    }

  // Portlet action
  override def processAction(request: ActionRequest, response: ActionResponse): Unit =
    settingsOpt foreach { settings ⇒
      request.getPortletMode match {
        case PortletMode.VIEW ⇒ doViewAction(request, response)
        case PortletMode.EDIT ⇒ doEditAction(request, response)
        case _ ⇒ // NOP
      }
    }

  private def doViewAction(request: ActionRequest, response: ActionResponse): Unit =
    settingsOpt foreach { settings ⇒
      withRootException("view action", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContext(
          getPortletContext,
          request,
          response,
          settings.httpClient,
          settings.useShortNamespaces
        )
        bufferedProcessAction(
          request,
          response,
          APISupport.callService(createRequestDetails(settings, request, response.getNamespace))._1
        )
      }
    }

  // Portlet resource
  override def serveResource(request: ResourceRequest, response: ResourceResponse): Unit =
    settingsOpt foreach { settings ⇒
      withRootException("resource", new PortletException(_)) {

        // Use this context so that URLs in XML responses are rewritten with `keep-parameters` as well
        implicit val ctx = new ProxyPortletEmbeddingContextWithResponse(
          settings,
          getPortletContext,
          request,
          response,
          settings.httpClient,
          settings.useShortNamespaces
        )

        APISupport.sanitizeResourceId(request.getResourceID, settings.FormRunnerResourcePath) match {
          case Some(sanitizedResourcePath) ⇒
            val url = APISupport.formRunnerURL(getPreference(request, FormRunnerURL), sanitizedResourcePath, embeddable = false)

            val requestDetails =
              newRequestDetails(
                settings,
                request,
                contentFromRequest(request, response.getNamespace),
                url
              )

            APISupport.proxyResource(requestDetails)

          case None ⇒
            // What were the Portlet API designers thinking?
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, HttpServletResponse.SC_NOT_FOUND.toString)
        }
      }
    }

  private def preferenceFromPortalQuery(request: PortletRequest, pref: Pref): Option[String] =
    if (getBooleanPreference(request, EnableURLParameters))
      portalQuery(request) collectFirst { case (pref.nameLabel.publicName, value) ⇒ value}
    else
      None

  private def preferenceFromPublicRenderParameter(request: PortletRequest, pref: Pref): Option[String] =
    if (getBooleanPreference(request, EnablePublicRenderParameters))
      publicRenderParametersIt(request) collectFirst { case (pref.nameLabel.publicName, value) ⇒ value }
    else
      None

  private def preferenceFromSessionParameter(request: PortletRequest, pref: Pref) = {
    if (getBooleanPreference(request, EnableSessionParameters))
      sessionParameters(request) collectFirst { case (pref.nameLabel.publicName, value) ⇒ value }
    else
      None
  }

  private def getPreferenceOrRequested(request: PortletRequest, pref: Pref) =
    preferenceFromSessionParameter(request, pref)        orElse
      preferenceFromPublicRenderParameter(request, pref) orElse
      preferenceFromPortalQuery(request, pref)           getOrElse
      getPreference(request, pref)

  private def createRequestDetails(settings: PortletSettings, request: PortletRequest, namespace: String): RequestDetails = {
    // Determine URL based on preferences and request
    val path = {

      def pathParameterOpt =
        Option(request.getParameter(WSRPURLRewriter.PathParameterName))

      def defaultPath =
        if (getPreference(request, Page) == "home")
          APISupport.formRunnerHomePath(None)
        else
          APISupport.formRunnerPath(
            getPreferenceOrRequested(request, AppName),
            getPreferenceOrRequested(request, FormName),
            getPreferenceOrRequested(request, Page),
            Option(getPreferenceOrRequested(request, DocumentId)),
            None
          )

      def filterMode(mode: String) =
        if (getBooleanPreference(request, ReadOnly) && mode == Edit.name) View.name else mode

      pathParameterOpt getOrElse defaultPath match {
        case path @ "/xforms-server-submit" ⇒
          path
        // Incoming path is Form Runner path without document id
        case FormRunnerPath(appName, formName, mode, _, query) ⇒
          APISupport.formRunnerPath(appName, formName, filterMode(mode), None, Option(query))
        // Incoming path is Form Runner path with document id
        case FormRunnerDocumentPath(appName, formName, mode, documentId, _, query) ⇒
          APISupport.formRunnerPath(appName, formName, filterMode(mode), Some(documentId), Option(query))
        // Incoming path is Form Runner Home page
        case FormRunnerHome(_, query) ⇒
          APISupport.formRunnerHomePath(Option(query))
        // Unsupported path
        case otherPath ⇒
          throw new PortletException(s"Unsupported path: `$otherPath`")
      }
    }

    newRequestDetails(
      settings,
      request,
      contentFromRequest(request, namespace),
      APISupport.formRunnerURL(getPreference(request, FormRunnerURL), path, embeddable = true)
    )
  }

  private def portalQuery(request: PortletRequest) =
    collectByErasedType[String](request.getAttribute("javax.servlet.forward.query_string")) map decodeSimpleQuery getOrElse Nil

  private def publicRenderParametersIt(request: PortletRequest): Iterator[(String, String)] =
    for {
      (key, values) ← request.getPublicParameterMap.asScala.iterator
      firstValue    ← values.headOption
      nonBlankValue ← firstValue.trimAllToOpt
    } yield
      key → nonBlankValue

  private def sessionParameters(request: PortletRequest): Iterator[(String, String)] = {

    def fromSession(name: String) =
      request.getPortletSession.getAttribute(name, PortletSession.APPLICATION_SCOPE).asInstanceOf[String]

    Iterator(
      DocumentId.nameLabel.publicName → fromSession("LIFERAY_SHARED_ORBEON_DOCUMENT_ID"),
      AppName.nameLabel.publicName    → fromSession("LIFERAY_SHARED_ORBEON_APP"),
      FormName.nameLabel.publicName   → fromSession("LIFERAY_SHARED_ORBEON_FORM"),
      Page.nameLabel.publicName       → fromSession("LIFERAY_SHARED_ORBEON_PAGE")
    )
  }

  private def newRequestDetails(
    settings: PortletSettings,
    request : PortletRequest,
    content : Option[StreamedContent],
    url     : String
  ): RequestDetails = {

    val sendLanguage = getBooleanPreference(request, SendLiferayLanguage)
    val sendUser     = getBooleanPreference(request, SendLiferayUser)

    val servletReqHeaders =
      findServletRequest(request).toList flatMap APISupport.requestHeaders

    import collection.JavaConverters._

    def portletRequestHeadersIt =
      for {
        name   ← request.getPropertyNames.asScala
        values = request.getProperties(name).asScala.toList
      } yield
        name → values

    val portletReqHeaders = portletRequestHeadersIt.to[List]

    // Language information
    // NOTE: Format returned is e.g. "en_US" or "fr_FR".
    def languageHeader =
      if (sendLanguage)
        LiferaySupport.languageHeader(request)
      else
        None

    // User information
    // This assumes that the security filter sets Orbeon-* headers
    def userHeadersToForward =
      if (sendUser)
        LiferaySupport.AllHeaderNamesLowerToCapitalized
      else
        Map.empty[String, String]

    APISupport.Logger.debug(s"incoming servlet request headers: $servletReqHeaders")
    APISupport.Logger.debug(s"incoming portlet request headers: $portletReqHeaders")

    val headersToSet =
      APISupport.headersToForward(servletReqHeaders, settings.forwardHeaders)                            ++
      APISupport.headersToForward(portletReqHeaders, settings.forwardProperties ++ userHeadersToForward) ++
      languageHeader

    val paramsToSet =
      for {
        pair @ (name, _) ← portalQuery(request)
        if settings.forwardParams(name)
      } yield
        pair

    APISupport.Logger.debug(s"outgoing request headers: $headersToSet")
    APISupport.Logger.debug(s"outgoing request parameters: $paramsToSet")

    RequestDetails(
      content = content,
      url     = url,
      headers = headersToSet.to[List],
      params  = paramsToSet
    )
  }

  private def contentFromRequest(request: PortletRequest, namespace: String): Option[StreamedContent] =
    request match {
      case clientDataRequest: ClientDataRequest if clientDataRequest.getMethod == "POST" ⇒
        Some(
          StreamedContent(
            clientDataRequest.getPortletInputStream,
            Option(clientDataRequest.getContentType),
            Some(clientDataRequest.getContentLength.toLong) filter (_ >= 0),
            None
          )
        )
      case _ ⇒
        None
    }
}

private[portlet] object OrbeonProxyPortlet {

  val FormRunnerHome           = """/fr/(\?(.*))?""".r
  val FormRunnerPath           = """/fr/([^/]+)/([^/]+)/(new|summary)(\?(.*))?""".r
  val FormRunnerDocumentPath   = """/fr/([^/]+)/([^/]+)/(new|edit|view)/([^/?]+)?(\?(.*))?""".r

  def withRootException[T](action: String, newException: Throwable ⇒ Exception)(body: ⇒ T)(implicit ctx: PortletContext): T =
    try body
    catch {
      case NonFatal(t) ⇒
        ctx.log("Exception when running " + action + '\n' + OrbeonFormatter.format(t))
        throw newException(Exceptions.getRootThrowable(t))
    }
}