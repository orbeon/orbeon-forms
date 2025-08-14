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

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.rpc.ConfigurationProperties
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle


class Form(
  val uuid                          : String,
  val elem                          : html.Form,
  val ns                            : String,
  val contextAndNamespaceOpt        : Option[(String, String)],
  val xformsServerPath              : String,                          // could move to configuration
  val xformsServerSubmitActionPath  : Option[String],                  // could move to configuration
  val xformsServerSubmitResourcePath: Option[String],                  // could move to configuration
  val xformsServerUploadPath        : String,                          // could move to configuration
  var repeatTreeChildToParent       : js.Dictionary[String],           // for JavaScript access
  var repeatTreeParentToAllChildren : js.Dictionary[js.Array[String]], // for JavaScript access
  val repeatIndexes                 : js.Dictionary[Int],              // for JavaScript access
  val xblInstances                  : js.Array[XBLCompanion],
  val configuration                 : ConfigurationProperties
) extends js.Object { // so that properties/methods can be accessed from JavaScript

  private var discardableTimerIds: List[SetTimeoutHandle] = Nil

  private var callbacks: Map[String, List[js.Function]] = Map.empty

  val namespacedFormId: String = ns + Constants.FormClass
  val transform: (String, String) => String = InitSupport.getResponseTransform(contextAndNamespaceOpt)

  val eventSupport: EventListenerSupport = new EventListenerSupport {}
  val ajaxFieldChangeTracker: AjaxFieldChangeTracker = new AjaxFieldChangeTracker

  def destroy(): Unit = {
    eventSupport.clearAllListeners()
    xblInstances.foreach(_.destroy())
    xblInstances.clear()
    InitSupport.removeNamespacePromise(ns)

    // Hide error panel, which isn't in `Globals.dialogs`
    errorPanel.asInstanceOf[js.Dynamic].hide()
  }

  def addCallback(name: String, fn: js.Function): Unit =
    callbacks += name -> (callbacks.getOrElse(name, Nil) :+ fn)

  def removeCallback(name: String, fn: js.Function): Unit = {
    callbacks += name -> callbacks.getOrElse(name, Nil).filterNot(_ eq fn)

    if (callbacks.get(name).exists(_.isEmpty))
      callbacks -= name
  }

  def getCallbacks(name: String): List[js.Function] =
    callbacks.getOrElse(name, Nil)

  lazy val errorPanel: js.Object =
    ErrorPanel.initializeErrorPanel(elem) getOrElse
      (throw new IllegalStateException(s"missing error panel element for form `${elem.id}`"))

  // https://github.com/orbeon/orbeon-forms/issues/6960
  var allowClientDataStatus: Boolean = false

  // https://github.com/orbeon/orbeon-forms/issues/4286
  private var _formDataSafe: Boolean = true

  def formDataSafe_=(safe: Boolean): Unit = {
    val wasSafe   = _formDataSafe
    _formDataSafe = safe
    if (safe && ! wasSafe) {
      dom.window.removeEventListener(Form.ListenerEvents, Form.ReturnMessage)
      clearDiscardableTimerIds()
    } else if (! safe && wasSafe) {
      dom.window.addEventListener   (Form.ListenerEvents, Form.ReturnMessage)
    }
  }

  def formDataSafe: Boolean = _formDataSafe

  def addDiscardableTimerId(id: SetTimeoutHandle): Unit =
    discardableTimerIds ::= id

  def clearDiscardableTimerIds(): Unit = {
    discardableTimerIds foreach timers.clearTimeout
    discardableTimerIds = Nil
  }

  def namespaceIdIfNeeded(id: String): String =
    if (id.startsWith(ns))
      id
    else
      ns + id

  def deNamespaceIdIfNeeded(id: String): String =
    if (id.startsWith(ns))
      id.substringAfter(ns)
    else
      id

  // 2022-09-07: Keep `()` for JavaScript callers
  def helpHandler(): Boolean = configuration.helpHandler
  def helpTooltip(): Boolean = configuration.helpTooltip
  def useARIA    (): Boolean = configuration.useAria
}

private object Form {
  private val ListenerEvents = "beforeunload"
  // 2018-05-07: Some browsers, including Firefox and Chrome, no longer use the message provided here.
  private val ReturnMessage: js.Function1[dom.Event, Boolean] = { event => event.preventDefault(); true }
}