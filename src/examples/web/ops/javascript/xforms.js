/**
 *  Copyright (C) 2006 Orbeon, Inc.
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

/**
 * Parameters
 */
var XFORMS_DELAY_BEFORE_INCREMENTAL_REQUEST_IN_MS = 500;
var XFORMS_DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_IN_MS = 2000;
var XFORMS_DELAY_BEFORE_ANY_REQUEST_IN_MS = 10;
var XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS = 500;
var XFORMS_DEBUG_WINDOW_HEIGHT = 600;
var XFORMS_DEBUG_WINDOW_WIDTH = 300;

/**
 * Constants
 */
var XFORMS_SEPARATOR_1 = "\xB7";
var XFORMS_SEPARATOR_2 = "-";
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var BASE_URL = null;
var XFORMS_SERVER_URL = null;
var PATH_TO_JAVASCRIPT = "/ops/javascript/xforms.js";
var XFORMS_IS_GECKO = navigator.userAgent.toLowerCase().indexOf("gecko") != -1;
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var ATTRIBUTE_TYPE = document.createAttribute("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;
var XFORMS_REGEXP_CR = new RegExp("\\r", "g");
var XFORMS_REGEXP_SINGLE_QUOTE = new RegExp("'", "g");
var XFORMS_REGEXP_OPEN_ANGLE = new RegExp("<", "g");
var XFORMS_REGEXP_AMPERSAND = new RegExp("&", "g");
var XFORMS_WIDE_TEXTAREA_MIN_ROWS = 5;

/* * * * * * Utility functions * * * * * */

ORBEON = {};
ORBEON.util = {};
ORBEON.xforms = {};

/**
 * Global constants and variable
 */
ORBEON.xforms.Globals = {
    // Booleans used for browser detection
    isRenderingEnginePresto: navigator.userAgent.toLowerCase().indexOf("opera") != -1,    // Opera
    isRenderingEngineWebCore: navigator.userAgent.toLowerCase().indexOf("safari") != -1,  // Safari
    isRenderingEngineWebCore13: navigator.userAgent.indexOf("AppleWebKit/312") != -1,     // Safari 1.3
    isRenderingEngineTridend: navigator.userAgent.toLowerCase().indexOf("msie") != -1     // Internet Explorer
        && navigator.userAgent.toLowerCase().indexOf("opera") == -1,

    eventQueue: [],                      // Events to be sent to the server
    eventsFirstEventTime: 0,             // Time when the first event in the queue was added
    requestForm: null,                   // HTML for the request currently in progress
    requestInProgress: false,            // Indicates wether an Ajax request is currently in process
    lastRequestIsError: false,           // Whether the last XHR request returned an error
    executeEventFunctionQueued: 0,       // Number of ORBEON.xforms.Server.executeNextRequest waiting to be executed
    maskFocusEvents: false,              // Avoid catching focus event when we do it because the server told us to
    previousDOMFocusOut: null,           // We only send a focus out when we receive a focus in, or another focus out
    htmlAreaNames: [],                   // Names of the FCK editors, which we need to reenable them on Firefox
    repeatTreeChildToParent: [],         // Describes the repeat hierarchy
    repeatIndexes: [],                   // The current index for each repeat
    repeatTreeParentToAllChildren: {},   // Map from parent to array with children, used when highlight changes
    overlayManager: null,                // YUI overlay manager used to close opened menu when user clicks on document
    inputCalendarCreated: {},            // Maps input id to true when the calendar has been created for that input
    inputCalendarOnclick: {},            // Maps input id to the JSCalendar function that displays the calendar
    tooltipLibraryInitialized: false,
    changedIdsRequest: {},               // Id of controls that have been touched by user since the last response was received
    serverValue: {},                     // Values on controls known to the server
    autoCompleteLastKeyCode: {},         // Stores the last key entered for each auto-complete field
    autoCompleteOpen: {},
    loadingOtherPage: false,             // Flag set when loading other page that revents the loading indicator to disappear
    activeControl: null,                 // The currently active control, used to disable hint
    autosizeTextareas: [],               // Ids of the autosize textareas on the page
    fckEditorLoading: false,             // True if  a FCK editor is currently loading
    fckEditorsToLoad: [],                // Queue of FCK editor to load
    dialogs: {},                         // Map for dialogs: id -> YUI dialog object
    debugDiv: null,                      // Points to the div when debug messages are displayed
    debugLastTime: new Date().getTime(), // Timestamp when the last debug message was printed
    pageLoadedRegistered: false,         // If the page loaded listener has been registered already, to avoid running it more than once

    // Data relative to a form is stored in an array indexed by the position of the form on the page.
    // Use ORBEON.util.Dom.getFormIndex(form) to get the index of a given form on the page
    formLoadingLoading: [],              // HTML element with the markup displayed when a request is in process
    formLoadingError: [],                // HTML element with the markup displayed when there is an error
    formLoadingNone: [],                 // HTML element with the markup displayed when nothing is displayed
    formStaticState: [],                 // State that does not change for the life of the page
    formDynamicState: [],                // State that changes at every request
    formClientState: []                  // Store for information we want to keep when the page reloaded
};

/**
 * The IE version of those methods does not store anything in the
 * elements as this has some negative side effects like IE reloading
 * background images set with CSS on the element.
 */
ORBEON.util.IEDom = {
    /**
     * Optimized version of YAHOO.util.Dom.hasClass(element, className).
     */
    hasClass: function(element, className) {
        if (element.className == className) {
            // Trivial true case
            return true;
        } else {
            var classes = element.className + XFORMS_SEPARATOR_1;
            if (classes.indexOf(className + " ") == 0) {
                // Starts with the class we look for
                return true;
            }
            if (classes.indexOf(" " + className + " ") != -1) {
                // The class we look for is in the middle
                return true;
            }
            if (classes.indexOf(" " + className + XFORMS_SEPARATOR_1) != -1) {
                // The class we look for is in the end
                return true;
            }
            return false;
        }
    },

    /**
     * Optimized version of YAHOO.util.Dom.addClass(element, className).
     */
    addClass: function(element, className) {
        if (!this.hasClass(element, className))
            element.className = element.className.length == 0 ? className
                    : (element.className + " " + className);
    },

    /**
     * Optimized version of YAHOO.util.Dom.removeClass(element, className).
     */
    removeClass: function(element, className) {
        if (this.hasClass(element, className)) {
            var classes = element.className.split(" ");
            var newClassName = "";
            for (var i = 0; i < classes.length; i++) {
                if (classes[i] != className) {
                    if (newClassName.length > 0) newClassName += " ";
                    newClassName += classes[i];
                }
            }
            element.className = newClassName;
        }
    }
};

/**
 * General purpose methods on string
 */
ORBEON.util.String = {
    replace: function(text, placeholder, replacement) {
        return text.replace(new RegExp(placeholder, "g"), replacement);
    }
}

/**
 * The hasClass, addClass and removeClass methods use a cache of the
 * classes for a give element for quick lookup. After having parsed the
 * className a first time we store that information in the orbeonClasses
 * map on the given element.
 */
ORBEON.util.MozDom = {
    /**
     * Changes the className on the element based on the information stored in
     * _elementToClasses.
     *
     * @private
     */
    _regenerateClassName: function(element) {
        var newClassName = "";
        for (var existingClassName in element.orbeonClasses) {
            if (element.orbeonClasses[existingClassName]) {
                if (newClassName.length > 0)
                    newClassName += " ";
                newClassName += existingClassName;
            }
        }
        element.className = newClassName;
    },

    /**
     * Optimized version of YAHOO.util.Dom.hasClass(element, className).
     */
    hasClass: function(element, className) {
        if (!element.orbeonClasses) {
            element.orbeonClasses = {};
            var classes = element.className.split(" ");
            for (var i = 0; i < classes.length; i++)
                element.orbeonClasses[classes[i]] = true;
        }
        return element.orbeonClasses[className] == true;
    },

    /**
     * Optimized version of YAHOO.util.Dom.addClass(element, className).
     */
    addClass: function(element, className) {
        if (!this.hasClass(element, className)) {
            element.orbeonClasses[className] = true;
            this._regenerateClassName(element);
        }
    },

    /**
     * Optimized version of YAHOO.util.Dom.removeClass(element, className).
     */
    removeClass: function(element, className) {
        if (this.hasClass(element, className)) {
            element.orbeonClasses[className] = false;
            this._regenerateClassName(element);
        }
    }
};

/**
 *  Utilities to deal with the DOM that supplement what is provided by YAHOO.util.Dom.
 */
ORBEON.util.Dom = {

    ELEMENT_TYPE: 1,

    isElement: function(node) {
        return node.nodeType == this.ELEMENT_TYPE;
    },

    getStringValue: function(element) {
        var result = "";
        for (var i = 0; i < element.childNodes.length; i++) {
            var child = element.childNodes[i];
            if (child.nodeType == TEXT_TYPE)
                result += child.nodeValue;
        }
        return result;
    },

    setStringValue: function(element, text) {
        // Remove content
        while (element.childNodes.length > 0)
            element.removeChild(element.firstChild);
        // Add specified text
        var textNode = element.ownerDocument.createTextNode(text);
        element.appendChild(textNode);
    },

    /**
     * Return null when the attribute is not there.
     */
    getAttribute: function(element, name) {
        if (ORBEON.xforms.Globals.isRenderingEngineTridend) {
            // IE incorrectly already return null when the attribute is not there,
            // but this happens to be what we want to do here
            return element.getAttribute(name);
        } else {
            // Other browsers that follow the spec return an empty string when the attribute is not there,
            // so we use hasAttribute() which is not implemented by IE to detect that case.
            if (element.hasAttribute(name)) {
                if (ORBEON.xforms.Globals.isRenderingEngineWebCore) {
                    return ORBEON.util.String.replace(element.getAttribute(name), "&#38;", "&");
                } else {
                    return element.getAttribute(name);
                }
            } else {
                return null;
            }
        }
    },

    getChildElement: function(parent, position) {
        for (var i = 0; i < parent.childNodes.length; i++) {
            var child = parent.childNodes[i];
            if (ORBEON.util.Dom.isElement(child)) {
                if (position == 0) return child;
                position--;
            }
        }
        return null;
    },

    getFormIndex: function(form) {
        for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
            var candidateForm = document.forms[formIndex];
            if (form == candidateForm)
                return formIndex;
        }
        return null;
    }
};

(function () {
    var methodsFrom = ORBEON.xforms.Globals.isRenderingEngineTridend ? ORBEON.util.IEDom : ORBEON.util.MozDom;
    for (var method in methodsFrom)
        ORBEON.util.Dom[method] = methodsFrom[method];
}());

/**
 * This object contains function generally designed to be called from JavaScript code
 * embedded in forms.
 */
ORBEON.xforms.Document = {

    /**
     * Reference: http://www.w3.org/TR/xforms/slice10.html#action-dispatch
     */
    dispatchEvent: function(targetId, eventName, form, bubbles, cancelable, incremental) {

        // Use the first XForms form on the page when no form is provided
        if (form == undefined) {
            for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
                var candidateForm = document.forms[formIndex];
                if (ORBEON.util.Dom.hasClass(candidateForm, "xforms-form")) {
                    form = candidateForm;
                    break;
                }
            }
        }

        // Create event and fire
        var event = new ORBEON.xforms.Server.Event(form, targetId, null, null, eventName, bubbles, cancelable);
        ORBEON.xforms.Server.fireEvents([event], incremental == undefined ? false : incremental);
    },

    /**
     * Returns the value of an XForms control.
     *
     * @param {String} controlId    Id of the control
     */
    getValue: function(controlId) {
        var control = document.getElementById(controlId);
        return ORBEON.xforms.Controls.getCurrentValue(control);
    },

    /**
     * Set the value of an XForms control.
     *
     * @param {String} controlId    Id of the control
     * @param {String} newValue     New value for the control
     */
    setValue: function(controlId, newValue) {
        var control = document.getElementById(controlId);
        xformsFireEvents(new Array(xformsCreateEventArray
                (control, "xxforms-value-change-with-focus-change", String(newValue), null)), false);
    }
};

