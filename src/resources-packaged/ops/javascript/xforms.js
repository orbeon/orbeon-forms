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

/**
 * Constants
 */
var XFORMS_SEPARATOR_1 = "\xB7";
var XFORMS_SEPARATOR_2 = "-";
var XFORMS_SERVER_PATH = "/xforms-server";
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var PATH_TO_JAVASCRIPT_1 = "/ops/javascript/xforms";
var PATH_TO_JAVASCRIPT_2 = XFORMS_SERVER_PATH + "/";
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var ATTRIBUTE_TYPE = document.createAttribute("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;
var XFORMS_REGEXP_CR = new RegExp("\\r", "g");
var XFORMS_REGEXP_SINGLE_QUOTE = new RegExp("'", "g");
var XFORMS_REGEXP_OPEN_ANGLE = new RegExp("<", "g");
var XFORMS_REGEXP_CLOSE_ANGLE = new RegExp(">", "g");
var XFORMS_REGEXP_AMPERSAND = new RegExp("&", "g");
var XFORMS_WIDE_TEXTAREA_MIN_ROWS = 5;
var DEFAULT_LOADING_TEXT = "Loading...";

// These variables are not set by default, but if set will be used by this code:
//
//     YUI_RTE_CUSTOM_CONFIG
//     USER_LANGUAGE

(function() {

    /**
     * Shortcuts
     */
    var YD = YAHOO.util.Dom;
    var OD;
    _.defer(function() {
        OD = ORBEON.util.Dom;
    });

    /**
     * Functions we add to the awesome Underscore.js
     */
    _.mixin({

        /**
         * Allows functions not part part Underscore.js to be used in chains.
         *
         * @see <a href="http://jsfiddle.net/avernet/uVnu2/">Example using take()</a>
         *
         * @param {*}               obj             Object passed as a parameter to the object
         * @param {function(*): *}  interceptor     Function applied to the current object
         * @param {?*}              context         Optional object on which the function is applied
         */
        take: function(obj, interceptor, context) {
            return interceptor.call(context, obj);
        },

        /**
         * This function is an alternative to using if/then/else, and is (very!) loosely inspired by Scala's pattern
         * matching (and very far from being as powerful!).
         *
         * @see <a href="http://programming-scala.labs.oreilly.com/ch03.html#PatternMatching">Pattern matching in Scala</a>
         * @see <a href="http://jsfiddle.net/avernet/NpCmv/">Example using match()</a>
         *
         * @param obj
         */
        match: function(obj) {
            function compareMaybe(f) { return _.isFunction(f) ? f(obj) : f == obj; }
            function applyMaybe(f) { return _.isFunction(f) ? f(obj) : f; }
            for (var i = 1; i < arguments.length - 1; i = i + 2)
                if (compareMaybe(arguments[i])) return applyMaybe(arguments[i+1]);
            return arguments.length % 2 == 0 ? applyMaybe(arguments[arguments.length - 1]) : obj;
        },

        returns: function(obj) {
            return _.bind(_.identity, this, obj);
        }
    });

    this.ORBEON = this.ORBEON || {};
    this.ORBEON.onJavaScriptLoaded = new YAHOO.util.CustomEvent("javascript-loaded");

    this.ORBEON.util = {

        /**
         * The IE version of those methods does not store anything in the
         * elements as this has some negative side effects like IE reloading
         * background images set with CSS on the element.
         */
        IEDom: {
            /**
             * Orbeon version of getting Elements by Name in IE
             */
            getElementsByName: function(element, localName, namespace) {
                return element.getElementsByTagName(namespace == null ? localName : namespace + ":" + localName);
            }
        },

        /**
         * The hasClass, addClass and removeClass methods use a cache of the
         * classes for a give element for quick lookup. After having parsed the
         * className a first time we store that information in the orbeonClasses
         * map on the given element.
         */
        MozDom: {
            /**
             * Optimized version of getting Elements by Name on Mozilla
             * Firefox 2 assumes there are no other elements with the
             * same local name that are in a different namespace. This has
             * been fixed in Firefox 3 / Gecko 1.9. See https://bugzilla.mozilla.org/show_bug.cgi?id=206053
             */
            getElementsByName: function(element, localName, namespace) {
                return element.getElementsByTagName((ORBEON.xforms.Globals.isFF3OrNewer && namespace != null ? namespace + ":" : "") + localName);
            }
        },

        /**
         *  Utilities to deal with the DOM that supplement what is provided by YAHOO.util.Dom.
         */
        Dom: {

            ELEMENT_TYPE: 1,

            isElement: function(node) {
                return node.nodeType == this.ELEMENT_TYPE;
            },

            /**
             * In February 2010, we determined that the particular bug we work around in safeGetElementById() cannot
             * happen in the vast majority of the cases. So instead, we call document.getElementById() directly.
             * Between February 2010 and October 2010, we have been calling here YAHOO.util.Dom.get() which was pretty
             * much directly calling document.getElementById(). In October 2010, we upgraded to YUI 2.8. YUI 2.8 version
             * of ORBEON.util.Dom.get() started working around IE's bug, sometimes incorrectly returning null on IE in a
             * case where an element with the specified id did exist. So we are now here calling document.getElementById()
             * directly instead of going through YUI.
             *
             * @param {string} controlId
             */
            get: function(controlId) {
                return document.getElementById(controlId);
            },

            /**
             * This is a "safe" version of document.getElementById(), which gets around bug in IE and Opera 8.2 where
             * getElementById() by return an element with name equal to the specified id (instead of id equal to the
             * specified id), or an element which before being cloned used to have that id.
             *
             * Even when IE is being used, we almost never fall in one of the cases where we must use this method. So
             * unless it has been determined that this method must be used, code should use the regular getElementById().
             *
             * Also see: http://www.csb7.com/test/ie_getelementbyid_bug/index.php
             *
             * @param {!string} controlId
             */
            safeGet: function(controlId) {
                var result = document.getElementById(controlId);
                if (result && (result.id != controlId) && document.all) {
                    result = null;
                    var documentAll = document.all[controlId];
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

            /**
             * Return null when the attribute is not there.
             */
            setAttribute: function(element, name, value) {

                // IE doesn't support setting the value of some attributes with setAttribute(). So for those attributes,
                // we set the attribute directly and use this code for all the browser, to avoid having different branches
                // run for different browsers. This list comes from jQuery (see comments for exceptions).
                var ATTRIBUTE_SLOTS =  {
                    "cellspacing": "cellSpacing",
                    "class": "className",
                    "colspan": "colSpan",
                    "for": "htmlFor",
                    "frameborder": "frameBorder",
                    "maxlength": "maxLength",
                    "readonly": "readOnly",
                    "rowspan": "rowSpan",
                    "tabindex": "tabIndex",
                    "usemap": "useMap",
                    "accesskey": "accessKey", // Not sure why jQuery doesn't include 'accesskey', but includes 'tabindex'
                    "type": "type"            // jQuery is doing further processing for 'type'
                };

                if (ATTRIBUTE_SLOTS[name]) {

                    // If the object property is of type integer and the value is an empty string, skip setting the value
                    // to avoid an error on IE. This is a test that, surprisingly, jQuery doesn't do, which means that with
                    // jQuery you might get different results when setting the value of an attribute depending on the
                    // browser. This is particularly important for us as the value of attributes can come from AVTs, which
                    // can become empty if they loose their evaluation context.
                    var key = ATTRIBUTE_SLOTS[name];
                    if (! (value == "" && YAHOO.lang.isNumber(element[key])))
                        element[key] = value;

                } else if (name == "name" && element.tagName.toLowerCase() == "input") {

                    // Here we handle a bug in IE6 and IE7 where the browser doesn't support changing the name of form elements.
                    // If changing the name doesn't work, we create the whole element with a new name and insert it into the DOM.
                    // This behavior is documented by Microsoft: http://msdn.microsoft.com/en-us/library/ms534184(VS.85).aspx

                    // Try to change the name
                    element.setAttribute(name, value);

                    // Check if changing the name worked. For this we need access to the form for this element, which
                    // we only have if the element is inside a form (it won't if the element is detached from the document).
                    // If we can't find the form for this element, we just hope for the best.
                    if (YAHOO.lang.isObject(element.form)) {
                        var controlsWithName = element.form[value];
                        var nameChangeSuccessful = false;
                        if (controlsWithName && YAHOO.lang.isNumber(controlsWithName.length)) {
                            // Get around issue with YAHOO.lang.isArray, as reported in YUI list:
                            // http://www.nabble.com/YAHOO.lang.isArray-doesn%27t-recognize-object-as-array-td22694312.html
                            for (var controlIndex = 0; controlIndex < controlsWithName.length; controlIndex++) {
                                if (controlsWithName[controlIndex] == element)
                                    nameChangeSuccessful = true;
                            }
                        } else if (YAHOO.lang.isObject(controlsWithName)) {
                            if (controlsWithName == element)
                                nameChangeSuccessful = true;
                        }

                        if (!nameChangeSuccessful) {
                            // Get HTML for the element
                            var elementSource = element.outerHTML;
                            // Remove the name attribute
                            elementSource = elementSource.replace(new RegExp(" name=.*( |>)", "g"), "$1");
                            // Add the name attribute with the new value
                            elementSource = elementSource.replace(new RegExp(">"), " name=\"" + value + "\">");
                            var newElement = document.createElement(elementSource);
                            // Replacing current element by newly created one
                            element.parentNode.insertBefore(newElement, element);
                            element.parentNode.removeChild(element);
                        }
                    }
                } else {
                    element.setAttribute(name, value);
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
                    if (ORBEON.util.Dom.isElement(child) && YAHOO.util.Dom.hasClass(child, clazz)) {
                        return child;
                    }
                }
                return null;
            },

            getChildElementsByClass: function(parent, clazz) {
                var nodes = [];
                for (var i = 0; i < parent.childNodes.length; i++) {
                    var child = parent.childNodes[i];
                    if (ORBEON.util.Dom.isElement(child) && YAHOO.util.Dom.hasClass(child, clazz)) {
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
                    return (new DOMParser()).parseFromString(xmlString, "application/xml");
                } else if (window.ActiveXObject) {
                    var dom = new ActiveXObject("Microsoft.XMLDOM");
                    dom.async = "false";
                    dom.loadXML(xmlString);
                    return dom;
                }
                return null;
            },

            /**
             * A safe way to focus on a form element, as IE can complains when we try to set the focus on non-visible
             * control. This can happen because of error in the XForms code, or in cases where we try to restore
             * the focus to a control which in the meantime has disappeared or became readonly. The precise IE error
             * we would get if we didn't catch the exception would be: "Can't move focus to the control because it is
             * invisible, not enabled, or if a type that does not accept the focus."
             *
             * @param {HTMLElement} element
             * @return void
             */
            focus: function(element) {
                try { element.focus(); }
                catch (e) { /* NOP */ }
            },

            clearUploadControl: function(uploadElement) {
                var inputElement = YAHOO.util.Dom.getElementsByClassName("xforms-upload-select", null, uploadElement)[0];
                var parentElement = inputElement.parentNode;
                var newInputElement = document.createElement("input");
                YAHOO.util.Dom.addClass(newInputElement, inputElement.className);
                newInputElement.setAttribute("type", inputElement.type);
                newInputElement.setAttribute("name", inputElement.name);
                newInputElement.setAttribute("size", inputElement.size);
                newInputElement.setAttribute("unselectable", "on");// the server sets this, so we have to set it again
                parentElement.replaceChild(newInputElement, inputElement);
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
            },

            /**
             * Nudge element after a short delay for IE6/7 to force IE to "do the right thing".
             */
            nudgeAfterDelay: function(element) {
                if (YAHOO.env.ua.ie != 0 && YAHOO.env.ua.ie <= 7) {
                    var tables = element.getElementsByTagName("table");
                    window.setTimeout(function() {
                        element.className = element.className;
                        for (var tableIndex = 0; tableIndex < tables.length; tableIndex++) {
                            var table = tables[tableIndex];
                            table.className = table.className;
                        }
                    }, ORBEON.util.Properties.internalShortDelay.get());
                }
            },

            /**
             * Similar to root.getElementsByTagName(tagName), but:
             *
             *    1. Returns root if root.tagName == tagName.
             *    2. Returns only one element (the first if there are many).
             *    3. Can take an array of tagName if there are alternatives.
             *
             * @param {Element}                 root            Root node from which we start the search
             * @param {string|Array.<string>}   tagNameOrArray  Tag name we're looking for
             */
            getElementByTagName: function(root, tagNameOrArray) {
                var result = _.isArray(tagNameOrArray)
                    ? _(tagNameOrArray).chain()
                        .map(_.bind(arguments.callee, null, root))
                        .compact()
                        .first()
                        .value()
                    : root.tagName.toLowerCase() == tagNameOrArray
                        ? root
                        : root.getElementsByTagName(tagNameOrArray)[0];
                return _.isUndefined(result) ? null : result;
            },

            isAncestorOrSelfHidden: function(element) {
                while (true) {
                    if (element == null) return false;
                    if (! YAHOO.lang.isUndefined(element.style) && YAHOO.util.Dom.getStyle(element, "display") == "none") return true;
                    element = element.parentNode;
                }
            },

            /**
             * Applies a function to all the HTML form elements under a root element, including the root if it is a
             * form element.
             */
            applyOnFormElements: function(root, fn, obj, overrideContext) {
                var FORM_TAG_NAMES = ["input", "textarea", "select", "button"];
                ORBEON.util.Utils.apply(FORM_TAG_NAMES, function(tagName) {
                    if (root.tagName.toLowerCase() == tagName)
                        if (overrideContext) fn.call(obj, root); else fn(root);
                    ORBEON.util.Utils.apply(root.getElementsByTagName(tagName), fn, obj, overrideContext);
                }, null, false);
            },

            /**
             * Test a function ancestor-or-self::* and returns true as soon as the function returns true, or false of the
             * function always returns false.
             */
            existsAncestorOrSelf: function(node, fn) {
                while (true) {
                    if (fn(node)) return true;
                    node = node.parentNode;
                    if (node == null || node == document) break;
                }
                return false;
            }
        },

        /**
         * General purpose methods on string
         */
        String: {
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
             * Escape text that appears in an HTML attribute which we use in an innerHTML.
             */
            escapeAttribute: function(text) {
                return ORBEON.util.String.replace(text, '"', '&quot;');
            },

            /**
             * Escape text that appears in an HTML attribute which we use in an innerHTML.
             */
            escapeHTMLMinimal: function(text) {
                text = ORBEON.util.String.replace(text, '&', '&amp;');
                return ORBEON.util.String.replace(text, '<', '&lt;');
            },

            /**
             * Checks if a string ends with another string.
             */
            endsWith: function(text, suffix) {
                var index = text.lastIndexOf(suffix);
                return index != -1 && index + suffix.length == text.length;
            },

            normalizeSerializedHTML: function(text) {
                // Mmmh, the caller might pass an integer, e.g. for the slider. Not sure if fixing this here is the best way.
                if (typeof text == "string") {
                    return text.replace(XFORMS_REGEXP_CR, "");
                } else {
                    return text;
                }
            }
        },

        /**
         * Utility functions dealing with dates and times.
         *
         * Credits - This is based and inspired by:
         *     Simon Willison's Magic date parser (http://simon.incutio.com/archive/2003/10/06/betterDateInput)
         *     Stoyan Stefanov's Magic time parsing (http://www.phpied.com/javascript-time-input/)
         */
        DateTime: {

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

            jsDateToFormatDisplayTime: function(jsDate) {
                var formatInputTime = ORBEON.util.Properties.formatInputTime.get();
                if (formatInputTime == "[H]:[m]:[s]") {
                    // EU time
                    return jsDate.getHours() + ":"
                            + ORBEON.util.DateTime._padAZero(jsDate.getMinutes()) + ":"
                            + ORBEON.util.DateTime._padAZero(jsDate.getSeconds());
                } else if (formatInputTime == "[H]:[m]") {
                    // EU time no seconds
                    return jsDate.getHours() + ":"
                            + ORBEON.util.DateTime._padAZero(jsDate.getMinutes());
                } else {
                    // US time: [h]:[m]:[s] [P] or [h]:[m]:[s] [P,2-2]
                    var amPm = ORBEON.util.String.endsWith(formatInputTime, "-2]")
                        ? (jsDate.getHours() < 12 ? " am" : " pm")
                        : (jsDate.getHours() < 12 ? " a.m." : " p.m.");
                    return (jsDate.getHours() == 12 ? 12 : jsDate.getHours() % 12) + ":"
                            + ORBEON.util.DateTime._padAZero(jsDate.getMinutes()) + ":"
                            + ORBEON.util.DateTime._padAZero(jsDate.getSeconds())
                            + amPm;
                }
            },

            jsDateToFormatDisplayDate: function(jsDate) {
                var inputDateFormat = ORBEON.util.Properties.formatInputDate.get(); // e.g. "[D01].[M01].[Y]"
                var inputDateFormatParts = inputDateFormat.split(new RegExp("[\\[\\]]")); // e.g. ["", "D01", ".", "M01", ".", "Y", ""]
                var result = [];
                for (var inputDateFormatPartIndex = 0; inputDateFormatPartIndex < inputDateFormatParts.length; inputDateFormatPartIndex++) {
                    var inputDateFormatPart = inputDateFormatParts[inputDateFormatPartIndex];

                    function padAndPush(dateOperation) {
                        var part = dateOperation.apply(jsDate).toString();
                        if (inputDateFormatPart.indexOf("01") == 1 && part.length < 2) part = "0" + part;
                        result.push(part);
                    }

                    if (inputDateFormatPart == "") ; // NOP: the first and last part will be an empty string
                    else if (inputDateFormatPart.indexOf("D") == 0) padAndPush(jsDate.getDate);
                    else if (inputDateFormatPart.indexOf("M") == 0) padAndPush(function() { return this.getMonth() + 1; });
                    else if (inputDateFormatPart.indexOf("Y") == 0) padAndPush(jsDate.getFullYear);
                    else result.push(inputDateFormatPart);
                }
                return result.join("");
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
                {   re: /^now$/i,
                    handler: function() {
                        return new Date();
                    }
                },
                // 12:34:56 p.m.
                {   re: /^(\d{1,2}):(\d{1,2}):(\d{1,2}) ?(p|pm|p\.m\.)$/,
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
                // 12:34 p.m.
                {   re: /^(\d{1,2}):(\d{1,2}) ?(p|pm|p\.m\.)$/,
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
                // 12 p.m.
                {   re: /^(\d{1,2}) ?(p|pm|p\.m\.)$/,
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
                // 12:34:56 (a.m.)
                {   re: /^(\d{1,2}):(\d{1,2}):(\d{1,2}) ?(a|am|a\.m\.)?$/,
                    handler: function(bits) {
                        var d = new Date();
                        var h = parseInt(bits[1], 10);
                        if (! YAHOO.lang.isUndefined(bits[4]) && bits[4] != "") h = h % 12;
                        d.setHours(h);
                        d.setMinutes(parseInt(bits[2], 10));
                        d.setSeconds(parseInt(bits[3], 10));
                        return d;
                    }
                },
                // 12:34 (a.m.)
                {   re: /^(\d{1,2}):(\d{1,2}) ?(a|am|a\.m\.)?$/,
                    handler: function(bits) {
                        var d = new Date();
                        var h = parseInt(bits[1], 10);
                        if (! YAHOO.lang.isUndefined(bits[3]) && bits[3] != "") h = h % 12;
                        d.setHours(h);
                        d.setMinutes(parseInt(bits[2], 10));
                        d.setSeconds(0);
                        return d;
                    }
                },
                // 12 (a.m.)
                {   re: /^(\d{1,2}) ?(a|am|a\.m\.)?$/,
                    handler: function(bits) {
                        var d = new Date();
                        var h = parseInt(bits[1], 10);
                        if (! YAHOO.lang.isUndefined(bits[2]) && bits[2] != "") h = h % 12;
                        d.setHours(h);
                        d.setMinutes(0);
                        d.setSeconds(0);
                        return d;
                    }
                },
                // hhmmss
                {   re: /^(\d{1,6})$/,
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

                // NOTE: Date() months are 0-based
                // Create date in one shot when possible, because if you set year, then month, then day, sometimes the result is incorrect!

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
                        return ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._currentYear, ORBEON.util.DateTime._parseMonth(bits[2]), parseInt(bits[1], 10));
                    }
                },
                // 4th Jan 2003
                {   re: /^(\d{1,2})(?:st|nd|rd|th)? (\w+),? (\d{2,4})$/i,
                    handler: function(bits) {
                        return ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._parseYear(bits[3]), ORBEON.util.DateTime._parseMonth(bits[2]), parseInt(bits[1], 10));
                    }
                },
                // Jan 4th
                {   re: /^(\w+) (\d{1,2})(?:st|nd|rd|th)?$/i,
                    handler: function(bits) {
                        return ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._currentYear, ORBEON.util.DateTime._parseMonth(bits[1]), parseInt(bits[2], 10));
                    }
                },
                // Jan 4th 2003
                {   re: /^(\w+) (\d{1,2})(?:st|nd|rd|th)?,? (\d{2,4})$/i,
                    handler: function(bits) {
                        return ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._parseYear(bits[3]), ORBEON.util.DateTime._parseMonth(bits[1]), parseInt(bits[2], 10));
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
                // mm/dd/yyyy (American style) or dd/mm/yyyy (European style)
                {   re: /^(\d{1,2}).(\d{1,2}).(\d{2,4})$/,
                    handler: function(bits) {
                        var d;
                        if (ORBEON.util.Properties.formatInputDate.get().indexOf("[D") == 0) {
                            // Day first
                            d = ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._parseYear(bits[3]), parseInt(bits[2], 10) - 1, parseInt(bits[1], 10));
                        } else {
                            // Month first
                            d = ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._parseYear(bits[3]), parseInt(bits[1], 10) - 1, parseInt(bits[2], 10));
                        }
                        return d;
                    }
                },
                // mm/dd (American style without year) or dd/mm (European style without year)
                {   re: /^(\d{1,2}).(\d{1,2})$/,
                    handler: function(bits) {
                        var d;
                        if (ORBEON.util.Properties.formatInputDate.get().indexOf("[D") == 0) {
                            d = ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._currentYear, parseInt(bits[1], 10) - 1, parseInt(bits[2], 10));
                        } else {
                            d = ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._currentYear, parseInt(bits[2], 10) - 1, parseInt(bits[1], 10));
                        }
                        return d;
                    }
                },
                // yyyy-mm-dd (ISO style)
                {   re: /(^\d{4})-(\d{1,2})-(\d{1,2})(Z|([+-]\d{2}:\d{2}))?$/, // allow for optional trailing timezone
                    handler: function(bits) {
                        return ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._parseYear(bits[1]), parseInt(bits[2], 10) - 1, parseInt(bits[3], 10));
                    }
                }
            ],

            /**
             * Helper function to pad a leading zero to an integer
             * if the integer consists of one number only.
             * This function s not related to the algo, it's for
             * getReadable()'s purposes only.
             *
             * @param s An integer value
             * @return string The input padded with a zero if it's one number int
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
                var matches = _.filter(ORBEON.util.DateTime._monthNames, function(item) {
                    return new RegExp("^" + month, "i").test(item);
                });
                if (matches.length == 0) {
                    throw new Error("Invalid month string");
                }
                if (matches.length > 1) {
                    throw new Error("Ambiguous month");
                }
                return _.indexOf(ORBEON.util.DateTime._monthNames, matches[0]);
            },

            /* Same as parseMonth but for days of the week */
            _parseWeekday: function(weekday) {
                var matches = _.filter(ORBEON.util.DateTime._weekdayNames, function(item) {
                    return new RegExp("^" + weekday, "i").test(item);
                });
                if (matches.length == 0) {
                    throw new Error("Invalid day string");
                }
                if (matches.length > 1) {
                    throw new Error("Ambiguous weekday");
                }
                return _.indexOf(ORBEON.util.DateTime._weekdayNames, matches[0]);
            },

            _currentYear: new Date().getFullYear(),
            _parseYear: function(year) {
                year = parseInt(year, 10);
                if (year < 100) {
                    var twentiethCentury = 1900 + year;
                    var twentyFirstCentury = 2000 + year;
                    year = Math.abs(twentiethCentury - ORBEON.util.DateTime._currentYear) < Math.abs(twentyFirstCentury - ORBEON.util.DateTime._currentYear)
                        ? twentiethCentury : twentyFirstCentury;
                }
                return year;
            },

            _newDate: function(year, month, day) {
                var result = new Date(year, month, day);
                return result.getFullYear() == year && result.getMonth() == month && result.getDate() == day
                    ? result : null;
            }
        },

        Property: function() {
            var Property = function(name, defaultValue) {
                this.name = name;
                this.defaultValue = defaultValue;
            };

            Property.prototype.get = function() {
                return YAHOO.lang.isUndefined(opsXFormsProperties) || YAHOO.lang.isUndefined(opsXFormsProperties[this.name])
                    ? this.defaultValue : opsXFormsProperties[this.name];
            };

            return Property;
        }(),

        Properties: {
            init: function() {
                this.sessionHeartbeat = new ORBEON.util.Property("session-heartbeat", true);
                this.sessionHeartbeatDelay = new ORBEON.util.Property("session-heartbeat-delay", 12 * 60 * 60 * 800); // 80 % of 12 hours in ms
                this.revisitHandling = new ORBEON.util.Property("revisit-handling", "restore");
                this.delayBeforeIncrementalRequest = new ORBEON.util.Property("delay-before-incremental-request", 500);
                this.delayBeforeForceIncrementalRequest = new ORBEON.util.Property("delay-before-force-incremental-request", 2000);
                this.delayBeforeGeckoCommunicationError = new ORBEON.util.Property("delay-before-gecko-communication-error", 5000);
                this.delayBeforeCloseMinimalDialog = new ORBEON.util.Property("delay-before-close-minimal-dialog", 5000);
                this.delayBeforeAjaxTimeout = new ORBEON.util.Property("delay-before-ajax-timeout", 30000);
                this.internalShortDelay = new ORBEON.util.Property("internal-short-delay", 100);
                this.delayBeforeDisplayLoading = new ORBEON.util.Property("delay-before-display-loading", 500);
                this.delayBeforeUploadProgressRefresh= new ORBEON.util.Property("delay-before-upload-progress-refresh", 2000);
                this.debugWindowHeight = new ORBEON.util.Property("debug-window-height", 600);
                this.debugWindowWidth = new ORBEON.util.Property("debug-window-width", 300);
                this.loadingMinTopPadding = new ORBEON.util.Property("loading-min-top-padding", 10);
                this.helpHandler = new ORBEON.util.Property("help-handler", false);
                this.helpTooltip = new ORBEON.util.Property("help-tooltip", false);
                this.offlineSupport = new ORBEON.util.Property("offline", false);
                this.formatInputTime = new ORBEON.util.Property("format.input.time", "[h] =[m] =[s] [P]");
                this.formatInputDate = new ORBEON.util.Property("format.input.date", "[M]/[D]/[Y]");
                this.datePickerNavigator = new ORBEON.util.Property("datepicker.navigator", true);
                this.datePickerTwoMonths = new ORBEON.util.Property("datepicker.two-months", false);
                this.showErrorDialog = new ORBEON.util.Property("show-error-dialog", true);
                this.clientEventMode = new ORBEON.util.Property("client.events.mode", "default");
                this.clientEventsFilter = new ORBEON.util.Property("client.events.filter", "");
                this.resourcesVersioned = new ORBEON.util.Property("oxf.resources.versioned", false);
                this.resourcesVersionNumber = new ORBEON.util.Property("oxf.resources.version-number", "");
                this.newXHTMLLayout = new ORBEON.util.Property("new-xhtml-layout", false);
                this.xhtmlLayout = new ORBEON.util.Property("xhtml-layout", "nospan");
                this.retryDelayIncrement = new ORBEON.util.Property("retry.delay-increment", 5000);
                this.retryMaxDelay = new ORBEON.util.Property("retry.max-delay", 30000);
            }
        },

        /**
         * Utility methods that don't in any other category
         */
        Utils: {
            logMessage: function(message) {
                if (typeof console != "undefined") {
                    console.log(message); // Normal use; do not remove
                }
            },

            isNewXHTMLLayout: function() {
                return ORBEON.util.Properties.newXHTMLLayout.get()
                    || ORBEON.util.Properties.xhtmlLayout.get() != "nospan";
            },

            hideModalProgressPanel: function() {
                if (ORBEON.xforms.Globals.modalProgressPanel) {
                    ORBEON.xforms.Globals.modalProgressPanel.hide();
                    // We set it to null when hiding so we have an easy way of knowing of the panel is visible or not.
                    // See: http://www.nabble.com/Is-Panel-visible--td22139417.html
                    ORBEON.xforms.Globals.modalProgressPanel = null;
                }
            },

            displayModalProgressPanel: function(formID) {
                if (!ORBEON.xforms.Globals.modalProgressPanel) {
                    ORBEON.xforms.Globals.modalProgressPanel =
                    new YAHOO.widget.Panel("wait", {
                        width: "60px",
                        fixedcenter: true,
                        close: false,
                        draggable: false,
                        zindex: 4,
                        modal: true,
                        visible: true
                    });
                    ORBEON.xforms.Globals.modalProgressPanel.setBody('<div class="xforms-modal-progress"/>');
                    ORBEON.xforms.Globals.modalProgressPanel.render(document.body);
                }
                ORBEON.xforms.Globals.modalProgressPanel.show();
            },

            /**
             * For the initial overlays (error dialog, loading indicator, message), which don't contains any XForms
             * markup that relies on the container being rendered on the page to initialize, we want the overlay
             * to be really hidden so it doesn't constrain how wide or high our browser needs to be.
             */
            overlayUseDisplayHidden: function(overlay) {
                YD.setStyle(overlay.element, "display", "none");
                overlay.beforeShowEvent.subscribe(function() { YD.setStyle(overlay.element, "display", "block"); });
                overlay.beforeHideEvent.subscribe(function() { YD.setStyle(overlay.element, "display", "none"); });
            },

            countOccurrences: function(str, character) {
                var count = 0;
                var pos = str.indexOf(character);
                while ( pos != -1 ) {
                    count++;
                    pos = str.indexOf(character,pos+1);
                }
                return count;
            },

            /**
             * For example: appendToEffectivefId("foo·1", "bar") returns "foobar·1"
             */
            appendToEffectiveId: function(effectiveId, ending) {
                var prefixedId = ORBEON.util.Utils.getEffectiveIdNoSuffix(effectiveId);
                return prefixedId + ending + ORBEON.util.Utils.getEffectiveIdSuffixWithSeparator(effectiveId);
            },

            /**
             * For example: getEffectiveIdNoSuffix("foo·1-2") returns "foo"
             */
            getEffectiveIdNoSuffix: function(effectiveId) {
                if (effectiveId == null)
                    return null;

                var suffixIndex = effectiveId.indexOf(XFORMS_SEPARATOR_1);
                if (suffixIndex != -1) {
                    return effectiveId.substring(0, suffixIndex);
                } else {
                    return effectiveId;
                }
            },

            /**
             * For example: getEffectiveIdNoSuffix("foo·1-2") returns "·1-2"
             */
            getEffectiveIdSuffixWithSeparator: function(effectiveId) {
                if (effectiveId == null)
                    return null;

                var suffixIndex = effectiveId.indexOf(XFORMS_SEPARATOR_1);
                if (suffixIndex != -1) {
                    return effectiveId.substring(suffixIndex);
                } else {
                    return "";
                }
            },

            getLocalName: function(element) {
                if (element.nodeType == 1) {
                    return element.tagName.indexOf(":") == -1
                            ? element.tagName
                            : element.tagName.substr(element.tagName.indexOf(":") + 1);
                } else {
                    return null;
                }
            },

            addSuffixToIdsAndRemoveDisabled: function(element, idSuffix, repeatDepth) {

                // Remove disabled, as form fields have a 'disabled' attribute so tabbing skips over form elements in the repeat template
                element.removeAttribute("disabled");

                // Compute new id
                var idSuffixWithDepth = idSuffix;
                for (var repeatDepthIndex = 0; repeatDepthIndex < repeatDepth; repeatDepthIndex++)
                    idSuffixWithDepth += XFORMS_SEPARATOR_2 + "1";

                // Update id attribute
                if (element.id) {
                    element.id = ORBEON.util.Utils.appendRepeatSuffix(element.id, idSuffixWithDepth);
                }

                // Update for attribute
                if (element.htmlFor)
                    element.htmlFor = ORBEON.util.Utils.appendRepeatSuffix(element.htmlFor, idSuffixWithDepth);

                // Update name attribute
                if (element.name) {
                    var newName = ORBEON.util.Utils.appendRepeatSuffix(element.name, idSuffixWithDepth);
                    if (element.tagName.toLowerCase() == "input" && element.type.toLowerCase() == "radio"
                            && ORBEON.xforms.Globals.isRenderingEngineTrident) {
                        // IE supports changing the name of elements, but according to the Microsoft documentation, "This does not
                        // cause the name in the programming model to change in the collection of elements". This has a implication
                        // for radio buttons where using a same name for a set of radio buttons is used to group them together.
                        // http://msdn.microsoft.com/library/default.asp?url=/workshop/author/dhtml/reference/properties/name_2.asp

                        // NOTE: Here we only fix the case of radio button groups. However, the name attribute issue is present
                        // for other controls as well. With IE versions (including IE 8 in quirks mode) that exhibit this bug,
                        // you cannot safely call document.getElementById() of a form element within a template once the template
                        // has been cloned. For example, in a template:
                        //
                        // <span id="my-input"><input id="my-input$$c" name="my-input">...
                        //
                        // getElementById("my-input") correctly returns <span id="my-input">
                        //
                        // Now clone the template. getElementById("my-input") now returns <input id="my-input$$c·1" name="my-input﻿·1">
                        //
                        // That's because IE mixes up the element id and the name, AND the name "my-input" incorrectly points to
                        // the cloned element.
                        //
                        // This seems fixed in IE 8 and up in standards mode.
                        //
                        // If we wanted to fix this, we could run the code below also for <textarea> and for all <input>, not
                        // only those with type="radio". We should also try to detect the issue so that we do not run this for IE
                        // 8 in standards mode.
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

                // Recurse through children
                for (var childIndex = 0; childIndex < element.childNodes.length; childIndex++) {
                    var childNode = element.childNodes[childIndex];
                    if (childNode.nodeType == ELEMENT_TYPE) {
                        if (childNode.id && childNode.id.indexOf("repeat-end-") == 0) repeatDepth--;
                        ORBEON.util.Utils.addSuffixToIdsAndRemoveDisabled(childNode, idSuffix, repeatDepth);
                        if (childNode.id && childNode.id.indexOf("repeat-begin-") == 0) repeatDepth++;
                    }
                }
            },

            getClassForRepeatId: function(repeatId) {
                var depth = 1;
                var currentRepeatId = repeatId;
                while (true) {
                    currentRepeatId = ORBEON.xforms.Globals.repeatTreeChildToParent[currentRepeatId];
                    if (currentRepeatId == null) break;
                    depth = (depth == 4) ? 1 : depth + 1;
                }
                return "xforms-repeat-selected-item-" + depth;
            },

            // Replace in a tree a placeholder by some other string in text nodes and attribute values
            stringReplace: function(node, placeholder, replacement) {

                function stringReplaceWorker(node, placeholderRegExp, replacement) {
                    switch (node.nodeType) {
                        case ELEMENT_TYPE:
                            for (var i = 0; i < node.attributes.length; i++) {
                                var newValue = new String(node.attributes[i].value).replace(placeholderRegExp, replacement);
                                if (newValue != node.attributes[i].value)
                                    ORBEON.util.Dom.setAttribute(node, node.attributes[i].name, newValue);
                            }
                            for (var i = 0; i < node.childNodes.length; i++)
                                stringReplaceWorker(node.childNodes[i], placeholderRegExp, replacement);
                            break;
                        case TEXT_TYPE:
                            var newValue = new String(node.nodeValue).replace(placeholderRegExp, replacement);
                            if (newValue != node.nodeValue)
                                node.nodeValue = newValue;
                            break;
                    }
                }

                // Escape dollar signs we might have in the placeholder or replacement so they can be used in regexp
                placeholder = placeholder.replace(new RegExp("\\$", "g"), "\\$");
                replacement = replacement.replace(new RegExp("\\$", "g"), "$$$$");

                var placeholderRegExp = new RegExp(placeholder, "g");
                stringReplaceWorker(node, placeholderRegExp, replacement);
            },

            appendRepeatSuffix: function(id, suffix) {
                if (suffix == "")
                    return id;

                // Remove "-" at the beginning of the suffix, if any
                if (suffix.charAt(0) == XFORMS_SEPARATOR_2)
                    suffix = suffix.substring(1);

                // Add suffix with the right separator
                id += id.indexOf(XFORMS_SEPARATOR_1) == -1 ? XFORMS_SEPARATOR_1 : XFORMS_SEPARATOR_2;
                id += suffix;

                return id;
            },

            /**
             * Locate the delimiter at the given position starting from a repeat begin element.
             *
             * @param repeatId      Can be either a pure repeat ID, such as "foobar" or an ID that contains information of its
             *                      position relative to its parents, such as "foobar.1". The former happens when we handle an
             *                      event such as <xxf:repeat-index id="foobar" old-index="4" new-index="6"/>. In this case
             *                      "foobar" means the "foobar at 'index' in the current foobar list". The latter happens when we handle
             *                      an event such as <xxf:repeat-iteration id="foobar.1" relevant="false" iteration="10"/>, which
             *                      does not necessarily apply to the current "foobar".
             * @param index
             */
            findRepeatDelimiter: function(repeatId, index) {

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
                var beginElement = ORBEON.util.Dom.get(beginElementId);
                if (!beginElement) return null;
                var cursor = beginElement;
                var cursorPosition = 0;
                while (true) {
                        while (cursor.nodeType != ELEMENT_TYPE || !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")) {
                        cursor = cursor.nextSibling;
                        if (!cursor) return null;
                    }
                    cursorPosition++;
                    if (cursorPosition == index) break;
                    cursor = cursor.nextSibling;
                }

                return cursor;
            },

            /**
             * Check whether a region is completely visible (i.e. is fully inside the viewport).
             * Note: this function is different than the function with the same name in YUI.
             */
            fitsInViewport: function(element) {

                // Viewport coordinates
                var viewportFirstTop = YAHOO.util.Dom.getDocumentScrollTop();
                var viewportFirstLeft = YAHOO.util.Dom.getDocumentScrollLeft();
                var viewportSecondTop = viewportFirstTop + YAHOO.util.Dom.getViewportHeight();
                var viewportSecondLeft = viewportFirstLeft + YAHOO.util.Dom.getViewportWidth();
                var viewportRegion = new YAHOO.util.Region(viewportFirstTop, viewportSecondLeft, viewportSecondTop, viewportFirstLeft);

                // Element coordinates
                var elementRegion = YAHOO.util.Dom.getRegion(element);

                return viewportRegion.top <= elementRegion.top && viewportRegion.left <= elementRegion.left
                    && elementRegion.bottom <= viewportRegion.bottom && elementRegion.right <= viewportRegion.right;
            },

            /**
             * Applies a function to all the elements of an array, and discards the value returned by the function, if any.
             */
            apply: function(array, fn, obj, overrideContext) {
                for (var arrayIndex = 0; arrayIndex < array.length; arrayIndex++) {
                    var arrayElement = array[arrayIndex];
                    if (overrideContext) fn.call(obj, arrayElement); else fn(arrayElement);
                }
            }
        },

        /**
         * Utility function to make testing with YUI Test easier.
         */
        Test: {
            /**
             * Tests that rely on instances having a certain value should start by callng this utility function
             */
            executeWithInitialInstance: function(testCase, testFunction) {
                ORBEON.testing.executeCausingAjaxRequest(testCase, function() {
                    ORBEON.xforms.Document.dispatchEvent("main-model", "restore-instance");
                }, function() {
                    testFunction.call(testCase);
                });
            },

            /**
             * Runs a first function as part of a YUI test case, waits for all Ajax requests (if any) that might ensue
             * to terminate, then run a second function.
             *
             * This doesn't use the ajaxResponseProcessedEvent, because we want this to work in cases where we have zero
             * or more than one Ajax requests.
             */
            executeCausingAjaxRequest: function(testCase, causingAjaxRequestFunction, afterAjaxResponseFunction) {

                function checkAjaxReceived() {
                    if (ORBEON.xforms.Globals.requestInProgress || ORBEON.xforms.Globals.eventQueue.length > 0) {
                        // Wait another 100 ms
                        setTimeout(checkAjaxReceived, 100);
                    } else {
                        // We done with Ajax requests, continue with the test
                        testCase.resume(function() {
                            afterAjaxResponseFunction.call(testCase);
                        });
                    }
                }

                causingAjaxRequestFunction.call(testCase);
                setTimeout(checkAjaxReceived, 100);
                testCase.wait(20000);
            },

            /**
             * Similar to executeCausingAjaxRequest
             */
            executeSequenceCausingAjaxRequest: function(testCase, tests) {
                if (tests.length > 0) {
                    var testTuple = tests.shift();
                    ORBEON.util.Test.executeCausingAjaxRequest(testCase, function() {
                        testTuple[0].call(testCase);
                    }, function() {
                        if (testTuple[1]) testTuple[1].call(testCase);
                        ORBEON.util.Test.executeSequenceCausingAjaxRequest(testCase, tests);
                    });
                }
            },

            /**
             * Starts Firebug Lite if Firebug is not already available (as it is most of the time on Firefox)
             */
            startFirebugLite: function() {
                if (! window.firebug) {
                    var firebugScript = document.createElement("script");
                    firebugScript.setAttribute('src','http://getfirebug.com/releases/lite/1.2/firebug-lite-compressed.js');
                    document.body.appendChild(firebugScript);
                    (function(){
                        if (window.firebug) firebug.init();
                        else setTimeout(arguments.callee);
                    })();
                }
            },

            /**
             * Function to be call in every test to start the test when the page is loaded.
             */
            onOrbeonLoadedRunTest: function() {
                ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
                    if (parent && parent.TestManager) {
                        parent.TestManager.load();
                    } else {
                        new YAHOO.tool.TestLogger();
                        YAHOO.tool.TestRunner.run();
                    }
                });
            },

            /**
             * Simulate the user clicking on a button.
             *
             * @param id    Button id.
             * @return {void}
             */
            click: function(id) {
                OD.getElementByTagName(OD.get(id), "button").click();
            },

            /**
             * Asserts that the specified message is shown in the message panel, and closes the message panel.
             *
             * @param   {String} expected   Message we expect to see shown in the message panel
             * @return  {void}
             */
            assertMessage: function(expected) {
                var messageDialog = OD.get("xforms-message-dialog");
                var body = YD.getElementsByClassName("bd", null, messageDialog)[0];
                var actual = OD.getStringValue(OD.getChildElementByIndex(body, 0));
                YAHOO.util.Assert.areEqual(expected, actual, "didn't get the expected message");
                OD.getElementByTagName(messageDialog, "button").click();
            }
        }
    };

    ORBEON.util.Properties.init();
})();

