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
var XFORMS_DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_IN_MS = 5000;
var XFORMS_DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_IN_MS = 5000;
var XFORMS_DELAY_BEFORE_AJAX_TIMEOUT_IN_MS = -1;
var XFORMS_INTERNAL_SHORT_DELAY_IN_MS = 10;
var XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS = 500;
var XFORMS_REQUEST_RETRIES = 3;
var XFORMS_DEBUG_WINDOW_HEIGHT = 600;
var XFORMS_DEBUG_WINDOW_WIDTH = 300;
var XFORMS_LOADING_MIN_TOP_PADDING = 10;
var XFORMS_SESSION_HEARTBEAT = true;

// NOTE: Default values below MUST match the ones in XFormsProperties
var XFORMS_SESSION_HEARTBEAT_DELAY = 30 * 60 * 800; // 80 % of 30 minutes in ms
var FCK_EDITOR_BASE_PATH = "/ops/fckeditor/";
var YUI_BASE_PATH = "/ops/images/yui/";

/**
 * Constants
 */
var XFORMS_SEPARATOR_1 = "\xB7";
var XFORMS_SEPARATOR_2 = "-";
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var BASE_URL = null;
var XFORMS_SERVER_URL = null;
var PATH_TO_JAVASCRIPT_1 = "/ops/javascript/xforms";
var PATH_TO_JAVASCRIPT_2 = "/xforms-server/";
var XFORMS_IS_GECKO = navigator.userAgent.toLowerCase().indexOf("gecko") != -1;
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var ATTRIBUTE_TYPE = document.createAttribute("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;
var XFORMS_REGEXP_CR = new RegExp("\\r", "g");
var XFORMS_REGEXP_SINGLE_QUOTE = new RegExp("'", "g");
var XFORMS_REGEXP_OPEN_ANGLE = new RegExp("<", "g");
var XFORMS_REGEXP_AMPERSAND = new RegExp("&", "g");
var XFORMS_WIDE_TEXTAREA_MIN_ROWS = 5;

// These variables are not set by default, but if set will be used by this code:
//
//     FCK_CUSTOM_CONFIG
//     USER_LANGUAGE

/* * * * * * Utility functions * * * * * */

var ORBEON = ORBEON || {};
ORBEON.util = ORBEON.util || {};
ORBEON.xforms = ORBEON.xforms || {};

/**
 * Global constants and variable
 */
ORBEON.xforms.Globals = ORBEON.xforms.Globals || {
    // Booleans used for browser detection
    isMac : navigator.userAgent.toLowerCase().indexOf("macintosh") != -1,                 // Running on Mac
    isRenderingEngineGecko: navigator.userAgent.toLowerCase().indexOf("gecko") != -1,     // Firefox
    isFF3: navigator.userAgent.toLowerCase().indexOf("firefox/3") != -1,                  // Firefox 3.0
    isRenderingEnginePresto: navigator.userAgent.toLowerCase().indexOf("opera") != -1,    // Opera
    isRenderingEngineWebCore: navigator.userAgent.toLowerCase().indexOf("safari") != -1,  // Safari
    isRenderingEngineWebCore13: navigator.userAgent.indexOf("AppleWebKit/312") != -1,     // Safari 1.3
    isRenderingEngineTridend: navigator.userAgent.toLowerCase().indexOf("msie") != -1     // Internet Explorer
        && navigator.userAgent.toLowerCase().indexOf("opera") == -1,

    /**
     * All the browsers support events in the capture phase, except IE and Safari 1.3. When browser don't support events
     * in the capture phase, we need to register a listener for certain events on the elements itself, instead of
     * just registering the event handler on the window object.
     */
    supportsCaptureEvents: window.addEventListener && navigator.userAgent.indexOf("AppleWebKit/312") == -1,
    eventQueue: [],                      // Events to be sent to the server
    eventsFirstEventTime: 0,             // Time when the first event in the queue was added
    requestForm: null,                   // HTML for the request currently in progress
    requestIgnoreErrors: false,          // Should we ignore errors that result from running this request
    requestInProgress: false,            // Indicates wether an Ajax request is currently in process
    requestDocument: "",                 // The last Ajax request, so we can resend it if necessary
    requestRetries: 3,                   // How many retries we have left before we give up with this Ajax request
    executeEventFunctionQueued: 0,       // Number of ORBEON.xforms.Server.executeNextRequest waiting to be executed
    maskFocusEvents: false,              // Avoid catching focus event when we do it because the server told us to
    previousDOMFocusOut: null,           // We only send a focus out when we receive a focus in, or another focus out
    htmlAreaNames: [],                   // Names of the FCK editors, which we need to reenable them on Firefox
    repeatTreeChildToParent: [],         // Describes the repeat hierarchy
    repeatIndexes: {},                   // The current index for each repeat
    repeatTreeParentToAllChildren: {},   // Map from parent to array with children, used when highlight changes
    inputCalendarCreated: {},            // Maps input id to true when the calendar has been created for that input
    inputCalendarOnclick: {},            // Maps input id to the JSCalendar function that displays the calendar
    inputCalendarCommitedValue: {},      // Maps input id to the value of JSCalendar actually selected by the user
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
    dialogMinimalVisible: {},            // Map for minimal dialog id -> boolean isVisible
    dialogMinimalLastMouseOut: {},       // Map for minimal dialog id -> -1 or timestamp of last time the mouse got out of the dialog
    hintTooltipForControl: {},           // Map from element id -> YUI tooltip or true, that tells us if we have already created a Tooltip for an element
    alertTooltipForControl: {},          // Map from element id -> YUI alert or true, that tells us if we have already created a Tooltip for an element
    debugDiv: null,                      // Points to the div when debug messages are displayed
    debugLastTime: new Date().getTime(), // Timestamp when the last debug message was printed
    pageLoadedRegistered: false,         // If the page loaded listener has been registered already, to avoid running it more than once
    menuItemsets: {},                    // Maps menu id to structure defining the content of the menu
    menuYui: {},                         // Maps menu id to the YUI object for that menu
    treeYui: {},                         // Maps tree id to the YUI object for that tree
    idToElement: {},                     // Maintain mapping from ID to element, so we don't lookup the sme ID more than once

    // Data relative to a form is stored in an array indexed by form id.
    formLoadingLoadingOverlay: {},       // Overlay for the loading indicator
    formLoadingLoadingInitialRightTop:{},// Initial number of pixel between the loading indicator and the top of the page
    formErrorPanel: {},                  // YUI panel used to report errors
    formHelpPanel: {},                   // Help dialog: YUI panel
    formHelpPanelMessageDiv: {},         // Help dialog: div containing the help message
    formHelpPanelCloseButton: {},        // Help dialog: close button
    formLoadingNone: {},                 // HTML element with the markup displayed when nothing is displayed
    formStaticState: {},                 // State that does not change for the life of the page
    formDynamicState: {},                // State that changes at every request
    formServerEvents: {},                // Server events information
    formClientState: {}                  // Store for information we want to keep when the page is reloaded
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
            // Setting the class with setAttribute("class", newClassName) doesn't work on IE6 and IE7
            element.className = newClassName;
        }
    },

    /**
     * Orbeon version of getting Elements by Name in IE
     */
    getElementsByName: function(element,localName,namespace) {
        return element.getElementsByTagName(namespace+":"+localName);
    }
};


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
    },

    /**
     * Optimized version of getting Elements by Name on Mozilla
     * Firefox 2 assumes there are no other elements with the
     * same local name that are in a different namespace. This has
     * been fixed in Firefox 3. See https://bugzilla.mozilla.org/show_bug.cgi?id=206053
     */
    getElementsByName: function(element, localName, namespace) {
        return element.getElementsByTagName((ORBEON.xforms.Globals.isFF3? namespace +":" : "") +localName);
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

    /**
     * This function should be used instead of document.getElementById for 2 reasons:
     *
     * 1) This gets around a bug in IE and Opera 8.2 where getElementById by return an element with name equal to
     *    the specified id (instead of id equal to the specified id).
     *    See: http://www.csb7.com/test/ie_getelementbyid_bug/index.php
     *
     * 2) This performs caching of element ids, so when the same element is requested many times in a row we'll be
     *    able to respond just by looking at the cache, instead of calling document.getElementById. This has a=
     *    significant impact in particular when copying many repeat items on Firefox.
     */
    getElementById: function(controlId) {
        var result = ORBEON.xforms.Globals.idToElement[controlId];
        if (result == null || result.id != controlId) {
            result = ORBEON.util.Dom.getElementByIdNoCache(controlId);
            if (result != null)
                ORBEON.xforms.Globals.idToElement[controlId] = result;
        }
        return result;
    },

    getElementByIdNoCache: function(controlId) {
        var result = document.getElementById(controlId);
        if (result && (result.id != controlId) && document.all) {
            result = null;
            documentAll = document.all[controlId];
            if (documentAll) {
                if (documentAll.length) {
                    for (var i = 0; i < documentAll.length; i++) {
                        if (documentAll[i].id == controlId) {
                            result = documentAll[i];
                            break;
                        }
                    }
                } else {
                    result = documentAll;
                }
            }
        }
        return result;
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

    getChildElementByIndex: function(parent, position) {
        for (var i = 0; i < parent.childNodes.length; i++) {
            var child = parent.childNodes[i];
            if (ORBEON.util.Dom.isElement(child)) {
                if (position == 0) return child;
                position--;
            }
        }
        return null;
    },

    getChildElementByClass: function(parent, clazz) {
        for (var i = 0; i < parent.childNodes.length; i++) {
            var child = parent.childNodes[i];
            if (ORBEON.util.Dom.isElement(child) && ORBEON.util.Dom.hasClass(child, clazz)) {
                return child;
            }
        }
        return null;
    },

    stringToDom: function(xmlString) {
        if (document.implementation.createDocument) {
            return (new DOMParser()).parseFromString(xmlString, "application/xml")
        } else if (window.ActiveXObject) {
            var dom  = new ActiveXObject("Microsoft.XMLDOM");
            dom.async="false";
            dom.loadXML(xmlString);
            return dom;
        }
        return null;
    },

    clearUploadControl: function(uploadElement) {

        var inputElement = ORBEON.util.Dom.getChildElementByClass(uploadElement, "xforms-upload-select");
        var parentElement = inputElement.parentNode;
        var newInputElement = document.createElement("input");
        ORBEON.util.Dom.addClass(newInputElement, inputElement.className);
        newInputElement.setAttribute("type", inputElement.type);
        newInputElement.setAttribute("name", inputElement.name);
        newInputElement.setAttribute("size", inputElement.size);
        parentElement.replaceChild(newInputElement, inputElement);
        // For non-w3c compliant browsers we must re-register listeners on the new upload element we just created
        if (! ORBEON.xforms.Globals.supportsCaptureEvents) {
            ORBEON.xforms.Init.registerListenersOnFormElement(newInputElement);
        }

        return null;
    },

    /**
     * Use W3C DOM API to get the content of an element.
     */
    getStringValue: function(element) {
        if (element.innerText == null) {
            // Use W3C DOM API
            var result = "";
            for (var i = 0; i < element.childNodes.length; i++) {
                var child = element.childNodes[i];
                if (child.nodeType == TEXT_TYPE)
                    result += child.nodeValue;
            }
            return result;
        } else {
            // Use IE's innerText, which is faster on IE
            return element.innerText;
        }
    },

    /**
     * Use W3C DOM API to set the content of an element.
     */
    setStringValue: function(element, text) {
        if (element.innerText == null) {
            // Use W3C DOM API
            // Remove content
            while (element.childNodes.length > 0)
                element.removeChild(element.firstChild);
            // Add specified text
            var textNode = element.ownerDocument.createTextNode(text);
            element.appendChild(textNode);
        } else {
            // Use IE's innerText, which is faster on IE
            element.innerText = text;
        }
    }

};

(function () {
    var methodsFrom = ORBEON.xforms.Globals.isRenderingEngineTridend ? ORBEON.util.IEDom : ORBEON.util.MozDom;
    for (var method in methodsFrom)
        ORBEON.util.Dom[method] = methodsFrom[method];
}());

/**
 * General purpose methods on string
 */
ORBEON.util.String = {
    replace: function(text, placeholder, replacement) {
        // Don't try to do the replacement if the string does not contain the placeholder
        return text.indexOf(placeholder) == -1 ? text :
               text.replace(new RegExp(placeholder, "g"), replacement);
    },

    /**
     * Evaluates JavaScript which can contain return caracters we need to remove
     */
    eval: function(javascriptString) {
        javascriptString = ORBEON.util.String.replace(javascriptString, "\n", " ");
        javascriptString = ORBEON.util.String.replace(javascriptString, "\r", " ");
        return eval(javascriptString);
    },

    /**
     * Escape text that apears in an HTML attribute which we use in an innerHTML.
     */
    escapeAttribute: function(text) {
        return ORBEON.util.String.replace(text, '"', '&quot;');
    },

    /**
     * Escape text that apears in an HTML attribute which we use in an innerHTML.
     */
    escapeHTMLMinimal: function(text) {
        text = ORBEON.util.String.replace(text, '&', '&amp;');
        return ORBEON.util.String.replace(text, '<', '&lt;');
    }
}

/**
 * Utility methods that don't in any other category
 */
ORBEON.util.Utils = {
    logException: function(message, exception) {
        if (typeof console != "undefined") {
            console.log(message); // Normal use; do not remove
            console.log(exception);  // Normal use; do not remove
        }
    }
}

/**
 * This object contains function generally designed to be called from JavaScript code
 * embedded in forms.
 */
ORBEON.xforms.Document = {

    /**
     * Reference: http://www.w3.org/TR/xforms/slice10.html#action-dispatch
     */
    dispatchEvent: function(targetId, eventName, form, bubbles, cancelable, incremental, ignoreErrors) {

        // Use the first XForms form on the page when no form is provided
        if (form == null) {
            for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
                var candidateForm = document.forms[formIndex];
                if (ORBEON.util.Dom.hasClass(candidateForm, "xforms-form")) {
                    form = candidateForm;
                    break;
                }
            }
        }

        // Create event and fire
        var event = new ORBEON.xforms.Server.Event(form, targetId, null, null, eventName, bubbles, cancelable, ignoreErrors);
        ORBEON.xforms.Server.fireEvents([event], incremental == undefined ? false : incremental);
    },

    /**
     * Returns the value of an XForms control.
     *
     * @param {String} controlId    Id of the control
     */
    getValue: function(controlId) {
        var control = ORBEON.util.Dom.getElementById(controlId);
        return ORBEON.xforms.Controls.getCurrentValue(control);
    },

    /**
     * Set the value of an XForms control.
     *
     * @param {String} controlId    Id of the control
     * @param {String} newValue     New value for the control
     */
    setValue: function(controlId, newValue) {
        var control = ORBEON.util.Dom.getElementById(controlId);
        if (control == null) throw "ORBEON.xforms.Document.setValue: can't find control id '" + controlId + "'";
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
        if (ORBEON.util.Dom.hasClass(control, "xforms-input") && !ORBEON.util.Dom.hasClass(control, "xforms-type-boolean")) {
            return ORBEON.util.Dom.getChildElementByIndex(control, 1).value;
        } if (ORBEON.util.Dom.hasClass(control, "xforms-select1-open")) {
            return ORBEON.util.Dom.getChildElementByIndex(control, 0).value;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || ORBEON.util.Dom.hasClass(control, "xforms-input-appearance-full")) {
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
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-compact")
                || ORBEON.util.Dom.hasClass(control, "xforms-input-appearance-minimal")
                || ORBEON.util.Dom.hasClass(control, "xforms-input-appearance-compact")) {
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
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-textarea")
                        && ORBEON.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            var editorInstance = FCKeditorAPI.GetInstance(control.name);
            return editorInstance.GetXHTML();
        } else {
            return control.value;
        }
    },

    _getControlLabel: function(control, className) {
        var candidate = control;
        while (true) {
            if (candidate == null) break;
            if (ORBEON.util.Dom.isElement(candidate)
                    && ORBEON.util.Dom.hasClass(candidate, className)
                    && (candidate.htmlFor == null || candidate.htmlFor == control.id)) break;
            candidate = className == "xforms-label" ? candidate.previousSibling : candidate.nextSibling;
        }
        return candidate;
    },

    _setMessage: function(control, className, message) {
        var label = ORBEON.xforms.Controls._getControlLabel(control, className);
        if (label != null) {
            label.innerHTML = message;
            var helpImage = ORBEON.xforms.Controls._getControlLabel(control, "xforms-help-image");
            if (message == "") {
                // Hide help, label, hint and alert with empty content
                ORBEON.util.Dom.addClass(label, "xforms-disabled");
                // If this is the help label, also disable help image
                if (className == "xforms-help")
                    ORBEON.util.Dom.addClass(helpImage, "xforms-disabled");
            } else {
                // We show help, label, hint and alert with non-empty content, but ONLY if the control is relevant
                if (ORBEON.xforms.Controls.isRelevant(control)) {
                    ORBEON.util.Dom.removeClass(label, "xforms-disabled");
                    // If this is the help label, also enable the help image
                    if (className == "xforms-help")
                        ORBEON.util.Dom.removeClass(helpImage, "xforms-disabled");
                }
            }
        }
    },

    setLabelMessage: function(control, message) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-trigger")
                || ORBEON.util.Dom.hasClass(control, "xforms-submit")) {
            if (control.tagName.toLowerCase() == "input") {
                // Image
                control.alt = message;
            } else {
                // Link or button
                control.innerHTML = message;
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-dialog")) {
            // Dialog
            var labelDiv = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            labelDiv.innerHTML = message;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-group-appearance-xxforms-fieldset")) {
            // Group with fieldset/legend
            var legend = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            if (legend != null)
                legend.innerHTML = message;
        } else {
            ORBEON.xforms.Controls._setMessage(control, "xforms-label", message);
        }
    },

    getHelpMessage: function(control) {
        var helpElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-help");
        return helpElement == null ? "" : ORBEON.util.Dom.getStringValue(helpElement);
    },

    setHelpMessage: function(control, message) {
        // We escape the value because the help element is a little special, containing escaped HTML
        ORBEON.xforms.Controls._setMessage(control, "xforms-help",  ORBEON.util.String.escapeHTMLMinimal(message));
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

        if (ORBEON.util.Dom.hasClass(control, "xforms-group-begin-end")) {
            // Case of group delimiters

            // Figure out id of the end delimiter
            var beginMarkerPrefix = "group-begin-";
            var id = control.id.substring(beginMarkerPrefix.length);
            var endMarker = "group-end-" + id;

            // Iterate over nodes until we find the end delimiter
            var current = control.nextSibling;
            while (true) {
                if (ORBEON.util.Dom.isElement(current)) {
                    if (current.id == endMarker) break;
                    if (isRelevant) ORBEON.util.Dom.removeClass(current, "xforms-disabled");
                    else ORBEON.util.Dom.addClass(current, "xforms-disabled");
                }
                current = current.nextSibling;
            }
        } else {
            var elementsToUpdate = [ control,
                ORBEON.xforms.Controls._getControlLabel(control, "xforms-label"),
                ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert")
            ];
            // Also show help if message is not empty
            if (!isRelevant || (isRelevant && ORBEON.xforms.Controls.getHelpMessage(control) != "")) {
                elementsToUpdate.push(ORBEON.xforms.Controls._getControlLabel(control, "xforms-help"));
                elementsToUpdate.push(ORBEON.xforms.Controls._getControlLabel(control, "xforms-help-image"));
            }
            // Also show hint if message is not empty
            if (!isRelevant || (isRelevant && ORBEON.xforms.Controls.getHintMessage(control) != ""))
                elementsToUpdate.push(ORBEON.xforms.Controls._getControlLabel(control, "xforms-hint"));
            for (var elementIndex = 0; elementIndex < elementsToUpdate.length; elementIndex++) {
                var element = elementsToUpdate[elementIndex];
                if (element != null) {
                    if (isRelevant) ORBEON.util.Dom.removeClass(element, "xforms-disabled");
                    else ORBEON.util.Dom.addClass(element, "xforms-disabled");
                }
            }
        }
    },

    getAlertMessage: function(control) {
        var alertElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert");
        return alertElement.innerHTML;
    },

    setAlertMessage: function(control, message) {
        ORBEON.xforms.Controls._setMessage(control, "xforms-alert", message);
        ORBEON.xforms.Controls._setHintAlertMessage(control, message, ORBEON.xforms.Globals.alertTooltipForControl);
    },

    getHintMessage: function(control) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-trigger")) {
            return control.title;
        } else {
            // Label for hint either has class="xforms-hint"
            var hintElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-hint");
            return hintElement == null ? "" : hintElement.innerHTML;
        }
    },

    setHintMessage: function(control, message) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-trigger")) {
            control.title = message;
        } else {
            ORBEON.xforms.Controls._setMessage(control, "xforms-hint", message);
            ORBEON.xforms.Controls._setHintAlertMessage(control, message, ORBEON.xforms.Globals.hintTooltipForControl);
        }
    },

    _setHintAlertMessage: function(control, message, tooltipForControl) {
        // If we have a YUI tooltip for this control, update the tooltip
        var currentTooltip = tooltipForControl[control.id];
        if (currentTooltip) {
            if (currentTooltip == true) {
                // Message used to be empty: we didn't create a YUI tooltip
                if (message != "") {
                    // Now there is a message: set to null so we'll create the YUI tooltip on mouseover
                    tooltipForControl[control.id] = null;
                }
            } else {
                // Message used not to be empty: we had a YUI tooltip
                if (message == "") {
                    // We don't the tooltip anymore
                    currentTooltip.destroy();
                    tooltipForControl[control.id] = true;
                } else {
                    // Update the tooltip message
                    currentTooltip.cfg.setProperty("text", message);
                }
            }
        }

    },

    /**
     * Sets focus to the specified control. This is called by the JavaScript code
     * generated by the server, which we invoke on page load.
     */
    setFocus: function(controlId) {
        var control = ORBEON.util.Dom.getElementById(controlId);
        // To-do: getting elements by position is not very robust
        ORBEON.xforms.Globals.maskFocusEvents = true;
        if (ORBEON.util.Dom.hasClass(control, "xforms-input") && !ORBEON.util.Dom.hasClass(control, "xforms-type-boolean")) {
            ORBEON.util.Dom.getChildElementByIndex(control, 1).focus();
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")) {
            ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(control, 0), 0).focus();
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-xxforms-autocomplete")) {
            ORBEON.util.Dom.getChildElementByIndex(control, 0).focus();
        } else if (typeof control.focus != "undefined") {
            control.focus();
        }

        // Save current value as server value. We usually do this on focus, but for control where we set the focus
        // with xforms:setfocus, we still receive the focus event when the value changes, but after the change event
        // (which means we then don't send the new value to the server).
        if (typeof ORBEON.xforms.Globals.serverValue[controlId] == "undefined") {
            var currentValue = ORBEON.xforms.Controls.getCurrentValue(control);
            ORBEON.xforms.Globals.serverValue[controlId] = currentValue;
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
    },

    updateLoadingPosition: function(formID) {
        // Compute new X
        var x; {
            var initialRight = ORBEON.xforms.Globals.formLoadingLoadingInitialRightTop[formID][0];
            var scrollX = document.documentElement.scrollLeft || document.body.scrollLeft;
            x = scrollX + YAHOO.util.Dom.getViewportWidth() - initialRight;
        }
        // Compute new Y
        var y; {
            // Distance between top of viewport and top of the page. Initially 0 when we are at the top of the page.
            var scrollY = document.documentElement.scrollTop || document.body.scrollTop;
            var initialTop = ORBEON.xforms.Globals.formLoadingLoadingInitialRightTop[formID][1];
            y = scrollY + XFORMS_LOADING_MIN_TOP_PADDING > initialTop
                    // Place indicator at a few pixels from the top of the viewport
                    ? scrollY + XFORMS_LOADING_MIN_TOP_PADDING
                    // Loading is visible left at its initial position, so leave it there
                    : initialTop;
        }
        // Position overlay
        var overlay = ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID];
        overlay.cfg.setProperty("x", x);
        overlay.cfg.setProperty("y", y);
    },

    treeOpenSelectedVisible: function(yuiTree, values) {
        for (nodeIndex in yuiTree._nodes) {
            var node = yuiTree._nodes[nodeIndex];
            if (xformsArrayContains(values, node.data.value)) {
                var nodeParent = node.parent;
                while (nodeParent != null) {
                    nodeParent.expand();
                    nodeParent = nodeParent.parent;
                }
            }
        }

    }
};

