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
package org.orbeon.oxf.logging

import java.util.concurrent.atomic.AtomicInteger

import javax.servlet.http.{HttpServletRequest, HttpSession}
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session, SessionListener, SessionScope}
import org.orbeon.oxf.externalcontext.RequestAdapter
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{JSON, NetUtils}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

private class MinimalSession(session: HttpSession) extends Session {

  def getId = session.getId

  def getCreationTime: Long                                           = throw new UnsupportedOperationException
  def isNew: Boolean                                                  = throw new UnsupportedOperationException
  def getLastAccessedTime: Long                                       = throw new UnsupportedOperationException
  def removeListener(sessionListener: SessionListener): Unit          = throw new UnsupportedOperationException
  def setMaxInactiveInterval(interval: Int): Unit                     = throw new UnsupportedOperationException
  def addListener(sessionListener: SessionListener): Unit             = throw new UnsupportedOperationException
  def invalidate(): Unit                                              = throw new UnsupportedOperationException
  def getMaxInactiveInterval: Int                                     = throw new UnsupportedOperationException

  def getAttribute(name: String, scope: SessionScope)                 = throw new UnsupportedOperationException
  def setAttribute(name: String, value: AnyRef, scope: SessionScope)  = throw new UnsupportedOperationException
  def removeAttribute(name: String, scope: SessionScope)              = throw new UnsupportedOperationException
}

private class MinimalRequest(req: HttpServletRequest) extends RequestAdapter {

  override lazy val getAttributesMap = new InitUtils.RequestMap(req)
  override def getRequestPath        = NetUtils.getRequestPathInfo(req)
  override def getMethod             = HttpMethod.withNameInsensitive(req.getMethod)

  private lazy val sessionWrapper = new MinimalSession(req.getSession(true))

  override def getSession(create: Boolean): Session = {
    val underlyingSession = req.getSession(create)
    if (underlyingSession ne null)
      sessionWrapper
    else
      null
  }
}

object MinimalRequest {
  def apply(req: HttpServletRequest): Request = new MinimalRequest(req)
}

object LifecycleLogger {

  private val LoggerName = "org.orbeon.lifecycle"
  private val Logger     = LoggerFactory.getLogger(LoggerName)

  private val globalRequestCounter = new AtomicInteger(0)

  private def arrayToParams(params: Array[String]) =
    params.grouped(2) map { case Array(x, y) => (x, y) } toList

  private def getRequestId(req: Request) =
    req.getAttributesMap.get(LoggerName + ".request-id").asInstanceOf[String]

  private def requestIdSetIfNeeded(req: Request): (String, Boolean) =
    Option(getRequestId(req)) map (_ -> true) getOrElse {
      val requestId = globalRequestCounter.incrementAndGet().toString
      req.getAttributesMap.put(LoggerName + ".request-id", requestId)
      requestId -> false
    }

  private def findSessionId(req: Request): Option[String] =
    req.sessionOpt map (_.getId)

  private def event(req: Request, source: String, message: String, params: Seq[(String, String)]): Unit =
    try {
      val (requestId, existingId) = requestIdSetIfNeeded(req)
      event(requestId, findSessionId(req), source, message, (if (existingId) Nil else basicRequestDetails(req)) ++ params)
    } catch {
      case NonFatal(t) => logInternalError(t)
    }

  private def event(requestId: String, sessionIdOpt: Option[String], source: String, message: String, params: Seq[(String, String)]): Unit = {
    val all = ("request" -> requestId) +: ("session" -> sessionIdOpt.orNull) +: ("source" -> source) +: ("message" -> message) +: params
    val formatted = all collect { case (name, value) if value ne null => s""""$name": "${JSON.quoteValue(value)}"""" }
    Logger.info(formatted.mkString("""event: {""", ", ", "}"))
  }

  private def logInternalError(t: Throwable) =
    Logger.error(s"throwable caught during logging: ${t.getMessage}")

  def basicRequestDetailsAssumingRequest(params: List[(String, String)]): List[(String, String)] =
    basicRequestDetails(NetUtils.getExternalContext.getRequest) ::: params

  def basicRequestDetails(req: Request) =
    List(
      "path"   -> req.getRequestPath,
      "method" -> req.getMethod.entryName
    )

  def withEventAssumingRequest[T](source: String, message: String, params: Seq[(String, String)])(body: => T): T =
    withEvent(NetUtils.getExternalContext.getRequest, source, message, params)(body)

  def withEvent[T](req: Request, source: String, message: String, params: Seq[(String, String)])(body: => T): T = {
    val timestamp = System.currentTimeMillis
    var currentThrowable: Throwable = null
    event(req, source, s"start: $message", params)
    try {
      body
    } catch {
      case t: Throwable =>
        currentThrowable = t
        throw t
    } finally {
      val endParams =
        ("time", f"${System.currentTimeMillis - timestamp}%,d ms") ::
        ((currentThrowable ne null) list ("threw", currentThrowable.getMessage))

      event(req, source, s"end: $message", endParams)
    }
  }

  def eventAssumingRequest(source: String, message: String, params: Seq[(String, String)]): Unit =
    try event(NetUtils.getExternalContext.getRequest, source, message, params)
    catch { case NonFatal(t) => logInternalError(t) }

  def formatDelay(timestamp: Long) =
    (System.currentTimeMillis - timestamp).toString
}
