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
import java.io._
import org.orbeon.oxf.util.ScalaUtils._
import java.net.{HttpURLConnection, URL}
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.util.{LoggerFactory, NetUtils}
import org.orbeon.oxf.externalcontext.WSRPURLRewriter

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
    case class AsyncContext(content: Option[Content], session: PortletSession, url: URL, namespace: String)

    private val FormRunnerPath = """/fr/([^/]+)/([^/]+)/(new|summary)(\?(.*))?""".r
    private val FormRunnerDocumentPath = """/fr/([^/]+)/([^/]+)/(new|edit|view)/([^/?]+)?(\?(.*))?""".r

    // Portlet render
    override def doView(request: RenderRequest, response: RenderResponse): Unit =
        withRootException("view render", new PortletException(_)) {
            def renderFunction =
                if (isAsyncPortletLoad)
                    startAsyncRender(request, createContext(request, response.getNamespace), callService)
                else
                    callService(createContext(request, response.getNamespace))

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
            bufferedProcessAction(request, response, callService(createContext(request, response.getNamespace)))
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
        val url = new URL(buildFormRunnerURL(getPreference(request, FormRunnerURL), resourceId))

        val namespace = response.getNamespace
        val connection = connectURL(AsyncContext(contentFromRequest(request, namespace), request.getPortletSession(true), url, namespace))

        useAndClose(connection.getInputStream) { is ⇒
            propagateHeaders(response, connection)
            val mediaType = NetUtils.getContentTypeMediaType(connection.getContentType)
            val mustRewrite = XMLUtils.isTextOrJSONContentType(mediaType) || XMLUtils.isXMLMediatype(mediaType)
            readRewriteToPortlet(response, is, mustRewrite, escape = request.getMethod == "POST")
        }
    }

    private def createContext(request: PortletRequest, namespace: String): AsyncContext = {
        // Determine URL based on preferences and request
        val url = {
            val pathParameter = request.getParameter(WSRPURLRewriter.PathParameterName)

            if (pathParameter == "/xforms-server-submit")
                // XForms server submit
                new URL(buildFormRunnerURL(getPreference(request, FormRunnerURL), pathParameter))
            else {
                // Form Runner path
                def filterAction(action: String) =
                    if (getPreference(request, ReadOnly) == "true" && action == "edit") "view" else action

                val (appName, formName, action, documentId, query) = pathParameter match {
                    // Incoming path is Form Runner path without document id
                    case FormRunnerPath(appName, formName, action, _, query) ⇒ (appName, formName, filterAction(action), None, Option(query))
                    // Incoming path is Form Runner path with document id
                    case FormRunnerDocumentPath(appName, formName, action, documentId, _, query) ⇒ (appName, formName, filterAction(action), Some(documentId), Option(query))
                    // No incoming path, use preferences
                    case null ⇒ (getPreference(request, AppName), getPreference(request, FormName), getPreference(request, Action), None, None)
                    // Unsupported path
                    case otherPath ⇒ throw new PortletException("Unsupported path: " + otherPath)
                }

                new URL(buildFormRunnerURL(getPreference(request, FormRunnerURL), appName, formName, action, documentId, query))
            }
        }

        AsyncContext(contentFromRequest(request, namespace), request.getPortletSession(true), url, namespace)
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
    private def callService(context: AsyncContext): ContentOrRedirect = {
        val connection = connectURL(context)
        useAndClose(connection.getInputStream) { is ⇒
            //propagateHeaders(response, connection)
            Content(Right(NetUtils.inputStreamToByteArray(is)), Option(connection.getHeaderField("Content-Type")), None)
        }
    }

    private def connectURL(context: AsyncContext): HttpURLConnection = {

        // POST when we get ClientDataRequest for:
        //
        // - actions requests
        // - resources requests: Ajax requests, form posts, and uploads
        //
        // GET otherwise for:
        //
        // - render requests
        // - resources: typically image, CSS, JavaScript, etc.

        val connection = context.url.openConnection.asInstanceOf[HttpURLConnection]

        connection.setDoInput(true)

        context.content foreach { content ⇒
            connection.setDoOutput(true)
            connection.setRequestMethod("POST")
            content.contentType foreach (connection.setRequestProperty("Content-Type", _))
        }

        setOutgoingRemoteSessionIdAndHeaders(context.session, connection)

        connection.connect()
        try {
            // Write content
            // NOTE: At this time we don't support application/x-www-form-urlencoded. When that type of encoding is
            // taking place, the portal doesn't provide a body and instead makes the content available via parameters.
            // So we would need to re-encode the POST. As of 2012-05-10, the XForms engine instead uses the
            // multipart/form-data encoding on the main form to help us here.
            context.content foreach { content ⇒
                content.body match {
                    case Left(string) ⇒ connection.getOutputStream.write(string.getBytes("utf-8"))
                    case Right(bytes) ⇒ connection.getOutputStream.write(bytes)
                }
            }

            handleRemoteSessionId(context.session, connection)

            connection
        } catch{
            case t: Throwable ⇒
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

    private val REMOTE_SESSION_ID_KEY = "org.orbeon.oxf.xforms.portlet.remote-session-id"

    // Propagate useful headers from Form Runner server to client
    private def propagateHeaders(response: MimeResponse, connection: HttpURLConnection): Unit =
        Seq("Content-Type", "Last-Modified", "Cache-Control") map
            (name ⇒ (name, connection.getHeaderField(name))) foreach {
                case ("Content-Type", value: String) ⇒ response.setContentType(value)
                case ("Content-Type", null) ⇒ getPortletContext.log("WARNING: Received null Content-Type for URL: " + connection.getURL.toString)
                case (name, value: String) ⇒ response.setProperty(name, value)
            }

    // If we know about the remote session id, set it on the connection to Form Runner
    private def setOutgoingRemoteSessionIdAndHeaders(session: PortletSession, connection: HttpURLConnection): Unit = {
        // Tell Orbeon Forms explicitly that we are in fact in a portlet environment. This causes the server to use
        // WSRP URL rewriting for the resulting HTML and CSS.
        connection.addRequestProperty("Orbeon-Client", "portlet")
        // Set session cookie
        session.getAttribute(REMOTE_SESSION_ID_KEY) match {
            case remoteSessionCookie: String ⇒ connection.setRequestProperty("Cookie", remoteSessionCookie)
            case _ ⇒
        }
    }

    // If Form Runner sets a remote session id, remember it
    private def handleRemoteSessionId(session: PortletSession, connection: HttpURLConnection): Unit =
        // Set session cookie
        connection.getHeaderField("Set-Cookie") match {
            case setCookieHeader: String if setCookieHeader contains "JSESSIONID" ⇒
                setCookieHeader split ';' find (_ contains "JSESSIONID") match {
                    case Some(remoteSessionCookie) ⇒
                        session.setAttribute(REMOTE_SESSION_ID_KEY, remoteSessionCookie.trim)
                    case _ ⇒
                }
            case _ ⇒
        }

    private def buildFormRunnerURL(baseURL: String, app: String, form: String, action: String, documentId: Option[String], query: Option[String]) =
        dropTrailingSlash(baseURL) + "/fr/" + app + "/" + form + "/" + action +
            (documentId map ("/" + _) getOrElse "") +
            (query map ("?" + _ + "&") getOrElse "?") + "orbeon-embeddable=true"

    private def buildFormRunnerURL(baseURL: String, path: String) =
        dropTrailingSlash(baseURL) + path
}