ORBEON.xforms.Controls = {

    // Returns MIP for a given control
    isRelevant: function (control) { return !ORBEON.util.Dom.hasClass(control, "xforms-disabled"); },
    isReadonly: function (control) { return  ORBEON.util.Dom.hasClass(control, "xforms-readonly"); },
    isRequired: function (control) { return  ORBEON.util.Dom.hasClass(control, "xforms-required"); },
    isValid:    function (control) { return !ORBEON.util.Dom.hasClass(control, "xforms-invalid"); },

    getForm: function(control) {
        if (typeof control.form == "undefined") {
            // There is a span around the control go through parents until we find the form element
            var candidateForm = control;
            while (candidateForm.tagName.toLowerCase() != "form")
                candidateForm = candidateForm.parentNode;
            return candidateForm;
        } else {
            // We have directly a form control
            return control.form;
        }
    },

    getCurrentValue: function(control) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-input")) {
            return ORBEON.util.Dom.getChildElement(control, 1).value;
        } if (ORBEON.util.Dom.hasClass(control, "xforms-select1-open")) {
            return ORBEON.util.Dom.getChildElement(control, 0).value;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-full")) {
            var inputs = control.getElementsByTagName("input");
            var spanValue = "";
            for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
                var input = inputs[inputIndex];
                if (input.checked) {
                    if (spanValue != "") spanValue += " ";
                    spanValue += input.value;
                }
            }
            return spanValue;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-compact")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-minimal")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-compact")) {
            var options = control.options;
            var selectValue = "";
            for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                var option = options[optionIndex];
                if (option.selected) {
                    if (selectValue != "") selectValue += " ";
                    selectValue += option.value;
                }
            }
            return selectValue;
        } else {
            return control.value;
        }
    },

    _getControlLabel: function(control, className) {
        var candidate = control;
        while (true) {
            if (candidate == null) break;
            if (ORBEON.util.Dom.isElement(candidate)
                    && ORBEON.util.Dom.hasClass(candidate, className)) break;
            candidate = className == "xforms-label" ? candidate.previousSibling : candidate.nextSibling;
        }
        return candidate;
    },

    _setMessage: function(control, className, message) {
        var label = ORBEON.xforms.Controls._getControlLabel(control, className);
        if (label != null) {
            ORBEON.util.Dom.setStringValue(label, message);
            var helpImage = ORBEON.xforms.Controls._getControlLabel(control, "xforms-help-image");
            if (message == "") {
                ORBEON.util.Dom.addClass(label, "xforms-disabled");
                // If this is the help label, also disable help image
                if (className == "xforms-help")
                    ORBEON.util.Dom.addClass(helpImage, "xforms-disabled");
            } else {
                ORBEON.util.Dom.removeClass(label, "xforms-disabled");
                // If this is the help label, also enable the help image
                if (className == "xforms-help")
                    ORBEON.util.Dom.removeClass(helpImage, "xforms-disabled");
            }
        }
    },

    setLabelMessage: function(control, message) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-trigger")
                || ORBEON.util.Dom.hasClass(control, "xforms-submit")) {
            if (control.tagName.toLowerCase() == "input") {
                // Image
                control.title = message;
            } else {
                // Button and link
                ORBEON.util.Dom.setStringValue(control, message);
            }
        } else {
            ORBEON.xforms.Controls._setMessage(control, "xforms-label", message);
        }
    },

    getHelpMessage: function(control) {
        var helpElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-help");
        return helpElement == null ? "" : ORBEON.util.Dom.getStringValue(helpElement);
    },

    setHelpMessage: function(control, message) {
        ORBEON.xforms.Controls._setMessage(control, "xforms-help", message);
    },

    setValid: function(control, isValid) {
        // Update class xforms-invalid on the control
        if (isValid) ORBEON.util.Dom.removeClass(control, "xforms-invalid");
        else ORBEON.util.Dom.addClass(control, "xforms-invalid");

        // Update class on alert label
        var alertElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert");
        if (alertElement != null) { // Some controls don't have validity indicator
            if (isValid) {
                ORBEON.util.Dom.removeClass(alertElement, "xforms-alert-active");
                ORBEON.util.Dom.addClass(alertElement, "xforms-alert-inactive");
            } else {
                ORBEON.util.Dom.removeClass(alertElement, "xforms-alert-inactive");
                ORBEON.util.Dom.addClass(alertElement, "xforms-alert-active");
            }
        }
    },

    setRelevant: function(control, isRelevant) {
        var elementsToUpdate = [ control,
            ORBEON.xforms.Controls._getControlLabel(control, "xforms-label"),
            ORBEON.xforms.Controls._getControlLabel(control, "xforms-hint"),
            ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert")
        ];
        // Also show help if there is a message attached
        if (isRelevant && ORBEON.xforms.Controls.getHelpMessage(control) != "") {
            elementsToUpdate.push(ORBEON.xforms.Controls._getControlLabel(control, "xforms-help"));
            elementsToUpdate.push(ORBEON.xforms.Controls._getControlLabel(control, "xforms-help-image"));
        }
        for (var elementIndex = 0; elementIndex < elementsToUpdate.length; elementIndex++) {
            var element = elementsToUpdate[elementIndex];
            if (element != null) {
                if (isRelevant) ORBEON.util.Dom.removeClass(element, "xforms-disabled");
                else ORBEON.util.Dom.addClass(element, "xforms-disabled");
            }
        }
    },

    getAlertMessage: function(control) {
        var alertElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert");
        return ORBEON.util.Dom.getStringValue(alertElement);
    },

    setAlertMessage: function(control, message) {
        ORBEON.xforms.Controls._setMessage(control, "xforms-alert", message);
    },

    setHintMessage: function(control, message) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-trigger")
                || ORBEON.util.Dom.hasClass(control, "xforms-trigger")) {
            control.title = message;
        } else {
            ORBEON.xforms.Controls._setMessage(control, "xforms-hint", message);
        }
    },

    /**
     * Sets focus to the specified control. This is called by the JavaScript code
     * generated by the server, which we invoke on page load.
     */
    setFocus: function(controlId) {
        var control = document.getElementById(controlId);
        if (ORBEON.util.Dom.hasClass(control, "xforms-input")) {
            ORBEON.util.Dom.getChildElement(control, 1).focus();
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")) {
            ORBEON.util.Dom.getChildElement(ORBEON.util.Dom.getChildElement(control, 0), 0).focus();
        } else if (typeof control.focus != "undefined") {
            control.focus();
        }
    },

    /**
     * Update the xforms-required-empty class as necessary.
     */
    updateRequiredEmpty: function(control) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-required")) {
            if (ORBEON.xforms.Controls.getCurrentValue(control) == "") {
                ORBEON.util.Dom.addClass(control, "xforms-required-empty");
                ORBEON.util.Dom.removeClass(control, "xforms-required-filled");
            } else {
                ORBEON.util.Dom.addClass(control, "xforms-required-filled");
                ORBEON.util.Dom.removeClass(control, "xforms-required-empty");
            }
        } else {
            ORBEON.util.Dom.removeClass(control, "xforms-required-filled");
            ORBEON.util.Dom.removeClass(control, "xforms-required-empty");
        }
    },

    hintActive: function(control, active) {
        var hintLabel = ORBEON.xforms.Events._findHint(control);
        if (hintLabel != null) {
            if (active) {
                // Disable previously active control
                if (ORBEON.xforms.Globals.activeControl != null)
                    ORBEON.xforms.Controls.hintActive(ORBEON.xforms.Globals.activeControl, false);
                ORBEON.xforms.Globals.activeControl = control;
                ORBEON.util.Dom.removeClass(hintLabel, "xforms-hint");
                ORBEON.util.Dom.addClass(hintLabel, "xforms-hint-active");
            } else {
                ORBEON.xforms.Globals.activeControl = null;
                ORBEON.util.Dom.addClass(hintLabel, "xforms-hint");
                ORBEON.util.Dom.removeClass(hintLabel, "xforms-hint-active");
            }
        }
    },

    autosizeTextarea: function(textarea) {
        var scrollHeight = textarea.scrollHeight;
        var clientHeight = textarea.clientHeight;
        var rowHeight = clientHeight / textarea.rows;
        var linesAdded = 0;

        if (scrollHeight > clientHeight) {
            // Grow
            while (scrollHeight > clientHeight) {
                textarea.rows = textarea.rows + 1;
                clientHeight = textarea.clientHeight;
                linesAdded++;

            }
        } else if (scrollHeight < clientHeight) {
            // Shrink
            while (textarea.rows > XFORMS_WIDE_TEXTAREA_MIN_ROWS && scrollHeight < clientHeight - rowHeight) {
                textarea.rows = textarea.rows - 1;
                clientHeight = textarea.clientHeight;
                linesAdded--;
            }
        }
    },

    updateHTMLAreaClasses: function(textarea) {
        var iframe = textarea.previousSibling;
        while (iframe.nodeType != ORBEON.util.Dom.ELEMENT_TYPE) iframe = textarea.previousSibling;
        iframe.className = textarea.className;
    }
};

ORBEON.xforms.Events = {

    /**
     * Look for a hint label that follows the control
     */
    _findHint: function(control) {
        var hintLabel = control;
        while (true) {
            hintLabel = hintLabel.nextSibling;
            // No, we can't found a hint label
            if (hintLabel == null) return null;
            // Yes, we found the hint label
            if (ORBEON.util.Dom.isElement(hintLabel)
                    && (ORBEON.util.Dom.hasClass(hintLabel, "xforms-hint")
                        || ORBEON.util.Dom.hasClass(hintLabel, "xforms-hint-active"))
                    && hintLabel.htmlFor == control.id)
                return hintLabel;
        }
    },

    /**
     * Look for the first parent control which is an XForms control
     */
    _findParentXFormsControl: function(element) {
        while (true) {
            if (!element) return null; // No more parent, stop search
            if (element.xformsElement) {
                // HTML area on Gecko: event target is the document, return the textarea
                return element.xformsElement;
            } else if (element.ownerDocument && element.ownerDocument.xformsElement) {
                // HTML area on IS: event target is the body of the document, return the textarea
                return element.ownerDocument.xformsElement;
            } else if (element.className != null) {
                if (ORBEON.util.Dom.hasClass(element, "xforms-control")
                        || ORBEON.util.Dom.hasClass(element, "xforms-help-image")
                        || ORBEON.util.Dom.hasClass(element, "xforms-alert")) {
                    // We found our XForms element
                    return element;
                }
            }
            // Go to parent and continue search
            element = element.parentNode;
        }
    },

    _keyCodeModifiesField: function(c) {
        return c != 9 && c != 13 && c != 16 && c != 17 && c != 18;
    },

    focus: function(event) {
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (target != null) {
                // Activate hint if we found one
                ORBEON.xforms.Controls.hintActive(target, true);
                // Store initial value of control
                if (typeof ORBEON.xforms.Globals.serverValue[target.id] == "undefined")
                    ORBEON.xforms.Globals.serverValue[target.id] = target.value;
                // Send focus events
                if (ORBEON.xforms.Globals.previousDOMFocusOut) {
                    if (ORBEON.xforms.Globals.previousDOMFocusOut != target) {
                        var events = new Array();
                        events.push(xformsCreateEventArray
                            (ORBEON.xforms.Globals.previousDOMFocusOut, "DOMFocusOut", null));
                        events.push(xformsCreateEventArray(target, "DOMFocusIn", null));
                        xformsFireEvents(events, true);
                    }
                    ORBEON.xforms.Globals.previousDOMFocusOut = null;
                } else {
                    if (document.xformsPreviousDOMFocusIn != target) {
                        xformsFireEvents(new Array(xformsCreateEventArray(target, "DOMFocusIn", null)), true);
                    }
                }
            }
            document.xformsPreviousDOMFocusIn = target;
        } else {
            ORBEON.xforms.Globals.maskFocusEvents = false;
        }
    },

    blur: function(event) {
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (target != null) {
                // De-activate hint if we found one
                ORBEON.xforms.Controls.hintActive(target, false);
                // This is an event for an XForms control
                ORBEON.xforms.Globals.previousDOMFocusOut = target;
                // HTML area does not throw value change event, so we throw it on blur
                if (ORBEON.util.Dom.hasClass(target, "xforms-textarea")
                        && ORBEON.util.Dom.hasClass(target, "xforms-mediatype-text-html")) {
                    var editorInstance = FCKeditorAPI.GetInstance(target.name);
                    target.value = editorInstance.GetXHTML();
                    xformsValueChanged(target, null);
                }
            }
        }
    },

    change: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null && !ORBEON.util.Dom.hasClass(target, "xforms-upload")) {
            // When we move out from a field, we don't receive the keyup events corresponding to keypress
            // for that field (go figure!). Se we reset here the count for keypress without keyup for that field.
            if (ORBEON.xforms.Globals.changedIdsRequest[target.id] != null)
                ORBEON.xforms.Globals.changedIdsRequest[target.id] = 0;

            // For select1 list, make sure we have exactly one value selected
            if (ORBEON.util.Dom.hasClass(target, "xforms-select1-appearance-compact")) {
                if (target.value == "") {
                    // Stop end-user from deselecting last selected value
                    target.options[0].selected = true;
                    //target.value = target.options[0].value;
                } else {
                    // Unselect options other than the first one
                    var foundSelected = false;
                    for (var optionIndex = 0; optionIndex < target.options.length; optionIndex++) {
                        var option = target.options[optionIndex];
                        if (option.selected) {
                            if (foundSelected) option.selected = false;
                            else foundSelected = true;
                        }
                    }
                }
            }

            // Fire change event
            xformsFireEvents([xformsCreateEventArray(target, "xxforms-value-change-with-focus-change",
                ORBEON.xforms.Controls.getCurrentValue(target))], false);
        }
    },

    keydown: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Remember that the user is editing this field, so don't overwrite when we receive an event from the server
            // Ignore some key codes that won't modify the value of the field
            if (ORBEON.xforms.Events._keyCodeModifiesField(event.keyCode))
                ORBEON.xforms.Globals.changedIdsRequest[target.id] =
                    ORBEON.xforms.Globals.changedIdsRequest[target.id] == null ? 1
                    : ORBEON.xforms.Globals.changedIdsRequest[target.id] + 1;
        }
    },

    keypress: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Input field and auto-complete: trigger DOMActive when when enter is pressed
            if (ORBEON.util.Dom.hasClass(target, "xforms-select1-open")
                    || ORBEON.util.Dom.hasClass(target, "xforms-input")) {
                if (event.keyCode == 10 || event.keyCode == 13) {
                    // Prevent default handling of enter, which might be equivalent as a click on some trigger in the form
                    YAHOO.util.Event.preventDefault(event);
                    // Send a value change and DOM activate
                    var events = [
                        xformsCreateEventArray(target, "xxforms-value-change-with-focus-change",
                            ORBEON.xforms.Controls.getCurrentValue(target)),
                        xformsCreateEventArray(target, "DOMActivate", null)
                    ];
                    xformsFireEvents(events, false);
                }
            }
        }
    },

    keyup: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Save keycode
            if (ORBEON.util.Dom.hasClass(target, "xforms-select1-open"))
                ORBEON.xforms.Globals.autoCompleteLastKeyCode[target.id] = event.keyCode;
            // Remember we have received the keyup for this element
            if (ORBEON.xforms.Events._keyCodeModifiesField(event.keyCode))
                ORBEON.xforms.Globals.changedIdsRequest[target.id]--;
            // Incremental control: treat keypress as a value change event
            if (ORBEON.util.Dom.hasClass(target, "xforms-incremental")) {
                xformsFireEvents([xformsCreateEventArray(target, "xxforms-value-change-with-focus-change",
                    ORBEON.xforms.Controls.getCurrentValue(target))], true);
            }

            // If value is required, add/remove xforms-required-empty appropriately
            ORBEON.xforms.Controls.updateRequiredEmpty(target);

            // Resize wide text area
            if (ORBEON.util.Dom.hasClass(target, "xforms-textarea-appearance-xxforms-autosize")) {
                ORBEON.xforms.Controls.autosizeTextarea(target);
            }
        }
    },

    mousedown: function(event) {
        // Close open menus of there are any
        if (ORBEON.xforms.Globals.overlayManager != null)
            ORBEON.xforms.Globals.overlayManager.hideAll();
    },

    resize: function(event) {
        for (var i = 0; i < ORBEON.xforms.Globals.autosizeTextareas.length; i++) {
            var textarea = ORBEON.xforms.Globals.autosizeTextareas[i];
            ORBEON.xforms.Controls.autosizeTextarea(textarea);
        }
    },


    _showToolTip: function(event, label, type, message) {
        // Initialize tooltip library
        if (!ORBEON.xforms.Globals.tooltipLibraryInitialized) {
            ORBEON.xforms.Globals.tooltipLibraryInitialized = true;
            tt_init();
        }

        // Figure out if we need to create a div
        // When there is an existing div, check if it has the same message
        var needToCreateDiv;
        var tooltipDivId = label.htmlFor + "-" + type;
        var existingDiv = document.getElementById(tooltipDivId);
        if (existingDiv != null) {
            if (existingDiv.message == message) {
                needToCreateDiv = false;
            } else {
                needToCreateDiv = true;
                existingDiv.parentNode.removeChild(existingDiv);
            }
        } else {
            needToCreateDiv = true;
        }

        // Create new div when necessary
        if (needToCreateDiv) {
            var divHTML = tt_Htm(this, tooltipDivId, message);
            var container = document.createElement("DIV");
            container.innerHTML = divHTML;
            var newDiv = container.firstChild;
            newDiv.message = message;
            document.body.appendChild(newDiv);
        }

        // Show the help div
        tt_Show(event, tooltipDivId, false, 0, false, false,
            ttOffsetX, ttOffsetY, false, false, ttTemp);
    },

    mouseover: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            if (ORBEON.util.Dom.hasClass(target, "xforms-help-image")) {
                // Show help tool-tip
                var label = target.nextSibling;
                while (!ORBEON.util.Dom.isElement(label)) label = target.nextSibling;
                var control = document.getElementById(label.htmlFor);
                ORBEON.xforms.Events._showToolTip(event, label, "xforms-help", ORBEON.xforms.Controls.getHelpMessage(control));
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-alert-active")) {
                var control = document.getElementById(target.htmlFor);
                var message = ORBEON.xforms.Controls.getAlertMessage(control);
                if (message != "") {
                    // Show alert tool-tip
                    ORBEON.xforms.Events._showToolTip(event, target, "xforms-alert", ORBEON.xforms.Controls.getAlertMessage(control));
                }
            }
        }
    },

    mouseout: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            // Hide help
            if (ORBEON.util.Dom.hasClass(target, "xforms-help-image")
                    || ORBEON.util.Dom.hasClass(target, "xforms-alert-active"))
                tt_Hide();
        }
    },

    click: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Activate hint
            ORBEON.xforms.Controls.hintActive(target, true);

            // Click on output
            if (ORBEON.util.Dom.hasClass(target, "xforms-output")) {
                xformsFireEvents([xformsCreateEventArray(target, "DOMFocusIn", null)], false);
            }

            // Click on trigger
            if ((ORBEON.util.Dom.hasClass(target, "xforms-trigger") || ORBEON.util.Dom.hasClass(target, "xforms-submit"))) {
                YAHOO.util.Event.preventDefault(event);
                if (!ORBEON.util.Dom.hasClass(target, "xforms-readonly")) {
                    // If this is an anchor and we didn't get a chance to register the focus event,
                    // send the focus event here. This is useful for anchors (we don't listen on the
                    // focus event on those, and for buttons on Safari which does not dispatch the focus
                    // event for buttons.
                    ORBEON.xforms.Events.focus(event);
                    xformsFireEvents([xformsCreateEventArray(target, "DOMActivate", null)], false);
                }
            }

            // Click on checkbox or radio button
            if (ORBEON.util.Dom.hasClass(target, "xforms-select1-appearance-full")
                    || ORBEON.util.Dom.hasClass(target, "xforms-select-appearance-full")) {
                xformsFireEvents(new Array(xformsCreateEventArray
                        (target, "xxforms-value-change-with-focus-change",
                                ORBEON.xforms.Controls.getCurrentValue(target), null)), false);
            }

            // Click on calendar inside input field
            if (ORBEON.util.Dom.hasClass(target, "xforms-input")) {

                // Initialize calendar when needed
                var displayField = ORBEON.util.Dom.getChildElement(target, 0);
                var inputField = ORBEON.util.Dom.getChildElement(target, 1);
                var showCalendar = ORBEON.util.Dom.getChildElement(target, 2);
                if (ORBEON.util.Dom.hasClass(inputField, "xforms-type-date")
                        && !ORBEON.util.Dom.hasClass(displayField, "xforms-readonly")) {

                    // Setup calendar library if not done already
                    if (!ORBEON.xforms.Globals.inputCalendarCreated[target.id]) {
                        Calendar.setup({
                            inputField     :    inputField.id,
                            ifFormat       :    "%Y-%m-%d",
                            showsTime      :    false,
                            button         :    target.id,
                            singleClick    :    true,
                            step           :    1,
                            onUpdate       :    ORBEON.xforms.Events.calendarUpdate,
                            electric       :    true
                        });
                        // JSCalendar sets his listener in the onclick attribute: save it so we can call it later
                        ORBEON.xforms.Globals.inputCalendarOnclick[target.id] = target.onclick;
                        target.onclick = null;
                        ORBEON.xforms.Globals.inputCalendarCreated[target.id] = true;
                    }

                    // Event can be received on calendar picker span, or on the containing span
                    ORBEON.xforms.Globals.inputCalendarOnclick[target.id]();
                }
            }
        }
    },

    /**
     * Send notification to XForms engine end-user clicked on day.
     * We don't notify the server the user is just navigating through the calendar.
     */
    calendarUpdate: function(calendar) {
        if (ORBEON.util.Dom.hasClass(calendar.activeDiv, "day")) {
            var inputField = calendar.params.inputField;
            var element = inputField.parentNode;
            xformsFireEvents([xformsCreateEventArray(element, "xxforms-value-change-with-focus-change",
                ORBEON.xforms.Controls.getCurrentValue(element))], false);
        }
    },

    sliderValueChange: function (offset) {
        // Notify server that value changed
        var rangeControl = document.getElementById(this.id);
        rangeControl.value = offset / 200;
        xformsValueChanged(rangeControl, null);
    },

    /**
     * Called by the YUI menu library when a click happens a menu entry.
     */
    menuClick: function(eventType, arguments, userObject) {
        var menu = userObject["menu"];
        var value = userObject["value"];
        ORBEON.xforms.Globals.overlayManager.hideAll();
        xformsFireEvents([xformsCreateEventArray(menu, "xxforms-value-change-with-focus-change", value)], false);
    },

    /**
     * Event listener on dialogs called by YUI when the dialog is closed. If the dialog was closed by the user (not
     * because the server told use to close the dialog), then we want to notify the server that this happened.
     */
    dialogClose: function(type, args, me) {
        var dialogId = me;
        var dialog = document.getElementById(dialogId);
        xformsFireEvents([xformsCreateEventArray(dialog, "xxforms-dialog-close")], false);
    }
};

