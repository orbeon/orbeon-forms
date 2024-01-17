/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import java.io.{Externalizable, ObjectInput, ObjectOutput}
import java.util.concurrent.ConcurrentLinkedQueue

import org.orbeon.oxf.externalcontext.ExternalContext.SessionListener

class SessionListeners extends Externalizable {

  private var listeners = new ConcurrentLinkedQueue[SessionListener]

  @volatile
  private var closed = false

  // 2015-07-02: called by:
  // - NetUtils.{deleteFileOnSessionTermination, renameAndExpireWithSession}
  // - XFormsStateManager.addCacheSessionListener
  def addListener(sessionListener: SessionListener): Unit =
    if (! closed)
      listeners.add(sessionListener)
    else
      throw new IllegalStateException("session already destroyed")

  // 2015-07-02: called by:
  // - XFormsStateManager.removeCacheSessionListener
  def removeListener(sessionListener: SessionListener): Unit =
    if (! closed)
      listeners.remove(sessionListener)

  // 2015-07-02: called by:
  // - OrbeonSessionListener.sessionDestroyed
  def iterateRemoveAndClose(): Iterator[SessionListener] = {
    closed = true
    Iterator.continually(listeners.poll()).takeWhile(_ ne null) // poll() retrieves and removes the head
  }

  // Use custom serialization so we can set a non-null `listeners` fields when Tomcat decides to restore a session
  override def readExternal(objectInput: ObjectInput): Unit = {
    listeners = new ConcurrentLinkedQueue[SessionListener]
    closed = objectInput.readBoolean()
  }

  override def writeExternal(objectOutput: ObjectOutput): Unit = {
    objectOutput.writeBoolean(closed)
  }
}

object SessionListeners {
  val SessionListenersKey = "oxf.servlet.session-listeners"
}