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

import org.orbeon.connection.StreamedContent
import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.ExternalContext.Session
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.http.*
import org.orbeon.oxf.http.Headers.*
import org.orbeon.oxf.pipeline.InitUtils.*
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.processor.XPLConstants.OXF_PROCESSORS_NAMESPACE
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, SecureUtils, URLRewriterUtils}
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache.CacheTracer

import java.util.Locale
import java.{util, util as ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


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
// TODO: This should reuse/be merged with `InternalHttpClient`!
//
object TestHttpClient {

  sealed trait CacheEvent
  case class DigestAndTemplate(digestIfFound: Option[String]) extends CacheEvent
  case class StaticState(found: Boolean, digest: String)      extends CacheEvent

  private val Logger = LoggerFactory.createLogger(TestHttpClient.getClass)

  private val Scheme      = "http"
  private val RemoteHost  = "localhost"
  private val RemoteAddr  = "127.0.0.1"

  private object ServerState {
    val Port             = 8080
    val Host             = "localhost"
    val ContextPath      = "/orbeon"
    val OrbeonTokenValue = SecureUtils.randomHexId
    val serverAttributes = mutable.LinkedHashMap[String, AnyRef]() += (OrbeonTokenLower -> OrbeonTokenValue)
    val sessions         = mutable.HashMap[String, Session]()
  }

  def connect(
    url         : String,
    method      : HttpMethod,
    headers     : Map[String, List[String]],
    content     : Option[StreamedContent],
    credentials : Option[Credentials] = None,
    attributes  : Map[String, AnyRef] = Map.empty
  ): (ProcessorService, HttpResponse, Option[Session], List[CacheEvent]) = {

    require(url.startsWith("/"), "TestHttpClient only supports absolute paths")

    implicit val logger = new IndentedLogger(Logger)

    val processorService = {

      def newProcessorDef(name: String) =
        new ProcessorDefinition(QName(name, OXF_PROCESSORS_NAMESPACE))

      val pfcProcessorDefinition =
        newProcessorDef("page-flow") |!> (_.addInput("controller", "oxf:/apps/fr/page-flow.xml"))

      new ProcessorService(pfcProcessorDefinition, None)
    }

    val events = mutable.ListBuffer[CacheEvent]()

    val tracer = new CacheTracer {
      override def digestAndTemplateStatus(digestIfFound: Option[String]) = events += DigestAndTemplate(digestIfFound)
      override def staticStateStatus(found: Boolean, digest: String)      = events += StaticState(found, digest)
    }

    val (response, sessionOpt) =
      withPipelineContext { pipelineContext =>

        val (externalContext, response) = {

          val webAppContext  = new TestWebAppContext(Logger, ServerState.serverAttributes)

          val request = LocalRequest(
            safeRequestCtx     =
              SafeRequestContext(
                webAppContext,
                makeBaseRequest(attributes, credentials)
              ),
            pathQuery               = url,
            method                  = method,
            headersMaybeCapitalized = headers + (OrbeonToken -> List(ServerState.OrbeonTokenValue)),
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

        withDebug(s"performing `$method` request to `$url` with headers `$headers` and content ${content.isDefined}") {
          ProcessorService.withProcessorService(processorService) {
            processorService.service(pipelineContext, externalContext)
          }
        }


        (response, Option(externalContext.getRequest.getSession(false)))
      }

    val httpResponse =
      new HttpResponse {
        lazy val statusCode   = response.getStatus
        lazy val headers      = response.capitalizedHeaders
        lazy val lastModified = DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.LastModified)
        lazy val content      = response.streamedContent
        def disconnect()      = content.close()
      }

    debug(s"response status code: ${httpResponse.statusCode}, headers: ${httpResponse.headers}, last modified: ${httpResponse.lastModified}")

    (processorService, httpResponse, sessionOpt, events.toList)
  }

  // Create a request with only the methods used by `LocalRequest.incomingRequest` parameter
  def makeBaseRequest(
    attributes           : Map[String, AnyRef] = Map.empty,
    connectionCredentials: Option[Credentials] = None
  ): RequestAdapter = new RequestAdapter {

    override val getContextPath                          = ServerState.ContextPath // called indirectly by `getClientContextPath`
    override val getAttributesMap                        = ju.Collections.synchronizedMap(attributes.asJava)
    override def incomingCookies                         = Nil
    override val getRequestURL                           = s"$Scheme://$RemoteHost:${ServerState.Port}${ServerState.ContextPath}/" // only for to resolve

    override val getContainerType                        = "servlet"
    override val getContainerNamespace                   = ""
    override val getPathTranslated                       = ""
    override val getProtocol                             = "HTTP/1.1"
    override val getServerPort                           = ServerState.Port
    override val getScheme                               = Scheme
    override val getRemoteHost                           = RemoteHost
    override val getRemoteAddr                           = RemoteAddr
    override val isSecure                                = Scheme == "https"
    override val getServerName                           = ServerState.Host
    override def getClientContextPath(urlString: String) = URLRewriterUtils.getClientContextPath(this, URLRewriterUtils.isPlatformPath(urlString))
    override def servicePrefix: String                   = "" // xxx or "/" or compute?
    override def sessionInvalidate()                     = session.invalidate()

    override val credentials                             = connectionCredentials

    // The following are never used by our code
    override val isRequestedSessionIdValid               = false   // only delegated
    override def isUserInRole(role: String)              = false   // called by `xxf:is-user-in-role()`
    override val getAuthType                             = "BASIC" // some processors read it but result is unused

    // Session handling
    private val session: Session =
      new SimpleSession(SecureUtils.randomHexId) |!>
        XFormsStateManager.sessionCreated

    override def getSession(create: Boolean): Session = session
  }
}
