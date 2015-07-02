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
package org.orbeon.oxf.webapp

import java.io.Serializable
import java.{util ⇒ ju}

import org.orbeon.oxf.pipeline.api.ExternalContext.Session.SessionListener

class SessionListeners extends Serializable {

    @transient
    private var listeners: ju.List[SessionListener] = null

    def addListener(sessionListener: SessionListener): Unit = {
        if (listeners == null) {
            listeners = new ju.ArrayList[SessionListener]
        }
        listeners.add(sessionListener)
    }

    def removeListener(sessionListener: SessionListener): Unit =
        if (listeners != null) listeners.remove(sessionListener)

    def iterator: ju.Iterator[SessionListener] =
        if (listeners ne null)
            listeners.iterator
        else
            ju.Collections.emptyList[SessionListener].iterator
}

object SessionListeners {
    val SessionListenersKey = "oxf.servlet.session-listeners"
}