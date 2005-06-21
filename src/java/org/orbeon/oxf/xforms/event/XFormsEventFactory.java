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

/**
 * Factory for XForms events
 */
public class XFormsEventFactory {

    public static XFormsEvent createEvent(String newEventName, Object targetObject) {
        return createEvent(newEventName, targetObject, null, false, true, true, null, null, null);
    }

    public static XFormsEvent createEvent(String newEventName, Object targetObject, Object otherTargetObject, String contextString, Element contextElement, Throwable contextThrowable) {
        return createEvent(newEventName, targetObject, otherTargetObject, false, true, true, contextString, contextElement, contextThrowable);
    }

    public static XFormsEvent createEvent(String newEventName, Object targetObject, boolean bubbles, boolean cancelable) {
        return createEvent(newEventName, targetObject, null, true, bubbles, cancelable, null, null, null);
    }

    private static XFormsEvent createEvent(String eventName, Object targetObject, Object otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable,
                                          String contextString, Element contextElement, Throwable contextThrowable) {

        // TODO
        // XFORMS_MODEL_DESTRUCT
        // XFORMS_RESET
        // XFORMS_DOM_FOCUS_IN

        // TODO: more efficient way to switch!

        if (eventName.equals(XFormsEvents.XFORMS_DOM_ACTIVATE)) {
            return new XFormsDOMActivateEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_COMPUTE_EXCEPTION)) {
            return new XFormsComputeExceptionEvent(targetObject, contextString, contextThrowable);
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
        } else if (eventName.equals(XFormsEvents.XFORMS_REFRESH)) {
            return new XFormsRefreshEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SELECT)) {
            return new XFormsSelectEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT_ERROR)) {
            return new XFormsSubmitErrorEvent(targetObject, contextString, contextThrowable);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT)) {
            return new XFormsSubmitEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_SUBMIT_DONE)) {
            return new XFormsSubmitDoneEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XXFORMS_INITIALIZE)) {
            return new XXFormsInitializeEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XXFORMS_INITIALIZE_CONTROLS)) {
            return new XXFormsInitializeControlsEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XXFORMS_INITIALIZE_STATE)) {
            return new XXFormsInitializeStateEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
            return new XXFormsValueChangeWithFocusChangeEvent(targetObject, otherTargetObject, contextString);
        } else if (eventName.equals(XFormsEvents.XXFORMS_SUBMIT)) {
            return new XXFormsSubmissionEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_MODEL_CONSTRUCT)) {
            return new XFormsModelConstructEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE)) {
            return new XFormsModelConstructDoneEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_READY)) {
            return new XFormsReady(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_REBUILD)) {
            return new XFormsRebuildEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_RECALCULATE)) {
            return new XFormsRecalculateEvent(targetObject, Boolean.getBoolean(contextString));
        } else if (eventName.equals(XFormsEvents.XFORMS_REVALIDATE)) {
            return new XFormsRevalidateEvent(targetObject, Boolean.getBoolean(contextString));
        } else if (eventName.equals(XFormsEvents.XFORMS_VALUE_CHANGED)) {
            return new XFormsValueChangeEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_DOM_FOCUS_OUT)) {
            return new XFormsDOMFocusOutEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_VALID)) {
            return new XFormsValidEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_INVALID)) {
            return new XFormsInvalidEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_REQUIRED)) {
            return new XFormsRequiredEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_OPTIONAL)) {
            return new XFormsOptionalEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_READWRITE)) {
            return new XFormsReadwriteEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_READONLY)) {
            return new XFormsReadonlyEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_ENABLED)) {
            return new XFormsEnabledEvent(targetObject);
        } else if (eventName.equals(XFormsEvents.XFORMS_DISABLED)) {
            return new XFormsDisabledEvent(targetObject);
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