ORBEON.xforms.Events = {

    /**
     * Look for the first parent control which is an XForms control
     */
    _findParentXFormsControl: function(element) {
        while (true) {
            if (!element) return null; // No more parent, stop search
            if (element.xformsElement) {
                // HTML area on Firefox: event target is the document, return the textarea
                return element.xformsElement;
            } else if (element.ownerDocument && element.ownerDocument.xformsElement) {
                // HTML area on IE: event target is the body of the document, return the textarea
                return element.ownerDocument.xformsElement;
            } else if (element.tagName != null
                    && element.tagName.toLowerCase() == "iframe") {
                // This might be the iframe that corresponds to a dialog on IE6
                for (var dialogId in ORBEON.xforms.Globals.dialogs) {
                    var dialog = ORBEON.xforms.Globals.dialogs[dialogId];
                    if (dialog.iframe == element)
                        return dialog.element;
                }
            } else if (element.className != null) {
                if (ORBEON.util.Dom.hasClass(element, "xforms-control")
                        || ORBEON.util.Dom.hasClass(element, "xforms-dialog")
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
                // Save id of the control that received blur last
                ORBEON.xforms.Globals.lastFocusControlId = target.id;
                // Store initial value of control if we don't have a server value already, and if this is is not a list
                // Initial value for lists is set up initialization, as when we receive the focus event the new value is already set.
                if (typeof ORBEON.xforms.Globals.serverValue[target.id] == "undefined"
                        && ! ORBEON.util.Dom.hasClass(target, "xforms-select-appearance-compact")) {
                    ORBEON.xforms.Globals.serverValue[target.id] = target.value;
                }
                // Send focus events
                var previousDOMFocusOut = ORBEON.xforms.Globals.previousDOMFocusOut;
                if (previousDOMFocusOut) {
                    if (previousDOMFocusOut != target) {
                        // HTML area and trees does not throw value change event, so we send the value change to the server
                        // when we get the focus on the next control
                        if (ORBEON.util.Dom.hasClass(previousDOMFocusOut, "xforms-textarea")
                                && ORBEON.util.Dom.hasClass(previousDOMFocusOut, "xforms-mediatype-text-html")) {
                            // To-do: would be nice to use the ORBEON.xforms.Controls.getCurrentValue() so we don't dupplicate the code here
                            var editorInstance = FCKeditorAPI.GetInstance(previousDOMFocusOut.name);
                            previousDOMFocusOut.value = editorInstance.GetXHTML();
                            xformsValueChanged(previousDOMFocusOut, null);
                        } else if (ORBEON.util.Dom.hasClass(previousDOMFocusOut, "xforms-select1-appearance-xxforms-tree")
                                || ORBEON.util.Dom.hasClass(previousDOMFocusOut, "xforms-select-appearance-xxforms-tree")) {
                            xformsValueChanged(previousDOMFocusOut, null);
                        } else if (ORBEON.xforms.Globals.isMac && ORBEON.xforms.Globals.isRenderingEngineGecko
                                && ORBEON.util.Dom.hasClass(previousDOMFocusOut, "xforms-control")
                                && ! ORBEON.util.Dom.hasClass(previousDOMFocusOut, "xforms-trigger")) {
                            // On Firefox running on Mac, when users ctrl-tabs out of Firefox, comes back, and then changes the focus
                            // to another field, we don't receive a change event. On Windows that change event is sent then user tabs
                            // out of Firefox. So here, we make sure that a value change has been sent to the server for the previous control
                            // that had the focus when we get the focus event for another control.
                            xformsFireEvents([xformsCreateEventArray(previousDOMFocusOut, "xxforms-value-change-with-focus-change",
                                ORBEON.xforms.Controls.getCurrentValue(previousDOMFocusOut))], false);
                        }
                        // Send focus out/focus in events
                        var events = new Array();
                        events.push(xformsCreateEventArray(previousDOMFocusOut, "DOMFocusOut", null));
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
                // This is an event for an XForms control
                ORBEON.xforms.Globals.previousDOMFocusOut = target;
            }
        }
    },

    change: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            if (ORBEON.util.Dom.hasClass(target, "xforms-upload")) {
                // For upload controls, generate an xforms-select event when a file is selected
                xformsFireEvents(new Array(xformsCreateEventArray(target, "xforms-select", "")), false);
            } else {
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
                    || (ORBEON.util.Dom.hasClass(target, "xforms-input")  && !ORBEON.util.Dom.hasClass(target, "xforms-type-boolean"))
                    || ORBEON.util.Dom.hasClass(target, "xforms-secret")) {
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

    resize: function(event) {
        for (var i = 0; i < ORBEON.xforms.Globals.autosizeTextareas.length; i++) {
            var textarea = ORBEON.xforms.Globals.autosizeTextareas[i];
            ORBEON.xforms.Controls.autosizeTextarea(textarea);
        }
    },

    mouseover: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            // Control tooltip
            if (ORBEON.xforms.Globals.hintTooltipForControl[target.id] == null
                    && ! ORBEON.util.Dom.hasClass(document.body, "xforms-disable-hint-as-tooltip")) {
                // We haven't initialized the YUI tooltip for this control yet, see there is a hint, and maybe initialize YUI tooltip
                var message = ORBEON.xforms.Controls.getHintMessage(target);
                if (message != "") {
                    // We have a hint, initialize YUI tooltip
                    ORBEON.xforms.Globals.hintTooltipForControl[target.id] =
                        new YAHOO.widget.Tooltip(target.id + "-orbeon-hint-tooltip", {
                            context: target.id,
                            text: message,
                            showDelay: 200,
                            effect: {effect:YAHOO.widget.ContainerEffect.FADE,duration: 0.2}
                        });
                } else {
                    // Remember we looked at this control already
                    ORBEON.xforms.Globals.hintTooltipForControl[target.id] = true;
                }
            }

            // Alert tooltip
            if (ORBEON.util.Dom.hasClass(target, "xforms-alert-active") &&
                    ORBEON.xforms.Globals.alertTooltipForControl[target.id] == null) {
                // We haven't initialized the YUI tooltip for this control yet, see there is an alert, and maybe initialize YUI tooltip
                var control = ORBEON.util.Dom.getElementById(target.htmlFor);
                var message = ORBEON.xforms.Controls.getAlertMessage(control);
                if (message != "") {
                    // We have a hint, initialize YUI tooltip
                    YAHOO.util.Dom.generateId(target);
                    ORBEON.xforms.Globals.alertTooltipForControl[target.id] =
                        new YAHOO.widget.Tooltip(target.id + "-orbeon-alert-tooltip", {
                            context: target.id,
                            text: message,
                            showDelay: 0,
                            effect: {effect:YAHOO.widget.ContainerEffect.FADE,duration: 0.2}
                        });
                } else {
                    // Remember we looked at this control already
                    ORBEON.xforms.Globals.alertTooltipForControl[target.id] = true;
                }
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-dialog-appearance-minimal")) {
                // Minimal dialog: record more is back inside the dialog
                ORBEON.xforms.Globals.dialogMinimalLastMouseOut[target.id] = -1;
            }

            // Check if this control is inside a minimal dialog, in which case we are also inside that dialog
            var current = target;
            while (current != null && current != document) {
                if (ORBEON.util.Dom.hasClass(current, "xforms-dialog-appearance-minimal")) {
                    ORBEON.xforms.Globals.dialogMinimalLastMouseOut[current.id] = -1;
                    break;
                }
                current = current.parentNode;
            }
        }
    },

    mouseout: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            if (ORBEON.util.Dom.hasClass(target, "xforms-dialog-appearance-minimal")) {
                // Minimal dialog: register listener to maybe close the dialog
                ORBEON.xforms.Globals.dialogMinimalLastMouseOut[yuiDialog.element.id] = new Date().getTime();
                window.setTimeout(function() { ORBEON.xforms.Events.dialogMinimalCheckMouseIn(yuiDialog); },
                        XFORMS_DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_IN_MS);
            }
        }
    },

    click: function(event) {
        var originalTarget = YAHOO.util.Event.getTarget(event);
        var target = ORBEON.xforms.Events._findParentXFormsControl(originalTarget);

        if (target != null) {
            if (ORBEON.util.Dom.hasClass(target, "xforms-output")) {
                // Click on output
                xformsFireEvents([xformsCreateEventArray(target, "DOMFocusIn", null)], false);
            } else  if ((ORBEON.util.Dom.hasClass(target, "xforms-trigger") || ORBEON.util.Dom.hasClass(target, "xforms-submit"))) {
                // Click on trigger
                YAHOO.util.Event.preventDefault(event);
                if (!ORBEON.util.Dom.hasClass(target, "xforms-readonly")) {
                    // If this is an anchor and we didn't get a chance to register the focus event,
                    // send the focus event here. This is useful for anchors (we don't listen on the
                    // focus event on those, and for buttons on Safari which does not dispatch the focus
                    // event for buttons.
                    ORBEON.xforms.Events.focus(event);
                    xformsFireEvents([xformsCreateEventArray(target, "DOMActivate", null)], false);
                }
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-select1-appearance-full")
                    || ORBEON.util.Dom.hasClass(target, "xforms-select-appearance-full")
                    || ORBEON.util.Dom.hasClass(target, "xforms-input-appearance-full")) {
                // Click on checkbox or radio button
                xformsFireEvents(new Array(xformsCreateEventArray
                        (target, "xxforms-value-change-with-focus-change",
                                ORBEON.xforms.Controls.getCurrentValue(target), null)), false);
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-input") && !ORBEON.util.Dom.hasClass(target, "xforms-type-boolean")) {
                // Click on calendar inside input field

                // Initialize calendar when needed
                var displayField = ORBEON.util.Dom.getChildElementByIndex(target, 0);
                var inputField = ORBEON.util.Dom.getChildElementByIndex(target, 1);
                var showCalendar = ORBEON.util.Dom.getChildElementByIndex(target, 2);
                if (ORBEON.util.Dom.hasClass(target, "xforms-type-date")
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
                            onClose        :    ORBEON.xforms.Events.calendarClose,
                            electric       :    true
                        });
                        // JSCalendar sets his listener in the onclick attribute: save it so we can call it later
                        ORBEON.xforms.Globals.inputCalendarOnclick[target.id] = target.onclick;
                        target.onclick = null;
                        ORBEON.xforms.Globals.inputCalendarCreated[target.id] = true;
                        // Save initial value
                        ORBEON.xforms.Globals.inputCalendarCommitedValue[target.id] = ORBEON.xforms.Controls.getCurrentValue(target);
                    }

                    // Event can be received on calendar picker span, or on the containing span
                    ORBEON.xforms.Globals.inputCalendarOnclick[target.id]();
                }
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-upload") && ORBEON.util.Dom.hasClass(originalTarget, "xforms-upload-remove")) {
                // Click on remove icon in upload control
                xformsFireEvents(new Array(xformsCreateEventArray(target, "xxforms-value-change-with-focus-change", "")), false);
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-select1-appearance-xxforms-menu")) {
                // Click on menu item

                // Find what is the position in the hiearchy of the item
                var positions = [];
                var currentParent = originalTarget;
                while (true) {
                    if (currentParent.tagName.toLowerCase() == "li") {
                        // Get the position of this li, and add it to positions
                        var liPosition = 0;
                        while (true) {
                            var previousSibling = currentParent.previousSibling;
                            if (previousSibling == null) break;
                            currentParent = previousSibling;
                            if (currentParent.nodeType == ELEMENT_TYPE && currentParent.tagName.toLowerCase() == "li") liPosition++;
                        }
                        positions.push(liPosition);
                    } else if (currentParent.tagName.toLowerCase() == "div" && ORBEON.util.Dom.hasClass(currentParent, "yuimenubar")) {
                        // Got to the top of the tree
                        break;
                    }
                    currentParent = currentParent.parentNode;
                }
                positions = positions.reverse();

                // Find value for this item
                var currentLevel = ORBEON.xforms.Globals.menuItemsets[target.id];
                var increment = 0;
                for (var positionIndex = 0; positionIndex < positions.length; positionIndex++) {
                    var position = positions[positionIndex];
                    currentLevel = currentLevel[position + increment];
                    increment = 3;
                }

                // Send value change to server
                var itemValue = currentLevel[1];
                xformsFireEvents(new Array(xformsCreateEventArray(target, "xxforms-value-change-with-focus-change", itemValue)), false);
                // Close the menu
                ORBEON.xforms.Globals.menuYui[target.id].clearActiveItem();
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-help-image")) {
                // Help image

                // Get label and control for this help message
                var label = target.nextSibling;
                while (!ORBEON.util.Dom.isElement(label)) label = target.nextSibling;
                var control = ORBEON.util.Dom.getElementById(label.htmlFor);
                var form = ORBEON.xforms.Controls.getForm(control);

                // Create help dialog if this hasn't been done already
                if (ORBEON.xforms.Globals.formHelpPanel[form.id] == null) {
                    // Look for help div in the page under the form
                    for (var childIndex = 0; childIndex < form.childNodes.length; childIndex++) {
                        var formChild = form.childNodes[childIndex];
                        if (ORBEON.util.Dom.isElement(formChild) && ORBEON.util.Dom.hasClass(formChild, "xforms-help-panel")) {
                            // Create YUI dialog for help based on template
                            YAHOO.util.Dom.generateId(formChild);
                            ORBEON.util.Dom.removeClass(formChild, "xforms-initially-hidden");
                            var helpPanel = new YAHOO.widget.Panel(formChild.id, {
                                modal: false,
                                fixedcenter: false,
                                underlay: "shadow",
                                visible: false,
                                constraintoviewport: false,
                                draggable: true,
                                effect: {effect:YAHOO.widget.ContainerEffect.FADE,duration: 0.3}
                            });
                            helpPanel.render();
                            helpPanel.element.style.display = "none";
                            ORBEON.xforms.Globals.formHelpPanel[form.id] = helpPanel;

                            // Find div for help body
                            var bodyDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "bd");
                            var messageDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-help-panel-message");
                            ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id] = messageDiv;

                            // Get the close button and register listener on that button
                            var closeDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-help-panel-close");
                            var closeButton = ORBEON.util.Dom.getChildElementByIndex(closeDiv, 0);
                            ORBEON.xforms.Globals.formHelpPanelCloseButton[form.id] = closeButton;
                            YAHOO.util.Event.addListener(closeButton, "click", ORBEON.xforms.Events.helpDialogButtonClose, form.id);

                            // Register listener for when the panel is closed by a click on the "x"
                            helpPanel.beforeHideEvent.subscribe(ORBEON.xforms.Events.helpDialogXClose, form.id);

                            // We found what we were looking for, no need to continue searching
                            break;
                        }
                    }
                }

                // Update message in help panel
                ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id].innerHTML = ORBEON.util.Dom.getStringValue(label);
                // Check where and if the panel is showing on the page
                var formHelpPanelRegion = YAHOO.util.Dom.getRegion(ORBEON.xforms.Globals.formHelpPanel[form.id].element);
                var showAndRepositionPanel;
                if (formHelpPanelRegion.top == null) {
                    // Panel is not open
                    showAndRepositionPanel = true;
                } else {
                    // Panel is shown. Check if it is visible.
                    // Get information about viewport
                    var viewPortWidth = YAHOO.util.Dom.getViewportWidth();
                    var viewPortHeight = YAHOO.util.Dom.getViewportHeight();
                    var scrollX = document.body.scrollLeft;
                    var scrollY = document.body.scrollTop;
                    // Check that top left corner and bottom right corner of dialog is in viewport
                    var verticalConstraint = formHelpPanelRegion.top >= scrollY && formHelpPanelRegion.bottom <= scrollY + viewPortHeight;
                    var horizontalContraint = formHelpPanelRegion.left >= scrollX && formHelpPanelRegion.right <= scrollX + viewPortWidth;
                    // Reposition if any constraint is not met
                    showAndRepositionPanel = !verticalConstraint || !horizontalContraint;
                }

                // Show and reposition dialog when needed
                if (showAndRepositionPanel) {
                ORBEON.xforms.Globals.formHelpPanel[form.id].element.style.display = "block";
                ORBEON.xforms.Globals.formHelpPanel[form.id].cfg.setProperty("context", [target, "tl", "tr"]);
                ORBEON.xforms.Globals.formHelpPanel[form.id].show();
                }

                // Set focus on close button if visible (we don't want to set the focus on the close button if not
                // visible as this would make the help panel scroll down to the close button)
                var bdDiv = ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id].parentNode;
                if (bdDiv.scrollHeight <= bdDiv.clientHeight)
                ORBEON.xforms.Globals.formHelpPanelCloseButton[form.id].focus();
            }
        }
    },

    /**
     * Upon scrolling or resizing, adjust position of loading indicators
     */
    scrollOrResize: function() {
        for (var formID in ORBEON.xforms.Globals.formLoadingLoadingOverlay) {
            var overlay = ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID];
            if (overlay && overlay.cfg.getProperty("visible"))
                ORBEON.xforms.Controls.updateLoadingPosition(formID);
        }
    },

    /**
     * Send notification to XForms engine end-user clicked on day.
     */
    calendarUpdate: function(calendar) {
        if (ORBEON.util.Dom.hasClass(calendar.activeDiv, "day")) {
            var inputField = calendar.params.inputField;
            var element = inputField.parentNode;
            var newValue = ORBEON.xforms.Controls.getCurrentValue(element);
            ORBEON.xforms.Globals.inputCalendarCommitedValue[element.id] = newValue;
            xformsFireEvents([xformsCreateEventArray(element, "xxforms-value-change-with-focus-change", newValue)], false);
        }
    },

    /**
     * Restore last value actualy selected by user in the input field, which at this point could
     * contain a value the user just browsed to, without selecting it.
     */
    calendarClose: function(calendar) {
        var inputField = calendar.params.inputField;
        var element = inputField.parentNode;
        inputField.value = ORBEON.xforms.Globals.inputCalendarCommitedValue[element.id];
        calendar.hide();
    },

    sliderValueChange: function(offset) {
        // Notify server that value changed
        var rangeControl = ORBEON.util.Dom.getElementById(this.id);
        rangeControl.value = offset / 200;
        xformsValueChanged(rangeControl, null);
    },

    /**
     * Called by the YUI menu library when a click happens a menu entry.
     */
    menuClick: function (eventType, arguments, userObject) {
        var menu = userObject["menu"];
        var value = userObject["value"];
        xformsFireEvents([xformsCreateEventArray(menu, "xxforms-value-change-with-focus-change", value)], false);
    },

    /**
     * Event listener on dialogs called by YUI when the dialog is closed. If the dialog was closed by the user (not
     * because the server told use to close the dialog), then we want to notify the server that this happened.
     */
    dialogClose: function(type, args, me) {
        var dialogId = me;
        var dialog = ORBEON.util.Dom.getElementById(dialogId);
        xformsFireEvents([xformsCreateEventArray(dialog, "xxforms-dialog-close")], false);
    },

    /**
     * Called when the "close" button is pressed in help dialog.
     */
    helpDialogButtonClose: function(_dummy, formID) {
        // Close dialog. This will trigger a call to helpDialogXClose.
        var formHelpPanel = ORBEON.xforms.Globals.formHelpPanel[formID];
        formHelpPanel.hide();
    },

    /**
     * Called when the help dialog is closed with the "x" button or after helpDialogButtonClose()
     * runs formHelpPanel.hide().
     */
    helpDialogXClose: function(_dummy, _dummy, formID) {
        // Fixes cursor Firefox issue; more on this in dialog init code
        var formHelpPanel = ORBEON.xforms.Globals.formHelpPanel[formID];
        formHelpPanel.element.style.display = "none";
    },

    /**
     * What we need to do when there is a click on a tree (select and select1)
     */
    treeClickFocus: function(control) {
        var isIncremental = ORBEON.util.Dom.hasClass(control, "xforms-incremental");
        if (ORBEON.xforms.Globals.lastFocusControlId != control.id) {
            // We are comming from another control, simulate a focus on this control
            var focusEvent = { target: control };
            ORBEON.xforms.Events.focus(focusEvent);
        }
        // Preemptively store current control in previousDOMFocusOut, so when another control gets
        // the focus it will send the value of this control to the server
        ORBEON.xforms.Globals.previousDOMFocusOut = control;
    },

    treeClickValueUpdated: function(control) {
        // If we are in incremental mode, send value to the server on every click
        if (ORBEON.util.Dom.hasClass(control, "xforms-incremental"))
            xformsValueChanged(control);
    },

    /**
     * xforms:select tree: handle click on check box
     */
    treeCheckClick: function() {
        var tree = this.tree;
        var control = ORBEON.util.Dom.getElementById(tree.id);
        ORBEON.xforms.Events.treeClickFocus(control);
        control.value = "";
        for (nodeIndex in tree._nodes) {
            var node = tree._nodes[nodeIndex];
            if (node.checkState == 2) {
                if (control.value != "") control.value += " ";
                control.value += node.data.value;
            }
        }
        ORBEON.xforms.Events.treeClickValueUpdated(control);
    },

    /**
     * xforms:select and xforms:select tree: handle click on label
     */
    treeLabelClick: function(node) {
        var yuiTree = this;
        var control = document.getElementById(yuiTree.id);
        var allowMultipleSelection = ORBEON.util.Dom.hasClass(control, "xforms-select");
        if (allowMultipleSelection) {
            // If checked uncheck, if unchecked check
            if (node.checked) {
                node.uncheck();
            } else {
                node.check();
            }
            // Call listener on check event
            node.onCheckClick();
        } else {
            // Unselect the old node and select the new node
            var oldNode = yuiTree.getNodeByProperty("value", control.value);
            if (oldNode != null)
                YAHOO.util.Dom.removeClass(oldNode.getLabelEl(), "xforms-tree-label-selected");
            if (node != null)
                YAHOO.util.Dom.addClass(node.getLabelEl(), "xforms-tree-label-selected");
            // Make we know this control has the focus
            ORBEON.xforms.Events.treeClickFocus(control);
            // Store the new value for this control
            control.value = node.data.value;
            // Send new value to server
            ORBEON.xforms.Events.treeClickValueUpdated(control);
        }
    },

    /**
     * Called when end-users click on the show/hide details link in the error panel.
     */
    errorShowHideDetails: function() {
        var errorBodyDiv = this.parentNode.parentNode.parentNode;
        var detailsHidden = ORBEON.util.Dom.getChildElementByClass(errorBodyDiv, "xforms-error-panel-details-hidden");
        var detailsShown = ORBEON.util.Dom.getChildElementByClass(errorBodyDiv, "xforms-error-panel-details-shown");
        if (this.className == "xforms-error-panel-show-details") {
            ORBEON.util.Dom.addClass(detailsHidden, "xforms-disabled");
            ORBEON.util.Dom.removeClass(detailsShown, "xforms-disabled");
        } else {
            ORBEON.util.Dom.removeClass(detailsHidden, "xforms-disabled");
            ORBEON.util.Dom.addClass(detailsShown, "xforms-disabled");
        }
    },

    /**
     * When the error dialog is closed, we make sure that the "details" section is closed,
     * so it will be closed the next time the dialog is opened.
     */
    errorPanelClosed: function(type, args, formID) {
        var errorPanel = ORBEON.xforms.Globals.formErrorPanel[formID];
        var errorBodyDiv = errorPanel.errorDetailsDiv.parentNode.parentNode;
        var detailsHidden = ORBEON.util.Dom.getChildElementByClass(errorBodyDiv, "xforms-error-panel-details-hidden");
        var detailsShown = ORBEON.util.Dom.getChildElementByClass(errorBodyDiv, "xforms-error-panel-details-shown");
        ORBEON.util.Dom.removeClass(detailsHidden, "xforms-disabled");
        ORBEON.util.Dom.addClass(detailsShown, "xforms-disabled");
    },

    errorCloseClicked: function(event, errorPanel) {
        errorPanel.hide();
    },

    errorReloadClicked: function(event, errorPanel) {
        window.location.reload(true);// force reload
    },

    /**
     * Called for each minimal dialog when there is a click on the document.
     * We have one listener per dialog, which listens to those events all the time,
     * not just when the dialog is open.
     */
    dialogMinimalBodyClick: function(event, yuiDialog) {
        // If this dialog is visible
        if (ORBEON.xforms.Globals.dialogMinimalVisible[yuiDialog.element.id]) {
            // Abord if one of the parents is drop-down dialog
            var current = YAHOO.util.Event.getTarget(event);
            var foundDropDownParent = false;
            while (current != null && current != document) {
                if (ORBEON.util.Dom.hasClass(current, "xforms-dialog-appearance-minimal")) {
                    foundDropDownParent = true;
                    break;
                }
                current = current.parentNode;
            }
            if (!foundDropDownParent)
                xformsFireEvents([xformsCreateEventArray(yuiDialog.element, "xxforms-dialog-close")], false);
        }
    },

    /**
     * Called when the mouse is outside of a minimal dialog for more than a certain amount of time.
     * Here we close the dialog if appropriate.
     */
    dialogMinimalCheckMouseIn: function(yuiDialog) {
        var current = new Date().getTime();
        if (ORBEON.xforms.Globals.dialogMinimalVisible[yuiDialog.element.id]
                && ORBEON.xforms.Globals.dialogMinimalLastMouseOut[yuiDialog.element.id] != -1
                && current - ORBEON.xforms.Globals.dialogMinimalLastMouseOut[yuiDialog.element.id] >= XFORMS_DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_IN_MS) {
            xformsFireEvents([xformsCreateEventArray(yuiDialog.element, "xxforms-dialog-close")], false);
        }
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
            "dialog": {
                "": ORBEON.xforms.Init._dialog,
                "full": ORBEON.xforms.Init._dialog,
                "minimal": ORBEON.xforms.Init._dialog
            }
        };
        return ORBEON.xforms.Init._specialControlsInitFunctions;
    },

    registerListenersOnFormElements: function() {
        for (var i = 0; i < document.forms.length; i++) {
            var form = document.forms[i];
            if (ORBEON.util.Dom.hasClass(form, "xforms-form")) {
                for (var j = 0; j < form.elements.length; j++) {
                    var element = form.elements[j];
                    ORBEON.xforms.Init.registerListenersOnFormElement((element));
                }
            }
        }
    },

    registerListenersOnFormElement: function(element) {
        YAHOO.util.Event.addListener(element, "focus", ORBEON.xforms.Events.focus);
        YAHOO.util.Event.addListener(element, "blur", ORBEON.xforms.Events.blur);
        YAHOO.util.Event.addListener(element, "change", ORBEON.xforms.Events.change);
    },

    document: function() {

        // Register events in the capture phase for W3C-compliant browsers.
        if (ORBEON.xforms.Globals.supportsCaptureEvents) {
            window.addEventListener("focus", ORBEON.xforms.Events.focus, true);
            window.addEventListener("blur", ORBEON.xforms.Events.blur, true);
            window.addEventListener("change", ORBEON.xforms.Events.change, true);
        } else {
            ORBEON.xforms.Init.registerListenersOnFormElements();
        }

        // Register events that bubble on document for all browsers
        YAHOO.util.Event.addListener(document, "keypress", ORBEON.xforms.Events.keypress);
        YAHOO.util.Event.addListener(document, "keydown", ORBEON.xforms.Events.keydown);
        YAHOO.util.Event.addListener(document, "keyup", ORBEON.xforms.Events.keyup);
        YAHOO.util.Event.addListener(document, "mouseover", ORBEON.xforms.Events.mouseover);
        YAHOO.util.Event.addListener(document, "mouseout", ORBEON.xforms.Events.mouseout);
        YAHOO.util.Event.addListener(document, "click", ORBEON.xforms.Events.click);
        YAHOO.util.Event.addListener(window, "resize", ORBEON.xforms.Events.resize);
        YAHOO.widget.Overlay.windowScrollEvent.subscribe(ORBEON.xforms.Events.scrollOrResize);
        YAHOO.widget.Overlay.windowResizeEvent.subscribe(ORBEON.xforms.Events.scrollOrResize);

        // Initialize XForms server URL
        // NOTE: The server provides us with a base URL, but we must use a client-side value to support proxying
        var scripts = document.getElementsByTagName("script");
        for (var scriptIndex = 0; scriptIndex < scripts.length; scriptIndex++) {
            var script = scripts[scriptIndex];
            var scriptSrc = ORBEON.util.Dom.getAttribute(script, "src");
            if (scriptSrc != null) {
                var startPathToJavaScript = scriptSrc.indexOf(PATH_TO_JAVASCRIPT_1);
                if (startPathToJavaScript == -1)
                    startPathToJavaScript = scriptSrc.indexOf(PATH_TO_JAVASCRIPT_2);
                if (startPathToJavaScript != -1) {
                    BASE_URL = scriptSrc.substr(0, startPathToJavaScript);
                    XFORMS_SERVER_URL = BASE_URL + "/xforms-server";
                    break;
                }
            }
        }

        // Override image location for YUI to use local images

        var yuiBaseURL;
        if (typeof opsXFormsProperties != "undefined" && typeof opsXFormsProperties["yui-base-path"] != "undefined")
            yuiBaseURL = opsXFormsProperties["yui-base-path"];
        else
            yuiBaseURL = BASE_URL + YUI_BASE_PATH;

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
                                var control = ORBEON.util.Dom.getElementById(controlIds[controlIndex]);
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
                var formID = document.forms[formIndex].id;

                // Initialize loading and error indicator
                ORBEON.xforms.Globals.formErrorPanel[formID] = null;
                ORBEON.xforms.Globals.formLoadingNone[formID] = null;

                var xformsLoadingCount = 0;
                for (var formChildIndex = 0; formChildIndex < form.childNodes.length; formChildIndex++) {
                    if (xformsLoadingCount == 3) break;
                    var formChild = form.childNodes[formChildIndex];
                    if (formChild.className == "xforms-loading-loading") {
                        formChild.style.display = "block";
                        ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID] = new YAHOO.widget.Overlay(formChild);
                        ORBEON.xforms.Globals.formLoadingLoadingInitialRightTop[formID] = [
                            YAHOO.util.Dom.getViewportWidth() - YAHOO.util.Dom.getX(formChild),
                            YAHOO.util.Dom.getY(formChild)
                        ];
                        formChild.style.right = "auto";
                        ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID].cfg.setProperty("visible", false);
                        xformsLoadingCount++;
                    } else if (ORBEON.util.Dom.isElement(formChild) && ORBEON.util.Dom.hasClass(formChild, "xforms-error-panel")) {

                        // Create and store error panel
                        YAHOO.util.Dom.generateId(formChild);
                        ORBEON.util.Dom.removeClass(formChild, "xforms-initially-hidden");
                        var errorPanel = new YAHOO.widget.Panel(formChild.id, {
                            width: "700px",
                            modal: true,
                            fixedcenter: false,
                            underlay: "shadow",
                            visible: false,
                            constraintoviewport: true,
                            draggable: true
                        });
                        errorPanel.render();
                        errorPanel.element.style.display = "none";
                        errorPanel.beforeHideEvent.subscribe(ORBEON.xforms.Events.errorPanelClosed, formID);
                        ORBEON.xforms.Globals.formErrorPanel[formID] = errorPanel;

                        // Find reference to elements in the deails hidden section
                        var titleDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "hd");
                        var bodyDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "bd");
                        var detailsHiddenDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-error-panel-details-hidden");
                        var showDetailsA = ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(detailsHiddenDiv, 0), 0);
                        YAHOO.util.Dom.generateId(showDetailsA);

                        // Find reference to elements in the deails shown section
                        var detailsShownDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-error-panel-details-shown");
                        var hideDetailsA = ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(detailsShownDiv, 0), 0);
                        YAHOO.util.Dom.generateId(hideDetailsA);
                        errorPanel.errorTitleDiv = titleDiv;
                        errorPanel.errorDetailsDiv = ORBEON.util.Dom.getChildElementByClass(detailsShownDiv, "xforms-error-panel-details");

                        // Register listener that will show/hide the detail section
                        YAHOO.util.Event.addListener(showDetailsA.id, "click", ORBEON.xforms.Events.errorShowHideDetails);
                        YAHOO.util.Event.addListener(hideDetailsA.id, "click", ORBEON.xforms.Events.errorShowHideDetails);

                        // Handle listeners on error panel
                        var closeA = YAHOO.util.Dom.getElementsByClassName("xforms-error-panel-close", null, formChild);
                        if (closeA.length != 0) {
                            YAHOO.util.Dom.generateId(closeA[0]);
                            YAHOO.util.Event.addListener(closeA[0].id, "click", ORBEON.xforms.Events.errorCloseClicked, errorPanel);
                        }

                        var reloadA = YAHOO.util.Dom.getElementsByClassName("xforms-error-panel-reload", null, formChild);
                        if (reloadA.length != 0) {
                            YAHOO.util.Dom.generateId(reloadA[0]);
                            YAHOO.util.Event.addListener(reloadA[0].id, "click", ORBEON.xforms.Events.errorReloadClicked, errorPanel);
                        }

                        xformsLoadingCount++;
                    } else if (formChild.className == "xforms-loading-none") {
                        ORBEON.xforms.Globals.formLoadingNone[formID] = formChild;
                        xformsLoadingCount++;
                    }
                }

                var elements = form.elements;
                var xformsRepeatTree;
                var xformsRepeatIndices;
                for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
                    var element = elements[elementIndex];
                    if (element.name.indexOf("$static-state") != -1) {
                        ORBEON.xforms.Globals.formStaticState[formID] = element;
                    } else if (element.name.indexOf("$dynamic-state") != -1) {
                        ORBEON.xforms.Globals.formDynamicState[formID] = element;
                    } else if (element.name.indexOf("$server-events") != -1) {
                        ORBEON.xforms.Globals.formServerEvents[formID] = element;
                    } else if (element.name.indexOf("$client-state") != -1) {
                        ORBEON.xforms.Globals.formClientState[formID] = element;
                        if (element.value == "") {
                            xformsStoreInClientState(formID, "ajax-dynamic-state",
                                    ORBEON.xforms.Globals.formDynamicState[formID].value);
                            xformsStoreInClientState(formID, "initial-dynamic-state",
                                    ORBEON.xforms.Globals.formDynamicState[formID].value);
                        }
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
                if (xformsGetFromClientState(formID, "load-did-run") == null) {
                    xformsStoreInClientState(formID, "load-did-run", "true");
                } else {
                    if (typeof opsXFormsProperties != "undefined" && opsXFormsProperties["revisit-handling"] == "reload") {
                        window.location.reload(true)
                    } else {
                        xformsFireEvents(new Array(xformsCreateEventArray(form, "xxforms-all-events-required", null, null)), false);
                    }
                }
            }
        }

        // Run code sent by server
        if (typeof xformsPageLoadedServer != "undefined" && !ORBEON.xforms.Globals.fckEditorLoading)
            xformsPageLoadedServer();
    },

    /**
     * Initialize a newly copied subtree.
     *
     * Some of the more advanced controls are initialized when the page first loads. The server sets the value of the
     * opsXFormsControls variable to tell the client the id of those controls and the type of each control. When new
     * controls are added, this function must be called so those the inserted advanced controls are initialized as
     * well.
     */
    insertedElement: function(element) {
        if (element.nodeType == ORBEON.util.Dom.ELEMENT_TYPE) {
            if (ORBEON.util.Dom.hasClass(element, "xforms-select1-appearance-xxforms-autocomplete")) {
                ORBEON.xforms.Init._autoComplete(element);
            }
            for (var childIndex = 0; childIndex < element.childNodes.length; childIndex++) {
                var child = element.childNodes[childIndex];
                if (child.nodeType == ORBEON.util.Dom.ELEMENT_TYPE)
                    ORBEON.xforms.Init.insertedElement(child);
            }
        }
    },

    _autoComplete: function(autoComplete) {
        var textfield = ORBEON.util.Dom.getChildElementByIndex(autoComplete, 0);
        var select = ORBEON.util.Dom.getChildElementByIndex(autoComplete, 1);
        // Get list of possible values from the select
        var values = new Array();
        for (var optionIndex = 1; optionIndex < select.options.length; optionIndex++)
            values.push(select.options[optionIndex].value);
        // Initialize auto-complete input
        var noFilter = ORBEON.util.Dom.hasClass(autoComplete, "xforms-select1-open-autocomplete-nofilter");
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
        slider.subscribe("change", ORBEON.xforms.Events.sliderValueChange);
    },

    _addToTree: function (treeDiv, nameValueArray, treeNode, firstPosition) {
        for (var arrayIndex = firstPosition; arrayIndex < nameValueArray.length; arrayIndex++) {
            // Extract information from the first 3 position in the array
            var childArray = nameValueArray[arrayIndex];
            var name = childArray[0];
            var value = childArray[1];
            var selected = childArray[2];
            var hasSelected = typeof selected == "boolean";
            // Create node and add to tree
            var nodeInformation = { label: name, value: value };
            var childNode;
            if (treeDiv.xformsAllowMultipleSelection) {
                childNode = new YAHOO.widget.TaskNode(nodeInformation, treeNode, false);
                childNode.onCheckClick = ORBEON.xforms.Events.treeCheckClick;
            } else {
                childNode = new YAHOO.widget.TextNode(nodeInformation, treeNode, false);
            }
            ORBEON.xforms.Init._addToTree(treeDiv, childArray, childNode, hasSelected ? 3 : 2);
            // Add this value to the list if selected
            if (hasSelected && selected) {
                if (treeDiv.value != "") treeDiv.value += " ";
                treeDiv.value += value;
            }
        }
    },

    _initTreeDivFromArray: function(treeDiv, yuiTree, treeArray) {
        // Populate the tree
        var treeRoot = yuiTree.getRoot();
        ORBEON.xforms.Init._addToTree(treeDiv, treeArray, treeRoot, 0);
        // For select tree, check the node that are selected
        if (treeDiv.xformsAllowMultipleSelection) {
            var values = treeDiv.value.split(" ");
            var nodes = yuiTree.getNodesByProperty();
            // nodes can be null when the tree is empty
            if (nodes != null) {
                for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
                    var node = nodes[nodeIndex];
                    var currentValue = node.data.value;
                    for (var valueIndex = 0; valueIndex < values.length; valueIndex++) {
                        if (currentValue == values[valueIndex]) {
                            node.check()
                            break;
                        }
                    }
                }
            }
        }
        // Make selected nodes visible
        var values = treeDiv.xformsAllowMultipleSelection ? treeDiv.value.split(" ") : [ treeDiv.value ];
        ORBEON.xforms.Controls.treeOpenSelectedVisible(yuiTree, values);
        // Draw the tree the first time
        yuiTree.draw();
    },

    _tree: function(treeDiv) {

        // Save in the control if it allows multiple selection
        treeDiv.xformsAllowMultipleSelection = ORBEON.util.Dom.hasClass(treeDiv, "xforms-select");
        // Parse data put by the server in the div
        var treeString = ORBEON.util.Dom.getStringValue(treeDiv);
        var treeArray = ORBEON.util.String.eval(treeString);
        ORBEON.util.Dom.setStringValue(treeDiv, "");
        treeDiv.value = "";
        // Create YUI tree and save a copy
        var yuiTree = new YAHOO.widget.TreeView(treeDiv.id);
        ORBEON.xforms.Globals.treeYui[treeDiv.id] = yuiTree;
        // Build the tree
        ORBEON.xforms.Init._initTreeDivFromArray(treeDiv, yuiTree, treeArray);
        // Save value in tree
        treeDiv.previousValue = treeDiv.value;
        // Show the currently selected value
        if (!treeDiv.xformsAllowMultipleSelection) {
            var selectedNode = yuiTree.getNodeByProperty("value", treeDiv.value);
            // Handle cases where the current value is not in the tree. In most cases this is because the value is
            // empty string; no value has been selected yet.
            if (selectedNode != null)
                YAHOO.util.Dom.addClass(selectedNode.getLabelEl(), "xforms-tree-label-selected");
        }
        // Register event handler for click on label
        yuiTree.subscribe("labelClick", ORBEON.xforms.Events.treeLabelClick);
        ORBEON.util.Dom.removeClass(treeDiv, "xforms-initially-hidden");
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
            // Create submenu
            var subMenu = new YAHOO.widget.Menu(menuItem.element.id + "menu");
            // Add menu items to submenu
            for (var arrayIndex = 3; arrayIndex < nameValueArray.length; arrayIndex++) {
                // Extract information from the first 3 position in the array
                var childArray = nameValueArray[arrayIndex];
                var name = childArray[0];
                var value = childArray[1];
                var selected = childArray[2];
                // Create menu item and add to menu
                var subMenuItem = new YAHOO.widget.MenuItem(name);
                subMenu.addItem(subMenuItem);
                // Add sub-sub menu
                ORBEON.xforms.Init._addToMenuItem(menu, childArray, subMenuItem)
            }
            menuItem.cfg.setProperty("submenu", subMenu);
        }
    },

    _menu: function (menu) {
        // Find the divs for the YUI menu and for the values inside the control
        var yuiMenuDiv;
        var valuesDiv;
        for (var j = 0; j < menu.childNodes.length; j++) {
            var childNode =  menu.childNodes[j];
            if (childNode.nodeType == ELEMENT_TYPE) {
                if (ORBEON.util.Dom.hasClass(childNode, "yuimenubar")) {
                    yuiMenuDiv = childNode;
                } else if (ORBEON.util.Dom.hasClass(childNode, "xforms-initially-hidden")) {
                    valuesDiv = childNode;
                }
            }
        }

        // Extract menu hierarchy from HTML
        var menuString = ORBEON.util.Dom.getStringValue(valuesDiv);
        ORBEON.xforms.Globals.menuItemsets[menu.id] = ORBEON.util.String.eval(menuString);

        // Initialize tree
        YAHOO.util.Dom.generateId(yuiMenuDiv);
        var yuiMenu = new YAHOO.widget.MenuBar(yuiMenuDiv.id, {
            autosubmenudisplay: true,
            hidedelay: 750,
            lazyload: true
        });
        yuiMenu.render();
        ORBEON.xforms.Globals.menuYui[menu.id] = yuiMenu;
    },

    /**
     * Initialize HTML areas.
     */
    _htmlArea: function (htmlArea) {
        var fckEditor = new FCKeditor(htmlArea.name);
        if (!xformsArrayContains(ORBEON.xforms.Globals.htmlAreaNames, htmlArea.name))
            ORBEON.xforms.Globals.htmlAreaNames.push(htmlArea.name);

        if (typeof opsXFormsProperties != "undefined" && typeof opsXFormsProperties["fck-editor-base-path"] != "undefined")
            fckEditor.BasePath = opsXFormsProperties["fck-editor-base-path"];
        else
            fckEditor.BasePath = BASE_URL + FCK_EDITOR_BASE_PATH;

        fckEditor.ToolbarSet = "OPS";

        // Change the language of the FCK Editor for its spellchecker, based on the USER_LANGUAGE variable
        var type_check = typeof USER_LANGUAGE;
        if (type_check != 'undefined') {
	        fckEditor.Config["AutoDetectLanguage"] = false;
    	    fckEditor.Config["DefaultLanguage"] = (USER_LANGUAGE != '') ? USER_LANGUAGE : 'en';
    	}
        // Change the path to a custom configuration, based on the FCK_CUSTOM_CONFIG variable
    	type_check = typeof FCK_CUSTOM_CONFIG;
    	if (type_check != 'undefined')
    		fckEditor.Config["CustomConfigurationsPath"] = FCK_CUSTOM_CONFIG;

        if (ORBEON.xforms.Globals.fckEditorLoading) {
            ORBEON.xforms.Globals.fckEditorsToLoad.push(fckEditor);
        } else {
            ORBEON.xforms.Globals.fckEditorLoading = true;
            fckEditor.ReplaceTextarea();
            ORBEON.xforms.Controls.updateHTMLAreaClasses(ORBEON.util.Dom.getElementById(fckEditor.InstanceName));
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
        var hasClose = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-close-true");
        var draggable = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-draggable-true");
        var isMinimal = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-appearance-minimal");
        ORBEON.util.Dom.removeClass(dialog, "xforms-initially-hidden");

        // Create dialog object
        if (isMinimal) {
            // Create minimal dialog
            yuiDialog = new YAHOO.widget.Overlay(dialog.id, {
                visible: false,
                constraintoviewport: true,
                iframe: true
            });
            // Close the dialog when users click on document
            YAHOO.util.Event.addListener(document.body, "click", ORBEON.xforms.Events.dialogMinimalBodyClick, yuiDialog);
        } else {
            // Create full dialog
            yuiDialog = new YAHOO.widget.Dialog(dialog.id, {
                modal: isModal,
                close: hasClose,
                visible: false,
                draggable: draggable,
                fixedcenter: false,
                constraintoviewport: true,
                underlay: "shadow"
            });
            // Register listener for when the dialog is closed by a click on the "x"
            yuiDialog.beforeHideEvent.subscribe(ORBEON.xforms.Events.dialogClose, dialog.id);
        }
        yuiDialog.render();

        // We hide the dialog as it otherwise interfers with other dialogs, preventing
        // the cursor from showing in input fields of other dialogs
        yuiDialog.element.style.display = "none";
        ORBEON.xforms.Globals.dialogs[dialog.id] = yuiDialog;
    }
};

