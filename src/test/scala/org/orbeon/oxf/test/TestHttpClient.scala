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

import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.{LocalExternalContext, _}
import org.orbeon.oxf.http.{Headers, HttpResponse, StreamedContent}
import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.pipeline.api.ExternalContext.Session
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
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
    methodUpper : String,
    headers     : Map[String, List[String]],
    content     : Option[StreamedContent],
    username    : Option[String] = None,
    group       : Option[String] = None,
    roles       : List[String]   = Nil
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

            override val getAttributesMap                        = ju.Collections.synchronizedMap(new ju.HashMap[String, AnyRef]())
            override val getRequestURL                           = s"$Scheme://$RemoteHost:$Port$ContextPath/" // just used to resolve against

            override val getContainerType                        = "servlet"
            override val getContainerNamespace                   = ""
            override val getPortletMode                          = null
            override val getWindowState                          = null
            override val getNativeRequest                        = null
            override val getPathTranslated                       = ""
            override val getProtocol                             = "HTTP/1.1"
            override val getServerPort                           = Port
            override val getScheme                               = Scheme
            override val getRemoteHost                           = RemoteHost
            override val getRemoteAddr                           = RemoteAddr
            override val isSecure                                = Scheme == "https"
            override val getLocale                               = null
            override val getLocales                              = null
            override val getServerName                           = ServerHost
            override def getClientContextPath(urlString: String) = URLRewriterUtils.getClientContextPath(this, URLRewriterUtils.isPlatformPath(urlString))

            override def sessionInvalidate()                     = session foreach (_.invalidate())
            override val getRequestedSessionId                   = null

            private var session: Option[Session] = None

            override def getSession(create: Boolean): Session =
              session getOrElse {
                if (create) {
                  val newSession = new TestSession(SecureUtils.randomHexId)
                  session = Some(newSession)
                  newSession
                } else {
                  null
                }
              }

            override val getUsername                             = username.orNull
            override val getUserRoles                            = roles.toArray
            override val getUserGroup                            = group.orNull

            // The following are never used by our code
            override val isRequestedSessionIdValid               = false   // only delegated
            override def isUserInRole(role: String)              = false   // called by `xxf:is-user-in-role()`
            override val getUserPrincipal                        = null    // some processors read it but result is unused
            override val getAuthType                             = "BASIC" // some processors read it but result is unused
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
