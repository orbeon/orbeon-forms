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

import collection.JavaConverters._
import collection.breakOut
import com.liferay.portal.util.PortalUtil
import java.io._
import java.net.{URI, HttpURLConnection, URL}
import java.{util ⇒ ju}
import javax.portlet._
import org.apache.http.client.CookieStore
import org.apache.http.cookie.CookieOrigin
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.cookie.BrowserCompatSpec
import org.apache.http.message.BasicHeader
import org.orbeon.oxf.externalcontext.WSRPURLRewriter
import org.orbeon.oxf.portlet.liferay.LiferaySupport
import org.orbeon.oxf.util.Headers._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{LoggerFactory, NetUtils}
import org.orbeon.oxf.xml.XMLUtils
import scala.util.control.NonFatal

/**
 * Orbeon Forms Form Runner proxy portlet.
 *
 * This portlet allows access to a remote Form Runner instance.
 */
class OrbeonProxyPortlet extends GenericPortlet with ProxyPortletEdit with BufferedPortlet with AsyncPortlet {

    // NOTE: Async mode needs to be tested
    private def isAsyncPortletLoad = false
    implicit val logger = LoggerFactory.createLogger(classOf[OrbeonProxyPortlet])

    // For BufferedPortlet
    def title(request: RenderRequest) = getTitle(request)
    def portletContext = getPortletContext

    // For AsyncPortlet
    val isMinimalResources = true
    val isWSRPEncodeResources = true
    type AsyncContext = RequestDetails

    // Init parameters to configure headers and parameters forwarding
    private var forwardHeaders = Map.empty[String, String] // lowercase name → original name
    private var forwardParams  = Set.empty[String]

    override def init(config: PortletConfig) {
        super.init(config)

        // Read portlet init parameters
        forwardHeaders = stringToSet(config.getInitParameter("forward-headers")).map(name ⇒ name.toLowerCase → name)(breakOut)
        forwardParams  = stringToSet(config.getInitParameter("forward-parameters"))
    }

    // Try to find getHttpServletRequest only the first time this is accessed
    private lazy val getHttpServletRequest =
        try Some(PortalUtil.getHttpServletRequest _)
        catch { case (_: NoClassDefFoundError) | (_: ClassNotFoundException) ⇒ None }

    private def findServletRequest(request: PortletRequest) =
        getHttpServletRequest flatMap (f ⇒ Option(f(request)))

    // Immutable information about an outgoing request
    case class RequestDetails(
        content: Option[Content],
        session: PortletSession,
        url: String,
        namespace: String,
        headers: Seq[(String, String)],
        params: Seq[(String, String)]
    )

    object RequestDetails {

        def apply(request: PortletRequest, content: Option[Content], url: String, namespace: String): RequestDetails = {

            def headerPairs =
                for {
                    servletRequest ← findServletRequest(request).toList
                    name           ← servletRequest.getHeaderNames.asInstanceOf[ju.Enumeration[String]].asScala
                    values         = servletRequest.getHeaders(name).asInstanceOf[ju.Enumeration[String]].asScala.toList
                } yield
                    name → values

            // Match on headers in a case-insensitive way, but the header we sent follows the capitalization of the
            // header specified in the init parameter.
            def headersToForward =
                for {
                    (name, value) ← filterAndCombineHeaders(headerPairs, out = true)
                    originalName  ← forwardHeaders.get(name.toLowerCase)
                } yield
                    originalName → value

            def paramsToForward =
                for {
                    pair @ (name, _) ← collectByErasedType[String](request.getAttribute("javax.servlet.forward.query_string")) map decodeSimpleQuery getOrElse Seq()
                    if forwardParams(name)
                } yield
                    pair

            val sendLanguage = getPreference(request, SendLiferayLanguage) == "true"
            val sendUser     = getPreference(request, SendLiferayUser)     == "true"

            // Language information
            // NOTE: Format returned is e.g. "en_US" or "fr_FR".
            def languageHeader =
                if (sendLanguage)
                    LiferaySupport.languageHeader(request)
                else
                    None

            // User information
            def userHeaders =
                if (sendUser)
                    for {
                        request   ← findServletRequest(request).toList
                        user      ← Option(PortalUtil.getUser(request)).toList
                        nameValue ← LiferaySupport.userHeaders(user)
                    } yield
                        nameValue
                else
                    Nil

            RequestDetails(
                content,
                request.getPortletSession(true),
                url,
                namespace,
                headersToForward.toList ++ languageHeader.toList ++ userHeaders,
                paramsToForward
            )
        }
    }

