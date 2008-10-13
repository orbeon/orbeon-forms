/**
 *  Copyright (C) 2008 Orbeon, Inc.
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

/**
 * === How state handling works
 *
 * Places where state are stored:
 *
 * - The current dynamic state is stored in:
 *      ORBEON.xforms.Globals.formDynamicState[formID].value
 * - The initial dynamic state is stored in the client state:
 *      xformsGetFromClientState(formID, "initial-dynamic-state")
 * - The static state is stored in
 *      ORBEON.xforms.Globals.formStaticState[formID].value
 *
 * Modifcations to the state information:
 *
 * - When the page is loaded if the client state is empty, the dynamic state is stored in the client state.
 * - When a response to an Ajax response is received the new dynamic state is stored right away.
 * - When an Ajax response also contains an updated static state, that state is stored as well.
 *
 */

// Parameter names
// NOTE: Names below MUST match the ones in XFormsProperties
var SESSION_HEARTBEAT_PROPERTY = "session-heartbeat";
var SESSION_HEARTBEAT_DELAY_PROPERTY = "session-heartbeat-delay";
var FCK_EDITOR_BASE_PATH_PROPERTY = "fck-editor-base-path";
var DELAY_BEFORE_INCREMENTAL_REQUEST_PROPERTY = "delay-before-incremental-request";
var DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_PROPERTY = "delay-before-force-incremental-request";
var DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_PROPERTY = "delay-before-gecko-communication-error";
var DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_PROPERTY = "delay-before-close-minimal-dialog";
var DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY = "delay-before-ajax-timeout";
var INTERNAL_SHORT_DELAY_PROPERTY = "internal-short-delay";
var DELAY_BEFORE_DISPLAY_LOADING_PROPERTY = "delay-before-display-loading";
var REQUEST_RETRIES_PROPERTY = "request-retries";
var DEBUG_WINDOW_HEIGHT_PROPERTY = "debug-window-height";
var DEBUG_WINDOW_WIDTH_PROPERTY = "debug-window-width";
var LOADING_MIN_TOP_PADDING_PROPERTY = "loading-min-top-padding";
var REVISIT_HANDLING_PROPERTY = "revisit-handling";
var HELP_HANDLER_PROPERTY = "help-handler";
var HELP_TOOLTIP_PROPERTY = "help-tooltip";
var OFFLINE_SUPPORT_PROPERTY = "offline";
var FORMAT_INPUT_TIME_PROPERTY = "format.input.time";
var FORMAT_INPUT_DATE_PROPERTY = "format.input.date";
var DATE_PICKER_PROPERTY = "datepicker";

var APPLICATION_RESOURCES_VERSION_PROPERTY = "oxf.resources.version-number";

// Parameter defaults
// NOTE: Default values below MUST match the ones in XFormsProperties
var XFORMS_SESSION_HEARTBEAT = true;
var XFORMS_SESSION_HEARTBEAT_DELAY = 12 * 60 * 60 * 800; // 80 % of 12 hours in ms
var FCK_EDITOR_BASE_PATH = "/ops/fckeditor/";
var XFORMS_DELAY_BEFORE_INCREMENTAL_REQUEST_IN_MS = 500;
var XFORMS_DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_IN_MS = 2000;
var XFORMS_DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_IN_MS = 5000;
var XFORMS_DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_IN_MS = 5000;
var XFORMS_DELAY_BEFORE_AJAX_TIMEOUT_IN_MS = -1;
var XFORMS_INTERNAL_SHORT_DELAY_IN_MS = 10;
var XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS = 500;
var XFORMS_REQUEST_RETRIES = 1;
var XFORMS_DEBUG_WINDOW_HEIGHT = 600;
var XFORMS_DEBUG_WINDOW_WIDTH = 300;
var XFORMS_LOADING_MIN_TOP_PADDING = 10;
var XFORMS_REVISIT_HANDLING = "restore";
var XFORMS_HELP_HANDLER = false;
var XFORMS_HELP_TOOLTIP = false;
var XFORMS_OFFLINE_SUPPORT = false;
var XFORMS_FORMAT_INPUT_TIME = "[h]:[m]:[s] [P]";
var XFORMS_FORMAT_INPUT_DATE = "[M]/[D]/[Y]";
var XFORMS_DATEPICKER = "yui";

var APPLICATION_RESOURCES_VERSION = "1.0";

/**
 * Constants
 */
var XFORMS_SEPARATOR_1 = "\xB7";
var XFORMS_SEPARATOR_2 = "-";
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var PATH_TO_JAVASCRIPT_1 = "/ops/javascript/xforms";
var PATH_TO_JAVASCRIPT_2 = "/xforms-server/";
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
ORBEON.widgets = ORBEON.widgets || {};
ORBEON.xforms.Globals = ORBEON.xforms.Globals || {}