// Define packages
ORBEON.xforms = {};
ORBEON.xforms.action = {};
ORBEON.xforms.control = {};
ORBEON.xforms.server = {};
ORBEON.widgets = ORBEON.widgets || {};  // Legacy name used by non-XBL components
ORBEON.widget = ORBEON.widget || {};    // New name to follow the same convention used by YUI
ORBEON.xforms.Globals = ORBEON.xforms.Globals || {};

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
                if (YAHOO.util.Dom.hasClass(candidateForm, "xforms-form")) {
                    form = candidateForm;
                    break;
                }
            }
        }

        // Create event and fire
        var event = new ORBEON.xforms.server.AjaxServer.Event(form, targetId, null, null, eventName, bubbles, cancelable, ignoreErrors);
        ORBEON.xforms.server.AjaxServer.fireEvents([event], incremental == undefined ? false : incremental);
    },

    /**
     * Returns the value of an XForms control.
     *
     * @param {String | HTMLElement} control    Either the id of the control or the element corresponding to that control
     */
    getValue: function(control) {
        control = _.isString(control) ? ORBEON.util.Dom.get(control) : control;
        return ORBEON.xforms.Controls.getCurrentValue(control);
    },

    /**
     * Set the value of an XForms control.
     *
     * @param {String | HTMLElement} control    Either the id of the control or the element corresponding to that control
     * @param {String} newValue                 New value for the control
     */
    setValue: function(control, newValue) {
        // User might pass non-string values, so make sure the result is a string
        var stringValue = "" + newValue;
        control = _.isString(control) ? ORBEON.util.Dom.get(control) : control;
        if (control == null || ORBEON.xforms.Controls.isInRepeatTemplate(control))
            throw "ORBEON.xforms.Document.setValue: can't find control id '" + control + "'";

        if (!YAHOO.util.Dom.hasClass(control, "xforms-output") && !YAHOO.util.Dom.hasClass(control, "xforms-upload")) {// ignore event on xforms:output and xforms:upload
            // Directly change the value of the control in the UI without waiting for a response from the server
            ORBEON.xforms.Controls.setCurrentValue(control, stringValue);
            // And also fire server event
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, stringValue, "xxforms-value-change-with-focus-change");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        } else {
            throw "ORBEON.xforms.Document.setValue: can't setvalue on output or upload control '" + control + "'";
        }
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
     * is the responsibility of the application (not the XForms engine) to detect if Gears can be installed and to
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
                    offlineIframe.contentWindow.ORBEON.xforms.Document.dispatchEvent("#document", "xxforms-offline");
                });
            }
        }, 100);
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
    takeOnlineFromSummary: function(url, beforeOnlineListener, formOnlineListener) {
        ORBEON.xforms.Offline.init();
        ORBEON.xforms.Offline.loadFormInIframe(url, function(offlineIframe) {
            // Calling listener to notify that the form is now completely online
            if (formOnlineListener) {
                offlineIframe.contentWindow.ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(function() {
                    formOnlineListener(offlineIframe.contentWindow);
                });
            }
            offlineIframe.contentWindow.ORBEON.xforms.Offline.takeOnline(beforeOnlineListener);
        });
    },

    /**
     * Gets a value stored in the hidden client-state input field.
     */
    getFromClientState: function(formID, key) {
        var clientState = ORBEON.xforms.Globals.formClientState[formID];
        var keyValues = clientState.value.split("&");
        for (var i = 0; i < keyValues.length; i = i + 2)
            if (keyValues[i] == key)
                return unescape(keyValues[i + 1]);
        return null;
    },

    /**
     * Returns a value stored in the hidden client-state input field.
     */
    storeInClientState: function(formID, key, value) {
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
};

