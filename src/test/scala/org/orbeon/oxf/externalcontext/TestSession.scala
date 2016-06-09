package org.orbeon.oxf.externalcontext

import java.{util ⇒ ju}

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.ExternalContext.Session

import scala.collection.JavaConverters._

class TestSession(sessionId: String) extends Session {

  private val creationTime     = System.currentTimeMillis
  private val sessionListeners = new ju.LinkedHashSet[Session.SessionListener]
  private val attributesMap    = new ju.LinkedHashMap[String, AnyRef]
  private var expired          = false

  def expireSession(): Unit = {
    for (listener ← sessionListeners.asScala)
      listener.sessionDestroyed()
    expired = true
  }

  def addListener(sessionListener: Session.SessionListener): Unit = {
    checkExpired()
    sessionListeners.add(sessionListener)
  }

def removeListener(sessionListener: Session.SessionListener): Unit = {
    checkExpired()
    sessionListeners.remove(sessionListener)
  }

  def getAttributesMap: ju.Map[String, AnyRef] = {
    checkExpired()
    attributesMap
  }

  def getAttributesMap(scope: Int): ju.Map[String, AnyRef] = {
    checkExpired()
    attributesMap
  }

  def getCreationTime: Long = {
    checkExpired()
    creationTime
  }

  def getId: String = {
    checkExpired()
    sessionId
  }

// TODO
  def getLastAccessedTime: Long = {
    checkExpired()
    0L
  }

// TODO
  def getMaxInactiveInterval: Int = {
    checkExpired()
    0
  }

// TODO
  def invalidate(): Unit = {
    checkExpired()
  }

// TODO
  def isNew: Boolean = {
    checkExpired()
    false
  }

// TODO
  def setMaxInactiveInterval(interval: Int): Unit = {
    checkExpired()
  }

  private def checkExpired(): Unit =
    if (expired)
      throw new OXFException("Cannot call methods on expired session.")
}