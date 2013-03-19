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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.util._
import ScalaUtils.{decodeSimpleQuery, combineValues, appendStartingSlash}
import collection.JavaConverters._
import collection.mutable
import java.io._
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.servlet.ServletExternalContext
import org.orbeon.oxf.util._
import org.apache.commons.lang3.StringUtils

/**
 * Request used for local requests.
 */
class LocalRequest(
        externalContext: ExternalContext,
        indentedLogger: IndentedLogger,
        contextPath: String,
        pathQuery: String,
        method: String,
        messageBodyOrNull: Array[Byte],
        headers: Map[String, Array[String]]) // primary constructor simulates a POST or a PUT
    extends RequestWrapper(externalContext.getRequest) {

    require(StringUtils.isAllUpperCase(method))

    private val contentType = headers.get("content-type") flatMap (_.lift(0)) getOrElse "application/octet-stream"
    private val messageBody = Option(messageBodyOrNull) getOrElse Array[Byte]()

    // Add content-length for POST/PUT
    // NOTE: We assume that Content-Type is already present in headers
    private def bodyHeaders =
        if (Connection.requiresRequestBody(method))
            Seq("content-length" → Array(messageBody.size.toString))
        else
            Seq()

    private val headerValuesMap = headers ++ bodyHeaders asJava

    // Secondary constructor simulates a GET or a DELETE
    def this(
            externalContext: ExternalContext,
            indentedLogger: IndentedLogger,
            contextPath: String,
            pathQuery: String,
            method: String,
            headers: Map[String, Array[String]]) =
        this(externalContext, indentedLogger, contextPath, pathQuery, method, null, headers)

    private lazy val queryString = {
        val mark = pathQuery.indexOf('?')
        if (mark == -1) null else pathQuery.substring(mark + 1)
    }

    private lazy val pathInfo =  {
        val mark = pathQuery.indexOf('?')
        if (mark == -1) pathQuery else pathQuery.substring(0, mark)
    }

    private lazy val queryAndBodyParameters = {
        // Query string
        // SRV.4.1: "Query string data is presented before post body data."
        def queryParameters = Option(getQueryString) map decodeSimpleQuery getOrElse Seq()

        // POST body form parameters
        // NOTE: Remember, the servlet container does not help us decoding the body: the "other side" will just end up here
        // when asking for parameters.
        def bodyParameters =
            if (method == "POST" && contentType == "application/x-www-form-urlencoded" && messageBody.nonEmpty) {
                // SRV.4.1.1 When Parameters Are Available
                val bodyString = new String(messageBody, ServletExternalContext.getDefaultFormCharset)
                Option(bodyString) map decodeSimpleQuery getOrElse Seq()
            } else
                Seq()

        // Make sure to keep order
        mutable.LinkedHashMap() ++ combineValues[AnyRef, Array](queryParameters ++ bodyParameters) asJava
    }

    // NOTE: Provide an empty stream if there is no body because caller might assume InputStream is non-null
    private lazy val inputStream = new ByteArrayInputStream(messageBody)

    /* SUPPORTED: methods called by ExternalContextToHttpServletRequestWrapper */

    override def getMethod              = method
    override def getParameterMap        = queryAndBodyParameters
    override def getQueryString         = queryString
    override def getCharacterEncoding   = null;//TODO?
    override def getContentLength       = messageBody.size
    override def getContentType         = contentType
    override def getInputStream         = inputStream
    override def getReader              = null;//TODO?
    override def getAttributesMap       = super.getAttributesMap // just return super since we do not override attributes here
    override def getHeaderValuesMap     = headerValuesMap

    override def getClientContextPath(urlString: String) = super.getClientContextPath(urlString) // assuming this is not going to be called

    /*
    * NOTE: All the path methods are handled by the request dispatcher implementation in the servlet container upon
    * forward, but upon include we must provide them.
    *
    * NOTE: Checked 2009-02-12 that none of the methods below are called when forwarding through
    * spring/JSP/filter/Orbeon in Tomcat 5.5.27. HOWEVER they are called when including.
    */
    override def getPathInfo = pathInfo
    override def getServletPath = ""
    override def getContextPath = contextPath // return the context path passed to this wrapper

    override def getRequestPath: String = {
        // Get servlet path and path info
        val servletPath = Option(getServletPath) getOrElse ""
        val pathInfo    = Option(getPathInfo)    getOrElse ""

        // Concatenate servlet path and path info, avoiding a double slash
        val requestPath =
            if (servletPath.endsWith("/") && pathInfo.startsWith("/"))
                servletPath + pathInfo.substring(1)
            else
                servletPath + pathInfo

        // Add starting slash if missing
        appendStartingSlash(requestPath)
    }

    override def getRequestURI: String = {
        // Must return the path including the context
        val contextPath = getContextPath
        if (contextPath == "/")
            getRequestPath
        else
            getContextPath + getRequestPath
    }

    // Probably not needed because computed by ExternalContextToHttpServletRequestWrapper
    override def getRequestURL: String = {
        // Get absolute URL w/o query string e.g. http://foo.com/a/b/c
        val incomingRequestURL = super.getRequestURL
        // Resolving request URI against incoming absolute URL, e.g. /d/e/f -> http://foo.com/d/e/f
        NetUtils.resolveURI(getRequestURI, incomingRequestURL)
    }
}