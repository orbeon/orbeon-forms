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

import io.circe.generic.auto._
import io.circe.parser.decode
import org.orbeon.facades.Mousetrap
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.Constants._
import org.orbeon.xforms.EventNames.{KeyModifiersPropertyName, KeyTextPropertyName}
import org.orbeon.xforms.StateHandling.ClientState
import org.orbeon.xforms.facade._
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.html
import org.scalajs.dom.html.Input

import scala.collection.{mutable ⇒ m}
import scala.scalajs.js
import scala.scalajs.js.Dictionary
import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

// Move to `Init` once `ORBEON.xforms.Init` is moved entirely to Scala.js
@JSExportTopLevel("ORBEON.xforms.InitSupport")
@JSExportAll
object InitSupport {

  import Private._

  def initialize(): Unit =
    dom.document.forms                      filter
      (_.classList.contains("xforms-form")) foreach
      (e ⇒ initialize(e.asInstanceOf[html.Form]))

  private def initialize(formElem: html.Form): Unit = {

    val formId = formElem.id

    // The error panel shouldn't depend on much and is useful early on
    val errorPanel = ErrorPanel.initializeErrorPanel(formElem) getOrElse
      (throw new IllegalStateException(s"missing error panel element for form `$formId`"))

    val initializations =
      decode[rpc.Initializations](getInitDataForAllForms(formId).initializations) match {
        case Left(e) ⇒
          ErrorPanel.showError(formId, e.getMessage)
          return
        case Right(initializations) ⇒
          initializations
      }

    // Q: Do this later?
    $(formElem).removeClass("xforms-initially-hidden")

    def setInitialState(uuid: String): Unit =
      StateHandling.updateClientState(
        formId,
        ClientState(
          uuid     = uuid,
          sequence = 1
        )
      )

    val uuid =
      StateHandling.findClientState(formId) match {
        case None ⇒

          StateHandling.log("no state found, setting initial state")

          val uuid = initializations.uuid
          setInitialState(uuid)
          uuid

        case Some(_) if BrowserUtils.getNavigationType == BrowserUtils.NavigationType.Reload ⇒

          StateHandling.log("state found upon reload, setting initial state")

          val uuid = initializations.uuid
          setInitialState(uuid)
          uuid

        case Some(_) if Properties.revisitHandling.get() == "reload" ⇒

          StateHandling.log("state found with `revisitHandling` set to `reload`, reloading page")

          StateHandling.clearClientState(formId)
          dom.window.location.reload(flag = true)

          // No need to continue the initialization
          return

        case Some(state) ⇒

          StateHandling.log("state found, assuming back/forward/navigate, requesting all events")

          AjaxServer.fireEvents(
            events      = js.Array(new AjaxServer.Event(formElem, null, null, EventNames.XXFormsAllEventsRequired)),
            incremental = false
          )

          state.uuid // should be the same as `getInitDataForAllForms(formId).uuid`!
      }

    val (uuidInput, serverEventInput) = {

      var uuidInput        : html.Input = null
      var serverEventInput : html.Input = null

      // Set global pointers to elements
      collectTwoPassSubmissionFields(formElem) foreach {
        case (UuidFieldName, e)         ⇒ uuidInput = e
        case (ServerEventsFieldName, e) ⇒ serverEventInput = e
        case _ ⇒
      }

      assert(uuidInput ne null)
      assert(serverEventInput ne null)

      uuidInput → serverEventInput
    }

    val (repeatTreeChildToParent, repeatTreeParentToAllChildren) =
      processRepeatHierarchy(initializations.repeatTree)

    // NOTE on paths: We switched back and forth between trusting the client or the server. Starting 2010-08-27
    // the server provides the info. Starting 2011-10-05 we revert to using the server values instead of client
    // detection, as that works in portals. The concern with using the server values was proxying. But should
    // proxying be able to change the path itself? If so, wouldn't other things break anyway? So for now
    // server values it is.

    Page.setForm(
      formId,
      new Form(
        elem                          = formElem,
        uuidInput                     = uuidInput,
        serverEventInput              = serverEventInput,
        ns                            = formId.substring(0, formId.indexOf("xforms-form")),
        xformsServerPath              = initializations.xformsServerPath,
        xformsServerUploadPath        = initializations.xformsServerUploadPath,
        calendarImagePath             = initializations.calendarImagePath,
        errorPanel                    = errorPanel,
        repeatTreeChildToParent       = repeatTreeChildToParent,
        repeatTreeParentToAllChildren = repeatTreeParentToAllChildren,
        repeatIndexes                 = processRepeatIndexes(initializations.repeatIndexes)
      )
    )

    uuidInput.value = uuid
    serverEventInput.value = ""

    initializeJavaScriptControls(initializations.controls)
    initializeKeyListeners(initializations.listeners, formElem)

    runInitialServerEvents(initializations.events, formElem)
  }

  // Used by AjaxServer.js
  def initializeJavaScriptControlsFromSerialized(initData: String): Unit =
    decode[List[rpc.Control]](initData) match {
      case Left(_)  ⇒
        // TODO: error
        None
      case Right(controls) ⇒
        initializeJavaScriptControls(controls)
    }

  // Used by AjaxServer.js
  def processRepeatHierarchyUpdateForm(formId: String, repeatTreeString: String): Unit = {

    val (repeatTreeChildToParent, repeatTreeParentToAllChildren) =
      processRepeatHierarchy(repeatTreeString)

    val form = Page.getForm(formId)

    form.repeatTreeChildToParent       = repeatTreeChildToParent
    form.repeatTreeParentToAllChildren = repeatTreeParentToAllChildren
  }

