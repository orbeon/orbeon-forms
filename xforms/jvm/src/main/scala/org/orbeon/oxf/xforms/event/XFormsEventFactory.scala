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
package org.orbeon.oxf.xforms.event

import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.xforms.EventNames._

// Factory for XForms events
object XFormsEventFactory {

  private type Factory = (XFormsEventTarget, PropertyGetter) => XFormsEvent

  private val nameToClassMap: Map[String, Factory] = Map(
    // UI events
    DOM_ACTIVATE                -> (new DOMActivateEvent(_, _)),
    XFORMS_HELP                 -> (new XFormsHelpEvent(_, _)),
    XFORMS_HINT                 -> (new XFormsHintEvent(_, _)),
    XFORMS_FOCUS                -> (new XFormsFocusEvent(_, _)),
    XXFORMS_BLUR                -> (new XXFormsBlurEvent(_, _)),
    DOM_FOCUS_IN                -> (new DOMFocusInEvent(_, _)),
    DOM_FOCUS_OUT               -> (new DOMFocusOutEvent(_, _)),
    XFORMS_ENABLED              -> (new XFormsEnabledEvent(_, _)),
    XFORMS_DISABLED             -> (new XFormsDisabledEvent(_, _)),
    XFORMS_READONLY             -> (new XFormsReadonlyEvent(_, _)),
    XFORMS_READWRITE            -> (new XFormsReadwriteEvent(_, _)),
    XFORMS_VALID                -> (new XFormsValidEvent(_, _)),
    XFORMS_INVALID              -> (new XFormsInvalidEvent(_, _)),
    XFORMS_REQUIRED             -> (new XFormsRequiredEvent(_, _)),
    XFORMS_OPTIONAL             -> (new XFormsOptionalEvent(_, _)),
    XFORMS_VALUE_CHANGED        -> (new XFormsValueChangeEvent(_, _)),
    // Other events
    XXFORMS_STATE_RESTORED      -> (new XXFormsStateRestoredEvent(_, _)),
    XXFORMS_BINDING_ERROR       -> (new XXFormsBindingErrorEvent(_, _)),
    XXFORMS_XPATH_ERROR         -> (new XXFormsXPathErrorEvent(_, _)),
    XXFORMS_INSTANCE_INVALIDATE -> (new XXFormsInstanceInvalidate(_, _)),
    XXFormsUploadStart          -> (new XXFormsUploadStartEvent(_, _)),
    XXFormsUploadProgress       -> (new XXFormsUploadProgressEvent(_, _)),
    XXFormsUploadCancel         -> (new XXFormsUploadCancelEvent(_, _)),
    XXFormsUploadDone           -> (new XXFormsUploadDoneEvent(_, _)),
    XXFormsUploadError          -> (new XXFormsUploadErrorEvent(_, _)),
    XFORMS_MODEL_CONSTRUCT_DONE -> (new XFormsModelConstructDoneEvent(_, _)),
    XFORMS_MODEL_CONSTRUCT      -> (new XFormsModelConstructEvent(_, _)),
    XXFORMS_INSTANCES_READY     -> (new XXFormsInstancesReadyEvent(_, _)),
    XFORMS_MODEL_DESTRUCT       -> (new XFormsModelDestructEvent(_, _)),
    XFORMS_READY                -> (new XFormsReadyEvent(_, _)),
    XFORMS_REBUILD              -> (new XFormsRebuildEvent(_, _)),
    XFORMS_REFRESH              -> (new XFormsRefreshEvent(_, _)),
    XFORMS_RESET                -> (new XFormsResetEvent(_, _)),
    XFORMS_REVALIDATE           -> (new XFormsRevalidateEvent(_, _)),
    XFORMS_SCROLL_FIRST         -> (new XFormsScrollFirstEvent(_, _)),
    XFORMS_SCROLL_LAST          -> (new XFormsScrollLastEvent(_, _)),
    XFORMS_SUBMIT               -> (new XFormsSubmitEvent(_, _)),
    XXFORMS_DIALOG_CLOSE        -> (new XXFormsDialogCloseEvent(_, _)),
    XXFORMS_INVALID             -> (new XXFormsInvalidEvent(_, _)),
    XXFORMS_READY               -> (new XXFormsReadyEvent(_, _)),
    XXFORMS_VALID               -> (new XXFormsValidEvent(_, _)),
    XXFORMS_ITERATION_MOVED     -> (new XXFormsIterationMovedEvent(_, _)),
    XXFORMS_REPEAT_ACTIVATE     -> (new XXFormsRepeatActivateEvent(_, _)),
    // Other other events ;)
    KeyPress                    -> (new KeypressEvent(_, _)),
    KeyDown                     -> (new KeydownEvent(_, _)),
    KeyUp                       -> (new KeyupEvent(_, _)),
    XFORMS_DESELECT             -> (new XFormsDeselectEvent(_, _)),
    XFORMS_INSERT               -> (new XFormsInsertEvent(_, _)),
    XFORMS_LINK_ERROR           -> (new XFormsLinkErrorEvent(_, _)),
    XFORMS_LINK_EXCEPTION       -> (new XFormsLinkExceptionEvent(_, _)),
    XFORMS_RECALCULATE          -> (new XFormsRecalculateEvent(_, _)),
    XFORMS_SELECT               -> (new XFormsSelectEvent(_, _)),
    XFORMS_SUBMIT_DONE          -> (new XFormsSubmitDoneEvent(_, _)),
    XFORMS_SUBMIT_ERROR         -> (new XFormsSubmitErrorEvent(_, _)),
    XFORMS_SUBMIT_SERIALIZE     -> (new XFormsSubmitSerializeEvent(_, _)),
    XXFORMS_DIALOG_OPEN         -> (new XXFormsDialogOpenEvent(_, _)),
    XXFORMS_DND                 -> (new XXFormsDndEvent(_, _)),
    XXFORMS_INDEX_CHANGED       -> (new XXFormsIndexChangedEvent(_, _)),
    XXFORMS_LOAD                -> (new XXFormsLoadEvent(_, _)),
    XXFORMS_ACTION_ERROR        -> (new XXFormsActionErrorEvent(_, _)),
    XXFORMS_NODESET_CHANGED     -> (new XXFormsNodesetChangedEvent(_, _)),
    XXFORMS_SETINDEX            -> (new XXFormsSetindexEvent(_, _)),
    XXFORMS_VALUE_CHANGED       -> (new XXFormsValueChangedEvent(_, _)),
    XFORMS_DELETE               -> (new XFormsDeleteEvent(_, _)),
    XXFORMS_REPLACE             -> (new XXFormsReplaceEvent(_, _)),
    XXFormsValue                -> (new XXFormsValueEvent(_, _))
  )

  // Create an event
  def createEvent(
    eventName         : String,
    target            : XFormsEventTarget,
    properties        : PropertyGetter = EmptyGetter,
    bubbles           : Boolean = true,
    cancelable        : Boolean = true
  ): XFormsEvent =
    nameToClassMap.get(eventName) match {
      case Some(factory) => factory(target, properties)
      case None          => new XFormsCustomEvent(eventName, target, properties, bubbles, cancelable)
    }

  // Check whether an event name maps to a built-in event
  def isBuiltInEvent(eventName: String) = nameToClassMap.contains(eventName)
}