ORBEON.xforms.Controls = {

    // Returns MIP for a given control
    isRelevant: function (control) {
        return !YAHOO.util.Dom.hasClass(control, "xforms-disabled")
            && !YAHOO.util.Dom.hasClass(control, "xforms-disabled-subsequent");
    },
    isReadonly: function (control) {
        return  YAHOO.util.Dom.hasClass(control, "xforms-readonly");
    },
    isRequired: function (control) {
        return  YAHOO.util.Dom.hasClass(control, "xforms-required");
    },
    isValid:    function (control) {
        return !YAHOO.util.Dom.hasClass(control, "xforms-invalid");
    },

    getForm: function(control) {
        // If the control is not an HTML form control look for an ancestor which is a form
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
        var formElement = ORBEON.xforms.Controls.getForm(control);
        if (YAHOO.util.Dom.hasClass(control, "xforms-type-time")) {
            // Time control
            var timeInputValue = YAHOO.util.Dom.getElementsByClassName("xforms-input-input", null, control)[0].value;
            var timeJSDate = ORBEON.util.DateTime.magicTimeToJSDate(timeInputValue);
            return timeJSDate == null ? timeInputValue : ORBEON.util.DateTime.jsDateToISOTime(timeJSDate);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-date") && YAHOO.util.Dom.hasClass(control, "xforms-input")) {
            // Date control
			var dateInputValue;
			if (YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal")) {
				var imgElement = YAHOO.util.Dom.getElementsByClassName("xforms-input-appearance-minimal", "img", control)[0];
				dateInputValue = ORBEON.util.Dom.getAttribute(imgElement, "alt");
			} else {
				dateInputValue = YAHOO.util.Dom.getElementsByClassName("xforms-input-input", null, control)[0].value;
			}
            var dateJSDate = ORBEON.util.DateTime.magicDateToJSDate(dateInputValue);
            return dateJSDate == null ? dateInputValue : ORBEON.util.DateTime.jsDateToISODate(dateJSDate);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-dateTime")) {
            // Date time control
            var dateValue = YAHOO.util.Dom.getElementsByClassName("xforms-type-date", null, control)[0].value;
            var jsDateDate = ORBEON.util.DateTime.magicDateToJSDate(dateValue);
            var timeValue = YAHOO.util.Dom.getElementsByClassName("xforms-type-time", null, control)[0].value;
            var jsDateTime = ORBEON.util.DateTime.magicTimeToJSDate(timeValue);
            if (jsDateDate == null || jsDateTime == null) {
                return dateValue == "" && timeValue == "" ? "" : dateValue + "T" + timeValue;
            } else {
                return ORBEON.util.DateTime.jsDateToISODateTime(jsDateDate, jsDateTime);
            }
        } else if ((YAHOO.util.Dom.hasClass(control, "xforms-input") && !YAHOO.util.Dom.hasClass(control, "xforms-type-boolean") && !YAHOO.util.Dom.hasClass(control, "xforms-static"))
                || YAHOO.util.Dom.hasClass(control, "xforms-secret")) {
            // Simple input
            var input = control.tagName.toLowerCase() == "input" ? control : control.getElementsByTagName("input")[0];
            return input.value;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))) {
            // Checkboxes, radio buttons, boolean input
            var inputs = control.getElementsByTagName("input");
            var spanValue = "";
            for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
                var input = inputs[inputIndex];
                if (input.checked) {
                    if (spanValue != "") spanValue += " ";
                    spanValue += input.value;
                }
            }
            // For boolean inputs, if the checkbox isn't checked, then the value is false
            if (spanValue == "" && YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))
                spanValue = "false";
            return spanValue;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-minimal")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal")
                || YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-compact")) {
            // Drop-down and list
            var options = ORBEON.util.Utils.isNewXHTMLLayout()
                          ? control.getElementsByTagName("select")[0].options
                          : control.options;
            var selectValue = "";
            for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                var option = options[optionIndex];
                if (option.selected) {
                    if (selectValue != "") selectValue += " ";
                    selectValue += option.value;
                }
            }
            return selectValue;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")
                && ! YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // Text area (not HTML)
            var textarea = control.tagName.toLowerCase() == "textarea" ? control : control.getElementsByTagName("textarea")[0];
            return textarea.value
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")
                && YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // HTML text area
            return ORBEON.xforms.Page.getControl(control).getValue();
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-output") || (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-static"))) {
            // Output and static input
            if (YAHOO.util.Dom.hasClass(control, "xforms-mediatype-image")) {
                var image = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                return image.src;
            } else if (YAHOO.util.Dom.hasClass(control, "xforms-output-appearance-xxforms-download")) {
                // Download link doesn't really have a value
                return null;
            } else if (YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
                return control.innerHTML;
            } else {
                var spanWithValue = ORBEON.util.Utils.isNewXHTMLLayout()
                    ? control.getElementsByTagName("span")[0]
                    : control;
                return ORBEON.util.Dom.getStringValue(spanWithValue);
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-xxforms-tree")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-xxforms-tree")) {
            // Select/Select tree
            return ORBEON.xforms.Page.getControl(control).getValue();
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-range")) {
            return ORBEON.xforms.Globals.sliderYui[control.id].previousVal / 200;
        }
    },

    isInRepeatTemplate: function(element) {
        return ORBEON.util.Dom.existsAncestorOrSelf(element, function(node) {
            return YAHOO.util.Dom.hasClass(node, "xforms-repeat-template")
        });
    },

    /**
     * Updates the value of a control in the UI.
     *
     * @param control           HTML element for the control we want to update
     * @param newControlValue   New value
     * @param attribute1        Optional
     * @param attribute2        Optional
     * @param attribute3        Optional
     * @param attribute4        Optional
     */
    setCurrentValue: function(control, newControlValue, attribute1, attribute2, attribute3, attribute4) {
        var isStaticReadonly = YAHOO.util.Dom.hasClass(control, "xforms-static");
        var formElement = ORBEON.xforms.Controls.getForm(control);
        if (YAHOO.util.Dom.hasClass(control, "xforms-output-appearance-xxforms-download")) {
            // XForms output with xxforms:download appearance
            var anchor = ORBEON.util.Dom.getElementsByName(control, "a")[0];
            if (newControlValue == "") {
                anchor.setAttribute("href", "#");
                YAHOO.util.Dom.addClass(anchor, "xforms-readonly");
            } else {
                anchor.setAttribute("href", newControlValue);
                YAHOO.util.Dom.removeClass(anchor, "xforms-readonly");
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-output") || isStaticReadonly) {
            // XForms output or "static readonly" mode
            if (YAHOO.util.Dom.hasClass(control, "xforms-mediatype-image")) {
                var image = ORBEON.util.Utils.isNewXHTMLLayout()
                    ? YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, control)[0]
                    : ORBEON.util.Dom.getChildElementByIndex(control, 0);
                image.src = newControlValue;
            } else {
                var output = ORBEON.util.Utils.isNewXHTMLLayout()
                        ? YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, control)[0]
                        : control;
                if (YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
                    output.innerHTML = newControlValue;
                } else {
                    ORBEON.util.Dom.setStringValue(output, newControlValue);
                }
            }
        } else if (ORBEON.xforms.Globals.changedIdsRequest[control.id] != null) {
            // User has modified the value of this control since we sent our request:
            // so don't try to update it
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // Triggers don't have a value: don't update them
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-time")) {
            // Time control
            var inputField = control.getElementsByTagName("input")[0];
            var jsDate = ORBEON.util.DateTime.magicTimeToJSDate(newControlValue);
            inputField.value = jsDate == null ? newControlValue : ORBEON.util.DateTime.jsDateToFormatDisplayTime(jsDate);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-date")) {
            // Date control
            var jsDate = ORBEON.util.DateTime.magicDateToJSDate(newControlValue);
            var displayDate = jsDate == null ? newControlValue : ORBEON.util.DateTime.jsDateToFormatDisplayDate(jsDate);
			if (YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal")) {
				var imgElement = control.getElementsByTagName("img")[0];
                ORBEON.util.Dom.setAttribute(imgElement, "alt", displayDate);
			} else {
                var inputField = control.getElementsByTagName("input")[0];
                inputField.value = displayDate;
			}
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-dateTime")) {
            // Only update value if different from the one we have. This handle the case where the fields contain invalid
            // values with the T letter in them. E.g. aTb/cTd, aTbTcTd sent to server, which we don't know anymore how
            // to separate into 2 values.
            if (ORBEON.xforms.Controls.getCurrentValue(control) != newControlValue) {
                var separatorIndex = newControlValue.indexOf("T");
                // Populate date field
                var datePartString = newControlValue.substring(0, separatorIndex);
                var datePartJSDate = ORBEON.util.DateTime.magicDateToJSDate(datePartString);
                var inputFieldDate = control.getElementsByTagName("input")[0];
                inputFieldDate.value = datePartJSDate == null ? datePartString : ORBEON.util.DateTime.jsDateToFormatDisplayDate(datePartJSDate);
                // Populate time field
                var timePartString = newControlValue.substring(separatorIndex + 1);
                var timePartJSDate = ORBEON.util.DateTime.magicTimeToJSDate(timePartString);
                var inputFieldTime = control.getElementsByTagName("input")[1];
                inputFieldTime.value = timePartJSDate == null ? timePartString : ORBEON.util.DateTime.jsDateToFormatDisplayTime(timePartJSDate);
            }
        } else if ((YAHOO.util.Dom.hasClass(control, "xforms-input") && !YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))
                || YAHOO.util.Dom.hasClass(control, "xforms-secret")) {
            // Regular XForms input (not boolean, date, time or dateTime) or secret
            var input = control.tagName.toLowerCase() == "input" ? control : control.getElementsByTagName("input")[0];
            if (control.value != newControlValue) {
                control.previousValue = newControlValue;
                control.valueSetByXForms++;
                control.value = newControlValue;
            }
            if (input.value != newControlValue)
                input.value = newControlValue;

            // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be the best thing to do.
            if (attribute1 != null) {
                if (attribute1 == "")
                    input.removeAttribute("size");
                else
                    input.size = attribute1;
            }
            if (attribute2 != null) {
                if (attribute2 == "")
                    input.removeAttribute("maxlength");// this, or = null doesn't work w/ IE 6
                else
                    input.maxLength = attribute2;// setAttribute() doesn't work with IE 6
            }
            if (attribute3 != null) {
                if (attribute2 == "")
                    input.removeAttribute("autocomplete");
                else
                    input.autocomplete = attribute3;
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))) {
            // Handle checkboxes and radio buttons
            var selectedValues = YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-full")
                    ? newControlValue.split(" ") : new Array(newControlValue);
            var checkboxInputs = control.getElementsByTagName("input");
            for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                var checkboxInput = checkboxInputs[checkboxInputIndex];
                checkboxInput.checked = xformsArrayContains(selectedValues, checkboxInput.value);
            }

            // Update classes on control
            ORBEON.xforms.Controls._setRadioCheckboxClasses(control);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-minimal")
                || YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal")) {
            // Handle lists and comboboxes
            var selectedValues = YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-compact")  ? newControlValue.split(" ") : new Array(newControlValue);
            var select = ORBEON.util.Utils.isNewXHTMLLayout() ? control.getElementsByTagName("select")[0] : control;
            var options = select.options;
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
                        // for the error message), it seems that DOM updates are somewhat asynchronous and that when you
                        // make an element visible and change a property right after that, it is sometimes as if the element
                        // is not visible yet, and so the property cannot be changed.
                    }
                }
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")
                && ! YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // Text area
            var textarea = control.tagName.toLowerCase() == "textarea" ? control : control.getElementsByTagName("textarea")[0];
            textarea.value = newControlValue;

            // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be the best thing to do.
            // NOTE: There is no "maxlength" attribute in HTML 4, but there is one in HTML 5. Should we add it anyway?
            if (attribute2 != null) {
                if (attribute2 == "")
                    textarea.removeAttribute("cols");
                else
                    textarea.cols = attribute2;
            }
            if (attribute3 != null) {
                if (attribute2 == "")
                    textarea.removeAttribute("rows");
                else
                    textarea.rows = attribute3;
            }

            // Autosize textarea
            if (YAHOO.util.Dom.hasClass(control, "xforms-textarea-appearance-xxforms-autosize")) {
                ORBEON.xforms.Controls.autosizeTextarea(control);
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")
                && YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // HTML area
            ORBEON.xforms.Page.getControl(control).setValue(newControlValue);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-xxforms-tree")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-xxforms-tree")) {
            return ORBEON.xforms.Page.getControl(control).setValue(newControlValue);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-upload")) {
            // Upload

            // Get elements we want to modify from the DOM
            var fileNameSpan = YAHOO.util.Dom.getElementsByClassName("xforms-upload-filename", null, control)[0];
            var mediatypeSpan = YAHOO.util.Dom.getElementsByClassName("xforms-upload-mediatype", null, control)[0];
            var sizeSpan = YAHOO.util.Dom.getElementsByClassName("xforms-upload-size", null, control)[0];
            // Set values in DOM
            var upload = ORBEON.xforms.Page.getControl(control);
            if (attribute1) upload.setState(attribute1);
            if (attribute2 != null)
                ORBEON.util.Dom.setStringValue(fileNameSpan, attribute2);
            if (attribute3 != null)
                ORBEON.util.Dom.setStringValue(mediatypeSpan, attribute3);
            if (attribute4 != null) {
                var displaySize = attribute4 > 1024 * 1024 ? Math.round(attribute4 / (1024 * 1024) * 10) / 10 + " MB"
                        : attribute4 > 1024 ? Math.round(attribute4 / 1024 * 10) / 10 + " KB"
                        : attribute4 + " B";
                ORBEON.util.Dom.setStringValue(sizeSpan, displaySize);
            }
        } else if (typeof(control.value) == "string") {
            // Textarea, password
            control.value = newControlValue;
            control.previousValue = newControlValue;
        }
    },

    _setRadioCheckboxClasses: function(target) {
        // Update xforms-selected/xforms-deselected classes on the parent <span> element
        var checkboxInputs = target.getElementsByTagName("input");
        for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
            var checkboxInput = checkboxInputs[checkboxInputIndex];
            var parentSpan = checkboxInput.parentNode.parentNode;
            if (checkboxInput.checked) {
                YAHOO.util.Dom.addClass(parentSpan, "xforms-selected");
                YAHOO.util.Dom.removeClass(parentSpan, "xforms-deselected");
            } else {
                YAHOO.util.Dom.addClass(parentSpan, "xforms-deselected");
                YAHOO.util.Dom.removeClass(parentSpan, "xforms-selected");
            }
        }
    },

    // Mapping between className (parameter of this method and added after "xforms-") and id of elements
    // in the case where they are outside of the control element.
    _classNameToId: {
        "label": "$$l",
        "hint": "$$t",
        "help": "$$p",
        "help-image": "$$i",
        "alert": "$$a",
        "control": "$$c"
    },

    _classNameToRegexp: {
        "label": "\\$\\$l",
        "hint": "\\$\\$t",
        "help": "\\$\\$p",
        "help-image": "\\$\\$i",
        "alert": "\\$\\$a"
    },

    /**
     * Look for an HTML element corresponding to an XForms LHHA element.
     * In the HTML generated by the server there is 1 element for each one and 2 for the help.
     */
    _getControlLHHA: function(control, lhhaType) {

        // For new layout, try to look for label under the control element
        if (ORBEON.util.Utils.isNewXHTMLLayout()) {
            var lhhaElements = YAHOO.util.Dom.getElementsByClassName("xforms-" + lhhaType, null, control);
            if (lhhaElements.length > 0) return lhhaElements[0];
        }
        // If old layout, or we couldn't find the element, look by ID
        var lhhaElementId = ORBEON.util.Utils.appendToEffectiveId(control.id, ORBEON.xforms.Controls._classNameToId[lhhaType]);
        return ORBEON.util.Dom.get(lhhaElementId);
    },

    /**
     * Return the control associated with a given LHHA element and its expected type.
     */
    getControlForLHHA: function(element, lhhaType) {
        var suffix = ORBEON.xforms.Controls._classNameToId[lhhaType];
        // NOTE: could probably do without llhaType parameter
        return element.id.indexOf(suffix) != -1
            ? ORBEON.util.Dom.get(element.id.replace(new RegExp(ORBEON.xforms.Controls._classNameToRegexp[lhhaType], "g"), ''))
            : element.parentNode;
    },

    _setMessage: function(control, lhhaType, message) {
        var lhhaElement = ORBEON.xforms.Controls._getControlLHHA(control, lhhaType);
        if (lhhaElement != null) {
            lhhaElement.innerHTML = message;
            if (message == "") {
                if (lhhaType == "help" && !YAHOO.util.Dom.hasClass(lhhaElement, "xforms-disabled")) {
                    // Hide help with empty content
                    YAHOO.util.Dom.addClass(lhhaElement, "xforms-disabled-subsequent");
                    // If this is the help element, also disable help image
                    var helpImage = ORBEON.xforms.Controls._getControlLHHA(control, "help-image");
                    YAHOO.util.Dom.addClass(helpImage, "xforms-disabled-subsequent");
                }
            } else {
                // We show LHHA with non-empty content, but ONLY if the control is relevant
                if (ORBEON.xforms.Controls.isRelevant(control)) {
                    YAHOO.util.Dom.removeClass(lhhaElement, "xforms-disabled");
                    YAHOO.util.Dom.removeClass(lhhaElement, "xforms-disabled-subsequent");
                    // If this is the help element, also enable the help image
                    if (lhhaType == "help") {
                        var helpImage = ORBEON.xforms.Controls._getControlLHHA(control, "help-image");
                        YAHOO.util.Dom.removeClass(helpImage, "xforms-disabled");
                        YAHOO.util.Dom.removeClass(helpImage, "xforms-disabled-subsequent");
                    }
                }
            }
        }
    },

    getLabelMessage: function(control) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            if (ORBEON.util.Utils.isNewXHTMLLayout()) {
                // Element is "label" and "control" at the same time so use "control"
                var labelElement = ORBEON.xforms.Controls._getControlLHHA(control, "control");
                return labelElement.innerHTML;
            } else {
                // Element is either <button> or <a>
                return control.innerHTML;
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-dialog")) {
            // Dialog
            var labelDiv = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            return labelDiv.innerHTML;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-group-appearance-xxforms-fieldset")) {
            // Group with fieldset/legend
            var legend = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            if (legend != null)
                return legend.innerHTML;
        } else {
            var labelElement = ORBEON.xforms.Controls._getControlLHHA(control, "label");
            return labelElement.innerHTML;
        }
    },

    setLabelMessage: function(control, message) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            if (ORBEON.util.Utils.isNewXHTMLLayout()) {
                // Element is "label" and "control" at the same time so use "control"
                ORBEON.xforms.Controls._setMessage(control, "control", message);
            } else {
                // Element is either <button> or <a>
                control.innerHTML = message;
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-dialog")) {
            // Dialog
            var labelDiv = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            labelDiv.innerHTML = message;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-group-appearance-xxforms-fieldset")) {
            // Group with fieldset/legend
            var legend = ORBEON.util.Dom.getChildElementByIndex(control, 0);
            if (legend != null)
                legend.innerHTML = message;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-output-appearance-xxforms-download")) {
            // Download link
            var anchor = YAHOO.util.Dom.getChildren(control)[0];
            anchor.innerHTML = message;
        } else {
            ORBEON.xforms.Controls._setMessage(control, "label", message);
        }
    },

    getHelpMessage: function(control) {
        var helpElement = ORBEON.xforms.Controls._getControlLHHA(control, "help");
        return helpElement == null ? "" : ORBEON.util.Dom.getStringValue(helpElement);
    },

    setHelpMessage: function(control, message) {
        // We escape the value because the help element is a little special, containing escaped HTML
        message = ORBEON.util.String.escapeHTMLMinimal(message);
        ORBEON.xforms.Controls._setMessage(control, "help", message);
        ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.helpTooltipForControl);
    },

    setValid: function(control, newValid) {
        // Update class xforms-invalid on the control
        var isValid;
        var isVisited = YAHOO.util.Dom.hasClass(control, "xforms-visited");
        if (newValid != null) {
            isValid = newValid != "false";
            if (isValid) {
                YAHOO.util.Dom.removeClass(control, "xforms-invalid");
                YAHOO.util.Dom.removeClass(control, "xforms-invalid-visited");
            } else {
                YAHOO.util.Dom.addClass(control, "xforms-invalid");
                if (isVisited) YAHOO.util.Dom.addClass(control, "xforms-invalid-visited");
            }
        } else {
            isValid = ORBEON.xforms.Controls.isValid(control);
        }

        // Update class on alert element
        var alertElement = ORBEON.xforms.Controls._getControlLHHA(control, "alert");
        if (alertElement != null) { // Some controls don't have an alert
            if (isValid) {
                YAHOO.util.Dom.removeClass(alertElement, "xforms-alert-active");
                YAHOO.util.Dom.removeClass(alertElement, "xforms-alert-active-visited");
                YAHOO.util.Dom.addClass(alertElement, "xforms-alert-inactive");
            } else {
                YAHOO.util.Dom.removeClass(alertElement, "xforms-alert-inactive");
                YAHOO.util.Dom.addClass(alertElement, "xforms-alert-active");
                if (isVisited) YAHOO.util.Dom.addClass(alertElement, "xforms-alert-active-visited");
            }
        }

        // If the control is now valid and there is an alert tooltip for this control, get rid of it
        var alertTooltip = ORBEON.xforms.Globals.alertTooltipForControl[control.id];
        if (alertTooltip != null && alertTooltip != true) {
            if (isValid) {
                // Prevent the tooltip from becoming visible on mouseover
                alertTooltip.cfg.setProperty("disabled", true);
                // If visible, hide the tooltip right away, otherwise it will only be hidden a few seconds later
                alertTooltip.hide();
            } else {
                // When a control becomes invalid and it always has a tooltip, this means that the tooltip got disabled
                // when the control previously became valid, so now re-enable it
                alertTooltip.cfg.setProperty("disabled", false);
            }
        }
    },

    setDisabledOnFormElement: function(element, disabled) {
        if (disabled) {
            element.setAttribute("disabled", "disabled");
        } else {
            element.removeAttribute("disabled");
        }
    },

    setRelevant: function(control, isRelevant) {
        var FN = ORBEON.xforms.FlatNesting;

        if (YAHOO.util.Dom.hasClass(control, "xforms-group-begin-end")) {
            // Case of group delimiters
            FN.setRelevant(control, isRelevant);
        } else {
            var elementsToUpdate = [ control,
                ORBEON.xforms.Controls._getControlLHHA(control, "label"),
                ORBEON.xforms.Controls._getControlLHHA(control, "alert")
            ];
            // Also show help if message is not empty
            if (!isRelevant || (isRelevant && ORBEON.xforms.Controls.getHelpMessage(control) != "")) {
                elementsToUpdate.push(ORBEON.xforms.Controls._getControlLHHA(control, "help"));
                elementsToUpdate.push(ORBEON.xforms.Controls._getControlLHHA(control, "help-image"));
            }
            // Also show hint if message is not empty
            if (!isRelevant || (isRelevant && ORBEON.xforms.Controls.getHintMessage(control) != ""))
                elementsToUpdate.push(ORBEON.xforms.Controls._getControlLHHA(control, "hint"));

            // Go through elements to update, and update classes
            for (var elementIndex = 0; elementIndex < elementsToUpdate.length; elementIndex++) {
                var element = elementsToUpdate[elementIndex];
                if (element != null) {
                    if (isRelevant) {
                        YAHOO.util.Dom.removeClass(element, "xforms-disabled");
                        YAHOO.util.Dom.removeClass(element, "xforms-disabled-subsequent");
                        ORBEON.util.Dom.nudgeAfterDelay(element);
                    } else {
                        YAHOO.util.Dom.addClass(element, "xforms-disabled-subsequent");
                    }
                }
            }
        }
    },

    setRepeatIterationRelevance: function(repeatID, iteration, relevant) {
        var OU = ORBEON.util.Utils;
        var FN = ORBEON.xforms.FlatNesting;

        var delimiter = OU.findRepeatDelimiter(repeatID, iteration);
        FN.setRelevant(delimiter, relevant);
    },

    setReadonly: function(control, isReadonly) {

        // Update class
        if (isReadonly) {
            YAHOO.util.Dom.addClass(control, "xforms-readonly");
        } else {
            YAHOO.util.Dom.removeClass(control, "xforms-readonly");
        }

        if (YAHOO.util.Dom.hasClass(control, "xforms-group-begin-end")) {
            // Case of group delimiters
            // Readonlyness is no inherited by controls inside the group, so we are just updating the class on the begin-marker
            // to be consistent with the markup generated by the server.
            if (isReadonly) {
                YAHOO.util.Dom.addClass(control, "xforms-readonly");
            } else {
                YAHOO.util.Dom.removeClass(control, "xforms-readonly");
            }
        } else if ((YAHOO.util.Dom.hasClass(control, "xforms-input"))
                || YAHOO.util.Dom.hasClass(control, "xforms-secret")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-full")) {
            // Input fields, radio buttons, or checkboxes

            // Add/remove xforms-readonly on span
            if (isReadonly) YAHOO.util.Dom.addClass(control, "xforms-readonly");
            else YAHOO.util.Dom.removeClass(control, "xforms-readonly");

            // Update disabled on input fields
            var inputs = control.getElementsByTagName("input");
            for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
                var input = inputs[inputIndex];
                ORBEON.xforms.Controls.setDisabledOnFormElement(input, isReadonly);
            }
            if (control.tagName.toLowerCase() == "input")
                ORBEON.xforms.Controls.setDisabledOnFormElement(control, isReadonly);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-minimal")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-compact")
                || YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal")
                || YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-compact")) {
            // Lists
            var select = ORBEON.util.Utils.isNewXHTMLLayout()
                ? control.getElementsByTagName("select")[0] : control;
            ORBEON.xforms.Controls.setDisabledOnFormElement(select, isReadonly);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-output")
                || YAHOO.util.Dom.hasClass(control, "xforms-group")) {
            // XForms output and group
            if (isReadonly) YAHOO.util.Dom.addClass(control, "xforms-readonly");
            else YAHOO.util.Dom.removeClass(control, "xforms-readonly");
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea") && YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // XForms HTML area
            ORBEON.xforms.Page.getControl(control).setReadonly(isReadonly);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-upload")) {
            // Upload control
            ORBEON.xforms.Controls.setDisabledOnFormElement(
                    ORBEON.util.Dom.getChildElementByClass(control, "xforms-upload-select"), isReadonly);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")) {
            // Textarea
            var textarea = ORBEON.util.Utils.isNewXHTMLLayout()
                ? control.getElementsByTagName("textarea")[0] : control;
            ORBEON.xforms.Controls.setDisabledOnFormElement(textarea, isReadonly);
        } else if ((YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                && ! YAHOO.util.Dom.hasClass(control, "xforms-trigger-appearance-minimal"))
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // Button
            var button = ORBEON.util.Dom.getElementByTagName(control, "button");
            ORBEON.xforms.Controls.setDisabledOnFormElement(button, isReadonly);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-trigger-appearance-minimal")) {
            // Also update class xforms-trigger-readonly to style the a inside the span (in span layout, for IE6)
            if (isReadonly) YAHOO.util.Dom.addClass(control, "xforms-trigger-readonly");
            else            YAHOO.util.Dom.removeClass(control, "xforms-trigger-readonly");
        }
    },

    isLHHA: function (element) {
        var lhhaClasses = ["xforms-label", "xforms-help", "xforms-hint", "xforms-alert"];
        return ORBEON.util.Dom.isElement(element) &&
            _.any(lhhaClasses, function(clazz) { return YAHOO.util.Dom.hasClass(element, clazz); });
    },

    getAlertMessage: function(control) {
        var alertElement = ORBEON.xforms.Controls._getControlLHHA(control, "alert");
        return alertElement.innerHTML;
    },

    setAlertMessage: function(control, message) {
        ORBEON.xforms.Controls._setMessage(control, "alert", message);
        ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.alertTooltipForControl);
    },

    getHintMessage: function(control) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger") || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            var formElement = ORBEON.util.Dom.getElementByTagName(control, ["a", "button"]);
            return formElement.title;
        } else {
            // Element for hint
            var hintElement = ORBEON.xforms.Controls._getControlLHHA(control, "hint");
            return hintElement == null ? "" : hintElement.innerHTML;
        }
    },

    setHintMessage: function(control, message) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger") || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // For triggers, the value is stored in the title for the control
            if (ORBEON.xforms.Globals.hintTooltipForControl[control.id] == null) {
                // We only update the title if we don't have already a YUI hint widget.
                // If we do, updating the value in the YUI widget is enough. The YUI widget empties the content of the
                // title attribute to avoid the text in the title from showing. If we set the title, we might have
                // both the title shown by the browser and the YUI hint widget.
                var formElement = ORBEON.util.Dom.getElementByTagName(control, ["a", "button"]);
                formElement.title = message;
            }
        } else {
            ORBEON.xforms.Controls._setMessage(control, "hint", message);
        }
        // If there is already a YUI hint created for that control, update the message for the YUI widget
        ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.hintTooltipForControl);
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
                    // Disable this tooltip, but keep the object tied to the control
                    currentTooltip.cfg.setProperty("disabled", true);
                } else {
                    // Update the tooltip message
                    currentTooltip.cfg.setProperty("text", message);
                    currentTooltip.cfg.setProperty("disabled", false);
                }
            }
        }

    },

    /**
     * Sets focus to the specified control. This is called by the JavaScript code
     * generated by the server, which we invoke on page load.
     */
    setFocus: function(controlId) {
        var control = ORBEON.util.Dom.get(controlId);
        // Keep track of the id of the last known control which has focus
        ORBEON.xforms.Globals.currentFocusControlId = controlId;
        ORBEON.xforms.Globals.currentFocusControlElement = control;
        ORBEON.xforms.Globals.maskFocusEvents = true;
        if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))) {
            // Look for radio button or check box that is is checked
            var formInputs = ORBEON.util.Dom.getElementsByName(control, "input");
            if (formInputs.length > 0) {
                var itemIndex = 0;
                var foundSelected = false;
                for (; itemIndex < formInputs.length; itemIndex++) {
                    var formInput = formInputs[itemIndex];
                    if (formInput && formInput.checked) {
                        foundSelected = true;
                        break;
                    }
                }
                // Set focus on either selected item if we found one or on first item otherwise
                ORBEON.util.Dom.focus(formInputs[foundSelected ? itemIndex : 0]);
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")
                && YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // Special case for RTE
            ORBEON.xforms.Page.getControl(control).setFocus();
        } else {
            // Generic code to find focusable descendant-or-self HTML element and focus on it
            var htmlControlNames = [ "input", "textarea", "select", "button", "a" ];
            var htmlControl = ORBEON.util.Dom.getElementByTagName(control, htmlControlNames);
            // If we found a control set the focus on it
            if (htmlControl != null) ORBEON.util.Dom.focus(htmlControl);
        }

        // Save current value as server value. We usually do this on focus, but for control where we set the focus
        // with xforms:setfocus, we still receive the focus event when the value changes, but after the change event
        // (which means we then don't send the new value to the server).
        if (ORBEON.xforms.ServerValueStore.get(controlId) == null) {
            var currentValue = ORBEON.xforms.Controls.getCurrentValue(control);
            ORBEON.xforms.ServerValueStore.set(controlId, currentValue);
        }
    },

    /**
     * On blur for a control: waits for the next Ajax response and if the control is invalid, add the class
     * xforms-invalid-visited. The code also tried to find the label for this control and add the class
     * xforms-alert-active-visited when necessary.
     */
    updateInvalidVisitedOnNextAjaxResponse: function(control) {
        if (! YAHOO.util.Dom.hasClass(control, "xforms-visited")) {
            ORBEON.xforms.Events.runOnNext(ORBEON.xforms.Events.ajaxResponseProcessedEvent, function() {
                if (YAHOO.util.Dom.hasClass(control, "xforms-invalid"))
                    YAHOO.util.Dom.addClass(control, "xforms-invalid-visited");
                var alertElement = ORBEON.xforms.Controls._getControlLHHA(control, "alert");
                if (alertElement != null && YAHOO.util.Dom.hasClass(alertElement, "xforms-alert-active"))
                    YAHOO.util.Dom.addClass(alertElement, "xforms-alert-active-visited");
            }, null, false);
        }
    },

    /**
     * Update the xforms-required-empty class as necessary.
     */
    updateRequiredEmpty: function(control, newValue) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-required")) {
            if (newValue == "") {
                YAHOO.util.Dom.addClass(control, "xforms-required-empty");
                YAHOO.util.Dom.removeClass(control, "xforms-required-filled");
                return true;
            } else {
                YAHOO.util.Dom.addClass(control, "xforms-required-filled");
                YAHOO.util.Dom.removeClass(control, "xforms-required-empty");
                return false;
            }
        } else {
            YAHOO.util.Dom.removeClass(control, "xforms-required-filled");
            YAHOO.util.Dom.removeClass(control, "xforms-required-empty");
            return false;
        }
    },

    autosizeTextarea: function(textarea) {
        if (textarea.tagName.toLowerCase() != "textarea")
            textarea = textarea.getElementsByTagName("textarea")[0];
        var scrollHeight = textarea.scrollHeight;
        var clientHeight = textarea.clientHeight;

        if (textarea.rows == -1)
            textarea.rows = 2;

        // In IE & Safari, scrollHeight is the length of vertical space text is taking in the textarea.
        // In Firefox, it is the greater of text or textarea height. Here, we're interested in getting the height of text
        // inside the text area (and not textarea height), we suppress textarea height to 0.
        // So that scrollHeight will always return the vertical space text is taking in a text area. After  that we
        // remove the height property, so that effect of setting of height (to 0) doesn't get proliferated elsewhere.
        if (ORBEON.xforms.Globals.isFF3OrNewer) {
			textarea.style.height = 0;
			scrollHeight = textarea.scrollHeight;
            textarea.style.height = null;
		}
        var rowHeight = clientHeight / textarea.rows;
        var linesAdded = 0;

        if (scrollHeight > clientHeight) {
            // Grow
            while (scrollHeight >= clientHeight) {
                textarea.rows = textarea.rows + 1;
                if (textarea.clientHeight <= clientHeight) {
                    // If adding a row didn't increase the height if the text area, there is nothing we can do, so stop here.
                    // This prevents an infinite loops happening with IE when the control is disabled.
                    break;
                }
                clientHeight = textarea.clientHeight;
                linesAdded++;

            }
        } else if (scrollHeight < clientHeight) {
            // Shrink
            while (textarea.rows > XFORMS_WIDE_TEXTAREA_MIN_ROWS && scrollHeight < clientHeight - rowHeight) {
                textarea.rows = textarea.rows - 1;
                if (textarea.clientHeight >= clientHeight) {
                    // If removing a row didn't decrease the height if the text area, there is nothing we can do, so stop here.
                    // This prevents an infinite loops happening with IE when the control is disabled.
                    break;
                }
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
                if (ORBEON.util.Dom.isElement(formChild) && YAHOO.util.Dom.hasClass(formChild, "xforms-help-panel")) {
                    // Create YUI dialog for help based on template
                    YAHOO.util.Dom.generateId(formChild);
                    YAHOO.util.Dom.removeClass(formChild, "xforms-initially-hidden");
                    ORBEON.xforms.Globals.lastDialogZIndex += 2;
                    var helpPanel = new YAHOO.widget.Panel(formChild.id, {
                        modal: true,
                        fixedcenter: false,
                        underlay: "shadow",
                        visible: false,
                        constraintoviewport: true,
                        draggable: true,
                        effect: {effect: YAHOO.widget.ContainerEffect.FADE, duration: 0.3},
                        zIndex: ORBEON.xforms.Globals.lastDialogZIndex
                    });
                    helpPanel.render();
                    helpPanel.element.style.display = "none";
                    ORBEON.xforms.Globals.formHelpPanel[form.id] = helpPanel;

                    // Find div for help body
                    var bodyDiv = ORBEON.util.Dom.getChildElementByClass(formChild, "bd");
                    ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id] =
                        YAHOO.util.Dom.getElementsByClassName("xforms-help-panel-message", null, bodyDiv)[0];

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
            var horizontalConstraint = formHelpPanelRegion.left >= scrollX && formHelpPanelRegion.right <= scrollX + viewPortWidth;
            // Reposition if any constraint is not met
            showAndRepositionPanel = !verticalConstraint || !horizontalConstraint;
        }

        // Show and reposition dialog when needed
        if (showAndRepositionPanel) {
            var controlContainer = ORBEON.util.Utils.isNewXHTMLLayout() ? control : control.parentNode;
            var helpImage = ORBEON.util.Dom.getChildElementByClass(controlContainer, "xforms-help-image");
            ORBEON.xforms.Globals.formHelpPanel[form.id].element.style.display = "block";
            ORBEON.xforms.Globals.formHelpPanel[form.id].cfg.setProperty("context", [helpImage, "bl", "tl"]);
            ORBEON.xforms.Globals.formHelpPanel[form.id].show();
            ORBEON.xforms.Globals.lastDialogZIndex += 2;
            ORBEON.xforms.Globals.formHelpPanel[form.id].cfg.setProperty("zIndex", ORBEON.xforms.Globals.lastDialogZIndex);
        }

        // Set focus on close button if visible (we don't want to set the focus on the close button if not
        // visible as this would make the help panel scroll down to the close button)
        var bdDiv = ORBEON.xforms.Globals.formHelpPanelMessageDiv[form.id].parentNode;
        if (bdDiv.scrollHeight <= bdDiv.clientHeight)
            ORBEON.xforms.Globals.formHelpPanelCloseButton[form.id].focus();
    },

    showDialog: function(controlId, neighbor) {
        var divElement = ORBEON.util.Dom.get(controlId);
        var yuiDialog = ORBEON.xforms.Globals.dialogs[controlId];

        // Take out the focus from the current control. This is particularly important with non-modal dialogs
        // opened with a minimal trigger, otherwise we have a dotted line around the link after it opens.
        if (ORBEON.xforms.Globals.currentFocusControlId != null) {
            var focusedElement = ORBEON.util.Dom.get(ORBEON.xforms.Globals.currentFocusControlId);
            if (focusedElement != null) focusedElement.blur();
        }

        // Render the dialog if needed
        if (YAHOO.util.Dom.hasClass(divElement, "xforms-initially-hidden")) {
            YAHOO.util.Dom.removeClass(divElement, "xforms-initially-hidden");
            yuiDialog.render();
        }

        // Reapply those classes. Those are classes added by YUI when creating the dialog, but they are then removed
        // by YUI if you close the dialog using the "X". So when opening the dialog, we add those again, just to make sure.
        // A better way to handle this would be to create the YUI dialog every time when we open it, instead of doing this
        // during initialization.
        YAHOO.util.Dom.addClass(yuiDialog.innerElement, "yui-module");
        YAHOO.util.Dom.addClass(yuiDialog.innerElement, "yui-overlay");
        YAHOO.util.Dom.addClass(yuiDialog.innerElement, "yui-panel");
        // Fixes cursor Firefox issue; more on this in dialog init code
        yuiDialog.element.style.display = "block";
        // Show the dialog
        yuiDialog.show();
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
        }
    },

    /**
     * Return the widget that applies to a given control.
     */
    getWidget: function(control) {
        for (var widgetClass in ORBEON.widgets) {
            var widget = ORBEON.widgets[widgetClass];
            if (widget.extending != null && widget.appliesToControl(control)) {
                return widget;
            }
        }
    },

    /**
     * Called when a control is removed from the DOM. We trash all the information we might store about this control.
     */
    deleteControl: function(control) {
        ORBEON.xforms.ServerValueStore.remove(control.id);
        ORBEON.xforms.Globals.hintTooltipForControl[control.id] = null;
        ORBEON.xforms.Globals.alertTooltipForControl[control.id] = null;
        ORBEON.xforms.Globals.helpTooltipForControl[control.id] = null;
        ORBEON.xforms.Globals.dialogs[control.id] = null;
        ORBEON.xforms.Globals.dialogMinimalLastMouseOut[control.id] = null;
    }
};

