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
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.orbeon.xforms.facade.Properties
import org.scalajs.dom
import org.scalajs.dom.HashChangeEvent

import scala.collection.immutable
import scala.scalajs.js
import scala.scalajs.js.Dictionary
import scala.scalajs.js.annotation.JSExport

object StateHandling {

  import Private._

  // Restore state after hash changes
  def initializeHashChangeListener(): Unit =
    GlobalEventListenerSupport.addListener(
      target = dom.window,
      name   = "hashchange",
      fn     = (_: HashChangeEvent) => Private.varStateOpt.foreach(Private.replaceState)
    )

  case class ClientState(
    uuid     : String,
    sequence : Int
  )

  sealed trait StateResult extends EnumEntry

  object StateResult extends Enum[StateResult] {

    val values: immutable.IndexedSeq[StateResult] = findValues

    case class  Uuid(uuid: String)    extends StateResult
    case class  Restore(uuid: String) extends StateResult
    case object Reload                extends StateResult
  }

  def initializeState(formId: String, initialUuid: String): StateResult = {

    def setInitialState(uuid: String): Unit =
      updateClientState(
        formId,
        ClientState(
          uuid     = uuid,
          sequence = 1
        )
      )

    findClientState(formId) match {
      case None =>

        scribe.debug("no state found, setting initial state")

        val uuid = initialUuid
        setInitialState(uuid)
        StateResult.Uuid(uuid)

      case Some(_) if BrowserUtils.getNavigationType == BrowserUtils.NavigationType.Reload =>

        scribe.debug("state found upon reload, setting initial state")

        val uuid = initialUuid
        setInitialState(uuid)
        StateResult.Uuid(uuid)

      case Some(_) if Properties.revisitHandling.get() == "reload" =>

        scribe.debug("state found with `revisitHandling` set to `reload`, reloading page")

        clearClientState(formId)
        StateResult.Reload

      case Some(state) =>

        scribe.debug("state found, assuming back/forward/navigate, requesting all events")

        StateResult.Restore(state.uuid)
    }
  }

  @JSExport
  def getFormUuid(formId: String): String =
    getClientStateOrThrow(formId).uuid

  @JSExport
  def getSequence(formId: String): String =
    getClientStateOrThrow(formId).sequence.toString

  @JSExport
  def updateSequence(formId: String, newSequence: Int): Unit =
    updateClientState(
      formId      = formId,
      clientState = getClientStateOrThrow(formId).copy(sequence = newSequence)
    )

  def getClientStateOrThrow(formId: String): ClientState =
    findClientState(formId) getOrElse (throw new IllegalStateException(s"client state not found for form `$formId`"))

  def findClientState(formId: String): Option[ClientState] =
    findRawState flatMap (_.get(formId)) flatMap { serialized =>
      decode[ClientState](serialized) match {
        case Left(_)  =>
          scribe.debug(s"error parsing state for form `$formId` and value `$serialized`")
          None
        case Right(state) =>
          scribe.trace(s"found state for form `$formId` and value `$state`")
          Some(state)
      }
    }

  def updateClientState(formId: String, clientState: ClientState): Unit = {

    val dict       = findRawState getOrElse js.Dictionary[String]()
    val serialized = clientState.asJson.noSpaces

    scribe.debug(s"updating client state for form `$formId` with value `$serialized`")

    dict(formId) = serialized
    Private.replaceState(dict)
  }

  def clearClientState(formId: String): Unit =
    findRawState foreach { state =>
      state -= formId
      Private.replaceState(state)
    }

  private object Private {

    // When present, state is a `js.Dictionary[String]` mapping form ids to serialized state
    var varStateOpt: Option[Dictionary[String]] = None

    def replaceState(newState: Dictionary[String]): Unit = {
      Private.varStateOpt = Some(newState)
      saveStateToHistory(newState)
    }

    def findRawState: Option[Dictionary[String]] = {

      // Update `varStateOpt` and history state as needed
      (varStateOpt, Option(dom.window.history.state)) match {
        // State found in variable: save to history, which can help getting around Firefox issue 1183881
        case (Some(varState), _) => saveStateToHistory(varState)
        // State found in history ony, so save it to the variable
        case (None, Some(state)) => varStateOpt = Some(state.asInstanceOf[js.Dictionary[String]])
        // No state found (which means we might be in trouble)
        case (None, None)        => // nop
      }

      // Just return `varStateOpt` as it is now up-to-date
      varStateOpt
    }

    def saveStateToHistory(state: Dictionary[String]): Unit =
      dom.window.history.replaceState(
        statedata = state,
        title     = "",
        url       = null
      )

  }
}
