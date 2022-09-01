/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.embedding.servlet

import java.io.Writer
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.orbeon.oxf.fr.embedding.APISupport.FormDynamicResourcesRegex
import org.orbeon.oxf.fr.embedding._
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.wsrp.WSRPSupport

import scala.util.Try


class ServletEmbeddingContext(
  val namespace  : String,
  req            : HttpServletRequest,
  val httpClient : HttpClient[org.apache.http.client.CookieStore]
) extends EmbeddingContext {

  private val session = req.getSession(true)

  def getSessionAttribute(name: String)                = Option(session.getAttribute(name))
  def setSessionAttribute(name: String, value: AnyRef) = session.setAttribute(name, value)
  def removeSessionAttribute(name: String)             = session.removeAttribute(name)

  val client = Headers.JavaApiEmbeddingClient
}

class ServletEmbeddingContextWithResponse(
  req          : HttpServletRequest,
  out          : Writer Either HttpServletResponse,
  namespace    : String,
  orbeonPrefix : String,
  httpClient   : HttpClient[org.apache.http.client.CookieStore]
) extends ServletEmbeddingContext(
  namespace,
  req,
  httpClient
) with EmbeddingContextWithResponse {

  def writer                                 = out.fold(identity, _.getWriter)
  def outputStream                           = Try(out.fold(_ => throw new IllegalStateException, _.getOutputStream))
  def setHeader(name: String, value: String) = out.foreach(_.setHeader(name, value))
  override def setStatusCode(code: Int)      = out.foreach(_.setStatus(code))

  def decodeURL(encoded: String) = {

    def namespaceResource(path: String) =
      path match {
        case "/xforms-server"              => true
        case path if path.endsWith(".css") => true
        case FormDynamicResourcesRegex(_)  => true
        case _                             => false
      }

    def createResourceURL(resourceId: String) =
      req.getContextPath + orbeonPrefix + '/' + (if (namespaceResource(resourceId)) namespace else APISupport.NamespacePrefix) + resourceId

    def path(navigationParameters: Map[String, Array[String]]) =
      navigationParameters.getOrElse(WSRPSupport.PathParameterName, Array()).headOption.getOrElse(throw new IllegalStateException)

    def createActionURL(portletMode: Option[String], windowState: Option[String], navigationParameters: Map[String, Array[String]]) =
      req.getContextPath + orbeonPrefix + '/' + path(navigationParameters).dropStartingSlash

    def createRenderURL(portletMode: Option[String], windowState: Option[String], navigationParameters: Map[String, Array[String]]) =
      path(navigationParameters)

    WSRPSupport.decodeURL(encoded, createResourceURL, createActionURL, createRenderURL)
  }
}

class ServletFilter extends Filter {

  private var settingsOpt: Option[EmbeddingSettings] = None

  override def init(config: FilterConfig): Unit = {
    APISupport.Logger.info("initializing embedding servlet filter")
    settingsOpt =
      Some(
        EmbeddingSettings(
          formRunnerURL  = Option(config.getInitParameter("form-runner-url")) getOrElse "http://localhost:8080/orbeon/",
          orbeonPrefix   = Option(config.getInitParameter("orbeon-prefix"))   getOrElse "/orbeon",
          resourcesRegex = Option(config.getInitParameter("resources-regex")) getOrElse APISupport.DefaultFormRunnerResourcePath,
          httpClient     = new ApacheHttpClient(HttpClientSettings(config.getInitParameter))
        )
      )
  }

  override def destroy(): Unit = {
    APISupport.Logger.info("destroying embedding servlet filter")
    settingsOpt foreach (_.httpClient.shutdown())
    settingsOpt = None
  }

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit =
    settingsOpt foreach { settings =>

      val httpReq = req.asInstanceOf[HttpServletRequest]
      val httpRes = res.asInstanceOf[HttpServletResponse]

      APISupport.scopeSettings(httpReq, settings) {
        NetUtils.getRequestPathInfo(httpReq) match {
          case settings.OrbeonSubmitPathRegex() =>
            // Request is for an Orbeon submission
            APISupport.proxySubmission(httpReq, httpRes)
          case settings.OrbeonResourcePathRegex(namespace, resourcePath) =>
            // Request is for an Orbeon resource or Ajax call
            APISupport.proxyServletResources(httpReq, httpRes, namespace, resourcePath)
          case _ =>
            // Not an Orbeon resource or submission
            chain.doFilter(httpReq, httpRes)
        }
      }
    }
}