ORBEON.xforms.FlatNesting = {

    /**
     * For nested groups:
     *
     *      <td id="group-begin-outer-group-flat" class="xforms-group-begin-end">
     *          ...
     *          <td id="group-begin-inner-group-flat" class="xforms-group-begin-end">
     *              ...
     *          <td id="group-end-inner-group-flat" class="xforms-group-begin-end">
     *          ...
     *      <td id="group-end-outer-group-flat" class="xforms-group-begin-end">
     *
     * For nested repeats (specific iteration of the outer repeat):
     *
     *      <span class="xforms-repeat-delimiter">
     *          ...
     *          <span class="xforms-repeat-begin-end" id="repeat-begin-inner-repeat·1">
     *          <span class="xforms-repeat-delimiter">
     *              ...
     *          <span class="xforms-repeat-begin-end" id="repeat-end-inner-repeat·1"></span>
     *          ...
     *      <span class="xforms-repeat-delimiter">
     */

    isGroupBeginEnd: function(node) { return node.nodeType == ELEMENT_TYPE && YAHOO.util.Dom.hasClass(node, "xforms-group-begin-end"); },
    isGroupBegin: function(node) { return this.isGroupBeginEnd(node) && node.id.indexOf("group-begin-") == 0; },
    isGroupEnd: function(node) { return this.isGroupBeginEnd(node) && node.id.indexOf("group-end-") == 0; },
    isRepeatBeginEnd: function(node) { return node.nodeType == ELEMENT_TYPE && YAHOO.util.Dom.hasClass(node, "xforms-repeat-begin-end"); },
    isRepeatBegin: function(node) { return this.isRepeatBeginEnd(node) && node.id.indexOf("repeat-begin-") == 0; },
    isRepeatEnd: function(node) { return this.isRepeatBeginEnd(node) && node.id.indexOf("repeat-end-") == 0; },
    isRepeatDelimiter: function(node) { return node.nodeType == ELEMENT_TYPE && YAHOO.util.Dom.hasClass(node, "xforms-repeat-delimiter"); },
    isBegin: function(node) { return this.isGroupBegin(node) || this.isRepeatBegin(node); },
    isEnd: function(node) { return this.isGroupEnd(node) || this.isRepeatEnd(node); },

    /**
     * Start applying foldFunction to all the ancestors of startNode, and stops if foldFunction returns stopValue.
     *
     * @param startNode     Node we start with: group begin or repeat delimiter
     * @param startValue    Start value for folding
     * @param foldFunction  function(beginNode, value) -> value
     * @param stopValue     Stop folding if fold function returns this value
     */
    foldAncestors: function(startNode, startValue, foldFunction, stopValue) {
        var FN = ORBEON.xforms.FlatNesting;

        // Determine if this is a group or a repeat
        var isGroup = FN.isGroupBegin(startNode);
        var isRepeat = FN.isRepeatDelimiter(startNode);

        // Iterate over previous sibling nodes
        var depth = 0;
        var currentNode = startNode;
        var currentValue = startValue;
        while (true) {
            currentNode = YAHOO.util.Dom.getPreviousSibling(currentNode);
            if (currentNode == null) break;
            if (currentNode.nodeType == ELEMENT_TYPE) {
                if (FN.isEnd(currentNode)) depth++;
                if (FN.isBegin(currentNode)) depth--;
                if (depth < 0 && ((isGroup && FN.isGroupEnd(currentNode)) || (isRepeat && FN.isRepeatBegin(currentNode)))) {
                    currentValue = foldFunction(currentNode, currentValue);
                    if (currentValue == stopValue) return stopValue;
                }
            }
        }
        return currentValue;
    },

    /**
     * Start applying foldFunction to descendants of startNode.
     *
     * @param startNode
     * @param startValue
     * @param foldFunction  function(node, value) -> value
     * @param stopValue
     */
    foldDescendants: function(startNode, startValue, foldFunction, stopValue) {
        var FN = ORBEON.xforms.FlatNesting;

        // Determine if this a group or a repeat
        var isGroup = this.isGroupBegin(startNode);
        var isRepeat = this.isRepeatDelimiter(startNode);

        // Iterate of following sibling nodes
        var depth = 0;
        var stopDepth = 0;
        var currentNode = startNode;
        var valueStack = [];
        var currentValue = startValue;
        while (true) {
            currentNode = YAHOO.util.Dom.getNextSibling(currentNode);
            if (currentNode == null) break;
            if (currentNode.nodeType == ELEMENT_TYPE) {
                if (this.isBegin(currentNode)) {
                    // Begin marker
                    depth++;
                    if (stopDepth > 0) {
                        stopDepth++;
                    } else {
                        valueStack.push(currentValue);
                        currentValue = foldFunction(currentNode, currentValue);
                        if (currentValue == stopValue) stopDepth++;
                    }
                } else if (this.isEnd(currentNode)) {
                    // End marker
                    depth--;
                    if (depth < 0) break;
                    if (stopDepth > 0) {
                        stopDepth--;
                    } else {
                        currentValue = valueStack.pop();
                    }
                } else if (isRepeat && depth == 0 && this.isRepeatDelimiter(currentNode)) {
                    // Next repeat delimiter
                    break;
                } else {
                    // Other element
                    if (stopDepth == 0) currentValue = foldFunction(currentNode, currentValue);
                }
            }
        }
        return currentValue;
    },

    /**
     * Returns true if at least one ancestor or self matches the condition.
     *
     * @param startNode             Child node whose ancestors we explore
     * @param conditionFunction     function(node) -> boolean
     */
    hasAncestor: function(startNode, conditionFunction) {
        var FN = ORBEON.xforms.FlatNesting;

        return FN.foldAncestors(startNode, false, function(value, node) {
            return conditionFunction(node);
        }, true);
    },

    setRelevant: function(node, isRelevant) {
        var FN = ORBEON.xforms.FlatNesting;
        var YD = YAHOO.util.Dom;
        var OD = ORBEON.util.Dom;
        var OC = ORBEON.xforms.Controls;

        // Update class on group begin or delimiter
        if (isRelevant) YAHOO.util.Dom.removeClass(node, "xforms-disabled");
        else YAHOO.util.Dom.addClass(node, "xforms-disabled");

        // If this group/iteration becomes relevant, but has a parent that is non-relevant, we should not
        // remove xforms-disabled otherwise it will incorrectly show, so our job stops here
        if (isRelevant && FN.hasAncestor(node, function(node) {
            return YAHOO.util.Dom.hasClass(node, "xforms-disabled");
        })) return;

        FN.foldDescendants(node, null, function(node, value) {
            // Skip sub-tree if we are enabling and this sub-tree is disabled
            if (isRelevant && FN.isBegin(node) && YD.hasClass(node, "xforms-disabled")) return true;
            // Update disabled class on node
            if (isRelevant) {
                YD.removeClass(node, "xforms-disabled");
                YD.removeClass(node, "xforms-disabled-subsequent");
                OD.nudgeAfterDelay(node);
            } else {
                YD.addClass(node, "xforms-disabled-subsequent");
            }
            return false;
        }, true);
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
                // FCKeditor HTML area on Firefox: event target is the document, return the textarea
                return element.xformsElement;
            } else if (element.ownerDocument && element.ownerDocument.xformsElement) {
                // FCKeditor HTML area on IE: event target is the body of the document, return the textarea
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

                if (element.id
                        && ORBEON.util.String.endsWith(element.id, "_container")
                        && YAHOO.util.Dom.hasClass(element, "xforms-textarea")
                        && YAHOO.util.Dom.hasClass(element, "xforms-mediatype-text-html")
                        && ORBEON.util.Properties.htmlEditor.get() == "yui") {
                    // We may get a focus event on the container created by YUI. Instead, find the
                    // original nested textarea element.

                    return ORBEON.util.Dom.getChildElementByClass(element, "xforms-textarea");

                } else if (YAHOO.util.Dom.hasClass(element, "xforms-control")) {

                    // HACK: With the YUI RTE, the xforms-control and other classes get copied to a div generated by the RTE.
                    // When we get an event on that div, we just want to ignore it (it's not really an event for the RTE).
                    // This is a hack, because a better way to handle this would be to figure why those classes are copied
                    // and prevent that copy from happening.
                    return element.id == "" ? null : element;

                } else if (YAHOO.util.Dom.hasClass(element, "xforms-dialog")
                        || YAHOO.util.Dom.hasClass(element, "xforms-help-image")
                        || YAHOO.util.Dom.hasClass(element, "xforms-alert")) {
                    return element;
                }
            }
            // Go to parent and continue search
            element = element.parentNode;
        }
    },

    focus: function(event) {
        var eventTarget = YAHOO.util.Event.getTarget(event);
        // If the browser does not support capture, register listener for change on capture
        if (YAHOO.lang.isUndefined(document.addEventListener)) {
            YAHOO.util.Dom.generateId(eventTarget);
            var changeListenerElement = ORBEON.xforms.Globals.changeListeners[eventTarget.id];
            var needToRegisterChangeListener = YAHOO.lang.isUndefined(changeListenerElement) || changeListenerElement != eventTarget;
            if (needToRegisterChangeListener) {
                YAHOO.util.Event.addListener(eventTarget, "change", ORBEON.xforms.Events.change);
                ORBEON.xforms.Globals.changeListeners[eventTarget.id] = eventTarget;
            }
        }
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            // Control elements
            var targetControlElement = ORBEON.xforms.Events._findParentXFormsControl(eventTarget);
            // NOTE: Below we use getElementByIdNoCache() because it may happen that the element has been removed from
            // a repeat iteration. The id cache should be improved to handle this better so we don't have to do this.
            var currentFocusControlElement = ORBEON.xforms.Globals.currentFocusControlId != null ? ORBEON.util.Dom.get(ORBEON.xforms.Globals.currentFocusControlId) : null;

            if (targetControlElement != null) {
                // Store initial value of control if we don't have a server value already, and if this is is not a list
                // Initial value for lists is set up initialization, as when we receive the focus event the new value is already set.
                if (ORBEON.xforms.ServerValueStore.get(targetControlElement.id) == null
                        && ! YAHOO.util.Dom.hasClass(targetControlElement, "xforms-select-appearance-compact")) {
                    var controlCurrentValue = ORBEON.xforms.Controls.getCurrentValue(targetControlElement);
                    ORBEON.xforms.ServerValueStore.set(targetControlElement.id, controlCurrentValue);
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
                    && !YAHOO.util.Dom.hasClass(targetControlElement, "xforms-dialog")) {

                // Handle special value changes upon losing focus

                // HTML area and trees does not throw value change event, so we send the value change to the server
                // when we get the focus on the next control
                var changeValue = false;
                if (currentFocusControlElement != null) { // Can be null on first focus
                    if (YAHOO.util.Dom.hasClass(currentFocusControlElement, "xforms-textarea")
                            && YAHOO.util.Dom.hasClass(currentFocusControlElement, "xforms-mediatype-text-html")) {
                        changeValue = true;
                    } else if (YAHOO.util.Dom.hasClass(currentFocusControlElement, "xforms-select1-appearance-xxforms-tree")
                            || YAHOO.util.Dom.hasClass(currentFocusControlElement, "xforms-select-appearance-xxforms-tree")) {
                        changeValue = true;
                    }
                    // Send value change if needed
                    if (changeValue) xformsValueChanged(currentFocusControlElement, null);

                    // Handle DOMFocusOut
                    // Should send out DOMFocusOut only if no xxforms-value-change-with-focus-change was sent to avoid extra
                    // DOMFocusOut, but it is hard to detect correctly
                    events.push(new ORBEON.xforms.server.AjaxServer.Event(null, currentFocusControlElement.id, null, null, "DOMFocusOut"));
                }

                // Handle DOMFocusIn
                events.push(new ORBEON.xforms.server.AjaxServer.Event(null, targetControlElement.id, null, null, "DOMFocusIn"));

                // Keep track of the id of the last known control which has focus
                ORBEON.xforms.Globals.currentFocusControlId = targetControlElement.id;
                ORBEON.xforms.Globals.currentFocusControlElement = targetControlElement;

                // Fire events
                ORBEON.xforms.server.AjaxServer.fireEvents(events, true);
            }

        } else {
            ORBEON.xforms.Globals.maskFocusEvents = false;
        }
    },

    blur: function(event) {
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            var targetControlElement = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (targetControlElement != null) {
                ORBEON.xforms.Controls.updateInvalidVisitedOnNextAjaxResponse(targetControlElement);

                if (!YAHOO.util.Dom.hasClass(targetControlElement, "xforms-dialog")) {
                    // This is an event for an XForms control which is not a dialog

                    // We don't run this for dialogs, as there is not much sense doing this AND this causes issues with
                    // FCKEditor embedded within dialogs with IE. In that case, the editor gets a blur, then the
                    // dialog, which prevents detection of value changes in focus() above.

                    // Keep track of the id of the last known control which has focus
                    ORBEON.xforms.Globals.currentFocusControlId = targetControlElement.id;
                    ORBEON.xforms.Globals.currentFocusControlElement = targetControlElement;
                }

                if (ORBEON.widgets.YUICalendar.appliesToControl(targetControlElement)) {
                    ORBEON.widgets.YUICalendar.blur(event, targetControlElement);
                }
            }
        }
    },

    change: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            ORBEON.xforms.Controls.updateInvalidVisitedOnNextAjaxResponse(target);
            if (YAHOO.util.Dom.hasClass(target, "xforms-upload")) {
                // Dispatch change event to upload control
                ORBEON.xforms.Page.getControl(target).change();
            } else {
                // When we move out from a field, we don't receive the keyup events corresponding to keypress
                // for that field (go figure!). Se we reset here the count for keypress without keyup for that field.
                if (ORBEON.xforms.Globals.changedIdsRequest[target.id] != null)
                    ORBEON.xforms.Globals.changedIdsRequest[target.id] = 0;

                if (YAHOO.util.Dom.hasClass(target, "xforms-select1-appearance-compact")) {
                    // For select1 list, make sure we have exactly one value selected
                    var select = ORBEON.util.Utils.isNewXHTMLLayout()
                                  ? ORBEON.util.Dom.getElementByTagName(target, "select")
                                  : target;
                    if (select.value == "") {
                        // Stop end-user from deselecting last selected value
                        select.options[0].selected = true;
                    } else {
                        // Unselect options other than the first one
                        var foundSelected = false;
                        for (var optionIndex = 0; optionIndex < select.options.length; optionIndex++) {
                            var option = select.options[optionIndex];
                            if (option.selected) {
                                if (foundSelected) option.selected = false;
                                else foundSelected = true;
                            }
                        }
                    }
                } else if (YAHOO.util.Dom.hasClass(target, "xforms-type-time")
                        || (YAHOO.util.Dom.hasClass(target, "xforms-type-date") && !YAHOO.util.Dom.hasClass(target, "xforms-input-appearance-minimal"))
                        || YAHOO.util.Dom.hasClass(target, "xforms-type-dateTime")) {

                    // For time, date, and dateTime fields, magic-parse field, and if recognized replace by display value

                    function toDisplayValue(input, magicToJSDate, jsDateToDisplay) {
                        var jsDate = magicToJSDate(input.value);
                        if (jsDate != null)
                            input.value = jsDateToDisplay(jsDate);
                    }

                    // Handle first text field (time or date)
                    toDisplayValue(YAHOO.util.Dom.getElementsByClassName("xforms-input-input", null, target)[0],
                            YAHOO.util.Dom.hasClass(target, "xforms-type-time") ? ORBEON.util.DateTime.magicTimeToJSDate : ORBEON.util.DateTime.magicDateToJSDate,
                            YAHOO.util.Dom.hasClass(target, "xforms-type-time") ? ORBEON.util.DateTime.jsDateToFormatDisplayTime : ORBEON.util.DateTime.jsDateToFormatDisplayDate);
                    // Handle second text field for dateTime
                    if (YAHOO.util.Dom.hasClass(target, "xforms-type-dateTime"))
                        toDisplayValue(YAHOO.util.Dom.getElementsByClassName("xforms-input-input", null, target)[1],
                            ORBEON.util.DateTime.magicTimeToJSDate, ORBEON.util.DateTime.jsDateToFormatDisplayTime);
                }

                // Fire change event
                var controlCurrentValue = ORBEON.xforms.Controls.getCurrentValue(target);
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, controlCurrentValue, "xxforms-value-change-with-focus-change");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
            }
        }
    },

    /**
     * Rational:
     *      Remember that the user is editing this field, so don't overwrite when we receive an event
     *      from the server
     * Testing on key code:
     *      Ignore some key codes that won't modify the value of the field
     *      (including when key code if undefined, which the RTE triggers in some cases).
     * Testing on type control:
     *      We only do this for text fields and text areas, because for other inputs (say select/select1) the user
     *      can press a key that doesn't change the value of the field, in which case we *do* want to update the
     *      control with a new value coming from the server.
     */
    _isChangingKey: function(control, keyCode) {
        return ! YAHOO.lang.isUndefined(keyCode) &&
            keyCode != 9 && keyCode != 16 && keyCode != 17 && keyCode != 18 &&
            (YAHOO.util.Dom.hasClass(control, "xforms-input") || YAHOO.util.Dom.hasClass(control, "xforms-secret")
                    || YAHOO.util.Dom.hasClass(control, "xforms-textarea"));
    },

    keydown: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            if (ORBEON.xforms.Events._isChangingKey(target, event.keyCode)) {
                ORBEON.xforms.Globals.changedIdsRequest[target.id] =
                    ORBEON.xforms.Globals.changedIdsRequest[target.id] == null ? 1
                            : ORBEON.xforms.Globals.changedIdsRequest[target.id] + 1;
            }
            if (ORBEON.widgets.YUICalendar.appliesToControl(target)) {
                ORBEON.widgets.YUICalendar.keydown(event, target);
            }
        }
    },

    keypress: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Input field and auto-complete: trigger DOMActive when when enter is pressed
            if ((YAHOO.util.Dom.hasClass(target, "xforms-input") && !YAHOO.util.Dom.hasClass(target, "xforms-type-boolean"))
                    || YAHOO.util.Dom.hasClass(target, "xforms-secret")) {
                if (event.keyCode == 10 || event.keyCode == 13) {
                    // Prevent default handling of enter, which might be equivalent as a click on some trigger in the form
                    YAHOO.util.Event.preventDefault(event);
                    // Send a value change and DOM activate
                    var events = [
                        new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, ORBEON.xforms.Controls.getCurrentValue(target), "xxforms-value-change-with-focus-change"),
                        new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, null, "DOMActivate")
                    ];
                    ORBEON.xforms.server.AjaxServer.fireEvents(events, false);
                }
            }
        }
    },

    keyup: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Remember we have received the keyup for this element
            if (ORBEON.xforms.Events._isChangingKey(target, event.keyCode))
                ORBEON.xforms.Globals.changedIdsRequest[target.id]--;
            // Incremental control: treat keypress as a value change event
            if (YAHOO.util.Dom.hasClass(target, "xforms-incremental")) {
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, ORBEON.xforms.Controls.getCurrentValue(target), "xxforms-value-change-with-focus-change");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], true);
                ORBEON.xforms.Controls.updateInvalidVisitedOnNextAjaxResponse(target);
            }

            // Resize wide text area
            if (YAHOO.util.Dom.hasClass(target, "xforms-textarea-appearance-xxforms-autosize")) {
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

    _showToolTip: function(tooltipForControl, control, target, toolTipSuffix, message, delay, event) {

        // If we already have a tooltip for this control, but that the control is not in the page anymore, destroy the tooltip
        if (YAHOO.lang.isObject(tooltipForControl[control.id])) {
            if (! YAHOO.util.Dom.inDocument(tooltipForControl[control.id].orbeonControl, document)) {
                // Prevent the tooltip from becoming visible on mouseover
                tooltipForControl[control.id].cfg.setProperty("disabled", true);
                // If visible, hide the tooltip right away, otherwise it will only be hidden a few seconds later
                tooltipForControl[control.id].hide();
                tooltipForControl[control.id] = null;
            }
        }

        // Create tooltip if have never "seen" this control
        if (tooltipForControl[control.id] == null) {
            if (message != "") {
                // We have a hint, initialize YUI tooltip
                var yuiTooltip = new YAHOO.widget.Tooltip(control.id + toolTipSuffix, {
                    context: target.id,
                    text: message,
                    showDelay: delay,
                    effect: {effect: YAHOO.widget.ContainerEffect.FADE, duration: 0.2},
                    // We provide here a "high" zIndex value so the tooltip is "always" displayed on top over everything else.
                    // Otherwise, with dialogs, the tooltip might end up being below the dialog and be invisible.
                    zIndex: 1000
                });
                yuiTooltip.orbeonControl = control;
                var context = ORBEON.util.Dom.get(target.id);
                // Send the mouse move event, because the tooltip gets positioned when receiving a mouse move.
                // Without this, sometimes the first time the tooltip is shows at the top left of the screen
                yuiTooltip.onContextMouseMove.call(context, event, yuiTooltip);
                // Send the mouse over event to the tooltip, since the YUI tooltip didn't receive it as it didn't
                // exist yet when the event was dispatched by the browser
                yuiTooltip.onContextMouseOver.call(context, event, yuiTooltip);
                // Save reference to YUI tooltip
                tooltipForControl[control.id] = yuiTooltip;
            } else {
                // Remember we looked at this control already
                tooltipForControl[control.id] = true;
            }
        }
    },

    mouseover: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            // Control tooltip
            if (! YAHOO.util.Dom.hasClass(document.body, "xforms-disable-hint-as-tooltip")) {
                var message = ORBEON.xforms.Controls.getHintMessage(target);
                if (YAHOO.util.Dom.hasClass(target, "xforms-trigger") || YAHOO.util.Dom.hasClass(target, "xforms-submit")) {
                    // Remove the title, to avoid having both the YUI tooltip and the browser tooltip based on the title showing up
                    var formElement = ORBEON.util.Dom.getElementByTagName(target, ["a", "button"]);
                    formElement.title = "";
                }
                ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.hintTooltipForControl, target, target, "-orbeon-hint-tooltip", message, 200, event);
            }

            // Alert tooltip
            if (YAHOO.util.Dom.hasClass(target, "xforms-alert-active")
                    && ! YAHOO.util.Dom.hasClass(document.body, "xforms-disable-alert-as-tooltip")) {
                // NOTE: control may be null if we have <div for="">. Using target.getAttribute("for") returns a proper
                // for, but then tooltips sometimes fail later with Ajax portlets in particular. So for now, just don't
                // do anything if there is no control found.
                var control = ORBEON.xforms.Controls.getControlForLHHA(target, "alert");
                if (control) {
                    // The 'for' can point to a form field which is inside the element representing the control
                    if (! (YAHOO.util.Dom.hasClass(control, "xforms-control") || YAHOO.util.Dom.hasClass(control, "xforms-group")))
                        control = YAHOO.util.Dom.getAncestorByClassName(control, "xforms-control");
                    if (control) {
                        var message = ORBEON.xforms.Controls.getAlertMessage(control);
                        YAHOO.util.Dom.generateId(target);
                        ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.alertTooltipForControl, control, target, "-orbeon-alert-tooltip", message, 10, event);
                    }
                }
            } else if (YAHOO.util.Dom.hasClass(target, "xforms-dialog-appearance-minimal")) {
                // Minimal dialog: record more is back inside the dialog
                ORBEON.xforms.Globals.dialogMinimalLastMouseOut[target.id] = -1;
            }

            // Help tooltip
            if (ORBEON.util.Properties.helpTooltip.get()
                    && YAHOO.util.Dom.hasClass(target, "xforms-help-image")) {
                // Get help element which is right after the image; there might be a text node between the two elements

                var helpElement = target.nextSibling;
                if (!ORBEON.util.Dom.isElement(helpElement))
                    helpElement = helpElement.nextSibling;
                // Get control
                var control = ORBEON.xforms.Controls.getControlForLHHA(target, "help-image");
                if (control) {
                    // The xforms:input is a unique case where the 'for' points to the input field, not the element representing the control
                    if (YAHOO.util.Dom.hasClass(control, "xforms-input-input"))
                        control = YAHOO.util.Dom.getAncestorByClassName(control, "xforms-control");
                        var message = ORBEON.xforms.Controls.getHelpMessage(control);
                        YAHOO.util.Dom.generateId(target);
                    ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.helpTooltipForControl, control, target, "-orbeon-help-tooltip", message, 0, event);
                }
            }

            // Check if this control is inside a minimal dialog, in which case we are also inside that dialog
            var current = target;
            while (current != null && current != document) {
                if (YAHOO.util.Dom.hasClass(current, "xforms-dialog-appearance-minimal")) {
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
            // Send the mouseout event to the YUI tooltip to handle the case where: (1) we get the mouseover event, (2) we
            // create a YUI tooltip, (3) the mouseout happens before the YUI dialog got a chance to register its listener
            // on mouseout, (4) the YUI dialog is only dismissed after autodismissdelay (5 seconds) leaving a trail.
            var yuiTooltip = ORBEON.xforms.Globals.hintTooltipForControl[target.id];
            if (! YAHOO.util.Dom.hasClass(document.body, "xforms-disable-hint-as-tooltip")
                    && YAHOO.lang.isObject(yuiTooltip)) {
                yuiTooltip.onContextMouseOut.call(target.id, event, yuiTooltip);
            }
        }
    },

    click: function(event) {
        // Stop processing if the mouse button that was clicked is not the left button
        // See: http://www.quirksmode.org/js/events_properties.html#button
        if (event.button != 0 && event.button != 1) return;
        ORBEON.xforms.Events.clickEvent.fire(event);
        var originalTarget = YAHOO.util.Event.getTarget(event);
        if (YAHOO.lang.isObject(originalTarget) && YAHOO.lang.isBoolean(originalTarget.disabled) && originalTarget.disabled) {
            // IE calls the click event handler on clicks on disabled controls, which Firefox doesn't.
            // To make processing more similar on all browsers, we stop going further here if we go a click on a disabled control.
            return;
        }
        var target = ORBEON.xforms.Events._findParentXFormsControl(originalTarget);

        if (target != null && YAHOO.util.Dom.hasClass(target, "xforms-output")) {
            // Click on output
            // Translate this into a focus event
            ORBEON.xforms.Events.focus(event);
        } else if (target != null && (YAHOO.util.Dom.hasClass(target, "xforms-trigger") || YAHOO.util.Dom.hasClass(target, "xforms-submit"))) {
            // Click on trigger
            YAHOO.util.Event.preventDefault(event);
            if (YAHOO.util.Dom.hasClass(target, "xxforms-offline-save")) {
                // This is a trigger take commits the data changed so far in Gears
                ORBEON.xforms.Offline.storeEvents(ORBEON.xforms.Offline.memoryOfflineEvents);
                ORBEON.xforms.Offline.memoryOfflineEvents = [];
            }
            if (YAHOO.util.Dom.hasClass(target, "xxforms-online")) {
                // This is a trigger take takes the form back online
                ORBEON.xforms.Offline.takeOnline();
            }
            if (!YAHOO.util.Dom.hasClass(target, "xforms-readonly")) {
                // If this is an anchor and we didn't get a chance to register the focus event,
                // send the focus event here. This is useful for anchors (we don't listen on the
                // focus event on those, and for buttons on Safari which does not dispatch the focus
                // event for buttons.
                ORBEON.xforms.Events.focus(event);
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, null, "DOMActivate");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], false);

                if (YAHOO.util.Dom.hasClass(target, "xforms-trigger-appearance-modal")) {
                    // If click on a modal trigger, we want to prevent any further interaction with the form until
                    // we get a response to this Ajax request from the server.
                    // Remove focus from trigger, otherwise user can press enter and activate the trigger even after the
                    // the progress panel is displayed.
                    target.blur();
                    // Display progress panel if trigger with "xforms-trigger-appearance-modal" class was activated
                    ORBEON.util.Utils.displayModalProgressPanel(ORBEON.xforms.Controls.getForm(target).id);
                }
            }
        } else if (target != null &&
                   (YAHOO.util.Dom.hasClass(target, "xforms-select1-appearance-full")
                || YAHOO.util.Dom.hasClass(target, "xforms-select-appearance-full")
                || (YAHOO.util.Dom.hasClass(target, "xforms-input") && YAHOO.util.Dom.hasClass(target, "xforms-type-boolean")))) {
            // Click on checkbox or radio button

            // Update classes right away to give user visual feedback
            ORBEON.xforms.Controls._setRadioCheckboxClasses(target);
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, ORBEON.xforms.Controls.getCurrentValue(target), "xxforms-value-change-with-focus-change");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);

        } else if (target != null && YAHOO.util.Dom.hasClass(originalTarget, "xforms-type-date") ) {
            // Click on calendar inside input field
            ORBEON.widgets.YUICalendar.click(event, target);
        } else if (target != null && YAHOO.util.Dom.hasClass(target, "xforms-upload") && YAHOO.util.Dom.hasClass(originalTarget, "xforms-upload-remove")) {
            // Click on remove icon in upload control
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, "", "xxforms-value-change-with-focus-change");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        } else if (target != null && YAHOO.util.Dom.hasClass(target, "xforms-select1-appearance-xxforms-menu")) {
            // Click on menu item

            // Find what is the position in the hierarchy of the item
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
                } else if (currentParent.tagName.toLowerCase() == "div" && YAHOO.util.Dom.hasClass(currentParent, "yuimenubar")) {
                    // Got to the top of the tree
                    break;
                }
                currentParent = currentParent.parentNode;
            }
            positions = positions.reverse();

            // Find value for this item
            var currentChildren = ORBEON.xforms.Globals.menuItemsets[target.id];
            var nodeInfo = null;
            for (var positionIndex = 0; positionIndex < positions.length; positionIndex++) {
                var position = positions[positionIndex];
                nodeInfo = currentChildren[position];
                currentChildren = nodeInfo.children;
            }

            // Send value change to server
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, nodeInfo.value, "xxforms-value-change-with-focus-change");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
            // Close the menu
            ORBEON.xforms.Globals.menuYui[target.id].clearActiveItem();
        } else if (target != null && YAHOO.util.Dom.hasClass(target, "xforms-help-image")) {
            // Help image

            // Get control for this help image
            var control = ORBEON.xforms.Controls.getControlForLHHA(target, "help-image");
            if (ORBEON.util.Properties.helpHandler.get()) {
                // We are sending the xforms-help event to the server and the server will tell us what do to
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, null, "xforms-help");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], false);

            } else {
                // If the servers tells us there are no event handlers for xforms-help in the page,
                // we can avoid a round trip and show the help right away
                ORBEON.xforms.Controls.showHelp(control);
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
                            var event = new ORBEON.xforms.server.AjaxServer.Event(form, targetId, null, null, "DOMFocusIn");
                            ORBEON.xforms.server.AjaxServer.fireEvents([event]);
                            foundRepeatBegin = true;
                            break;
                        } else if (YAHOO.util.Dom.hasClass(sibling, "xforms-repeat-delimiter")) {
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
     * Called upon resizing.
     */
    _resize: function() {
        // Move hidden tooltips to the top-left of the document to avoid having a useless scrollbar show up in
        // case they are outside of the viewport.
        var collections = [ORBEON.xforms.Globals.hintTooltipForControl, ORBEON.xforms.Globals.helpTooltipForControl, ORBEON.xforms.Globals.alertTooltipForControl];
        for (var i = 0; i < 3; i++) {
            var collection = collections[i];
            for (var control in collection) {
                var tooltip = collection[control];
                if (tooltip != null && tooltip != true) {
                    if (YAHOO.lang.isObject(tooltip.element) && tooltip.element.style.visibility == "hidden") {
                        tooltip.element.style.top = 0;
                        tooltip.element.style.left = 0;
                    }
                }
            }
        }
    },

    /**
     * Called upon scrolling or resizing.
     */
    scrollOrResize: function() {
        ORBEON.xforms.Events._resize();
        // Adjust position of dialogs with "constraintoviewport" since YUI doesn't do it automatically
        // NOTE: comment this one out for now, as that causes issues like unreachable buttons for large dialogs, and funny scrolling
//        for (var yuiDialogId in ORBEON.xforms.Globals.dialogs) {
//            var yuiDialog = ORBEON.xforms.Globals.dialogs[yuiDialogId];
//            if (yuiDialog.cfg.getProperty("visible") && yuiDialog.cfg.getProperty("constraintoviewport")) {
//                yuiDialog.cfg.setProperty("xy", yuiDialog.cfg.getProperty("xy"));
//            }
//        }
    },

    sliderValueChange: function(offset) {
        // Notify server that value changed
        var rangeControl = ORBEON.util.Dom.get(this.id);
        if (ORBEON.util.Utils.isNewXHTMLLayout())
            rangeControl = rangeControl.parentNode;

        var value = offset / 200;
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, rangeControl.id, null, String(value), "xxforms-value-change-with-focus-change");
        ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
    },

    /**
     * Called by the YUI menu library when a click happens a menu entry.
     */
    menuClick: function (eventType, arguments, userObject) {
        var menu = userObject["menu"];
        var value = userObject["value"];
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, menu.id, null, value, "xxforms-value-change-with-focus-change");
        ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
    },

    /**
     * Event listener on dialogs called by YUI when the dialog is closed. If the dialog was closed by the user (not
     * because the server told use to close the dialog), then we want to notify the server that this happened.
     */
    dialogClose: function(type, args, me) {
        if (! ORBEON.xforms.Globals.maskDialogCloseEvents) {
            var dialogId = me;
            var dialog = ORBEON.util.Dom.get(dialogId);
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, dialog.id, null, null, "xxforms-dialog-close");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    },

    /**
     * Event listener on dialogs called by YUI when the dialog is shown.
     */
	dialogShow: function(type, args, me) {
		if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
            // On IE6, when the dialog is opened for the second time, part of the dialog are not visible.
            // Setting the class again on the dialog gives notch to IE and is hack to get around this issue.
            var dialogId = me;
			var dialog = ORBEON.util.Dom.get(dialogId);
			dialog.className = dialog.className;
		}
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
        var isIncremental = YAHOO.util.Dom.hasClass(control, "xforms-incremental");
        if (ORBEON.xforms.Globals.currentFocusControlId != control.id) {// not sure we need to do this test here since focus() may do it anyway
            // We are coming from another control, simulate a focus on this control
            var focusEvent = { target: control };
            ORBEON.xforms.Events.focus(focusEvent);
        }
        // Preemptively store current control in previousDOMFocusOut, so when another control gets
        // the focus it will send the value of this control to the server
        ORBEON.xforms.Globals.currentFocusControlId = control.id;
        ORBEON.xforms.Globals.currentFocusControlElement = control;
    },

    treeClickValueUpdated: function(control) {
        // If we are in incremental mode, send value to the server on every click
        if (YAHOO.util.Dom.hasClass(control, "xforms-incremental")) {
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, ORBEON.xforms.Controls.getCurrentValue(control), "xxforms-value-change-with-focus-change");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    },

    /**
     * xforms:select tree: handle click on check box
     */
    treeCheckClick: function() {
        var tree = this.tree;
        var control = tree.getEl();
        if (! YAHOO.util.Dom.hasClass(control, "xforms-control")) control = control.parentNode;
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
    treeLabelClick: function(object) {

        // Depending who calls this listeners, we either get the node directly (for the enterKeyPressed
        // and labelClick events) or we get an object which contains the node (for clickEvent).
        var node = ! YAHOO.lang.isUndefined(object._type) && (object._type == "TextNode" || object._type == "TaskNode")
            ? object : object.node;

        var yuiTree = this;
        var control = document.getElementById(yuiTree.id);
        if (ORBEON.util.Utils.isNewXHTMLLayout()) control = control.parentNode;
        var allowMultipleSelection = YAHOO.util.Dom.hasClass(control, "xforms-select");
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
            var currentValue = ORBEON.xforms.Controls.getCurrentValue(control);
            var oldNode = yuiTree.getNodeByProperty("value", currentValue);
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
            YAHOO.util.Dom.addClass(detailsHidden, "xforms-disabled-subsequent");
            YAHOO.util.Dom.removeClass(detailsShown, "xforms-disabled");
            YAHOO.util.Dom.removeClass(detailsShown, "xforms-disabled-subsequent");
        } else {
            YAHOO.util.Dom.removeClass(detailsHidden, "xforms-disabled");
            YAHOO.util.Dom.removeClass(detailsHidden, "xforms-disabled-subsequent");
            YAHOO.util.Dom.addClass(detailsShown, "xforms-disabled-subsequent");
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
        YAHOO.util.Dom.removeClass(detailsHidden, "xforms-disabled");
        YAHOO.util.Dom.removeClass(detailsHidden, "xforms-disabled-subsequent");
        YAHOO.util.Dom.addClass(detailsShown, "xforms-disabled-subsequent");
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
        if (yuiDialog.element.style.display == "block") {
            // Abord if one of the parents is drop-down dialog
            var current = YAHOO.util.Event.getTarget(event);
            var foundDropDownParent = false;
            while (current != null && current != document) {
                if (YAHOO.util.Dom.hasClass(current, "xforms-dialog-appearance-minimal")) {
                    foundDropDownParent = true;
                    break;
                }
                current = current.parentNode;
            }
            if (!foundDropDownParent) {
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, yuiDialog.id, null, null, "xxforms-dialog-close");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
            }
        }
    },

    /**
     * Called when the mouse is outside of a minimal dialog for more than a certain amount of time.
     * Here we close the dialog if appropriate.
     */
    dialogMinimalCheckMouseIn: function(yuiDialog) {
        var current = new Date().getTime();
        if (yuiDialog.element.style.display == "block"
                && ORBEON.xforms.Globals.dialogMinimalLastMouseOut[yuiDialog.element.id] != -1
                && current - ORBEON.xforms.Globals.dialogMinimalLastMouseOut[yuiDialog.element.id] >= ORBEON.util.Properties.delayBeforeCloseMinimalDialog.get()) {
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, yuiDialog.element.id, null, null, "xxforms-dialog-close");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    },

    /**
     * A method for sending a heartbeat event if no event has sent to server in
     * the last time interval determined by session-heartbeat-delay property
     */
    sendHeartBeatIfNeeded: function(heartBeatDelay) {
        var currentTime = new Date().getTime();
        if ((currentTime - ORBEON.xforms.Globals.lastEventSentTime) >= heartBeatDelay) {
            var heartBeatDiv = ORBEON.util.Dom.get("xforms-heartbeat");
            if (heartBeatDiv == null) {
                var form;
                for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
                    var candidateForm = document.forms[formIndex];
                    if (YAHOO.util.Dom.hasClass(candidateForm, "xforms-form")) {
                        form = candidateForm;
                        break;
                    }
                }
                var heartBeatDiv = document.createElement("div");
                heartBeatDiv.className = "xforms-heartbeat";
                heartBeatDiv.id = "xforms-heartbeat";
                form.appendChild(heartBeatDiv);
            }
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, heartBeatDiv.id, null, null, "xxforms-session-heartbeat");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    },

    runOnNext: function(event, listener,  obj, overrideContext) {
        function worker() {
            event.unsubscribe(worker);
            if (overrideContext) {
                listener.call(obj);
            } else {
                listener(obj);
            }
        }
        event.subscribe(worker);
    },

    orbeonLoadedEvent: new YAHOO.util.CustomEvent("orbeonLoaded"),
    ajaxResponseProcessedEvent: new YAHOO.util.CustomEvent("ajaxResponseProcessed"),
    clickEvent: new YAHOO.util.CustomEvent("clickEvent"),
    errorEvent: new YAHOO.util.CustomEvent("errorEvent"),
    yuiCalendarCreated: new YAHOO.util.CustomEvent("yuiCalendarCreated")
};

