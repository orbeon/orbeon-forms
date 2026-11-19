/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analytics

import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.util.control.NonFatal


sealed trait SubmissionCategory
object SubmissionCategory {
  case object Persistence extends SubmissionCategory
  case object Service     extends SubmissionCategory
  case object Other       extends SubmissionCategory

  def fromUri(uri: String): SubmissionCategory =
    if (
      uri.contains("/fr/service/persistence/") ||
      uri.contains("/fr/service/sqlite/")      ||
      uri.contains("/fr/service/postgresql/")  ||
      uri.contains("/fr/service/mysql/")       ||
      uri.contains("/fr/service/oracle/")      ||
      uri.contains("/fr/service/db2/")         ||
      uri.contains("/fr/service/sqlserver/")   ||
      uri.contains("/fr/service/resource/")    ||
      uri.contains("/fr/service/filesystem/")  ||
      uri.contains("/fr/service/s3/")
    )
      Persistence
    else if (uri.startsWith("oxf:") || uri.startsWith("input:"))
      Other
    else
      Service
}

sealed trait DocumentRemovalReason
object DocumentRemovalReason {
  case object Error            extends DocumentRemovalReason
  case object SessionOverflow  extends DocumentRemovalReason
  case object SessionDestroyed extends DocumentRemovalReason
}

trait XFormsAnalyticsListener {
  def onHttpSessionCreated(sessionId: String, createdAtMs: Long, maxInactiveSec: Int): Unit
  def onHttpSessionDestroyed(sessionId: String, destroyedAtMs: Long, documentUuids: List[String]): Unit

  def onDocumentInitialized(
    doc            : XFormsContainingDocument,
    sessionIdOpt   : Option[String],
    usernameOpt    : Option[String],
    maxInactiveSec : Int,
    durationMs     : Long
  ): Unit

  def onDocumentInitFailed(requestPath: String, durationMs: Long): Unit

  def onAjaxUpdateCompleted(
    doc            : XFormsContainingDocument,
    sessionIdOpt   : Option[String],
    usernameOpt    : Option[String],
    maxInactiveSec : Int,
    isInteraction  : Boolean,
    durationMs     : Long,
    hadServerError : Boolean
  ): Unit

  def onDocumentPassivated(uuid: String, atMs: Long): Unit
  def onDocumentActivatedFromStore(uuid: String, atMs: Long): Unit
  def onDocumentRemoved(uuid: String, reason: DocumentRemovalReason, atMs: Long): Unit

  def onSubmissionCompleted(
    docOpt     : Option[XFormsContainingDocument],
    targetUri  : String,
    category   : SubmissionCategory,
    durationMs : Long,
    succeeded  : Boolean
  ): Unit
}

object XFormsAnalyticsSupport {

  @volatile var listenerOpt: Option[XFormsAnalyticsListener] = None

  @inline def withListener(f: XFormsAnalyticsListener => Unit): Unit =
    listenerOpt match {
      case Some(l) =>
        try f(l)
        catch { case NonFatal(_) => () }
      case None =>
        ()
    }
}
