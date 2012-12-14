/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import java.io._
import java.net.URL
import java.net.URLEncoder
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.processor.ResourceServer
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.Caches
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xforms.XFormsProperties
import ExternalContext.Session.APPLICATION_SCOPE
import ScalaUtils._
import org.orbeon.exception.OrbeonFormatter

/**
 * Serve XForms engine JavaScript and CSS resources by combining them.
 */
class XFormsResourceServer extends ProcessorImpl with Logging {

    import XFormsResourceServer._

    override def start(pipelineContext: PipelineContext) {
        val externalContext = NetUtils.getExternalContext
        val request = externalContext.getRequest
        val response = externalContext.getResponse

        val requestPath = request.getRequestPath
        val filename = requestPath.substring(requestPath.lastIndexOf('/') + 1)

        implicit val indentedLogger = getIndentedLogger

        if (requestPath.startsWith(DynamicResourcesPath)) {
            // Dynamic resource requested
            val session = externalContext.getRequest.getSession(false)
            if (session ne null) {
                // Store mapping into session
                val lookupKey = DynamicResourcesSessionKey + filename
                // Use same session scope as proxyURI()
                val resource = session.getAttributesMap(APPLICATION_SCOPE).get(lookupKey).asInstanceOf[DynamicResource]
                if ((resource ne null) && (resource.url ne null)) {
                    // Found URL, stream it out

                    // Set caching headers

                    // NOTE: Algorithm is that XFOutputControl currently passes either -1 or the last modified of the
                    // resource if "fast" to obtain last modified ("oxf:" or "file:"). Would be nice to do better: pass
                    // whether resource is cacheable or not; here, when dereferencing the resource, we get the last
                    // modified (Last-Modified header from HTTP even) and store it. Then we can handle conditional get.
                    // This is some work though. Might have to proxy conditional GET as well. So for now we don't
                    // handle conditional GET and produce a non-now last modified only in a few cases.

                    response.setResourceCaching(resource.lastModified, 0)

                    if (resource.size > 0)
                        response.setContentLength(resource.size.asInstanceOf[Int]) // Q: Why does this API (and Servlet counterpart) take an int?

                    // TODO: for Safari, try forcing application/octet-stream
                    // NOTE: IE 6/7 don't display a download box when detecting an HTML document (known IE bug)
                    response.setContentType(Option(resource.contentType) getOrElse "application/octet-stream")

                    // File name visible by the user
                    val contentFilename = Option(resource.filename) getOrElse filename

                    // Handle as attachment
                    // TODO: should try to provide extension based on mediatype if file name is not provided?
                    // TODO: filename should be encoded somehow, as 1) spaces don't work and 2) non-ISO-8859-1 won't work
                    response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(contentFilename, "UTF-8"))

                    // Copy stream out
                    try {
                        val connection = resource.url.openConnection

                        // Set outgoing headers
                        for {
                            (name, values) ← resource.headers
                            value          ← values
                        } connection.addRequestProperty(name, value)

                        copyStream(connection.getInputStream, response.getOutputStream)
                    } catch {
                        case e: Exception ⇒ warn("exception copying stream", Seq("throwable" → OrbeonFormatter.format(e)))
                    }
                } else {
                    // Not found
                    response.setStatus(ExternalContext.SC_NOT_FOUND)
                }
            }
        } else {
            // CSS or JavaScript resource requested
            val isCSS = filename.endsWith(".css")
            val isJS = filename.endsWith(".js")

            // Eliminate funny requests
            if (! isCSS && ! isJS) {
                response.setStatus(ExternalContext.SC_NOT_FOUND)
                return
            }
            val isMinimal = false
            val resources =
                if (filename.startsWith("orbeon-")) {
                    // New hash-based mechanism
                    val resourcesHash = filename.substring("orbeon-".length, filename.lastIndexOf("."))
                    val cacheElement = Caches.resourcesCache.get(resourcesHash)
                    if (cacheElement ne null) {
                        // Mapping found
                        val resourcesStrings = cacheElement.getValue.asInstanceOf[Array[String]].toList
                        resourcesStrings map (r ⇒ new XFormsFeatures.ResourceConfig(r, r))
                    } else {
                        // Not found, either because the hash is invalid, or because the cache lost the mapping
                        response.setStatus(ExternalContext.SC_NOT_FOUND)
                        return
                    }
                } else {
                    response.setStatus(ExternalContext.SC_NOT_FOUND)
                    return
                }

            // Get last modified date
            val combinedLastModified = XFormsResourceRewriter.computeCombinedLastModified(resources, isMinimal)

            // Set Last-Modified, required for caching and conditional get
            if (URLRewriterUtils.isResourcesVersioned)
                // Use expiration far in the future
                response.setResourceCaching(combinedLastModified, combinedLastModified + ResourceServer.ONE_YEAR_IN_MILLISECONDS)
            else
                // Use standard expiration policy
                response.setResourceCaching(combinedLastModified, 0)

            // Check If-Modified-Since and don't return content if condition is met
            if (! response.checkIfModifiedSince(combinedLastModified, false)) {
                response.setStatus(ExternalContext.SC_NOT_MODIFIED)
                return
            }

            response.setContentType(if (isCSS) "text/css" else "application/x-javascript")

            def debugParameters = Seq("request path" → requestPath)

            if (XFormsProperties.isCacheCombinedResources) {
                // Caching requested
                val resourceFile = XFormsResourceRewriter.cacheResources(resources, requestPath, combinedLastModified, isCSS, isMinimal)
                if (resourceFile ne null) {
                    // Caching could take place, send out cached result
                    debug("serving from cache ", debugParameters)
                    copyStream(new FileInputStream(resourceFile), response.getOutputStream)
                } else {
                    // Was unable to cache, just serve
                    debug("caching requested but not possible, serving directly", debugParameters)
                    useAndClose(response.getOutputStream) { os ⇒
                        XFormsResourceRewriter.generate(indentedLogger, resources, os, isCSS, isMinimal)
                    }
                }
            } else {
                // Should not cache, just serve
                debug("caching not requested, serving directly", debugParameters)
                useAndClose(response.getOutputStream) { os ⇒
                    XFormsResourceRewriter.generate(indentedLogger, resources, os, isCSS, isMinimal)
                }
            }
        }
    }
}