(function() {

    var OUD = ORBEON.util.Dom;
    var YUD = YAHOO.util.Dom;

    ORBEON.xforms.FullUpdate = {

        /** @private @type {Object.<string, Array.<string>>} */                 _fullUpdateToComponents: {},
        /** @private @type {Object.<string, boolean>} */                        _knownComponents: {},
        /** @private @type {Object.<string, function(HTMLElement): Object>} */  _componentsXblClass: {},

        clinit: function() {
            ORBEON.xforms.XBL.componentInitialized.subscribe(this.onComponentInitialized, this, true);
        },

        /**
         * Called whenever a component is initialized.
         *
         * @param component
         * @return {void}
         */
        onComponentInitialized: function(component) {
            if (! this._knownComponents[component.container.id]) {

                // Find if this instance is in a full update container
                /** @type {HTMLElement} */ var fullUpdate = null;
                OUD.existsAncestorOrSelf(component.container, function(node) {
                    return YUD.hasClass(node, "xforms-update-full") ? (fullUpdate = node, true) : false;
                });

                // This component is inside a full update
                if (fullUpdate != null) {
                    // Remember that component is associated with full update
                    if (this._fullUpdateToComponents[fullUpdate.id] == null) this._fullUpdateToComponents[fullUpdate.id] = [];
                    this._fullUpdateToComponents[fullUpdate.id].push(component.container.id);
                    // Remember factory for this component
                    this._componentsXblClass[component.container.id] = component.xblClass;
                }

                // Remember we looked at this one, so we don't have to do it again
                this._knownComponents[component.container.id] = true;
            }
        },

        /**
         * Called when a full update is performed.
         *
         * @param {!string} fullUpdateId    Id of the control that contains the section that was updated.
         */
        onFullUpdateDone: function(fullUpdateId) {

            // Re-initialize all the existing XBL components inside the this container
            var me = this;
            var componentIds = this._fullUpdateToComponents[fullUpdateId];
            if (componentIds) {
                _.each(componentIds, function(componentId) {
                    /** @type {HTMLElement} */ var componentContainer = OUD.get(componentId);
                    if (componentContainer != null) {
                        // Call instance which will call init if necessary
                        var component = me._componentsXblClass[componentId].instance(componentContainer);
                        if (component.enabled) component.enabled();
                    }
                });
            }

            // Re-initialize all the legacy/built-in controls
            ORBEON.xforms.Init.insertedElement(YUD.get(fullUpdateId));
        }
    };

    ORBEON.onJavaScriptLoaded.subscribe(ORBEON.xforms.FullUpdate.clinit, ORBEON.xforms.FullUpdate, true);
})();

