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

import enumeratum.*
import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.scalajs.dom

import scala.collection.immutable

object StateHandling {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.StateHandling")

  sealed trait StateResult extends EnumEntry
  object       StateResult extends Enum[StateResult] {

    val values: immutable.IndexedSeq[StateResult] = findValues

    case object Initialized extends StateResult
    case object Restored    extends StateResult
    case object Reloaded    extends StateResult
  }

  def getStateResult(uuid: String, revisitHandling: String): StateResult =
    XFormsSessionStorage.hasClientState(uuid) match {
      case false =>
        logger.debug("no state found")
        StateResult.Initialized
      case true =>
        if (BrowserUtils.getNavigationType == BrowserUtils.NavigationType.Reload) {
          logger.debug("state found upon reload")
          StateResult.Initialized
        } else if (revisitHandling == "reload") {
          logger.debug("state found with `revisitHandling` set to `reload`")
          StateResult.Reloaded
        } else {
          logger.debug("state found, assuming back/forward/navigate")
          StateResult.Restored
        }
    }

  def initializeState(namespacedFormId: String, stateResult: StateResult): Unit =
    stateResult match {
      case StateResult.Initialized =>
        logger.debug("setting initial state")
        XFormsSessionStorage.set(namespacedFormId, 1)
      case StateResult.Reloaded =>
        logger.debug("clearing initial state")
        clearClientState(namespacedFormId)
      case StateResult.Restored =>
        logger.debug("will request all events")
    }

  def getSequence(namespacedFormId: String): String =
    XFormsSessionStorage.get(namespacedFormId) match {
        case Some(sequence) => sequence.toString
        case None           => throw new IllegalArgumentException(s"Sequence for form `$namespacedFormId` not found")
      }

  def updateSequence(namespacedFormId: String, newSequence: Int): Unit =
    XFormsSessionStorage.set(namespacedFormId, newSequence)

  def clearClientState(namespacedFormId: String): Unit =
    XFormsSessionStorage.remove(namespacedFormId)

  private object XFormsSessionStorage {

    def hasClientState(uuid: String): Boolean =
      Option(dom.window.sessionStorage.getItem(keyFromUuid(uuid))).isDefined

    def get(namespacedFormId: String): Option[Int] =
      Option(dom.window.sessionStorage.getItem(keyFromNamespacedFormId(namespacedFormId))).flatMap(_.toIntOption)

    def set(namespacedFormId: String, sequence: Int): Unit =
      dom.window.sessionStorage.setItem(keyFromNamespacedFormId(namespacedFormId), sequence.toString)

    def remove(namespacedFormId: String): Unit =
      dom.window.sessionStorage.removeItem(keyFromNamespacedFormId(namespacedFormId))

    private val KeyPrefix = "xf-"

    private def keyFromNamespacedFormId(namespacedFormId: String): String =
      Page.findXFormsFormFromNamespacedId(namespacedFormId) match {
        case Some(form) => keyFromUuid(form.uuid)
        case None       => throw new IllegalArgumentException(s"UUID for form `$namespacedFormId` not found")
      }

    private def keyFromUuid(uuid: String): String =
      KeyPrefix + uuid
  }
}
