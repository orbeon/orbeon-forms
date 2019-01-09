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

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

class Form(
  val elem                          : html.Element,
  val uuidInput                     : html.Input,
  val serverEventInput              : html.Input,
  val ns                            : String,
  val xformsServerPath              : String,
  val xformsServerUploadPath        : String,
  val calendarImagePath             : String,
  val errorPanel                    : js.Object,
  var repeatTreeChildToParent       : js.Dictionary[String],           // for JavaScript access
  var repeatTreeParentToAllChildren : js.Dictionary[js.Array[String]], // for JavaScript access
  val repeatIndexes                 : js.Dictionary[String]            // for JavaScript access
) extends js.Object { // so that properties/methods can be accessed from JavaScript

  private var discardableTimerIds: List[Int] = Nil
  private var dialogTimerIds: Map[String, Int] = Map.empty

  val loadingIndicator = new LoadingIndicator

  def addDiscardableTimerId(id: Int): Unit =
    discardableTimerIds ::= id

  def clearDiscardableTimerIds(): Unit = {
    discardableTimerIds foreach dom.window.clearTimeout
    discardableTimerIds = Nil
  }

  def addDialogTimerId(dialogId: String, id: Int): Unit =
    dialogTimerIds += dialogId → id

  def removeDialogTimerId(dialogId: String): Unit = {
    dialogTimerIds.get(dialogId) foreach dom.window.clearTimeout
    dialogTimerIds -= dialogId
  }
}