/**
 * Global constants and variable
 */

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
    getElementsByName: function(element, localName, namespace) {
        return element.getElementsByTagName(namespace == null ? localName : namespace + ":" + localName);
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
        return element.getElementsByTagName((ORBEON.xforms.Globals.isFF3 && namespace != null ? namespace + ":" : "") + localName);
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
     *    able to respond just by looking at the cache, instead of calling document.getElementById. This has a
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
        if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
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

    getChildElementsByClass: function(parent, clazz) {
        var nodes = [];
        for (var i = 0; i < parent.childNodes.length; i++) {
            var child = parent.childNodes[i];
            if (ORBEON.util.Dom.isElement(child) && ORBEON.util.Dom.hasClass(child, clazz)) {
                nodes[nodes.length] = child;
            }
        }
        return nodes.length == 0 ? null : nodes;
    },

    nextSiblingElement: function(element) {
        while (true) {
            var candidate = element.nextSibling;
            if (candidate == null) return null;
            if (ORBEON.util.Dom.isElement(candidate)) return candidate;
        }
    },

    stringToDom: function(xmlString) {
        if (document.implementation.createDocument) {
            return (new DOMParser()).parseFromString(xmlString, "application/xml")
        } else if (window.ActiveXObject) {
            var dom = new ActiveXObject("Microsoft.XMLDOM");
            dom.async = "false";
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
        newInputElement.setAttribute("unselectable", "on");// the server sets this, so we have to set it again
        parentElement.replaceChild(newInputElement, inputElement);
        // For non-W3C compliant browsers we must re-register listeners on the new upload element we just created
        if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
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
    var methodsFrom = ORBEON.xforms.Globals.isRenderingEngineTrident ? ORBEON.util.IEDom : ORBEON.util.MozDom;
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
     * Evaluates JavaScript which can contain return characters we need to remove
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
 * Utility functions dealing with dates and times.
 * 
 * Credits - This is based and inspired by:
 *     Simon Willison's Magic date parser (http://simon.incutio.com/archive/2003/10/06/betterDateInput)
 *     Stoyan Stefanov's Magic time parsing (http://www.phpied.com/javascript-time-input/)
 */
ORBEON.util.DateTime = {

    magicTimeToJSDate: function(magicTime) {
        return ORBEON.util.DateTime._magicToJSDate(magicTime, ORBEON.util.DateTime._timeParsePatterns);
    },

    magicDateToJSDate: function(magicDate) {
        return ORBEON.util.DateTime._magicToJSDate(magicDate, ORBEON.util.DateTime._dateParsePatterns);
    },

    _magicToJSDate: function(magicTimeDate, parsePatterns) {
        for (var i = 0; i < parsePatterns.length; i++) {
            var re = parsePatterns[i].re;
            var handler = parsePatterns[i].handler;
            var bits = re.exec(magicTimeDate);
            if (bits) {
                return handler(bits);
            }
        }
        return null;
    },

    jsDateToISOTime: function(jsDate) {
        return ORBEON.util.DateTime._padAZero(jsDate.getHours())
               + ':'
               + ORBEON.util.DateTime._padAZero(jsDate.getMinutes())
               + ':'
               + ORBEON.util.DateTime._padAZero(jsDate.getSeconds());
    },

    jsDateToISODate: function(jsDate) {
        return jsDate.getFullYear()
               + '-' + ORBEON.util.DateTime._padAZero(jsDate.getMonth() + 1)
               + '-' + ORBEON.util.DateTime._padAZero(jsDate.getDate());
    },

    jsDateToISODateTime: function(jsDateDate, jsDateTime) {
        return ORBEON.util.DateTime.jsDateToISODate(jsDateDate) + "T" + ORBEON.util.DateTime.jsDateToISOTime(jsDateTime);
    },

    jsDateToformatDisplayTime: function(jsDate) {
        if (ORBEON.util.Utils.getProperty(FORMAT_INPUT_TIME_PROPERTY) == "[H]:[m]:[s]") {
            // EU time
            return jsDate.getHours() + ":"
                    + ORBEON.util.DateTime._padAZero(jsDate.getMinutes()) + ":"
                    + ORBEON.util.DateTime._padAZero(jsDate.getSeconds());
        } else {
            // Default: [h]:[m]:[s] [P]
            // US time
            return jsDate.getHours() % 12 + ":"
                    + ORBEON.util.DateTime._padAZero(jsDate.getMinutes()) + ":"
                    + ORBEON.util.DateTime._padAZero(jsDate.getSeconds())
                    + (jsDate.getHours() < 12 ? " a.m." : " p.m.");
        }
    },

    jsDateToformatDisplayDate: function(jsDate) {
        if (ORBEON.util.Utils.getProperty(FORMAT_INPUT_DATE_PROPERTY) == "[D].[M].[Y]") {
            // "Swiss" date
            return jsDate.getDate()
                   + '.' + (jsDate.getMonth() + 1)
                   + '.' + jsDate.getFullYear();
        } else {
            // Default: [M]/[D]/[Y]
            // US date
            return (jsDate.getMonth() + 1)
                   + '/' + jsDate.getDate()
                   + '/' + jsDate.getFullYear();
        }
    },

    /**
     * Array of objects, each has:
     * <ul><li>'re' - a regular expression</li>
     * <li>'handler' - a function for creating a date from something
     *     that matches the regular expression</li>
     * Handlers may throw errors if string is unparseable.
     */
    _timeParsePatterns: [
        // Now
        {   re: /^now/i,
            handler: function() {
                return new Date();
            }
        },
        // p.m.
        {   re: /(\d{1,2}):(\d{1,2}):(\d{1,2})(?:p| p)/,
            handler: function(bits) {
                var d = new Date();
                var h = parseInt(bits[1], 10);
                if (h < 12) {h += 12;}
                d.setHours(h);
                d.setMinutes(parseInt(bits[2], 10));
                d.setSeconds(parseInt(bits[3], 10));
                return d;
            }
        },
        // p.m., no seconds
        {   re: /(\d{1,2}):(\d{1,2})(?:p| p)/,
            handler: function(bits) {
                var d = new Date();
                var h = parseInt(bits[1], 10);
                if (h < 12) {h += 12;}
                d.setHours(h);
                d.setMinutes(parseInt(bits[2], 10));
                d.setSeconds(0);
                return d;
            }
        },
        // p.m., hour only
        {   re: /(\d{1,2})(?:p| p)/,
            handler: function(bits) {
                var d = new Date();
                var h = parseInt(bits[1], 10);
                if (h < 12) {h += 12;}
                d.setHours(h);
                d.setMinutes(0);
                d.setSeconds(0);
                return d;
            }
        },
        // hh:mm:ss
        {   re: /(\d{1,2}):(\d{1,2}):(\d{1,2})/,
            handler: function(bits) {
                var d = new Date();
                d.setHours(parseInt(bits[1], 10));
                d.setMinutes(parseInt(bits[2], 10));
                d.setSeconds(parseInt(bits[3], 10));
                return d;
            }
        },
        // hh:mm
        {   re: /(\d{1,2}):(\d{1,2})/,
            handler: function(bits) {
                var d = new Date();
                d.setHours(parseInt(bits[1], 10));
                d.setMinutes(parseInt(bits[2], 10));
                d.setSeconds(0);
                return d;
            }
        },
        // hhmmss
        {   re: /(\d{1,6})/,
            handler: function(bits) {
                var d = new Date();
                var h = bits[1].substring(0,2);
                var m = parseInt(bits[1].substring(2,4), 10);
                var s = parseInt(bits[1].substring(4,6), 10);
                if (isNaN(m)) {m = 0;}
                if (isNaN(s)) {s = 0;}
                d.setHours(parseInt(h, 10));
                d.setMinutes(parseInt(m, 10));
                d.setSeconds(parseInt(s, 10));
                return d;
            }
        }
    ],

    _dateParsePatterns: [
        // Today
        {   re: /^tod/i,
            handler: function() {
                return new Date();
            }
        },
        // Tomorrow
        {   re: /^tom/i,
            handler: function() {
                var d = new Date();
                d.setDate(d.getDate() + 1);
                return d;
            }
        },
        // Yesterday
        {   re: /^yes/i,
            handler: function() {
                var d = new Date();
                d.setDate(d.getDate() - 1);
                return d;
            }
        },
        // 4th
        {   re: /^(\d{1,2})(st|nd|rd|th)?$/i,
            handler: function(bits) {
                var d = new Date();
                d.setDate(parseInt(bits[1], 10));
                return d;
            }
        },
        // 4th Jan
        {   re: /^(\d{1,2})(?:st|nd|rd|th)? (\w+)$/i,
            handler: function(bits) {
                var d = new Date();
                d.setMonth(ORBEON.util.DateTime._parseMonth(bits[2]));
                d.setDate(parseInt(bits[1], 10));
                return d;
            }
        },
        // 4th Jan 2003
        {   re: /^(\d{1,2})(?:st|nd|rd|th)? (\w+),? (\d{2,4})$/i,
            handler: function(bits) {
                var d = new Date();
                d.setYear(bits[3]);
                d.setMonth(ORBEON.util.DateTime._parseMonth(bits[2]));
                d.setDate(parseInt(bits[1], 10));
                return d;
            }
        },
        // Jan 4th
        {   re: /^(\w+) (\d{1,2})(?:st|nd|rd|th)?$/i,
            handler: function(bits) {
                var d = new Date();
                d.setMonth(ORBEON.util.DateTime._parseMonth(bits[1]));
                d.setDate(parseInt(bits[2], 10));
                return d;
            }
        },
        // Jan 4th 2003
        {   re: /^(\w+) (\d{1,2})(?:st|nd|rd|th)?,? (\d{2,4})$/i,
            handler: function(bits) {
                var d = new Date();
                d.setDate(parseInt(bits[2], 10));
                d.setMonth(ORBEON.util.DateTime._parseMonth(bits[1]));
                d.setYear(bits[3]);
                return d;
            }
        },
        // next Tuesday - this is suspect due to weird meaning of "next"
        {   re: /^next (\w+)$/i,
            handler: function(bits) {
                var d = new Date();
                var day = d.getDay();
                var newDay = ORBEON.util.DateTime._parseWeekday(bits[1]);
                var addDays = newDay - day;
                if (newDay <= day) {
                    addDays += 7;
                }
                d.setDate(d.getDate() + addDays);
                return d;
            }
        },
        // last Tuesday
        {   re: /^last (\w+)$/i,
            handler: function(bits) {
                throw new Error("Not yet implemented");
            }
        },
        // mm/dd/yyyy (American style)
        {   re: /^(\d{1,2})\/(\d{1,2})\/(\d{2,4})$/,
            handler: function(bits) {
                var d = new Date();
                d.setYear(bits[3]);
                d.setMonth(parseInt(bits[1], 10) - 1); // Because months indexed from 0
                d.setDate(parseInt(bits[2], 10));
                return d;
            }
        },
        // mm/dd (American style without year)
        {   re: /^(\d{1,2})\/(\d{1,2})$/,
            handler: function(bits) {
                var d = new Date();
                d.setDate(parseInt(bits[2], 10));
                d.setMonth(parseInt(bits[1], 10) - 1); // Because months indexed from 0
                return d;
            }
        },
        // dd.mm.yyyy (Swiss style)
        {   re: /^(\d{1,2})\.(\d{1,2})\.(\d{2,4})$/,
            handler: function(bits) {
                var d = new Date();
                d.setYear(bits[3]);
                d.setMonth(parseInt(bits[2], 10) - 1); // Because months indexed from 0
                d.setDate(parseInt(bits[1], 10));
                return d;
            }
        },
        // yyyy-mm-dd (ISO style)
        {   re: /(\d{2,4})-(\d{1,2})-(\d{1,2})/,
            handler: function(bits) {
                var d = new Date();
                d.setYear(parseInt(bits[1]));
                d.setMonth(parseInt(bits[2], 10) - 1);
                d.setDate(parseInt(bits[3], 10));
                return d;
            }
        }
    ],

    /**
     * Helper function to pad a leading zero to an integer
     * if the integer consists of one number only.
     * This function s not related to the algo, it's for
     * getReadable()'s purposes only.
     *
     * @param int s An integer value
     * @return string The input padded with a zero if it's one number int
     * @see getReadable()
     */
    _padAZero: function(s) {
        s = s.toString();
        if (s.length == 1) {
            return '0' + s;
        } else {
            return s;
        }
    },

    _monthNames: "January February March April May June July August September October November December".split(" "),
    _weekdayNames: "Sunday Monday Tuesday Wednesday Thursday Friday Saturday".split(" "),

    /**
     *  Takes a string, returns the index of the month matching that string, throws
     *  an error if 0 or more than 1 matches
     */
    _parseMonth: function(month) {
        var matches = ORBEON.util.DateTime._monthNames.filter(function(item) {
            return new RegExp("^" + month, "i").test(item);
        });
        if (matches.length == 0) {
            throw new Error("Invalid month string");
        }
        if (matches.length > 1) {
            throw new Error("Ambiguous month");
        }
        return ORBEON.util.DateTime._monthNames.indexOf(matches[0]);
    },

    /* Same as parseMonth but for days of the week */
    _parseWeekday: function(weekday) {
        var matches = ORBEON.util.DateTime._weekdayNames.filter(function(item) {
            return new RegExp("^" + weekday, "i").test(item);
        });
        if (matches.length == 0) {
            throw new Error("Invalid day string");
        }
        if (matches.length > 1) {
            throw new Error("Ambiguous weekday");
        }
        return ORBEON.util.DateTime._weekdayNames.indexOf(matches[0]);
    }
}

/**
 * Utility methods that don't in any other category
 */
ORBEON.util.Utils = {
    logMessage: function(message) {
        if (typeof console != "undefined") {
            console.log(message); // Normal use; do not remove
        }
    },

    /**
     * A convenience method for getting values of property supplied as inline script.
     * If the property value is not supplied, then the default value will be returned.
     */

    getProperty: function(propertyName) {
        // Check if the value for the property was supplied in inline script
        if (typeof opsXFormsProperties != "undefined" && typeof opsXFormsProperties[propertyName] != "undefined")
            return opsXFormsProperties[propertyName];

        // Return default value for the property
        switch (propertyName) {
            case SESSION_HEARTBEAT_PROPERTY: { return XFORMS_SESSION_HEARTBEAT; }
            case SESSION_HEARTBEAT_DELAY_PROPERTY: { return XFORMS_SESSION_HEARTBEAT_DELAY;  }
            case REVISIT_HANDLING_PROPERTY: { return XFORMS_REVISIT_HANDLING; }
            case FCK_EDITOR_BASE_PATH_PROPERTY: { return FCK_EDITOR_BASE_PATH; }
            case DELAY_BEFORE_INCREMENTAL_REQUEST_PROPERTY: { return XFORMS_DELAY_BEFORE_INCREMENTAL_REQUEST_IN_MS; }
            case DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_PROPERTY: { return XFORMS_DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_IN_MS; }
            case DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_PROPERTY: { return XFORMS_DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_IN_MS; }
            case DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_PROPERTY: { return XFORMS_DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_IN_MS; }
            case DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY: { return XFORMS_DELAY_BEFORE_AJAX_TIMEOUT_IN_MS; }
            case INTERNAL_SHORT_DELAY_PROPERTY: { return XFORMS_INTERNAL_SHORT_DELAY_IN_MS; }
            case DELAY_BEFORE_DISPLAY_LOADING_PROPERTY: { return XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS; }
            case REQUEST_RETRIES_PROPERTY: { return XFORMS_REQUEST_RETRIES; }
            case DEBUG_WINDOW_HEIGHT_PROPERTY: { return XFORMS_DEBUG_WINDOW_HEIGHT; }
            case DEBUG_WINDOW_WIDTH_PROPERTY: { return XFORMS_DEBUG_WINDOW_WIDTH; }
            case LOADING_MIN_TOP_PADDING_PROPERTY: { return XFORMS_LOADING_MIN_TOP_PADDING; }
            case HELP_HANDLER_PROPERTY: { return XFORMS_HELP_HANDLER; }
            case HELP_TOOLTIP_PROPERTY: { return XFORMS_HELP_TOOLTIP; }
            case OFFLINE_SUPPORT_PROPERTY: { return XFORMS_OFFLINE_SUPPORT; }
            case FORMAT_INPUT_TIME_PROPERTY: { return XFORMS_FORMAT_INPUT_TIME; }
            case FORMAT_INPUT_DATE_PROPERTY: { return XFORMS_FORMAT_INPUT_DATE; }
            case DATE_PICKER_PROPERTY: { return XFORMS_DATEPICKER; }
            }
    	// Neither the property's value was supplied, nor a default value exists for the property
        return null;
    },

    hideModalProgressPanel:function() {
        if (ORBEON.xforms.Globals.modalProgressPanel) {
            ORBEON.xforms.Globals.modalProgressPanel.hide();
        }
    },

    displayModalProgressPanel:function() {
        if (!ORBEON.xforms.Globals.modalProgressPanel) {
            ORBEON.xforms.Globals.modalProgressPanel =
            new YAHOO.widget.Panel("wait", {
                width:"60px",
                fixedcenter:true,
                close:false,
                draggable:false,
                zindex:4,
                modal:true,
                visible:true
            });
            ORBEON.xforms.Globals.modalProgressPanel.setBody('<img src="' + ORBEON.xforms.Globals.baseURL + '/ops/images/xforms/processing.gif"/>');
            ORBEON.xforms.Globals.modalProgressPanel.render(document.body);
        }
        ORBEON.xforms.Globals.modalProgressPanel.show();
    },

    countOccurences: function(str, character) {
        var count = 0;
        var pos = str.indexOf(character);
        while ( pos != -1 ) {
            count++;
            pos = str.indexOf(character,pos+1);
        }
            return count;
    }
}

/**
 * This object contains function designed to be called from JavaScript code
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
        // If we are offline, directly change the value of the control
        if (!ORBEON.xforms.Offline.isOnline)
            ORBEON.xforms.Controls.setCurrentValue(control, newValue);
        xformsFireEvents(new Array(xformsCreateEventArray
                (control, "xxforms-value-change-with-focus-change", String(newValue), null)), false);
    },

    /**
     * Returns whether the document is being reloaded.
     */
    isReloading: function() {
        return ORBEON.xforms.Globals.isReloading;
    },

    /**
     * Exposes to JavaScript the current index of all the repeats. This is the JavaScript equivalent to the
     * XForms XPath function index(repeatId).
     */
    getRepeatIndex: function(repeatId) {
        return ORBEON.xforms.Globals.repeatIndexes[repeatId];
    },

    setOfflineEncryptionPassword: function(password) {
        ORBEON.xforms.Offline.init();

        // Create hash of constant length based on password
        // This algorithm comes from JavaScrypt (http://www.fourmilab.ch/javascrypt/)
        password = encode_utf8(password);
        if (password.length == 1)
            password += password;
        md5_init();
        for (var i = 0; password < password.length; i += 2)
            md5_update(password.charCodeAt(i));
        md5_finish();
        var kmd5e = byteArrayToHex(digestBits);
        md5_init();
        for (i = 1; i < password.length; i += 2)
            md5_update(password.charCodeAt(i));
        md5_finish();
        var kmd5o = byteArrayToHex(digestBits);
        password = kmd5e + kmd5o;
        password = password.substring(32);

        // Check or store password
        var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select * from Current_Password");
        if (resultSet.isValidRow()) {
            // If we have a current password, check that this is the right password
            var currentPassword = resultSet.fieldByName("encrypted_password");
            if (currentPassword != password)
                throw "Invalid password";
        } else {
            // Store encrypted password
            ORBEON.xforms.Offline.gearsDatabase.execute("insert into Current_Password (encrypted_password) values (?)", [ password ]);
        }
        document.cookie = "orbeon.forms.encryption.password=" + password + "; path=/; secure";
        // Reset key, in case we already had another key previously
        ORBEON.xforms.Offline.encryptionKey = null;
    },

    changeOfflineEncryptionPassword: function(currentPassword, newPassword) {
        ORBEON.xforms.Offline.init();
        // Get old key
        ORBEON.xforms.Document.setOfflineEncryptionPassword(currentPassword);
        var oldKey = ORBEON.xforms.Offline.getEncryptionKey();
        // Scrap encrypted password in database
        ORBEON.xforms.Offline.gearsDatabase.execute("delete from Current_Password");
        // Get new key
        ORBEON.xforms.Document.setOfflineEncryptionPassword(newPassword);
        var newKey = ORBEON.xforms.Offline.getEncryptionKey();

        // Go over events in SQL database and reencrypt them
        var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select url, event_response, offline_events from Offline_Forms");
        while (resultSet.isValidRow()) {
            var url = resultSet.fieldByName("url");
            var eventResponse = resultSet.fieldByName("event_response");
            var offlineEvents = resultSet.fieldByName("offline_events");
            // Decrypt and reencrypt
            eventResponse = ORBEON.xforms.Offline._decrypt(eventResponse, oldKey);
            eventResponse = ORBEON.xforms.Offline._encrypt(eventResponse, newKey);
            offlineEvents = ORBEON.xforms.Offline._decrypt(offlineEvents, oldKey);
            offlineEvents = ORBEON.xforms.Offline._encrypt(offlineEvents, newKey);
            // Update entries for this URL
            ORBEON.xforms.Offline.gearsDatabase.execute("update Offline_Forms set event_response = ?, offline_events = ? where url= ?",
                    [eventResponse, offlineEvents, url]).close();
            resultSet.next();
        }
        resultSet.close();
    },

    /**
     * Is it possible to take forms offline. This will require Gears to be installed. If Gears is not installed, it
     * is the responsability of the application (not the XForms engine) to detect if Gears can be installed and to
     * guide the user through the installation of Gears.
     */
    isOfflineAvailable: function() {
        ORBEON.xforms.Offline.init();
        return ORBEON.xforms.Offline.hasGears;
    },

    /**
     * Returns true if a form identified by URL has been taken offline. This should only be called if the offline
     * mode is available.
     */
    isFormOffline: function(url) {
        ORBEON.xforms.Offline.init();
        var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select * from Offline_Forms where url = ?", [ url ]);
        var result = resultSet.isValidRow();
        resultSet.close();
        return result;
    },

    /**
     * Returns the current value of controls for the specified form.
     *
     */
    getOfflineControlValues: function(url) {
        var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select control_values from Offline_Forms where url = ?", [ url ]);
        if (! resultSet.isValidRow()) return null;
        var controlValues = resultSet.fieldByName("control_values");
        controlValues = ORBEON.xforms.Offline._deserializerControlValues(controlValues);
        return controlValues;
    },

    /**
     * Function that be called from a summary page to take a form offline.
     * @param url                   The URL of the form to take offline
     * @param formOfflineListener   An optional function which will be called when the form has been taken offline.
     *                              This function receives window in which the form is loaded as parameter.
     */
    takeOfflineFromSummary: function(url, formOfflineListener) {
        ORBEON.xforms.Offline.init();
        var formLoadingComplete = false;
        // Check at a regular interval if the flag is set, when it is load the form in the frame.
        var formLoadingIntervalID = window.setInterval(function() {
            if (formLoadingComplete) {
                window.clearInterval(formLoadingIntervalID);
                // Load the form in the iframe
                ORBEON.xforms.Offline.loadFormInIframe(url, function(offlineIframe) {
                    // Wait for the form to be marked as offline before we call the listener
                    var takingFormOfflineIntervalID = window.setInterval(function() {
                        if (! offlineIframe.contentWindow.ORBEON.xforms.Offline.isOnline) {
                            window.clearInterval(takingFormOfflineIntervalID);
                            // Calling listener to notify that the form is now completely offline
                            if (formOfflineListener)
                                formOfflineListener(offlineIframe.contentWindow);
                        }
                    }, 100);
                    // Send offline event to the server
                    offlineIframe.contentWindow.ORBEON.xforms.Document.dispatchEvent("$containing-document$", "xxforms-offline");
                });
            }
        }, 100)
        // We first capture the form
        ORBEON.xforms.Offline.formStore.capture(url, function (url, success, captureId) {
            // When capture is done, set a flag.
            // We need to resort to this trick because the code here does not run the same context and setting src
            // attribute on the iframe would otherwise fail.
            formLoadingComplete = true;
        });
    },

    /**
     * Function that be called from a summary page to take a form online.
     * @param url                   The URL of the form to take online
     * @param formOnlineListener    An optional function which will be called when the form has been taken online.
     *                              This function receives window in which the form is loaded as parameter.
     */
    takeOnlineFromSummary: function(url, formOnlineListener) {
        ORBEON.xforms.Offline.init();
        ORBEON.xforms.Offline.loadFormInIframe(url, function(offlineIframe) {
            // Calling listener to notify that the form is now completely online
            if (formOnlineListener) {
                var waitingForFormOnlineIntervalID = window.setInterval(function() {
                    if (! ORBEON.xforms.Document.isFormOffline(url)) {
                        window.clearInterval(waitingForFormOnlineIntervalID);
                        formOnlineListener(offlineIframe.contentWindow);
                    }
                }, 100);
            }
            offlineIframe.contentWindow.ORBEON.xforms.Offline.takeOnline();
        });
    }
};

ORBEON.xforms.Controls = {

    // Returns MIP for a given control
    isRelevant: function (control) {
        return !ORBEON.util.Dom.hasClass(control, "xforms-disabled");
    },
    isReadonly: function (control) {
        return  ORBEON.util.Dom.hasClass(control, "xforms-readonly");
    },
    isRequired: function (control) {
        return  ORBEON.util.Dom.hasClass(control, "xforms-required");
    },
    isValid:    function (control) {
        return !ORBEON.util.Dom.hasClass(control, "xforms-invalid");
    },

    getForm: function(control) {
        // If the control is not an HTML form control look for an ancestor whch is a form
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
        if (ORBEON.util.Dom.hasClass(control, "xforms-type-time")) {
            var inputValue = ORBEON.util.Dom.getChildElementByIndex(control, 0).value;
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate(inputValue);
            return jsDate == null ? inputValue : ORBEON.util.DateTime.jsDateToISOTime(jsDate);
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-type-date")) {
            var inputValue = ORBEON.util.Dom.getChildElementByIndex(control, 0).value;
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate(inputValue);
            return jsDate == null ? inputValue : ORBEON.util.DateTime.jsDateToISODate(jsDate);
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime")) {
            var dateValue = ORBEON.util.Dom.getChildElementByIndex(control, 0).value;
            var jsDateDate = ORBEON.util.DateTime.magicDateToJSDate(dateValue);
            var timeValue = ORBEON.util.Dom.getChildElementByIndex(control, 1).value;
            var jsDateTime = ORBEON.util.DateTime.magicTimeToJSDate(timeValue);
            if (jsDateDate == null || jsDateTime == null) {
                return dateValue == "" && timeValue == "" ? "" : dateValue + "T" + timeValue;
            } else {
                return ORBEON.util.DateTime.jsDateToISODateTime(jsDateDate, jsDateTime);
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-input") && !ORBEON.util.Dom.hasClass(control, "xforms-type-boolean") && !ORBEON.util.Dom.hasClass(control, "xforms-static")) {
            return ORBEON.util.Dom.getChildElementByIndex(control, 0).value;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select1-open")) {
            return ORBEON.util.Dom.getChildElementByIndex(control, 0).value;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || (ORBEON.util.Dom.hasClass(control, "xforms-input") && ORBEON.util.Dom.hasClass(control, "xforms-type-boolean"))) {
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
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-output") || (ORBEON.util.Dom.hasClass(control, "xforms-input") && ORBEON.util.Dom.hasClass(control, "xforms-static"))) {
            if (ORBEON.util.Dom.hasClass(control, "xforms-mediatype-image")) {
                var image = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                return image.src;
            } else if (ORBEON.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
                return control.innerHTML;
            } else {
                return ORBEON.util.Dom.getStringValue(control);
            }
        } else {
            return control.value;
        }
    },

    /**
     * Updates the value of a control in the UI.
     *
     * @param control           HTML element for the control we want to update
     * @param newControlValue   New value
     * @param displayValue      Optional display value when different from newControlValue
     * @param uploadState       Optional
     * @param uploadFilename    Optional
     * @param uploadMediatype   Optional
     * @param uploadSize        Optional
     */
    setCurrentValue: function(control, newControlValue, displayValue, previousServerValue, uploadState, uploadFilename, uploadMediatype, uploadSize) {
        var isStaticReadonly = ORBEON.util.Dom.hasClass(control, "xforms-static");
        if (ORBEON.util.Dom.hasClass(control, "xforms-output") || isStaticReadonly) {
            // XForms output or "static readonly" mode
            var newOutputControlValue = displayValue != null ? displayValue : newControlValue;
            if (ORBEON.util.Dom.hasClass(control, "xforms-mediatype-image")) {
                var image = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                image.src = newOutputControlValue;
            } else if (ORBEON.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
                control.innerHTML = newOutputControlValue;
            } else {
                ORBEON.util.Dom.setStringValue(control, newOutputControlValue);
            }
        } else if (ORBEON.xforms.Globals.changedIdsRequest[control.id] != null) {
            // User has modified the value of this control since we sent our request:
            // so don't try to update it
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-trigger")
                || ORBEON.util.Dom.hasClass(control, "xforms-submit")) {
            // Triggers don't have a value: don't update them
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select1-open")) {
            // Auto-complete
            if (control.value != newControlValue) {
                control.value = newControlValue;
                ORBEON.util.Dom.getChildElementByIndex(control, 0).value = newControlValue;
                control.previousValue = newControlValue;
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || (ORBEON.util.Dom.hasClass(control, "xforms-input") && ORBEON.util.Dom.hasClass(control, "xforms-type-boolean"))) {
            // Handle checkboxes and radio buttons
            var selectedValues = ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")
                    ? newControlValue.split(" ") : new Array(newControlValue);
            var checkboxInputs = control.getElementsByTagName("input");
            for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                var checkboxInput = checkboxInputs[checkboxInputIndex];
                checkboxInput.checked = xformsArrayContains(selectedValues, checkboxInput.value);
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-compact")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-compact")
                || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-minimal")
                || ORBEON.util.Dom.hasClass(control, "xforms-input-appearance-compact")
                || ORBEON.util.Dom.hasClass(control, "xforms-input-appearance-minimal")) {
            // Handle lists and comboboxes
            var selectedValues = ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-compact")
                    ? newControlValue.split(" ") : new Array(newControlValue);
            var options = control.options;
            if (options != null) {
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
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-type-time")) {
            var inputField = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate(newControlValue);
            inputField.value = jsDate == null ? newControlValue : ORBEON.util.DateTime.jsDateToformatDisplayTime(jsDate);
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-type-date")) {
            var inputField = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate(newControlValue);
            inputField.value = jsDate == null ? newControlValue : ORBEON.util.DateTime.jsDateToformatDisplayDate(jsDate);
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime")) {
            // Only update value if different from the one we have. This handle the case where the fields contain invalid 
            // values with the T letter in them. E.g. aTb/cTd, aTbTcTd sent to server, which we don't know anymore how
            // to separate into 2 values.
            if (ORBEON.xforms.Controls.getCurrentValue(control) != newControlValue) {
                var separatorIndex = newControlValue.indexOf("T");
                // Populate date field
                var datePartString = newControlValue.substring(0, separatorIndex);
                var datePartJSDate = ORBEON.util.DateTime.magicDateToJSDate(datePartString);
                var inputFieldDate = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                inputFieldDate.value = datePartJSDate == null ? datePartString : ORBEON.util.DateTime.jsDateToformatDisplayDate(datePartJSDate);
                // Populate time field
                var timePartString = newControlValue.substring(separatorIndex + 1);
                var timePartJSDate = ORBEON.util.DateTime.magicTimeToJSDate(timePartString);
                var inputFieldTime = ORBEON.util.Dom.getChildElementByIndex(control, 1);
                inputFieldTime.value = timePartJSDate == null ? timePartString : ORBEON.util.DateTime.jsDateToformatDisplayTime(timePartJSDate);
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-input") && !ORBEON.util.Dom.hasClass(control, "xforms-type-boolean")) {
            // XForms input
            var inputField = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            if (control.value != newControlValue) {
                control.previousValue = newControlValue;
                control.valueSetByXForms++;
                control.value = newControlValue;
            }
            if (inputField.value != newControlValue)
                inputField.value = newControlValue;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-textarea")
                && ORBEON.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // HTML area
            var htmlEditor = FCKeditorAPI.GetInstance(control.name);
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
                ORBEON.xforms.Globals.serverValue[control.id] = htmlEditor.GetXHTML();
                control.value = newControlValue;
                control.previousValue = newControlValue;
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-xxforms-tree")) {
            // Select tree
            var values = newControlValue.split(" ");
            var yuiTree = ORBEON.xforms.Globals.treeYui[control.id];
            for (var nodeIndex in yuiTree._nodes) {
                var node = yuiTree._nodes[nodeIndex];
                if (node.children.length == 0) {
                    var checked = xformsArrayContains(values, node.data.value);
                    if (checked) node.check(); else node.uncheck();
                }
            }
            control.value = newControlValue;
            control.previousValue = newControlValue;

        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-xxforms-tree")) {
            // Select1 tree
            // Make sure the tree is open enough so the node with the new value is visible
            var yuiTree = ORBEON.xforms.Globals.treeYui[control.id];
            ORBEON.xforms.Controls.treeOpenSelectedVisible(yuiTree, [newControlValue]);
                    // Deselect old value, select new value
            var oldNode = yuiTree.getNodeByProperty("value", control.value);
            var newNode = yuiTree.getNodeByProperty("value", newControlValue);
            if (oldNode != null)
                YAHOO.util.Dom.removeClass(oldNode.getLabelEl(), "xforms-tree-label-selected");
            if (newNode != null)
                YAHOO.util.Dom.addClass(newNode.getLabelEl(), "xforms-tree-label-selected");
                    // Update value
            control.value = newControlValue;
            control.previousValue = newControlValue;
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-upload")) {
            // Upload

            // Get elements we want to modify from the DOM
            var fileInfoSpan = ORBEON.util.Dom.getChildElementByClass(control, "xforms-upload-info");
            var fileNameSpan = ORBEON.util.Dom.getChildElementByClass(fileInfoSpan, "xforms-upload-filename");
            var mediatypeSpan = ORBEON.util.Dom.getChildElementByClass(fileInfoSpan, "xforms-upload-mediatype");
            var sizeSpan = ORBEON.util.Dom.getChildElementByClass(fileInfoSpan, "xforms-upload-size");
            // Set values in DOM
            if (uploadState == "empty") {
                ORBEON.util.Dom.removeClass(control, "xforms-upload-state-file")
                ORBEON.util.Dom.addClass(control, "xforms-upload-state-empty")
            }
            if (uploadState == "file") {
                ORBEON.util.Dom.removeClass(control, "xforms-upload-state-empty")
                ORBEON.util.Dom.addClass(control, "xforms-upload-state-file")

                // Clear upload input by replacing the control
                ORBEON.util.Dom.clearUploadControl(control);
            }
            if (uploadFilename != null)
                ORBEON.util.Dom.setStringValue(fileNameSpan, uploadFilename);
            if (uploadMediatype != null)
                ORBEON.util.Dom.setStringValue(mediatypeSpan, uploadMediatype);
            if (uploadSize != null) {
                var displaySize = uploadSize > 1024 * 1024 ? Math.round(uploadSize / (1024 * 1024) * 10) / 10 + " MB"
                        : uploadSize > 1024 ? Math.round(uploadSize / 1024 * 10) / 10 + " KB"
                        : uploadSize + " B";
                ORBEON.util.Dom.setStringValue(sizeSpan, displaySize);
            }
        } else if (typeof(control.value) == "string") {
            // Textarea, password
            if (xformsNormalizeEndlines(control.value) != xformsNormalizeEndlines(newControlValue)) {
                control.value = newControlValue;
                control.previousValue = newControlValue;
                // Autosize textarea
                if (ORBEON.util.Dom.hasClass(control, "xforms-textarea-appearance-xxforms-autosize")) {
                    ORBEON.xforms.Controls.autosizeTextarea(control);
                }
            }
        }
    },

    /**
     * Look for an HTML label corresponding to an XForms label, help, hint, and alert.
     * In the HTML generated by the server there is 1 element for each one and 2 for the help.
     * Because there can be a text node in between, we are looking 10 nodes before and 10
     * nodes after the control.
     */
    _getControlLabel: function(control, className) {

        // Checks if the node is the one we are looking for
        function isFound(candidate) {
            return ORBEON.util.Dom.isElement(candidate)
                    && ORBEON.util.Dom.hasClass(candidate, className)
                    // If element has a "for" attribute, make sure it is for the right control
                    && (candidate.htmlFor == null || candidate.htmlFor == control.id)
                    // If candidate is the help image, make sure the following sibling (the help label) is for the right control
                    && (className != "xforms-help-image" ||
                            ORBEON.util.Dom.nextSiblingElement(candidate).htmlFor == control.id);
        }

        // Search for label in the 10 node that follow the control
        var candidate = control.nextSibling;
        for (var position = 0; position < 10; position++) {
            if (candidate == null) break;
            if (isFound(candidate)) return candidate;
            candidate = candidate.nextSibling;
        }

        // Search for label in the 10 node that preceed the control
        var candidate = control.previousSibling;
        for (var position = 0; position < 10; position++) {
            if (candidate == null) break;
            if (isFound(candidate)) return candidate;
            candidate = candidate.previousSibling;
        }

        return null;
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
        message = ORBEON.util.String.escapeHTMLMinimal(message);
        ORBEON.xforms.Controls._setMessage(control, "xforms-help", message);
        ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.helpTooltipForControl);
    },

    setValid: function(control, newValid, isRequiredEmpty) {
        // Update class xforms-invalid on the control
        var isValid;
        var isVisited = ORBEON.util.Dom.hasClass(control, "xforms-visited");
        if (newValid != null) {
            isValid = newValid != "false";
            if (isValid) {
                ORBEON.util.Dom.removeClass(control, "xforms-invalid");
                if (isVisited) ORBEON.util.Dom.removeClass(control, "xforms-invalid-visited");
            } else {
                ORBEON.util.Dom.addClass(control, "xforms-invalid");
                if (isVisited) ORBEON.util.Dom.addClass(control, "xforms-invalid-visited");
            }
        } else {
            isValid = ORBEON.xforms.Controls.isValid(control);
        }

        // Update class on alert label
        var alertElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert");
        if (alertElement != null) { // Some controls don't have validity indicator
            if (isValid && !isRequiredEmpty) {
                ORBEON.util.Dom.removeClass(alertElement, "xforms-alert-active");
                if (isVisited) ORBEON.util.Dom.removeClass(alertElement, "xforms-alert-active-visited");
                ORBEON.util.Dom.addClass(alertElement, "xforms-alert-inactive");
            } else {
                ORBEON.util.Dom.removeClass(alertElement, "xforms-alert-inactive");
                ORBEON.util.Dom.addClass(alertElement, "xforms-alert-active");
                if (isVisited) ORBEON.util.Dom.addClass(alertElement, "xforms-alert-active-visited");
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

    setRepeatIterationRelevance: function(repeatID, iteration, relevant) {
        var cursor = xformsFindRepeatDelimiter(repeatID, iteration).nextSibling;
        while (!(cursor.nodeType == ELEMENT_TYPE &&
                 (ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                         || ORBEON.util.Dom.hasClass(cursor, "xforms-repeat-begin-end")))) {
            if (cursor.nodeType == ELEMENT_TYPE) {
                if (relevant) ORBEON.util.Dom.removeClass(cursor, "xforms-disabled");
                else ORBEON.util.Dom.addClass(cursor, "xforms-disabled");
            }
            cursor = cursor.nextSibling;
        }
    },

    setReadonly: function(control, isReadonly) {
        function setReadonlyOnFormElement(element, isReadonly) {
            if (isReadonly) {
                element.setAttribute("disabled", "disabled");
                ORBEON.util.Dom.addClass(element, "xforms-readonly");
            } else {
                element.removeAttribute("disabled");
                ORBEON.util.Dom.removeClass(element, "xforms-readonly");
            }
        }
        if (ORBEON.util.Dom.hasClass(control, "xforms-input") && !ORBEON.util.Dom.hasClass(control, "xforms-type-boolean")) {
            // Add/remove xforms-readonly on span
            if (isReadonly) ORBEON.util.Dom.addClass(control, "xforms-readonly");
            else ORBEON.util.Dom.removeClass(control, "xforms-readonly");
            // XForms input: set/remove disabled attribute on first input
            var firstInput = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            if (isReadonly) firstInput.setAttribute("disabled", "disabled");
            else firstInput.removeAttribute("disabled");
            // XForms input for date-time: set/remove disabled attribute on first input
            if (ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime")) {
                var secondInput = ORBEON.util.Dom.getChildElementByIndex(control, 1);
                if (isReadonly) secondInput.setAttribute("disabled", "disabled");
                else secondInput.removeAttribute("disabled");
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-output")
                || ORBEON.util.Dom.hasClass(control, "xforms-group")) {
            // XForms output and group
            if (isReadonly) ORBEON.util.Dom.addClass(control, "xforms-readonly");
            else ORBEON.util.Dom.removeClass(control, "xforms-readonly");
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || (ORBEON.util.Dom.hasClass(control, "xforms-input") && ORBEON.util.Dom.hasClass(control, "xforms-type-boolean"))) {
            // XForms radio buttons
            for (var spanIndex = 0; spanIndex < control.childNodes.length; spanIndex++) {
                var span = control.childNodes[spanIndex];
                var input = span.firstChild;
                setReadonlyOnFormElement(input, isReadonly);
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-xxforms-autocomplete")) {
            // Auto-complete field
            var input = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            setReadonlyOnFormElement(input, isReadonly);
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-textarea") && ORBEON.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // XForms HTML area
            var htmlEditor = FCKeditorAPI.GetInstance(control.name);
            if (isReadonly) {
                htmlEditor.ToolbarSet.Collapse();
                    // TO-DO
            } else {
                htmlEditor.ToolbarSet.Expand();
                    // TO-DO
            }
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-upload")) {
            // Upload control
            setReadonlyOnFormElement(
                    ORBEON.util.Dom.getChildElementByClass(control, "xforms-upload-select"), isReadonly);
        } else {
            // Other controls
            setReadonlyOnFormElement(control, isReadonly);
        }
    },

    getAlertMessage: function(control) {
        var alertElement = ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert");
        return alertElement.innerHTML;
    },

    setAlertMessage: function(control, message) {
        ORBEON.xforms.Controls._setMessage(control, "xforms-alert", message);
        ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.alertTooltipForControl);
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
            ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.hintTooltipForControl);
        }
    },

    _setTooltipMessage: function(control, message, tooltipForControl) {
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
            ORBEON.util.Dom.getChildElementByIndex(control, 0).focus();
        } else if (ORBEON.util.Dom.hasClass(control, "xforms-select-appearance-full") || ORBEON.util.Dom.hasClass(control, "xforms-select1-appearance-full")) {
            // Find for radio button or check box that is is checked
            var itemIndex = 0;
            var foundSelected = false;
            while (true) {
                var item = ORBEON.util.Dom.getChildElementByIndex(control, itemIndex);
                if (item == null) break;
                var formInput = ORBEON.util.Dom.getChildElementByIndex(item, 0);
                if (formInput.checked) {
                    foundSelected = true;
                    break;
                }
                itemIndex++;
            }
            // Set focus on either selected item if we found one or on first item otherwise
            ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(control, foundSelected ? itemIndex : 0), 0).focus();
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
    updateRequiredEmpty: function(control, newValue) {
        if (ORBEON.util.Dom.hasClass(control, "xforms-required")) {
            if (newValue == "") {
                ORBEON.util.Dom.addClass(control, "xforms-required-empty");
                ORBEON.util.Dom.removeClass(control, "xforms-required-filled");
                return true;
            } else {
                ORBEON.util.Dom.addClass(control, "xforms-required-filled");
                ORBEON.util.Dom.removeClass(control, "xforms-required-empty");
                return false;
            }
        } else {
            ORBEON.util.Dom.removeClass(control, "xforms-required-filled");
            ORBEON.util.Dom.removeClass(control, "xforms-required-empty");
            return false;
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
        var x;
        {
            var initialRight = ORBEON.xforms.Globals.formLoadingLoadingInitialRightTop[formID][0];
            var scrollX = document.documentElement.scrollLeft || document.body.scrollLeft;
            x = scrollX + YAHOO.util.Dom.getViewportWidth() - initialRight;
        }
        // Compute new Y
        var y;
        {
            // Distance between top of viewport and top of the page. Initially 0 when we are at the top of the page.
            var scrollY = document.documentElement.scrollTop || document.body.scrollTop;
            var initialTop = ORBEON.xforms.Globals.formLoadingLoadingInitialRightTop[formID][1];
            y = scrollY + ORBEON.util.Utils.getProperty(LOADING_MIN_TOP_PADDING_PROPERTY) > initialTop
                // Place indicator at a few pixels from the top of the viewport
                    ? scrollY + ORBEON.util.Utils.getProperty(LOADING_MIN_TOP_PADDING_PROPERTY)
                // Loading is visible left at its initial position, so leave it there
                    : initialTop;
        }
        // Position overlay
        var overlay = ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID];
        overlay.cfg.setProperty("x", x);
        overlay.cfg.setProperty("y", y);
    },

    treeOpenSelectedVisible: function(yuiTree, values) {
        for (var nodeIndex in yuiTree._nodes) {
            var node = yuiTree._nodes[nodeIndex];
            if (xformsArrayContains(values, node.data.value)) {
                var nodeParent = node.parent;
                while (nodeParent != null) {
                    nodeParent.expand();
                    nodeParent = nodeParent.parent;
                }
            }
        }

    },

    showHelp: function(control) {
        // Create help dialog if this hasn't been done already
        var form = ORBEON.xforms.Controls.getForm(control);
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
                        constraintoviewport: true,
                        draggable: true,
                        effect: {effect:YAHOO.widget.ContainerEffect.FADE,duration: 0.3}
                    });
                    helpPanel.render();
                    helpPanel.element.style.display = "none";
                    ORBEON.xforms.Globals.formHelpPanel[form.id] = helpPanel;

                    // Find div for help body
                    var bodyDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "bd");

                    var messageDiv = YAHOO.util.Dom.getElementsByClassName("xforms-help-panel-message", null, bodyDiv)[0];
                    ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id] = messageDiv;

                    // Get the close button and register listener on that button
                    var closeDiv = YAHOO.util.Dom.getElementsByClassName("xforms-help-panel-close", null, bodyDiv)[0];
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
        ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id].innerHTML = ORBEON.xforms.Controls.getHelpMessage(control);
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
            var helpImage = ORBEON.util.Dom.getChildElementByClass(control.parentNode, "xforms-help-image");
            ORBEON.xforms.Globals.formHelpPanel[form.id].element.style.display = "block";
            ORBEON.xforms.Globals.formHelpPanel[form.id].cfg.setProperty("context", [helpImage, "bl", "tl"]);
            ORBEON.xforms.Globals.formHelpPanel[form.id].show();
            ORBEON.xforms.Globals.formHelpPanel[form.id].cfg.setProperty("zIndex", ORBEON.xforms.Globals.lastDialogZIndex++);
        }

        // Set focus on close button if visible (we don't want to set the focus on the close button if not
        // visible as this would make the help panel scroll down to the close button)
        var bdDiv = ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id].parentNode;
        if (bdDiv.scrollHeight <= bdDiv.clientHeight)
            ORBEON.xforms.Globals.formHelpPanelCloseButton[form.id].focus();
    },

    showDialog: function(controlId, neighbor) {
        var divElement = ORBEON.util.Dom.getElementById(controlId);
        var yuiDialog = ORBEON.xforms.Globals.dialogs[controlId];
        // Fixes cursor Firefox issue; more on this in dialog init code
        yuiDialog.element.style.display = "block";
        // Show the dialog
        yuiDialog.show();
        // By default try to display the dialog inside the viewport, but this can be overridden with consrain="false"
        var constrain = ORBEON.util.Dom.getAttribute(divElement, "constrain") == "false" ? false : true;
        yuiDialog.cfg.setProperty("constraintoviewport", constrain);
        // Make sure that this dialog is on top of everything else
        yuiDialog.cfg.setProperty("zIndex", ORBEON.xforms.Globals.lastDialogZIndex++);
        // Position the dialog either at the center of the viewport or relative of a neighbor
        if (neighbor == null) {
            // Center dialog in page, if not positioned relative to other element
            yuiDialog.center();
        } else {
            // Align dialog relative to neighbor
            yuiDialog.cfg.setProperty("context", [neighbor, "tl", "bl"]);
            yuiDialog.align();
            ORBEON.xforms.Globals.dialogMinimalVisible[controlId] = true;
        }
        // Take out the focus from the current control. This is particulary important with non-modal dialogs
        // opened with a minimal trigger, otherwise we have a dotted line around the link after it opens.
        if (ORBEON.xforms.Globals.currentFocusControlId != null) {
            var focusedElement = ORBEON.util.Dom.getElementById(ORBEON.xforms.Globals.currentFocusControlId);
            if (focusedElement != null) focusedElement.blur();
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
            // Control elements
            var targetControlElement = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            var currentFocusControlElement = ORBEON.xforms.Globals.currentFocusControlId != null ? ORBEON.util.Dom.getElementById(ORBEON.xforms.Globals.currentFocusControlId) : null;

            if (targetControlElement != null) {
                // Store initial value of control if we don't have a server value already, and if this is is not a list
                // Initial value for lists is set up initialization, as when we receive the focus event the new value is already set.
                if (typeof ORBEON.xforms.Globals.serverValue[targetControlElement.id] == "undefined"
                        && ! ORBEON.util.Dom.hasClass(targetControlElement, "xforms-select-appearance-compact")) {
                    ORBEON.xforms.Globals.serverValue[targetControlElement.id] = targetControlElement.value;
                }
            }

            // Send focus events
            var events = new Array();

            // The idea here is that we only register focus changes when focus moves between XForms controls. If focus
            // goes out to nothing, we don't handle it at this point but wait until focus comes back to a control.

            // We don't run this for dialogs, as there is not much sense doing this AND this causes issues with
            // FCKEditor embedded within dialogs with IE. In that case, the editor gets a blur, then the dialog, which
            // prevents detection of value changes in focus() above.

            if (targetControlElement != null && currentFocusControlElement != targetControlElement
                    && !ORBEON.util.Dom.hasClass(targetControlElement, "xforms-dialog")) {

                // Handle special value changes upon losing focus

                // HTML area and trees does not throw value change event, so we send the value change to the server
                // when we get the focus on the next control
                var changeValue = false;
                if (currentFocusControlElement != null) {// can be null on first focus
                    if (ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-textarea")
                            && ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-mediatype-text-html")) {
                        // To-do: would be nice to use the ORBEON.xforms.Controls.getCurrentValue() so we don't duplicate the code here
                        var editorInstance = FCKeditorAPI.GetInstance(currentFocusControlElement.name);
                        currentFocusControlElement.value = editorInstance.GetXHTML();
                        changeValue = true;
                    } else if (ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-select1-appearance-xxforms-tree")
                            || ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-select-appearance-xxforms-tree")) {
                        changeValue = true;
                    } else if (ORBEON.xforms.Globals.isMac && ORBEON.xforms.Globals.isRenderingEngineGecko
                            && ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-control")
                            && !ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-output")
                            && ! ORBEON.util.Dom.hasClass(currentFocusControlElement, "xforms-trigger")) {
                        // On Firefox running on Mac, when users ctrl-tabs out of Firefox, comes back, and then changes the focus
                        // to another field, we don't receive a change event. On Windows that change event is sent then user tabs
                        // out of Firefox. So here, we make sure that a value change has been sent to the server for the previous control
                        // that had the focus when we get the focus event for another control.
                        changeValue = true;
                    }
                    // Send value change if needed
                    if (changeValue)
                        xformsValueChanged(currentFocusControlElement, null);

                    // Handle DOMFocusOut
                    // Should send out DOMFocusOut only if no xxforms-value-change-with-focus-change was sent to avoid extra
                    // DOMFocusOut, but it is hard to detect correctly
                    events.push(xformsCreateEventArray(currentFocusControlElement, "DOMFocusOut", null));
                }

                // Handle DOMFocusIn
                events.push(xformsCreateEventArray(targetControlElement, "DOMFocusIn", null));

                // Keep track of the id of the last known control which has focus
                ORBEON.xforms.Globals.currentFocusControlId = targetControlElement.id;

                // Fire events
                xformsFireEvents(events, true);
            }

        } else {
            ORBEON.xforms.Globals.maskFocusEvents = false;
        }
    },

    blur: function(event) {
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            var targetControlElement = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (targetControlElement != null) {

                // Keep track of visited controls
                if (! ORBEON.util.Dom.hasClass(targetControlElement, "xforms-visited")) {
                    ORBEON.util.Dom.addClass(targetControlElement, "xforms-visited");
                    ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(ORBEON.xforms.Events._blurSetInvalidVisited, targetControlElement, true);
                }

                if (!ORBEON.util.Dom.hasClass(targetControlElement, "xforms-dialog")) {
                    // This is an event for an XForms control

                    // We don't run this for dialogs, as there is not much sense doing this AND this causes issues with
                    // FCKEditor embedded within dialogs with IE. In that case, the editor gets a blur, then the
                    // dialog, which prevents detection of value changes in focus() above.

                    // Keep track of the id of the last known control which has focus
                    ORBEON.xforms.Globals.currentFocusControlId = targetControlElement.id;
                }

                if (ORBEON.widgets.YUICalendar.appliesToControl(targetControlElement)) {
                    ORBEON.widgets.YUICalendar.blur(event, targetControlElement);
                }
            }
        }
    },

    /**
     * Called for a given control (this) that has receieved a blur after the following Ajax response
     * has been received.
     */
    _blurSetInvalidVisited: function() {
        var control = this;
        if (ORBEON.util.Dom.hasClass(control, "xforms-invalid"))
            ORBEON.util.Dom.addClass(control, "xforms-invalid-visited");
        var alertLabel = ORBEON.xforms.Controls._getControlLabel(control, "xforms-alert");
        if (alertLabel != null && ORBEON.util.Dom.hasClass(alertLabel, "xforms-alert-active"))
            ORBEON.util.Dom.addClass(alertLabel, "xforms-alert-active-visited");
        ORBEON.xforms.Events.ajaxResponseProcessedEvent.unsubscribe(ORBEON.xforms.Events._blurSetInvalidVisited);
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

                if (ORBEON.util.Dom.hasClass(target, "xforms-select1-appearance-compact")) {
                    // For select1 list, make sure we have exactly one value selected
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
                } else if (ORBEON.util.Dom.hasClass(target, "xforms-type-time") || ORBEON.util.Dom.hasClass(target, "xforms-type-date") || ORBEON.util.Dom.hasClass(target, "xforms-type-dateTime")) {

                    // For time, date, and dateTime fields, magic-parse field, and if recognized replace by display value

                    function toDisplayValue(input, magicToJSDate, jsDateToDisplay) {
                        var jsDate = magicToJSDate(input.value);
                        if (jsDate != null)
                            input.value = jsDateToDisplay(jsDate);
                    }

                    // Handle first text field (time or date)
                    toDisplayValue(ORBEON.util.Dom.getChildElementByIndex(target, 0),
                            ORBEON.util.Dom.hasClass(target, "xforms-type-time") ? ORBEON.util.DateTime.magicTimeToJSDate : ORBEON.util.DateTime.magicDateToJSDate,
                            ORBEON.util.Dom.hasClass(target, "xforms-type-time") ? ORBEON.util.DateTime.jsDateToformatDisplayTime : ORBEON.util.DateTime.jsDateToformatDisplayDate);
                    // Handle second text field for dateTime
                    if (ORBEON.util.Dom.hasClass(target, "xforms-type-dateTime"))
                        toDisplayValue(ORBEON.util.Dom.getChildElementByIndex(target, 1), ORBEON.util.DateTime.magicTimeToJSDate, ORBEON.util.DateTime.jsDateToformatDisplayTime);
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
            if (ORBEON.widgets.JSCalendar.appliesToControl(target)) {
                ORBEON.widgets.JSCalendar.keydown(event, target);
            } else if (ORBEON.widgets.YUICalendar.appliesToControl(target)) {
                ORBEON.widgets.YUICalendar.keydown(event, target);
            }
        }
    },

    keypress: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Input field and auto-complete: trigger DOMActive when when enter is pressed
            if (ORBEON.util.Dom.hasClass(target, "xforms-select1-open")
                    || (ORBEON.util.Dom.hasClass(target, "xforms-input") && !ORBEON.util.Dom.hasClass(target, "xforms-type-boolean"))
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
            // NOTE: Don't do this anymore, as this can now show an alert, which may be outdated before the next Ajax response
            //ORBEON.xforms.Controls.updateRequiredEmpty(target);

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

    _showToolTip: function(tooltipForControl, controlId, targetId, toolTipSuffix, message, delay, event) {
        if (message != "") {
            // We have a hint, initialize YUI tooltip
            var yuiTooltip =
                    new YAHOO.widget.Tooltip(controlId + toolTipSuffix, {
                        context: targetId,
                        text: message,
                        showDelay: delay,
                        effect: {effect: YAHOO.widget.ContainerEffect.FADE, duration: 0.2},
                        // We provide here a "high" zIndex value so the tooltip is "always" displayed on top over everything else.
                        // Otherwise, with dialogs, the tooltip might end up being below the dialog and be invisible.
                        zIndex: 1000
                    });
            var context = ORBEON.util.Dom.getElementById(targetId);
            // Send the mouse move event, because the tooltip gets positioned when receiving a mouse move.
            // Without this, sometimes the first time the tooltip is shows at the top left of the screen
            yuiTooltip.onContextMouseMove.call(context, event, yuiTooltip);
            // Send the mouse over event to the tooltip, since the YUI tooltip didn't receive it as it didn't
            // exist yet when the event was dispatched by the browser
            yuiTooltip.onContextMouseOver.call(context, event, yuiTooltip);
            // Save reference to YUI tooltip
            tooltipForControl[controlId] = yuiTooltip;
        } else {
            // Remember we looked at this control already
            tooltipForControl[controlId] = true;
        }
    },

    mouseover: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            // Control tooltip
            if (ORBEON.xforms.Globals.hintTooltipForControl[target.id] == null
                    && ! ORBEON.util.Dom.hasClass(document.body, "xforms-disable-hint-as-tooltip")) {
                var message = ORBEON.xforms.Controls.getHintMessage(target);
                ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.hintTooltipForControl, target.id, target.id, "-orbeon-hint-tooltip", message, 200, event);
            }

            // Alert tooltip
            if (ORBEON.util.Dom.hasClass(target, "xforms-alert-active")
                    && ! ORBEON.util.Dom.hasClass(document.body, "xforms-disable-alert-as-tooltip")) {
                var control = ORBEON.util.Dom.getElementById(target.htmlFor);
                if (ORBEON.xforms.Globals.alertTooltipForControl[control.id] == null) {
                    var message = ORBEON.xforms.Controls.getAlertMessage(control);
                    YAHOO.util.Dom.generateId(target);
                    ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.alertTooltipForControl, control.id, target.id, "-orbeon-alert-tooltip", message, 10, event);
                }
            } else if (ORBEON.util.Dom.hasClass(target, "xforms-dialog-appearance-minimal")) {
                // Minimal dialog: record more is back inside the dialog
                ORBEON.xforms.Globals.dialogMinimalLastMouseOut[target.id] = -1;
            }

            // Help tooltip
            if (ORBEON.util.Utils.getProperty(HELP_TOOLTIP_PROPERTY)
                    && ORBEON.util.Dom.hasClass(target, "xforms-help-image")) {
                // Get help label which is right after the image; there might be a text node between the two elements

                var helpLabel = target.nextSibling;
                if (!ORBEON.util.Dom.isElement(helpLabel))
                    helpLabel = helpLabel.nextSibling;
                // Get control
                var control = ORBEON.util.Dom.getElementById(helpLabel.htmlFor);
                if (ORBEON.xforms.Globals.helpTooltipForControl[control.id] == null) {
                    var message = ORBEON.xforms.Controls.getHelpMessage(control);
                    YAHOO.util.Dom.generateId(target);
                    ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.helpTooltipForControl, control.id, target.id, "-orbeon-help-tooltip", message, 0, event);
                }
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
                window.setTimeout(function() {
                    ORBEON.xforms.Events.dialogMinimalCheckMouseIn(yuiDialog);
                },
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
                // Translate this into a focus event
                ORBEON.xforms.Events.focus(event);
            } else  if ((ORBEON.util.Dom.hasClass(target, "xforms-trigger") || ORBEON.util.Dom.hasClass(target, "xforms-submit"))) {
                // Click on trigger
                YAHOO.util.Event.preventDefault(event);
                if (ORBEON.util.Dom.hasClass(target, "xxforms-offline-save")) {
                    // This is a trigger take commits the data changed so far in Gears
                    ORBEON.xforms.Offline.storeEvents(ORBEON.xforms.Offline.memoryOfflineEvents);
                    ORBEON.xforms.Offline.memoryOfflineEvents = [];
                }
                if (ORBEON.util.Dom.hasClass(target, "xxforms-online")) {
                    // This is a trigger take takes the form back online
                    ORBEON.xforms.Offline.takeOnline();
                }
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
                    || (ORBEON.util.Dom.hasClass(target, "xforms-input") && ORBEON.util.Dom.hasClass(target, "xforms-type-boolean"))) {
                // Click on checkbox or radio button
                xformsFireEvents(new Array(xformsCreateEventArray
                        (target, "xxforms-value-change-with-focus-change",
                                ORBEON.xforms.Controls.getCurrentValue(target), null)), false);
            } else if (ORBEON.util.Dom.hasClass(originalTarget, "xforms-type-date") ) {
                // Click on calendar inside input field
                if (ORBEON.util.Utils.getProperty(DATE_PICKER_PROPERTY) == "jscalendar") {
                    ORBEON.widgets.JSCalendar.click(event, target);
                } else {
                    ORBEON.widgets.YUICalendar.click(event, target);
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

                if (ORBEON.util.Utils.getProperty(HELP_HANDLER_PROPERTY)) {
                    // We are sending the xforms-help event to the server and the server will tell us what do to
                    xformsFireEvents(new Array(xformsCreateEventArray(control, "xforms-help")), false);
                } else {
                    // If the servers tells us there are no event handlers for xforms-help in the page,
                    // we can avoid a round trip and show the help right away
                    ORBEON.xforms.Controls.showHelp(control);
                }
            }
        } else {
            // Click on something that is not an XForms element, but which might still be in an repeat iteration,
            // in which case we want to let the server know about where in the iteration the click was.

            var node = originalTarget;

            // Iterate on ancestors, stop when we don't find ancestors anymore or we arrive at the form element
            while (node != null && ! (ORBEON.util.Dom.isElement(node) && node.tagName.toLowerCase() == "form")) {
                // Iterate on previous siblings
                var delimiterCount = 0;
                var foundRepeatBegin = false;
                var sibling = node;
                while (sibling != null) {
                    if (ORBEON.util.Dom.isElement(sibling)) {
                        if (sibling.id.indexOf("repeat-begin-") == 0) {
                            // Found beginning of current iteration, tell server
                            var form = ORBEON.xforms.Controls.getForm(sibling);
                            var targetId = sibling.id.substring("repeat-begin-".length);
                            targetId += targetId.indexOf(XFORMS_SEPARATOR_1) == -1 ? XFORMS_SEPARATOR_1 : XFORMS_SEPARATOR_2;
                            targetId += delimiterCount;
                            var event = new ORBEON.xforms.Server.Event(form, targetId, null, null, "DOMFocusIn");
                            ORBEON.xforms.Server.fireEvents([event]);
                            foundRepeatBegin = true;
                            break;
                        } else if (ORBEON.util.Dom.hasClass(sibling, "xforms-repeat-delimiter")) {
                            delimiterCount++;
                        }
                    }
                    sibling = sibling.previousSibling;
                }
                // We found what we were looking for, no need to go to parents
                if (foundRepeatBegin) break;
                // Explore parent
                node = node.parentNode;
            }
        }
    },

    /**
     * Called upon scrolling or resizing.
     */
    scrollOrResize: function() {
        // Adjust position of loading indicators
        for (var formID in ORBEON.xforms.Globals.formLoadingLoadingOverlay) {
            var overlay = ORBEON.xforms.Globals.formLoadingLoadingOverlay[formID];
            if (overlay && overlay.cfg.getProperty("visible"))
                ORBEON.xforms.Controls.updateLoadingPosition(formID);
        }
        // Ajust position of dialogs with "constraintoviewport" since YUI doesn't do it automatically
        for (var yuiDialogId in ORBEON.xforms.Globals.dialogs) {
            var yuiDialog = ORBEON.xforms.Globals.dialogs[yuiDialogId];
            if (yuiDialog.cfg.getProperty("visible") && yuiDialog.cfg.getProperty("constraintoviewport")) {
                yuiDialog.cfg.setProperty("xy", yuiDialog.cfg.getProperty("xy"));
            }
        }
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
    helpDialogXClose: function(_dummy1, _dummy2, formID) {
        // Fixes cursor Firefox issue; more on this in dialog init code
        var formHelpPanel = ORBEON.xforms.Globals.formHelpPanel[formID];
        formHelpPanel.element.style.display = "none";
    },

    /**
     * What we need to do when there is a click on a tree (select and select1)
     */
    treeClickFocus: function(control) {
        var isIncremental = ORBEON.util.Dom.hasClass(control, "xforms-incremental");
        if (ORBEON.xforms.Globals.currentFocusControlId != control.id) {// not sure we need to do this test here since focus() may do it anyway
            // We are comming from another control, simulate a focus on this control
            var focusEvent = { target: control };
            ORBEON.xforms.Events.focus(focusEvent);
        }
        // Preemptively store current control in previousDOMFocusOut, so when another control gets
        // the focus it will send the value of this control to the server
        ORBEON.xforms.Globals.currentFocusControlId = control.id;
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
        for (var nodeIndex in tree._nodes) {
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
        ORBEON.xforms.Globals.isReloading = true;
        window.location.reload(true);// force reload
        //NOTE: You would think that if reload is canceled, you would reset this to false, but somehow this fails with IE
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
                && current - ORBEON.xforms.Globals.dialogMinimalLastMouseOut[yuiDialog.element.id] >= ORBEON.util.Utils.getProperty(DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_PROPERTY)) {
            xformsFireEvents([xformsCreateEventArray(yuiDialog.element, "xxforms-dialog-close")], false);
        }
    },

    /**
     * A method for sending a heartbeat event if no event has sent to server in
     * the last time interval determined by session-heartbeat-delay property
     */
    sendHeartBeatIfNeeded: function(heartBeatDelay) {
        var currentTime = new Date().getTime();
        if ((currentTime - ORBEON.xforms.Globals.lastEventSentTime) >= heartBeatDelay) {
            var heartBeatDiv = ORBEON.util.Dom.getElementById("xforms-heartbeat");
            if (heartBeatDiv == null) {
                var form;
                for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
                    var candidateForm = document.forms[formIndex];
                    if (ORBEON.util.Dom.hasClass(candidateForm, "xforms-form")) {
                        form = candidateForm;
                        break;
                    }
                }
                var heartBeatDiv = document.createElement("div");
                heartBeatDiv.className = "xforms-heartbeat";
                heartBeatDiv.id = "xforms-heartbeat";
                form.appendChild(heartBeatDiv);
            }
            xformsFireEvents([xformsCreateEventArray(heartBeatDiv, "xxforms-session-heartbeat")], false);
        }
    },

    orbeonLoadedEvent: new YAHOO.util.CustomEvent("orbeonLoaded"),
    ajaxResponseProcessedEvent: new YAHOO.util.CustomEvent("ajaxResponseProcessed")
};

