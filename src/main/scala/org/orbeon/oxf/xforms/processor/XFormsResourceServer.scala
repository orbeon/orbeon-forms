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
import java.net.{URI, URLEncoder}

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.pipeline.api.ExternalContext.Session.APPLICATION_SCOPE
import org.orbeon.oxf.pipeline.api.ExternalContext._
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.processor.{ProcessorImpl, ResourceServer}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.{Caches, Loggers, XFormsProperties}

import scala.util.control.NonFatal

/**
 * Serve XForms engine JavaScript and CSS resources by combining them.
 */
class XFormsResourceServer extends ProcessorImpl with Logging {

    import org.orbeon.oxf.xforms.processor.XFormsResourceServer._

    override def start(pipelineContext: PipelineContext): Unit = {

        implicit val externalContext = NetUtils.getExternalContext
        val requestPath = externalContext.getRequest.getRequestPath

        if (requestPath.startsWith(DynamicResourcesPath))
            serveDynamicResource(requestPath)
        else
            serveCSSOrJavaScript(requestPath)
    }

    private def serveDynamicResource(requestPath: String)(implicit externalContext: ExternalContext): Unit = {
        val session = externalContext.getRequest.getSession(false)
        if (session ne null) {
            val filenameFromRequest = filename(requestPath)
            val response = externalContext.getResponse

            // Store mapping into session
            val lookupKey = DynamicResourcesSessionKey + filenameFromRequest
            // Use same session scope as proxyURI()
            val resource = session.getAttributesMap(APPLICATION_SCOPE).get(lookupKey).asInstanceOf[DynamicResource]
            if (resource ne null) {
                // Found URL, stream it out

                // Set caching headers

                // NOTE: Algorithm is that XFOutputControl currently passes either -1 or the last modified of the
                // resource if "fast" to obtain last modified ("oxf:" or "file:"). Would be nice to do better: pass
                // whether resource is cacheable or not; here, when dereferencing the resource, we get the last
                // modified (Last-Modified header from HTTP even) and store it. Then we can handle conditional get.
                // This is some work though. Might have to proxy conditional GET as well. So for now we don't
                // handle conditional GET and produce a non-now last modified only in a few cases.

                response.setResourceCaching(resource.lastModified, 0)

                if (resource.size >= 0)
                    response.setContentLength(resource.size.asInstanceOf[Int]) // Q: Why does this API (and Servlet counterpart) take an int?

                // TODO: for Safari, try forcing application/octet-stream
                // NOTE: IE 6/7 don't display a download box when detecting an HTML document (known IE bug)
                response.setContentType(resource.contentType getOrElse "application/octet-stream")

                // File name visible by the user
                val contentFilename = resource.filename getOrElse filenameFromRequest

                // Handle as attachment
                // TODO: should try to provide extension based on mediatype if file name is not provided?
                // TODO: filename should be encoded somehow, as 1) spaces don't work and 2) non-ISO-8859-1 won't work
                response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(contentFilename, "UTF-8"))

                // Copy stream out
                try {
                    val cxr =
                        Connection(
                            httpMethodUpper = "GET",
                            url             = resource.uri,
                            credentials     = None,
                            content         = None,
                            headers         = resource.headers,
                            loadState       = true,
                            logBody         = false
                        ).connect(
                            saveState = true
                        )

                    useAndClose(cxr.content.inputStream) { is ⇒
                        useAndClose(response.getOutputStream) { os ⇒
                            copyStream(is, os)
                        }
                    }
                } catch {
                    case NonFatal(t) ⇒ warn("exception copying stream", Seq("throwable" → OrbeonFormatter.format(t)))
                }
            } else {
                // Not found
                response.setStatus(SC_NOT_FOUND)
            }
        }
    }

    private def serveCSSOrJavaScript(requestPath: String)(implicit externalContext: ExternalContext): Unit = {

        val filenameFromRequest = filename(requestPath)

        val isCSS = filenameFromRequest endsWith ".css"
        val isJS  = filenameFromRequest endsWith ".js"

        val response = externalContext.getResponse

        // Eliminate funny requests
        if (! isCSS && ! isJS && ! filenameFromRequest.startsWith("orbeon-")) {
            response.setStatus(SC_NOT_FOUND)
            return
        }

        val resources = {
            // New hash-based mechanism
            val resourcesHash = filenameFromRequest.substring("orbeon-".length, filenameFromRequest.lastIndexOf("."))
            val cacheElement = Caches.resourcesCache.get(resourcesHash)
            if (cacheElement ne null) {
                // Mapping found
                val resourcesStrings = cacheElement.getObjectValue.asInstanceOf[Array[String]].toList
                resourcesStrings map (r ⇒ new XFormsFeatures.ResourceConfig(r, r))
            } else {
                // Not found, either because the hash is invalid, or because the cache lost the mapping
                response.setStatus(SC_NOT_FOUND)
                return
            }
        }

        val isMinimal = false

        // Get last modified date
        val combinedLastModified = XFormsResourceRewriter.computeCombinedLastModified(resources, isMinimal)

        // Set Last-Modified, required for caching and conditional get
        if (URLRewriterUtils.isResourcesVersioned)
            // Use expiration far in the future
            response.setResourceCaching(combinedLastModified, System.currentTimeMillis + ResourceServer.ONE_YEAR_IN_MILLISECONDS)
        else
            // Use standard expiration policy
            response.setResourceCaching(combinedLastModified, 0)

        // Check If-Modified-Since and don't return content if condition is met
        if (! response.checkIfModifiedSince(combinedLastModified)) {
            response.setStatus(SC_NOT_MODIFIED)
            return
        }

        response.setContentType(if (isCSS) "text/css; charset=UTF-8" else "application/x-javascript")

        // Namespace to use, must be None if empty
        def namespaceOpt = {
            def nsFromParameters = Option(externalContext.getRequest.getParameterMap.get(NamespaceParameter)) map (_(0).asInstanceOf[String])
            def nsFromContainer  = Some(response.getNamespacePrefix)

            nsFromParameters orElse nsFromContainer filter (_.nonEmpty)
        }

        def debugParameters = Seq("request path" → requestPath)

        if (XFormsProperties.isCacheCombinedResources) {
            // Caching requested
            val resourceFile = XFormsResourceRewriter.cacheResources(resources, requestPath, namespaceOpt, combinedLastModified, isCSS, isMinimal)
            if (resourceFile ne null) {
                // Caching could take place, send out cached result
                debug("serving from cache ", debugParameters)
                useAndClose(response.getOutputStream) { os ⇒
                    copyStream(new FileInputStream(resourceFile), os)
                }
            } else {
                // Was unable to cache, just serve
                debug("caching requested but not possible, serving directly", debugParameters)
                XFormsResourceRewriter.generateAndClose(resources, namespaceOpt, response.getOutputStream, isCSS, isMinimal)(indentedLogger)
            }
        } else {
            // Should not cache, just serve
            debug("caching not requested, serving directly", debugParameters)
            XFormsResourceRewriter.generateAndClose(resources, namespaceOpt, response.getOutputStream, isCSS, isMinimal)(indentedLogger)
        }
    }
}