    private val FormRunnerHome         = """/fr/(\?(.*))?""".r
    private val FormRunnerPath         = """/fr/([^/]+)/([^/]+)/(new|summary)(\?(.*))?""".r
    private val FormRunnerDocumentPath = """/fr/([^/]+)/([^/]+)/(new|edit|view)/([^/?]+)?(\?(.*))?""".r

    // Portlet render
    override def doView(request: RenderRequest, response: RenderResponse): Unit =
        withRootException("view render", new PortletException(_)) {
            def renderFunction =
                if (isAsyncPortletLoad)
                    startAsyncRender(request, createRequestDetails(request, response.getNamespace), callService)
                else
                    callService(createRequestDetails(request, response.getNamespace))

            bufferedRender(request, response, renderFunction)
        }

    // Portlet action
    override def processAction(request: ActionRequest, response: ActionResponse): Unit =
        request.getPortletMode match {
            case PortletMode.VIEW ⇒ doViewAction(request, response)
            case PortletMode.EDIT ⇒ doEditAction(request, response)
            case _ ⇒ // NOP
        }

    private def doViewAction(request: ActionRequest, response: ActionResponse): Unit =
        withRootException("view action", new PortletException(_)) {
            bufferedProcessAction(request, response, callService(createRequestDetails(request, response.getNamespace)))
        }

    // Portlet resource
    override def serveResource(request: ResourceRequest, response: ResourceResponse): Unit =
        withRootException("resource", new PortletException(_)) {
            if (isAsyncPortletLoad)
                asyncServeResource(request, response, directServeResource(request, response))
            else
                directServeResource(request, response)
        }

    private def directServeResource(request: ResourceRequest, response: ResourceResponse): Unit = {
        val resourceId = request.getResourceID
        val url = buildFormRunnerURL(getPreference(request, FormRunnerURL), resourceId, embeddable = false)

        val namespace = response.getNamespace
        val connection = connectURL(RequestDetails(request, contentFromRequest(request, namespace), url, namespace))

        useAndClose(connection.getInputStream) { is ⇒
            propagateResponseHeaders(connection, response)
            val mediaType = NetUtils.getContentTypeMediaType(connection.getContentType)
            val mustRewrite = XMLUtils.isTextOrJSONContentType(mediaType) || XMLUtils.isXMLMediatype(mediaType)
            readRewriteToPortlet(response, is, mustRewrite, escape = request.getMethod == "POST")
        }
    }

    private def createRequestDetails(request: PortletRequest, namespace: String): RequestDetails = {
        // Determine URL based on preferences and request
        val path = {

            def pathParameterOpt =
                Option(request.getParameter(WSRPURLRewriter.PathParameterName))

            def defaultPath =
                if (getPreference(request, Page) == "home")
                    buildFormRunnerHomePath(None)
                else
                    buildFormRunnerPath(getPreference(request, AppName), getPreference(request, FormName), getPreference(request, Page), None, None)

            def filterAction(action: String) =
                if (getPreference(request, ReadOnly) == "true" && action == "edit") "view" else action

            pathParameterOpt getOrElse defaultPath match {
                case path @ "/xforms-server-submit" ⇒
                    path
                // Incoming path is Form Runner path without document id
                case FormRunnerPath(appName, formName, action, _, query) ⇒
                    buildFormRunnerPath(appName, formName, filterAction(action), None, Option(query))
                // Incoming path is Form Runner path with document id
                case FormRunnerDocumentPath(appName, formName, action, documentId, _, query) ⇒
                    buildFormRunnerPath(appName, formName, filterAction(action), Some(documentId), Option(query))
                // Incoming path is Form Runner Home page
                case FormRunnerHome(_, query) ⇒
                    buildFormRunnerHomePath(Option(query))
                // Unsupported path
                case otherPath ⇒
                    throw new PortletException("Unsupported path: " + otherPath)
            }
        }

        RequestDetails(request, contentFromRequest(request, namespace), buildFormRunnerURL(getPreference(request, FormRunnerURL), path, embeddable = true), namespace)
    }

