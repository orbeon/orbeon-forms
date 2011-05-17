/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.pipeline.api.ExternalContext.{Response, Request}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.JavaConversions._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.resources.handler.HTTPURLConnection
import org.orbeon.oxf.processor.generator.RequestGenerator

/**
 * The persistence proxy processor:
 *
 * - proxies GET, PUT, DELETE and POST to the appropriate persistence implementation
 * - sets persistence implementation headers
 */
class FormRunnerPersistenceProxy extends ProcessorImpl {

    private val FormPath = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
    private val DataPath = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/([^/]+)/([^/]+))""".r
    private val DataCollectionPath = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
    private val SearchPath = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r

    // Start the processor
    override def start(pipelineContext: PipelineContext) {
        val ec = NetUtils.getExternalContext
        proxyRequest(ec.getRequest, ec.getResponse)
    }

    // Proxy the request to the appropriate persistence implementation
    def proxyRequest(request: Request, response: Response) {
        val incomingPath = request.getRequestPath
        incomingPath match {
            case FormPath(path, app, form, _) => proxyRequest(request, response, app, form, "form", path)
            case DataPath(path, app, form, _, _) => proxyRequest(request, response, app, form, "data", path)
            case DataCollectionPath(path, app, form) => proxyRequest(request, response, app, form, "data", path)
            case SearchPath(path, app, form) => proxyRequest(request, response, app, form, "data", path)
            case _ => throw new OXFException("Unsupported path: " + incomingPath)
        }
    }

    // Proxy the request depending on app/form name and whether we are accessing form or data
    private def proxyRequest(request: Request, response: Response, app: String, form: String, formOrData: String, path: String) {

        // Get persistence implementation target URL and configuration headers
        val (persistenceBaseURL, headers) = FormRunner.getPersistenceURLHeaders(app, form, formOrData)

        // Create the absolute outgoing URL
        val ougoingURL = {
            val persistenceBaseAbsoluteURL = NetUtils.getExternalContext.rewriteServiceURL(persistenceBaseURL, ExternalContext.Response.REWRITE_MODE_ABSOLUTE)
            URLFactory.createURL(dropTrailingSlash(persistenceBaseAbsoluteURL) + path)
        }

        def setPersistenceHeaders(connection: HTTPURLConnection) {
            for ((name, value) <- headers)
                connection.setRequestProperty(capitalizeHeader(name), value)
        }

        def proxyHeaders(headers: => Traversable[(String, Seq[String])], set: (String, String) => Unit, out: Boolean): Unit =
            for {
                (name, values) <- headers
                if !Set("transfer-encoding", "connection", "host")(name.toLowerCase)
                if !out || name.toLowerCase != "content-length"
                if values != null && values.nonEmpty
            } yield
                set(capitalizeHeader(name), values mkString ",")

        def proxyOutgoingHeaders(connection: HTTPURLConnection) =
            proxyHeaders(request.getHeaderValuesMap map {case (name, values) => (name, values.toSeq)}, connection.setRequestProperty _, out = true)

        def proxyIncomingHeaders(connection: HTTPURLConnection) =
            proxyHeaders(connection.getHeaderFields map {case (name, values) => (name, values.toSeq)}, response.setHeader _, out = false)

        // Proxy
        if (Set("GET", "DELETE", "PUT", "POST")(request.getMethod)) {

            // Prepare connection
            val doOutput = Set("PUT", "POST")(request.getMethod)
            val connection = ougoingURL.openConnection.asInstanceOf[HTTPURLConnection]

            connection.setDoInput(true)
            connection.setDoOutput(doOutput)
            connection.setRequestMethod(request.getMethod)

            setPersistenceHeaders(connection)
            proxyOutgoingHeaders(connection)

            // Write body if needed
            // NOTE: HTTPURLConnection requires setting the body before calling connect()
            if (doOutput) {
                // Ask the request generator first, as the body might have been read already
                // Q: Could this be handled automatically in ExternalContext?
                val is = RequestGenerator.getRequestBody(request) match {
                    case bodyURL: String => NetUtils.uriToInputStream(bodyURL)
                    case _ => request.getInputStream
                }

                copyStream(is, connection.getOutputStream)
            }

            connection.connect()

            // Handle response
            proxyIncomingHeaders(connection)
            copyStream(connection.getInputStream, response.getOutputStream)
        } else
            throw new OXFException("Unsupported method: " + request.getMethod)
    }
}