object XFormsResourceServer {

    val DynamicResourcesSessionKey = "orbeon.resources.dynamic."
    val DynamicResourcesPath       = "/xforms-server/dynamic/"
    val NamespaceParameter         = "ns"

    implicit def indentedLogger: IndentedLogger = Loggers.getIndentedLogger("resources")

    // Transform an URI accessible from the server into a URI accessible from the client.
    // The mapping expires with the session.
    def proxyURI(
        uri              : String,
        filename         : Option[String],
        contentType      : Option[String],
        lastModified     : Long,
        customHeaders    : Map[String, List[String]],
        headersToForward : Set[String])(implicit
        logger           : IndentedLogger
    ): String = {

        // Create a digest, so that for a given URI we always get the same key
        val digest = SecureUtils.digestString(uri, "hex")

        // Get session
        val externalContext = NetUtils.getExternalContext
        val session = externalContext.getRequest.getSession(true)

        if (session ne null) {

            // The resource URI may already be absolute, or may be relative to the server base. Make sure we work with an absolute URI.
            val serviceURI = new URI(URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE))

            // Store mapping into session
            val outgoingHeaders =
                Connection.buildConnectionHeadersLowerIfNeeded(
                    scheme           = serviceURI.getScheme,
                    hasCredentials   = false,
                    customHeaders    = customHeaders,
                    headersToForward = headersToForward,
                    cookiesToForward = Connection.cookiesToForwardFromProperty)(
                    logger           = logger
                )

            val resource = DynamicResource(serviceURI, filename, contentType, -1, lastModified, outgoingHeaders)

            session.getAttributesMap(APPLICATION_SCOPE).put(DynamicResourcesSessionKey + digest, resource)
        }

        // Rewrite new URI to absolute path without the context
        DynamicResourcesPath + digest
    }

    // For Java callers
    def jProxyURI(uri: String, contentType: String) =
        proxyURI(uri, None, Option(contentType), -1, Map(), Set())(null)

    // For unit tests only (called from XSLT)
    def testGetResources(key: String)  =
        Option(Caches.resourcesCache.get(key)) map (_.getObjectValue.asInstanceOf[Array[String]]) orNull

    // Information about the resource, stored into the session
    case class DynamicResource(uri: URI, filename: Option[String], contentType: Option[String], size: Long, lastModified: Long, headers: Map[String, List[String]])

    private def filename(requestPath: String) = requestPath.substring(requestPath.lastIndexOf('/') + 1)
}