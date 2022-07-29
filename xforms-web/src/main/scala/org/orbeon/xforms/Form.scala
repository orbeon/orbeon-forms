/**
  * Copyright (C) 2017 Orbeon, Inc.
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

import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle

class Form(
  val uuid                          : String,
  val elem                          : html.Form,
  val uuidInput                     : html.Input,
  val ns                            : String,
  val xformsServerPath              : String,
  val xformsServerSubmitActionPath  : String,
  val xformsServerSubmitResourcePath: String,
  val xformsServerUploadPath        : String,
  var repeatTreeChildToParent       : js.Dictionary[String],           // for JavaScript access
  var repeatTreeParentToAllChildren : js.Dictionary[js.Array[String]], // for JavaScript access
  val repeatIndexes                 : js.Dictionary[String],           // for JavaScript access
  val xblInstances                  : js.Array[XBLCompanion]
) extends js.Object { // so that properties/methods can be accessed from JavaScript

  private var discardableTimerIds: List[SetTimeoutHandle] = Nil
  private var dialogTimerIds: Map[String, Int] = Map.empty

  lazy val errorPanel: js.Object =
    ErrorPanel.initializeErrorPanel(elem) getOrElse
      (throw new IllegalStateException(s"missing error panel element for form `${elem.id}`"))

  // https://github.com/orbeon/orbeon-forms/issues/4286
  var isFormDataSafe: Boolean = false

  def addDiscardableTimerId(id: SetTimeoutHandle): Unit =
    discardableTimerIds ::= id

  def clearDiscardableTimerIds(): Unit = {
    discardableTimerIds foreach timers.clearTimeout
    discardableTimerIds = Nil
  }

  def addDialogTimerId(dialogId: String, id: Int): Unit =
    dialogTimerIds += dialogId -> id

  def removeDialogTimerId(dialogId: String): Unit = {
    dialogTimerIds.get(dialogId) foreach dom.window.clearTimeout
    dialogTimerIds -= dialogId
  }
}