object XFormsResourceServer {

    val DynamicResourcesSessionKey = "orbeon.resources.dynamic."
    val DynamicResourcesPath       = "/xforms-server/dynamic/"

    def getIndentedLogger = Loggers.getIndentedLogger("resources")

    /**
     * Transform an URI accessible from the server into a URI accessible from the client. The mapping expires with the
     * session.
     *
     * @param uri               server URI to transform
     * @param filename          file name
     * @param contentType       type of the content referred to by the URI, or null if unknown
     * @param lastModified      last modification timestamp
     * @param headers           connection headers
     * @return                  client URI
     */
    def proxyURI(indentedLogger: IndentedLogger, uri: String, filename: String, contentType: String, lastModified: Long, headers: Map[String, Array[String]], headersToForward: String): String = {

        // Create a digest, so that for a given URI we always get the same key
        val digest = SecureUtils.digestString(uri, "hex")

        // Get session
        val externalContext = NetUtils.getExternalContext
        val session = externalContext.getRequest.getSession(true)

        if (session ne null) {
            val url = {
                // The resource URI may already be absolute, or may be relative to the server base. Make sure we work with an absolute URI.
                val absoluteResourceURI: String = URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE)
                URLFactory.createURL(absoluteResourceURI)
            }

            // Store mapping into session
            val outgoingHeaders = Connection.buildConnectionHeaders(url.getProtocol, None, headers, Option(headersToForward))(indentedLogger)
            session.getAttributesMap(APPLICATION_SCOPE).put(DynamicResourcesSessionKey + digest, DynamicResource(url, filename, contentType, -1, lastModified, outgoingHeaders))
        }

        // Rewrite new URI to absolute path without the context
        DynamicResourcesPath + digest
    }

    // For unit tests only (called from XSLT)
    def testGetResources(key: String)  =
        Option(Caches.resourcesCache.get(key)) map (_.getValue.asInstanceOf[Array[String]]) orNull

    case class DynamicResource(url: URL, filename: String, contentType: String, size: Long, lastModified: Long, headers: Map[String, Array[String]])
}