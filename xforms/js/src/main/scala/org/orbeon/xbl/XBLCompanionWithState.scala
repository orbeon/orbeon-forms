/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.xbl

import io.circe.{Decoder, Encoder, parser}
import org.orbeon.xforms.DocumentAPI
import org.orbeon.xforms.facade.XBLCompanion

import scala.util.{Failure, Success, Try}

abstract class XBLCompanionWithState extends XBLCompanion {

  import io.circe.syntax._

  type State

  protected var stateOpt: Option[State] = None

  implicit val stateEncoder: Encoder[State]
  implicit val stateDecoder: Decoder[State]

  private def encode(state: State)      : String = state.asJson.noSpaces
  private def decode(jsonString: String): Try[State] = parser.decode[State](jsonString).fold(Failure.apply, Success.apply)

  final override def xformsGetValue(): String =
    stateOpt map encode getOrElse ""

  final override def xformsUpdateValue(newValue: String): Unit =
    decode(newValue) match {
      case Success(newState) =>

        val previousStateOpt = stateOpt
        stateOpt = Some(newState)

        scribe.debug(s"previousStateOpt = `$previousStateOpt`, newState = `$newState`")
        xformsUpdateState(previousStateOpt, newState)
      case Failure(t) =>
        scribe.debug(s"error decoding value: ${t.getMessage}")
    }

  final def updateStateAndSendValueToServerIfNeeded(newState: State, valueFromState: State => String): Boolean = {

    val mustUpdateStateAndSendValue =
      ! (stateOpt exists (state => valueFromState(state) == valueFromState(newState)))

    scribe.debug(s"mustSendValue = `$mustUpdateStateAndSendValue`, stateOpt = `$stateOpt`, newState = `$newState`")

    if (mustUpdateStateAndSendValue) {
      stateOpt = Some(newState)
      val encodedState = encode(newState)
      scribe.debug(s"encodedState = `$encodedState`")
      DocumentAPI.setValue(containerElem, encodedState)
    }

    mustUpdateStateAndSendValue
  }

  def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit
}
