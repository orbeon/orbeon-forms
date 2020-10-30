/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions

import org.apache.commons.lang3.StringUtils.isNotBlank
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAPI, XFormsAction}
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEventFactory.createEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEventTarget}

import scala.util.Try


class XFormsDispatchAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val interpreter   = actionContext.interpreter
    val actionElement = actionContext.element

    // Mandatory attribute
    val newEventNameAttributeValue =
      Option(actionElement.attributeValue(NAME_QNAME)) getOrElse
      (throw new OXFException("Missing mandatory name attribute on xf:dispatch element."))

    // As of 2009-05, XForms 1.1 gives @targetid priority over @target
    val newEventTargetIdValue =
      Option(actionElement.attributeValue(TARGETID_QNAME)) orElse
      Option(actionElement.attributeValue(TARGET_QNAME))   getOrElse
      (throw new OXFException("Missing mandatory targetid attribute on xf:dispatch element."))

    val resolvedNewEventName =
      Option(interpreter.resolveAVTProvideValue(actionElement, newEventNameAttributeValue)) getOrElse (return)

    val resolvedNewEventTargetStaticId =
      Option(interpreter.resolveAVTProvideValue(actionElement, newEventTargetIdValue)) getOrElse (return)

    // Optional attributes
    // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
    // The event factory makes sure that those values are ignored for predefined events
    val newEventBubbles =
      (Option(interpreter.resolveAVT(actionElement, "bubbles")) getOrElse true.toString).toBoolean

    // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
    // The event factory makes sure that those values are ignored for predefined events
    val newEventCancelable =
      (Option(interpreter.resolveAVT(actionElement, "cancelable")) getOrElse true.toString).toBoolean

    val resolvedDelayOpt =
      Option(interpreter.resolveAVT(actionElement, "delay")) filter
      isNotBlank                  flatMap // "The default is the empty string, which indicates no delay"
      (s => Try(s.toInt).toOption) filter  // "if the given value does not conform to xsd:nonNegativeInteger, then the event…"
      (_ >= 0)                            // "…is dispatched immediately as the result of the dispatch action"

    // Whether to allow duplicates by name/targetid if the event has a non-negative delay
    // See https://github.com/orbeon/orbeon-forms/issues/3208.
    val allowDuplicates =
      interpreter.resolveAVT(actionElement, XXFORMS_ALLOW_DUPLICATES_QNAME) == true.toString

    // Whether to tell the client to show a progress indicator when sending this event
    val showProgress =
      interpreter.resolveAVT(actionElement, XXFORMS_SHOW_PROGRESS_QNAME) != false.toString

    // Find actual target
    interpreter.resolveObject(actionElement, resolvedNewEventTargetStaticId) match {
      case xformsEventTarget: XFormsEventTarget =>
        // Execute the dispatch proper
        XFormsDispatchAction.dispatch(
          eventName       = resolvedNewEventName,
          target          = xformsEventTarget,
          bubbles         = newEventBubbles,
          cancelable      = newEventCancelable,
          properties      = XFormsAction.eventProperties(interpreter, actionElement),
          delayOpt        = resolvedDelayOpt,
          showProgress    = showProgress,
          allowDuplicates = allowDuplicates
        )
      case _ =>
        // "If there is a null search result for the target object and the source object is an XForms action such as
        // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
        warn("xf:dispatch: cannot find target, ignoring action", List("target id" -> resolvedNewEventTargetStaticId))
    }
  }
}

object XFormsDispatchAction {

  def dispatch(
    eventName       : String,
    target          : XFormsEventTarget,
    bubbles         : Boolean,
    cancelable      : Boolean,
    properties      : PropertyGetter,
    delayOpt        : Option[Int],
    showProgress    : Boolean,
    allowDuplicates : Boolean
  ): Unit =
    delayOpt match {
      case Some(delay) if delay >= 0 =>
        // Event is dispatched after a delay

        // "10.8 The dispatch Element [...] the specified event is added to the delayed event queue unless an event
        // with the same name and target element already exists on the delayed event queue. The dispatch action has
        // no effect if the event delay is a non-negative integer and the specified event is already in the delayed
        // event queue. [...] Since an element bearing a particular ID may be repeated, the delayed event queue may
        // contain more than one event with the same name and target IDREF. It is the name and the target run-time
        // element that must be unique."

        // TODO: We should either handle properties or throw an exception. We could handle atomic value but not nodes.
        // However, we cannot implement this as is because `PropertyGetter` does not provide a way to enumerate the
        // properties. So we cannot check whether they are empty or whether we support their type.

        XFormsAPI.inScopeContainingDocument.addDelayedEvent(
          eventName         = eventName,
          targetEffectiveId = target.getEffectiveId,
          bubbles           = bubbles,
          cancelable        = cancelable,
          time              = System.currentTimeMillis + delay,
          showProgress      = showProgress,
          allowDuplicates   = allowDuplicates
        )
      case _ =>
        // Event is dispatched immediately

        // "10.8 The dispatch Element [...] If the delay is not specified or if the given value does not conform
        // to xsd:nonNegativeInteger, then the event is dispatched immediately as the result of the dispatch
        // action."

        // Create and dispatch the event including custom properties (AKA context information)
        Dispatch.dispatchEvent(
          createEvent(
            eventName,
            target,
            properties,
            bubbles,
            cancelable
          )
        )
    }
}