    private def contentFromRequest(request: PortletRequest, namespace: String): Option[Content] = {
        request match {
            case clientDataRequest: ClientDataRequest if clientDataRequest.getMethod == "POST" ⇒
                // Read content
                if (XMLUtils.isXMLMediatype(NetUtils.getContentTypeMediaType(clientDataRequest.getContentType))) {
                    // Strip namespace ids from content of Ajax request
                    val content = NetUtils.readStreamAsString(new InputStreamReader(clientDataRequest.getPortletInputStream, "utf-8"))
                    val replacedContent = content.replaceAllLiterally(namespace, "")
                    Some(Content(Left(replacedContent), Option(clientDataRequest.getContentType), None))
                } else {
                    // Just read without rewriting
                    Some(Content(Right(NetUtils.inputStreamToByteArray(clientDataRequest.getPortletInputStream)), Option(clientDataRequest.getContentType), None))
                }
            case _ ⇒ None
        }
    }

    // Call the Orbeon service at the other end
    private def callService(requestDetails: RequestDetails): ContentOrRedirect = {
        val connection = connectURL(requestDetails)
        useAndClose(connection.getInputStream) { is ⇒
            if (NetUtils.isRedirectCode(connection.getResponseCode))
                Redirect(connection.getHeaderField("Location"), exitPortal = true) // we could consider an option for intra-portlet redirection
            else
                Content(Right(NetUtils.inputStreamToByteArray(is)), Option(connection.getHeaderField("Content-Type")), None)
        }
    }

    private def connectURL(requestDetails: RequestDetails): HttpURLConnection = {

        // POST when we get ClientDataRequest for:
        //
        // - actions requests
        // - resources requests: Ajax requests, form posts, and uploads
        //
        // GET otherwise for:
        //
        // - render requests
        // - resources: typically image, CSS, JavaScript, etc.

        val newURL = recombineQuery(requestDetails.url, requestDetails.params)

        val connection = new URL(newURL).openConnection.asInstanceOf[HttpURLConnection]

        connection.setInstanceFollowRedirects(false)
        connection.setDoInput(true)

        requestDetails.content foreach { content ⇒
            connection.setDoOutput(true)
            connection.setRequestMethod("POST")
            content.contentType foreach (connection.setRequestProperty("Content-Type", _))
        }

        propagateRequestHeaders(requestDetails.headers, connection)
        setRequestRemoteSessionIdAndHeaders(requestDetails.session, connection, newURL)

        connection.connect()
        try {
            // Write content
            // NOTE: At this time we don't support application/x-www-form-urlencoded. When that type of encoding is
            // taking place, the portal doesn't provide a body and instead makes the content available via parameters.
            // So we would need to re-encode the POST. As of 2012-05-10, the XForms engine instead uses the
            // multipart/form-data encoding on the main form to help us here.
            requestDetails.content foreach { content ⇒
                content.body match {
                    case Left(string) ⇒ connection.getOutputStream.write(string.getBytes("utf-8"))
                    case Right(bytes) ⇒ connection.getOutputStream.write(bytes)
                }
            }

            CookieManager.processResponseSetCookieHeaders(requestDetails.session, connection, newURL)

            connection
        } catch {
            case NonFatal(t) ⇒
                val is = connection.getInputStream
                if (is ne null)
                    runQuietly(is.close())

                throw t
        }
    }

    // Read a response and rewrite URLs within it
    private def readRewriteToPortlet(response: MimeResponse, is: InputStream, mustRewrite: Boolean, escape: Boolean): Unit  =
        if (mustRewrite) {
            // Read content
            val content = NetUtils.readStreamAsString(new InputStreamReader(is, "utf-8"))
            // Rewrite and send
            WSRP2Utils.write(response, content, BufferedPortlet.shortIdNamespace(response.getNamespace, getPortletContext), escape)
        } else {
            // Simply forward content
            NetUtils.copyStream(is, response.getPortletOutputStream)
        }

    private val RemoteSessionIdKey = "org.orbeon.oxf.xforms.portlet.remote-session-id"