ORBEON.widgets.Base = function() {
    return {
        /**
         * Other widget this widget extends.
         */
        extending: null,

        /**
         * Function that returns true if and only if the this class applies to the control provided. This provides a
         * way to implement conditions in addition to the one set on the classes the element must have.
         */
        appliesToControl: function(control) {},

        /**
         * Respond to the click event.
         */
        click: function(event, target) {},
        blur: function(event, target) {},
        keydown: function(event, target) {}
    }
}();

ORBEON.widgets.JSCalendar = function() {

    /**
     * Send notification to XForms engine end-user clicked on day.
     */
    function update(calendar) {
        if (ORBEON.util.Dom.hasClass(calendar.activeDiv, "day")) {
            // Change value in field from ISO to display value
            var inputField = calendar.params.inputField;
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate(inputField.value);
            inputField.value = ORBEON.util.DateTime.jsDateToformatDisplayDate(jsDate);
            var element = inputField.parentNode;
            xformsFireEvents([xformsCreateEventArray(element, "xxforms-value-change-with-focus-change",
                    ORBEON.xforms.Controls.getCurrentValue(element))], false);
        }
    }

    /**
     * Restore last value actualy selected by user in the input field, which at this point could
     * contain a value the user just browsed to, without selecting it.
     */
    function close(calendar) {
        var inputField = calendar.params.inputField;
        var element = inputField.parentNode;
        calendar.hide();
    }

    return {

        extending: ORBEON.widgets.Base,

        appliesToControl: function(control) {
            return (ORBEON.util.Dom.hasClass(control, "xforms-type-date") || ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime"))
                    && ORBEON.util.Utils.getProperty(DATE_PICKER_PROPERTY) == "jscalendar";
        },

        click: function(event, target) {
            // Initialize calendar when needed
            var inputField = ORBEON.util.Dom.getChildElementByIndex(target, 0);

            // Setup calendar library
            Calendar.setup({
                inputField     :    inputField.id,
                ifFormat       :    "%m/%d/%Y",
                showsTime      :    false,
                button         :    target.id,
                singleClick    :    true,
                step           :    1,
                onUpdate       :    update,
                onClose        :    close,
                electric       :    false
            });
            // JSCalendar sets his listener in the onclick attribute: save it so we can call it later
            var jsCalendarOnclick = target.onclick;
            target.onclick = null;
            // Call jscalendar code that opens the calendar
            jsCalendarOnclick();
        },

        blur: function(event, target) {},

        keydown: function(event, target) {
            // Close calendar when user starts typing
            calendar.hide();
        }
    }
}();

ORBEON.widgets.YUICalendar = function() {

    var RESOURCES = {
        "en": {
            "MONTHS_LONG": [ "January", "February", "March", "April", "May", "June", "July", "August",  "Septembre",  "October",  "November",  "December" ],
            "WEEKDAYS_SHORT": ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"],
            "START_WEEKDAY": 0
        },
        "fr": {
            "MONTHS_LONG": [ "Janvier", "F\xe9vrier", "Mars", "Avril", "Mai", "Juin", "Juillet", "Ao\xfbt",  "Septembre",  "Octobre",  "Novembre",  "D\xe9cembre" ],
            "WEEKDAYS_SHORT": ["Di", "Lu", "Ma", "Me", "Je", "Ve", "Sa"],
            "START_WEEKDAY": 1
        },
        "es": {
            "MONTHS_LONG": [ "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",  "Septiembre",  "Octubre",  "Noviembre",  "Diciembre" ],
            "WEEKDAYS_SHORT": ["Do", "Lu", "Ma", "Mi", "Ju", "Vi", "S\xe1"],
            "START_WEEKDAY": 1
        }
    };

    // Set when the calendar is ceated the first time
    var yuiCalendar = null;
    var calendarDiv = null;

    // Set when the calendar is opened for a given control
    var control = null;
    var inputField = null;

    // When calendar is in use, is the mouse in the calendar area
    var mouseOverCalendar = false;

    function hideCal() {
        if (!over_cal) {
            YAHOO.util.Dom.setStyle('cal1Container', 'display', 'none');
        }
    }

    function mouseover() {
        mouseOverCalendar = true;
    }

    function mouseout() {
        mouseOverCalendar = false;
    }

    // After the calendar is rendered, setup listeners on mouseover/mouseout
    function setupListeners() {
        YAHOO.util.Event.addListener(calendarDiv, "mouseover", mouseover);
        YAHOO.util.Event.addListener(calendarDiv, "mouseout", mouseout);
    }

    // User selected a date in the picker
    function dateSelected() {
        var jsDate = yuiCalendar.getSelectedDates()[0];
        inputField.value = ORBEON.util.DateTime.jsDateToformatDisplayDate(jsDate);
        xformsFireEvents([xformsCreateEventArray(control, "xxforms-value-change-with-focus-change",
                ORBEON.xforms.Controls.getCurrentValue(control))], false);
        closeCalendar();
    }

    // Hide calendar div and do some cleanup of private variables
    function closeCalendar() {
        control = null;
        inputField = null;
        mouseOverCalendar = false;
        YAHOO.util.Dom.setStyle(calendarDiv, "display", "none");
    }

    return {
        extending: ORBEON.widgets.Base,

        appliesToControl: function(control) {
            return (ORBEON.util.Dom.hasClass(control, "xforms-type-date") || ORBEON.util.Dom.hasClass(control, "xforms-type-dateTime"))
                    && ORBEON.util.Utils.getProperty(DATE_PICKER_PROPERTY) == "yui";
        },

        click: function(event, target) {

            // Create YUI calendar the first time this is used
            if (calendarDiv == null) {
                // Create div for the YUI calendar widget
                calendarDiv = document.createElement("div");
                calendarDiv.id = "orbeon-calendar-div";
                document.body.appendChild(calendarDiv);

                // Create YUI calendar
                yuiCalendar = new YAHOO.widget.Calendar(calendarDiv.id);

                // Listeners on calendar events
                yuiCalendar.renderEvent.subscribe(setupListeners, yuiCalendar, true);
                yuiCalendar.selectEvent.subscribe(dateSelected, yuiCalendar, true);
            }

            // Localize calendar
            var lang = ORBEON.util.Dom.getAttribute(document.documentElement, "lang");
            if (lang == null)
                lang = "en";
            var resources = RESOURCES[lang];
            for (var key in resources)
                yuiCalendar.cfg.setProperty(key, resources[key]);

            // Set date
            control = target;
            var date = ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(control));
            if (date == null) {
                yuiCalendar.cfg.setProperty("selected", "", false);
                yuiCalendar.cfg.setProperty("pagedate", new Date(), false);
            } else {
                // Date must be the internal format expected by YUI
                var dateStringForYUI = (date.getMonth() + 1)
                   + "/" + date.getDate()
                   + "/" + date.getFullYear();
                yuiCalendar.cfg.setProperty("selected", dateStringForYUI, false);
                yuiCalendar.cfg.setProperty("pagedate", date, false);
            }
            yuiCalendar.cfg.applyConfig();

            // Show calendar
            yuiCalendar.render();
            YAHOO.util.Dom.setStyle(calendarDiv, "display", "block");

            // Position calendar below field
            inputField = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            var xy = YAHOO.util.Dom.getXY(inputField);
            xy[1] = xy[1] + 20;
            YAHOO.util.Dom.setXY(calendarDiv, xy);
        },

        blur: function(event, target) {
            if (mouseOverCalendar) {
                window.setTimeout(function() { inputField.focus(); }, XFORMS_INTERNAL_SHORT_DELAY_IN_MS);
            } else {
                closeCalendar();
            }
        },

        keydown: function(event, target) {
            // Close calendar when user starts typing
            closeCalendar();
        }
    }
}();


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
                var elementCount = form.elements.length;
                for (var j = 0; j < elementCount; j++) {
                    var element = form.elements[j];
                    ORBEON.xforms.Init.registerListenersOnFormElement((element));
                }
            }
        }
    },

    /**
     * For IE, since we haven't found a way to have one listener on the whole document the "change" event.
     */
    registerListenersOnFormElement: function(element) {
        YAHOO.util.Event.addListener(element, "change", ORBEON.xforms.Events.change);
    },

    registerDraggableListenersOnRepeatElements: function() {
        var repeatDelimiter = YAHOO.util.Dom.getElementsByClassName("xforms-repeat-delimiter");

        for (var i = 0; i < repeatDelimiter.length; i++) {
            var previousSiblingId = YAHOO.util.Dom.getPreviousSibling(repeatDelimiter[i]).id;
            var repeatElement = YAHOO.util.Dom.getNextSibling(repeatDelimiter[i]);
            if (ORBEON.util.Dom.hasClass(repeatElement, "xforms-dnd")) {
                //Right now, we support Drag n Drop of following elements
                if(repeatElement.tagName.toLowerCase() == "div" || repeatElement.tagName.toLowerCase() == "tr" || repeatElement.tagName.toLowerCase() == "td") {
                    ORBEON.xforms.Init.registerDraggableListenersOnRepeatElement(repeatElement, previousSiblingId);
                }
            }
        }
    },

    registerDraggableListenersOnRepeatElement: function(repeatElement, previousSiblingId) {
        var draggableItem = new ORBEON.xforms.DnD.DraggableItem(repeatElement);

        if(previousSiblingId.indexOf(XFORMS_SEPARATOR_1) == -1)
            repeatElement.position = XFORMS_SEPARATOR_1 + "1";
        else
            repeatElement.position = XFORMS_SEPARATOR_1 + previousSiblingId.substring(previousSiblingId.indexOf(XFORMS_SEPARATOR_1)+1) + XFORMS_SEPARATOR_2 + "1";


        if(ORBEON.util.Dom.hasClass(repeatElement, "xforms-dnd-vertical"))
            draggableItem.setXConstraint(0, 0);
        else if(ORBEON.util.Dom.hasClass(repeatElement, "xforms-dnd-horizontal"))
            draggableItem.setYConstraint(0, 0);
    },

    document: function() {

        ORBEON.xforms.Globals = {
            // Booleans used for browser detection
            isMac : navigator.userAgent.toLowerCase().indexOf("macintosh") != -1,                 // Running on Mac
            isRenderingEngineGecko: navigator.userAgent.toLowerCase().indexOf("gecko") != -1,     // Firefox
            isFF3: navigator.userAgent.toLowerCase().indexOf("firefox/3") != -1,                  // Firefox 3.0 (NOTE: will have to fix this for Firefox 4 and later)
            isRenderingEnginePresto: navigator.userAgent.toLowerCase().indexOf("opera") != -1,    // Opera
            isRenderingEngineWebCore: navigator.userAgent.toLowerCase().indexOf("safari") != -1,  // Safari
            isRenderingEngineWebCore13: navigator.userAgent.indexOf("AppleWebKit/312") != -1,     // Safari 1.3
            isRenderingEngineTrident: navigator.userAgent.toLowerCase().indexOf("msie") != -1     // Internet Explorer
                    && navigator.userAgent.toLowerCase().indexOf("opera") == -1,

            /**
             * All the browsers support events in the capture phase, except IE and Safari 1.3. When browser don't support events
             * in the capture phase, we need to register a listener for certain events on the elements itself, instead of
             * just registering the event handler on the window object.
             */
            baseURL: null,
            xformsServerURL: null,
            eventQueue: [],                      // Events to be sent to the server
            eventsFirstEventTime: 0,             // Time when the first event in the queue was added
            requestForm: null,                   // HTML for the request currently in progress
            requestIgnoreErrors: false,          // Should we ignore errors that result from running this request
            requestInProgress: false,            // Indicates wether an Ajax request is currently in process
            requestDocument: "",                 // The last Ajax request, so we can resend it if necessary
            requestRetries: 3,                   // How many retries we have left before we give up with this Ajax request
            executeEventFunctionQueued: 0,       // Number of ORBEON.xforms.Server.executeNextRequest waiting to be executed
            maskFocusEvents: false,              // Avoid catching focus event when we do call setfocus upon server request
            currentFocusControlId: null,         // Track which control has focus
            htmlAreaNames: [],                   // Names of the FCK editors, which we need to reenable them on Firefox
            repeatTreeChildToParent: {},         // Describes the repeat hierarchy
            repeatIndexes: {},                   // The current index for each repeat
            repeatTreeParentToAllChildren: {},   // Map from parent to array with children, used when highlight changes
            inputCalendarCommitedValue: {},      // Maps input id to the value of JSCalendar actually selected by the user
            yuiCalendar: null,                   // Reusable calendar widget
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
            helpTooltipForControl: {},           // Map from element id -> YUI help or true, that tells us if we have already created a Tooltip for an element
            debugDiv: null,                      // Points to the div when debug messages are displayed
            debugLastTime: new Date().getTime(), // Timestamp when the last debug message was printed
            lastEventSentTime: new Date().getTime(), // Timestamp when the last event was sent to server
            pageLoadedRegistered: true,          // If the page loaded listener has been registered already, to avoid running it more than once
            menuItemsets: {},                    // Maps menu id to structure defining the content of the menu
            menuYui: {},                         // Maps menu id to the YUI object for that menu
            treeYui: {},                         // Maps tree id to the YUI object for that tree
            idToElement: {},                     // Maintain mapping from ID to element, so we don't lookup the sme ID more than once
            isReloading: false,                  // Whether the form is being reloaded from the server
            lastDialogZIndex: 5,                 // zIndex of the last dialog displayed. Gets incremented so the last dialog is always on top of everything else
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
            formClientState: {},                 // Store for information we want to keep when the page is reloaded
            modalProgressPanel: null,            // Overlay modal panel for displaying progress bar
            topLevelListenerRegistered:          // Have we already registered the listeners on the top-level elements, which never change
                ORBEON.xforms.Globals.topLevelListenerRegistered == null ? false : ORBEON.xforms.Globals.topLevelListenerRegistered 
        };


        // Notify the offline module that the page was loaded
        if (ORBEON.util.Utils.getProperty(OFFLINE_SUPPORT_PROPERTY))
            ORBEON.xforms.Offline.pageLoad();

        // Register events in the capture phase for W3C-compliant browsers.
        if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
            ORBEON.xforms.Init.registerListenersOnFormElements();
            if (!ORBEON.xforms.Globals.topLevelListenerRegistered) {
                YAHOO.util.Event.addListener(document, "focusin", ORBEON.xforms.Events.focus);
                YAHOO.util.Event.addListener(document, "focusout", ORBEON.xforms.Events.blur);
                YAHOO.util.Event.addListener(document, "change", ORBEON.xforms.Events.change);
            }
        } else {
            if (!ORBEON.xforms.Globals.topLevelListenerRegistered) {
                document.addEventListener("focus", ORBEON.xforms.Events.focus, true);
                document.addEventListener("blur", ORBEON.xforms.Events.blur, true);
                document.addEventListener("change", ORBEON.xforms.Events.change, true);
            }
        }

        ORBEON.xforms.Init.registerDraggableListenersOnRepeatElements();
        // Register events that bubble on document for all browsers
        if (!ORBEON.xforms.Globals.topLevelListenerRegistered) {
            YAHOO.util.Event.addListener(document, "keypress", ORBEON.xforms.Events.keypress);
            YAHOO.util.Event.addListener(document, "keydown", ORBEON.xforms.Events.keydown);
            YAHOO.util.Event.addListener(document, "keyup", ORBEON.xforms.Events.keyup);
            YAHOO.util.Event.addListener(document, "mouseover", ORBEON.xforms.Events.mouseover);
            YAHOO.util.Event.addListener(document, "mouseout", ORBEON.xforms.Events.mouseout);
            YAHOO.util.Event.addListener(document, "click", ORBEON.xforms.Events.click);
            YAHOO.util.Event.addListener(window, "resize", ORBEON.xforms.Events.resize);
            YAHOO.widget.Overlay.windowScrollEvent.subscribe(ORBEON.xforms.Events.scrollOrResize);
            YAHOO.widget.Overlay.windowResizeEvent.subscribe(ORBEON.xforms.Events.scrollOrResize);
        }

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
                    ORBEON.xforms.Globals.baseURL = scriptSrc.substr(0, startPathToJavaScript);
                    break;
                }
            }
        }

        ORBEON.xforms.Globals.xformsServerURL = ORBEON.xforms.Globals.baseURL + "/xforms-server";

        // A heartbeat event - An AJAX request for letting server know that "I'm still alive"
        if (ORBEON.util.Utils.getProperty(SESSION_HEARTBEAT_PROPERTY)) {
            var heartBeatDelay = ORBEON.util.Utils.getProperty(SESSION_HEARTBEAT_DELAY_PROPERTY);
            if (heartBeatDelay > 0) {
                window.setInterval(function() {
                    ORBEON.xforms.Events.sendHeartBeatIfNeeded(heartBeatDelay);
                }, heartBeatDelay / 10); // say session is 30 mn, heartbeat must come after 24 mn, we check every 2.4 mn so we should
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

                        // Find reference to elements in the details hidden section
                        var titleDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "hd");
                        var bodyDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "bd");
                        var detailsHiddenDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-error-panel-details-hidden");
                        var showDetailsA = ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(detailsHiddenDiv, 0), 0);
                        YAHOO.util.Dom.generateId(showDetailsA);

                        // Find reference to elements in the details shown section
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
                            // Store the initial dynamic state in client state. We do this only if formClientState is empty.
                            // If it is not empty, this means that we already have an initial state stored there, and that this
                            // function runs because the user reloaded or navigated back to this page.
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
                    if (repeatInfo.length > 1) {
                        var parent = repeatInfo[repeatInfo.length - 1];
                        ORBEON.xforms.Globals.repeatTreeChildToParent[id] = parent;
                    }
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
                    if (ORBEON.util.Utils.getProperty(REVISIT_HANDLING_PROPERTY) == "reload") {
                        ORBEON.xforms.Globals.isReloading = true;
                        window.location.reload(true);
                        //NOTE: You would think that if reload is canceled, you would reset this to false, but somehow this fails with IE
                    } else {
                        xformsFireEvents(new Array(xformsCreateEventArray(form, "xxforms-all-events-required", null, null)), false);
                    }
                }
            }
        }

        // Run code sent by server
        if (typeof xformsPageLoadedServer != "undefined" && !ORBEON.xforms.Globals.fckEditorLoading)
            xformsPageLoadedServer();

        // Run call-back function interested in knowing when the form is initialized
        if (window.parent.childWindowOrbeonReady) {
            window.parent.childWindowOrbeonReady();
            window.parent.childWindowOrbeonReady = null;
        }

        ORBEON.xforms.Globals.topLevelListenerRegistered = true;
        ORBEON.xforms.Events.orbeonLoadedEvent.fire();
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
            var childNode = menu.childNodes[j];
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

        fckEditor.BasePath = ORBEON.xforms.Globals.baseURL + ORBEON.util.Utils.getProperty(FCK_EDITOR_BASE_PATH_PROPERTY);
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
            fckEditor.Config["CustomConfigurationsPath"] = fckEditor.BasePath + FCK_CUSTOM_CONFIG;

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
        var isDraggable = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-draggable-true");
        var isVisible = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-visible-true");
        var isMinimal = ORBEON.util.Dom.hasClass(dialog, "xforms-dialog-appearance-minimal");
        ORBEON.util.Dom.removeClass(dialog, "xforms-initially-hidden"); // mmh, should we move this further down after render()?

        // Create dialog object
        if (isMinimal) {
            // Create minimal dialog
            yuiDialog = new YAHOO.widget.Panel(dialog.id, {
                close: false,
                modal: isModal,
                visible: false,
                constraintoviewport: true,
                iframe: true,
                underlay: "shadow"
            });
            // Close the dialog when users click on document
            YAHOO.util.Event.addListener(document.body, "click", ORBEON.xforms.Events.dialogMinimalBodyClick, yuiDialog);
        } else {
            // Create full dialog
            yuiDialog = new YAHOO.widget.Dialog(dialog.id, {
                modal: isModal,
                close: hasClose,
                visible: false,
                draggable: isDraggable,
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

        if (isVisible)
            ORBEON.xforms.Controls.showDialog(dialog.id, null);
    }
};

ORBEON.xforms.Server = {

    Event: function(form, targetId, otherId, value, eventName, bubbles, cancelable, ignoreErrors, additionalAttribs) {
        this.form = form;
        this.targetId = targetId;
        this.otherId = otherId;
        this.value = value;
        this.eventName = eventName;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
        this.ignoreErrors = ignoreErrors;
        this.additionalAttribs = additionalAttribs;
    },

    /**
     * When an exception happens while we communicate with the server, we catch it and show an error in the UI.
     * This is to prevent the UI from becoming totally unusable after an error.
     */
    exceptionWhenTalkingToServer: function(e, formID) {
        ORBEON.util.Utils.logMessage("JavaScript error");
        ORBEON.util.Utils.logMessage(e);
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
        if (! ORBEON.xforms.Offline.isOnline) {
            // Go through all events
            var valueChangeEvents = [];
            for (var eventIndex = 0; eventIndex < events.length; eventIndex++) {
                var event = events[eventIndex];
                if (event.eventName == "xxforms-value-change-with-focus-change") {
                    valueChangeEvents.push(event);
                    // Store new value of control
                    ORBEON.xforms.Offline.controlValues[event.targetId] = event.value;
                }
            }
            // Evaluate MIPS if there was a value change event
            if (valueChangeEvents.length > 0) {
                // Store in memory the value change events
                ORBEON.xforms.Offline.memoryOfflineEvents = ORBEON.xforms.Offline.memoryOfflineEvents.concat(valueChangeEvents);
                // Insert delay before we evaluate MIPS, just to avoid repeatedly evaluating MIPS if nothing changed
                ORBEON.xforms.Globals.executeEventFunctionQueued++;
                window.setTimeout(
                    function() {
                        ORBEON.xforms.Globals.executeEventFunctionQueued--;
                        if (ORBEON.xforms.Globals.executeEventFunctionQueued == 0)
                            ORBEON.xforms.Offline.evaluateMIPs();
                    },
                    ORBEON.util.Utils.getProperty(INTERNAL_SHORT_DELAY_PROPERTY)
                );
            }
        } else {
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
                                 ORBEON.util.Utils.getProperty(DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_PROPERTY))) {
                // After a delay (e.g. 500 ms), run executeNextRequest() and send queued events to server
                // if there are no other executeNextRequest() that have been added to the queue after this
                // request.
                window.setTimeout(
                    function() { ORBEON.xforms.Server.executeNextRequest(false); },
                    ORBEON.util.Utils.getProperty(DELAY_BEFORE_INCREMENTAL_REQUEST_PROPERTY)
                );
            } else {
                // After a very short delay (e.g. 20 ms), run executeNextRequest() and force queued events
                // to be sent to the server, even if there are other executeNextRequest() queued.
                // The small delay is here so we don't send multiple requests to the server when the
                // browser gives us a sequence of events (e.g. focus out, change, focus in).
                window.setTimeout(
                    function() { ORBEON.xforms.Server.executeNextRequest(true); },
                    ORBEON.util.Utils.getProperty(INTERNAL_SHORT_DELAY_PROPERTY)
                );
            }
            ORBEON.xforms.Globals.lastEventSentTime = new Date().getTime(); // Update the last event sent time
        }
    },

    executeNextRequest: function(bypassRequestQueue) {
        bypassRequestQueue = typeof(bypassRequestQueue) == "boolean" && bypassRequestQueue == true;

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
                var delayBeforeDisplayLoading = ORBEON.util.Utils.getProperty(DELAY_BEFORE_DISPLAY_LOADING_PROPERTY);
                if (delayBeforeDisplayLoading == 0) xformsDisplayLoading();
                else window.setTimeout(xformsDisplayLoading, delayBeforeDisplayLoading);

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
                    if (event.eventName == "xxforms-all-events-required" || event.eventName == "xxforms-offline") {
                        sendInitialDynamicState = true;
                    }
                    // Display progress panel if trigger with "xforms-trigger-appearance-modal" class was activated
                    var eventElement = ORBEON.util.Dom.getElementById(event.targetId);
                    if (event.eventName == 'DOMActivate') {
                        if (ORBEON.util.Dom.hasClass(eventElement, "xforms-trigger-appearance-modal"))
                            ORBEON.util.Utils.displayModalProgressPanel();
                        else if (ORBEON.util.Dom.hasClass(eventElement, "xxforms-offline"))
                            sendInitialDynamicState = true; // Case of going offline
                    }
                }

                // Build request
                var requestDocumentString = [];

                // Add entity declaration for nbsp. We are adding this as this entity is generated by the FCK editor.
                requestDocumentString.push('<!DOCTYPE xxforms:event-request [<!ENTITY nbsp "&#160;">]>\n');

                var indent = "    ";
                {
                    // Start request
                    requestDocumentString.push('<xxforms:event-request xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">\n');

                    // Add static state
                    requestDocumentString.push(indent);
                    requestDocumentString.push('<xxforms:static-state>');
                    requestDocumentString.push(ORBEON.xforms.Globals.formStaticState[formID].value);
                    requestDocumentString.push('</xxforms:static-state>\n');

                    // Add dynamic state
                    requestDocumentString.push(indent);
                    requestDocumentString.push('<xxforms:dynamic-state>');
                    requestDocumentString.push(ORBEON.xforms.Globals.formDynamicState[formID].value);
                    requestDocumentString.push('</xxforms:dynamic-state>\n');

                    // Add initial dynamic state if needed
                    if (sendInitialDynamicState) {
                        requestDocumentString.push(indent);
                        requestDocumentString.push('<xxforms:initial-dynamic-state>');
                        requestDocumentString.push(xformsGetFromClientState(formID, "initial-dynamic-state"));
                        requestDocumentString.push('</xxforms:initial-dynamic-state>\n');
                    }


                    // Keep track of the events we have handled, so we can later remove them from the queue
                    var handledEvents = [];

                    // Add server-events, if any. Server execpts server-events in a separate elements before the
                    // <xxforms:action> which contains all the <xxforms:event>.
                    for (var i = 0; i < ORBEON.xforms.Globals.eventQueue.length; i++) {
                        var event = ORBEON.xforms.Globals.eventQueue[i];
                        // Only handle this event if it is for the form we chose, and if
                        if (ORBEON.xforms.Controls.getForm(event.form) == ORBEON.xforms.Globals.requestForm) {
                            if (event.eventName == "server-events") {
                                requestDocumentString.push(indent);
                                requestDocumentString.push('<xxforms:server-events>');
                                requestDocumentString.push(event.value);
                                requestDocumentString.push('</xxforms:server-events>');
                                handledEvents.unshift(i);
                            }
                        }
                    }

                    // Start action
                    requestDocumentString.push(indent);
                    requestDocumentString.push('<xxforms:action>\n');

                    // Add events
                    for (var i = 0; i < ORBEON.xforms.Globals.eventQueue.length; i++) {
                        var event = ORBEON.xforms.Globals.eventQueue[i];

                        // Only handle this event if it is for the form we chose, and if
                        // And if this is not an xforms-events (which is sent separately, not as an action).
                        if (ORBEON.xforms.Controls.getForm(event.form) == ORBEON.xforms.Globals.requestForm
                                && event.eventName != "server-events") {
                            // Create <xxforms:event> element
                            requestDocumentString.push(indent + indent);
                            requestDocumentString.push('<xxforms:event');
                            requestDocumentString.push(' name="' + event.eventName + '"');
                            if (event.targetId != null)
                                requestDocumentString.push(' source-control-id="' + event.targetId + '"');
                            if (event.otherId != null)
                                requestDocumentString.push(' other-control-id="' + event.otherId + '"');
                            if (event.additionalAttribs != null) {
                                for(var attribIndex = 0; attribIndex < event.additionalAttribs.length - 1; attribIndex+=2)
                                    requestDocumentString.push(' '+ event.additionalAttribs[attribIndex] +'="' + event.additionalAttribs[attribIndex+1] + '"');
                            }
                            requestDocumentString.push('>');
                            if (event.value != null) {
                                // When the range is used we get an int here when the page is first loaded
                                if (typeof event.value == "string") {
                                    event.value = event.value.replace(XFORMS_REGEXP_AMPERSAND, "&amp;");
                                    event.value = event.value.replace(XFORMS_REGEXP_OPEN_ANGLE, "&lt;");
                                }
                                requestDocumentString.push(event.value);
                            }
                            requestDocumentString.push('</xxforms:event>\n');
                            handledEvents.unshift(i);
                        }
                    }

                    // End action
                    requestDocumentString.push(indent);
                    requestDocumentString.push('</xxforms:action>\n');

                    // End request
                    requestDocumentString.push('</xxforms:event-request>');

                    // Remove events we have handled from event queue
                    for (var i = 0; i < handledEvents.length; i++)
                        ORBEON.xforms.Globals.eventQueue.splice(handledEvents[i], 1);
                }

                // Send request
                executedRequest = true;
                ORBEON.xforms.Globals.requestRetries = ORBEON.util.Utils.getProperty(REQUEST_RETRIES_PROPERTY);
                ORBEON.xforms.Globals.requestDocument = requestDocumentString.join("");
                ORBEON.xforms.Server.asyncRequest();
            }
        }

        // Hide loading indicator if we have not started a new request (nothing more to run)
        // and there are not events in the queue. However make sure not to hide the error message
        // if the last XHR query returned an error.
        if (!executedRequest && ORBEON.xforms.Globals.eventQueue.length == 0) {
            xformsDisplayIndicator("none");
        }
    },

    asyncRequest: function() {
        try {
            ORBEON.xforms.Globals.requestRetries--;
            YAHOO.util.Connect.setDefaultPostHeader(false);
            YAHOO.util.Connect.initHeader("Content-Type", "application/xml");
            var callback = {
                success: ORBEON.xforms.Server.handleResponseAjax,
                failure: ORBEON.xforms.Server.handleFailure
            };
            var ajaxTimeout = ORBEON.util.Utils.getProperty(DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY);
            if (ajaxTimeout != -1)
                callback.timeout = ajaxTimeout;
            YAHOO.util.Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL, callback, ORBEON.xforms.Globals.requestDocument);
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
                window.setTimeout(function() {
                    ORBEON.xforms.Server.showError("Error while processing response", details, formID);
                },
                        ORBEON.util.Utils.getProperty(DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_PROPERTY));
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
        ORBEON.xforms.Server.handleResponseAjax(o);
    },


    handleResponseAjax: function(o) {

        var responseXML = o.responseXML;
        if (!YAHOO.lang.isUndefined(o.getResponseHeader) && o.getResponseHeader["Content-Type"] == "text/html") {

            // Parse content we receive into a new div we create just for that purpose
            var temporaryContainer = document.createElement("div");
            temporaryContainer.innerHTML = o.responseText;
            var newPortletDiv = ORBEON.util.Dom.getChildElementByIndex(temporaryContainer, 0);

            // Get existing div which is above the form that issued this request
            var existingPortletDiv = ORBEON.xforms.Globals.requestForm;
            while (existingPortletDiv != null && !ORBEON.util.Dom.hasClass(existingPortletDiv, "orbeon-portlet-div"))
                existingPortletDiv = existingPortletDiv.parentNode;

            // Remove content from existing div
            while (existingPortletDiv.childNodes.length > 0)
                existingPortletDiv.removeChild(existingPortletDiv.firstChild);

            // Add children from newPortletDiv
            while (newPortletDiv.childNodes.length > 0)
                existingPortletDiv.appendChild(newPortletDiv.firstChild);

            // Perform initialization again
            ORBEON.xforms.Init.document();

        } else {
            if (!responseXML || (responseXML && responseXML.documentElement && responseXML.documentElement.tagName.toLowerCase() == "html")) {
                // The XML docucment does not come in o.responseXML: parse o.responseText.
                // This happens in particular when we get a response after a background upload.
                var xmlString = o.responseText.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
                responseXML = ORBEON.util.Dom.stringToDom(xmlString);
            }
            var formID = ORBEON.xforms.Globals.requestForm.id;
            ORBEON.xforms.Server.handleResponseDom(responseXML, formID);
            // Reset changes, as changes are included in this bach of events
            ORBEON.xforms.Globals.changedIdsRequest = {};
            // Go ahead with next request, if any
            ORBEON.xforms.Globals.requestInProgress = false;
            ORBEON.xforms.Globals.requestDocument = "";
            ORBEON.xforms.Globals.executeEventFunctionQueued++;
            ORBEON.util.Utils.hideModalProgressPanel();
            ORBEON.xforms.Server.executeNextRequest(false);

            // Notify listeners that we are done processing this request
            ORBEON.xforms.Events.ajaxResponseProcessedEvent.fire();
        }
    },

    /**
     * Process events in the DOM passed as parameter.
     *
     * @param responseXML       DOM containing events to process
     */
    handleResponseDom: function(responseXML, formID) {

        try {
            if (responseXML && responseXML.documentElement
                    && responseXML.documentElement.tagName.indexOf("event-response") != -1) {

                // Good: we received an XML document from the server
                var responseRoot = responseXML.documentElement;

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

                    // Store new dynamic and static state as soon as we find it. This is because the server only keeps the last
                    // dynamic state. So if a JavaScript error happens later on while processing the response,
                    // the next request we do we still want to send the latest dynamic state known to the server.
                    if (xformsGetLocalName(responseRoot.childNodes[i]) == "dynamic-state") {
                        var newDynamicState = ORBEON.util.Dom.getStringValue(responseRoot.childNodes[i]);
                        ORBEON.xforms.Globals.formDynamicState[formID].value = newDynamicState;
                    } else if (xformsGetLocalName(responseRoot.childNodes[i]) == "static-state") {
                        var newStaticState = ORBEON.util.Dom.getStringValue(responseRoot.childNodes[i]);
                        ORBEON.xforms.Globals.formStaticState[formID].value = newStaticState;
                    } else if (xformsGetLocalName(responseRoot.childNodes[i]) == "action") {
                        var actionElement = responseRoot.childNodes[i];

                        // First repeat and delete "lines" in repeat (as itemset changed below might be in a new line)
                        for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {
                            var actionName = xformsGetLocalName(actionElement.childNodes[actionIndex]);
                            switch (actionName) {

                                case "control-values": {
                                    var controlValuesElement = actionElement.childNodes[actionIndex];
                                    var copyRepeatTemplateElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "copy-repeat-template", xmlNamespace);
                                    var copyRepeatTemplateElementsLength = copyRepeatTemplateElements.length;
                                    for (var j = 0; j < copyRepeatTemplateElementsLength; j++) {

                                        // Copy repeat template
                                        var copyRepeatTemplateElement = copyRepeatTemplateElements[j];
                                        var repeatId = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "id");
                                        var parentIndexes = ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "parent-indexes");
                                        var startSuffix = Number(ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "start-suffix"));
                                        var endSuffix = Number(ORBEON.util.Dom.getAttribute(copyRepeatTemplateElement, "end-suffix"));
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
                                                    if (ORBEON.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-begin-") == 0)
                                                        nestedRepeatLevel--;
                                                    // Increment nestedRepeatLevel when we enter a nested repeat
                                                    if (ORBEON.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-end-") == 0)
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
                                        for (var suffix = startSuffix; suffix <= endSuffix; suffix++) {
                                            var nestedRepeatLevel = 0;
                                            for (var templateNodeIndex = 0; templateNodeIndex < templateNodes.length; templateNodeIndex++) {
                                                var templateNode = templateNodes[templateNodeIndex];

                                                // Add suffix to all the ids
                                                var newTemplateNode;
                                                if (startSuffix == endSuffix || suffix == endSuffix) {
                                                    // Just one template to copy, or we are at the end: do the work on the initial copy
                                                    newTemplateNode = templateNodes[templateNodeIndex];
                                                } else {
                                                    // Clone again
                                                    newTemplateNode = templateNodes[templateNodeIndex].cloneNode(true)
                                                }
                                                if (newTemplateNode.nodeType == ELEMENT_TYPE) {
                                                    // Decrement nestedRepeatLevel when we we exit a nested repeat
                                                    if (ORBEON.util.Dom.hasClass(newTemplateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-end-") == 0)
                                                        nestedRepeatLevel--;
                                                    xformsAddSuffixToIds(newTemplateNode, parentIndexes == "" ? String(suffix) : parentIndexes + XFORMS_SEPARATOR_2 + suffix, nestedRepeatLevel);
                                                    // Increment nestedRepeatLevel when we enter a nested repeat
                                                    if (ORBEON.util.Dom.hasClass(newTemplateNode, "xforms-repeat-begin-end") && templateNode.id.indexOf("repeat-begin-") == 0)
                                                        nestedRepeatLevel++;
                                                }
                                                afterInsertionPoint.parentNode.insertBefore(newTemplateNode, afterInsertionPoint);
                                                ORBEON.xforms.Init.insertedElement(newTemplateNode);
                                            }
                                        }
                                        // Initialize newly added form elements. We don't need to do this for IE, because with
                                        // IE when an element is cloned, the clone has the same event listeners as the original.
                                        if (ORBEON.xforms.Globals.isRenderingEngineWebCore13) {
                                            ORBEON.xforms.Init.registerListenersOnFormElements();
                                            ORBEON.xforms.Init.registerDraggableListenersOnRepeatElements();
                                        }
                                    }

                                    var deleteRepeatTemplateElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "delete-repeat-elements", xmlNamespace);
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
                                            if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
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
                                    var controlElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "control", xmlNamespace);
                                    var controlElementslength = controlElements.length;
                                    // Update control value and MIPs
                                    for (var j = 0; j < controlElementslength; j++) {
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
                                            if (documentElement == null) ORBEON.util.Utils.logMessage ("Can't find element or iteration with ID '" + controlId + "'");
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
                                                newDocumentElement.classname = documentElementClasses.join(" ") + " xforms-static";
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

                                        // Update input control type
                                        if (type != null && ORBEON.util.Dom.hasClass(documentElement, "xforms-input")) {
                                            var isDateType = type == "{http://www.w3.org/2001/XMLSchema}date" || type == "{http://www.w3.org/2002/xforms}date"
                                            var isTimeType = type == "{http://www.w3.org/2001/XMLSchema}time" || type == "{http://www.w3.org/2002/xforms}time"
                                            var isDateTimeType = type == "{http://www.w3.org/2001/XMLSchema}dateTime" || type == "{http://www.w3.org/2002/xforms}dateTime"
                                            //var isBooleanType = type == "{http://www.w3.org/2001/XMLSchema}boolean" || type == "{http://www.w3.org/2002/xforms}boolean"
                                            //var isStringType = type == "" || type == "{http://www.w3.org/2001/XMLSchema}string" || type == "{http://www.w3.org/2002/xforms}string"

                                            function removeAllTypeCSS(element) {
                                                ORBEON.util.Dom.removeClass(element, "xforms-type-date");
                                                ORBEON.util.Dom.removeClass(element, "xforms-type-time");
                                                ORBEON.util.Dom.removeClass(element, "xforms-type-dateTime");
                                            }

                                            // Do clean-up: remove type classes, and second input, if any
                                            removeAllTypeCSS(documentElement);
                                            var firstInput = ORBEON.util.Dom.getChildElementByIndex(documentElement, 0);
                                            removeAllTypeCSS(firstInput);
                                            var secondInput = ORBEON.util.Dom.getChildElementByIndex(documentElement, 1);
                                            if (secondInput != null)
                                                documentElement.removeChild(secondInput);

                                            if (isDateType) {
                                                ORBEON.util.Dom.addClass(documentElement, "xforms-type-date");
                                                ORBEON.util.Dom.addClass(firstInput, "xforms-type-date");
                                            } else if (isTimeType) {
                                                ORBEON.util.Dom.addClass(documentElement, "xforms-type-time");
                                                ORBEON.util.Dom.addClass(firstInput, "xforms-type-time");
                                            } else if (isDateTimeType) {
                                                ORBEON.util.Dom.addClass(documentElement, "xforms-type-dateTime");
                                                ORBEON.util.Dom.addClass(firstInput, "xforms-type-date");
                                                secondInput = document.createElement("input");
                                                secondInput.setAttribute("type", "text");
                                                secondInput.className = "xforms-input-input xforms-type-time"
                                                documentElement.appendChild(secondInput);
                                            }
                                        }

                                        // Handle readonly
                                        if (readonly != null && !isStaticReadonly)
                                            ORBEON.xforms.Controls.setReadonly(documentElement, readonly == "true");

                                        // Update value
                                        if (isControl) {
                                            if (ORBEON.util.Dom.hasClass(documentElement, "xforms-upload")) {
                                                // Additional parameters for upload control
                                                // <xxforms:control id="xforms-control-id"
                                                //    state="empty|file"
                                                //    filename="filename.txt" mediatype="text/plain" size="23kb"/>
                                                var state = ORBEON.util.Dom.getAttribute(controlElement, "state");
                                                var filename = ORBEON.util.Dom.getAttribute(controlElement, "filename");
                                                var mediatype = ORBEON.util.Dom.getAttribute(controlElement, "mediatype");
                                                var size = ORBEON.util.Dom.getAttribute(controlElement, "size");
                                                ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, displayValue, previousServerValue, state, filename, mediatype, size);
                                            } else {
                                                // Other control just have a new value (and optional display value)
                                                ORBEON.xforms.Controls.setCurrentValue(documentElement, newControlValue, displayValue, previousServerValue);
                                            }
                                            // Mark field field as visited when its value changes, unless the new value is given to us when the field becomes relevant
                                            // This is a heuristic that works when a section is shown for the first time, but won't work in many cases. This will be changed
                                            // by handling this on the server-side with custom MIPS.
                                            if (ORBEON.util.Dom.hasClass(documentElement, "xforms-output") && relevant == null) {
                                                ORBEON.util.Dom.addClass(documentElement, "xforms-visited");
                                                if (ORBEON.util.Dom.hasClass(documentElement, "xforms-invalid"))
                                                    ORBEON.util.Dom.addClass(documentElement, "xforms-invalid-visited");
                                            }
                                        }

                                        // Update the required-empty/required-full even if the required has not changed or
                                        // is not specified as the value may have changed
                                        var isRequiredEmpty;
                                        if (!isStaticReadonly) {
                                            isRequiredEmpty = ORBEON.xforms.Controls.updateRequiredEmpty(documentElement, newControlValue);
                                        } else {
                                            isRequiredEmpty = false;
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
                                        ORBEON.xforms.Controls.setValid(documentElement, newValid, isRequiredEmpty);

                                        // After we update classes on textarea, copy those classes on the FCKeditor iframe
                                        if (ORBEON.util.Dom.hasClass(documentElement, "xforms-textarea")
                                                && ORBEON.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                            ORBEON.xforms.Controls.updateHTMLAreaClasses(documentElement);
                                        }
                                    }

                                    // Handle updates to HTML attributes
                                    var attributeElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "attribute", xmlNamespace);
                                    var attributeElementslength = attributeElements.length;
                                    for (var j = 0; j < attributeElementslength; j++) {
                                        var attributeElement = attributeElements[j];
                                        var newAttributeValue = ORBEON.util.Dom.getStringValue(attributeElement);
                                        var forAttribute = ORBEON.util.Dom.getAttribute(attributeElement, "for");
                                        var nameAttribute = ORBEON.util.Dom.getAttribute(attributeElement, "name");
                                        var htmlElement = ORBEON.util.Dom.getElementById(forAttribute);
                                        if (htmlElement != null) {// use case: xhtml:html/@lang but HTML fragment produced
                                            if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
                                                // Hack for common broken IE attributes
                                                if (nameAttribute == "class") {
                                                    htmlElement.className = newAttributeValue;
                                                } else if (nameAttribute == "colspan") {
                                                    htmlElement.colSpan = newAttributeValue;
                                                } else if (nameAttribute == "rowspan") {
                                                    htmlElement.rowSpan = newAttributeValue;
                                                } else if (nameAttribute == "accesskey") {
                                                    htmlElement.accessKey = newAttributeValue;
                                                } else if (nameAttribute == "tabindex") {
                                                    htmlElement.tabIndex = newAttributeValue;
                                                } else {
                                                    htmlElement.setAttribute(nameAttribute, newAttributeValue);
                                                }
                                            } else {
                                                htmlElement.setAttribute(nameAttribute, newAttributeValue);
                                            }
                                        }
                                    }

                                    // Handle text updates
                                    var textElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "text", xmlNamespace);
                                    var textElementslength = textElements.length;
                                    for (var j = 0; j < textElementslength; j++) {
                                        var textElement = textElements[j];
                                        var newTextValue = ORBEON.util.Dom.getStringValue(textElement);
                                        var forAttribute = ORBEON.util.Dom.getAttribute(textElement, "for");
                                        var htmlElement = ORBEON.util.Dom.getElementById(forAttribute);

                                        if (htmlElement != null && htmlElement.tagName.toLowerCase() == "title") {
                                            // Set HTML title
                                            document.title = newTextValue;
                                        }
                                    }

                                    // Model item properties on a repeat item
                                    var repeatIterationElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "repeat-iteration", xmlNamespace);
                                    var repeatIterationElementslength = repeatIterationElements.length;
                                    for (var j = 0; j < repeatIterationElementslength; j++) {
                                        var repeatIterationElement = repeatIterationElements[j];
                                        // Extract data from server response
                                        var repeatId = ORBEON.util.Dom.getAttribute(repeatIterationElement, "id");
                                        var iteration = ORBEON.util.Dom.getAttribute(repeatIterationElement, "iteration");
                                        var relevant = ORBEON.util.Dom.getAttribute(repeatIterationElement, "relevant");
                                        // Remove or add xforms-disabled on elements after this delimiter
                                        if (relevant != null)
                                            ORBEON.xforms.Controls.setRepeatIterationRelevance(repeatId, iteration, relevant == "true" ? true : false);
                                    }

                                    // "div" elements for xforms:switch and xxforms:dialog
                                    var divsElements = ORBEON.util.Dom.getElementsByName(controlValuesElement, "div", xmlNamespace);
                                    var divElementslength = divsElements.length;
                                    for (var j = 0; j < divElementslength; j++) {
                                        var divElement = divsElements[j];

                                        var controlId = ORBEON.util.Dom.getAttribute(divElement, "id");
                                        var visible = ORBEON.util.Dom.getAttribute(divElement, "visibility") == "visible";
                                        var neighbor = ORBEON.util.Dom.getAttribute(divElement, "neighbor");

                                        var yuiDialog = ORBEON.xforms.Globals.dialogs[controlId];
                                        var children = new Array();// elements that are being shown
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
                                                    ORBEON.util.Dom.addClass(cursor, visible ? "xforms-case-selected" : "xforms-case-deselected");
                                                    ORBEON.util.Dom.removeClass(cursor, visible ? "xforms-case-deselected" : "xforms-case-selected");

                                                    children[children.length] = cursor;
                                                }
                                            }
                                        } else {
                                            // This is a dialog
                                            if (visible) {
                                                ORBEON.xforms.Controls.showDialog(controlId, neighbor);
                                                children[0] = ORBEON.util.Dom.getElementById(controlId);
                                            } else {
                                                yuiDialog.hide();
                                                // Fixes cursor Firefox issue; more on this in dialog init code
                                                yuiDialog.element.style.display = "none";
                                                // Remember the server knows that this dialog is closed so we don't close it again later
                                                if (ORBEON.xforms.Globals.dialogMinimalVisible[yuiDialog.element.id])
                                                    ORBEON.xforms.Globals.dialogMinimalVisible[yuiDialog.element.id] = false;
                                            }
                                        }

                                        // After we display divs, we must re-enable the HTML editors.
                                        // This is a workaround for a Gecko (pre-Firefox 3) bug documented at:
                                        // http://wiki.fckeditor.net/Troubleshooting#gecko_hidden_div
                                        if (children.length > 0 && ORBEON.xforms.Globals.isRenderingEngineGecko && !ORBEON.xforms.Globals.isFF3
                                                && ORBEON.xforms.Globals.htmlAreaNames.length > 0) {

                                            for (var childIndex = 0; childIndex < children.length; childIndex++) {
                                                var child = children[childIndex];
                                                var textHTMLElements = YAHOO.util.Dom.getElementsByClassName("xforms-mediatype-text-html", null, child);

                                                // Below we try to find elements with both xforms-mediatype-text-html and xforms-textarea
                                                if (textHTMLElements != null && textHTMLElements.length > 0) {
                                                    for (var htmlElementIndex = 0; htmlElementIndex < textHTMLElements.length; htmlElementIndex++) {
                                                        var htmlElement = textHTMLElements[htmlElementIndex];
                                                        // The code below tries to make sure we are getting an HTML form element
                                                        if (htmlElement.name != null && htmlElement.name != "" && ORBEON.util.Dom.hasClass(htmlElement, "xforms-textarea")) {
                                                            var editor = FCKeditorAPI.GetInstance(htmlElement.name);
                                                            if (editor != null) {
                                                                try {
                                                                    editor.EditorDocument.designMode = "on";
                                                                } catch (e) {
                                                                    // Nop
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
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
                                        if (typeof repeatId == "string") { // Hack because repeatId may be trash when some libraries override Object
                                            var newIndex = newRepeatIndexes[repeatId];
                                            if (typeof newIndex == "string" && newIndex != 0) { // Hack because repeatId may be trash when some libraries override Object
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
                                    var serverEventsElement = actionElement.childNodes[actionIndex];
                                    var delay = ORBEON.util.Dom.getAttribute(serverEventsElement, "delay");
                                    if (delay == null) {
                                        // Case of 2-phase submission: store position of this element, and later when we
                                        // process the submission element, we'll store the value of server-events in the
                                        // $server-events form field, which will be submitted to the server by POSTing
                                        // the form.
                                        serverEventsIndex = actionIndex;
                                    } else {
                                        // Case where we need to send those events to the server with a regular Ajax request
                                        //  after the given delay.
                                        var serverEvents = ORBEON.util.Dom.getStringValue(serverEventsElement);
                                        window.setTimeout(function () {
                                            var event = new ORBEON.xforms.Server.Event(ORBEON.util.Dom.getElementById(formID), null, null, serverEvents, "server-events");
                                            ORBEON.xforms.Server.fireEvents([event]);
                                        }, delay);
                                    }
                                    break;
                                }

                                // Submit form
                                case "submission": {
                                    var submissionElement = actionElement.childNodes[actionIndex];
                                    var showProcess = ORBEON.util.Dom.getAttribute(submissionElement, "show-progress");
                                    var replace = ORBEON.util.Dom.getAttribute(submissionElement, "replace");
                                    var target = ORBEON.util.Dom.getAttribute(submissionElement, "target");
                                    if (replace == null) replace = "all";
                                    if (serverEventsIndex != -1) {
                                        ORBEON.xforms.Globals.formServerEvents[formID].value = ORBEON.util.Dom.getStringValue(actionElement.childNodes[serverEventsIndex]);
                                    } else {
                                        ORBEON.xforms.Globals.formServerEvents[formID].value = "";
                                    }
                                    var requestForm = ORBEON.util.Dom.getElementById(formID);
                                    if (replace == "all") {
                                        // Go to another page
                                        if (showProcess != "false") {
                                            // Display loading indicator unless the server tells us not to display it
                                            newDynamicStateTriggersReplace = true;
                                        }
                                        // We now always use the action set by the client so we don't set requestForm.action
                                        if (target == null) {
                                            // Reset as this may have been changed before by asyncRequest
                                            requestForm.removeAttribute("target");
                                        } else {
                                            // Set the requested target
                                            requestForm.target = target;
                                        }
                                        requestForm.submit();
                                    } else {
                                        // Submit form in the background (pseudo-Ajax request)
                                        YAHOO.util.Connect.setForm(requestForm, true, true);
                                        var callback = {
                                            upload: ORBEON.xforms.Server.handleUploadResponse,
                                            failure: ORBEON.xforms.Server.handleFailure
                                        }
                                        YAHOO.util.Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL, callback);
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
                                            if (resource.charAt(0) != '#' && showProcess != "false")
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

                                // Show help message for specified control
                                case "help": {
                                    var helpElement = actionElement.childNodes[actionIndex];
                                    var controlId = ORBEON.util.Dom.getAttribute(helpElement, "control-id");
                                    var control = ORBEON.util.Dom.getElementById(controlId);
                                    ORBEON.xforms.Controls.showHelp(control);
                                    break;
                                }

                                // Take form offline
                                case "offline": {
                                    var offlineElement = actionElement.childNodes[actionIndex];
                                    var eventsElement = ORBEON.util.Dom.getElementsByName(offlineElement, "events", xmlNamespace)[0];
                                    var mappingsElement = ORBEON.util.Dom.getElementsByName(offlineElement, "mappings", xmlNamespace)[0];
                                    var replayResponse = ORBEON.util.Dom.getStringValue(eventsElement);
                                    var mappings = ORBEON.util.Dom.getStringValue(mappingsElement);
                                    ORBEON.xforms.Offline.takeOffline(replayResponse, formID, mappings);
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
    },

    callUserScript: function(functionName, targetId, observerId) {
        var targetElement = ORBEON.util.Dom.getElementById(targetId);
        var observer = ORBEON.util.Dom.getElementById(observerId);
        var event = { "target" : targetElement };
        var theFunction = eval(functionName);
        theFunction.call(observer, event);
    }
};

YAHOO.util.DDM.mode = YAHOO.util.DDM.INTERSECT;
YAHOO.util.DragDropMgr.preventDefault = false;

ORBEON.xforms.DnD = {

    DraggableItem: function(element, sGroup, config) {
        ORBEON.xforms.DnD.DraggableItem.superclass.constructor.call(this, element, element.tagName, config);
        YAHOO.util.Dom.setStyle(element, "cursor", "move");
    },

    getClosestMatch: function(id) {
        var bestMatchId = YAHOO.util.DDM.getBestMatch(id);
        var bestMatchCount = ORBEON.util.Utils.countOccurences(bestMatchId.id.substring(bestMatchId.id.indexOf(XFORMS_SEPARATOR_1)+1),XFORMS_SEPARATOR_2);
        for(var i = 0 ; i < id.length; i++) {
            var count = ORBEON.util.Utils.countOccurences(id[i].id.substring(id[i].id.indexOf(XFORMS_SEPARATOR_1)+1) ,XFORMS_SEPARATOR_2);
            if(count > bestMatchCount) { //We get a more specific match
                bestMatchId = id[i];
                bestMatchCount = count;
            }
            else if(count == bestMatchCount) { //Both are specific to the same level, let's check the level of overlap
                if(bestMatchId.getEl().overlap && (bestMatchId.getEl().overlap.getArea() < id[i].overlap.getArea())) {
                    bestMatchId = id[i];
                    bestMatchCount = count;
                }
            }
        }
        return bestMatchId.getEl();
    }
};

YAHOO.extend(ORBEON.xforms.DnD.DraggableItem, YAHOO.util.DDProxy, {

    startDrag: function(x, y) {

        var dragElement = this.getDragEl();
        var srcElement = this.getEl();
        var sourceControlElement = ORBEON.util.Dom.getChildElementByClass(srcElement.parentNode, "xforms-repeat-begin-end");
        this.sourceControlID = sourceControlElement.id.substr(13);//we are interested in part after repeat-begin- in id

        var index = srcElement.id.indexOf(XFORMS_SEPARATOR_1); //middot's index
        this.startPosition = (index != -1) ? srcElement.id.substr(index+1) : srcElement.position.substr(srcElement.position.indexOf(XFORMS_SEPARATOR_1)+1);
        YAHOO.util.Dom.setStyle(dragElement, "opacity", 0.67);
        dragElement.innerHTML = srcElement.innerHTML;
        dragElement.className = srcElement.className;
        YAHOO.util.Dom.setStyle(srcElement, "visibility", "hidden");
    },

    onDragDrop: function(e, id) {
        var overElement;

        if ("string" == typeof id) {
            overElement = YAHOO.util.DDM.getElement(id);
        }
        else {
            overElement = ORBEON.xforms.DnD.getClosestMatch(id);
        }
        var srcElement = this.getEl();
        var index = overElement.id.indexOf(XFORMS_SEPARATOR_1); //middot's index
        this.endPosition = (index != -1) ? overElement.id.substr(index+1) : overElement.position.substr(overElement.position.indexOf(XFORMS_SEPARATOR_1)+1);
    },

    endDrag: function(e) {

        var srcElement = this.getEl();
        var proxy = this.getDragEl();

        YAHOO.util.Dom.setStyle(proxy, "visibility", "");
        var motion = new YAHOO.util.Motion(
                proxy, {
            points: {
                to: YAHOO.util.Dom.getXY(srcElement)
            }
        },
                0.2,
                YAHOO.util.Easing.easeOut
                )
        var proxyid = proxy.id;
        var thisid = this.id;

        // Hide the proxy and show the source element when finished with the animation
        motion.onComplete.subscribe(function() {
            YAHOO.util.Dom.setStyle(proxyid, "visibility", "hidden");
            YAHOO.util.Dom.setStyle(thisid, "visibility", "");
        });
        motion.animate();

        if(this.startPosition == null || this.endPosition == null)
            return;
        var draggableRepeatDiv = YAHOO.util.Dom.getElementsByClassName("xforms-draggableRepeat")[0];
        if (draggableRepeatDiv == null) {
            var form;
            for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
                var candidateForm = document.forms[formIndex];
                if (ORBEON.util.Dom.hasClass(candidateForm, "xforms-form")) {
                    form = candidateForm;
                    break;
                }
            }
            var draggableRepeatDiv = document.createElement("div");
            draggableRepeatDiv.className = "xforms-draggableRepeat";
            draggableRepeatDiv.id = this.sourceControlID;
            form.appendChild(draggableRepeatDiv);
        }
        else {
            draggableRepeatDiv.id = this.sourceControlID;
        }
        xformsFireEvents([xformsCreateEventArray(draggableRepeatDiv, "xxforms-dnd", null, null,
                new Array("dnd-start", this.startPosition, "dnd-end", this.endPosition))], false);
    }
});

ORBEON.xforms.Offline = {

    isOnline: true,                                 // True if we are online, false otherwise
    hasGears: false,                                // True if Gears is installed
    gearsDatabase: null,                            // The Gears SQL database
    formStore: null,                                // The Gears ResourceStore
    memoryOfflineEvents: [],                        // Holds events happening offline before they are commited to Gears
    mips: {},                                       // Mapping: control ID -> { required -> XPath,  ... }
    variables: {},                                  // Mapping: variable name -> control ID
    controlIDToVariableName: {},                    // Mapping: control ID -> variable name
    encryptionKey: null,                            // Key that will be used when storing events in the store
    controlValues: null,                            // While offline, contains the latest value of the controls
    typeRegExps: {
        "{http://www.w3.org/2001/XMLSchema}decimal": new RegExp("^[+-]?[0-9]+(\\.[0-9]+)?$", "g"),
        "{http://www.w3.org/2001/XMLSchema}integer": new RegExp("^[+-]?[0-9]+$", "g")
    },

    /**
     * A failed (so far) attempt at trying to figure out if Gears is available and usable by this web application
     * without having Gears ask users if they want to grant the right to this application to use Gears.
     */
    isGearsEnabled: function() {
        try {
            // Try to create factory
            var factory = typeof GearsFactory != "undefined" ? new GearsFactory() : new ActiveXObject("Gears.Factory");
            //return factory.getPermission();
        } catch (e) {
            // ActiveX object not there either, then Gears is not available
            return false;
        }
    },

    /**
     * Google Gears initialization function.
     * Some of the code below is aken gears_init.js which is part of Google Gears.
     */
    init: function() {

        // === START gears_init.js

        // We are already defined. Hooray!
        if (window.google && google.gears) {
            return;
        }

        var factory = null;

        // Firefox
        if (typeof GearsFactory != 'undefined') {
            factory = new GearsFactory();
        } else {
            // IE
            try {
                factory = new ActiveXObject('Gears.Factory');
                // privateSetGlobalObject is only required and supported on WinCE.
                if (factory.getBuildInfo().indexOf('ie_mobile') != -1) {
                    factory.privateSetGlobalObject(this);
                }
            } catch (e) {
                // Safari
                if ((typeof navigator.mimeTypes != 'undefined')
                        && navigator.mimeTypes["application/x-googlegears"]) {
                    factory = document.createElement("object");
                    factory.style.display = "none";
                    factory.width = 0;
                    factory.height = 0;
                    factory.type = "application/x-googlegears";
                    document.documentElement.appendChild(factory);
                }
            }
        }

        // *Do not* define any objects if Gears is not installed. This mimics the
        // behavior of Gears defining the objects in the future.
        if (!factory) {
            return;
        }

        // Now set up the objects, being careful not to overwrite anything.
        //
        // Note: In Internet Explorer for Windows Mobile, you can't add properties to
        // the window object. However, global objects are automatically added as
        // properties of the window object in all browsers.
        if (!window.google) {
            google = {};
        }

        if (!google.gears) {
            google.gears = {factory: factory};
        }

        // === END gears_init.js

        ORBEON.xforms.Offline.hasGears = window.google && google.gears;
        if (ORBEON.xforms.Offline.hasGears) {

            // Create database and create the tables we need if necessary
            var database = google.gears.factory.create("beta.database");
            ORBEON.xforms.Offline.gearsDatabase = database;
            database.open("orbeon.xforms");
            // Table storing the list of the forms taken offline
            database.execute("create table if not exists Offline_Forms (url text, event_response text, form_id text, static_state text, dynamic_state text, mappings text, control_values text, offline_events text)").close();
            // Table storing the current password if any, in encrypted form
            database.execute("create table if not exists Current_Password (encrypted_password text)").close();

            // Create form store
            var localServer = google.gears.factory.create("beta.localserver");
            ORBEON.xforms.Offline.formStore = localServer.createStore("orbeon.form");
        }

        // Define the xxforms:if() XPath function
        FunctionCallExpr.prototype.xpathfunctions["xxforms:if"] = function(ctx) {
            var test = this.args[0].evaluate(ctx).booleanValue();
            return new StringValue(this.args[test ? 1 : 2].evaluate(ctx).stringValue());
        };
        // Define the xxforms:if() XPath function
        FunctionCallExpr.prototype.xpathfunctions["matches"] = function(ctx) {
            var input = this.args[0].evaluate(ctx).stringValue();
            var pattern = this.args[1].evaluate(ctx).stringValue();
            return new BooleanValue(new RegExp(pattern).test(input));
        };
    },

    reset: function() {
        ORBEON.xforms.Offline.init();
        var localServer = google.gears.factory.create("beta.localserver");
        localServer.removeStore("orbeon.form");
        ORBEON.xforms.Offline.gearsDatabase.execute("drop table if exists Events").close();
        ORBEON.xforms.Offline.gearsDatabase.execute("drop table if exists Offline_Forms").close();
        ORBEON.xforms.Offline.gearsDatabase.execute("drop table if exists Current_Password").close();
        window.google = null;
        document.cookie = "orbeon.forms.encryption.password=; path=/; secure";
    },

    /**
     * On page load, check in the database if this form is online or offline
     */
    pageLoad: function() {
        ORBEON.xforms.Offline.init();
        if (ORBEON.xforms.Offline.hasGears) {
            var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select * from Offline_Forms where url = ?",
                    [ window.location.href ]);
            // If we find that this URL is in the store, then we are not online
            ORBEON.xforms.Offline.isOnline = ! resultSet.isValidRow();
            // If we are offline, replay initial events saved in Gears
            if (! ORBEON.xforms.Offline.isOnline) {
                var initialEvents = ORBEON.xforms.Offline._decrypt(resultSet.fieldByName("event_response"), ORBEON.xforms.Offline.getEncryptionKey());
                var controlValues = resultSet.fieldByName("control_values");
                var formID = resultSet.fieldByName("form_id");
                // Get mappings, evaluate JSON, and save mips and variables mapping in attributes
                var mappingsString = resultSet.fieldByName("mappings");
                var mappings = ORBEON.util.String.eval("({" + mappingsString + "})");
                ORBEON.xforms.Offline.mips = mappings.mips;
                ORBEON.xforms.Offline._setVariables(mappings.variables);
                // Replay initial events
                var initialEventsXML = ORBEON.util.Dom.stringToDom(initialEvents);
                ORBEON.xforms.Globals.requestForm = ORBEON.util.Dom.getElementById(formID);
                ORBEON.xforms.Server.handleResponseDom(initialEventsXML, formID);
                // Set control values
                controlValues = ORBEON.xforms.Offline._deserializerControlValues(controlValues);
                for (var controlID in controlValues) {
                    var controlValue = controlValues[controlID];
                    var control = ORBEON.util.Dom.getElementById(controlID);
                    ORBEON.xforms.Controls.setCurrentValue(control, controlValue);
                }
                // Store controlValues in variable, so it can be updated on value change
                ORBEON.xforms.Offline.controlValues = controlValues;
            } else {
                ORBEON.xforms.Offline.controlValues = {};
            }
            resultSet.close();
        }
    },

    getEncryptionKey: function() {
        if (ORBEON.xforms.Offline.encryptionKey != null) {
            // We already have an encryption key created
            return ORBEON.xforms.Offline.encryptionKey;
        } else {
            // Go through cookie looking for password
            var cookieValue = null;
            var cookieArray = document.cookie.split(';');
            for (var cookieIndex = 0; cookieIndex < cookieArray.length; cookieIndex++) {
                var cookie = cookieArray[cookieIndex];
                var cookieName = "orbeon.forms.encryption.password=";
                while (cookie.charAt(0) == " ") cookie = cookie.substring(1, cookie.length);
                if (cookie.indexOf(cookieName) == 0) {
                    cookieValue = cookie.substring(cookieName.length, cookie.length);
                    break;
                }
            }
            if (cookieValue == null || cookieValue == "") {
                // No password found in cookies
                return null;
            } else {
                // Create key based on cookie
                ORBEON.xforms.Offline.encryptionKey = hexToByteArray(cookieValue);
                return ORBEON.xforms.Offline.encryptionKey;
            }
        }
    },

    /**
     * Called when the form is taken offline.
     * Makes sure we have everything in the store so we can run this form while offline.
     */
    takeOffline: function(eventResponse, formID, mappings) {
        ORBEON.xforms.Offline.init();

        // Figure out the list of controls which value we want to keep
        var controlKeepValueIDs = [];

        // Controls for which we go a value in the eventResponse
        /*
            When form is taken offline, no need to store in control_values the values for the controls in
            eventResponse, as the eventResponse is going to be played back when the form is loaded offline.
        var initialEventsXML = ORBEON.util.Dom.stringToDom(eventResponse);
        var actionElement = ORBEON.util.Dom.getChildElementByIndex(initialEventsXML.documentElement, 0)
        var controlValuesElement = ORBEON.util.Dom.getChildElementByIndex(actionElement, 0)
        for (var controlIndex = 0; controlIndex < controlValuesElement.childNodes.length; controlIndex++) {
            var controlElement = controlValuesElement.childNodes[controlIndex];
            if (ORBEON.util.Dom.isElement(controlElement)) {
                var controlId = ORBEON.util.Dom.getAttribute(controlElement, "id");
                controlKeepValueIDs.push(controlId);
            }
        }
        */

        // Controls for which there is a variable defined
        //     We store the latest values for controls for this there is a variable defined, because those are controls
        //     which value we want to make available to the summary page through
        //     ORBEON.xforms.Document.getOfflineControlValues(url).
        var mappingsObject = ORBEON.util.String.eval("({" + mappings + "})");
        ORBEON.xforms.Offline.mips = mappingsObject.mips;
        ORBEON.xforms.Offline._setVariables(mappingsObject.variables);
        for (var variableName in mappingsObject.variables) {
            var controlID = mappingsObject.variables[variableName].value;
            controlKeepValueIDs.push(controlID);
        }

        // Compute the value for those controls
        var controlValues = {};
        for (var controlIndex = 0; controlIndex < controlKeepValueIDs.length; controlIndex++) {
            var controlID = controlKeepValueIDs[controlIndex];
            controlValues[controlID] = ORBEON.xforms.Controls.getCurrentValue(ORBEON.util.Dom.getElementById(controlID));
        }
        var controlValuesString = ORBEON.xforms.Offline._serializeControlValues(controlValues);

        // Remember that we took this form offline
        var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute(
            "insert into Offline_Forms (url, event_response, form_id, static_state, dynamic_state, mappings, control_values, offline_events) values (?, ?, ?, ?, ?, ?, ?, ?)", [
                window.location.href,
                ORBEON.xforms.Offline._encrypt(eventResponse, ORBEON.xforms.Offline.getEncryptionKey()),
                formID,
                ORBEON.xforms.Globals.formStaticState[formID].value,
                ORBEON.xforms.Globals.formDynamicState[formID].value,
                mappings,
                controlValuesString,
                ""]);
        resultSet.close();

        // Capture all the resources this form needs.
        // Here we don't capture the form itself, as we assume this has been done already. (If we were to capture the
        // the form here, when the form is taken offline from a summary page, we would have two requests made to the server.)
        var htmlElements = [].concat(
            YAHOO.util.Selector.query("script"),
            YAHOO.util.Selector.query("link"),
            YAHOO.util.Selector.query("img")
        );
        // Create list of URLs to capture
        var urlsToCapture = [ window.location.href ];
        for (var elementIndex = 0; elementIndex < htmlElements.length; elementIndex++) {
            var element = htmlElements[elementIndex];
            if (YAHOO.lang.isString(element.href) && element.href != "")
                urlsToCapture.push(element.href);
            if (YAHOO.lang.isString(element.src) && element.src != "")
                urlsToCapture.push(element.src);
        }
		// Remove duplicates
		{
			var removeDupplicates = [];
			urlsToCapture = urlsToCapture.sort();
	        for (var urlIndex = 0; urlIndex < urlsToCapture.length; urlIndex++) {
				if (urlIndex == 0 || urlsToCapture[urlIndex] != urlsToCapture[urlIndex - 1])
					removeDupplicates.push(urlsToCapture[urlIndex]);
			}
			urlsToCapture = removeDupplicates;
		}
        // Remove from the list URLs that have been captured already
		{
			var removeAlreadyCaptured = [];
	        for (var urlIndex = 0; urlIndex < urlsToCapture.length; urlIndex++) {
	            var url = urlsToCapture[urlIndex];
	            if (! ORBEON.xforms.Offline.formStore.isCaptured(url))
	                removeAlreadyCaptured.push(url);
	        }
			urlsToCapture = removeAlreadyCaptured;
		}
		// Call Gears to perform the capture
        if (urlsToCapture.length != 0) {
            ORBEON.xforms.Offline.formStore.capture(urlsToCapture, function (url, success, captureId) {
                // When capture is done, mark the form as offline to prevent any event from being sent to the server
                if (url == urlsToCapture[urlsToCapture.length - 1])
                    ORBEON.xforms.Offline.isOnline = false;
            });
        } else {
            ORBEON.xforms.Offline.isOnline = false;
        }
    },

    /**
     * Called when the form is taken back online.
     * Send the events that have been stored in the database to the server.
     */
    takeOnline: function() {
        ORBEON.xforms.Offline.init();

        // Update the static state and dynamic state with the one from the database
        var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select form_id, static_state, dynamic_state, offline_events from Offline_Forms where url = ?", [window.location.href]);
        var formID = resultSet.fieldByName("form_id");
        ORBEON.xforms.Globals.formDynamicState[formID].value = resultSet.fieldByName("dynamic_state");
        ORBEON.xforms.Globals.formStaticState[formID].value = resultSet.fieldByName("static_state");

        // Prepare array with all the events that happened since the form was taken offline
        // Get all events from the database to create events array
        var eventsString = resultSet.fieldByName("offline_events");
        eventsString = ORBEON.xforms.Offline._decrypt(eventsString, ORBEON.xforms.Offline.getEncryptionKey());
        ORBEON.xforms.Offline.isOnline = true; // Need to set this early, otherwise even won't reach the server
        if (eventsString != "") {
            var eventsStringArray = eventsString.split(" ");
            var events = [];
            for (var eventIndex = 0; eventIndex < eventsStringArray.length; eventIndex++) {
                var eventString = unescape(eventsStringArray[eventIndex]);
                // Extract components of event into an array and unescape each one
                var eventArray = eventString.split(" ");
                for (var eventComponentIndex = 0; eventComponentIndex < eventArray.length; eventComponentIndex++)
                    eventArray[eventComponentIndex] = unescape(eventArray[eventComponentIndex]);
                // Create event object
                events.push(new ORBEON.xforms.Server.Event(
                    ORBEON.util.Dom.getElementById(eventArray[0]),
                    eventArray[1],
                    eventArray[2],
                    eventArray[3],
                    eventArray[4],
                    eventArray[5],
                    eventArray[6] == "1",
                    eventArray[7] == "1"));
            }
            // Send all the events back to the server
            ORBEON.xforms.Server.fireEvents(events, false);
        }

        // Remove form from store and database
        ORBEON.xforms.Offline.gearsDatabase.execute("delete from Offline_Forms where url = ?", [ window.location.href ]).close();
        ORBEON.xforms.Offline.formStore.remove(window.location.href);

        // Tell the server we are going online
        ORBEON.xforms.Document.dispatchEvent("$containing-document$", "xxforms-online");
    },

    /**
     * Stores the events (which have just been fired) in the store (instead of the sending the events to the server).
     */
    storeEvents: function(events) {
        function emptyStringIfNull(value) { return value == null ? "" : value; }

        ORBEON.xforms.Offline._runShowingIndicator(function() {
            ORBEON.xforms.Offline.init();

            // Compute new events
            var newEventsString = [];
            for (var eventIndex = 0; eventIndex < events.length; eventIndex++) {
                var event = events[eventIndex];

                // Array containing all the properties of the event we are storing the database
                var eventArray = [
                    emptyStringIfNull(event.form.id),
                    emptyStringIfNull(event.targetId),
                    emptyStringIfNull(event.otherId),
                    emptyStringIfNull(event.value),
                    emptyStringIfNull(event.eventName),
                    event.bubbles ? 1 : 0,
                    event.cancelable ? 1 : 0,
                    event.ignoreErrors ? 1 : 0
                ];
                // Serialize all the information
                var eventString = [];
                for (var eventComponentIndex = 0; eventComponentIndex < eventArray.length; eventComponentIndex++) {
                    if (eventComponentIndex != 0) eventString.push(" ");
                    eventString.push(escape(eventArray[eventComponentIndex]));
                }
                // Add this event to newEventsString
                if (newEventsString.length > 0) newEventsString.push(" ");
                newEventsString.push(escape(eventString.join("")));
            }


            if (newEventsString.length > 0) {
                var resultSet = ORBEON.xforms.Offline.gearsDatabase.execute("select offline_events from Offline_Forms where url = ?", [ window.location.href ]);
                var currentEventsString = resultSet.fieldByName("offline_events");
                if (currentEventsString != "") {
                    currentEventsString = ORBEON.xforms.Offline._decrypt(currentEventsString, ORBEON.xforms.Offline.getEncryptionKey());
                    currentEventsString += " ";
                }
                currentEventsString += newEventsString.join("");
                currentEventsString = ORBEON.xforms.Offline._encrypt(currentEventsString, ORBEON.xforms.Offline.getEncryptionKey());

                // Compute new values of controls
                var controlValuesString = ORBEON.xforms.Offline._serializeControlValues(ORBEON.xforms.Offline.controlValues);

                // Store new events and new value of controls
                ORBEON.xforms.Offline.gearsDatabase.execute("update Offline_Forms set control_values = ?, offline_events = ? where url = ?",
                        [ controlValuesString, currentEventsString, window.location.href ]).close();
            }
        });
    },

    loadFormInIframe: function(url, loadListener) {

        // Remove existing iframe, if it exists
        var offlineIframeId = "orbeon-offline-iframe";
        var offlineIframe = ORBEON.util.Dom.getElementByIdNoCache(offlineIframeId);
        if (offlineIframe != null) {
            offlineIframe.parentNode.removeChild(offlineIframe);
        }

        // Create new iframe
        offlineIframe = document.createElement("iframe");
        offlineIframe.id = offlineIframeId;
        offlineIframe.name = offlineIframeId;
        offlineIframe.style.display = "none";
        document.body.appendChild(offlineIframe);

        // Load URL and call listener when done
        window.childWindowOrbeonReady = function() {
            loadListener(offlineIframe);
        };
        offlineIframe.src = url;
    },

    evaluateMIPs: function() {

        //  Applies a relevance or read-onlyness to inherited controls
        function applyToInherited(control, mips, getter, setter, inherited, value, isRelevance) {
            if (getter(control) != value) {
                setter(control, value);
                if (inherited) {
                    for (var inheritedControlIndex = 0; inheritedControlIndex < mips.relevant.inherited.length; inheritedControlIndex++) {
                        var controlID = inherited[inheritedControlIndex];
                        var inheritedControl = ORBEON.util.Dom.getElementById(controlID);
                        if (inheritedControl == null) {
                            // If we can't find the control, maybe this is a the ID of a group
                            inheritedControl = ORBEON.util.Dom.getElementById("group-begin-" + controlID);
                        }
                        if (isRelevance && inheritedControl == null) {
                            // We have a repeat iteration (this is a special case for relevance where the ID points to a repeat iteration).
                            // This is not handled with ORBEON.xforms.Controls.setRelevant() but rather in ORBEON.xforms.Controls.setRepeatIterationRelevance().
                            var separatorPosition = Math.max(controlID.lastIndexOf(XFORMS_SEPARATOR_1), controlID.lastIndexOf(XFORMS_SEPARATOR_2));
                            var repeatID = controlID.substring(0, separatorPosition);
                            var iteration = controlID.substring(separatorPosition + 1);
                            ORBEON.xforms.Controls.setRepeatIterationRelevance(repeatID, iteration, value);
                        } else {
                            // We have a control
                            setter(inheritedControl, value);
                        }
                    };
                }
            }
        }

        // Evaluates XPath. If there is an error, logs the error and returns null.
        function evaluateXPath(xpath, xpathContext) {
            try {
                return xpathParse(xpath).evaluate(xpathContext).value;
            } catch (e) {
                ORBEON.util.Utils.logMessage("Error evaluating XPath expression " + xpath);
                return null;
            }
        }

        ORBEON.xforms.Offline._runShowingIndicator(function() {

            // Create context once; we then update it for values that are calculated
            var xpathNode = document.createElement("dummy"); // Node used as current node to evaluate XPath expression
            var xpathContext = new ExprContext(xpathNode);
            for (var variableName in ORBEON.xforms.Offline.variables) {
                var controlID = ORBEON.xforms.Offline.variables[variableName].value;
                var variableValue = ORBEON.xforms.Controls.getCurrentValue(ORBEON.util.Dom.getElementById(controlID));
                xpathContext.setVariable(variableName, variableValue);
            }

            // Go over all controls
            for (var controlID in ORBEON.xforms.Offline.mips) {
                var mips = ORBEON.xforms.Offline.mips[controlID];
                var control = ORBEON.util.Dom.getElementById(controlID);
                var controlValue = ORBEON.xforms.Controls.getCurrentValue(control);

                // Update xpathContext with the value of the current control
                ORBEON.util.Dom.setStringValue(xpathNode, controlValue);

                // Calculate
                if (mips.calculate) {
                    var newValue = evaluateXPath(mips.calculate.value, xpathContext);
                    if (newValue != null) {
                        // Update value
                        ORBEON.xforms.Controls.setCurrentValue(control, newValue);
                        // Change value of variable
                        var variableName = ORBEON.xforms.Offline.controlIDToVariableName[controlID];
                        if (variableName != null) {
                            xpathContext.setVariable(variableName, newValue);
                        }
                    }
                }

                // Constraint
                var isValid = true;
                if (mips.constraint) {
                    var constraint = evaluateXPath("boolean(" + mips.constraint.value + ")", xpathContext);
                    if (constraint != null)
                    isValid = isValid && constraint;
                }

                // Required
                var requiredButEmpty;
                if (mips.required) {
                    var required = evaluateXPath(mips.required.value, xpathContext);
                    if (required != null)
                    requiredButEmpty = controlValue == "" && required;
                }

                // Relevant
                if (mips.relevant) {
                    var isRelevant = evaluateXPath("boolean(" + mips.relevant.value + ")", xpathContext);
                    if (isRelevant != null)
                        applyToInherited(control, mips, ORBEON.xforms.Controls.isRelevant, ORBEON.xforms.Controls.setRelevant, mips.relevant.inherited, isRelevant, true);
                }

                // Readonly
                if (mips.readonly) {
                    var isReadonly = evaluateXPath("boolean(" + mips.readonly.value + ")", xpathContext);
                    if (isReadonly != null)
                        applyToInherited(control, mips, ORBEON.xforms.Controls.isReadonly, ORBEON.xforms.Controls.setReadonly, mips.readonly.inherited, isReadonly, false);
                }

                // Type
                if (mips.type) {
                    var regExp = ORBEON.xforms.Offline.typeRegExps[mips.type.value];
                    if (regExp != null) {
                        if (! controlValue.match(regExp))
                            isValid = false;
                    }
                }

                ORBEON.xforms.Controls.setValid(control, isValid ? "true" : "false", requiredButEmpty);
            }
        });
    },

    _serializeControlValues: function(controlValues) {
        var controlValuesString = [];
        for (controlID in controlValues) {
            if (controlValuesString.length > 0) controlValuesString.push(" ");
            controlValuesString.push(escape(controlID));
            controlValuesString.push(" ");
            controlValuesString.push(escape(controlValues[controlID]));
        }
        controlValuesString = ORBEON.xforms.Offline._encrypt(controlValuesString.join(""), ORBEON.xforms.Offline.getEncryptionKey());
        return controlValuesString;
    },

    _deserializerControlValues: function(controlValuesString) {
        controlValuesString = ORBEON.xforms.Offline._decrypt(controlValuesString, ORBEON.xforms.Offline.getEncryptionKey());
        var controlValuesArray = controlValuesString.split(" ");
        var controlValues = {};
        for (var controlIndex = 0; controlIndex < controlValuesArray.length / 2; controlIndex++) {
            var controlID = unescape(controlValuesArray[controlIndex * 2]);
            var controlValue = unescape(controlValuesArray[controlIndex * 2 + 1]);
            controlValues[controlID] = controlValue;
        }
        return controlValues;
    },

    _setVariables: function(variables) {
        ORBEON.xforms.Offline.variables = variables;
        var controlIDToVariableName = {};
        for (var name in variables) {
            var controlID = variables[name].value;
            controlIDToVariableName[controlID] = name;
        }
        ORBEON.xforms.Offline.controlIDToVariableName = controlIDToVariableName;
    },

    /**
     * Encrypt text with the offline key
     */
    _encrypt: function(text, key) {
        return key == null ? text :
               text == "" ? text :
               byteArrayToHex(rijndaelEncrypt(text, key, "ECB"));
    },

    /**
     * Decrypt text with the offline key
     */
    _decrypt: function(text, key) {
        return key == null ? text :
               text == "" ? text :
               byteArrayToString(rijndaelDecrypt(hexToByteArray(text), key, "ECB"));
    },

    _runShowingIndicator: function(f) {
        // Show loading indicator
        xformsDisplayIndicator("loading");
        // Use timeout function to give a change to the browser to show the loading indicator before we continue with evaluation of the MIPS
        window.setTimeout(function() {
            // Run actual code
            f();
            // Hide loading indicator
            xformsDisplayIndicator("none");
        }, ORBEON.util.Utils.getProperty(INTERNAL_SHORT_DELAY_PROPERTY));
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
 *
 * @param repeatId      Can be either a pure repeat ID, such as "todo" or an ID that contains information of its
 *                      position relative to its parents, such as "todo.1". The former happens when we handle an
 *                      event such as <xxf:repeat-index id="todo" old-index="4" new-index="6"/>. In this case
 *                      "todo" means the "todo at 'index' in the current todo list". The latter happens when we handle
 *                      an event such as <xxf:repeat-iteration id="todo.1" relevant="false" iteration="10"/>, which
 *                      does not necessarily apply to the current "todo".
 * @param index
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
        if (navigator.appName.indexOf("Microsoft") != -1) {
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
        debugDiv.style.width = ORBEON.util.Utils.getProperty(DEBUG_WINDOW_WIDTH_PROPERTY) + "px";
        debugDiv.style.left = visibleWidth - (ORBEON.util.Utils.getProperty(DEBUG_WINDOW_WIDTH_PROPERTY) + 50) + "px";
        debugDiv.style.height = ORBEON.util.Utils.getProperty(DEBUG_WINDOW_HEIGHT_PROPERTY) + "px";
        debugDiv.style.top = visibleHeight - (ORBEON.util.Utils.getProperty(DEBUG_WINDOW_HEIGHT_PROPERTY) + 20) + "px";

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
    // Form can be null if an incremental event happens around the same time as an non-incremental event. Both were sent
    // but the incremental run an executeNextRequest after the response arrived. If the response replaces the HTML,
    // then the form will be null.
    if (form != null) {
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
    var keyValues = clientState.value == "" ? new Array() : clientState.value.split("&");
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
    var newValue = ORBEON.xforms.Controls.getCurrentValue(target);
    var valueChanged = newValue != target.previousValue;
    // We don't send value change events for the XForms upload control
    var isUploadControl = ORBEON.util.Dom.hasClass(target, "xforms-upload");
    if (valueChanged && !isUploadControl) {
        target.previousValue = newValue;
        var events = new Array(xformsCreateEventArray(target, "xxforms-value-change-with-focus-change", newValue, other));
        var incremental = other == null && ORBEON.util.Dom.hasClass(target, "xforms-incremental");
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
        var additionalAttribs = event[4];
        var form = target == undefined ? null : ORBEON.xforms.Controls.getForm(target);

        // If a target was specified but we can't find the form for that target, then it means that the target is not
        // in the page anymore. It must have been removed and we don't want to send information about this
        // target to the server.
        if (target == undefined || form != null) {
            newEvents.push(new ORBEON.xforms.Server.Event(
                    target == undefined ? null : ORBEON.xforms.Controls.getForm(target),
                    target == undefined ? null : target.id,
                    other == undefined ? null : other.id,
                    value, eventName, null, null,
                    additionalAttribs ==  undefined ? null : additionalAttribs));
        }
    }
    ORBEON.xforms.Server.fireEvents(newEvents, incremental);
}

function xformsCreateEventArray(target, eventName, value, other, additionalAttribs) {
    return new Array(target, eventName, value, other, additionalAttribs);
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

    // Work around for bug #308473 (http://tinyurl.com/2jv62f)
    //
    // About the bug:
    //
    // On IE6 (but not IE7), when a FCK editor is used on the page, the first time we change a class on a tr around
    // the drop-down a "death flash" happens and if the drop-down was open it gets closed and its value is set to
    // empty string. This creates the "death flash" right when the page is first loaded instead of doing it later,
    // to avoid the issue described in bug #308473.
    //
    // Issue with the fix:
    //
    // On some deployments with some versions of IE this is causing the page to load and become blank until users
    // move around the mouse on the page. Since this is caused by the FCK editor and we will be moving to using the
    // YUI RTE, we will leave this open for now.
    //
    // When to remove this:
    //
    // This comment can be removed after the switch to YUI RTE.
    //
    //if (ORBEON.xforms.Globals.isRenderingEngineTrident)
    //    document.body.className = document.body.className;
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
                && ORBEON.xforms.Globals.isRenderingEngineTrident) {
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
    YAHOO.util.Event.onDOMReady(ORBEON.xforms.Init.document);
    ORBEON.xforms.Globals.debugLastTime = new Date().getTime();
    ORBEON.xforms.Globals.lastEventSentTime = new Date().getTime();
}
