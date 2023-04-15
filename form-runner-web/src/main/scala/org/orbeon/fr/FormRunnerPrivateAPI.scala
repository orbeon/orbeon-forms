/**
  * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.fr

import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.{$, Page}
import org.scalajs.dom
import org.scalajs.dom.experimental.URLSearchParams
import org.scalajs.dom.experimental.domparser.{DOMParser, SupportedType}
import org.scalajs.dom.raw.HTMLFormElement
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global


object FormRunnerPrivateAPI extends js.Object {

  private val ListenerSuffix = ".orbeon-beforeunload"
  private val ListenerEvents = s"beforeunload$ListenerSuffix"

  // 2018-05-07: Some browsers, including Firefox and Chrome, no longer use the message provided here.
  private val Message = "You may lose some unsaved changes."

  private val NewPathSuffix = "/new"

  def setDataStatus(uuid: String, safe: Boolean): Unit = {

    // https://github.com/orbeon/orbeon-forms/issues/4286
    Page.findFormByUuid(uuid) foreach (_.isFormDataSafe = safe)

    if (safe)
      $(global.window).off(
        ListenerEvents
      )
    else
      $(global.window).on(
        ListenerEvents,
        ((_: JQueryEventObject) => Message): js.Function
      )
  }

  def submitLogin(
    username    : String,
    password    : String,
    loginAction : String
  ): Unit = {

    val formElem =
      <form action={loginAction} method="post">
        <input type="hidden" name="j_username" value={username}/>
        <input type="hidden" name="j_password" value={password}/>
      </form>

    val domParser    = new DOMParser
    val formDocument = domParser.parseFromString(formElem.toString, SupportedType.`text/html`)
    val formElement  = formDocument.querySelector("form").asInstanceOf[HTMLFormElement]
    dom.document.body.appendChild(formElement)
    formElement.submit()
  }

  def newToEdit(documentId: String, isDraft: String): Unit = {

    val location = dom.window.location

    if (location.pathname.endsWith(NewPathSuffix)) {

      val newSearch = {
        val supportsURLSearchParams = global.URLSearchParams.asInstanceOf[UndefOr[URLSearchParams]].isDefined
        if (supportsURLSearchParams) {
          val urlSearchParams = new URLSearchParams(location.search)
          urlSearchParams.delete("draft")
          if (isDraft.toBoolean) urlSearchParams.set("draft", "true")
          urlSearchParams.toString match {
            case ""                 => ""
            case stringSearchParams => s"?$stringSearchParams"
          }
        } else {
          // IE11 is the last browser not to support `URLSearchParams`; in this case, don't bother updating `draft`
          location.search
        }
      }

      // `search`: for example `?form-version=42`
      // `hash`: for now not used by Form Runner, but it is safer to keep it
      dom.window.history.replaceState(
        statedata = dom.window.history.state,
        title     = "",
        url       = s"edit/$documentId$newSearch${location.hash}"
      )
    }
  }

  // For Summary page when changing the form version
  def updateLocationFormVersion(version: Int): Unit =
    removeReplaceOrAddUrlParameter("form-version", Some(version.toString))

  def removeUrlParameter(name: String): Unit =
    removeReplaceOrAddUrlParameter(name, None)

  private def removeReplaceOrAddUrlParameter(name: String, newValueOpt: Option[String]): Unit = {

    val location = dom.window.location
    val search   = location.search
    val query    = PathUtils.decodeSimpleQuery(if (search.startsWith("?")) search.substring(1) else search)
    val newQuery = PathUtils.removeReplaceOrAddUrlParameter(query, name, newValueOpt)

    if (query != newQuery)
      dom.window.history.replaceState(
        statedata = dom.window.history.state,
        title     = "",
        url       = PathUtils.recombineQuery(location.pathname, newQuery) + location.hash
      )
  }

  def navigateToError(
    validationPosition: String,
    elementId         : String,
    controlName       : String,
    controlLabel      : String,
    validationMessage : String,
    validationLevel   : String,
    sectionNames      : String
  ): Unit = {

    Option(dom.window.document.getElementById(elementId)) foreach { elem =>
      dom.window.asInstanceOf[js.Dynamic].scrollTo( // `scrollTo(options)` not in facade yet
        new js.Object {
          val top      = elem.getBoundingClientRect().top + dom.window.pageYOffset + ErrorScrollOffset
          val behavior = "smooth"
        }
      )
    }

    FormRunnerAPI.errorSummary._dispatch(
      validationPosition.toInt,
      elementId,
      controlName,
      controlLabel,
      validationMessage,
      validationLevel,
      sectionNames.splitTo[js.Array]()
    )
  }

  private val ErrorScrollOffset = -100
}
