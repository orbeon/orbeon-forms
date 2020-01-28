/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event

import collection.{mutable,immutable}
import Dispatch.EventListener

// Support for adding/removing/getting event listeners.
trait ListenersTrait {

  // Not sure `Vector` is a good fit for small collections. Might use `List` and reverse upon `getListeners`.
  // Use `var`/`null` instead of `lazy val` so that `getListeners` won't cause creation of the map.
  private var listeners: mutable.Map[String, Vector[EventListener]] = _

  // Add a listener for the given event name
  def addListener(eventName: String, listener: EventListener): Unit = {
    require(eventName ne null)
    require(listener ne null)

    if (listeners eq null)
      listeners = new mutable.HashMap[String, Vector[EventListener]]

    val currentListeners = listeners.getOrElseUpdate(eventName, Vector.empty)
    listeners += eventName -> (currentListeners :+ listener)
  }

  // Add a given listener (if provided) or all listeners for the given event name
  def removeListener(eventName: String, listener: Option[EventListener]): Unit = {

    require(eventName ne null)
    require(listener ne null)

    if (listeners ne null)
      listeners.get(eventName) foreach {
        currentListeners =>
          listener match {
            case Some(listener) =>
              // Remove given listener only
              val newListeners = currentListeners filterNot (_ eq listener)
              if (newListeners.nonEmpty)
                listeners += eventName -> newListeners
              else
                listeners -= eventName
            case None =>
              // Remove all listeners
              listeners -= eventName
          }
      }
  }

  def getListeners(eventName: String): immutable.Seq[EventListener] =
    if (listeners ne null) listeners.getOrElse(eventName, Nil) else Nil
}