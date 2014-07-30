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

import com.liferay.portal.util.PortalUtil
import org.apache.commons.io.IOUtils
import org.orbeon.errorified.Exceptions
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.WSRPURLRewriter
import org.orbeon.oxf.fr.embedding._
import org.orbeon.oxf.http.{HttpClient, ApacheHttpClient, HttpClientSettings}
import org.orbeon.oxf.portlet.liferay.LiferaySupport
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.ScalaUtils.{withRootException ⇒ _, _}
import org.orbeon.oxf.xml.XMLUtils

import scala.collection.breakOut
import scala.util.control.NonFatal

/**
 * Orbeon Forms Form Runner proxy portlet.
 *
 * This portlet allows access to a remote Form Runner instance.
 */
class OrbeonProxyPortlet extends GenericPortlet with ProxyPortletEdit with BufferedPortlet {

    import org.orbeon.oxf.portlet.OrbeonProxyPortlet._

    private case class PortletSettings(
        forwardHeaders: Map[String, String], // lowercase name → original name
        forwardParams : Set[String],
        httpClient    : HttpClient
     )

    // For BufferedPortlet
    def title(request: RenderRequest) = getTitle(request)

    private var settingsOpt: Option[PortletSettings] = None

    override def init(config: PortletConfig) {
        super.init(config)
        settingsOpt = Some(
            PortletSettings(
                forwardHeaders = stringToSet(config.getInitParameter("forward-headers")).map(name ⇒ name.toLowerCase → name)(breakOut),
                forwardParams  = stringToSet(config.getInitParameter("forward-parameters")),
                httpClient     = new ApacheHttpClient(HttpClientSettings(config.getInitParameter))
            )
        )
    }

    override def destroy() = {
        settingsOpt foreach (_.httpClient.shutdown())
        settingsOpt = None
        super.destroy()
    }

    // Try to find getHttpServletRequest only the first time this is accessed
    private lazy val getHttpServletRequest =
        try Some(PortalUtil.getHttpServletRequest _)
        catch { case (_: NoClassDefFoundError) | (_: ClassNotFoundException) ⇒ None }

    private def findServletRequest(request: PortletRequest) =
        getHttpServletRequest flatMap (f ⇒ Option(f(request)))

    private val FormRunnerHome         = """/fr/(\?(.*))?""".r
    private val FormRunnerPath         = """/fr/([^/]+)/([^/]+)/(new|summary)(\?(.*))?""".r
    private val FormRunnerDocumentPath = """/fr/([^/]+)/([^/]+)/(new|edit|view)/([^/?]+)?(\?(.*))?""".r

    // Portlet render
    override def doView(request: RenderRequest, response: RenderResponse): Unit =
        settingsOpt foreach { settings ⇒
            withRootException("view render", new PortletException(_)) {

                implicit val ctx = new PortletEmbeddingContextWithResponse(
                    getPortletContext,
                    request,
                    response,
                    settings.httpClient
                )

                bufferedRender(request, response, APISupport.callService(createRequestDetails(settings, request, response.getNamespace)))
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
                    settings.httpClient
                )
                bufferedProcessAction(request, response, APISupport.callService(createRequestDetails(settings, request, response.getNamespace)))
            }
        }

    // Portlet resource
    override def serveResource(request: ResourceRequest, response: ResourceResponse): Unit =
        settingsOpt foreach { settings ⇒
            withRootException("resource", new PortletException(_)) {
                implicit val ctx = new PortletEmbeddingContextWithResponse(
                    getPortletContext,
                    request,
                    response,
                    settings.httpClient
                )
                val resourceId = request.getResourceID
                val url = APISupport.formRunnerURL(getPreference(request, FormRunnerURL), resourceId, embeddable = false)

                val requestDetails =
                    newRequestDetails(
                        settings,
                        request,
                        contentFromRequest(request, response.getNamespace),
                        url
                    )

                APISupport.proxyResource(requestDetails)
            }
        }

    private def createRequestDetails(settings: PortletSettings, request: PortletRequest, namespace: String): RequestDetails = {
        // Determine URL based on preferences and request
        val path = {

            def pathParameterOpt =
                Option(request.getParameter(WSRPURLRewriter.PathParameterName))

            def defaultPath =
                if (getPreference(request, Page) == "home")
                    APISupport.formRunnerHomePath(None)
                else
                    APISupport.formRunnerPath(getPreference(request, AppName), getPreference(request, FormName), getPreference(request, Page), None, None)

            def filterAction(action: String) =
                if (getPreference(request, ReadOnly) == "true" && action == "edit") "view" else action

            pathParameterOpt getOrElse defaultPath match {
                case path @ "/xforms-server-submit" ⇒
                    path
                // Incoming path is Form Runner path without document id
                case FormRunnerPath(appName, formName, action, _, query) ⇒
                    APISupport.formRunnerPath(appName, formName, filterAction(action), None, Option(query))
                // Incoming path is Form Runner path with document id
                case FormRunnerDocumentPath(appName, formName, action, documentId, _, query) ⇒
                    APISupport.formRunnerPath(appName, formName, filterAction(action), Some(documentId), Option(query))
                // Incoming path is Form Runner Home page
                case FormRunnerHome(_, query) ⇒
                    APISupport.formRunnerHomePath(Option(query))
                // Unsupported path
                case otherPath ⇒
                    throw new PortletException("Unsupported path: " + otherPath)
            }
        }

        newRequestDetails(
            settings,
            request,
            contentFromRequest(request, namespace),
            APISupport.formRunnerURL(getPreference(request, FormRunnerURL), path, embeddable = true)
        )
    }

    private def newRequestDetails(settings: PortletSettings, request: PortletRequest, content: Option[Content], url: String): RequestDetails = {

        def clientHeaders =
            findServletRequest(request).toList flatMap APISupport.requestHeaders

        def paramsToSet =
            for {
                pair @ (name, _) ← collectByErasedType[String](request.getAttribute("javax.servlet.forward.query_string")) map decodeSimpleQuery getOrElse Nil
                if settings.forwardParams(name)
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

        def headersToSet =
            APISupport.headersToForward(clientHeaders, settings.forwardHeaders).toList ++ languageHeader.toList ++ userHeaders

        RequestDetails(
            content,
            url,
            headersToSet,
            paramsToSet
        )
    }

    private def contentFromRequest(request: PortletRequest, namespace: String): Option[Content] =
        request match {
            case clientDataRequest: ClientDataRequest if clientDataRequest.getMethod == "POST" ⇒
                val body =
                    if (XMLUtils.isXMLMediatype(NetUtils.getContentTypeMediaType(clientDataRequest.getContentType)))
                        Left(IOUtils.toString(clientDataRequest.getPortletInputStream, Option(clientDataRequest.getCharacterEncoding) getOrElse "utf-8"))
                    else
                        // Just read without rewriting
                        Right(IOUtils.toByteArray(clientDataRequest.getPortletInputStream))

                Some(Content(body, Option(clientDataRequest.getContentType), None))
            case _ ⇒
                None
        }
}

object OrbeonProxyPortlet {
    def withRootException[T](action: String, newException: Throwable ⇒ Exception)(body: ⇒ T)(implicit ctx: PortletContext): T =
        try body
        catch {
            case NonFatal(t) ⇒
                ctx.log("Exception when running " + action + '\n' + OrbeonFormatter.format(t))
                throw newException(Exceptions.getRootThrowable(t))
        }
}