ORBEON.xforms.XBL = {

    /**
     * Base class for classes implementing an XBL component.
     */
    _BaseClass: function() {
        var BaseClass = function() {};
        BaseClass.prototype = {

            /**
             * The HTML element that contains the component on the page.
             */
            container: null
        }
    }(),

    /**
     * To be documented on Wiki.
     */
    declareClass: function(xblClass, cssClass) {
        var doNothingSingleton = null;
        var instanceAlreadyCalled = false;

        // Define factory function for this class
        xblClass.instance = function(target) {
            var hasInit = ! YAHOO.lang.isUndefined(xblClass.prototype.init);

            // Get the top-level element in the HTML DOM corresponding to this control
            var container = target == null || ! YAHOO.util.Dom.inDocument(target, document)
                ? null
                : (YAHOO.util.Dom.hasClass(target, cssClass) ? target
                    : YAHOO.util.Dom.getAncestorByClassName(target, cssClass));

            // The first time instance() is called for this class, override init() on the class object
            // to make sure that the init method is not called more than once
            if (! instanceAlreadyCalled) {
                instanceAlreadyCalled = true;
                // Inject init
                if (hasInit) {
                    var originalInit = this.prototype.init;
                    this.prototype.init = function() {
                        if (! this.initialized) {
                            originalInit.call(this);
                            this.initialized = true;
                            ORBEON.xforms.XBL.componentInitialized.fire(this);
                        }
                    }
                }
                // Inject destroy
                var originalDestroy = this.prototype.destroy;
                this.prototype.destroy = function() {
                    if (! YAHOO.lang.isUndefined(originalDestroy))
                        originalDestroy.call(this);
                    xblClass._instances[this.container.id] = null;
                }
            }

            if (container == null) {
                // If we get an event for a target which is not in the document, return a mock object
                // that won't do anything when its methods are called
                if (doNothingSingleton == null) {
                    doNothingSingleton = {};
                    for (var methodName in xblClass.prototype)
                        doNothingSingleton[methodName] = function(){};
                }
                return doNothingSingleton;
            } else {
                // Create object holding instances
                if (YAHOO.lang.isUndefined(this._instances))
                    this._instances = {};
                // Get or create instance
                var instance = this._instances[container.id];
                if (YAHOO.lang.isUndefined(instance) || YAHOO.lang.isNull(instance) || instance.container != container) {
                    instance = new xblClass(container);
                    instance.xblClass = xblClass;
                    instance.container = container;
                    if (hasInit) {
                        instance.initialized = false;
                        instance.init();
                    }
                    this._instances[container.id] = instance;
                }
                return instance;
            }
        };
    },

    callValueChanged: function(prefix, component, property) {
        var partial = YAHOO.xbl;                                    if (partial == null) return;
        partial = partial[prefix];                                  if (partial == null) return;
        partial = partial[component];                               if (partial == null) return;
        partial = partial.instance(this);                           if (partial == null) return;
        var method = partial["parameter" + property + "Changed"];   if (method == null) return;
        method.call(partial);
    },

    componentInitialized: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT)
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
    };
}();

