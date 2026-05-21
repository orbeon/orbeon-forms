package org.orbeon.oxf.fb.mcp

import org.orbeon.oxf.xforms.XFormsContainingDocument

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*


private[mcp] final class FormBuilderMcpSessionStore(ttlMillis: Long = 30L * 60L * 1000L) {

  private val sessions = new ConcurrentHashMap[String, BuilderSession]

  def create(documentId: String, document: XFormsContainingDocument): String = {
    val sessionId = UUID.randomUUID().toString
    sessions.put(sessionId, BuilderSession(documentId, document, System.currentTimeMillis()))
    sessionId
  }

  def withSession[T](sessionId: String)(body: BuilderSession => T): T = {
    val session = Option(sessions.get(sessionId)).getOrElse(throw new IllegalArgumentException(s"Unknown session: $sessionId"))
    session.lastAccess = System.currentTimeMillis()
    session.document.synchronized(body(session))
  }

  def cleanupExpiredSessions(): Unit = {
    val now = System.currentTimeMillis()
    sessions.entrySet().asScala.foreach { entry =>
      if (now - entry.getValue.lastAccess > ttlMillis)
        sessions.remove(entry.getKey)
    }
  }
}
