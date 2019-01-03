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

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.facade.{AjaxServer, Controls, Globals, Properties}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.html

import scala.collection.{mutable ⇒ m}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.JSStringOps._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.{URIUtils, |}

@JSExportTopLevel("ORBEON.xforms.Document")
@JSExportAll
object DocumentAPI {

  import Private._

  // Dispatch an event
  // NOTE: This doesn't support all parameters.
  // Which should be deprecated, this or the other `dispatchEvent()`?
  def dispatchEvent(
    targetId     : String,
    eventName    : String,
    formElem     : js.UndefOr[html.Element]          = js.undefined,
    bubbles      : js.UndefOr[Boolean]               = js.undefined,
    cancelable   : js.UndefOr[Boolean]               = js.undefined,
    incremental  : js.UndefOr[Boolean]               = js.undefined,
    ignoreErrors : js.UndefOr[Boolean]               = js.undefined,
    properties   : js.UndefOr[js.Dictionary[String]] = js.undefined
  ): Unit = {

    val eventObject  = new js.Object
    val eventDynamic = eventObject.asInstanceOf[js.Dynamic]

    eventDynamic.targetId  = targetId
    eventDynamic.eventName = eventName

    formElem     foreach (eventDynamic.form         = _)
    bubbles      foreach (eventDynamic.bubbles      = _)
    cancelable   foreach (eventDynamic.cancelable   = _)
    incremental  foreach (eventDynamic.incremental  = _)
    ignoreErrors foreach (eventDynamic.ignoreErrors = _)
    properties   foreach (eventDynamic.properties   = _)

    dispatchEvent(eventObject)
  }

  // Dispatch an event defined by an object
  // NOTE: Use the first XForms form on the page when no form is provided.
  // TODO: Can we type `eventObject`?
  def dispatchEvent(eventObject: js.Object): Unit = {

    val eventDynamic = eventObject.asInstanceOf[js.Dynamic]

    val (resolvedForm, adjustedTargetId) =
      adjustIdNamespace(
        eventDynamic.form.asInstanceOf[html.Element],
        eventDynamic.targetId.asInstanceOf[String]
      )

    eventDynamic.form     = resolvedForm
    eventDynamic.targetId = adjustedTargetId

    AjaxServer.fireEvents(
      js.Array(new AjaxServer.Event(eventDynamic)),
      incremental = eventDynamic.incremental.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
    )
  }

  // Return the value of an XForms control
  def getValue(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): js.UndefOr[String] =
      Controls.getCurrentValue(findControlOrThrow(controlIdOrElem, formElem))

  // Set the value of an XForms control
  def setValue(
    controlIdOrElem : String | html.Element,
    newValue        : String | Double | Boolean,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): Unit = {

    val newStringValue = newValue.toString

    val control = findControlOrThrow(controlIdOrElem, formElem)

    require(
      ! $(control).is(".xforms-output, .xforms-upload"),
      s"Cannot set the value of an output or upload control for id `${control.id}`"
    )

    // Directly change the value in the UI without waiting for an Ajax response
    Controls.setCurrentValue(control, newStringValue)

    // And also fire server event
    val event = new AjaxServer.Event(
      new js.Object {
        val targetId  = control.id
        val eventName = "xxforms-value"
        val value     = newStringValue
      }
    )

    AjaxServer.fireEvents(js.Array(event), incremental = false)
  }

  def focus(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): Unit = {

    val control = findControlOrThrow(controlIdOrElem, formElem)

    Controls.setFocus(control.id)
    dispatchEvent(targetId = control.id, eventName = "xforms-focus")
  }

  //noinspection AccessorLikeMethodIsEmptyParen
  // https://github.com/orbeon/orbeon-forms/issues/3279
  // Whether the form is being reloaded from the server
  //
  // This is used in two cases:
  //
  // - browser back/reload with `revisit-handling == 'reload'`
  // - click reload on error panel
  //
  def isReloading(): Boolean = Globals.isReloading

  // Return the current index of the repeat (equivalent to `index($repeatId)`)
  def getRepeatIndex(repeatId: String): String = Globals.repeatIndexes(repeatId)

  // Get a value stored in the hidden client-state input field
  def getFromClientState(formId: String, key: String): String =
    clientStateToMap(formId).get(key).orNull

  // Store a value into the hidden client-state input field
  def storeInClientState(formId: String, key: String, value: String | Double | Boolean): Unit =
    mapToClientState(formId, clientStateToMap(formId) + (key → value.toString))

  private object Private {

