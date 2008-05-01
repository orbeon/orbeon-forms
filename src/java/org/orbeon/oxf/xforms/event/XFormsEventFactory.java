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

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for XForms events
 */
public class XFormsEventFactory {

    private static Map nameToClassMap = new HashMap();

    static {
        nameToClassMap.put(XFormsEvents.XFORMS_DOM_ACTIVATE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsDOMActivateEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_COMPUTE_EXCEPTION, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsComputeExceptionEvent(targetObject, contextString, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DELETE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsDeleteEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DESELECT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsDeselectEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_INSERT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsInsertEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_ERROR, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsLinkErrorEvent(targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_EXCEPTION, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsLinkExceptionEvent(targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_BINDING_EXCEPTION, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsBindingExceptionEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REFRESH, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsRefreshEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SELECT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsSelectEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT_ERROR, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsSubmitErrorEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsSubmitEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT_SERIALIZE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsSubmitSerializeEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SUBMIT_DONE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsSubmitDoneEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_READY, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsReadyEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsValueChangeWithFocusChangeEvent(targetObject, otherTargetObject, contextString);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_SUBMIT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsSubmitEvent(targetObject, filesElement);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_LOAD, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsLoadEvent(targetObject, contextString);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_ONLINE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsOnlineEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_OFFLINE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsOfflineEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_MODEL_CONSTRUCT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsModelConstructEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_MODEL_DESTRUCT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsModelDestructEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_RESET, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsResetEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsModelConstructDoneEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_READY, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsReadyEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REBUILD, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsRebuildEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_RECALCULATE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsRecalculateEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REVALIDATE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsRevalidateEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_VALUE_CHANGED, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsValueChangeEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DOM_FOCUS_OUT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsDOMFocusOutEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DOM_FOCUS_IN, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsDOMFocusInEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_VALID, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsValidEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_INVALID, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsInvalidEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_REQUIRED, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsRequiredEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_OPTIONAL, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsOptionalEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_READWRITE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsReadwriteEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_READONLY, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsReadonlyEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_ENABLED, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsEnabledEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_DISABLED, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsDisabledEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_FOCUS, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsFocusEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_HELP, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsHelpEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_HINT, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsHintEvent((XFormsControl) targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SCROLL_FIRST, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsScrollFirstEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_SCROLL_LAST, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsScrollLastEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_EXCEPTION, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsLinkExceptionEvent(targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XFORMS_LINK_ERROR, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XFormsLinkErrorEvent(targetObject, contextString, contextElement, contextThrowable);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_DIALOG_CLOSE, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsDialogCloseEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_DIALOG_OPEN, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsDialogOpenEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_DND, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsDndEvent(targetObject, parameters[0], parameters[1]);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_VALID, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsValidEvent(targetObject);
            }
        });
        nameToClassMap.put(XFormsEvents.XXFORMS_INVALID, new Factory() {
            public XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {
                return new XXFormsInvalidEvent(targetObject);
            }
        });
    }

    public static XFormsEvent createEvent(String newEventName, XFormsEventTarget targetObject) {
        return createEvent(newEventName, targetObject, null, false, true, true, null, null, null, null, null);
    }

    public static XFormsEvent createEvent(String newEventName, XFormsEventTarget targetObject, boolean bubbles, boolean cancelable) {
        return createEvent(newEventName, targetObject, null, true, bubbles, cancelable, null, null, null, null, null);
    }

    public static XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable,
                                           String contextString, Element filesElement, String[] parameters) {

        return createEvent(eventName, targetObject, otherTargetObject, allowCustomEvents, bubbles, cancelable, contextString, null ,null, filesElement, parameters);
    }

    private static XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, boolean allowCustomEvents, boolean bubbles, boolean cancelable,
                                           String contextString, Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters) {

        final Factory factory = (Factory) nameToClassMap.get(eventName);
        if (factory == null) {
            if (!allowCustomEvents) {
                // No custom events are allowed, just throw
                throw new OXFException("Invalid event name: " + eventName);
            } else {
                // Return a custom event
                return new XFormsCustomEvent(eventName, targetObject, bubbles, cancelable);
            }
        } else {
            // Return a built-in event
            return factory.createEvent(eventName, targetObject, otherTargetObject, allowCustomEvents, bubbles, cancelable, contextString, contextElement, contextThrowable, filesElement, parameters);
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
        public abstract XFormsEvent createEvent(String eventName, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject,
                                                boolean allowCustomEvents, boolean bubbles, boolean cancelable, String contextString,
                                                Element contextElement, Throwable contextThrowable, Element filesElement, String[] parameters);
    }

    private XFormsEventFactory() {}
}