ORBEON.xforms.Init = {

    /**
     * Functions used to initialize special controls
     */
    _specialControlsInitFunctions: null,
    _getSpecialControlsInitFunctions: function () {
        ORBEON.xforms.Init._specialControlsInitFunctions = ORBEON.xforms.Init._specialControlsInitFunctions || {
            "select1": {
                "compact" : ORBEON.xforms.Init._list,
                "{http://orbeon.org/oxf/xml/xforms}autocomplete": ORBEON.xforms.Init._autoComplete,
                "{http://orbeon.org/oxf/xml/xforms}menu": ORBEON.xforms.Init._menu,
                "{http://orbeon.org/oxf/xml/xforms}tree": ORBEON.xforms.Init._tree
            },
            "select": {
                "compact" : ORBEON.xforms.Init._list,
                "{http://orbeon.org/oxf/xml/xforms}tree": ORBEON.xforms.Init._tree
            },
            "range": { "": ORBEON.xforms.Init._range },
            "textarea": {
                "{http://orbeon.org/oxf/xml/xforms}autosize": ORBEON.xforms.Init._widetextArea,
                "text/html": ORBEON.xforms.Init._htmlArea
            },
            "dialog": { "": ORBEON.xforms.Init._dialog}
        };
        return ORBEON.xforms.Init._specialControlsInitFunctions;
    },

    _addToTree: function (tree, nameValueArray, treeNode, firstPosition) {
        for (var arrayIndex = firstPosition; arrayIndex < nameValueArray.length; arrayIndex++) {
            // Extract information from the first 3 position in the array
            var childArray = nameValueArray[arrayIndex];
            var name = childArray[0];
            var value = childArray[1];
            var selected = childArray[2];
            // Create node and add to tree
            var nodeInformation = { label: name, value: value };
            var childNode;
            if (tree.xformsAllowMultipleSelection) {
                childNode = new YAHOO.widget.TaskNode(nodeInformation, treeNode, false);
                childNode.onCheckClick = xformsSelectTreeSelect;
                if (selected) childNode.check();
            } else {
                nodeInformation.href = "javascript:xformsSelect1TreeSelect('"
                        + tree.id + "', '"
                        + value.replace(XFORMS_REGEXP_SINGLE_QUOTE, "\\'")
                        + "')";
                childNode = new YAHOO.widget.TextNode(nodeInformation, treeNode, false);
            }
            ORBEON.xforms.Init._addToTree(tree, childArray, childNode, 3);
            // Add this value to the list if selected
            if (selected) {
                if (tree.value != "") tree.value += " ";
                tree.value += value;
            }
        }
    },

    /**
     * Create a sub-menu attached to the given menu item. In the nameValueArray we
     * ignore the first 3 items that correspond to the menuItem.
     */
    _addToMenuItem: function (menu, nameValueArray, menuItem) {
        // Assign id to menu item
        if (menuItem.element.id == "")
            YAHOO.util.Dom.generateId(menuItem.element);
        // Handle click on menu item
        menuItem.clickEvent.subscribe(ORBEON.xforms.Events.menuClick,
            {"menu": menu, "value": nameValueArray[1]});

        // Create sub-menu if necessary
        if (nameValueArray.length > 3) {
            // Show submenu when mouse over menu item
            menuItem.mouseOverEvent.subscribe(xformsOnMenuBarItemMouseOver);
            menuItem.mouseOutEvent.subscribe(xformsOnMenuBarItemMouseOut);
            // Create submenu
            var subMenu = new YAHOO.widget.Menu(menuItem.element.id + "menu");
            //subMenu.mouseOverEvent.subscribe(onSubmenuMouseOver, subMenu, true);
            //subMenu.mouseOutEvent.subscribe(onSubmenuMouseOut,subMenu, true);
            // Add menu items to submenu
            for (var arrayIndex = 3; arrayIndex < nameValueArray.length; arrayIndex++) {
                // Extract information from the first 3 position in the array
                var childArray = nameValueArray[arrayIndex];
                var name = childArray[0];
                var value = childArray[1];
                var selected = childArray[2];
                // Create menu item and add to menu
                var subMenuItem = new YAHOO.widget.MenuItem(name, { url: "#" });
                subMenu.addItem(subMenuItem);
                // Add sub-sub menu
                ORBEON.xforms.Init._addToMenuItem(menu, childArray, subMenuItem)
            }
            menuItem.cfg.setProperty("submenu", subMenu);
            ORBEON.xforms.Globals.overlayManager.register(subMenu);
        }
    },

    _registerListerersOnFormElements: function() {
        for (var i = 0; i < document.forms.length; i++) {
            var form = document.forms[i];
            if (ORBEON.util.Dom.hasClass(form, "xforms-form")) {
                for (var j = 0; j < form.elements.length; j++) {
                    var element = form.elements[j];
                    YAHOO.util.Event.addListener(element, "focus", ORBEON.xforms.Events.focus);
                    YAHOO.util.Event.addListener(element, "blur", ORBEON.xforms.Events.blur);
                    YAHOO.util.Event.addListener(element, "change", ORBEON.xforms.Events.change);
                }
            }
        }
    },

    document: function() {

        // Register events in capture phase for W3C-compliant browsers.
        // Avoid Safari 1.3 which is buggy.
        if (window.addEventListener && !ORBEON.xforms.Globals.isRenderingEngineWebCore13) {
            window.addEventListener("focus", ORBEON.xforms.Events.focus, true);
            window.addEventListener("blur", ORBEON.xforms.Events.blur, true);
            window.addEventListener("change", ORBEON.xforms.Events.change, true);
        } else {
            ORBEON.xforms.Init._registerListerersOnFormElements();
        }

        // Register events that bubble on document for all browsers
        YAHOO.util.Event.addListener(document, "keypress", ORBEON.xforms.Events.keypress);
        YAHOO.util.Event.addListener(document, "keydown", ORBEON.xforms.Events.keydown);
        YAHOO.util.Event.addListener(document, "keyup", ORBEON.xforms.Events.keyup);
        YAHOO.util.Event.addListener(document, "mousedown", ORBEON.xforms.Events.mousedown);
        YAHOO.util.Event.addListener(document, "mouseover", ORBEON.xforms.Events.mouseover);
        YAHOO.util.Event.addListener(document, "mouseout", ORBEON.xforms.Events.mouseout);
        YAHOO.util.Event.addListener(document, "click", ORBEON.xforms.Events.click);
        YAHOO.util.Event.addListener(window, "resize", ORBEON.xforms.Events.resize);

        // Initialize XForms server URL
        var scripts = document.getElementsByTagName("script");
        for (var scriptIndex = 0; scriptIndex < scripts.length; scriptIndex++) {
            var script = scripts[scriptIndex];
            var scriptSrc = ORBEON.util.Dom.getAttribute(script, "src");
            if (scriptSrc != null) {
                var startPathToJavaScript = scriptSrc.indexOf(PATH_TO_JAVASCRIPT);
                if (startPathToJavaScript != -1) {
                    BASE_URL = ORBEON.util.Dom.getAttribute(script, "src").substr(0, startPathToJavaScript);
                    XFORMS_SERVER_URL = BASE_URL + "/xforms-server";
                    break;
                }
            }
        }

        // Override image location for YUI to use local images
        var yuiBaseURL = BASE_URL + "/ops/images/yui/";
        if (YAHOO && YAHOO.widget) {
            if (YAHOO.widget.Module) {
                YAHOO.widget.Module.IMG_ROOT = yuiBaseURL;
                YAHOO.widget.Module.IMG_ROOT_SSL = yuiBaseURL;
            }
            if (YAHOO.widget.Calendar_Core) {
                YAHOO.widget.Calendar_Core.IMG_ROOT = yuiBaseURL;
                YAHOO.widget.Calendar_Core.IMG_ROOT_SSL = yuiBaseURL;
            }
            if (YAHOO.widget.MenuModuleItem) {
                YAHOO.widget.MenuModuleItem.prototype.IMG_ROOT = yuiBaseURL;
                YAHOO.widget.MenuModuleItem.prototype.IMG_ROOT_SSL = yuiBaseURL;
            }
        }

        // Initialize special controls
        if (!(window.opsXFormsControls === undefined)) {
            var initFunctions = ORBEON.xforms.Init._getSpecialControlsInitFunctions();
            // Iterate over controls
            for (var controlType in window.opsXFormsControls["controls"]) {
                if (initFunctions[controlType]) {
                    var controlAppearances = window.opsXFormsControls["controls"][controlType];
                    // Iterate over appearance for current control
                    for (var controlAppearance in controlAppearances) {
                        var initFunction = initFunctions[controlType][controlAppearance];
                        if (initFunction) {
                            var controlIds = controlAppearances[controlAppearance];
                            // Iterate over controls
                            for (var controlIndex = 0; controlIndex < controlIds.length; controlIndex++) {
                                var control = document.getElementById(controlIds[controlIndex]);
                                initFunction(control);
                            }
                        }
                    }
                }
            }
        }

        // Initialize attributes on form
        for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
            var form = document.forms[formIndex];
            // If this is an XForms form, procede with initialization
            if (ORBEON.util.Dom.hasClass(form, "xforms-form")) {

                // Initialize loading and error indicator
                ORBEON.xforms.Globals.formLoadingLoading[formIndex] = null;
                ORBEON.xforms.Globals.formLoadingError[formIndex] = null;
                ORBEON.xforms.Globals.formLoadingNone[formIndex] = null;

                var xformsLoadingCount = 0;
                for (var formChildIndex = 0; formChildIndex < form.childNodes.length; formChildIndex++) {
                    if (xformsLoadingCount == 3) break;
                    var formChild = form.childNodes[formChildIndex];
                    if (formChild.className == "xforms-loading-loading") {
                        ORBEON.xforms.Globals.formLoadingLoading[formIndex] = formChild;
                        xformsLoadingCount++;
                        continue;
                    }
                    if (formChild.className == "xforms-loading-error") {
                        ORBEON.xforms.Globals.formLoadingError[formIndex] = formChild;
                        xformsLoadingCount++;
                        continue;
                    }
                    if (formChild.className == "xforms-loading-none") {
                        ORBEON.xforms.Globals.formLoadingNone[formIndex] = formChild;
                        xformsLoadingCount++;
                        continue;
                    }
                }

                var elements = form.elements;
                var xformsRepeatTree;
                var xformsRepeatIndices;
                for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
                    var element = elements[elementIndex];
                    if (element.name.indexOf("$static-state") != -1) {
                        ORBEON.xforms.Globals.formStaticState[formIndex] = element;
                    } else if (element.name.indexOf("$dynamic-state") != -1) {
                        ORBEON.xforms.Globals.formDynamicState[formIndex] = element;
                    } else if (element.name.indexOf("$client-state") != -1) {
                        ORBEON.xforms.Globals.formClientState[formIndex] = element;
                        if (element.value == "")
                            xformsStoreInClientState(formIndex, "ajax-dynamic-state",
                                    ORBEON.xforms.Globals.formDynamicState[formIndex].value);
                    } else if (element.name.indexOf("$repeat-tree") != -1) {
                        xformsRepeatTree = element;
                    } else if (element.name.indexOf("$repeat-indexes") != -1) {
                        xformsRepeatIndices = element;
                        // This is the last input field we are interested in
                        break;
                    }
                }

                // Parse and store initial repeat hierarchy
                var repeatTreeString = xformsRepeatTree.value;
                var repeatTree = repeatTreeString.split(",");
                for (var repeatIndex = 0; repeatIndex < repeatTree.length; repeatIndex++) {
                    var repeatInfo = repeatTree[repeatIndex].split(" ");
                    var id = repeatInfo[0];
                    var parent = repeatInfo.length > 1 ? repeatInfo[repeatInfo.length - 1] : null;
                    ORBEON.xforms.Globals.repeatTreeChildToParent[id] = parent;
                }
                for (var child in ORBEON.xforms.Globals.repeatTreeChildToParent) {
                    var parent = ORBEON.xforms.Globals.repeatTreeChildToParent[child];
                    while (parent != null) {
                        if (!ORBEON.xforms.Globals.repeatTreeParentToAllChildren[parent])
                            ORBEON.xforms.Globals.repeatTreeParentToAllChildren[parent] = new Array();
                        ORBEON.xforms.Globals.repeatTreeParentToAllChildren[parent].push(child);
                        parent = ORBEON.xforms.Globals.repeatTreeChildToParent[parent];
                    }
                }

                // Parse and store initial repeat indexes
                var repeatIndexesString = xformsRepeatIndices.value;
                var repeatIndexes = repeatIndexesString.split(",");
                for (var repeatIndex = 0; repeatIndex < repeatIndexes.length; repeatIndex++) {
                    var repeatInfo = repeatIndexes[repeatIndex].split(" ");
                    var id = repeatInfo[0];
                    var index = repeatInfo[repeatInfo.length - 1];
                    ORBEON.xforms.Globals.repeatIndexes[id] = index;
                }

                // Ask server to resend events if this is not the first time load is called
                if (xformsGetFromClientState(formIndex, "load-did-run") == null) {
                    xformsStoreInClientState(formIndex, "load-did-run", "true");
                } else {
                    xformsFireEvents(new Array(xformsCreateEventArray(form, "xxforms-all-events-required", null, null)), false);
                }
            }
        }

        // Run code sent by server
        if (typeof xformsPageLoadedServer != "undefined")
            xformsPageLoadedServer();
    },

    _autoComplete: function(autoComplete) {
        var textfield = ORBEON.util.Dom.getChildElement(autoComplete, 0);
        var select = ORBEON.util.Dom.getChildElement(autoComplete, 1);
        // Get list of possible values from the select
        var values = new Array();
        for (var optionIndex = 1; optionIndex < select.options.length; optionIndex++)
            values.push(select.options[optionIndex].value);
        // Initialize auto-complete input
        var noFilter = ORBEON.util.Dom.hasClass(autoComplete, "xforms-select1-open-autocomplete-nofilter");
        ORBEON.xforms.Globals.autoCompleteLastKeyCode[autoComplete.id] = -1;
        ORBEON.xforms.Globals.autoCompleteOpen[autoComplete.id] = actb(textfield, values, noFilter);
    },

    _widetextArea: function(textarea) {
        ORBEON.xforms.Globals.autosizeTextareas.push(textarea);
        ORBEON.xforms.Controls.autosizeTextarea(textarea);
    },

    _range: function(range) {
        range.tabIndex = 0;
        range.previousValue = 0; // Will be modified once the initial value can be set
        var thumbDiv = range.firstChild;
        if (thumbDiv.nodeType != ELEMENT_TYPE) thumbDiv = thumbDiv.nextSibling;
        thumbDiv.id = range.id + XFORMS_SEPARATOR_1 + "thumb";
        var slider = YAHOO.widget.Slider.getHorizSlider(range.id, thumbDiv.id, 0, 200);
        slider.onChange = ORBEON.xforms.Events.sliderValueChange;
    },

    _tree: function(tree) {
        // Save in the control if it allows multiple selection
        tree.xformsAllowMultipleSelection = ORBEON.util.Dom.hasClass(tree, "xforms-select");
        // Parse data put by the server in the div
        var treeString = ORBEON.util.Dom.getStringValue(tree);
        treeString = ORBEON.util.String.replace(treeString, "\n", " ");
        treeString = ORBEON.util.String.replace(treeString, "\r", " ");
        var treeArray = eval(treeString);
        ORBEON.util.Dom.setStringValue(tree, "");
        tree.value = "";
        // Create, populate, and show the tree
        tree.xformsTree = new YAHOO.widget.TreeView(tree.id);
        var treeRoot = tree.xformsTree.getRoot();
        ORBEON.xforms.Init._addToTree(tree, treeArray, treeRoot, 0);
        // Make selected nodes visible'
        var values = tree.xformsAllowMultipleSelection ? tree.value.split(" ") : [ tree.value ];
        for (nodeIndex in tree.xformsTree._nodes) {
            var node = tree.xformsTree._nodes[nodeIndex];
            if (xformsArrayContains(values, node.data.value)) {
                var nodeParent = node.parent;
                while (nodeParent != null) {
                    nodeParent.expand();
                    nodeParent = nodeParent.parent;
                }
            }
        }
        // Save value in tree
        tree.previousValue = tree.value;
        tree.xformsTree.draw();
        ORBEON.util.Dom.removeClass(tree, "xforms-initially-hidden");
    },

    _menu: function (menu) {
        // Find the divs for the tree and for the values inside the control
        var treeDiv;
        var valuesDiv;
        for (var j = 0; j < menu.childNodes.length; j++) {
            var childNode =  menu.childNodes[j];
            if (childNode.nodeType == ELEMENT_TYPE) {
                if (ORBEON.util.Dom.hasClass(childNode, "yuimenubar")) {
                    treeDiv = childNode;
                } else if (ORBEON.util.Dom.hasClass(childNode, "xforms-initially-hidden")) {
                    valuesDiv = childNode;
                }
            }
        }

        // Create overlay manager, if we don't have one already
        if (ORBEON.xforms.Globals.overlayManager == null)
            ORBEON.xforms.Globals.overlayManager = new YAHOO.widget.OverlayManager();
        // Extract menu hierarchy from HTML
        var menuString = ORBEON.util.Dom.getStringValue(valuesDiv);
        menuString = ORBEON.util.String.replace(menuString, "\n", " ");
        menuString = ORBEON.util.String.replace(menuString, "\r", " ");
        var menuArray = eval(menuString);
        ORBEON.util.Dom.setStringValue(valuesDiv, "");
        // Initialize tree
        YAHOO.util.Dom.generateId(treeDiv);
        menu.xformsMenu = new YAHOO.widget.MenuBar(treeDiv.id);
        for (var topLevelIndex = 0; topLevelIndex < menu.xformsMenu.getItemGroups()[0].length; topLevelIndex++) {
            var topLevelArray = menuArray[topLevelIndex];
            var menuItem = menu.xformsMenu.getItem(topLevelIndex);
            ORBEON.xforms.Init._addToMenuItem(menu, topLevelArray, menuItem);
        }
        menu.xformsMenu.render();
        menu.xformsMenu.show();
        ORBEON.util.Dom.removeClass(menu, "xforms-initially-hidden");
    },

    /**
     * Initialize HTML areas.
     */
    _htmlArea: function (htmlArea) {
        var fckEditor = new FCKeditor(htmlArea.name);
        if (!xformsArrayContains(ORBEON.xforms.Globals.htmlAreaNames, htmlArea.name))
            ORBEON.xforms.Globals.htmlAreaNames.push(htmlArea.name);
        fckEditor.BasePath = BASE_URL + "/ops/fckeditor/";
        fckEditor.ToolbarSet = "OPS";
        if (ORBEON.xforms.Globals.fckEditorLoading) {
            ORBEON.xforms.Globals.fckEditorsToLoad.push(fckEditor);
        } else {
            ORBEON.xforms.Globals.fckEditorLoading = true;
            fckEditor.ReplaceTextarea();
            ORBEON.xforms.Controls.updateHTMLAreaClasses(document.getElementById(fckEditor.InstanceName));
        }
    },

    /**
     * For all the controls except list, we figure out the initial value of the control when
     * receiving the first focus event. For the lists on Firefox, the value has already changed
     * when we receive the focus event. So here we save the value for lists when the page loads.
     */
    _list: function(list) {
        var value = "";
        for (var i = 0; i < list.options.length; i++) {
            var option = list.options[i];
            if (option.selected) {
                if (value != "") value += " ";
                value += option.value;
            }
        }
        ORBEON.xforms.Globals.serverValue[list.id] = value;
    },

    /**
     * Initialize dialogs
     */
    _dialog: function(dialog) {
        var isModal = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-modal");
        var hasClose = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-close");
        ORBEON.util.Dom.removeClass(dialog, "xforms-initially-hidden");
        yuiDialog = new YAHOO.widget.Dialog(dialog.id, {
            modal: isModal,
            close: hasClose,
            visible: false,
            draggable: true,
            fixedcenter: true,
            constraintoviewport: true,
            underlay: "shadow"
        });
        yuiDialog.beforeHideEvent.subscribe(ORBEON.xforms.Events.dialogClose, dialog.id);
        yuiDialog.render();
        ORBEON.xforms.Globals.dialogs[dialog.id] = yuiDialog;
    }
};

