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
import java.net.{HttpURLConnection, URL}
import org.orbeon.oxf.util.StringBuilderWriter
import java.io._

/**
 * Orbeon Forms Form Runner proxy portlet.
 *
 * This portlet allows access to a remote Form Runner instance.
 */
class OrbeonProxyPortlet extends GenericPortlet {

    private object PreferenceName extends Enumeration {
        val FormRunnerURL = Value("form-runner-url")
        val AppName = Value("app-name")
        val FormName = Value("form-name")
        val Action = Value("action")
        val ReadOnly = Value("read-only")
    }

//    private val actions = Set("new", "summary")

    private val preferences = Map(
        PreferenceName.FormRunnerURL → "Form Runner URL",
        PreferenceName.AppName → "Form Runner app name",
        PreferenceName.FormName → "Form Runner form name",
        PreferenceName.Action → "Form Runner action",
        PreferenceName.ReadOnly → "Read-Only access"
    )

    // Return the value of the preference if set, otherwise the value of the initialization parameter
    // NOTE: We should be able to use portlet.xml portlet-preferences/preference, but somehow this doesn't work properly
    private def getPreference(request: PortletRequest, pref: PreferenceName.Value) =
        request.getPreferences.getValue(pref.toString, getPortletConfig.getInitParameter(pref.toString))

    private val FormRunnerPath = """/fr/([^/]+)/([^/]+)/(new|summary)""".r
    private val FormRunnerDocumentPath = """/fr/([^/]+)/([^/]+)/(new|edit|view)/([^/]+)""".r

    override def doView(request: RenderRequest, response: RenderResponse) = {

        def filterAction(request: RenderRequest, action: String) =
            if (getPreference(request, PreferenceName.ReadOnly) == "true" && action == "edit") "view" else action 

        val formRunnerURL = getPreference(request, PreferenceName.FormRunnerURL)

        val (appName, formName, action, documentId) = request.getParameter("orbeon.path") match {
            // Incoming path is Form Runner path without document id
            case FormRunnerPath(appName, formName, action) ⇒ (appName, formName, filterAction(request, action), None)
            // Incoming path is Form Runner path with document id
            case FormRunnerDocumentPath(appName, formName, action, documentId) ⇒ (appName, formName, filterAction(request, action), Some(documentId))
            // No incoming path, use preferences
            case null ⇒ (getPreference(request, PreferenceName.AppName), getPreference(request, PreferenceName.FormName), getPreference(request, PreferenceName.Action), None)
            // Unsupported path
            case otherPath ⇒ throw new PortletException("Unsupported path: " + otherPath)
        }

        val url = new URL(Util.buildFormRunnerURL(formRunnerURL, appName, formName, action, documentId))

        val connection = url.openConnection.asInstanceOf[HttpURLConnection]

        connection.setDoInput(true)
        connection.setRequestMethod("GET")
        setOutgoingRemoteSessionIdAndHeaders(request, connection)

        connection.connect()
        try {
            propagateHeaders(response, connection)
            handleRemoteSessionId(request, connection)
            readRewrite(response, connection, true, false)
        } finally {
            val is = connection.getInputStream
            if (is ne null) is.close()
        }
    }

    private def doViewAction(request: ActionRequest, response: ActionResponse) = ()

    // Very simple preferences editor
    override def doEdit(request: RenderRequest, response: RenderResponse) = {

        response setTitle "Orbeon Forms Preferences"
        response.getWriter write
            <div>
                <style>
                    .orbeon-pref-form label {{display: block; font-weight: bold}}
                    .orbeon-pref-form input {{display: block; width: 20em }}
                </style>
                <form action={response.createActionURL.toString} method="post" class="orbeon-pref-form">
                    {
                        for ((pref, label) ← preferences) yield
                            <label>{label}: <input name={pref.toString} value={getPreference(request, pref)}/></label>
                    }
                    <hr/>
                    <p>
                        <button name="save" value="save">Save</button>
                        <button name="cancel" value="cancel">Cancel</button>
                    </p>
                </form>
            </div>.toString
    }

    // Handle preferences editor save/cancel
    private def doEditAction(request: ActionRequest, response: ActionResponse) = {

        request.getParameter("save") match {
            case "save" ⇒
                def setPreference(name: PreferenceName.Value, value: String) = request.getPreferences.setValue(name.toString, value)

                for ((pref, label) ← preferences)
                    setPreference(pref, request.getParameter(pref.toString))

                request.getPreferences.store()
            case _ ⇒
        }

        // Go back to view mode
        response.setPortletMode(PortletMode.VIEW)
    }

