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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{JSON, LoggerFactory}

import scala.util.control.NonFatal


object LifecycleLogger {

  private val LoggerName = "org.orbeon.lifecycle"
  private val Logger     = LoggerFactory.createLogger(LoggerName)

  private val globalRequestCounter = new AtomicInteger(0)

  private def getRequestId(req: Request): String =
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

  private def logInternalError(t: Throwable): Unit =
    Logger.error(s"throwable caught during logging: ${t.getMessage}")

  def basicRequestDetailsAssumingRequest(params: List[(String, String)])(implicit ec: ExternalContext): List[(String, String)] =
    basicRequestDetails(ec.getRequest) ::: params

  def basicRequestDetails(req: Request) =
    List(
      "path"   -> req.getRequestPath,
      "method" -> req.getMethod.entryName
    )

  def withEventAssumingRequest[T](source: String, message: String, params: Seq[(String, String)])(body: => T)(implicit ec: ExternalContext): T =
    withEvent(ec.getRequest, source, message, params)(body)

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

  def eventAssumingRequest(source: String, message: String, params: Seq[(String, String)])(implicit ec: ExternalContext): Unit =
    try event(ec.getRequest, source, message, params)
    catch { case NonFatal(t) => logInternalError(t) }

  def formatDelay(timestamp: Long): String =
    (System.currentTimeMillis - timestamp).toString
}