ORBEON.xforms.Server = {

    Event: function(form, targetId, otherId, value, eventName, bubbles, cancelable) {
        this.form = form;
        this.targetId = targetId;
        this.otherId = otherId;
        this.value = value;
        this.eventName = eventName;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
    },

    fireEvents: function(events, incremental) {
        // Store the time of the first event to be sent in the queue
        var currentTime = new Date().getTime();
        if (ORBEON.xforms.Globals.eventQueue.length == 0)
            ORBEON.xforms.Globals.eventsFirstEventTime = currentTime;

        // Store events to fire
        for (var eventIndex = 0; eventIndex < events.length; eventIndex++) {
            ORBEON.xforms.Globals.eventQueue.push(events[eventIndex]);
        }

        // Fire them with a delay to give us a change to aggregate events together
        ORBEON.xforms.Globals.executeEventFunctionQueued++;
        if (incremental && !(currentTime - ORBEON.xforms.Globals.eventsFirstEventTime >
                XFORMS_DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_IN_MS)) {
            // After a delay (e.g. 500 ms), run executeNextRequest() and send queued events to server
            // if there are no other executeNextRequest() that have been added to the queue after this
            // request.
            window.setTimeout(function() { ORBEON.xforms.Server.executeNextRequest(false); },
                XFORMS_DELAY_BEFORE_INCREMENTAL_REQUEST_IN_MS);
        } else {
            // After a very short delay (e.g. 20 ms), run executeNextRequest() and force queued events
            // to be sent to the server, even if there are other executeNextRequest() queued.
            // The small delay is here so we don't send multiple requests to the server when the
            // browser gives us a sequence of events (e.g. focus out, change, focus in).
            window.setTimeout(function() { ORBEON.xforms.Server.executeNextRequest(true); },
                XFORMS_DELAY_BEFORE_ANY_REQUEST_IN_MS);
        }
        return false;
    },

    executeNextRequest: function(bypassRequestQueue) {
        bypassRequestQueue = typeof(bypassRequestQueue) == "boolean"  && bypassRequestQueue == true;

        ORBEON.xforms.Globals.executeEventFunctionQueued--;
        var executedRequest = false;
        if (!ORBEON.xforms.Globals.requestInProgress
                && ORBEON.xforms.Globals.eventQueue.length > 0
                && (bypassRequestQueue || ORBEON.xforms.Globals.executeEventFunctionQueued == 0)) {

            // Collapse value change for the same control
            {
                var seenControlValue = {};
                var newEvents = [];
                for (var eventIndex = ORBEON.xforms.Globals.eventQueue.length - 1; eventIndex >= 0; eventIndex--) {
                    // Extract information from event array
                    var event = ORBEON.xforms.Globals.eventQueue[eventIndex];
                    if (event.eventName == "xxforms-value-change-with-focus-change") {
                        // Don't send change value if there is already a change value for the same control
                        if (!seenControlValue[event.targetId] == true) {
                            seenControlValue[event.targetId] = true;
                            // Don't send change value if the server already knows about the value of this control
                            if (ORBEON.xforms.Globals.serverValue[event.targetId] != "undefined"
                                    && ORBEON.xforms.Globals.serverValue[event.targetId] != event.value) {
                                ORBEON.xforms.Globals.serverValue[event.targetId] = event.value;
                                newEvents.unshift(event);
                            }
                        }
                    } else {
                        newEvents.unshift(event);
                    }
                }
                ORBEON.xforms.Globals.eventQueue = newEvents;
            }

            // Check again that we have events to send after collapsing
            if (ORBEON.xforms.Globals.eventQueue.length > 0) {

                // Save the form for this request
                ORBEON.xforms.Globals.requestForm = ORBEON.xforms.Globals.eventQueue[0].form;
                var formIndex = ORBEON.util.Dom.getFormIndex(ORBEON.xforms.Globals.requestForm);

                // Mark this as loading
                ORBEON.xforms.Globals.requestInProgress = true;
                if (XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS == 0) xformsDisplayLoading();
                else window.setTimeout(xformsDisplayLoading, XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS);

                // Remove from this list of ids that changed the id of controls for
                // which we have received the keyup corresponding to the keydown
                for (var id  in ORBEON.xforms.Globals.changedIdsRequest) {
                    if (ORBEON.xforms.Globals.changedIdsRequest[id] == 0)
                        ORBEON.xforms.Globals.changedIdsRequest[id] = null;
                }

                // Build request
                var requestDocumentString = "";

                // Add entity declaration for nbsp. We are adding this as this entity is generated by the FCK editor.
                requestDocumentString += '<!DOCTYPE xxforms:event-request [<!ENTITY nbsp "&#160;">]>\n';

                var indent = "    ";
                {
                    // Start request
                    requestDocumentString += '<xxforms:event-request xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">\n';

                    // Add static state
                    requestDocumentString += indent;
                    requestDocumentString += '<xxforms:static-state>';
                    requestDocumentString += ORBEON.xforms.Globals.formStaticState[formIndex].value;
                    requestDocumentString += '</xxforms:static-state>\n';

                    // Add dynamic state (element is just created and will be filled just before we send the request)
                    requestDocumentString += indent;
                    requestDocumentString += '<xxforms:dynamic-state>';
                    requestDocumentString += xformsGetFromClientState(formIndex, "ajax-dynamic-state");
                    requestDocumentString += '</xxforms:dynamic-state>\n';

                    // Start action
                    requestDocumentString += indent;
                    requestDocumentString += '<xxforms:action>\n';

                    // Add events
                    var handledEvents = [];
                    for (var i = 0; i < ORBEON.xforms.Globals.eventQueue.length; i++) {
                        var event = ORBEON.xforms.Globals.eventQueue[i];

                        // Only handle this event if it is for the form we chose
                        if (ORBEON.xforms.Controls.getForm(event.form) == ORBEON.xforms.Globals.requestForm) {
                            // Create <xxforms:event> element
                            requestDocumentString += indent + indent;
                            requestDocumentString += '<xxforms:event';
                            requestDocumentString += ' name="' + event.eventName + '"';
                            if (event.targetId != null)
                                requestDocumentString += ' source-control-id="' + event.targetId + '"';
                            if (event.otherId != null)
                                requestDocumentString += ' other-control-id="' + event.otherId + '"';
                            requestDocumentString += '>';
                            if (event.value != null) {
                                // When the range is used we get an int here when the page is first loaded
                                if (typeof event.value == "string") {
                                    event.value = event.value.replace(XFORMS_REGEXP_AMPERSAND, "&amp;");
                                    event.value = event.value.replace(XFORMS_REGEXP_OPEN_ANGLE, "&lt;");
                                }
                                requestDocumentString += event.value;
                            }
                            requestDocumentString += '</xxforms:event>\n';
                            handledEvents.unshift(i);
                        }
                    }

                    // End action
                    requestDocumentString += indent;
                    requestDocumentString += '</xxforms:action>\n';

                    // End request
                    requestDocumentString += '</xxforms:event-request>';

                    // Remove events we have handled from event queue
                    for (var i = 0; i < handledEvents.length; i++)
                        ORBEON.xforms.Globals.eventQueue.splice(handledEvents[i], 1);
                }

                // Send request
                executedRequest = true;
                YAHOO.util.Connect.setDefaultPostHeader(false);
                YAHOO.util.Connect.initHeader("Content-Type", "application/xml");
                YAHOO.util.Connect.asyncRequest("POST", XFORMS_SERVER_URL, { success: xformsHandleResponse }, requestDocumentString);
            }
        }

        // Hide loading indicator if we have not started a new request (nothing more to run)
        // and there are not events in the queue. However make sure not to hide the error message
        // if the last XHR query returned an error.
        if (!executedRequest && ORBEON.xforms.Globals.eventQueue.length == 0
                && !ORBEON.xforms.Globals.lastRequestIsError)
            xformsDisplayIndicator("none");
    },

    callUserScript: function(functionName, targetId, observerId) {
        var targetElement = document.getElementById(targetId);
        var observer = document.getElementById(observerId);
        var event = { "target" : targetElement };
        var theFunction = eval(functionName);
        theFunction.call(observer, event);
    }
};

