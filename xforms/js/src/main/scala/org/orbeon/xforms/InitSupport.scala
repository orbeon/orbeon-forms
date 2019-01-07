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
import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

// Move to `Init` once `ORBEON.xforms.Init` is moved entirely to Scala.js
@JSExportTopLevel("ORBEON.xforms.InitSupport")
@JSExportAll
object InitSupport {

  import Private._

  def initialize(formElem: html.Form): Unit = {

    val formId   = formElem.id
    val initData = getInitDataForAllForms(formId)

    val twoPassSubmissionFields = collectTwoPassSubmissionFields(formElem)

    // Sets `repeatTreeChildToParent`, `repeatTreeParentToAllChildren`, `repeatIndexes`
    processRepeatHierarchy(initData.repeatTree)
    processRepeatIndexes(initData.repeatIndexes)

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

          val uuid = initData.uuid
          setInitialState(uuid)
          uuid

        case Some(_) if BrowserUtils.getNavigationType == BrowserUtils.NavigationType.Reload ⇒

          StateHandling.log("state found upon reload, setting initial state")

          val uuid = initData.uuid
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

    // Set global pointers to elements
    twoPassSubmissionFields foreach {
      case (UuidFieldName, e)         ⇒ Globals.formUUID        (formId) = e
      case (ServerEventsFieldName, e) ⇒ Globals.formServerEvents(formId) = e
      case _ ⇒
    }

    // Set global values
    Globals.formUUID        (formId).value = uuid
    Globals.formServerEvents(formId).value = ""

    initializeJavaScriptControls(initData)
    initializeKeyListeners(initData, formElem)
  }

  private def initializeKeyListeners(initData: InitData, formElem: html.Form): Unit = {
    initData.keyListeners.toOption foreach { keyListeners ⇒
      decode[rpc.KeyListeners](keyListeners) match {
        case Left(_)  ⇒
          // TODO: error
          None
        case Right(rpc.KeyListeners(listeners)) ⇒
          // TODO: Maybe deduplicate listeners, here or on server
          listeners foreach { case rpc.KeyListener(observer, keyText, modifiers) ⇒

            val mousetrap =
              if (observer == "#document")
                Mousetrap
              else //if (dom.document.getElementById(observer).classList.contains("xforms-dialog"))
                Mousetrap(dom.document.getElementById(observer).asInstanceOf[html.Element])

            val modifierStrings =
              modifiers.toList map (_.entryName)

            val modifierString =
              modifierStrings mkString " "

            val callback: js.Function = () ⇒ {
              DocumentAPI.dispatchEvent(
                targetId    = observer,
                eventName   = EventNames.KeyPress,
                incremental = false,
                properties  = Map(KeyTextPropertyName → keyText) ++ (modifiers map (_ ⇒ KeyModifiersPropertyName → modifierString)) toJSDictionary
              )
            }

            val keys = modifierStrings ::: List(keyText.toLowerCase) mkString "+"

            mousetrap.bind(keys, callback)
          }
      }
    }
  }

  // Also used by AjaxServer.js
  def initializeJavaScriptControls(initData: InitData): Unit = {
    initData.controls.toOption foreach { controls ⇒
      decode[rpc.Controls](controls) match {
        case Left(_)  ⇒
          // TODO: error
          None
        case Right(rpc.Controls(controls)) ⇒

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
      }
    }
  }

  def getInitDataForAllForms: js.Dictionary[InitData] =
    g.orbeonInitData.asInstanceOf[js.Dictionary[InitData]]

  // NOTE: Public for now as it is also called from AjaxServer.js
  def processRepeatHierarchy(repeatTreeString: String): Unit = {

    val childToParent    = parseRepeatTree(repeatTreeString)
    val childToParentMap = childToParent.toMap

    Globals.repeatTreeChildToParent = childToParentMap.toJSDictionary

    val parentToChildren = m.Map[String, js.Array[String]]()

    childToParentMap foreach { case (child, parent) ⇒
      Iterator.iterateOpt(parent)(childToParentMap.get) foreach { p ⇒
        parentToChildren.getOrElseUpdate(p, js.Array[String]()).push(child)
      }
    }

    Globals.repeatTreeParentToAllChildren = createParentToChildrenMap(childToParentMap).toJSDictionary
  }

  private object Private {

    private val TwoPassSubmissionFields = Set(UuidFieldName, ServerEventsFieldName)

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

    def processRepeatIndexes(repeatIndexesString: String): Unit = {
      Globals.repeatIndexes = parseRepeatIndexes(repeatIndexesString).toMap.toJSDictionary
    }
  }
}
