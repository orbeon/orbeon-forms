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

package org.orbeon.xforms.rpc

import org.orbeon.oxf.util.Modifier


case class Control(id: String, valueOpt: Option[String])
case class KeyListener(eventName: Set[String], observer: String, keyText: String, modifiers: Set[Modifier])
case class ServerEvent(delay: Long, discardable: Boolean, showProgress: Boolean, encodedEvent: String)
case class UserScript(functionName: String, targetId: String, observerId: String, paramValues: List[String])

case class Initializations(
  uuid                   : String,
  namespacedFormId       : String,
  repeatTree             : String,
  repeatIndexes          : String,
  xformsServerPath       : String,
  xformsServerUploadPath : String,
  calendarImagePath      : String,
  controls               : List[Control],
  listeners              : List[KeyListener],
  events                 : List[ServerEvent],
  userScripts            : List[UserScript]
)
