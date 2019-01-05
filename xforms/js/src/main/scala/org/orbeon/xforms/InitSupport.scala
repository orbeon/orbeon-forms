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
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.StateHandling.ClientState
import org.orbeon.xforms.facade.{AjaxServer, Globals, InitData, Properties}
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

  def initialize(formElement: html.Form): Unit = {

    val formId = formElement.id

    val twoPassSubmissionFields = collectTwoPassSubmissionFields(formElement)

    // Sets `repeatTreeChildToParent`, `repeatTreeParentToAllChildren`, `repeatIndexes`
    processRepeatHierarchy(getInitDataForAllForms(formId).repeatTree)
    processRepeatIndexes(getInitDataForAllForms(formId).repeatIndexes)

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

          val uuid =  getInitDataForAllForms(formId).uuid
          setInitialState(uuid)
          uuid

        case Some(_) if BrowserUtils.getNavigationType == BrowserUtils.NavigationType.Reload ⇒

          StateHandling.log("state found upon reload, setting initial state")

          val uuid =  getInitDataForAllForms(formId).uuid
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
            events      = js.Array(new AjaxServer.Event(formElement, null, null, "xxforms-all-events-required")),
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

    val UuidFieldName         = "$uuid"
    val ServerEventsFieldName = "$server-events"

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
