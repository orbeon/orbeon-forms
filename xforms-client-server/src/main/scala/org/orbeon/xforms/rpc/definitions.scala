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
case class PollEvent(delay: Long)
case class UserScript(functionName: String, targetId: String, observerId: String, paramValues: List[String])
case class Dialog(id: String, neighborId: Option[String])
case class Error(title: String, details: String, formId: String)

case class Initializations(
  uuid                          : String,
  namespacedFormId              : String,
  repeatTree                    : String,
  repeatIndexes                 : String,
  xformsServerPath              : String,
  xformsServerSubmitActionPath  : Option[String],
  xformsServerSubmitResourcePath: Option[String],
  xformsServerUploadPath        : String,
  controls                      : List[Control],
  listeners                     : List[KeyListener],
  pollEvent                     : Option[PollEvent],
  userScripts                   : List[UserScript],
  messagesToRun                 : List[String],
  dialogsToShow                 : List[Dialog],
  focusElementId                : Option[String],
  errorsToShow                  : Option[Error],
  configuration                 : ConfigurationProperties,
)

sealed trait WireAjaxEvent {

  def eventName  : String
  def properties : Map[String, String]

  def valueOpt: Option[String] = properties.get("value")
}

case class WireAjaxEventWithTarget(
  eventName  : String,
  targetId   : String,
  properties : Map[String, String]
) extends WireAjaxEvent

case class WireAjaxEventWithoutTarget(
  eventName  : String,
  properties : Map[String, String]
) extends WireAjaxEvent

case class ConfigurationProperties(
  sessionHeartbeatEnabled           : Boolean,
  maxInactiveIntervalMillis         : Long,
  sessionExpirationTriggerPercentage: Int,
  sessionExpirationMarginMillis     : Long,
  sessionId                         : String,
  revisitHandling                   : String,
  delayBeforeIncrementalRequest     : Int,
  delayBeforeAjaxTimeout            : Long,
  internalShortDelay                : Int,
  delayBeforeDisplayLoading         : Int,
  delayBeforeUploadProgressRefresh  : Int,
  helpHandler                       : Boolean,
  helpTooltip                       : Boolean,
  showErrorDialog                   : Boolean,
  loginPageDetectionRegexp          : Option[String],
  retryDelayIncrement               : Int,
  retryMaxDelay                     : Int,
  useAria                           : Boolean,
  resourcesVersioned                : Boolean,
  resourcesVersionNumber            : Option[String],
  dateFormatInput                   : String, // set but not used anymore
  timeFormatInput                   : String, // set but not used anymore
)