    override def serveResource(request: ResourceRequest, response: ResourceResponse) = {

        val resourceId = request.getResourceID
        val url = new URL(Util.buildResourceURL(getPreference(request, PreferenceName.FormRunnerURL), resourceId))

        request.getMethod match {
            // GET of a resource, typically image, CSS, JavaScript, etc.
            case "GET" ⇒
                val connection = url.openConnection.asInstanceOf[HttpURLConnection]

                connection.setDoInput(true)
                connection.setRequestMethod("GET")
                setOutgoingRemoteSessionIdAndHeaders(request, connection)

                connection.connect()
                try {
                    propagateHeaders(response, connection)
                    handleRemoteSessionId(request, connection)

                    readRewrite(response, connection, Net.getContentTypeMediaType(connection.getContentType) == "text/css", false)
                } finally {
                    val is = connection.getInputStream
                    if (is ne null) is.close()
                }
            // POST of a resource, used for Ajax requests, form posts, and uploads
            case "POST" ⇒

                val connection = url.openConnection.asInstanceOf[HttpURLConnection]

                connection.setDoInput(true)
                connection.setDoOutput(true)
                connection.setRequestMethod("POST")
                connection.setRequestProperty("Content-Type", request.getContentType)
                setOutgoingRemoteSessionIdAndHeaders(request, connection)
                
                connection.connect()
                try {
                    // Write content

                    if (Net.getContentTypeMediaType(request.getContentType) == "application/xml") {
                        // Strip namespace ids from content of Ajax request
                        val content = Net.readStreamAsString(new InputStreamReader(request.getPortletInputStream, "utf-8"))
                        connection.getOutputStream.write(content.replaceAllLiterally(response.getNamespace, "").getBytes("utf-8"))
                    } else {
                        // Just copy the stream
                        Net.copyStream(request.getPortletInputStream, connection.getOutputStream)
                    }

                    propagateHeaders(response, connection)
                    handleRemoteSessionId(request, connection)

                    readRewrite(response, connection, Net.getContentTypeMediaType(connection.getContentType) == "application/xml", true)
                } finally {
                    val is = connection.getInputStream
                    if (is ne null) is.close()
                }
        }
    }

    override def processAction(request: ActionRequest, response: ActionResponse) =
        request.getPortletMode match {
            case PortletMode.VIEW ⇒ doViewAction(request, response)
            case PortletMode.EDIT ⇒ doEditAction(request, response)
            case _ ⇒ // NOP
        }

    // Read a response and rewrite URLs within it
    private def readRewrite(response: MimeResponse, connection: HttpURLConnection, mustRewrite: Boolean, escape: Boolean): Unit  =
        if (mustRewrite) {
            // Read content
            val content = Net.readStreamAsString(new InputStreamReader(connection.getInputStream, "utf-8"))
            // Rewrite and send
            WSRP2Utils.write(response, content, OrbeonPortlet2Delegate.shortIdNamespace(response, getPortletContext), escape)

        } else {
            // Simply forward content
            Net.copyStream(connection.getInputStream, response.getPortletOutputStream)
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
    private def setOutgoingRemoteSessionIdAndHeaders(request: PortletRequest, connection: HttpURLConnection): Unit = {
        // Tell Orbeon Forms explicitly that we are in fact in a portlet environment. This causes the server to use
        // WSRP URL rewriting for the resulting HTML and CSS.
        connection.addRequestProperty("Orbeon-Container", "portlet")
        // Set session cookie
        request.getPortletSession(true).getAttribute(REMOTE_SESSION_ID_KEY) match {
            case remoteSessionCookie: String ⇒ connection.setRequestProperty("Cookie", remoteSessionCookie)
            case _ ⇒
        }
    }

    // If Form Runner sets a remote session id, remember it
    private def handleRemoteSessionId(request: PortletRequest, connection: HttpURLConnection): Unit =
        // Set session cookie
        connection.getHeaderField("Set-Cookie") match {
            case setCookieHeader: String if setCookieHeader contains "JSESSIONID" ⇒
                setCookieHeader split ';' find (_ contains "JSESSIONID") match {
                    case Some(remoteSessionCookie) ⇒
                        request.getPortletSession(true).setAttribute(REMOTE_SESSION_ID_KEY, remoteSessionCookie.trim)
                    case _ ⇒
                }
            case _ ⇒
        }

    private object Util {

        def buildFormRunnerURL(baseURL: String, app: String, form: String, action: String, documentId: Option[String]) =
            removeTrailingSlash(baseURL) + "/fr/" + app + "/" + form + "/" + action + (if (documentId.isDefined) "/" + documentId.get else "") + "?orbeon-embeddable"

        def buildResourceURL(baseURL: String, resourceId: String) =
            removeTrailingSlash(baseURL) + resourceId

        def removeTrailingSlash(path: String) = path match {
            case path if path.last == '/' ⇒ path.init
            case _ ⇒ path
        }
    }

    private object Net {

        private val COPY_BUFFER_SIZE = 8192

        def copyStream(is: InputStream, os: OutputStream) {
            var count = -1
            val buffer: Array[Byte] = new Array[Byte](COPY_BUFFER_SIZE)
            while ({count = is.read(buffer); count} > 0)
                os.write(buffer, 0, count)
        }

        def copyStream(reader: Reader, writer: Writer) {
            var count = -1
            val buffer: Array[Char] = new Array[Char](COPY_BUFFER_SIZE)
            while ({count = reader.read(buffer); count} > 0)
                writer.write(buffer, 0, count)
        }

        def readStreamAsString(reader: Reader) = {
            val writer = new StringBuilderWriter
            copyStream(reader, writer)
            writer.toString
        }

        def getContentTypeMediaType(contentType: String) = contentType match {
            case null ⇒ null
            case value: String if value.trim.isEmpty ⇒ null
            case _ ⇒ (contentType split ';' head).trim
        }
    }
}