    def adjustIdNamespace(
      formElem : js.UndefOr[html.Element],
      targetId : String
    ): (html.Element, String) = {

      val form = Support.formElemOrDefaultForm(formElem)
      val ns   = Globals.ns(form.id)

      // For backward compatibility, handle the case where the id is already prefixed.
      // This is not great as we don't know for sure whether the control starts with a namespace, e.g. `o0`,
      // `o1`, etc. It might be safer to disable the short namespaces feature because of this.
      form → (
        if (targetId.startsWith(ns))
          targetId
        else
          ns + targetId
      )
    }

    def findControlOrThrow(
      controlIdOrElem : String | html.Element,
      formElem        : js.UndefOr[html.Element]
    ): html.Element = {

      val (resolvedControlId, resolvedControlOpt) =
        (controlIdOrElem: Any) match {
          case givenControlId: String ⇒
            givenControlId → Option(dom.document.getElementById(adjustIdNamespace(formElem, givenControlId)._2))
          case givenElement: html.Element ⇒
            givenElement.id → Some(givenElement)
        }

      resolvedControlOpt match {
        case Some(resolvedControl: html.Element) if Controls.isInRepeatTemplate(resolvedControl) ⇒
          throw new IllegalArgumentException(s"Control is within a repeat template for id `$resolvedControlId`")
        case Some(resolvedControl: html.Element) ⇒
          resolvedControl
        case _ ⇒
          throw new IllegalArgumentException(s"Cannot find control for id `$resolvedControlId`")
      }
    }

    def clientStateToMap(formId: String): Map[String, String] =
      Globals.formClientState(formId).value.trimAllToOpt match {
        case Some(clientState) ⇒
          // Careful: JavaScript's `split` is different from Scala's `split`!
          clientState.jsSplit("&").to[List].sliding(2, 2) map {
            case List(key, value) ⇒
              key → URIUtils.decodeURIComponent(value)
          } toMap
        case None ⇒
          Map.empty
      }

    def mapToClientState(formId: String, map: Map[String, String]): Unit = {

      val stateString =
        map.iterator flatMap {
          case (key, value) ⇒
            Iterator(key, URIUtils.encodeURIComponent(value))
        } mkString "&"

      Globals.formClientState(formId).value = stateString
    }
  }

  def initialize(formElement: html.Form): Unit = {

    val formID = formElement.id

    formElement.elements.iterator collect { case e: html.Input ⇒ e } foreach {
      case e if e.name == "$uuid"           ⇒ Globals.formUUID        (formID) = e
      case e if e.name == "$static-state"   ⇒ Globals.formStaticState (formID) = e
      case e if e.name == "$dynamic-state"  ⇒ Globals.formDynamicState(formID) = e
      case e if e.name == "$server-events"  ⇒ Globals.formServerEvents(formID) = e
      case e if e.name == "$client-state"   ⇒
        Globals.formClientState(formID) = e
        if (e.value.isBlank) {
          // If the client state is empty, store the initial dynamic state (old system) or UUID (new system).
          // If it is not empty, this means that we already have an initial state stored there, and that this
          // function runs because the user reloaded or navigated back to this page.
          storeInClientState(formID, "initial-dynamic-state",  Globals.formDynamicState(formID).value)
          storeInClientState(formID, "uuid", Globals.formUUID(formID).value)
        } else {
          // The user reloaded or navigated back to this page. Reset the value of the $uuid field to
          // the value found in the client state, because the browser sometimes restores the value of
          // hidden fields in an erratic way, for example from the value the hidden field had from
          // the same URL loaded in another tab (e.g. Chrome, Firefox).
          Globals.formUUID(formID).value = getFromClientState(formID, "uuid")
        }

      case e if e.name == "$repeat-tree"    ⇒ processRepeatHierarchy(e.value)
      case e if e.name == "$repeat-indexes" ⇒ processRepeatIndexes(e.value)

      case _ ⇒ // NOP
    }

    // Ask server to resend events if this is not the first time load is called
    if (getFromClientState(formID, "load-did-run") eq null) {
        storeInClientState(formID, "load-did-run", "true")
        storeInClientState(formID, "sequence", "1")
    } else {
        if (Properties.revisitHandling.get() == "reload") {
            Globals.isReloading = true
            g.window.location.reload(true)
            //NOTE: You would think that if reload is canceled, you would reset this to false, but somehow this fails with IE
        } else {
            AjaxServer.fireEvents(
              events      = js.Array(new AjaxServer.Event(formElement, null, null, "xxforms-all-events-required")),
              incremental = false
            )
        }
    }
  }

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

  // NOTE: Also called from AjaxServer.js
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
}
