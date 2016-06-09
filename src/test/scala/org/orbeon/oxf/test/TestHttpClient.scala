/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.test

import java.{util ⇒ ju}

import org.apache.http.client.CookieStore
import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.{LocalExternalContext, _}
import org.orbeon.oxf.http.{Credentials, Headers, HttpResponse, StreamedContent}
import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{LoggerFactory, SecureUtils, URLRewriterUtils}
import org.orbeon.oxf.webapp.{ProcessorService, TestWebAppContext}
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache.CacheTracer
import org.orbeon.oxf.xml.XMLConstants._

import scala.collection.mutable

// HttpClient which simulates a call to Orbeon Forms without using a servlet. This acts as if the caller
// is a client making a call to an Orbeon Forms server.
//
// Limitations:
//
// - `WebAppContext` keeps attributes globally so breaks test isolation!
// - no notion of a "server" which would keep sessions alive between calls
// - no `Set-Cookie`/`Cookie` headers
//
// NOTE: Doesn't extend HttpClient because it has to return a `ProcessorService` and `CacheEvent`s.
//
object TestHttpClient {

  sealed trait CacheEvent
  case class DigestAndTemplate(digestIfFound: Option[String]) extends CacheEvent
  case class StaticState(found: Boolean, digest: String)      extends CacheEvent

  private val Logger = LoggerFactory.createLogger(TestHttpClient.getClass)

  private val Scheme      = "http"
  private val Port        = 8080
  private val RemoteHost  = "localhost"
  private val RemoteAddr  = "127.0.0.1"

  private val ServerHost  = "localhost"
  private val ContextPath = "/orbeon"

  def connect(
    url         : String,
    credentials : Option[Credentials], // ignored for now
    cookieStore : CookieStore,         // ignored for now
    methodUpper : String,
    headers     : Map[String, List[String]],
    content     : Option[StreamedContent]
  ): (ProcessorService, HttpResponse, List[CacheEvent]) = {

    require(url.startsWith("/"), "TestHttpClient only supports absolute paths")

    val processorService = {

      def newProcessorDef(name: String) =
        new ProcessorDefinition(new QName(name, OXF_PROCESSORS_NAMESPACE))

      val pfcProcessorDefinition =
        newProcessorDef("page-flow") |!> (_.addInput("controller", "oxf:/page-flow.xml"))

      new ProcessorService(pfcProcessorDefinition, None)
    }

    val events = mutable.ListBuffer[CacheEvent]()

    val tracer = new CacheTracer {
      override def digestAndTemplateStatus(digestIfFound: Option[String]) = events += DigestAndTemplate(digestIfFound)
      override def staticStateStatus(found: Boolean, digest: String)      = events += StaticState(found, digest)
    }

    val response =
      withPipelineContext { pipelineContext ⇒

        val (externalContext, response) = {

          val webAppContext = new TestWebAppContext(Logger)

          // Create a request with only the methods used by `LocalRequest.incomingRequest` parameter
          val baseRequest = new RequestAdapter {

            private val attributesMap = ju.Collections.synchronizedMap(new ju.HashMap[String, AnyRef]())

            override def getAttributesMap: ju.Map[String, AnyRef] = attributesMap

            override def getRequestURL                           = s"$Scheme://$RemoteHost:$Port$ContextPath/" // just used to resolve against

            override def getContainerType                        = "servlet"
            override def getContainerNamespace                   = ""
            override def getPortletMode                          = null
            override def getWindowState                          = null
            override def getNativeRequest                        = null
            override def getPathTranslated                       = ""
            override def getProtocol                             = "HTTP/1.1"
            override def getServerPort                           = Port
            override def getScheme                               = Scheme
            override def getRemoteHost                           = RemoteHost
            override def getRemoteAddr                           = RemoteAddr
            override def isSecure                                = Scheme == "https"
            override def getLocale                               = null
            override def getLocales                              = null
            override def getServerName                           = ServerHost
            override def getClientContextPath(urlString: String) = URLRewriterUtils.getClientContextPath(this, URLRewriterUtils.isPlatformPath(urlString))

            override def isRequestedSessionIdValid               = false // never called by our code, only delegated
            override def sessionInvalidate()                     = if (session ne null) session.invalidate()
            override def getRequestedSessionId                   = null

            private var session: ExternalContext.Session = null

            override def getSession(create: Boolean): ExternalContext.Session = {
              if ((session eq null) && create)
                session = new TestSession(SecureUtils.randomHexId)

              session
            }

            override def getUsername                             = null        // TODO
            override def getUserRoles                            = Array.empty // TODO
            override def getUserGroup                            = null        // TODO

            // The following are never used by our code
            override def isUserInRole(role: String)              = false   // called by `xxf:is-user-in-role()`
            override def getUserPrincipal                        = null    // some processors read it but result is unused
            override def getAuthType                             = "BASIC" // some processors read it but result is unused
          }

          val request = new LocalRequest(
            incomingRequest         = baseRequest,
            contextPath             = ContextPath,
            pathQuery               = url,
            methodUpper             = methodUpper,
            headersMaybeCapitalized = headers,
            content                 = content
          )

          val response = new LocalResponse(new ServletURLRewriter(request))

          val externalContext =
            new LocalExternalContext(
              webAppContext,
              request,
              response
            )

          (externalContext, response)
        }

        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
        pipelineContext.setAttribute("orbeon.cache.test.tracer", tracer)
        pipelineContext.setAttribute("orbeon.cache.test.initialize-xforms-document", true) // the default

        ProcessorService.withProcessorService(processorService) {
          processorService.service(pipelineContext, externalContext)
        }

        response
      }

    val httpResponse =
      new HttpResponse {
        lazy val statusCode   = response.statusCode
        lazy val headers      = response.capitalizedHeaders
        lazy val lastModified = Headers.firstDateHeaderIgnoreCase(headers, Headers.LastModified)
        lazy val content      = response.streamedContent
        def disconnect()      = content.close()
      }

    (processorService, httpResponse, events.toList)
  }
}