function xformsIsDefined(thing) {
    return typeof thing != "undefined";
}

function xformsDispatchEvent(target, eventName) {
    if (target.dispatchEvent) {
        var event = document.createEvent("HTMLEvents");
        event.initEvent(eventName.toLowerCase(), true, true);
        target.dispatchEvent(event);
    } else {
        target.fireEvent("on" + eventName);
    }
}

function xformsPreventDefault(event) {
    if (event.preventDefault) {
        // Firefox version
        event.preventDefault();
    } else {
        // IE version
        return false;
    }
}

function xformsArrayContains(array, element) {
    for (var i = 0; i < array.length; i++)
        if (array[i] == element)
            return true;
    return false;
}

function xformsStringReplaceWorker(node, placeholderRegExp, replacement) {
    switch (node.nodeType) {
        case ELEMENT_TYPE:
            for (var i = 0; i < node.attributes.length; i++) {
                var newValue = new String(node.attributes[i].value).replace(placeholderRegExp, replacement);
                if (newValue != node.attributes[i].value)
                    node.setAttribute(node.attributes[i].name, newValue);
            }
            for (var i = 0; i < node.childNodes.length; i++)
                xformsStringReplaceWorker(node.childNodes[i], placeholderRegExp, replacement);
            break;
        case TEXT_TYPE:
            var newValue = new String(node.nodeValue).replace(placeholderRegExp, replacement);
            if (newValue != node.nodeValue)
                node.nodeValue = newValue;
            break;
    }
}

// Replace in a tree a placeholder by some other string in text nodes and attribute values
function xformsStringReplace(node, placeholder, replacement) {
    var placeholderRegExp = new RegExp(placeholder.replace(new RegExp("\\$", "g"), "\\$"), "g");
    xformsStringReplaceWorker(node, placeholderRegExp, replacement);
}

function xformsNormalizeEndlines(text) {
    return text.replace(XFORMS_REGEXP_CR, "");
}

function xformsAppendRepeatSuffix(id, suffix) {
    if (suffix == "")
        return id;
    if (suffix.charAt(0) == XFORMS_SEPARATOR_2)
        suffix = suffix.substring(1);
    if (id.indexOf(XFORMS_SEPARATOR_1) == -1)
        return id + XFORMS_SEPARATOR_1 + suffix;
    else
        return id + XFORMS_SEPARATOR_2 + suffix;
}

/**
* Locate the delimiter at the given position starting from a repeat begin element.
*/
function xformsFindRepeatDelimiter(repeatId, index) {

    // Find id of repeat begin for the current repeatId
    var parentRepeatIndexes = "";
    {
        var currentId = repeatId;
        while (true) {
            var parent = ORBEON.xforms.Globals.repeatTreeChildToParent[currentId];
            if (parent == null) break;
            var grandParent = ORBEON.xforms.Globals.repeatTreeChildToParent[parent];
            parentRepeatIndexes = (grandParent == null ? XFORMS_SEPARATOR_1 : XFORMS_SEPARATOR_2)
                    + ORBEON.xforms.Globals.repeatIndexes[parent] + parentRepeatIndexes;
            currentId = parent;
        }
    }

    var beginElementId = "repeat-begin-" + repeatId + parentRepeatIndexes;
    var beginElement = document.getElementById(beginElementId);
    if (!beginElement) return null;
    var cursor = beginElement;
    var cursorPosition = 0;
    while (true) {
        while (cursor.nodeType != ELEMENT_TYPE || !ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")) {
            cursor = cursor.nextSibling;
            if (!cursor) return null;
        }
        cursorPosition++;
        if (cursorPosition == index) break;
        cursor = cursor.nextSibling;
    }

    return cursor;
}

function xformsLog(text) {
    var debugDiv = document.getElementById("xforms-debug");
    if (debugDiv == null) {
        // Figure out width and heigh of visible part of the page
        var visibleWidth;
        var visibleHeight;
        if (navigator.appName.indexOf("Microsoft")!=-1) {
            visibleWidth = document.body.offsetWidth;
            visibleHeight = document.body.offsetHeight;
        } else {
            visibleWidth = window.innerWidth;
            visibleHeight = window.innerHeight;
        }

        // Create div with appropriate size and position
        debugDiv = document.createElement("div");
        debugDiv.className = "xforms-debug";
        debugDiv.id = "xforms-debug";
        debugDiv.style.width = XFORMS_DEBUG_WINDOW_WIDTH + "px";
        debugDiv.style.left = visibleWidth - (XFORMS_DEBUG_WINDOW_WIDTH + 50) + "px";
        debugDiv.style.height = XFORMS_DEBUG_WINDOW_HEIGHT + "px";
        debugDiv.style.top = visibleHeight - (XFORMS_DEBUG_WINDOW_HEIGHT + 20) + "px";

        // Add "clear" button
        var clear = document.createElement("BUTTON");
        clear.appendChild(document.createTextNode("Clear"));
        debugDiv.appendChild(clear);
        document.body.insertBefore(debugDiv, document.body.firstChild);

        // Handle click on clear button
        YAHOO.util.Event.addListener(clear, "click", function (event) {
            var target = getEventTarget(event);
            alert("click");
            while (target.nextSibling)
                target.parentNode.removeChild(target.nextSibling);
            return false;
        });

        // Make it so user can move the debug window
        YAHOO.util.Event.addListener(debugDiv, "mousedown", function (event) {
            ORBEON.xforms.Globals.debugDiv = getEventTarget(event);
            return false;
        });
        YAHOO.util.Event.addListener(document, "mouseup", function (event) {
            ORBEON.xforms.Globals.debugDiv = null;
            return false;
        });
        YAHOO.util.Event.addListener(document, "mousemove", function (event) {
            if (ORBEON.xforms.Globals.debugDiv) {
                ORBEON.xforms.Globals.debugDiv.style.left = event.clientX;
                ORBEON.xforms.Globals.debugDiv.style.top = event.clientY;
            }
            return false;
        });
    }
    debugDiv.innerHTML += text + " | ";
}

function xformsLogTime(text) {
    return;
    var oldTime = ORBEON.xforms.Globals.debugLastTime;
    var currentTime = new Date().getTime();
    ORBEON.xforms.Globals.debugLastTime = currentTime;
    xformsLog((currentTime - oldTime) + ": " + text);
}

function xformsLogProperties(object) {
    var message = "[";
    var first = true;
    for (var p in object) {
        if (first) first = false; else message += ", ";
        message += p + ": " + object[p];
    }
    message += "]";
    xformsLog(message);
}

function xformsDisplayIndicator(state) {
    var form = ORBEON.xforms.Globals.requestForm;
    var formIndex = ORBEON.util.Dom.getFormIndex(form);
    switch (state) {
        case "loading":
            if (ORBEON.xforms.Globals.formLoadingLoading[formIndex] != null)
                ORBEON.xforms.Globals.formLoadingLoading[formIndex].style.display = "block";
            if (ORBEON.xforms.Globals.formLoadingError[formIndex] != null)
                ORBEON.xforms.Globals.formLoadingError[formIndex].style.display = "none";
            if (ORBEON.xforms.Globals.formLoadingNone[formIndex] != null)
                ORBEON.xforms.Globals.formLoadingNone[formIndex].style.display = "block";
            break;
        case "error":
            if (ORBEON.xforms.Globals.formLoadingLoading[formIndex] != null)
                ORBEON.xforms.Globals.formLoadingLoading[formIndex].style.display = "none";
            if (ORBEON.xforms.Globals.formLoadingError[formIndex] != null)
                ORBEON.xforms.Globals.formLoadingError[formIndex].style.display = "block";
            if (ORBEON.xforms.Globals.formLoadingNone[formIndex] != null)
                ORBEON.xforms.Globals.formLoadingNone[formIndex].style.display = "none";
            break;
        case "none":
            if (!ORBEON.xforms.Globals.loadingOtherPage) {
                if (ORBEON.xforms.Globals.formLoadingLoading[formIndex] != null)
                    ORBEON.xforms.Globals.formLoadingLoading[formIndex].style.display = "none";
                if (ORBEON.xforms.Globals.formLoadingError[formIndex] != null)
                    ORBEON.xforms.Globals.formLoadingError[formIndex].style.display = "none";
                if (ORBEON.xforms.Globals.formLoadingNone[formIndex] != null)
                    ORBEON.xforms.Globals.formLoadingNone[formIndex].style.display = "block";
            }
            break;
    }
}

// Gets a value stored in the hidden client-state input field
function xformsGetFromClientState(formIndex, key) {
    var clientState = ORBEON.xforms.Globals.formClientState[formIndex];
    var keyValues = clientState.value.split("&");
    for (var i = 0; i < keyValues.length; i = i + 2)
        if (keyValues[i] == key)
            return unescape(keyValues[i + 1]);
    return null;
}

// Returns a value stored in the hidden client-state input field
function xformsStoreInClientState(formIndex, key, value) {
    var clientState = ORBEON.xforms.Globals.formClientState[formIndex];
    var keyValues = clientState.value == ""? new Array() : clientState.value.split("&");
    var found = false;
    // If we found the key, replace the value
    for (var i = 0; i < keyValues.length; i = i + 2) {
        if (keyValues[i] == key) {
            keyValues[i + 1] = escape(value);
            found = true;
            break;
        }
    }
    // If key is there already, replace it
    if (!found) {
        keyValues.push(key);
        keyValues.push(escape(value));
    }
    clientState.value = keyValues.join("&");
}

/**
 * Value change handling for all the controls. It is assumed that the "value" property of "target"
 * contain the current value for the control. We create a value change event and fire it by calling
 * xformsFireEvents().
 *
 * This function is in general called by xformsHandleValueChange(), and will be called directly by
 * other event handler for less usual events (e.g. slider, HTML area).
 */
function xformsValueChanged(target, other) {
    var valueChanged = target.value != target.previousValue;
    // We don't send value change events for the XForms upload control
    var isUploadControl = ORBEON.util.Dom.hasClass(target, "xforms-upload");
    if (valueChanged && !isUploadControl) {
        target.previousValue = target.value;
        var events = new Array(xformsCreateEventArray
                (target, "xxforms-value-change-with-focus-change", target.value, other));
        var incremental = other == null
                && ORBEON.util.Dom.hasClass(target, "xforms-incremental");
        xformsFireEvents(events, incremental);
    }
    return valueChanged;
}

// Handle click on trigger
function xformsHandleClick(event) {
    var target = getEventTarget(event);
    // Make sure the user really clicked on the trigger, instead of pressing enter in a nearby control
    if ((ORBEON.util.Dom.hasClass(target, "xforms-trigger") || ORBEON.util.Dom.hasClass(target, "xforms-trigger"))
            && !ORBEON.util.Dom.hasClass(target, "xforms-readonly"))
        xformsFireEvents(new Array(xformsCreateEventArray(target, "DOMActivate", null)), false);
    return false;
}

function xformsHandleAutoCompleteMouseChange(input) {
    input.parentNode.lastKeyCode = -1;
    input.parentNode.value = input.value;
    xformsValueChanged(input.parentNode, null);
}

/**
 * Root function called by any widget that wants an event to be sent to the server.
 *
 * @param events       Array of arrays with each array containing:
 *                     new Array(target, eventName, value, other)
 * @param incremental  Are these incremental events
 */
function xformsFireEvents(events, incremental) {
    var newEvents = [];
    for (var eventIndex = 0; eventIndex < events.length; eventIndex++) {
        var event = events[eventIndex];
        var target = event[0];
        var eventName = event[1];
        var value = event[2];
        var other = event[3];
        newEvents.push(new ORBEON.xforms.Server.Event(
                target == undefined ? null : ORBEON.xforms.Controls.getForm(target),
                target == undefined ? null : target.id,
                other == undefined ? null : other.id,
                value, eventName, null, null));
    }
    ORBEON.xforms.Server.fireEvents(newEvents, incremental);
}

