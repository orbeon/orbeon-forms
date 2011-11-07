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
package org.orbeon.oxf.xforms.event;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.events.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for XForms events
 */
public class XFormsEventFactory {

    private static Map<String, Factory> nameToClassMap = new HashMap<String, Factory>();

    static {
        nameToClassMap.put(XFormsEvents.DOM_ACTIVATE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new DOMActivateEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DELETE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsDeleteEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DESELECT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsDeselectEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_INSERT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsInsertEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_ERROR, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsLinkErrorEvent(containingDocument, targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_EXCEPTION, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsLinkExceptionEvent(containingDocument, targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REFRESH, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsRefreshEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SELECT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsSelectEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT_ERROR, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsSubmitErrorEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsSubmitEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT_SERIALIZE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsSubmitSerializeEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT_DONE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsSubmitDoneEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_READY, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsReadyEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsValueChangeWithFocusChangeEvent(containingDocument, targetObject, otherTargetObject, contextString);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_LOAD, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsLoadEvent(containingDocument, targetObject, contextString);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_UPLOAD_START, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsUploadStartEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_UPLOAD_CANCEL, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsUploadCancelEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_UPLOAD_DONE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsUploadDoneEvent(containingDocument, targetObject, parameters);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_UPLOAD_PROGRESS, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsUploadProgressEvent(containingDocument, targetObject, parameters);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_MODEL_CONSTRUCT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsModelConstructEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_MODEL_DESTRUCT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsModelDestructEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_RESET, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsResetEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsModelConstructDoneEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_READY, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsReadyEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REBUILD, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsRebuildEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_RECALCULATE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsRecalculateEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REVALIDATE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsRevalidateEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_VALUE_CHANGED, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsValueChangeEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.DOM_FOCUS_OUT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new DOMFocusOutEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.DOM_FOCUS_IN, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new DOMFocusInEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.KEYPRESS, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new KeypressEvent(containingDocument, targetObject, parameters);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_VALID, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsValidEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_INVALID, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsInvalidEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REQUIRED, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsRequiredEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_OPTIONAL, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsOptionalEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_READWRITE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsReadwriteEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_READONLY, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsReadonlyEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_ENABLED, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsEnabledEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DISABLED, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsDisabledEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_FOCUS, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsFocusEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_HELP, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsHelpEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_HINT, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsHintEvent(containingDocument, (XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SCROLL_FIRST, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsScrollFirstEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SCROLL_LAST, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsScrollLastEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_EXCEPTION, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsLinkExceptionEvent(containingDocument, targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_ERROR, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XFormsLinkErrorEvent(containingDocument, targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_DIALOG_CLOSE, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsDialogCloseEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_DIALOG_OPEN, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsDialogOpenEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_DND, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                // NOTE: Allow null parameters so we can dynamically create this event with xforms:dispatch, especially for testing
                return new XXFormsDndEvent(containingDocument, targetObject, parameters);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_VALID, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsValidEvent(containingDocument, targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_INVALID, new Factory() {
            public XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
                return new XXFormsInvalidEvent(containingDocument, targetObject);
            }
        });
    }

    public static XFormsEvent createEvent(XFormsContainingDocument containingDocument, String newEventName, XFormsEventTarget targetObject) {
        return createEvent(containingDocument, newEventName, targetObject, null, false, true, true, null, null, null, null);
    }

    public static XFormsEvent createEvent(XFormsContainingDocument containingDocument, String newEventName, XFormsEventTarget targetObject, boolean bubbles, boolean cancelable) {
        return createEvent(containingDocument, newEventName, targetObject, null, true, bubbles, cancelable, null, null, null, null);
    }

    public static XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable,
                                           String contextString, Map<String, String> parameters) {

        return createEvent(containingDocument, eventName, targetObject, otherTargetObject, allowCustomEvents, bubbles, cancelable, contextString, null, null, parameters);
    }

    private static XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable,
                                           String contextString, Element contextElement, Throwable contextThrowable, Map<String, String> parameters) {
        final Factory factory = nameToClassMap.get(eventName);
        if (factory == null) {
            if (!allowCustomEvents) {
                // No custom events are allowed, just throw
                throw new OXFException("Invalid event name: " + eventName);
            } else {
                // Return a custom event
                return new XFormsCustomEvent(containingDocument, eventName, targetObject, bubbles, cancelable);
            }
        } else {
            // Return a built-in event
            return factory.createEvent(containingDocument, eventName, targetObject, otherTargetObject, allowCustomEvents, bubbles, cancelable, contextString, contextElement, contextThrowable, parameters);
        }
    }

    /**
     * Check whether an event name maps to a built-in event.
     *
     * @param eventName event name to check
     * @return          true if built-in, false otherwise
     */
    public static boolean isBuiltInEvent(String eventName) {
        return nameToClassMap.get(eventName) != null;
    }

    private static abstract class Factory {
        public abstract XFormsEvent createEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject,
                                                boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString,
                                                Element contextElement, Throwable contextThrowable, Map<String, String> parameters);
    }

    private XFormsEventFactory() {}
}
