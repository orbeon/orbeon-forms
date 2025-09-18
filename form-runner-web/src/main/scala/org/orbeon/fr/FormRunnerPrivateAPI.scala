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

import org.log4s.Logger
import org.orbeon.fr.rpc.FormRunnerRpcClient
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xbl.Wizard
import org.orbeon.xbl.Wizard.WizardCompanion
import org.orbeon.xforms
import org.orbeon.xforms.Page
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.{HTMLFormElement, URLSearchParams}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.UndefOr


object FormRunnerPrivateAPI extends js.Object {

  // Placing this here because we don't want this to be visible in `FormRunnerAPI`, which is a `js.Object`
  val logger: Logger = LoggerFactory.createLogger("org.orbeon.fr.FormRunnerAPI")

  private val ErrorScrollOffset = -100

  private val NewEditViewPathRe = """(.*/fr/)([^/]+)/([^/]+)/(new|edit|view)(?:/([^/]+))?$""".r

  // https://github.com/orbeon/orbeon-forms/issues/6960
  def enableClientDataStatus(uuid: String): Unit =
    Page.findFormByUuid(uuid)
      .foreach(_.allowClientDataStatus = true)

  // https://github.com/orbeon/orbeon-forms/issues/4286
  def setDataStatus(uuid: String, safe: Boolean): Unit =
    Page.findFormByUuid(uuid)
      .foreach(_.formDataSafe = safe)

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

    val domParser    = new dom.DOMParser
    val formDocument = domParser.parseFromString(formElem.toString, dom.MIMEType.`text/html`)
    val formElement  = formDocument.querySelector("form").asInstanceOf[HTMLFormElement]
    dom.document.body.appendChild(formElement)
    formElement.submit()
  }

  def newToEdit(documentId: String, isDraft: String): Unit = {

    val location = dom.window.location

    location.pathname match {
      case NewEditViewPathRe(_, _, _, "new", _) =>
        val newSearch = {
          val urlSearchParams = new URLSearchParams(location.search)
          urlSearchParams.delete("draft")
          if (isDraft.toBoolean)
            urlSearchParams.set("draft", "true")
          urlSearchParams.toString match {
            case ""                 => ""
            case stringSearchParams => s"?$stringSearchParams"
          }
        }

        // `newSearch`: for example `?form-version=42`
        // `hash`: for now not used by Form Runner, but it is safer to keep it
        dom.window.history.replaceState(
          statedata = dom.window.history.state,
          title     = "",
          url       = s"edit/$documentId$newSearch${location.hash}"
        )
      case _ =>
    }
  }

  def editToNew(): Unit = {

    val location = dom.window.location

    location.pathname match  {
      case NewEditViewPathRe(context, app, form, "edit", _) =>
        val newSearch = {
          val urlSearchParams = new URLSearchParams(location.search)
          urlSearchParams.delete("draft")
          urlSearchParams.toString match {
            case ""                 => ""
            case stringSearchParams => s"?$stringSearchParams"
          }
        }

        // `newSearch`: for example `?form-version=42`
        // `hash`: for now not used by Form Runner, but it is safer to keep it
        dom.window.history.replaceState(
          statedata = dom.window.history.state,
          title     = "",
          url       = s"$context$app/$form/new$newSearch${location.hash}"
        )
      case _ =>
    }
  }

  def updateWizardPageName(form: xforms.Form, pageNameWithIndex: String): Unit = {

    removeReplaceOrAddUrlParameter("fr-wizard-page", Some(pageNameWithIndex))

    val (pageNamePart: String, pageIndexOptPart: Option[Int]) = {
      val parts = pageNameWithIndex.splitTo[List]("/")
      parts.head -> parts.lift(1).flatMap(s => s.toIntOption)
    }

    XBL.instanceForControl(form.elem.querySelectorT(".xbl-fr-wizard"))

    XBL.instanceForControl(form.elem.querySelectorT(".xbl-fr-wizard"))
      .asInstanceOf[WizardCompanion]
      ._dispatchPageChangeEvent(
        new Wizard.PageChangeEvent {
          override val pageName : UndefOr[String] = pageNamePart
          override val pageIndex: UndefOr[Int]    = pageIndexOptPart.orUndefined
        }
      )
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
      sectionNames.splitTo[Array]().toJSArray
    )
  }

  def processRpcResponse(id: String, response: String): Unit =
    FormRunnerRpcClient.processResponse(id, response)
}
