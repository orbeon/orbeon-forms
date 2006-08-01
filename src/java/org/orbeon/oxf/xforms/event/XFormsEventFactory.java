/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.events.*;

/**
 * Factory for XForms events
 */
public class XFormsEventFactory {

    public static XFormsEvent createEvent(String newEventName, XFormsEventTarget targetObject) {
        return createEvent(newEventName, targetObject, null, false, true, true, null, null, null, null);
    }

    public static XFormsEvent createEvent(String newEventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement) {
        return createEvent(newEventName, targetObject, otherTargetObject, false, true, true, contextString, contextElement, contextThrowable, filesElement);
    }

    public static XFormsEvent createEvent(String newEventName, XFormsEventTarget targetObject, boolean bubbles, boolean cancelable) {
        return createEvent(newEventName, targetObject, null, true, bubbles, cancelable, null, null, null, null);
    }

    private static XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable,
                                           String contextString, Element contextElement, Throwable contextThrowable, Element filesElement) {

        // TODO: more efficient way to switch!
        if (eventName.equals(XFormsEvents.XFORMS_DOM_ACTIVATE)) {
            return new XFormsDOMActivateEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_COMPUTE_EXCEPTION)) {
            return new org.orbeon.oxf.xforms.event.events.XFormsComputeExceptionEvent(targetObject, contextString, contextThrowable);
        } else if (eventName.equals(XFormsEvents.XFORMS_DELETE)) {
            return new XFormsDeleteEvent(targetObject, contextString);
        } else if (eventName.equals(XFormsEvents.XFORMS_DESELECT)) {
            return new XFormsDeselectEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_INSERT)) {
            return new XFormsInsertEvent(targetObject, contextString);
        } else if (eventName.equals(XFormsEvents.XFORMS_LINK_ERROR)) {
            return new XFormsLinkErrorEvent(targetObject, contextString, contextElement, contextThrowable);
        } else if (eventName.equals(XFormsEvents.XFORMS_LINK_EXCEPTION)) {
            return new XFormsLinkExceptionEvent(targetObject, contextString, contextElement, contextThrowable);
        } else if (eventName.equals(XFormsEvents.XFORMS_BINDING_EXCEPTION)) {
            return new XFormsBindingExceptionEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_REFRESH)) {
            return new XFormsRefreshEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SELECT)) {
            return new XFormsSelectEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT_ERROR)) {
            return new XFormsSubmitErrorEvent(targetObject, contextString);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT)) {
            return new XFormsSubmitEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT_SERIALIZE)) {
            return new XFormsSubmitSerializeEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT_DONE)) {
            return new XFormsSubmitDoneEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
            return new XXFormsValueChangeWithFocusChangeEvent(targetObject, otherTargetObject, contextString);
        } else if (eventName.equals(XFormsEvents.XXFORMS_SUBMIT)) {
            return new XXFormsSubmissionEvent(targetObject, filesElement);
        } else if (eventName.equals(XFormsEvents.XXFORMS_LOAD)) {
            return new XXFormsLoadEvent(targetObject, contextString);
        } else if (eventName.equals(XFormsEvents.XFORMS_MODEL_CONSTRUCT)) {
            return new XFormsModelConstructEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_MODEL_DESTRUCT)) {
            return new XFormsModelDestructEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_RESET)) {
            return new XFormsResetEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE)) {
            return new XFormsModelConstructDoneEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_READY)) {
            return new XFormsReadyEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_REBUILD)) {
            return new XFormsRebuildEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_RECALCULATE)) {
            return new XFormsRecalculateEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_REVALIDATE)) {
            return new XFormsRevalidateEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_VALUE_CHANGED)) {
            return new XFormsValueChangeEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_DOM_FOCUS_OUT)) {
            return new XFormsDOMFocusOutEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_DOM_FOCUS_IN)) {
            return new XFormsDOMFocusInEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_VALID)) {
            return new XFormsValidEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_INVALID)) {
            return new XFormsInvalidEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_REQUIRED)) {
            return new XFormsRequiredEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_OPTIONAL)) {
            return new XFormsOptionalEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_READWRITE)) {
            return new XFormsReadwriteEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_READONLY)) {
            return new XFormsReadonlyEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_ENABLED)) {
            return new XFormsEnabledEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_DISABLED)) {
            return new XFormsDisabledEvent((XFormsControl) targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_FOCUS)) {
            return new XFormsFocusEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SCROLL_FIRST)) {
            return new XFormsScrollFirstEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SCROLL_LAST)) {
            return new XFormsScrollLastEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_LINK_EXCEPTION)) {
            return new XFormsLinkExceptionEvent(targetObject, contextString, contextElement, contextThrowable);
        } else if (eventName.equals(XFormsEvents.XFORMS_LINK_ERROR)) {
            return new XFormsLinkErrorEvent(targetObject, contextString, contextElement, contextThrowable);
        } else if (allowCustomEvents) {
            return new XFormsCustomEvent(eventName, targetObject, bubbles, cancelable);
        } else {
            throw new OXFException("Event factory could not find event with name: " + eventName);
        }
    }

    private XFormsEventFactory() {}
}