ORBEON.xforms.Init = {

    /**
     * Functions used to initialize special controls
     */
    _specialControlsInitFunctions: null,
    _getSpecialControlsInitFunctions: function() {

        // As moves the code for all the controls to classes, they will all use this genericInit().
        function genericInit(container) {
            ORBEON.xforms.Page.getControl(container);
        }

        var tree = new ORBEON.xforms.control.Tree();
        ORBEON.xforms.Init._specialControlsInitFunctions = ORBEON.xforms.Init._specialControlsInitFunctions || {
            "select1": {
                "compact" : ORBEON.xforms.Init._list,
                "{http://orbeon.org/oxf/xml/xforms}menu": ORBEON.xforms.Init._menu,
                "{http://orbeon.org/oxf/xml/xforms}tree": genericInit
            },
            "select": {
                "compact" : ORBEON.xforms.Init._list,
                "{http://orbeon.org/oxf/xml/xforms}tree": genericInit
            },
            "range": { "": ORBEON.xforms.Init._range },
            "textarea": {
                "{http://orbeon.org/oxf/xml/xforms}autosize": ORBEON.xforms.Init._widetextArea,
                "text/html": genericInit
            },
            "dialog": {
                "": ORBEON.xforms.Init._dialog,
                "full": ORBEON.xforms.Init._dialog,
                "minimal": ORBEON.xforms.Init._dialog
            }
        };
        return ORBEON.xforms.Init._specialControlsInitFunctions;
    },

    registerDraggableListenersOnRepeatElements: function(form) {
        return;
        var dndElements = YAHOO.util.Dom.getElementsByClassName("xforms-dnd", null, form);
        for (var dnElementIndex = 0; dnElementIndex < dndElements.length; dnElementIndex++) {
            var dndElement = dndElements[dnElementIndex];
            // Right now, we support D&D of following elements
            var tagName = dndElement.tagName.toLowerCase();
            if (tagName == "div" || tagName == "tr" || tagName == "td" || tagName == "li") {
                ORBEON.xforms.Init.registerDraggableListenersOnRepeatElement(dndElement);
            }
        }
    },

    registerDraggableListenersOnRepeatElement: function(repeatElement) {
        var draggableItem = new ORBEON.xforms.DnD.DraggableItem(repeatElement);
        if (YAHOO.util.Dom.hasClass(repeatElement, "xforms-dnd-vertical"))
            draggableItem.setXConstraint(0, 0);
        else if (YAHOO.util.Dom.hasClass(repeatElement, "xforms-dnd-horizontal"))
            draggableItem.setYConstraint(0, 0);
    },

    document: function() {

        ORBEON.xforms.Globals = {
            // Booleans used for browser detection
            isMac : navigator.userAgent.toLowerCase().indexOf("macintosh") != -1,                 // Running on Mac
            isRenderingEngineGecko: YAHOO.env.ua.gecko,                                           // Firefox or compatible (Gecko rendering engine)
            isFF3OrNewer: YAHOO.env.ua.gecko >= 1.9,                                              // Firefox 3.0 or newer or compatible (Gecko >= 1.9)
            isRenderingEnginePresto: YAHOO.env.ua.opera,                                          // Opera
            isRenderingEngineWebCore: YAHOO.env.ua.webkit,                                        // Safari
            isRenderingEngineWebCore13: YAHOO.env.ua.webkit <=312,                                // Safari 1.3
            isRenderingEngineTrident: YAHOO.env.ua.ie,                                            // Internet Explorer

            /**
             * All the browsers support events in the capture phase, except IE and Safari 1.3. When browser don't support events
             * in the capture phase, we need to register a listener for certain events on the elements itself, instead of
             * just registering the event handler on the window object.
             */
            ns: {},                              // Namespace of ids (for portlets)
            resourcesBaseURL: {},                // Base URL for resources e.g. /context[/version]
            xformsServerURL: {},                 // XForms Server URL
            eventQueue: [],                      // Events to be sent to the server
            eventsFirstEventTime: 0,             // Time when the first event in the queue was added
            discardableTimerIds: {},             // Maps form id to array of discardable events (which are used by the server as a form of polling)
            requestForm: null,                   // HTML for the request currently in progress
            requestIgnoreErrors: false,          // Should we ignore errors that result from running this request
            requestInProgress: false,            // Indicates whether an Ajax request is currently in process
            requestDocument: "",                 // The last Ajax request, so we can resend it if necessary
            requestTryCount: 0,                  // How many attempts to run the current Ajax request we have done so far
            executeEventFunctionQueued: 0,       // Number of ORBEON.xforms.server.AjaxServer.executeNextRequest waiting to be executed
            maskFocusEvents: false,              // Avoid catching focus event when we do call setfocus upon server request
            maskDialogCloseEvents: false,        // Avoid catching a dialog close event received from the server, so we don't sent it back to the server
            currentFocusControlId: null,         // Id of the control that got the focus last
            currentFocusControlElement: null,    // Element for the control that got the focus last
            htmlAreaNames: [],                   // Names of the HTML editors, which we need to reenable them on Firefox
            repeatTreeChildToParent: {},         // Describes the repeat hierarchy
            repeatIndexes: {},                   // The current index for each repeat
            repeatTreeParentToAllChildren: {},   // Map from parent to array with children, used when highlight changes
            yuiCalendar: null,                   // Reusable calendar widget
            tooltipLibraryInitialized: false,
            changedIdsRequest: {},               // Id of controls that have been touched by user since the last response was received
            loadingOtherPage: false,             // Flag set when loading other page that revents the loading indicator to disappear
            activeControl: null,                 // The currently active control, used to disable hint
            autosizeTextareas: [],               // Ids of the autosize textareas on the page
            dialogs: {},                         // Map for dialogs: id -> YUI dialog object
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
            sliderYui: {},                       // Maps slider id to the YUI object for that slider
            isReloading: false,                  // Whether the form is being reloaded from the server
            lastDialogZIndex: 5,                 // zIndex of the last dialog displayed. Gets incremented so the last dialog is always on top of everything else
            // Data relative to a form is stored in an array indexed by form id.
            formErrorPanel: {},                  // YUI panel used to report errors
            formHelpPanel: {},                   // Help dialog: YUI panel
            formHelpPanelMessageDiv: {},         // Help dialog: div containing the help message
            formHelpPanelCloseButton: {},        // Help dialog: close button
            formUUID: {},                        // UUID of the form/containing document
            formStaticState: {},                 // State that does not change for the life of the page
            formDynamicState: {},                // State that changes at every request
            formServerEvents: {},                // Server events information
            formClientState: {},                 // Store for information we want to keep when the page is reloaded
            modalProgressPanel: null,            // Overlay modal panel for displaying progress bar
            changeListeners: {},                 // Maps control id to DOM element for which we have registered a change listener
            topLevelListenerRegistered:          // Have we already registered the listeners on the top-level elements, which never change
                ORBEON.xforms.Globals.topLevelListenerRegistered == null ? false : ORBEON.xforms.Globals.topLevelListenerRegistered
        };

        // Initialize DOM methods based on browser
        (function () {
            var methodsFrom = ORBEON.xforms.Globals.isRenderingEngineTrident ? ORBEON.util.IEDom : ORBEON.util.MozDom;
            for (var method in methodsFrom)
                ORBEON.util.Dom[method] = methodsFrom[method];
        }());

        // Add yui-skin-sam class on body, if not already there.
        // Rationale: When the whole page is generated by Orbeon Forms, the class will be present, but if the class
        // is embedded in an existing page (portlet-like), then the class will most likely not be there.
        YAHOO.util.Dom.addClass(document.body, "yui-skin-sam");

        // Notify the offline module that the page was loaded
        if (ORBEON.util.Properties.offlineSupport.get())
            ORBEON.xforms.Offline.pageLoad();

        // Initialize attributes on form
        for (var formIndex = 0; formIndex < document.forms.length; formIndex++) {
            var formElement = document.forms[formIndex];
            // If this is an XForms form, proceed with initialization
            if (YAHOO.util.Dom.hasClass(formElement, "xforms-form")) {
                var formID = document.forms[formIndex].id;

                ORBEON.xforms.Globals.ns[formID] = formID.substring(0, formID.indexOf("xforms-form"));

                // Initialize XForms server URL
                ORBEON.xforms.Init._setBasePaths(formID, document.getElementsByTagName("script"), ORBEON.util.Properties.resourcesVersioned.get());

                // Remove class xforms-initially-hidden on form element, which might have been added to prevent user
                // interaction with the form before it is initialized
                YAHOO.util.Dom.removeClass(formElement, "xforms-initially-hidden");

                // Create Orbeon Form object, which give it a change to perform its own initialization
                ORBEON.xforms.Page.getForm(formID);

                // Initialize D&D
                ORBEON.xforms.Init.registerDraggableListenersOnRepeatElements(formElement);

                // Initialize loading and error indicator
                ORBEON.xforms.Globals.formErrorPanel[formID] = null;

                for (var formChildIndex = 0; formChildIndex < formElement.childNodes.length; formChildIndex++) {
                    var formChild = formElement.childNodes[formChildIndex];
                    if (ORBEON.util.Dom.isElement(formChild) && YAHOO.util.Dom.hasClass(formChild, "xforms-error-panel")) {

                        // Create and store error panel
                        YAHOO.util.Dom.generateId(formChild);
                        YAHOO.util.Dom.removeClass(formChild, "xforms-initially-hidden");
                        var errorPanel = new YAHOO.widget.Panel(formChild.id, {
                            modal: true,
                            fixedcenter: false,
                            underlay: "shadow",
                            visible: false,
                            constraintoviewport: true,
                            draggable: true
                        });
                        errorPanel.render();
                        ORBEON.util.Utils.overlayUseDisplayHidden(errorPanel);
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
                        break;
                    }
                }

                var elements = formElement.elements;
                var xformsRepeatTree;
                var xformsRepeatIndices;
                for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
                    var element = elements[elementIndex];
                    if (element.name.indexOf("$uuid") != -1) {
                        ORBEON.xforms.Globals.formUUID[formID] = element;
                    } else if (element.name.indexOf("$static-state") != -1) {
                        ORBEON.xforms.Globals.formStaticState[formID] = element;
                    } else if (element.name.indexOf("$dynamic-state") != -1) {
                        ORBEON.xforms.Globals.formDynamicState[formID] = element;
                    } else if (element.name.indexOf("$server-events") != -1) {
                        ORBEON.xforms.Globals.formServerEvents[formID] = element;
                    } else if (element.name.indexOf("$client-state") != -1) {
                        ORBEON.xforms.Globals.formClientState[formID] = element;
                        if (element.value == "") {
                            // If the client state is empty, store the initial dynamic state (old system) or UUID (new system).
                            // If it is not empty, this means that we already have an initial state stored there, and that this
                            // function runs because the user reloaded or navigated back to this page.
                            ORBEON.xforms.Document.storeInClientState(formID, "initial-dynamic-state",
                                    ORBEON.xforms.Globals.formDynamicState[formID].value);
                            ORBEON.xforms.Document.storeInClientState(formID, "uuid",
                                    ORBEON.xforms.Globals.formUUID[formID].value);
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
                if (ORBEON.xforms.Document.getFromClientState(formID, "load-did-run") == null) {
                    ORBEON.xforms.Document.storeInClientState(formID, "load-did-run", "true");
                    ORBEON.xforms.Document.storeInClientState(formID, "sequence", "1");
                } else {
                    if (ORBEON.util.Properties.revisitHandling.get() == "reload") {
                        ORBEON.xforms.Globals.isReloading = true;
                        window.location.reload(true);
                        //NOTE: You would think that if reload is canceled, you would reset this to false, but somehow this fails with IE
                    } else {
                        var event = new ORBEON.xforms.server.AjaxServer.Event(formElement, null, null, null, "xxforms-all-events-required");
                        ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
                    }
                }

                // Initialize controls, listeners, server-events
                if (!(window.orbeonInitData === undefined)) {

                    var formInitData = window.orbeonInitData[formID];

                    var initFunctions = ORBEON.xforms.Init._getSpecialControlsInitFunctions();
                    // Iterate over controls
                    for (var controlType in formInitData["controls"]) {
                        if (initFunctions[controlType]) {
                            var controlAppearances = formInitData["controls"][controlType];
                            // Iterate over appearance for current control
                            for (var controlAppearance in controlAppearances) {
                                var initFunction = initFunctions[controlType][controlAppearance];
                                if (initFunction) {
                                    var controlIds = controlAppearances[controlAppearance];
                                    // Iterate over controls
                                    for (var controlIndex = 0; controlIndex < controlIds.length; controlIndex++) {
                                        var control = ORBEON.util.Dom.get(controlIds[controlIndex]);
                                        initFunction(control);
                                    }
                                }
                            }
                        }
                    }

                    // Register key listeners
                    var keyListeners = formInitData["keylisteners"];
                    if (YAHOO.lang.isArray(keyListeners)) {
                        for (var keyListenerIndex = 0; keyListenerIndex < keyListeners.length; keyListenerIndex++) {
                            var keyListener = keyListeners[keyListenerIndex];

                            // When listening on events from the document, the server gives us the id of the form
                            keyListener.isDocumentListener = keyListener.observer == "#document";
                            keyListener.isDialogListener = false;
                            if (! keyListener.isDocumentListener) {
                                keyListener.observerElement = ORBEON.util.Dom.get(keyListener.observer);
                                keyListener.isDialogListener = YAHOO.util.Dom.hasClass(keyListener.observerElement, "xforms-dialog");
                            }
                            if (keyListener.isDocumentListener || keyListener.isDialogListener) keyListener.observerElement = document;

                            // Save current form, which we'll need when creating an event
                            keyListener.form = formElement;

                            // Handle optional modifiers
                            var keyData = {};
                            if (YAHOO.lang.isString(keyListener.modifier)) {
                                var modifiers = keyListener.modifier.split(" ");
                                for (var modifierIndex = 0; modifierIndex < modifiers.length; modifierIndex++) {
                                    var modifier = modifiers[modifierIndex];
                                    if (modifier.toLowerCase() == "control") keyData["ctrl"] = true;
                                    if (modifier.toLowerCase() == "shift") keyData["shift"] = true;
                                    if (modifier.toLowerCase() == "alt") keyData["alt"] = true;
                                }
                            }
                            // Handle text string by building array of key codes
                            keyData["keys"] = [];
                            var text = keyListener.text.toUpperCase();
                            for (var textIndex = 0; textIndex < text.length; textIndex++)
                                keyData["keys"].push(text.charCodeAt(textIndex));

                            // Create YUI listener
                            var yuiKeyListener = new YAHOO.util.KeyListener(keyListener.observerElement, keyData, {
                                scope: keyListener,
                                correctScope: false,
                                fn: function(eventName, eventObject, keyListener) {
                                    // YUI doesn't give us the target of the event, so we provide the observer as the target to the server
                                    var targetId = keyListener.observer;
                                    var additionalAttributes = ["text", keyListener.text];
                                    if (! YAHOO.lang.isUndefined(keyListener.modifier)) {
                                        additionalAttributes.push("modifiers");
                                        additionalAttributes.push(keyListener.modifier);
                                    }
                                    var event = new ORBEON.xforms.server.AjaxServer.Event(keyListener.form, targetId, null, null, "keypress",
                                        null, null, null, null, null, additionalAttributes);
                                    ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
                                }
                            });

                            // Register listener on dialog or enable
                            if (keyListener.isDialogListener) {
                                var yuiDialog = ORBEON.xforms.Globals.dialogs[keyListener.observer];
                                var dialogKeyListeners = yuiDialog.cfg.getProperty("keylisteners");
                                if (YAHOO.lang.isUndefined(dialogKeyListeners)) dialogKeyListeners = [];
                                dialogKeyListeners.push(yuiKeyListener);
                                yuiDialog.cfg.setProperty("keylisteners", dialogKeyListeners);
                            } else {
                				yuiKeyListener.enable();
                            }
                        }
                    }

                    // Handle server events
                    var serverEvents = formInitData["server-events"];
                    if (YAHOO.lang.isArray(serverEvents)) {
                        // For now just take the id of the first XForms form; this will need to be changed to support multiple forms
                        for (var serverEventIndex = 0; serverEventIndex < serverEvents.length; serverEventIndex++) {
                            var serverEvent = serverEvents[serverEventIndex];
                            var discardable = ! YAHOO.lang.isUndefined(serverEvent["discardable"]) && serverEvent["discardable"];
                            ORBEON.xforms.server.AjaxServer.createDelayedServerEvent(serverEvent["event"], serverEvent["delay"],
                                serverEvent["show-progress"], serverEvent["progress-message"], discardable, formElement.id);
                        }
                    }
                }
            }
        }

        // Special registration for focus, blur, and change events
        if (!ORBEON.xforms.Globals.topLevelListenerRegistered) {
            if (YAHOO.lang.isUndefined(document.addEventListener)) {
                // For browsers that don't support the capture mode (IE) register listener for the non-standard
                // focusin and focusout events (which do bubble), and we'll register the listener for change on the
                // element on focus
                YAHOO.util.Event.addListener(document, "focusin", ORBEON.xforms.Events.focus);
                YAHOO.util.Event.addListener(document, "focusout", ORBEON.xforms.Events.blur);
            } else {
                // Register event handlers using capture phase for W3C-compliant browsers
                document.addEventListener("focus", ORBEON.xforms.Events.focus, true);
                document.addEventListener("blur", ORBEON.xforms.Events.blur, true);
                document.addEventListener("change", ORBEON.xforms.Events.change, true);
            }
        }

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

        // Run code sent by server
        if (typeof xformsPageLoadedServer != "undefined") {
            xformsPageLoadedServer();
        }

        // Run call-back function interested in knowing when the form is initialized
        if (window.parent.childWindowOrbeonReady) {
            window.parent.childWindowOrbeonReady();
            window.parent.childWindowOrbeonReady = null;
        }

        ORBEON.xforms.Globals.topLevelListenerRegistered = true;

        // A heartbeat event - An AJAX request for letting server know that "I'm still alive"
        if (ORBEON.util.Properties.sessionHeartbeat.get()) {
            var heartBeatDelay = ORBEON.util.Properties.sessionHeartbeatDelay.get();
            if (heartBeatDelay > 0) {
                window.setInterval(function() {
                    ORBEON.xforms.Events.sendHeartBeatIfNeeded(heartBeatDelay);
                }, heartBeatDelay / 10); // say session is 30 mn, heartbeat must come after 24 mn, we check every 2.4 mn so we should
            }
        }

        // We don't call ORBEON.xforms.Events.orbeonLoadedEvent.fire() directly, as without this, in some cases in IE,
        // YUI event.js's call to this.subscribers.length in fire method hangs.
        window.setTimeout(function() {
            ORBEON.xforms.Events.orbeonLoadedEvent.fire();
        }, ORBEON.util.Properties.internalShortDelay.get());
    },

    profileDocument: function() {
        console.profile("XForms initialization");
        ORBEON.xforms.Init.document();
        console.profileEnd();
    },

    /**
     * Initialize a newly copied subtree.
     *
     * Some of the more advanced controls are initialized when the page first loads. The server sets the value of the
     * orbeonInitData variable to tell the client the id of those controls and the type of each control. When new
     * controls are added, this function must be called so those the inserted advanced controls are initialized as
     * well.
     */
    insertedElement: function(element) {
        // TODO: Also need destructors for controls
        if (element.nodeType == ORBEON.util.Dom.ELEMENT_TYPE) {
            if (YAHOO.util.Dom.hasClass(element, "xforms-textarea")
                           && YAHOO.util.Dom.hasClass(element, "xforms-mediatype-text-html")) {
                // HTML area
                ORBEON.xforms.Init._htmlArea(element);
            } else if (YAHOO.util.Dom.hasClass(element, "xforms-dialog")) {
                // Dialog
                ORBEON.xforms.Init._dialog(element);
            }
            // Recurse
            for (var childIndex = 0; childIndex < element.childNodes.length; childIndex++) {
                var child = element.childNodes[childIndex];
                if (child.nodeType == ORBEON.util.Dom.ELEMENT_TYPE)
                    ORBEON.xforms.Init.insertedElement(child);
            }
        }
    },

    _setBasePaths: function(formID, scripts, versioned) {
        // NOTE: The server provides us with a base URL, but we must use a client-side value to support proxying

        var resourcesBaseURL = null;
        var xformsServerURL = null;

        // Try scripts
        for (var scriptIndex = 0; scriptIndex < scripts.length; scriptIndex++) {
            var script = scripts[scriptIndex];
            var scriptSrc = ORBEON.util.Dom.getAttribute(script, "src");
            if (scriptSrc != null) {
                var startPathToJavaScript = scriptSrc.indexOf(PATH_TO_JAVASCRIPT_1);
                if (startPathToJavaScript != -1) {
                    // Found path to non-xforms-server resource
                    // scriptSrc: (/context)(/version)/ops/javascript/xforms-min.js

                    // Take the part of the path before the JS path
                    // prefix: (/context)(/version)
                    // NOTE: may be "" if no context is present
                    var prefix = scriptSrc.substr(0, startPathToJavaScript);
                    resourcesBaseURL = prefix;
                    if (versioned) {
                        // Remove version
                        xformsServerURL = prefix.substring(0, prefix.lastIndexOf("/")) + XFORMS_SERVER_PATH;
                    } else {
                        xformsServerURL = prefix + XFORMS_SERVER_PATH;
                    }

                    break;
                } else {
                    startPathToJavaScript = scriptSrc.indexOf(PATH_TO_JAVASCRIPT_2);
                    if (startPathToJavaScript != -1) {
                        // Found path to xforms-server resource
                        // scriptSrc: (/context)/xforms-server(/version)/xforms-...-min.js

                        // Take the part of the path before the JS path
                        // prefix: (/context)
                        // NOTE: may be "" if no context is present
                        var prefix = scriptSrc.substr(0, startPathToJavaScript);
                        var jsPath = scriptSrc.substr(startPathToJavaScript);
                        if (versioned) {

                            var bits = /^(\/[^\/]+)(\/[^\/]+)(\/.*)$/.exec(jsPath);
                            var version = bits[2];

                            resourcesBaseURL = prefix + version;
                            xformsServerURL = prefix + XFORMS_SERVER_PATH;
                        } else {
                            resourcesBaseURL = prefix;
                            xformsServerURL = prefix + XFORMS_SERVER_PATH;
                        }

                        break;
                    }
                }
            }
        }

        if (resourcesBaseURL == null) {
            if (!(window.orbeonInitData === undefined)) {
                // Try values passed by server (in portlet mode)
                // NOTE: We try this second as of 2010-08-27 server always puts these in
                var formInitData = window.orbeonInitData[formID];
                if (formInitData && formInitData["paths"]) {
                    resourcesBaseURL = formInitData["paths"]["resources-base"];
                    xformsServerURL = formInitData["paths"]["xforms-server"];
                }
            }
        }

        ORBEON.xforms.Globals.resourcesBaseURL[formID] = resourcesBaseURL;
        ORBEON.xforms.Globals.xformsServerURL[formID] = xformsServerURL;
    },

    _widetextArea: function(textarea) {
        ORBEON.xforms.Globals.autosizeTextareas.push(textarea);
        ORBEON.xforms.Controls.autosizeTextarea(textarea);
    },

    _range: function(range) {
        range.tabIndex = 0;
        ORBEON.xforms.ServerValueStore.set(range.id, 0);

        // In both cases the background <div> element must already have an id
        var backgroundDiv;
        if (ORBEON.util.Utils.isNewXHTMLLayout()) {
            backgroundDiv = YAHOO.util.Dom.getElementsByClassName("xforms-range-background", "div", range)[0];
        } else {
            backgroundDiv = range;
        }

        var thumbDiv = YAHOO.util.Dom.getElementsByClassName("xforms-range-thumb", "div", range)[0];
        thumbDiv.id = ORBEON.util.Utils.appendToEffectiveId(range.id, "$$thumb");

        var slider = YAHOO.widget.Slider.getHorizSlider(backgroundDiv.id, thumbDiv.id, 0, 200);
        slider.subscribe("change", ORBEON.xforms.Events.sliderValueChange);
        ORBEON.xforms.Globals.sliderYui[range.id] = slider;
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
                ORBEON.xforms.Init._addToMenuItem(menu, childArray, subMenuItem);
            }
            menuItem.cfg.setProperty("submenu", subMenu);
        }
    },

    _menu: function (menu) {
        // Find the divs for the YUI menu and for the values inside the control
        var yuiMenuDiv = YAHOO.util.Dom.getElementsByClassName("yuimenubar", null, menu)[0];
        var valuesDiv = YAHOO.util.Dom.getElementsByClassName("xforms-initially-hidden", null, menu)[0];

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
        ORBEON.xforms.Page.getControl(htmlArea);
    },

    /**
     * For all the controls except list, we figure out the initial value of the control when
     * receiving the first focus event. For the lists on Firefox, the value has already changed
     * when we receive the focus event. So here we save the value for lists when the page loads.
     */
    _list: function(list) {
        var value = "";
        if (ORBEON.util.Utils.isNewXHTMLLayout())
            list = ORBEON.util.Dom.getElementByTagName(list, "select");
        for (var i = 0; i < list.options.length; i++) {
            var option = list.options[i];
            if (option.selected) {
                if (value != "") value += " ";
                value += option.value;
            }
        }
        ORBEON.xforms.ServerValueStore.set(list.id, value);
    },

    /**
     * Initialize dialogs
     */
    _dialog: function(dialog) {
        var isModal = YAHOO.util.Dom.hasClass(dialog, "xforms-dialog-modal");
        var hasClose = YAHOO.util.Dom.hasClass(dialog, "xforms-dialog-close-true");
        var isDraggable = YAHOO.util.Dom.hasClass(dialog, "xforms-dialog-draggable-true");
        var isVisible = YAHOO.util.Dom.hasClass(dialog, "xforms-dialog-visible-true");
        var isMinimal = YAHOO.util.Dom.hasClass(dialog, "xforms-dialog-appearance-minimal");

        // If we already have a dialog for the same id, first destroy it, as this is an object left behind
        // by a deleted repeat iteration
        if (ORBEON.xforms.Globals.dialogs[dialog.id])
            ORBEON.xforms.Globals.dialogs[dialog.id].destroy();

        // Create dialog object
        var yuiDialog;
        if (isMinimal) {
            // Create minimal dialog
            yuiDialog = new YAHOO.widget.Dialog(dialog.id, {
                modal: isModal,
                close: false,
                visible: false,
                draggable: false,
                fixedcenter: false,
                constraintoviewport: true,
                underlay: "none",
                usearia: true,
                role: "" // See bug 315634 http://goo.gl/54vzd
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
                underlay: "none", // Similarly, setting the underlay to "shadow" conflicts with the CSS used to limit the width and height of the dialog on IE6
                usearia: true,
                role: "" // See bug 315634 http://goo.gl/54vzd
            });
			yuiDialog.showEvent.subscribe(ORBEON.xforms.Events.dialogShow, dialog.id);
            // Register listener for when the dialog is closed by a click on the "x"
            yuiDialog.beforeHideEvent.subscribe(ORBEON.xforms.Events.dialogClose, dialog.id);
        }

        // This is for JAWS to read the content of the dialog (otherwise it just reads the button)
        var dialogDiv = YAHOO.util.Dom.getElementsByClassName("xforms-dialog", "div", yuiDialog.element)[0];
        dialogDiv.setAttribute("aria-live", "polite");

        // Move the dialog under the form element, as if the dialog is inside another absolute block it can be cropped
        // (can't escape that block), and in some cases the mask can show on top of the dialog (even if the z-index
        // for the dialog is higher than the z-index for the mask). See:
        // http://forge.ow2.org/tracker/index.php?func=detail&aid=314943&group_id=168&atid=350207
        var form = ORBEON.xforms.Controls.getForm(yuiDialog.element);
        if (yuiDialog.element.parentNode != form)
            form.appendChild(yuiDialog.element);

        ORBEON.xforms.Globals.dialogs[dialog.id] = yuiDialog;
        if (isVisible) ORBEON.xforms.Controls.showDialog(dialog.id, null);
    }
};

