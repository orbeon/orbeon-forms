/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import enumeratum._
import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.scalajs.dom.ext.SessionStorage

import scala.collection.compat._
import scala.collection.immutable
import scala.scalajs.js.annotation.JSExport

object StateHandling {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.StateHandling")

  sealed trait StateResult extends EnumEntry
  object       StateResult extends Enum[StateResult] {

    val values: immutable.IndexedSeq[StateResult] = findValues

    case object Initialized extends StateResult
    case object Restored    extends StateResult
    case object Reloaded    extends StateResult
  }

  def initializeState(formId: String, revisitHandling: String): StateResult =
    XFormsSessionStorage.get(formId) match {
      case None =>
        logger.debug("no state found, setting initial state")
        XFormsSessionStorage.set(formId, 1)
        StateResult.Initialized
      case Some(_) =>
        if (BrowserUtils.getNavigationType == BrowserUtils.NavigationType.Reload) {
          logger.debug("state found upon reload, setting initial state")
          XFormsSessionStorage.set(formId, 1)
          StateResult.Initialized
        } else if (revisitHandling == "reload") {
          logger.debug("state found with `revisitHandling` set to `reload`, reloading page")
          clearClientState(formId)
          StateResult.Reloaded
        } else {
          logger.debug("state found, assuming back/forward/navigate, requesting all events")
          StateResult.Restored
        }
    }

  @JSExport
  def getSequence(formId: String): String =
    XFormsSessionStorage.get(formId) match {
        case Some(sequence) => sequence.toString
        case None           => throw new IllegalArgumentException(s"Sequence for form `$formId` not found")
      }

  @JSExport
  def updateSequence(formId: String, newSequence: Int): Unit =
    XFormsSessionStorage.set(formId, newSequence)

  def clearClientState(formId: String): Unit =
    XFormsSessionStorage.remove(formId)

  private object XFormsSessionStorage {

    def get(formId: String): Option[Int] =
      SessionStorage(key(formId)).flatMap(_.toIntOption)

    def set(formId: String, sequence: Int): Unit =
      SessionStorage.update(key(formId), sequence.toString)

    def remove(formId: String): Unit =
      SessionStorage.remove(key(formId))

    private val KeyPrefix = "xf-"
    private def key(formId: String): String =
      Page.findXFormsFormFromNamespacedId(formId) match {
        case Some(form) => KeyPrefix + form.uuid
        case None       => throw new IllegalArgumentException(s"UUID for form `$formId` not found")
      }
  }
}