function xformsCreateEventArray(target, eventName, value, other) {
    return new Array(target, eventName, value, other);
}

function getEventTarget(event) {
    if (event && event.LinkedField) {
        // Case of events coming from HTML area thrown by the HTML area library
        return event.LinkedField;
    } else {
        // Case of normal HTML DOM events
        event = event ? event : window.event;
        var target = event.srcElement ? event.srcElement : event.target;
        if (target.xformsElement) {
            // HTML area on Gecko: event target is the document, return the textarea
            return target.xformsElement;
        } else if (target.ownerDocument.xformsElement) {
            // HTML area on IS: event target is the body of the document, return the textarea
            return target.ownerDocument.xformsElement;
        } else {
            return target;
        }
    }
}

function xformsHtmlEditorChange(editorInstance) {
    editorInstance.LinkedField.value = editorInstance.GetXHTML();
    // Throw value change event if the field is in incremental mode
    if (ORBEON.util.Dom.hasClass(editorInstance.LinkedField, "xforms-incremental"))
        xformsValueChanged(editorInstance.LinkedField, null);
}

/**
 * Handle selection in tree that corresponds to xforms:select, where only one item can be selected.
 */
function xformsSelect1TreeSelect(id, value) {
    var control = document.getElementById(id);
    control.value = value;
    xformsValueChanged(control);
}

/**
 * Handle selection in tree that corresponds to xforms:select1, where we have checkboxes and
 * multiple nodes can be selected.
 */
function xformsSelectTreeSelect() {
    var tree = this.tree;
    var control = document.getElementById(tree.id);
    control.value = "";
    for (nodeIndex in tree._nodes) {
        var node = tree._nodes[nodeIndex];
        if (node.checked) {
            if (control.value != "") control.value += " ";
            control.value += node.data.value;
        }
    }
    xformsValueChanged(control);
}

function xformsOnMenuBarItemMouseOver() {
    var oActiveItem = this.parent.activeItem;
    // Hide any other submenus that might be visible
    if(oActiveItem && oActiveItem != this) {
        this.parent.clearActiveItem();
    }
    // Select and focus the current MenuItem instance
    this.cfg.setProperty("selected", true);
    this.focus();
    // Show the submenu for this instance
    var oSubmenu = this.cfg.getProperty("submenu");
    if(oSubmenu) {
        oSubmenu.show();
    }
}

function xformsOnMenuBarItemMouseOut(eventType, arguments) {
    this.cfg.setProperty("selected", false);
    var oSubmenu = this.cfg.getProperty("submenu");
    if(oSubmenu) {
        var oEvent = arguments[0], oRelatedTarget = YAHOO.util.Event.getRelatedTarget(oEvent);
        if(!(oRelatedTarget == oSubmenu.element
                ||  this._oDom.isAncestor(oSubmenu.element, oRelatedTarget))) {
            oSubmenu.hide();
        }
    }
}

function xformsOnDocumentMouseDown(p_oEvent) {
    ORBEON.xforms.Globals.overlayManager.hideAll();
}

/**
 * Called by FCKeditor when an editor is fully loaded. This is our opportunity
 * to listen for events on this editor.
 */
function FCKeditor_OnComplete(editorInstance) {
    // Save reference to XForms element (textarea) in document for event handlers that receive the document
    editorInstance.EditorDocument.xformsElement = editorInstance.LinkedField;
    // Register value change handler when in incremental mode
    if (ORBEON.util.Dom.hasClass(editorInstance.LinkedField, "xforms-incremental"))
        editorInstance.Events.AttachEvent("OnSelectionChange", xformsHtmlEditorChange);
    // Register focus/blur events for Gecko
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "focus", ORBEON.xforms.Events.focus);
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "blur", ORBEON.xforms.Events.blur);
    // Register focus/blur events for IE
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "focusin", ORBEON.xforms.Events.focus);
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "focusout", ORBEON.xforms.Events.blur);
    // Load other editors in the queue
    if (ORBEON.xforms.Globals.fckEditorsToLoad.length > 0) {
        var fckEditor = ORBEON.xforms.Globals.fckEditorsToLoad.shift();
        fckEditor.ReplaceTextarea();
        ORBEON.xforms.Controls.updateHTMLAreaClasses(document.getElementById(fckEditor.InstanceName));
    } else {
        ORBEON.xforms.Globals.fckEditorLoading = false;
    }
}

function xformsGetLocalName(element) {
    if (element.nodeType == 1) {
        return element.tagName.indexOf(":") == -1
            ? element.tagName
            : element.tagName.substr(element.tagName.indexOf(":") + 1);
    } else {
        return null;
    }
}

function xformsAddSuffixToIds(element, idSuffix, repeatDepth) {
    var idSuffixWithDepth = idSuffix;
    for (var repeatDepthIndex = 0; repeatDepthIndex < repeatDepth; repeatDepthIndex++)
         idSuffixWithDepth += XFORMS_SEPARATOR_2 + "1";
    if (element.id)
        element.id = xformsAppendRepeatSuffix(element.id, idSuffixWithDepth);
    if (element.htmlFor)
        element.htmlFor = xformsAppendRepeatSuffix(element.htmlFor, idSuffixWithDepth);
    if (element.name) {
        var newName = xformsAppendRepeatSuffix(element.name, idSuffixWithDepth);
        if (element.tagName.toLowerCase() == "input" && element.type.toLowerCase() == "radio"
                && ORBEON.xforms.Globals.isRenderingEngineTridend) {
            // IE supports changing the name of elements, but according to the Microsoft documentation, "This does not
            // cause the name in the programming model to change in the collection of elements". This has a implication
            // for radio buttons where using a same name for a set of radio buttons is used to group them together.
            // http://msdn.microsoft.com/library/default.asp?url=/workshop/author/dhtml/reference/properties/name_2.asp
            var clone = document.createElement("<" + element.tagName + " name='" + newName + "'>");
            for (var attributeIndex = 0; attributeIndex < element.attributes.length; attributeIndex++) {
                var attribute = element.attributes[attributeIndex];
                if (attribute.nodeName.toLowerCase() != "name" && attribute.nodeName.toLowerCase() != "height" && attribute.nodeValue)
                    clone.setAttribute(attribute.nodeName, attribute.nodeValue);
            }
            YAHOO.util.Event.addListener(clone, "focus", ORBEON.xforms.Events.focus);
            YAHOO.util.Event.addListener(clone, "blur", ORBEON.xforms.Events.blur);
            YAHOO.util.Event.addListener(clone, "change", ORBEON.xforms.Events.change);
            element.replaceNode(clone);
        } else {
            element.name = newName;
        }
    }
    // Remove references to hint, help, alert, label as they might have changed
    for (var childIndex = 0; childIndex < element.childNodes.length; childIndex++) {
        var childNode = element.childNodes[childIndex];
        if (childNode.nodeType == ELEMENT_TYPE) {
            if (childNode.id && childNode.id.indexOf("repeat-end-") == 0) repeatDepth--;
            xformsAddSuffixToIds(childNode, idSuffix, repeatDepth);
            if (childNode.id && childNode.id.indexOf("repeat-begin-") == 0) repeatDepth++
        }
    }
}

// Function to find the input element below a given node
function xformsGetInputUnderNode(node) {
    if (node.nodeType == ELEMENT_TYPE) {
        if (node.tagName.toLowerCase() == "input") {
            return node;
        } else {
            for (var childIndex = 0; childIndex < node.childNodes.length; childIndex++) {
                var result = xformsGetInputUnderNode(node.childNodes[childIndex]);
                if (result != null) return result;
            }
        }
    } else {
        return null;
    }
}

function xformsGetClassForRepeatId(repeatId) {
    var depth = 1;
    var currentRepeatId = repeatId;
    while (true) {
        currentRepeatId = ORBEON.xforms.Globals.repeatTreeChildToParent[currentRepeatId];
        if (currentRepeatId == null) break;
        depth = depth == 1 ? 2 : 1;
    }
    return "xforms-repeat-selected-item-" + depth;
}