ORBEON.xforms.Server = {

    Event: function(form, targetId, otherId, value, eventName, bubbles, cancelable, ignoreErrors) {
        this.form = form;
        this.targetId = targetId;
        this.otherId = otherId;
        this.value = value;
        this.eventName = eventName;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
        this.ignoreErrors = ignoreErrors;
    },

    /**
     * When an exception happens while we communicate with the server, we catch it and show an error in the UI.
     * This is to prevent the UI from becoming totally unusable after an error.
     */
    exceptionWhenTalkingToServer: function(e, formID) {
        ORBEON.util.Utils.logException("JavaScript error", e);
        var details = "Exception in client-side code.";
        details += "<ul>";
        if (e.message != null) details += "<li>Message: " + e.message + "</li>";
        if (e.fileName != null) details += "<li>File: " + e.fileName + "</li>";
        if (e.lineNumber != null) details += "<li>Line number: " + e.lineNumber + "</li>";
        details += "</ul>";
        ORBEON.xforms.Server.showError("Exception in client-side code", details, formID);
    },

    /**
     * Display the error panel and shows the specified detailed message in the detail section of the panel.
     */
    showError: function(title, details, formID) {
        if (!ORBEON.xforms.Globals.requestIgnoreErrors) {
            if (ORBEON.xforms.Globals.formErrorPanel[formID]) {
                ORBEON.xforms.Globals.formErrorPanel[formID].element.style.display = "block";
                ORBEON.xforms.Globals.formErrorPanel[formID].errorTitleDiv.innerHTML = title;
                ORBEON.xforms.Globals.formErrorPanel[formID].errorDetailsDiv.innerHTML = details;
                ORBEON.xforms.Globals.formErrorPanel[formID].show();
                ORBEON.xforms.Globals.formErrorPanel[formID].center();
            }
        }
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
                XFORMS_INTERNAL_SHORT_DELAY_IN_MS);
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
                        if (seenControlValue[event.targetId] == null) {
                            seenControlValue[event.targetId] = true;
                            // Don't send change value if the server already knows about the value of this control
                            if (ORBEON.util.Dom.hasClass(ORBEON.util.Dom.getElementById(event.targetId), "xforms-upload") ||
                                (ORBEON.xforms.Globals.serverValue[event.targetId] != "undefined"
                                    && ORBEON.xforms.Globals.serverValue[event.targetId] != event.value)) {
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
                var formID = ORBEON.xforms.Globals.requestForm.id;

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

                ORBEON.xforms.Globals.requestIgnoreErrors = true;
                var sendInitialDynamicState = false;
                for (var eventIndex = 0; eventIndex < ORBEON.xforms.Globals.eventQueue.length; eventIndex++) {
                    var event = ORBEON.xforms.Globals.eventQueue[eventIndex];
                    // Figure out if we will be ignoring error during this request or not
                    if (!event.ignoreErrors) {
                        ORBEON.xforms.Globals.requestIgnoreErrors = false;
                    }
                    // Figure out whether we need to send the initial dynamic state
                    if (event.eventName == "xxforms-all-events-required") {
                        sendInitialDynamicState = true;
                }
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
                    requestDocumentString += ORBEON.xforms.Globals.formStaticState[formID].value;
                    requestDocumentString += '</xxforms:static-state>\n';

                    // Add dynamic state
                    requestDocumentString += indent;
                    requestDocumentString += '<xxforms:dynamic-state>';
                    requestDocumentString += xformsGetFromClientState(formID, "ajax-dynamic-state");
                    requestDocumentString += '</xxforms:dynamic-state>\n';

                    // Add initial dynamic state if needed
                    if (sendInitialDynamicState) {
                        requestDocumentString += indent;
                        requestDocumentString += '<xxforms:initial-dynamic-state>';
                        requestDocumentString += xformsGetFromClientState(formID, "initial-dynamic-state");
                        requestDocumentString += '</xxforms:initial-dynamic-state>\n';
                    }

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
                ORBEON.xforms.Globals.requestRetries = XFORMS_REQUEST_RETRIES;
                ORBEON.xforms.Globals.requestDocument = requestDocumentString;
                ORBEON.xforms.Server.asyncRequest();
            }
        }

        // Hide loading indicator if we have not started a new request (nothing more to run)
        // and there are not events in the queue. However make sure not to hide the error message
        // if the last XHR query returned an error.
        if (!executedRequest && ORBEON.xforms.Globals.eventQueue.length == 0)
            xformsDisplayIndicator("none");
    },

    asyncRequest: function() {
        try {
            ORBEON.xforms.Globals.requestRetries--;
            YAHOO.util.Connect.setDefaultPostHeader(false);
            YAHOO.util.Connect.initHeader("Content-Type", "application/xml");
            var callback = {
                success: ORBEON.xforms.Server.handleResponse,
                failure: ORBEON.xforms.Server.handleFailure
            };
            if (XFORMS_DELAY_BEFORE_AJAX_TIMEOUT_IN_MS != -1)
                callback.timeout = XFORMS_DELAY_BEFORE_AJAX_TIMEOUT_IN_MS;
            YAHOO.util.Connect.asyncRequest("POST", XFORMS_SERVER_URL, callback, ORBEON.xforms.Globals.requestDocument);
        } catch (e) {
            ORBEON.xforms.Globals.requestInProgress = false;
            ORBEON.xforms.Server.exceptionWhenTalkingToServer(e, formID);
        }
    },

    handleFailure: function(o) {
        if (ORBEON.xforms.Globals.requestRetries > 0) {
            // If the request fails, we are trying again up to 3 times
            ORBEON.xforms.Globals.requestRetries--;
            ORBEON.xforms.Server.asyncRequest();
        } else {
            // We have tried this 3 times, give up.
            ORBEON.xforms.Globals.requestInProgress = false;
            ORBEON.xforms.Globals.requestDocument = "";
            var formID = ORBEON.xforms.Globals.requestForm.id;
            var details = "Error while processing response: " + (o.responseText !== undefined ? o.responseText : o.statusText);
            if (ORBEON.xforms.Globals.isRenderingEngineGecko && o.statusText == "communication failure") {
                // On Firefox, when the user navigates to another page while an Ajax request is in progress,
                // we receive an error here, which we don't want to display. We don't have a good way of knowning if we get
                // this error because there was really a communication failure or if this is because the user is
                // going to another page. So we wait some time before showing the error, hoping that if another page is
                // loading, that other page will be loaded by the time our timeout expires.
                window.setTimeout(function() { ORBEON.xforms.Server.showError("Error while processing response", details, formID); },
                    XFORMS_DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_IN_MS);
            } else {
                // Display alert right away
                ORBEON.xforms.Server.showError("Error while processing response", details, formID);
            }
        }
    },

    handleUploadResponse: function(o) {
        // Clear all the upload fields (currently we submit all of them, but in the future, should clear only the ones that have been submitted)
        var uploadElements = YAHOO.util.Dom.getElementsByClassName("xforms-upload", "span");
        for (var uploadIndex = 0; uploadIndex < uploadElements.length; uploadIndex++) {
            var uploadElement = uploadElements[uploadIndex];
            if (ORBEON.util.Dom.hasClass(uploadElement, "xforms-upload-state-empty"))// this also excludes templates
                ORBEON.util.Dom.clearUploadControl(uploadElement);
        }
        ORBEON.xforms.Server.handleResponse(o);
    },

    handleResponse: function(o) {
        var formID = ORBEON.xforms.Globals.requestForm.id;
        var responseXML = o.responseXML;
        if (!responseXML || (responseXML && responseXML.documentElement && responseXML.documentElement.tagName.toLowerCase() == "html")) {
            // The XML docucment does not come in o.responseXML: parse o.responseText.
            // This happens in particular when we get a response after a background upload.
            var xmlString = o.responseText.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
            responseXML = ORBEON.util.Dom.stringToDom(xmlString);
        }

        try {
            if (responseXML && responseXML.documentElement
                    && responseXML.documentElement.tagName.indexOf("event-response") != -1) {

                // Good: we received an XML document from the server
                var responseRoot = responseXML.documentElement;
                var newDynamicState = null;

                // Whether this response has triggered a load which will replace the current page.
                var newDynamicStateTriggersReplace = false;

                var xmlNamespace = null; // xforms namespace
                //Getting xforms namespace
                for (var j = 0; j < responseRoot.attributes.length; j++) {
                    if (responseRoot.attributes[j].nodeValue == XXFORMS_NAMESPACE_URI) {
                        var attrName = responseRoot.attributes[j].name;
                        xmlNamespace = attrName.substr(attrName.indexOf(":") + 1);
                        break;
                    }
                }

                for (var i = 0; i < responseRoot.childNodes.length; i++) {

                    // Update instances
                    if (xformsGetLocalName(responseRoot.childNodes[i]) == "dynamic-state") {
                        newDynamicState = ORBEON.util.Dom.getStringValue(responseRoot.childNodes[i]);
                        // Store new dynamic state as soon as we find it. This is because the server only keeps the last
                        // dynamic state. So if a JavaScript error happens later on while processing the response,
                        // the next request we do we still want to send the latest dynamic state known to the server.
                        xformsStoreInClientState(formID, "ajax-dynamic-state", newDynamicState);
                    }

                    if (xformsGetLocalName(responseRoot.childNodes[i]) == "action") {
                        var actionElement = responseRoot.childNodes[i];

                        // Firt repeat and delete "lines" in repeat (as itemset changed below might be in a new line)
                        for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {
                            var actionName = xformsGetLocalName(actionElement.childNodes[actionIndex]);
                            switch (actionName) {

                                case "control-values": {
                                    var controlValuesElement = actionElement.childNodes[actionIndex];
                                    var copyRepeatTemplateElements = ORBEON.util.Dom.getElementsByName(controlValuesElement,"copy-repeat-template",xmlNamespace);
                                    var copyRepeatTemplateElementsLength = copyRepeatTemplateElements.length;
                                    for (var j = 0; j < copyRepeatTemplateElementsLength; j++) {

                                        // Copy repeat template
                                        var copyRepeatTemplateElement = copyRepeatTemplateElements[j];
                                        var repeatId = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "id");
                                        var parentIndexes = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "parent-indexes");
                                        var idSuffix = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "id-suffix");
                                        // Put nodes of the template in an array
                                        var templateNodes = new Array();
                                        {
                                             // Locate end of the repeat
                                             var delimiterTagName = null;
                                             var templateRepeatEnd = ORBEON.util.Dom.getElementById("repeat-end-" + repeatId);
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
                                                var repeatEnd = ORBEON.util.Dom.getElementById("repeat-end-" + repeatId);
                                                var cursor = repeatEnd.previousSibling;
                                                while (!(cursor.nodeType == ELEMENT_TYPE
                                                        && ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                        && !ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-template"))) {
                                                    cursor = cursor.previousSibling;
                                                }
                                                afterInsertionPoint = cursor;
                                                // Nested repeat: does not contain a template
                                            } else {
                                                var repeatEnd = ORBEON.util.Dom.getElementById("repeat-end-" + xformsAppendRepeatSuffix(repeatId, parentIndexes));
                                                afterInsertionPoint = repeatEnd;
                                            }
                                        }
                                        // Insert copy of template nodes
                                        for (var templateNodeIndex = 0; templateNodeIndex < templateNodes.length; templateNodeIndex++) {
                                            templateNode = templateNodes[templateNodeIndex];
                                            afterInsertionPoint.parentNode.insertBefore(templateNode, afterInsertionPoint);
                                            ORBEON.xforms.Init.insertedElement(templateNode);
                                        }
                                        // Initialize newly added form elements. We don't need to do this for IE, because with
                                        // IE when an element is cloned, the clone has the same event listeners as the original.
                                        if (ORBEON.xforms.Globals.isRenderingEngineWebCore13)
                                            ORBEON.xforms.Init.registerListenersOnFormElements();
                                       }

                                       var deleteRepeatTemplateElements = ORBEON.util.Dom.getElementsByName(controlValuesElement,"delete-repeat-elements",xmlNamespace);
                                       var deleteRepeatTemplateElementsLength = deleteRepeatTemplateElements.length;
                                       for (var j = 0; j < deleteRepeatTemplateElementsLength; j++) {

                                           // Extract data from server response
                                           //var deleteElementElement = controlValuesElement.childNodes[j];
                                           var deleteElementElement = deleteRepeatTemplateElements[j];
                                           var deleteId = ORBEON.util.Dom.getAttribute(deleteElementElement, "id");
                                           var parentIndexes = ORBEON.util.Dom.getAttribute(deleteElementElement, "parent-indexes");
                                           var count = ORBEON.util.Dom.getAttribute(deleteElementElement, "count");
                                           // Find end of the repeat
                                           var repeatEnd = ORBEON.util.Dom.getElementById("repeat-end-" + xformsAppendRepeatSuffix(deleteId, parentIndexes));
                                           // Find last element to delete
                                           var lastElementToDelete;
                                           {
                                               lastElementToDelete = repeatEnd.previousSibling;
                                               if (parentIndexes == "") {
                                                   // Top-level repeat: need to go over template
                                                   while (true) {
                                                       // Look for delimiter that comes just before the template
                                                       if (lastElementToDelete.nodeType == ELEMENT_TYPE
                                                               && ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter")
                                                               && !ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-template"))
                                                           break;
                                                       lastElementToDelete = lastElementToDelete.previousSibling;
                                                   }
                                                   lastElementToDelete = lastElementToDelete.previousSibling;
                                               }
                                           }
                                           // Perform delete
                                           for (var countIndex = 0; countIndex < count; countIndex++) {
                                               var nestedRepeatLevel = 0;
                                               while (true) {
                                                   var wasDelimiter = false;
                                                   if (lastElementToDelete.nodeType == ELEMENT_TYPE) {
                                                       if (ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-begin-end") &&
                                                               lastElementToDelete.id.indexOf("repeat-end-") == 0) {
                                                           // Entering nested repeat
                                                           nestedRepeatLevel++;
                                                       } else if (ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-begin-end") &&
                                                               lastElementToDelete.id.indexOf("repeat-begin-") == 0) {
                                                           // Exiting nested repeat
                                                           nestedRepeatLevel--;
                                                       } else {
                                                           wasDelimiter = nestedRepeatLevel == 0 &&
                                                              ORBEON.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter");
                                                       }
                                                   }
                                                   var previous = lastElementToDelete.previousSibling;
                                                   lastElementToDelete.parentNode.removeChild(lastElementToDelete);
                                                   lastElementToDelete = previous;
                                                   if (wasDelimiter) break;
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
                                        var itemsetTree = ORBEON.util.String.eval(ORBEON.util.Dom.getStringValue(itemsetElement));
                                        var controlId = ORBEON.util.Dom.getAttribute(itemsetElement, "id");
                                        var documentElement = ORBEON.util.Dom.getElementById(controlId);
                                        var documentElementClasses = documentElement.className.split(" ");

                                        if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-open")) {

                                            // Build list with new values
                                            var newValues = new Array();
                                            for (var topIndex = 0; topIndex < itemsetTree.length; topIndex++)
                                                newValues.push(itemsetTree[topIndex][1]);

                                            // Case of the auto-complete control
                                            var textfield = ORBEON.util.Dom.getChildElementByIndex(documentElement, 0);
                                            textfield.actb_keywords = newValues;
                                            // Reopen auto-complete if necessary
                                            var lastKeyCode = ORBEON.xforms.Globals.autoCompleteLastKeyCode[documentElement.id];
                                            if (lastKeyCode != null)
                                                ORBEON.xforms.Globals.autoCompleteOpen[documentElement.id](lastKeyCode);

                                        } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-xxforms-tree")
                                                || ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-xxforms-tree")) {

                                            // Case of a tree

                                            // Remove markup for current tree
                                            var yuiTree = ORBEON.xforms.Globals.treeYui[documentElement.id];
                                            var yuiRoot = yuiTree.getRoot();
                                            yuiTree.removeChildren(yuiRoot);
                                            // Expand root. If we don't the tree with checkboxes does not show.
                                            yuiRoot.expand();

                                            // Re-populate the tree
                                            ORBEON.xforms.Init._initTreeDivFromArray(documentElement, yuiTree, itemsetTree);

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

                                            // Utility function to generate an option
                                            function generateOption(label, value, selectedValues) {
                                                var selected = xformsArrayContains(selectedValues, value);
                                                return '<option value="' + ORBEON.util.String.escapeAttribute(value) + '"'
                                                        + (selected ? ' selected="selected"' : '') + '>' + label + '</option>';
                                            }

                                            // Build new content for the select element
                                            var sb = new Array();
                                            for (var topIndex = 0; topIndex < itemsetTree.length; topIndex++) {
                                                var itemElement = itemsetTree[topIndex];
                                                var label = itemElement[0];
                                                var value = itemElement[1];
                                                if (itemElement.length > 2) {
                                                    // This is item that contains other elements
                                                    sb[sb.length] = '<optgroup label="' + ORBEON.util.String.escapeAttribute(label) + '">';
                                                    // Go through options in this optgroup
                                                    for (var innerIndex = 2; innerIndex < itemElement.length; innerIndex++) {
                                                        var itemElementOption = itemElement[innerIndex];
                                                        sb[sb.length] = generateOption(itemElementOption[0], itemElementOption[1], selectedValues);
                                                    }
                                                    sb[sb.length] = '</optgroup>';
                                                } else {
                                                    // This item is directly an option
                                                    sb[sb.length] = generateOption(label, value, selectedValues);
                                                }
                                            }

                                            // Set content of select element
                                            if (ORBEON.xforms.Globals.isRenderingEngineTridend) {
                                                // IE does not support setting the content of a select with innerHTML
                                                // So we have to generate the whole select, and use outerHTML
                                                documentElement.innerHTML = "";
                                                documentElement.outerHTML =
                                                    documentElement.outerHTML.substring(0, documentElement.outerHTML.indexOf("</SELECT>"))
                                                    + sb.join("") + "</select>";
                                                // Get again control, as it has been re-created
                                                documentElement = ORBEON.util.Dom.getElementByIdNoCache(controlId);
                                                // Must now update the cache
                                                ORBEON.xforms.Globals.idToElement[controlId] = documentElement;
                                                // Re-register handlers on the newly created control
                                                ORBEON.xforms.Init.registerListenersOnFormElement(documentElement);
                                            } else {
                                                // Version for compliant browsers
                                                documentElement.innerHTML = sb.join("");
                                            }

                                        } else {

                                            // Case of checkboxes / radio buttons

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
                                            for (var k = 0; k < itemsetTree.length; k++) {
                                                var itemElement = itemsetTree[k];
                                                var templateClone = template.cloneNode(true);
                                                xformsStringReplace(templateClone, "$xforms-template-label$", itemElement[0]);
                                                xformsStringReplace(templateClone, "$xforms-template-value$", itemElement[1]);
                                                xformsStringReplace(templateClone, "$xforms-item-index$", itemIndex);
                                                documentElement.appendChild(templateClone);
                                                // Restore checked state after copy
                                                if (valueToChecked[itemElement[1]] == true)
                                                    xformsGetInputUnderNode(templateClone).checked = true;
                                                itemIndex++;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Handle other actions
                        var serverEventsIndex = -1;
                        for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {

                            var actionName = xformsGetLocalName(actionElement.childNodes[actionIndex]);
                            switch (actionName) {

                                // Update controls
                                case "control-values": {
                                    var controlValuesElement = actionElement.childNodes[actionIndex];
                                    var controlElements = ORBEON.util.Dom.getElementsByName(controlValuesElement,"control",xmlNamespace);
                                    var controlElementslength = controlElements.length;
                                    // Update control value and MIPs
				                    for(var j = 0 ; j < controlElementslength; j++){
                                        var controlElement = controlElements[j];
                                        var newControlValue = ORBEON.util.Dom.getStringValue(controlElement);
                                        var controlId = ORBEON.util.Dom.getAttribute(controlElement, "id");
                                        var staticReadonly = ORBEON.util.Dom.getAttribute(controlElement, "static");
                                        var relevant = ORBEON.util.Dom.getAttribute(controlElement, "relevant");
                                        var readonly = ORBEON.util.Dom.getAttribute(controlElement, "readonly");
                                        var required = ORBEON.util.Dom.getAttribute(controlElement, "required");
                                        var displayValue = ORBEON.util.Dom.getAttribute(controlElement, "display-value");
                                        var type = ORBEON.util.Dom.getAttribute(controlElement, "type");
                                        var documentElement = ORBEON.util.Dom.getElementById(controlId);
                                        if (documentElement == null) {
                                            documentElement = ORBEON.util.Dom.getElementById("group-begin-" + controlId);
                                        }
                                        var documentElementClasses = documentElement.className.split(" ");
                                        var isControl = ORBEON.util.Dom.hasClass(documentElement, "xforms-control");

                                        // Save new value sent by server (upload controls don't carry their value the same way as other controls)
                                        var previousServerValue = ORBEON.xforms.Globals.serverValue[controlId];
                                        if (!ORBEON.util.Dom.hasClass(documentElement, "xforms-upload"))
                                             ORBEON.xforms.Globals.serverValue[controlId] = newControlValue;

                                        // Handle migration of control from non-static to static if needed
                                         var isStaticReadonly = ORBEON.util.Dom.hasClass(documentElement, "xforms-static");
                                         if (!isStaticReadonly && staticReadonly == "true") {
                                             if (isControl) {
                                                 // Replace existing element with span
                                                 var parentElement = documentElement.parentNode;
                                                 var newDocumentElement = document.createElement("span");
                                                 newDocumentElement.setAttribute("id", controlId);
                                                 newDocumentElement.setAttribute("class", documentElementClasses.join(" ") + " xforms-static");
                                                 parentElement.replaceChild(newDocumentElement, documentElement);
                                                 // Remove alert
                                                 var alertElement = ORBEON.xforms.Controls._getControlLabel(newDocumentElement, "xforms-alert");
                                                 if (alertElement != null)
                                                     parentElement.removeChild(alertElement);
                                                     // Remove hint
                                                     var hintLabel = ORBEON.xforms.Controls._getControlLabel(newDocumentElement, "xforms-hint");
                                                     if (hintLabel != null)
                                                         parentElement.removeChild(hintLabel);
                                                     // Update document element information
                                                     documentElement = newDocumentElement;
                                             } else {
                                                   // Just add the new class
                                                   ORBEON.util.Dom.addClass(documentElement, "xforms-static");
                                             }
                                             isStaticReadonly = true;
                                             documentElementClasses = documentElement.className.split(" ");
                                         }

					                     // We update the relevance and readonly before we update the value. If we don't, updating the value
                                         // can fail on IE in some cases. (The details about this issue have been lost.)

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
                                                if (ORBEON.util.Dom.hasClass(documentElement, "xforms-input") && !ORBEON.util.Dom.hasClass(documentElement, "xforms-type-boolean")) {
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
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-full")
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-input-appearance-full")) {
                                                    // XForms radio buttons
                                                    for (var spanIndex = 0; spanIndex < documentElement.childNodes.length; spanIndex++) {
                                                        var span = documentElement.childNodes[spanIndex];
                                                        var input = span.firstChild;
                                                        setReadonlyOnFormElement(input, isReadonly);
                                                    }
                                                } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-xxforms-autocomplete")) {
                                                    // Auto-complete field
                                                    var input = ORBEON.util.Dom.getChildElementByIndex(documentElement, 0);
                                                    setReadonlyOnFormElement(input, isReadonly);
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
                                                } else if(ORBEON.util.Dom.hasClass(documentElement, "xforms-upload")) {
                                                   // Upload control
                                                     setReadonlyOnFormElement(
                                                        ORBEON.util.Dom.getChildElementByClass(documentElement, "xforms-upload-select"), isReadonly);
                                                } else {
                                                    // Other controls
                                                    setReadonlyOnFormElement(documentElement, isReadonly);
                                                }
                                            }
                                            // Update value
                                            if (isControl) {
                                                if (ORBEON.util.Dom.hasClass(documentElement, "xforms-output") || isStaticReadonly) {
                                                    // XForms output or "static readonly" mode
                                                    var newOutputControlValue = displayValue != null ? displayValue : newControlValue;
                                                    if (ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-image")) {
                                                        var image = ORBEON.util.Dom.getChildElementByIndex(documentElement, 0);
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
                                                        ORBEON.util.Dom.getChildElementByIndex(documentElement, 0).value = newControlValue;
                                                        documentElement.previousValue = newControlValue;
                                                    }
                                                } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-full")
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-full")
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-input-appearance-full")) {
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
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-minimal")
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-input-appearance-compact")
                                                        || ORBEON.util.Dom.hasClass(documentElement, "xforms-input-appearance-minimal")) {
                                                    // Handle lists and comboboxes
                                                    var selectedValues = ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-compact")
                                                       ? newControlValue.split(" ") : new Array(newControlValue);
                                                    var options = documentElement.options;
                                                    for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                                                        var option = options[optionIndex];
                                                        try {
                                                            option.selected = xformsArrayContains(selectedValues, option.value);
                                                        } catch (e) {
                                                            // nop
                                                            //
                                                            // This is to prevent the error "Could not set the selected property. Unspecified error." in IE.
                                                            // Like noted in this blog entry: http://ianso.blogspot.com/2005_10_01_ianso_archive.html (search
                                                            // for the error message), it seems that DOM updates are somewhat asynchrous and that when you
                                                            // make an element visible and change a property right after that, it is sometimes as if the element
                                                            // is not visible yet, and so the property cannot be changed.
                                                        }
                                                    }
                                                } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-input") && !ORBEON.util.Dom.hasClass(documentElement, "xforms-type-boolean")) {
                                                    // XForms input
                                                    var displayField = ORBEON.util.Dom.getChildElementByIndex(documentElement, 0);
                                                    var inputField = ORBEON.util.Dom.getChildElementByIndex(documentElement, 1);
                                                    var datePicker = ORBEON.util.Dom.getChildElementByIndex(documentElement, 2);
                                                    // Change classes on control and date pick based on type
                                                    if (type == "{http://www.w3.org/2001/XMLSchema}date" || type == "{http://www.w3.org/2002/xforms}date") {
//                                                         for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
//                                                             var child = documentElement.childNodes[childIndex];
                                                              ORBEON.util.Dom.addClass(documentElement, "xforms-type-date");
                                                              ORBEON.util.Dom.removeClass(documentElement, "xforms-type-string");
//                                                            }
                                                    } else if (type != null && type != "{http://www.w3.org/2001/XMLSchema}date" && type != "{http://www.w3.org/2002/xforms}date") {
//                                                            for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
//                                                                var child = documentElement.childNodes[childIndex];
//                                                                ORBEON.util.Dom.addClass(documentElement, "xforms-type-string");
                                                                ORBEON.util.Dom.removeClass(documentElement, "xforms-type-date");
//                                                            }
                                                        }

                                                        // Populate values
                                                        if (ORBEON.util.Dom.hasClass(documentElement, "xforms-type-date")) {
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
                                                        var doUpdate =
                                                                // Update only if the new value is different than the value already have in the HTML area
                                                                xformsNormalizeEndlines(htmlEditor.GetXHTML()) != xformsNormalizeEndlines(newControlValue)
                                                                // Also update only if the value in the HTML area is the same now as it was when we sent it to the server
                                                                // If there is no previousServerValue, go ahead and update field
                                                                && (previousServerValue == null || htmlEditor.GetXHTML() == previousServerValue);
                                                        if (doUpdate) {
                                                            // Directly modify the DOM instead of using SetHTML() provided by the FCKeditor,
                                                            // as we loose our listeners after using the later
                                                            htmlEditor.EditorDocument.body.innerHTML = newControlValue;
                                                            // Set again the server value based on the HTML as seen from the field. HTML changes slightly when it
                                                            // is pasted in the FCK editor. The server value will be compared to the field value, to (a) figure out
                                                            // if we need to send the value again to the server and (b) to figure out if the FCK editor has been edited
                                                            // since the last time we sent the value to the serer. The bottom line is that we are going to compare
                                                            // the server value to the content of the field. So storing the value as seen by the field vs. as seen by
                                                            // server accounts for the slight difference there might be in those 2 representations.
                                                            ORBEON.xforms.Globals.serverValue[controlId] = htmlEditor.GetXHTML();
                                                            documentElement.value = newControlValue;
                                                            documentElement.previousValue = newControlValue;
                                                        }
                                                    } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select-appearance-xxforms-tree")) {
                                                        // Select tree
                                                        var values = newControlValue.split(" ");
                                                        var yuiTree = ORBEON.xforms.Globals.treeYui[documentElement.id];
                                                        for (nodeIndex in yuiTree._nodes) {
                                                            var node = yuiTree._nodes[nodeIndex];
                                                            if (node.children.length == 0) {
                                                                var checked = xformsArrayContains(values, node.data.value);
                                                                if (checked) node.check(); else node.uncheck();
                                                            }
                                                        }
                                                        documentElement.value = newControlValue;
                                                        documentElement.previousValue = newControlValue;

                                                    } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-select1-appearance-xxforms-tree")) {
                                                        // Select1 tree
                                                        // Make sure the tree is open enough so the node with the new value is visible
                                                        var yuiTree = ORBEON.xforms.Globals.treeYui[documentElement.id];
                                                        ORBEON.xforms.Controls.treeOpenSelectedVisible(yuiTree, [newControlValue]);
                                                        // Deselect old value, select new value
                                                        var oldNode = yuiTree.getNodeByProperty("value", documentElement.value);
                                                        var newNode = yuiTree.getNodeByProperty("value", newControlValue);
                                                        if (oldNode != null)
                                                            YAHOO.util.Dom.removeClass(oldNode.getLabelEl(), "xforms-tree-label-selected");
                                                        if (newNode != null)
                                                            YAHOO.util.Dom.addClass(newNode.getLabelEl(), "xforms-tree-label-selected");
                                                        // Update value
                                                        documentElement.value = newControlValue;
                                                        documentElement.previousValue = newControlValue;
                                                    } else if (ORBEON.util.Dom.hasClass(documentElement, "xforms-upload")) {
                                                        // Upload

                                                        // <xxforms:control id="xforms-control-id"
                                                        //    state="empty|file"
                                                        //    filename="filename.txt" mediatype="text/plain" size="23kb"/>

                                                        // Get attributes from response
                                                        var state = ORBEON.util.Dom.getAttribute(controlElement, "state");
                                                        var filename = ORBEON.util.Dom.getAttribute(controlElement, "filename");
                                                        var mediatype = ORBEON.util.Dom.getAttribute(controlElement, "mediatype");
                                                        var size = ORBEON.util.Dom.getAttribute(controlElement, "size");
                                                        // Get elements we want to modify from the DOM
                                                        var fileInfoSpan = ORBEON.util.Dom.getChildElementByClass(documentElement, "xforms-upload-info");
                                                        var fileNameSpan = ORBEON.util.Dom.getChildElementByClass(fileInfoSpan, "xforms-upload-filename");
                                                        var mediatypeSpan = ORBEON.util.Dom.getChildElementByClass(fileInfoSpan, "xforms-upload-mediatype");
                                                        var sizeSpan = ORBEON.util.Dom.getChildElementByClass(fileInfoSpan, "xforms-upload-size");
                                                        // Set values in DOM
                                                        if (state == "empty") {
                                                            ORBEON.util.Dom.removeClass(documentElement, "xforms-upload-state-file")
                                                            ORBEON.util.Dom.addClass(documentElement, "xforms-upload-state-empty")
                                                        }
                                                        if (state == "file") {
                                                            ORBEON.util.Dom.removeClass(documentElement, "xforms-upload-state-empty")
                                                            ORBEON.util.Dom.addClass(documentElement, "xforms-upload-state-file")

                                                            // Clear upload input by replacing the control
                                                            ORBEON.util.Dom.clearUploadControl(documentElement);
                                                        }
                                                        if (filename != null)
                                                            ORBEON.util.Dom.setStringValue(fileNameSpan, filename);
                                                        if (mediatype != null)
                                                            ORBEON.util.Dom.setStringValue(mediatypeSpan, mediatype);
                                                        if (size != null)
                                                            ORBEON.util.Dom.setStringValue(sizeSpan, size);
                                                    } else if (typeof(documentElement.value) == "string") {
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
                                                }

                                                // Update the required-empty/required-full even if the required has not changed or
                                                // is not specified as the value may have changed
                                                if (!isStaticReadonly) {
                                                    ORBEON.xforms.Controls.updateRequiredEmpty(documentElement);
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

                                                // After we update classes on textarea, copy those classes on the FCKeditor iframe
                                                if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea")
                                                        && ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                                    ORBEON.xforms.Controls.updateHTMLAreaClasses(documentElement);
                                                }
                                            }

                                            // Model item properties on a repeat item
                                        var repeatIterationElements = ORBEON.util.Dom.getElementsByName(controlValuesElement,"repeat-iteration",xmlNamespace);
                                            var repeatIterationElementslength = repeatIterationElements.length;
                                            // Extract data from server response
                                            for(var j = 0 ; j < repeatIterationElementslength; j++) {
                                                var repeatIterationElement = repeatIterationElements[j];
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
                                                // This is a case
                                                var caseBeginId = "xforms-case-begin-" + controlId;
                                                var caseBegin = ORBEON.util.Dom.getElementById(caseBeginId);
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
                                                if (visibile)  {
                                                    // Fixes cursor Firefox issue; more on this in dialog init code
                                                    yuiDialog.element.style.display = "block";
                                                    // Show the dialog
                                                    yuiDialog.show();
                                                    // By default try to display the dialog inside the viewport, but this can be overridden with consrain="false"
                                                    var constrain = ORBEON.util.Dom.getAttribute(divElement, "constrain") == "false" ? false : true;
                                                    yuiDialog.cfg.setProperty("constraintoviewport", constrain);
                                                    // Position the dialog either at the center of the viewport or relative of a neighbor
                                                    var neighbor = ORBEON.util.Dom.getAttribute(divElement, "neighbor");
                                                    if (neighbor == null) {
                                                        // Center dialog in page, if not positined relative to other element
                                                        yuiDialog.center();
                                                    } else {
                                                        // Align dialog relative to neighbor
                                                        yuiDialog.cfg.setProperty("context", [neighbor, "tl", "bl"]);
                                                        yuiDialog.align();
                                                        ORBEON.xforms.Globals.dialogMinimalVisible[controlId] = true;
                                                    }
                                                    // Take out the focus from the current control. This is particulary important with non-modal dialogs
                                                    // opened with a minimal trigger, otherwise we have a dotted line around the link after it opens.
                                                    if (ORBEON.xforms.Globals.lastFocusControlId) {
                                                        var focusedElement = ORBEON.util.Dom.getElementById(ORBEON.xforms.Globals.lastFocusControlId);
                                                        if (focusedElement) focusedElement.blur();
                                                    }
                                                } else {
                                                    yuiDialog.hide();
                                                    // Fixes cursor Firefox issue; more on this in dialog init code
                                                    yuiDialog.element.style.display = "none";
                                                    // Remember the server knows that this dialog is closed so we don't close it again later
                                                    if (ORBEON.xforms.Globals.dialogMinimalVisible[yuiDialog.element.id])
                                                        ORBEON.xforms.Globals.dialogMinimalVisible[yuiDialog.element.id] = false;
                                                }
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
                                        if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                            var children = ORBEON.xforms.Globals.repeatTreeParentToAllChildren[repeatId];
                                            if (children != null) { // test on null is a hack because repeatId may be trash when some libraries override Object
                                                for (var childIndex in children) {
                                                    var child = children[childIndex];
                                                    if (!newRepeatIndexes[child])
                                                        newRepeatIndexes[child] = ORBEON.xforms.Globals.repeatIndexes[child];
                                                }
                                            }
                                        }
                                    }
                                    // Unhighlight items at old indexes
                                    for (var repeatId in newRepeatIndexes) {
                                        if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                            var oldIndex = ORBEON.xforms.Globals.repeatIndexes[repeatId];
                                            if (typeof oldIndex == "string" && oldIndex != 0) { // hack because repeatId may be trash when some libraries override Object
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
                                    }
                                    // Store new indexes
                                    for (var repeatId in newRepeatIndexes) {
                                        var newIndex = newRepeatIndexes[repeatId];
                                        ORBEON.xforms.Globals.repeatIndexes[repeatId] = newIndex;
                                    }
                                    // Highlight item at new index
                                    for (var repeatId in newRepeatIndexes) {
                                        if (typeof repeatId == "string") { // hack because repeatId may be trash when some libraries override Object
                                            var newIndex = newRepeatIndexes[repeatId];
                                            if (typeof newIndex == "string" && newIndex != 0) { // hack because repeatId may be trash when some libraries override Object
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
                                    }
                                    break;
                                }

                                // Server events
                                case "server-events": {
                                    // If there is a "submission" element, this must always come first
                                    serverEventsIndex = actionIndex;
                                    break;
                                }

                                // Submit form
                                case "submission": {
                                    var submissionElement = actionElement.childNodes[actionIndex];
                                    var showProcess = ORBEON.util.Dom.getAttribute(submissionElement, "show-progress");
                                    var action = ORBEON.util.Dom.getAttribute(submissionElement, "action");
                                    var replace = ORBEON.util.Dom.getAttribute(submissionElement, "replace");
                                    var target = ORBEON.util.Dom.getAttribute(submissionElement, "target");
                                    if (replace == null) replace = "all";
                                    ORBEON.xforms.Globals.formDynamicState[formID].value = newDynamicState;
                                    if (serverEventsIndex != -1) {
                                        ORBEON.xforms.Globals.formServerEvents[formID].value = ORBEON.util.Dom.getStringValue(actionElement.childNodes[serverEventsIndex]);
                                    } else {
                                        ORBEON.xforms.Globals.formServerEvents[formID].value = "";
                                    }
                                    if (replace == "all") {
                                        // Go to another page
                                        if (showProcess != "false") {
                                            // Display loading indicator unless the server tells us not to display it
                                            newDynamicStateTriggersReplace = true;
                                        }
                                        ORBEON.xforms.Globals.requestForm.action = action;
                                        if (target == null) {
                                            // Reset as this may have been changed before by asyncRequest
                                            ORBEON.xforms.Globals.requestForm.removeAttribute("target");
                                        } else {
                                            // Set the requested target
                                            ORBEON.xforms.Globals.requestForm.target = target;
                                        }
                                        ORBEON.xforms.Globals.requestForm.submit();
                                    } else {
                                        // Submit form in the background
                                        YAHOO.util.Connect.setForm(ORBEON.xforms.Globals.requestForm, true, true);
                                        var callback =  {
                                            upload: ORBEON.xforms.Server.handleUploadResponse,
                                            failure: ORBEON.xforms.Server.handleFailure
                                        }
                                        YAHOO.util.Connect.asyncRequest("POST", action, callback);
                                    }
                                    ORBEON.xforms.Globals.formServerEvents[formID].value = "";
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
                                            try {
                                                window.location.href = resource;
                                            } catch (e) {
                                                // nop
                                                //
                                                // This is to prevent the error "Unspecified error" in IE. This can happen when navigating away
                                                // is cancelled by the user pressing cancel on a dialog displayed on unload.
                                            }
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
                                    ORBEON.xforms.Controls.setFocus(controlId);
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

                if (newDynamicStateTriggersReplace) {
                    // Display loading indicator when we go to another page.
                    // Display it even if it was not displayed before as loading the page could take time.
                    xformsDisplayIndicator("loading");
                    ORBEON.xforms.Globals.loadingOtherPage = true;
                }

                // Hack for instance inspector - need to see how to do this better
//                sourceResize();

            } else if (responseXML && responseXML.documentElement
                    && responseXML.documentElement.tagName.indexOf("error") != -1) {
                // Extract and display error message
                var title = ORBEON.util.Dom.getStringValue(ORBEON.util.Dom.getElementsByName(responseXML.documentElement, "title", null)[0]);
                var details = ORBEON.util.Dom.getStringValue(ORBEON.util.Dom.getElementsByName(responseXML.documentElement, "body", null)[0]);
                ORBEON.xforms.Server.showError(title, details, formID);
            } else {
                // The server didn't send valid XML
                ORBEON.xforms.Globals.lastRequestIsError = true;
                ORBEON.xforms.Server.showError("Server didn't respond with valid XML", "Server didn't respond with valid XML", formID);
            }
        } catch (e) {
            ORBEON.xforms.Server.exceptionWhenTalkingToServer(e, formID);
        }

        // Reset changes, as changes are included in this bach of events
        ORBEON.xforms.Globals.changedIdsRequest = {};
        // Go ahead with next request, if any
        ORBEON.xforms.Globals.requestInProgress = false;
        ORBEON.xforms.Globals.requestDocument = "";
        ORBEON.xforms.Globals.executeEventFunctionQueued++;
        ORBEON.xforms.Server.executeNextRequest(false);
    },

    callUserScript: function(functionName, targetId, observerId) {
        var targetElement = ORBEON.util.Dom.getElementById(targetId);
        var observer = ORBEON.util.Dom.getElementById(observerId);
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
    var beginElement = ORBEON.util.Dom.getElementById(beginElementId);
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

function xformsLog(object) {
    var debugDiv = ORBEON.util.Dom.getElementById("xforms-debug");
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
    // Convert object to text
    text =
        object === undefined ? "undefined"
        : object === null ? "null"
        : typeof object == "string" && object == "" ? "empty string"
        : object.nodeType && object.nodeType == ORBEON.util.Dom.ELEMENT_TYPE ? "Element " + object.tagName
        : object.nodeType && object.nodeType == ORBEON.util.Dom.TEXT_TYPE ? "Text: " + ORBEON.util.Dom.getStringValue(object)
        : object;
    debugDiv.innerHTML += text + " | ";
}

function xformsLogTime(text) {
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
    var formID = form.id;
    switch (state) {
        case "loading":
            if (ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID] != null) {
                ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID].cfg.setProperty("visible", true);
                ORBEON.xforms.Controls.updateLoadingPosition(formID);
            }
            if (ORBEON.xforms.Globals.formLoadingNone[formID] != null)
                ORBEON.xforms.Globals.formLoadingNone[formID].style.display = "block";
            break;
        case "none":
            if (!ORBEON.xforms.Globals.loadingOtherPage) {
                if (ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID] != null)
                    ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID].cfg.setProperty("visible", false);
                if (ORBEON.xforms.Globals.formLoadingNone[formID] != null)
                    ORBEON.xforms.Globals.formLoadingNone[formID].style.display = "block";
            }
            break;
    }
}

// Gets a value stored in the hidden client-state input field
function xformsGetFromClientState(formID, key) {
    var clientState = ORBEON.xforms.Globals.formClientState[formID];
    var keyValues = clientState.value.split("&");
    for (var i = 0; i < keyValues.length; i = i + 2)
        if (keyValues[i] == key)
            return unescape(keyValues[i + 1]);
    return null;
}

// Returns a value stored in the hidden client-state input field
function xformsStoreInClientState(formID, key, value) {
    var clientState = ORBEON.xforms.Globals.formClientState[formID];
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
        ORBEON.xforms.Controls.updateHTMLAreaClasses(ORBEON.util.Dom.getElementById(fckEditor.InstanceName));
    } else {
        ORBEON.xforms.Globals.fckEditorLoading = false;
        if (typeof xformsPageLoadedServer != "undefined")
            xformsPageLoadedServer();
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
    if (element.id) {
        element.id = xformsAppendRepeatSuffix(element.id, idSuffixWithDepth);
        ORBEON.xforms.Globals.idToElement[element.id] = element;
    }
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
        depth = (depth == 4) ? 1 : depth + 1;
    }
    return "xforms-repeat-selected-item-" + depth;
}

function xformsDisplayLoading() {
    if (ORBEON.xforms.Globals.requestInProgress == true)
        xformsDisplayIndicator("loading");
}

// Run xformsPageLoaded when the browser has finished loading the page
// In case this script is loaded twice, we still want to run the initialization only once
if (!ORBEON.xforms.Globals.pageLoadedRegistered) {
    ORBEON.xforms.Globals.pageLoadedRegistered = true;
    // If the browser does not provide a console object, create one which delegates log() to xformsLog()
    YAHOO.util.Event.addListener(window, "load", ORBEON.xforms.Init.document);
    ORBEON.xforms.Globals.debugLastTime = new Date().getTime();
}