  private object Private {

    private val TwoPassSubmissionFields = Set(UuidFieldName, ServerEventsFieldName)

    def getInitDataForAllForms: js.Dictionary[InitData] =
      g.orbeonInitData.asInstanceOf[js.Dictionary[InitData]]

    def collectTwoPassSubmissionFields(formElement: html.Form): Map[String, Input] =
      formElement.elements.iterator collect
        { case e: html.Input if TwoPassSubmissionFields(e.name) ⇒ e } map
        { e ⇒ e.name → e } toMap

    def parseRepeatIndexes(repeatIndexesString: String): List[(String, String)] =
      for {
        repeatIndexes ← repeatIndexesString.splitTo[List](",")
        repeatInfos   = repeatIndexes.splitTo[List]() // must be of the form "a b"
      } yield
        repeatInfos.head → repeatInfos.last

    def parseRepeatTree(repeatTreeString: String): List[(String, String)] =
      for {
       repeatTree  ← repeatTreeString.splitTo[List](",")
       repeatInfos = repeatTree.splitTo[List]() // must be of the form "a b"
       if repeatInfos.size > 1
     } yield
       repeatInfos.head → repeatInfos.last

    def createParentToChildrenMap(childToParentMap: Map[String, String]): collection.Map[String, js.Array[String]] = {

      val parentToChildren = m.Map[String, js.Array[String]]()

       childToParentMap foreach { case (child, parent) ⇒
         Iterator.iterateOpt(parent)(childToParentMap.get) foreach { p ⇒
           parentToChildren.getOrElseUpdate(p, js.Array[String]()).push(child)
         }
       }

      parentToChildren
    }

    def processRepeatHierarchy(repeatTreeString: String): (Dictionary[String], Dictionary[js.Array[String]]) = {

      val childToParent    = parseRepeatTree(repeatTreeString)
      val childToParentMap = childToParent.toMap

      val parentToChildren = m.Map[String, js.Array[String]]()

      childToParentMap foreach { case (child, parent) ⇒
        Iterator.iterateOpt(parent)(childToParentMap.get) foreach { p ⇒
          parentToChildren.getOrElseUpdate(p, js.Array[String]()).push(child)
        }
      }

      (childToParentMap.toJSDictionary, createParentToChildrenMap(childToParentMap).toJSDictionary)
    }

    def processRepeatIndexes(repeatIndexesString: String): Dictionary[String] =
      parseRepeatIndexes(repeatIndexesString).toMap.toJSDictionary

    def initializeJavaScriptControls(controls: List[rpc.Control]): Unit =
      controls foreach { case rpc.Control(id, valueOpt) ⇒
        Option(dom.document.getElementById(id).asInstanceOf[html.Element]) foreach { control ⇒
          val jControl = $(control)
          // Exclude controls in repeat templates
          if (jControl.parents(".xforms-repeat-template").length == 0) {
              if (XBL.isComponent(control)) {
                // Custom XBL component initialization
                val instance = XBL.instanceForControl(control)
                if (instance ne null) {
                  valueOpt foreach { value ⇒
                    Controls.setCurrentValue(control, value)
                  }
                }
              } else if (jControl.is(".xforms-dialog.xforms-dialog-visible-true")) {
                  // Initialized visible dialogs
                  Init._dialog(control)
              } else if (jControl.is(".xforms-select1-appearance-compact, .xforms-select-appearance-compact")) {
                  // Legacy JavaScript initialization
                  Init._compactSelect(control)
              } else if (jControl.is(".xforms-range")) {
                  // Legacy JavaScript initialization
                  Init._range(control)
              }
          }
        }
      }

    def initializeKeyListeners(listeners: List[rpc.KeyListener], formElem: html.Form): Unit =
      listeners foreach { case rpc.KeyListener(eventNames, observer, keyText, modifiers) ⇒

        // NOTE: 2019-01-07: We don't handle dialogs yet.
        //if (dom.document.getElementById(observer).classList.contains("xforms-dialog"))

        val mousetrap =
          if (observer == "#document")
            Mousetrap
          else
            Mousetrap(dom.document.getElementById(observer).asInstanceOf[html.Element])

        val modifierStrings =
          modifiers.toList map (_.entryName)

        val modifierString =
          modifierStrings mkString " "

        val callback: js.Function = (e: dom.KeyboardEvent, combo: String) ⇒ {

          val properties =
            Map(KeyTextPropertyName → keyText) ++
              (modifiers map (_ ⇒ KeyModifiersPropertyName → modifierString))

          DocumentAPI.dispatchEvent(
            targetId    = observer,
            eventName   = e.`type`,
            incremental = false,
            properties  = properties.toJSDictionary
          )

          if (modifiers.nonEmpty)
            e.preventDefault()
        }

        val keys = modifierStrings ::: List(keyText.toLowerCase) mkString "+"

        // It is unlikely that supporting multiple event names is very useful, but you can imagine
        // in theory supporting both `keydown` and `keyup` for example.
        eventNames foreach { eventName ⇒
          mousetrap.bind(keys, callback, eventName)
        }
      }

    def runInitialServerEvents(events: List[rpc.ServerEvent], formElem: html.Form): Unit =
      events foreach { case rpc.ServerEvent(delay, discardable, showProgress, event) ⇒
        AjaxServer.createDelayedServerEvent(
          serverEvents = event,
          delay        = delay.toDouble,
          showProgress = showProgress,
          discardable  = discardable,
          formId       = formElem.id
        )
      }
  }
}