/**
 * Store for values are we think they are known to the server.
 *
 * @namespace ORBEON.xforms
 * @class ServerValueStore
 */
ORBEON.xforms.ServerValueStore = {

    /** @private @type {Object.<string, HTMLElement>} */    _idToControl: {},
    /** @private @type {Object.<string, string>} */         _idToValue: {},

    /**
     * Stores a value for a control which we know the server knows about.
     *
     * @param {!string} id       Id of the control
     * @param {!string} value    Value for the control
     * @returns {void}
     */
    set: function(id, value) {
        this._idToControl[id] = ORBEON.util.Dom.get(id);
        this._idToValue[id] = value;
    },

    /**
     * Returns the value of a control as known by the server, or null if we don't know what the value known by the
     * server is.
     *
     * @param {!string} id      Id of the control
     * @return {string}         Value of null
     */
    get: function(id) {
        var cachedControl = this._idToControl[id];
        if (cachedControl == null) {
            // We known nothing about this control
            return null;
        } else if (cachedControl == ORBEON.util.Dom.get(id)) {
            // We have the value and it is for the right control
            return ORBEON.xforms.ServerValueStore._idToValue[id];
        } else {
            // We have a value but it is for an obsolete control
            this._idToControl[id] = null;
            ORBEON.xforms.ServerValueStore._idToValue[id] = null;
            return null;
        }
    },

    /**
     * Removes the value we know for a specific control.
     *
     * @param {!string} id
     * @return {void}
     */
    remove: function(id) {
        this._idToControl[id] = null;
        this._idToValue[id] = null;
    },

    /**
     * Got through all the server, check that each one of for a control which is still in the DOM, and if not purge it.
     *
     * @return {void}
     */
    purgeExpired: function() {
        _.each(_.keys(this._idToControl), function(id) {
            if (! YAHOO.util.Dom.inDocument(this._idToControl[id]))
                this.remove(id);
        })
    }
};

YAHOO.util.DDM.mode = YAHOO.util.DDM.INTERSECT;
// Commented so text doesn't get selected as we D&D on the page. But this causes an issue as we can't select
// text anymore in a form control which is a D&D area.
//YAHOO.util.DragDropMgr.preventDefault = false;

ORBEON.xforms.DnD = {

    DraggableItem: function(element, sGroup, config) {
        ORBEON.xforms.DnD.DraggableItem.superclass.constructor.call(this, element, element.tagName, config);
        YAHOO.util.Dom.setStyle(element, "cursor", "move");

        // Move the drag element under the form for our CSS rules to work
        var dragElement = this.getDragEl();
        if (! YAHOO.util.Dom.hasClass(dragElement.parentNode, "xforms-form")) {
            var form = ORBEON.xforms.Controls.getForm(element);
            form.appendChild(dragElement);
        }
    }
};

YAHOO.extend(ORBEON.xforms.DnD.DraggableItem, YAHOO.util.DDProxy, {

    _startPosition: -1,

    _getPosition: function(element) {
        var previousSibling = element;
        var position = 0;
        while (true) {
            if (YAHOO.util.Dom.hasClass(previousSibling, "xforms-repeat-begin-end")) break;
            if (YAHOO.util.Dom.hasClass(previousSibling, "xforms-repeat-delimiter")) position++;
            previousSibling = YAHOO.util.Dom.getPreviousSibling(previousSibling);
        }
        return position;
    },

    _renumberIDsWorker: function(element, repeatDepth, newIndex) {
        // Rename ID on this element
        var repeatSeparatorPosition = element.id.indexOf(XFORMS_SEPARATOR_1);
        if (repeatSeparatorPosition != -1) {
            var repeatIndexes =  element.id.substring(repeatSeparatorPosition + 1).split(XFORMS_SEPARATOR_2);
            repeatIndexes[repeatDepth] = newIndex;
            var newID = element.id.substring(0, repeatSeparatorPosition) + XFORMS_SEPARATOR_1 + repeatIndexes.join(XFORMS_SEPARATOR_2);
            element.id = newID;

        }
        // Do the same with all the children
        YAHOO.util.Dom.batch(YAHOO.util.Dom.getChildren(element), function(childElement) {
            this._renumberIDsWorker(childElement, repeatDepth, newIndex);
        }, this, true);
    },

    /**
     * Renumber the IDs for a given repeat ID, for all the elements between the begin and end marker for that repeat
     * @param repeatID      E.g. repeat-begin-todo·1 for the repeat on to-dos in the first to-do list.
     */
    _renumberIDs: function(repeatID) {

        // Figure at what depth this repeat is
        var repeatDepth = 0;
        var currentRepeat = repeatID;
        var repeatSeparatorPosition = currentRepeat.indexOf(XFORMS_SEPARATOR_1);
        if (repeatSeparatorPosition != -1)
            currentRepeat = currentRepeat.substring(0, repeatSeparatorPosition);
        while (true) {
            var parentRepeat = ORBEON.xforms.Globals.repeatTreeChildToParent[currentRepeat];
            if (! parentRepeat) break;
            repeatDepth++;
            currentRepeat = parentRepeat;
        }

        // Go through the top elements and change the IDs of all the children
        var currentElement = ORBEON.util.Dom.get("repeat-begin-" + repeatID);
        var newIndex = 0;
        while (true) {
            currentElement = YAHOO.util.Dom.getNextSibling(currentElement);
            if (currentElement == null || YAHOO.util.Dom.hasClass(currentElement, "xforms-repeat-begin-end")) break;
            if (! YAHOO.util.Dom.hasClass(currentElement, "xforms-repeat-delimiter")
                    && ! YAHOO.util.Dom.hasClass(currentElement, "xforms-repeat-template")) {
                newIndex++;
                this._renumberIDsWorker(currentElement, repeatDepth, newIndex);
            }
        }
    },

    startDrag: function(x, y) {

        var dragElement = this.getDragEl();
        var srcElement = this.getEl();

        this._startPosition = this._getPosition(srcElement);
        var repeatId = YAHOO.util.Dom.getElementsByClassName("xforms-repeat-begin-end", null, srcElement.parentNode)[0].id;
        this.sourceControlID = repeatId.substring(13);

        YAHOO.util.Dom.setStyle(dragElement, "opacity", 0.67);
        // We set the width of the div to be the same as the one of the source element, otherwise if copy the content of
        // the source element into a div, the resulting div might be much smaller or larger than the source element.
        YAHOO.util.Dom.setStyle(dragElement, "width", parseInt(dragElement.style.width) + 2 +"px");
        YAHOO.util.Dom.setStyle(dragElement, "height", "auto");
        // If the source element is a table row, we can't copy table cells into a div. So we insert a table around the cell.
        var tagName = srcElement.tagName.toLowerCase();
        dragElement.innerHTML = tagName == "tr" ? "<table'><tr>" + srcElement.innerHTML + "</tr></table>"
                : srcElement.innerHTML;
        dragElement.className = srcElement.className;
        YAHOO.util.Dom.setStyle(srcElement, "visibility", "hidden");
    },

    onDrag: function(e) {
        // Keep track of the direction of the drag for use during onDragOver
        var y = YAHOO.util.Event.getPageY(e);

        if (y < this.lastY) {
            this.goingUp = true;
        }
        else if (y > this.lastY) {
            this.goingUp = false;
        }
        this.lastY = y;
    },

    //Callback method implementation added for showing preview
    onDragOver: function(e, id) {

        var srcElement = this.getEl();
        var srcDelimiter = YAHOO.util.Dom.getPreviousSibling(srcElement);
        var destElement = ORBEON.util.Dom.get(id)[0];

        if (YAHOO.util.Dom.hasClass(srcElement, "xforms-dnd") && srcElement.nodeName.toLowerCase() == destElement.getEl().nodeName.toLowerCase()) {
            var parent = destElement.getEl().parentNode;
            this.overElement = destElement.getEl();
            parent.removeChild(srcDelimiter);
            parent.removeChild(srcElement);
            if (this.goingUp) {
                var insertionReferenceElement = this.overElement;
                parent.insertBefore(srcElement, insertionReferenceElement);
                parent.insertBefore(srcDelimiter, insertionReferenceElement);
            } else {
                var insertionReferenceElement = YAHOO.util.Dom.getNextSibling(this.overElement);
                parent.insertBefore(srcDelimiter, insertionReferenceElement);
                parent.insertBefore(srcElement, insertionReferenceElement);
            }
            YAHOO.util.DragDropMgr.refreshCache();
        }
    },

    endDrag: function(e) {

        var srcElement = this.getEl();
        var proxy = this.getDragEl();
        var proxyid = proxy.id;
        var thisid = this.id;

        YAHOO.util.Dom.setStyle(proxyid, "visibility", "hidden");
        YAHOO.util.Dom.setStyle(thisid, "visibility", "");

        // Find end position in repeat
        var endPosition = this._getPosition(srcElement);
        if (endPosition != this._startPosition) {
            var form = ORBEON.xforms.Controls.getForm(srcElement);
            var event = new ORBEON.xforms.server.AjaxServer.Event(form, this.sourceControlID, null, null, "xxforms-dnd", null, null, null, null, null,
                    ["dnd-start", this._startPosition, "dnd-end", endPosition]);
            this._renumberIDs(this.sourceControlID);
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    }

});

ORBEON.xforms.Offline = {

    isOnline: true,                                 // True if we are online, false otherwise
    lastRequestIsTakeOnline: false,                 // Set when we take the form online
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
                ORBEON.xforms.Globals.requestForm = ORBEON.util.Dom.get(formID);
                ORBEON.xforms.server.AjaxServer.handleResponseDom(initialEventsXML, formID);
                // Set control values
                controlValues = ORBEON.xforms.Offline._deserializerControlValues(controlValues);
                for (var controlID in controlValues) {
                    var controlValue = controlValues[controlID];
                    var control = ORBEON.util.Dom.get(controlID);
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
            controlValues[controlID] = ORBEON.xforms.Controls.getCurrentValue(ORBEON.util.Dom.get(controlID));
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
    takeOnline: function(beforeOnlineListener) {
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
        ORBEON.xforms.Offline.lastRequestIsTakeOnline = true;
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
                events.push(new ORBEON.xforms.server.AjaxServer.Event(
                    ORBEON.util.Dom.get(eventArray[0]),
                    eventArray[1],
                    eventArray[2],
                    eventArray[3],
                    eventArray[4],
                    eventArray[5],
                    eventArray[6] == "1",
                    eventArray[7] == "1"));
            }
            // Send all the events back to the server
            ORBEON.xforms.server.AjaxServer.fireEvents(events, false);
        }

        // Tell the server we are going online
        ORBEON.xforms.Document.dispatchEvent("#document", "xxforms-online");

        // Give a chance to some code to run before the online event is sent to the server
        if (!YAHOO.lang.isUndefined(beforeOnlineListener))
            beforeOnlineListener(window);
    },

    /**
     * Stores the events (which have just been fired) in the store (instead of the sending the events to the server).
     */
    storeEvents: function(events) {
        function emptyStringIfNull(value) { return value == null ? "" : value; }

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
    },

    loadFormInIframe: function(url, loadListener) {

        // Remove existing iframe, if it exists
        var offlineIframeId = "orbeon-offline-iframe";
        var offlineIframe = ORBEON.util.Dom.get(offlineIframeId);
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

        //  Applies a relevant or readonly to inherited controls
        function applyToInherited(control, mips, getter, setter, inherited, value, isRelevance) {
            if (getter(control) != value) {
                setter(control, value);
                if (inherited) {
                    for (var inheritedControlIndex = 0; inheritedControlIndex < mips.relevant.inherited.length; inheritedControlIndex++) {
                        var controlID = inherited[inheritedControlIndex];
                        var inheritedControl = ORBEON.util.Dom.get(controlID);
                        if (inheritedControl == null) {
                            // If we can't find the control, maybe this is a the ID of a group
                            inheritedControl = ORBEON.util.Dom.get("group-begin-" + controlID);
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

        // Create context once; we then update it for values that are calculated
        var xpathNode = document.createElement("dummy"); // Node used as current node to evaluate XPath expression
        var xpathContext = new ExprContext(xpathNode);
        for (var variableName in ORBEON.xforms.Offline.variables) {
            var controlID = ORBEON.xforms.Offline.variables[variableName].value;
            var variableValue = ORBEON.xforms.Controls.getCurrentValue(ORBEON.util.Dom.get(controlID));
            xpathContext.setVariable(variableName, variableValue);
        }

        // Go over all controls
        for (var controlID in ORBEON.xforms.Offline.mips) {
            var mips = ORBEON.xforms.Offline.mips[controlID];
            var control = ORBEON.util.Dom.get(controlID);
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
            if (mips.required) {
                var required = evaluateXPath(mips.required.value, xpathContext);
                if (required != null)
                    isValid &= !(controlValue == "" && required);
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

            ORBEON.xforms.Controls.setValid(control, isValid ? "true" : "false");
        }
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

function xformsLog(object) {
    var debugDiv = ORBEON.util.Dom.get("xforms-debug");
    if (debugDiv == null) {
        // Figure out width and height of visible part of the page
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
        debugDiv.style.width = ORBEON.util.Properties.debugWindowWidth.get() + "px";
        debugDiv.style.left = visibleWidth - (ORBEON.util.Properties.debugWindowWidth.get() + 50) + "px";
        debugDiv.style.height = ORBEON.util.Properties.debugWindowHeight.get() + "px";
        debugDiv.style.top = visibleHeight - (ORBEON.util.Properties.debugWindowHeight.get() + 20) + "px";

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

/**
 * Value change handling for all the controls. It is assumed that the "value" property of "target"
 * contain the current value for the control.
 *
 * This function is in general called by xformsHandleValueChange(), and will be called directly by
 * other event handler for less usual events (e.g. slider, HTML area).
 */
function xformsValueChanged(target, other) {
    var newValue = ORBEON.xforms.Controls.getCurrentValue(target);
    var valueChanged = newValue != target.previousValue;
    // We don't send value change events for the XForms upload control
    var isUploadControl = YAHOO.util.Dom.hasClass(target, "xforms-upload");
    if (valueChanged && !isUploadControl) {
        target.previousValue = newValue;
        var incremental = other == null && YAHOO.util.Dom.hasClass(target, "xforms-incremental");
        var otherID = YAHOO.lang.isObject(other) ? other.id : null;
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, otherID, newValue, "xxforms-value-change-with-focus-change");
        ORBEON.xforms.server.AjaxServer.fireEvents([event], incremental);
    }
    return valueChanged;
}

// Handle click on trigger
function xformsHandleClick(event) {
    var target = getEventTarget(event);
    // Make sure the user really clicked on the trigger, instead of pressing enter in a nearby control
    if ((YAHOO.util.Dom.hasClass(target, "xforms-trigger") || YAHOO.util.Dom.hasClass(target, "xforms-trigger"))
            && !YAHOO.util.Dom.hasClass(target, "xforms-readonly")) {
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, null, "DOMActivate");
        ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
    }
    return false;
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

// Run xformsPageLoaded when the browser has finished loading the page
// In case this script is loaded twice, we still want to run the initialization only once
if (!ORBEON.xforms.Globals.pageLoadedRegistered) {

    // See if we the form is inside a <div class="dijitContentPane">. If it is, this means that the form has been
    // included into the <div> by Dojo. When that happens, using YAHOO.util.Event.onDOMReady() works with IE but not
    // Firefox. So the user is responsible from calling ORBEON.xforms.Init.document() (see the Wiki:
    // http://tinyurl.com/cufx4f). But because YAHOO.util.Event.onDOMReady() does work on IE, that causes
    // ORBEON.xforms.Init.document() to be called twice which causes problems. So here we look for a
    // <div class="dijitContentPane"> and if we can find one, we don't call YAHOO.util.Event.onDOMReady().
    var foundDojoContentPane = false;
    for (var i = 0; i < document.forms.length; i++) {
        var form = document.forms[i];
        if (form.className.indexOf("xforms-form") != -1) {
            // Found Orbeon Forms <form> element
            var currentElement = form.parentNode;
            while (currentElement != null) {
                if (currentElement.className == "dijitContentPane") {
                    foundDojoContentPane = true;
                    break;
                }
                currentElement = currentElement.parentNode;
            }
        }
    }
    if (!foundDojoContentPane) {
        ORBEON.xforms.Globals.pageLoadedRegistered = true;
        YAHOO.util.Event.throwErrors = true;
        YAHOO.util.Event.onDOMReady(ORBEON.xforms.Init.document);
        ORBEON.xforms.Globals.debugLastTime = new Date().getTime();
        ORBEON.xforms.Globals.lastEventSentTime = new Date().getTime();
    }
}
ORBEON.onJavaScriptLoaded.fire();
//ORBEON.util.Test.startFirebugLite();