    // Propagate useful headers from Form Runner to the client
    private def propagateResponseHeaders(connection: HttpURLConnection, response: MimeResponse): Unit =
        Seq("Content-Type", "Last-Modified", "Cache-Control") map
            (name ⇒ (name, connection.getHeaderField(name))) foreach {
                case ("Content-Type", value: String) ⇒ response.setContentType(value)
                case ("Content-Type", null)          ⇒ getPortletContext.log("WARNING: Received null Content-Type for URL: " + connection.getURL.toString)
                case (name, value: String)           ⇒ response.setProperty(name, value)
            }

    private def propagateRequestHeaders(headers: Seq[(String, String)], connection: HttpURLConnection): Unit =
        for ((name, value) ← headers)
            connection.addRequestProperty(name, value)

    private def setRequestRemoteSessionIdAndHeaders(session: PortletSession, connection: HttpURLConnection, url: String): Unit = {
        // Tell Orbeon Forms explicitly that we are in fact in a portlet environment. This causes the server to use
        // WSRP URL rewriting for the resulting HTML and CSS.
        connection.addRequestProperty("Orbeon-Client", "portlet")
        // Set Cookie header
        CookieManager.processRequestCookieHeaders(session, connection, url)
    }

    // Simple cookie manager using HttpClient classes
    // See https://github.com/orbeon/orbeon-forms/issues/1413
    // See also https://github.com/orbeon/orbeon-forms/issues/1412
    // It doesn't look like we can use the built-in Java classes, which only seem to allow for a "system-wide" cookie
    // manager, and has non-serializable classes.
    private object CookieManager {

        def processRequestCookieHeaders(session: PortletSession, connection: HttpURLConnection, url: String): Unit = {

            val cookieSpec   = new BrowserCompatSpec // because not thread-safe
            val cookieOrigin = getCookieOrigin(url)
            val cookieStore  = getOrCreateCookieStore(session)

            cookieStore.clearExpired(new ju.Date)

            val relevantCookies =
                for {
                    cookie ← cookieStore.getCookies.asScala.toList
                    if cookieSpec.`match`(cookie, cookieOrigin)
                } yield
                    cookie

            // NOTE: BrowserCompatSpec always only return a single Cookie header
            if (relevantCookies.nonEmpty)
                for (header ← cookieSpec.formatCookies(relevantCookies.asJava).asScala)
                    connection.setRequestProperty(header.getName, header.getValue)
        }

        def processResponseSetCookieHeaders(session: PortletSession, connection: HttpURLConnection, url: String): Unit = {

            val cookieSpec   = new BrowserCompatSpec // because not thread-safe
            val cookieOrigin = getCookieOrigin(url)
            val cookieStore  = getOrCreateCookieStore(session)

            for {
                (name, values) ← connection.getHeaderFields.asScala.toList
                if (name ne null) && name.toLowerCase == "set-cookie" // Yes, name can be null! Crazy.
                value          ← values.asScala
                cookie         ← cookieSpec.parse(new BasicHeader(name, value), cookieOrigin).asScala
            } locally {
                cookieStore.addCookie(cookie)
            }
        }

        def getCookieOrigin(url: String) = {
            val uri = new URI(url)
            def defaultPort   = if (uri.getScheme == "https") 443 else 80
            def effectivePort = if (uri.getPort < 0) defaultPort else uri.getPort
            new CookieOrigin(uri.getHost, effectivePort, uri.getPath, uri.getScheme == "https")
        }

        def getOrCreateCookieStore(session: PortletSession) =
            Option(session.getAttribute(RemoteSessionIdKey).asInstanceOf[CookieStore]) getOrElse {
                val newCookieStore = new BasicCookieStore
                session.setAttribute(RemoteSessionIdKey, newCookieStore)
                newCookieStore
            }
    }

    private def buildFormRunnerPath(app: String, form: String, action: String, documentId: Option[String], query: Option[String]) =
        NetUtils.appendQueryString("/fr/" + app + "/" + form + "/" + action + (documentId map ("/" +) getOrElse ""), query getOrElse "")

    private def buildFormRunnerHomePath(query: Option[String]) =
        NetUtils.appendQueryString("/fr/", query getOrElse "")

    private def buildFormRunnerURL(baseURL: String, path: String, embeddable: Boolean) =
        NetUtils.appendQueryString(dropTrailingSlash(baseURL) + path, if(embeddable) "orbeon-embeddable=true" else "")
}