function xformsHandleResponse(o) {
    var responseXML = o.responseXML;
    var formIndex = ORBEON.util.Dom.getFormIndex(ORBEON.xforms.Globals.requestForm);
    if (responseXML && responseXML.documentElement
            && responseXML.documentElement.tagName.indexOf("event-response") != -1) {

        // Good: we received an XML document from the server
        var responseRoot = responseXML.documentElement;
        var newDynamicState = null;
        var newDynamicStateTriggersPost = false;

        // Whether this response has triggered a load which will replace the current page.
        var newDynamicStateTriggersReplace = false;

        for (var i = 0; i < responseRoot.childNodes.length; i++) {

            // Update instances
            if (xformsGetLocalName(responseRoot.childNodes[i]) == "dynamic-state")
                newDynamicState = ORBEON.util.Dom.getStringValue(responseRoot.childNodes[i]);

            if (xformsGetLocalName(responseRoot.childNodes[i]) == "action") {
                var actionElement = responseRoot.childNodes[i];

                // Firt repeat and delete "lines" in repeat (as itemset changed below might be in a new line)
                for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {
                    var actionName = xformsGetLocalName(actionElement.childNodes[actionIndex]);
                    switch (actionName) {

                        case "control-values": {
                            var controlValuesElement = actionElement.childNodes[actionIndex];
                            for (var j = 0; j < controlValuesElement.childNodes.length; j++) {
                                var controlValueAction = xformsGetLocalName(controlValuesElement.childNodes[j]);
                                switch (controlValueAction) {

                                    // Copy repeat template
                                    case "copy-repeat-template": {
                                        var copyRepeatTemplateElement = controlValuesElement.childNodes[j];
                                        var repeatId = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "id");
                                        var parentIndexes = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "parent-indexes");
                                        var idSuffix = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "id-suffix");
                                        // Put nodes of the template in an array
                                        var templateNodes = new Array();
                                        {
                                            // Locate end of the repeat
                                            var delimiterTagName = null;
                                            var templateRepeatEnd = document.getElementById("repeat-end-" + repeatId);
                                            var templateNode = templateRepeatEnd.previousSibling;
                                            var nestedRepeatLevel = 0;
                                            while (!(nestedRepeatLevel == 0 && templateNode.nodeType == ELEMENT_TYPE
                                                     && ORBEON.util.Dom.hasClass(templateNode, "xforms-repeat-delimiter"))) {
                                                var nodeCopy = templateNode.cloneNode(true);
                                                if (templateNode.nodeType == ELEMENT_TYPE) {
                                                    // Save tag name to be used for delimiter
                                                    delimiterTagName = templateNode.tagName;
                                                    // Decrement nestedRepeatLevel when we we exit a nested repeat
                                                    if (ORBEON.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") &&
                                                            templateNode.id.indexOf("repeat-begin-") == 0)
                                                        nestedRepeatLevel--;
                                                    // Add suffix to all the ids
                                                    xformsAddSuffixToIds(nodeCopy, parentIndexes == "" ? idSuffix : parentIndexes + XFORMS_SEPARATOR_2 + idSuffix, nestedRepeatLevel);
                                                    // Increment nestedRepeatLevel when we enter a nested repeat
                                                    if (ORBEON.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") &&
                                                            templateNode.id.indexOf("repeat-end-") == 0)
                                                        nestedRepeatLevel++;
                                                    // Remove "xforms-repeat-template" from classes on copy of element
                                                    var nodeCopyClasses = nodeCopy.className.split(" ");
                                                    var nodeCopyNewClasses = new Array();
                                                    for (var nodeCopyClassIndex = 0; nodeCopyClassIndex < nodeCopyClasses.length; nodeCopyClassIndex++) {
                                                        var currentClass = nodeCopyClasses[nodeCopyClassIndex];
                                                        if (currentClass != "xforms-repeat-template")
                                                            nodeCopyNewClasses.push(currentClass);
                                                    }
                                                    nodeCopy.className = nodeCopyNewClasses.join(" ");
                                                }
                                                templateNodes.push(nodeCopy);
                                                templateNode = templateNode.previousSibling;
                                            }
                                            // Add a delimiter
                                            var newDelimiter = document.createElement(delimiterTagName);
                                            newDelimiter.className = "xforms-repeat-delimiter";
                                            templateNodes.push(newDelimiter);
                                            // Reverse nodes as they were inserted in reverse order
                                            templateNodes = templateNodes.reverse();
                                        }
                                        // Find element after insertion point
                                        var afterInsertionPoint;
                                        {
                                            if (parentIndexes == "") {
                                                // Top level repeat: contains a template
                                                var repeatEnd = document.getElementById("repeat-end-" + repeatId);
                                                var cursor = repeatEnd.previousSibling;
                                                while (!(cursor.nodeType == ELEMENT_TYPE
                                                        && ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                        && !ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-template"))) {
                                                    cursor = cursor.previousSibling;
                                                }
                                                afterInsertionPoint = cursor;
                                            } else {
                                                // Nested repeat: does not contain a template
                                                var repeatEnd = document.getElementById("repeat-end-" + xformsAppendRepeatSuffix(repeatId, parentIndexes));
                                                afterInsertionPoint = repeatEnd;
                                            }
                                        }
                                        // Insert copy of template nodes
                                        for (var templateNodeIndex in templateNodes) {
                                            templateNode = templateNodes[templateNodeIndex];
                                            afterInsertionPoint.parentNode.insertBefore(templateNode, afterInsertionPoint);
                                        }
                                        // Initialize newly added form elements
                                        if (ORBEON.xforms.Globals.isRenderingEngineWebCore13)
                                            ORBEON.xforms.Init._registerListerersOnFormElements();
                                        break;
                                    }


                                    // Delete element in repeat
                                    case "delete-repeat-elements": {
                                        // Extract data from server response
                                        var deleteElementElement = controlValuesElement.childNodes[j];
                                        var deleteId = ORBEON.util.Dom.getAttribute(deleteElementElement, "id");
                                        var parentIndexes = ORBEON.util.Dom.getAttribute(deleteElementElement, "parent-indexes");
                                        var count = ORBEON.util.Dom.getAttribute(deleteElementElement, "count");
                                        // Find end of the repeat
                                        var repeatEnd = document.getElementById("repeat-end-" + xformsAppendRepeatSuffix(deleteId, parentIndexes));
                                        // Find last element to delete
                                        var lastElementToDelete;
                                        {
                                            lastElementToDelete = repeatEnd.previousSibling;
                                            if (parentIndexes == "") {
                                                // Top-level repeat: need to go over template
                                                while (lastElementToDelete.nodeType != ELEMENT_TYPE
                                                        || !ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter"))
                                                    lastElementToDelete = lastElementToDelete.previousSibling;
                                                lastElementToDelete = lastElementToDelete.previousSibling;
                                            }
                                        }
                                        // Perform delete
                                        for (var countIndex = 0; countIndex < count; countIndex++) {
                                            while (true) {
                                                var wasDelimiter = lastElementToDelete.nodeType == ELEMENT_TYPE
                                                    && ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter");
                                                var previous = lastElementToDelete.previousSibling;
                                                lastElementToDelete.parentNode.removeChild(lastElementToDelete);
                                                lastElementToDelete = previous;
                                                if (wasDelimiter) break;
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                // Second handle the <xxforms:itemsets> actions (we want to do this before we set the value of
                // controls as the value of the select might be in the new values of the itemset).
                for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {
                    // Change values in an itemset
                    if (xformsGetLocalName(actionElement.childNodes[actionIndex]) == "itemsets") {
                        var itemsetsElement = actionElement.childNodes[actionIndex];
                        for (var j = 0; j < itemsetsElement.childNodes.length; j++) {
                            if (xformsGetLocalName(itemsetsElement.childNodes[j]) == "itemset") {
                                var itemsetElement = itemsetsElement.childNodes[j];
                                var controlId = ORBEON.util.Dom.getAttribute(itemsetElement, "id");
                                var documentElement = document.getElementById(controlId);
                                var documentElementClasses = documentElement.className.split(" ");

                                if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-open")) {
                                    // Build list with new values
                                    var newValues = new Array();
                                    for (var k = 0; k < itemsetElement.childNodes.length; k++) {
                                        var itemElement = itemsetElement.childNodes[k];
                                        if (itemElement.nodeType == ELEMENT_TYPE)
                                            newValues.push(ORBEON.util.Dom.getAttribute(itemElement, "value"));
                                    }

                                    // Case of the auto-complete control
                                    var textfield = ORBEON.util.Dom.getChildElement(documentElement, 0);
                                    textfield.actb_keywords = newValues;
                                    // Reopen auto-complete if necessary
                                    var lastKeyCode = ORBEON.xforms.Globals.autoCompleteLastKeyCode[documentElement.id];
                                    if (lastKeyCode != -1)
                                        ORBEON.xforms.Globals.autoCompleteOpen[documentElement.id](lastKeyCode);
                                } else if (documentElement.tagName == "SELECT") {

                                    // Case of list / combobox
                                    var options = documentElement.options;

                                    // Remember selected values
                                    var selectedValueCount = 0;
                                    var selectedValues = new Array();
                                    for (var k = 0; k < options.length; k++) {
                                        if (options[k].selected) {
                                            selectedValues[selectedValueCount] = options[k].value;
                                            selectedValueCount++;
                                        }
                                    }

                                    // Update select per content of itemset
                                    var itemCount = 0;
                                    for (var k = 0; k < itemsetElement.childNodes.length; k++) {
                                        var itemElement = itemsetElement.childNodes[k];
                                        if (itemElement.nodeType == ELEMENT_TYPE) {
                                            if (itemCount >= options.length) {
                                                // Add a new option
                                                var newOption = document.createElement("OPTION");
                                                documentElement.appendChild(newOption);
                                                newOption.text = ORBEON.util.Dom.getAttribute(itemElement, "label");
                                                newOption.value = ORBEON.util.Dom.getAttribute(itemElement, "value");
                                                newOption.selected = xformsArrayContains(selectedValues, newOption.value);
                                            } else {
                                                // Replace current label/value if necessary
                                                var option = options[itemCount];
                                                if (option.text != ORBEON.util.Dom.getAttribute(itemElement, "label")) {
                                                    option.text = ORBEON.util.Dom.getAttribute(itemElement, "label");
                                                }
                                                if (option.value != ORBEON.util.Dom.getAttribute(itemElement, "value")) {
                                                    option.value = ORBEON.util.Dom.getAttribute(itemElement, "value");
                                                }
                                                option.selected = xformsArrayContains(selectedValues, option.value);
                                            }
                                            itemCount++;
                                        }
                                    }

                                    // Remove options in select if necessary
                                    while (options.length > itemCount) {
                                        if (options.remove) {
                                            // For IE
                                            options.remove(options.length - 1);
                                        } else {
                                            // For Firefox
                                            var toRemove = options.item(options.length - 1);
                                            toRemove.parentNode.removeChild(toRemove);
                                        }
                                    }
                                } else {

                                    // Case of checkboxes / radio bottons

                                    // Actual values:
                                    //     <span>
                                    //         <input type="checkbox" checked="" value="v" name="xforms-element-97" id="element-97-opsitem0"/>
                                    //         <label for="xforms-element-97-opsitem0" id="xforms-element-99">Vanilla</label>
                                    //     </span>
                                    //
                                    // Template follows:
                                    //     <span>
                                    //         <input type="checkbox" value="$xforms-template-value$" name="xforms-element-97" id="xforms-element-97-opsitem0"/>
                                    //         <label for="xforms-element-97-opsitem0" id="xforms-element-99">$xforms-template-label$</label>
                                    //     </span>

                                    // Get element following control
                                    var template = documentElement.nextSibling;
                                    while (template.nodeType != ELEMENT_TYPE)
                                        template = template.nextSibling;

                                    // Get its child element (a span that contains an input)
                                    template = template.firstChild;
                                    while (template.nodeType != ELEMENT_TYPE)
                                        template = template.nextSibling;

                                    // Remove content and store current checked value
                                    var valueToChecked = new Array();
                                    while (documentElement.childNodes.length > 0) {
                                        var input = xformsGetInputUnderNode(documentElement.firstChild);
                                        valueToChecked[input.value] = input.checked;
                                        documentElement.removeChild(documentElement.firstChild);
                                    }

                                    // Recreate content based on template
                                    var itemIndex = 0;
                                    for (var k = 0; k < itemsetElement.childNodes.length; k++) {
                                        var itemElement = itemsetElement.childNodes[k];
                                        if (itemElement.nodeType == ELEMENT_TYPE) {
                                            var templateClone = template.cloneNode(true);
                                            xformsStringReplace(templateClone, "$xforms-template-label$",
                                                ORBEON.util.Dom.getAttribute(itemElement, "label"));
                                            xformsStringReplace(templateClone, "$xforms-template-value$",
                                                ORBEON.util.Dom.getAttribute(itemElement, "value"));
                                            xformsStringReplace(templateClone, "$xforms-item-index$", itemIndex);
                                            documentElement.appendChild(templateClone);
                                            // Restore checked state after copy
                                            if (valueToChecked[ORBEON.util.Dom.getAttribute(itemElement, "value")] == true)
                                                xformsGetInputUnderNode(templateClone).checked = true;
                                            itemIndex++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Handle other actions
                for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {

                    var actionName = xformsGetLocalName(actionElement.childNodes[actionIndex]);
                    switch (actionName) {

                        // Update controls
                        case "control-values": {
                            var controlValuesElement = actionElement.childNodes[actionIndex];
                            for (var j = 0; j < controlValuesElement.childNodes.length; j++) {
                                var controlValueAction = xformsGetLocalName(controlValuesElement.childNodes[j]);
                                switch (controlValueAction) {

                                    // Update control value
                                    case "control": {
                                        var controlElement = controlValuesElement.childNodes[j];
                                        var newControlValue = ORBEON.util.Dom.getStringValue(controlElement);
                                        var controlId = ORBEON.util.Dom.getAttribute(controlElement, "id");
                                        var relevant = ORBEON.util.Dom.getAttribute(controlElement, "relevant");
                                        var readonly = ORBEON.util.Dom.getAttribute(controlElement, "readonly");
                                        var required = ORBEON.util.Dom.getAttribute(controlElement, "required");
                                        var displayValue = ORBEON.util.Dom.getAttribute(controlElement, "display-value");
                                        var type = ORBEON.util.Dom.getAttribute(controlElement, "type");
                                        var documentElement = document.getElementById(controlId);
                                        var documentElementClasses = documentElement.className.split(" ");

                                        // Save new value sent by server
                                        ORBEON.xforms.Globals.serverValue[controlId] = newControlValue;

                                        // Update value
                                        var isStaticReadonly = ORBEON.util.Dom.hasClass(documentElement, "xforms-static");
                                        if (ORBEON.util.Dom.hasClass(documentElement, "xforms-output") || isStaticReadonly) {
                                            // XForms output or "static readonly" mode
                                            var newOutputControlValue = displayValue != null ? displayValue : newControlValue;
                                            if (ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-image")) {
                                                var image = ORBEON.util.Dom.getChildElement(documentElement, 0);
                                                image.src = newOutputControlValue;
                                            } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                                documentElement.innerHTML = newOutputControlValue;
                                            } else {
                                                ORBEON.util.Dom.setStringValue(documentElement, newOutputControlValue);
                                            }
                                        } else if (ORBEON.xforms.Globals.changedIdsRequest[controlId] != null) {
                                            // User has modified the value of this control since we sent our request:
                                            // so don't try to update it
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-trigger")
                                                || ORBEON.util.Dom.hasClass(documentElement, "xforms-submit")) {
                                            // Triggers don't have a value: don't update them
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-open")) {
                                            // Auto-complete
                                            if (documentElement.value != newControlValue) {
                                                documentElement.value = newControlValue;
                                                ORBEON.util.Dom.getChildElement(documentElement, 0).value = newControlValue;
                                                documentElement.previousValue = newControlValue;
                                            }
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-full")
                                                || ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-full")) {
                                            // Handle checkboxes and radio buttons
                                            var selectedValues = ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-full")
                                                ? newControlValue.split(" ") : new Array(newControlValue);
                                            var checkboxInputs = documentElement.getElementsByTagName("input");
                                            for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                                                var checkboxInput = checkboxInputs[checkboxInputIndex];
                                                checkboxInput.checked = xformsArrayContains(selectedValues, checkboxInput.value);
                                            }
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-compact")
                                                || ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-compact")
                                                || ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-minimal")) {
                                            // Handle lists and comboboxes
                                            var selectedValues = ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-compact")
                                                ? newControlValue.split(" ") : new Array(newControlValue);
                                            var options = documentElement.options;
                                            for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                                                var option = options[optionIndex];
                                                option.selected = xformsArrayContains(selectedValues, option.value);
                                            }
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-input")) {
                                            // XForms input
                                            var displayField = ORBEON.util.Dom.getChildElement(documentElement, 0);
                                            var inputField = ORBEON.util.Dom.getChildElement(documentElement, 1);
                                            var datePicker = ORBEON.util.Dom.getChildElement(documentElement, 2);

                                            // Change classes on control and date pick based on type
                                            if (type == "{http://www.w3.org/2001/XMLSchema}date") {
                                                for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
                                                    var child = documentElement.childNodes[childIndex];
                                                    ORBEON.util.Dom.addClass(child, "xforms-type-date");
                                                    ORBEON.util.Dom.removeClass(child, "xforms-type-string");
                                                }
                                            } else if (type != null && type != "{http://www.w3.org/2001/XMLSchema}date") {
                                                for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
                                                    var child = documentElement.childNodes[childIndex];
                                                    ORBEON.util.Dom.addClass(child, "xforms-type-string");
                                                    ORBEON.util.Dom.removeClass(child, "xforms-type-date");
                                                }
                                            }

                                            // Populate values
                                            if (ORBEON.util.Dom.hasClass(inputField, "xforms-type-date")) {
                                                ORBEON.util.Dom.setStringValue(displayField, (displayValue == null || displayValue == "") ? "\u00a0" : displayValue);
                                            }
                                            if (documentElement.value != newControlValue) {
                                                documentElement.previousValue = newControlValue;
                                                documentElement.valueSetByXForms++;
                                                documentElement.value = newControlValue;
                                            }
                                            if (inputField.value != newControlValue)
                                                inputField.value = newControlValue;
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea")
                                                && ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                            // HTML area
                                            var htmlEditor = FCKeditorAPI.GetInstance(documentElement.name);
                                            if (xformsNormalizeEndlines(htmlEditor.GetXHTML()) != xformsNormalizeEndlines(newControlValue)) {
                                                htmlEditor.SetHTML(newControlValue);
                                                documentElement.value = newControlValue;
                                                documentElement.previousValue = newControlValue;
                                            }
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-xxforms-tree")) {
                                            // Tree
                                            var values = newControlValue.split(" ");
                                            for (nodeIndex in documentElement.xformsTree._nodes) {
                                                var node = documentElement.xformsTree._nodes[nodeIndex];
                                                if (node.children.length == 0) {
                                                    var checked = xformsArrayContains(values, node.data.value);
                                                    if (checked) node.check(); else node.uncheck();
                                                }
                                            }
                                            documentElement.value = newControlValue;
                                            documentElement.previousValue = newControlValue;
                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-control")
                                                && typeof(documentElement.value) == "string") {
                                            // Textarea, password
                                            if (xformsNormalizeEndlines(documentElement.value) != xformsNormalizeEndlines(newControlValue)) {
                                                documentElement.value = newControlValue;
                                                documentElement.previousValue = newControlValue;
                                                // Autosize textarea
                                                if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea-appearance-xxforms-autosize")) {
                                                    ORBEON.xforms.Controls.autosizeTextarea(documentElement);
                                                }
                                            }
                                        }

                                        // Store new label message in control attribute
                                        var newLabel = ORBEON.util.Dom.getAttribute(controlElement, "label");
                                        if (newLabel != null)
                                            ORBEON.xforms.Controls.setLabelMessage(documentElement, newLabel);
                                        // Store new hint message in control attribute
                                        var newHint = ORBEON.util.Dom.getAttribute(controlElement, "hint");
                                        if (newHint != null)
                                            ORBEON.xforms.Controls.setHintMessage(documentElement, newHint);
                                        // Store new help message in control attribute
                                        var newHelp = ORBEON.util.Dom.getAttribute(controlElement, "help");
                                        if (newHelp != null)
                                            ORBEON.xforms.Controls.setHelpMessage(documentElement, newHelp);
                                        // Store new alert message in control attribute
                                        var newAlert = ORBEON.util.Dom.getAttribute(controlElement, "alert");
                                        if (newAlert != null)
                                            ORBEON.xforms.Controls.setAlertMessage(documentElement, newAlert);
                                        // Store validity, label, hint, help in element
                                        var newValid = ORBEON.util.Dom.getAttribute(controlElement, "valid");
                                        if (newValid != null) {
                                            var newIsValid = newValid != "false";
                                            ORBEON.xforms.Controls.setValid(documentElement, newIsValid);
                                        }

                                        // Handle relevance
                                        if (relevant != null) {
                                            var isRelevant = relevant == "true";
                                            ORBEON.xforms.Controls.setRelevant(documentElement, isRelevant);
                                            // Autosize textarea
                                            if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea-appearance-xxforms-autosize")) {
                                                ORBEON.xforms.Controls.autosizeTextarea(documentElement);
                                            }
                                        }

                                        // Handle required
                                        if (required != null) {
                                            var isRequired = required == "true";
                                            if (isRequired) ORBEON.util.Dom.addClass(documentElement, "xforms-required");
                                            else ORBEON.util.Dom.removeClass(documentElement, "xforms-required");
                                        }
                                        // Update the required-empty/required-full even if the required has not changed or
                                        // is not specified as the value may have changed
                                        if (!isStaticReadonly) {
                                            ORBEON.xforms.Controls.updateRequiredEmpty(documentElement);
                                        }

                                        // Handle readonly
                                        if (readonly != null && !isStaticReadonly) {
                                            function setReadonlyOnFormElement(element, isReadonly) {
                                                if (isReadonly) {
                                                    element.setAttribute("disabled", "disabled");
                                                    ORBEON.util.Dom.addClass(element, "xforms-readonly");
                                                } else {
                                                    element.removeAttribute("disabled");
                                                    ORBEON.util.Dom.removeClass(element, "xforms-readonly");
                                                }
                                            }

                                            var isReadonly = readonly == "true";
                                            if (ORBEON.util.Dom.hasClass(documentElement, "xforms-input")) {
                                                // XForms input

                                                // Display value
                                                var displaySpan = documentElement.firstChild;
                                                while (displaySpan.nodeType != ELEMENT_TYPE) displaySpan = displaySpan.nextSibling;
                                                if (isReadonly) ORBEON.util.Dom.addClass(displaySpan, "xforms-readonly");
                                                else ORBEON.util.Dom.removeClass(displaySpan, "xforms-readonly");

                                                // Text field
                                                var textField = displaySpan.nextSibling;
                                                while (textField.nodeType != ELEMENT_TYPE) textField = textField.nextSibling;
                                                if (isReadonly) textField.setAttribute("disabled", "disabled");
                                                else textField.removeAttribute("disabled");

                                                // Calendar picker
                                                var showCalendar = textField.nextSibling;
                                                while (showCalendar.nodeType != ELEMENT_TYPE) showCalendar = showCalendar.nextSibling;
                                                if (isReadonly) ORBEON.util.Dom.addClass(showCalendar, "xforms-showcalendar-readonly");
                                                else ORBEON.util.Dom.removeClass(showCalendar, "xforms-showcalendar-readonly");
                                            } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-output")
                                                    || ORBEON.util.Dom.hasClass(documentElement, "xforms-group")) {
                                                // XForms output and group
                                                if (isReadonly) ORBEON.util.Dom.addClass(documentElement, "xforms-readonly");
                                                else ORBEON.util.Dom.removeClass(documentElement, "xforms-readonly");
                                            } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-full")
                                                    || ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-full")) {
                                                // XForms radio buttons
                                                for (var spanIndex = 0; spanIndex < documentElement.childNodes.length; spanIndex++) {
                                                    var span = documentElement.childNodes[spanIndex];
                                                    var input = span.firstChild;
                                                    setReadonlyOnFormElement(input, isReadonly);
                                                }
                                            } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea") && ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                                // XForms HTML area
                                                var htmlEditor = FCKeditorAPI.GetInstance(documentElement.name);
                                                if (isReadonly) {
                                                    htmlEditor.ToolbarSet.Collapse();
                                                    // TO-DO
                                                } else {
                                                    htmlEditor.ToolbarSet.Expand();
                                                    // TO-DO
                                                }
                                            } else {
                                                // Other controls
                                                setReadonlyOnFormElement(documentElement, isReadonly);
                                            }
                                        }

                                        // After we update classes on textarea, copy those classes on the FCKeditor iframe
                                        if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea")
                                                && ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                            ORBEON.xforms.Controls.updateHTMLAreaClasses(documentElement);
                                        }

                                        break;
                                    }

                                    // Model item properties on a repeat item
                                    case "repeat-iteration": {
                                        // Extract data from server response
                                        var repeatIterationElement = controlValuesElement.childNodes[j];
                                        var repeatId = ORBEON.util.Dom.getAttribute(repeatIterationElement, "id");
                                        var iteration = ORBEON.util.Dom.getAttribute(repeatIterationElement, "iteration");
                                        var relevant = ORBEON.util.Dom.getAttribute(repeatIterationElement, "relevant");
                                        // Remove or add xforms-disabled on elements after this delimiter
                                        var cursor = xformsFindRepeatDelimiter(repeatId, iteration).nextSibling;
                                        while (!(cursor.nodeType == ELEMENT_TYPE &&
                                                 (ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                    || ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-begin-end")))) {
                                            if (cursor.nodeType == ELEMENT_TYPE) {
                                                if (relevant) {
                                                    if (relevant == "true") ORBEON.util.Dom.removeClass(cursor, "xforms-disabled");
                                                    else ORBEON.util.Dom.addClass(cursor, "xforms-disabled");
                                                }
                                            }
                                            cursor = cursor.nextSibling;
                                        }
                                        break;
                                    }
                                }
                            }
                            break;
                        }

                        // Display or hide divs
                        case "divs": {
                            var divsElement = actionElement.childNodes[actionIndex];
                            for (var j = 0; j < divsElement.childNodes.length; j++) {
                                if (xformsGetLocalName(divsElement.childNodes[j]) == "div") {
                                    var divElement = divsElement.childNodes[j];
                                    var controlId = ORBEON.util.Dom.getAttribute(divElement, "id");
                                    var visibile = ORBEON.util.Dom.getAttribute(divElement, "visibility") == "visible";

                                    var yuiDialog = ORBEON.xforms.Globals.dialogs[controlId];
                                    if (yuiDialog == null) {
                                        // This is a case, not a dialog
                                        var caseBeginId = "xforms-case-begin-" + controlId;
                                        var caseBegin = document.getElementById(caseBeginId);
                                        var caseBeginParent = caseBegin.parentNode;
                                        var foundCaseBegin = false;
                                        for (var childId = 0; caseBeginParent.childNodes.length; childId++) {
                                            var cursor = caseBeginParent.childNodes[childId];
                                            if (!foundCaseBegin) {
                                                if (cursor.id == caseBegin.id) foundCaseBegin = true;
                                                else continue;
                                            }
                                            if (cursor.nodeType == ELEMENT_TYPE) {
                                                if (cursor.id == "xforms-case-end-" + controlId) break;
                                                ORBEON.util.Dom.addClass(cursor, visibile ? "xforms-case-selected" : "xforms-case-deselected");
                                                ORBEON.util.Dom.removeClass(cursor, visibile ? "xforms-case-deselected" : "xforms-case-selected");
                                            }
                                        }
                                    } else {
                                        // This is a dialog
                                        if (visibile) yuiDialog.show();
                                        else yuiDialog.hide();
                                    }
                                }
                            }

                            // After we display divs, we must reenable the HTML editors.
                            // This is a workaround for a Gecko bug documented at:
                            // http://wiki.fckeditor.net/Troubleshooting#gecko_hidden_div
                            if (XFORMS_IS_GECKO && ORBEON.xforms.Globals.htmlAreaNames.length > 0) {
                                for (var htmlAreaIndex = 0; htmlAreaIndex < ORBEON.xforms.Globals.htmlAreaNames.length; htmlAreaIndex++) {
                                    var name = ORBEON.xforms.Globals.htmlAreaNames[htmlAreaIndex];
                                    var editor = FCKeditorAPI.GetInstance(name);
                                    try {
                                        editor.EditorDocument.designMode = "on";
                                    } catch (e) {
                                        // Nop
                                    }
                                }
                            }

                            break;
                        }

                        // Change highlighted section in repeat
                        case "repeat-indexes": {
                            var repeatIndexesElement = actionElement.childNodes[actionIndex];
                            var newRepeatIndexes = new Array();
                            // Extract data from server response
                            for (var j = 0; j < repeatIndexesElement.childNodes.length; j++) {
                                if (xformsGetLocalName(repeatIndexesElement.childNodes[j]) == "repeat-index") {
                                    var repeatIndexElement = repeatIndexesElement.childNodes[j];
                                    var repeatId = ORBEON.util.Dom.getAttribute(repeatIndexElement, "id");
                                    var newIndex = ORBEON.util.Dom.getAttribute(repeatIndexElement, "new-index");
                                    newRepeatIndexes[repeatId] = newIndex;
                                }
                            }
                            // For each repeat id that changes, see if all the children are also included in
                            // newRepeatIndexes. If they are not, add an entry with the index unchanged.
                            for (var repeatId in newRepeatIndexes) {
                                var children = ORBEON.xforms.Globals.repeatTreeParentToAllChildren[repeatId];
                                for (var childIndex in children) {
                                    var child = children[childIndex];
                                    if (!newRepeatIndexes[child])
                                        newRepeatIndexes[child] = ORBEON.xforms.Globals.repeatIndexes[child];
                                }
                            }
                            // Unhighlight items at old indexes
                            for (var repeatId in newRepeatIndexes) {
                                var oldIndex = ORBEON.xforms.Globals.repeatIndexes[repeatId];
                                if (oldIndex != 0) {
                                    var oldItemDelimiter = xformsFindRepeatDelimiter(repeatId, oldIndex);
                                    if (oldItemDelimiter != null) {
                                        var cursor = oldItemDelimiter.nextSibling;
                                        while (cursor.nodeType != ELEMENT_TYPE ||
                                               (!ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                               && !ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-begin-end"))) {
                                            if (cursor.nodeType == ELEMENT_TYPE)
                                                ORBEON.util.Dom.removeClass(cursor, xformsGetClassForRepeatId(repeatId));
                                            cursor = cursor.nextSibling;
                                        }
                                    }
                                }
                            }
                            // Store new indexes
                            for (var repeatId in newRepeatIndexes) {
                                var newIndex = newRepeatIndexes[repeatId];
                                ORBEON.xforms.Globals.repeatIndexes[repeatId] = newIndex;
                            }
                            // Highlight item a new index
                            for (var repeatId in newRepeatIndexes) {
                                var newIndex = newRepeatIndexes[repeatId];
                                if (newIndex != 0) {
                                    var newItemDelimiter = xformsFindRepeatDelimiter(repeatId, newIndex);
                                    var cursor = newItemDelimiter.nextSibling;
                                    while (cursor.nodeType != ELEMENT_TYPE ||
                                           (!ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                           && !ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-begin-end"))) {
                                        if (cursor.nodeType == ELEMENT_TYPE)
                                            ORBEON.util.Dom.addClass(cursor, xformsGetClassForRepeatId(repeatId));
                                        cursor = cursor.nextSibling;
                                    }
                                }
                            }
                            break;
                        }

                        // Submit form
                        case "submission": {
                            var submissionElement = actionElement.childNodes[actionIndex];
                            var showProcess = ORBEON.util.Dom.getAttribute(submissionElement, "show-progress");
                            // Display loading indicator unless the server tells us not to display it
                            if (showProcess != "false")
                                newDynamicStateTriggersReplace = true;
                            newDynamicStateTriggersPost = true;
                            ORBEON.xforms.Globals.formDynamicState[formIndex].value = newDynamicState;
                            ORBEON.xforms.Globals.requestForm.submit();
                            break;
                        }

                        // Display modal message
                        case "message": {
                            var messageElement = actionElement.childNodes[actionIndex];
                            var message = ORBEON.util.Dom.getStringValue(messageElement);
                            if (ORBEON.util.Dom.getAttribute(messageElement, "level") == "modal")
                                alert(message);
                            break;
                        }

                        // Load another page
                        case "load": {
                            var loadElement = actionElement.childNodes[actionIndex];
                            var resource = ORBEON.util.Dom.getAttribute(loadElement, "resource");
                            var show = ORBEON.util.Dom.getAttribute(loadElement, "show");
                            var target = ORBEON.util.Dom.getAttribute(loadElement, "target");
                            var showProcess = ORBEON.util.Dom.getAttribute(loadElement, "show-progress");
                            if (show == "replace") {
                                if (target == null) {
                                    // Display loading indicator unless the server tells us not to display it
                                    if (showProcess != "false")
                                        newDynamicStateTriggersReplace = true;
                                    window.location.href = resource;
                                } else {
                                    window.open(resource, target);
                                }
                            } else {
                                window.open(resource, "_blank");
                            }
                            break;
                        }

                        // Set focus to a control
                        case "setfocus": {
                            var setfocusElement = actionElement.childNodes[actionIndex];
                            var controlId = ORBEON.util.Dom.getAttribute(setfocusElement, "control-id");
                            var control = document.getElementById(controlId);
                            if (ORBEON.util.Dom.hasClass(control, "xforms-input"))
                                control = ORBEON.util.Dom.getChildElement(control, 1);
                            ORBEON.xforms.Globals.maskFocusEvents = true;
                            control.focus();
                            break;
                        }

                        // Run JavaScript code
                        case "script": {
                            var scriptElement = actionElement.childNodes[actionIndex];
                            var functionName = ORBEON.util.Dom.getAttribute(scriptElement, "name");
                            var targetId = ORBEON.util.Dom.getAttribute(scriptElement, "target-id");
                            var observerId = ORBEON.util.Dom.getAttribute(scriptElement, "observer-id");
                            ORBEON.xforms.Server.callUserScript(functionName, targetId, observerId);
                            break;
                        }
                    }
                }
            }
        }

        // Store new dynamic state if that state did not trigger a post
        if (!newDynamicStateTriggersPost) {
            xformsStoreInClientState(formIndex, "ajax-dynamic-state", newDynamicState);
        }

        if (newDynamicStateTriggersReplace) {
            // Display loading indicator when we go to another page.
            // Display it even if it was not displayed before as loading the page could take time.
            xformsDisplayIndicator("loading");
            ORBEON.xforms.Globals.loadingOtherPage = true;
        }
        ORBEON.xforms.Globals.lastRequestIsError = false;

    } else if (responseXML && responseXML.documentElement
            && responseXML.documentElement.tagName.indexOf("exceptions") != -1) {
        // We received an error from the server

        // Find an error message starting from the inner-most exception
        var errorMessage = "XForms error";
        var messageElements = responseXML.getElementsByTagName("message");
        for (var messageIndex = messageElements.length - 1; messageIndex >= 0; messageIndex--) {
            if (messageElements[messageIndex].firstChild != null) {
                errorMessage += ": " + ORBEON.util.Dom.getStringValue(messageElements[messageIndex]);
                break;
            }
        }

        // Display error
        var errorContainer = ORBEON.xforms.Globals.formLoadingError[formIndex];
        ORBEON.util.Dom.setStringValue(errorContainer, errorMessage);
        ORBEON.xforms.Globals.lastRequestIsError = true;
        xformsDisplayIndicator("error");
    } else {
        // The server didn't send valid XML
        var errorContainer = ORBEON.xforms.Globals.formLoadingError[formIndex];
        errorContainer.innerHTML = "Unexpected response received from server";
        ORBEON.xforms.Globals.lastRequestIsError = true;
        xformsDisplayIndicator("error");
    }

    // Reset changes, as changes are included in this bach of events
    ORBEON.xforms.Globals.changedIdsRequest = {};

    // Go ahead with next request, if any
    ORBEON.xforms.Globals.requestInProgress = false;
    ORBEON.xforms.Globals.executeEventFunctionQueued++;
    ORBEON.xforms.Server.executeNextRequest(false);
}

function xformsDisplayLoading() {
    if (ORBEON.xforms.Globals.requestInProgress == true)
        xformsDisplayIndicator("loading");
}

// Run xformsPageLoaded when the browser has finished loading the page
// In case this script is loaded twice, we still want to run the initialization only once
if (!ORBEON.xforms.Globals.pageLoadedRegistered) {
    ORBEON.xforms.Globals.pageLoadedRegistered = true;
    YAHOO.util.Event.addListener(window, "load", ORBEON.xforms.Init.document);
}
ORBEON.xforms.Globals.debugLastTime = new Date().getTime();
