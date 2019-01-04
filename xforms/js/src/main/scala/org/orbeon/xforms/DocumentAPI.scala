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

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.facade.{AjaxServer, Controls, Globals, Properties}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.html

import scala.collection.{mutable ⇒ m}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.JSStringOps._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.{Dictionary, |}

@JSExportTopLevel("ORBEON.xforms.Document")
@JSExportAll
object DocumentAPI {

  case class ClientState(
    uuid     : String,
    sequence : Int
  )

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

  def getFormUuid(formId: String): String =
    getClientStateOrThrow(formId).uuid

  def getSequence(formId: String): String =
    getClientStateOrThrow(formId).sequence.toString

  def updateSequence(formId: String, newSequence: Int): Unit =
    updateClientState(
      formId      = formId,
      clientState = getClientStateOrThrow(formId).copy(sequence = newSequence)
    )

  def initialize(formElement: html.Form): Unit = {

    val formId = formElement.id

    formElement.elements.iterator collect { case e: html.Input ⇒ e } foreach {
      case e if e.name == "$uuid"           ⇒ Globals.formUUID        (formId) = e
      case e if e.name == "$server-events"  ⇒ Globals.formServerEvents(formId) = e
      case e if e.name == "$repeat-tree"    ⇒ processRepeatHierarchy(e.value)
      case e if e.name == "$repeat-indexes" ⇒ processRepeatIndexes(e.value)
      case _ ⇒ // NOP
    }

    findClientState(formId) match {
      case None ⇒
        // Initial state
        updateClientState(
          formId,
          ClientState(
            uuid     = Globals.formUUID(formId).value,
            sequence = 1
          )
        )
      case Some(_) if Properties.revisitHandling.get() == "reload" ⇒
        // The user reloaded or navigated back to this page and we must reload the page

        clearClientState(formId)
        Globals.isReloading = true
        dom.window.location.reload(flag = true)
        // NOTE: You would think that if reload is canceled, you would reset this to false, but
        // somehow this fails with IE.
      case Some(state) ⇒
        // The user reloaded or navigated back to this page and we must restore state

        // Old comment: Reset the value of the `$uuid` field to the value found in the client state,
        // because the browser sometimes restores the value of hidden fields in an erratic way, for
        // example from the value the hidden field had from the same URL loaded in another tab (e.g.
        // Chrome, Firefox).
        Globals.formUUID(formId).value = state.uuid

        AjaxServer.fireEvents(
          events      = js.Array(new AjaxServer.Event(formElement, null, null, "xxforms-all-events-required")),
          incremental = false
        )
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

  private def createParentToChildrenMap(childToParentMap: Map[String, String]): collection.Map[String, js.Array[String]] = {

    val parentToChildren = m.Map[String, js.Array[String]]()

     childToParentMap foreach { case (child, parent) ⇒
       Iterator.iterateOpt(parent)(childToParentMap.get) foreach { p ⇒
         parentToChildren.getOrElseUpdate(p, js.Array[String]()).push(child)
       }
     }

    parentToChildren
  }

  private def processRepeatIndexes(repeatIndexesString: String): Unit = {
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

  private object Private {

    def log(s: String): Unit = ()
//      println(s"state handling: $s")

    // Assume the state is a `js.Dictionary[String]` mapping form ids to serialized state
    private def findRawState: Option[Dictionary[String]] =
      Option(dom.window.history.state) map
        (_.asInstanceOf[js.Dictionary[String]])

    def getClientStateOrThrow(formId: String): ClientState =
      findClientState(formId) getOrElse (throw new IllegalStateException(s"client state not found for form `$formId`"))

    def findClientState(formId: String): Option[ClientState] =
      findRawState flatMap (_.get(formId)) flatMap { serialized ⇒
        decode[ClientState](serialized) match {
          case Left(_)  ⇒
            log(s"error parsing state for form `$formId` and value `$serialized`")
            None
          case Right(state) ⇒
            log(s"found state for form `$formId` and value `$state`")
            Some(state)
        }
      }

    def updateClientState(formId: String, clientState: ClientState): Unit = {

      val dict       = findRawState getOrElse js.Dictionary[String]()
      val serialized = clientState.asJson.noSpaces

      log(s"updating client state for form `$formId` with value `$serialized`")

      dict(formId) = serialized

      dom.window.history.replaceState(
        statedata = dict,
        title     = "",
        url       = null
      )
    }

    def clearClientState(formId: String): Unit =
      findRawState foreach { state ⇒
        dom.window.history.replaceState(
          statedata = state -= formId,
          title     = "",
          url       = null
        )
      }

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
  }
}
