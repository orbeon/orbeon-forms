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
var XF_REPEAT_SEPARATOR = "\u2299";
var XF_REPEAT_INDEX_SEPARATOR = "-";
var XF_COMPONENT_SEPARATOR = "\u2261";
var XF_LHHAI_SEPARATOR = XF_COMPONENT_SEPARATOR + XF_COMPONENT_SEPARATOR;

var XFORMS_SERVER_PATH = "/xforms-server";
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var ATTRIBUTE_TYPE = document.createAttribute("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;
var XFORMS_REGEXP_CR = new RegExp("\\r", "g");
var XFORMS_REGEXP_OPEN_ANGLE = new RegExp("<", "g");
var XFORMS_REGEXP_CLOSE_ANGLE = new RegExp(">", "g");
var XFORMS_REGEXP_AMPERSAND = new RegExp("&", "g");
var XFORMS_REGEXP_INVALID_XML_CHAR = new RegExp("[\x00-\x08\x0B\x0C\x0E-\x1F]", "g");
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

                } else if (name == "style") {

                    // For IE6/7 (but not IE8), using setAttribute with style doesn't work
                    // So here we set the style as done by jQuery
                    // https://github.com/jquery/jquery/blob/master/src/attributes.js#L600
                    element.style.cssText = "" + value;

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
             * We don't use jQuery to set the focus, as this would trigger our listener on the focus event to be [called
             * twice][1]. Since we use a mask to avoid telling the server about a focus the server just told us about,
             * the focus listener running twice would [send the focus event to the server on the second run][2], which
             * we don't want. We'll be able to simply use jQuery when we [implement code keeping track of the control
             * that has the focus from the server's perspective][3].
             *
             *   [1]: http://jquery.com/upgrade-guide/1.9/#order-of-triggered-focus-events
             *   [2]: https://github.com/orbeon/orbeon-forms/issues/747
             *   [3]: https://github.com/orbeon/orbeon-forms/issues/755
             */
            focus: function(element) {
                try { element.focus(); }
                catch (e) { /* NOP */ }
            },

            blur: function(element) {
                try { element.blur(); }
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
                if (element.textContent) {
                    return element.textContent;
                } else if (element.innerText) {
                    // This is probably only called on IE8 as of 2014-12-10
                    return element.innerText;
                } else {
                    // For XML DOM
                    var result = "";
                    for (var i = 0; i < element.childNodes.length; i++) {
                        var child = element.childNodes[i];
                        if (child.nodeType == TEXT_TYPE)
                            result += child.nodeValue;
                    }
                    return result;
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
                    if (! _.isUndefined(element.style) && YAHOO.util.Dom.getStyle(element, "display") == "none") return true;
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
                        if (! _.isUndefined(bits[4]) && bits[4] != "") h = h % 12;
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
                        if (! _.isUndefined(bits[3]) && bits[3] != "") h = h % 12;
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
                        if (! _.isUndefined(bits[2]) && bits[2] != "") h = h % 12;
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
                // Support separators: ".", "/", "-", and single space
                {   re: /^(\d{1,2})[./\-\s](\d{1,2})[./\-\s](\d{2,4})$/,
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
                // Support separators: ".", "/", "-", and single space
                {   re: /^(\d{1,2})[./\-\s](\d{1,2})$/,
                    handler: function(bits) {
                        var d;
                        if (ORBEON.util.Properties.formatInputDate.get().indexOf("[D") == 0) {
                            // Day first
                            d = ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._currentYear, parseInt(bits[2], 10) - 1, parseInt(bits[1], 10));
                        } else {
                            // Month first
                            d = ORBEON.util.DateTime._newDate(ORBEON.util.DateTime._currentYear, parseInt(bits[1], 10) - 1, parseInt(bits[2], 10));
                        }
                        return d;
                    }
                },
                // yyyy-mm-dd (ISO style)
                // But also support separators: ".", "/", "-", and single space
                {   re: /(^\d{4})[./\-\s](\d{1,2})[./\-\s](\d{1,2})(Z|([+-]\d{2}:\d{2}))?$/, // allow for optional trailing timezone
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
                return _.isUndefined(opsXFormsProperties) || _.isUndefined(opsXFormsProperties[this.name])
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
                this.formatInputTime = new ORBEON.util.Property("format.input.time", "[h] =[m] =[s] [P]");
                this.formatInputDate = new ORBEON.util.Property("format.input.date", "[M]/[D]/[Y]");
                this.datePickerNavigator = new ORBEON.util.Property("datepicker.navigator", true);
                this.datePickerTwoMonths = new ORBEON.util.Property("datepicker.two-months", false);
                this.showErrorDialog = new ORBEON.util.Property("show-error-dialog", true);
                this.loginPageDetectionRegexp = new ORBEON.util.Property("login-page-detection-regexp", "");
                this.clientEventMode = new ORBEON.util.Property("client.events.mode", "default");
                this.clientEventsFilter = new ORBEON.util.Property("client.events.filter", "");
                this.resourcesVersioned = new ORBEON.util.Property("oxf.resources.versioned", false);
                this.resourcesVersionNumber = new ORBEON.util.Property("oxf.resources.version-number", "");
                this.retryDelayIncrement = new ORBEON.util.Property("retry.delay-increment", 5000);
                this.retryMaxDelay = new ORBEON.util.Property("retry.max-delay", 30000);
                this.useARIA = new ORBEON.util.Property("use-aria", false);
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

            hideModalProgressPanel: function() {
                if (ORBEON.xforms.Globals.modalProgressPanel)
                    ORBEON.xforms.Globals.modalProgressPanel.hide();
            },

            displayModalProgressPanel: function(formID) {
                if (!ORBEON.xforms.Globals.modalProgressPanel) {
                    ORBEON.xforms.Globals.modalProgressPanel =
                    new YAHOO.widget.Panel(ORBEON.xforms.Globals.ns[formID] + "orbeon-spinner", {
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
             * See: http://wiki.orbeon.com/forms/projects/ui/mobile-and-tablet-support#TOC-Problem-and-solution
             */
            overlayUseDisplayHidden: function(overlay) {
                YD.setStyle(overlay.element, "display", "none");
                // For why use subscribers.unshift instead of subscribe, see:
                // http://wiki.orbeon.com/forms/projects/ui/mobile-and-tablet-support#TOC-Avoiding-scroll-when-showing-a-mess
                overlay.beforeShowEvent.subscribers.unshift(new YAHOO.util.Subscriber(function() { YD.setStyle(overlay.element, "display", "block"); }));
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
             * For example: appendToEffectivefId("foo⊙1", "bar") returns "foobar⊙1"
             */
            appendToEffectiveId: function(effectiveId, ending) {
                var prefixedId = ORBEON.util.Utils.getEffectiveIdNoSuffix(effectiveId);
                return prefixedId + ending + ORBEON.util.Utils.getEffectiveIdSuffixWithSeparator(effectiveId);
            },

            /**
             * For example: getEffectiveIdNoSuffix("foo⊙1-2") returns "foo"
             */
            getEffectiveIdNoSuffix: function(effectiveId) {
                if (effectiveId == null)
                    return null;

                var suffixIndex = effectiveId.indexOf(XF_REPEAT_SEPARATOR);
                if (suffixIndex != -1) {
                    return effectiveId.substring(0, suffixIndex);
                } else {
                    return effectiveId;
                }
            },

            /**
             * For example: getRepeatIndexes("foo⊙1-2") returns ["1", "2"]
             */
            getRepeatIndexes: function(effectiveId) {
                if (effectiveId == null)
                    return null;

                var suffixIndex = effectiveId.indexOf(XF_REPEAT_SEPARATOR);
                if (suffixIndex != -1) {
                    return effectiveId.substring(suffixIndex + 1).split(XF_REPEAT_INDEX_SEPARATOR);
                } else {
                    return "";
                }
            },

            /**
             * For example: getEffectiveIdNoSuffix("foo⊙1-2") returns "⊙1-2"
             */
            getEffectiveIdSuffixWithSeparator: function(effectiveId) {
                if (effectiveId == null)
                    return null;

                var suffixIndex = effectiveId.indexOf(XF_REPEAT_SEPARATOR);
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
                    idSuffixWithDepth += XF_REPEAT_INDEX_SEPARATOR + "1";

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
                        // Now clone the template. getElementById("my-input") now returns <input id="my-input$$c⊙1" name="my-input﻿⊙1">
                        //
                        // That's because IE mixes up the element id and the name, AND the name "my-input" incorrectly points to
                        // the cloned element.
                        //
                        // This seems fixed in IE 8 and up in standards mode.
                        //
                        // If we wanted to fix this, we could run the code below also for <textarea> and for all <input>, not
                        // only those with type="radio". We should also try to detect the issue so that we do not run this for IE
                        // 8 in standards mode.
                        var clone = document.createElement("<" + element.tagName + " name='" + newName + "' type='" + element.type + "'>");
                        for (var attributeIndex = 0; attributeIndex < element.attributes.length; attributeIndex++) {
                            var attribute = element.attributes[attributeIndex];
                            if (attribute.nodeName.toLowerCase() != "name" && attribute.nodeName.toLowerCase() != "type" && attribute.nodeName.toLowerCase() != "height" && attribute.nodeValue)
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

                // Escape $ in replacement as it has a special meaning
                replacement = replacement.replace(new RegExp("\\$", "g"), "$$$$");

                var placeholderRegExp = new RegExp(ORBEON.util.Utils.escapeRegex(placeholder), "g");
                stringReplaceWorker(node, placeholderRegExp, replacement);
            },

            // Escape a literal search string so it can be used in String.replace()
            escapeRegex: function(value) {
                return value.replace(/[\-\[\]{}()*+?.,\\\^$|#\s]/g, "\\$&");
            },

            appendRepeatSuffix: function(id, suffix) {
                if (suffix == "")
                    return id;

                // Remove "-" at the beginning of the suffix, if any
                if (suffix.charAt(0) == XF_REPEAT_INDEX_SEPARATOR)
                    suffix = suffix.substring(1);

                // Add suffix with the right separator
                id += id.indexOf(XF_REPEAT_SEPARATOR) == -1 ? XF_REPEAT_SEPARATOR : XF_REPEAT_INDEX_SEPARATOR;
                id += suffix;

                return id;
            },

            /**
             * Locate the delimiter at the given position starting from a repeat begin element.
             *
             * @param repeatId      Can be either a pure repeat ID, such as "foobar" or an ID that contains information of its
             *                      position relative to its parents, such as "foobar.1". The former happens when we handle an
             *                      event such as <xxf:repeat-index id="foobar" new-index="6"/>. In this case
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
                        parentRepeatIndexes = (grandParent == null ? XF_REPEAT_SEPARATOR : XF_REPEAT_INDEX_SEPARATOR)
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
             * Take a sequence a function, and returns a function(testCase, next). If a function in the sequence
             * invokes and XHR, then the following function will only run when that XHR finished.
             */
            runMayCauseXHR: function(/* testCase, continuations...*/) {
                var testCase = arguments[0];
                var continuations = Array.prototype.slice.call(arguments, 1);
                if (continuations.length > 0) {
                    var continuation = continuations.shift();
                    ORBEON.util.Test.executeCausingAjaxRequest(testCase, function() {
                        continuation.call(testCase);
                    }, function() {
                        ORBEON.util.Test.runMayCauseXHR.apply(null, [testCase].concat(continuations));
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
             *
             * You can pass one optional argument: the name of a test function (say 'testSomething'). If this argument is present, then
             * only this specific test will be run. This is useful when debugging test, replacing the call to Test.onOrbeonLoadedRunTest()
             * by a call to Test.onOrbeonLoadedRunTest('testSomething') to only run testSomething().
             */
            onOrbeonLoadedRunTest: function(onlyFunctionName) {
                ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
                    if (parent && parent.TestManager) {
                        parent.TestManager.load();
                    } else {
                        if (! _.isUndefined(onlyFunctionName)) {
                            _.each(YAHOO.tool.TestRunner.masterSuite.items, function(testCase) {
                                _.each(_.functions(testCase), function(functionName) {
                                    if (functionName.indexOf('test') == 0 && functionName != onlyFunctionName)
                                        delete testCase[functionName];
                                })
                            });
                        }
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
                var element = OD.get(id);
                var button = element.tagName.toLowerCase() == "button" ? element : OD.getElementByTagName(element, "button");
                button.click();
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

    getCurrentValueEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    getCurrentValue: function(control) {
        var event = { control: control };
        ORBEON.xforms.Controls.getCurrentValueEvent.fire(event);
        if (! _.isUndefined(event.result)) {
            return event.result;
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-time")) {
            // Time control
            var timeInputValue = YAHOO.util.Dom.getElementsByClassName("xforms-input-input", null, control)[0].value;
            var timeJSDate = ORBEON.util.DateTime.magicTimeToJSDate(timeInputValue);
            return timeJSDate == null ? timeInputValue : ORBEON.util.DateTime.jsDateToISOTime(timeJSDate);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-date")) {
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
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-dateTime")) {
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
            var options = control.getElementsByTagName("select")[0].options;
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
                var spanWithValue = control.getElementsByTagName("span")[0];
                return ORBEON.util.Dom.getStringValue(spanWithValue);
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-range")) {
            var value = ORBEON.xforms.Globals.sliderYui[control.id].previousVal / 200;
            return value.toString();
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
     * @param attribute5        Optional
     */
    beforeValueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    valueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    afterValueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    setCurrentValue: function(control, newControlValue, attribute1, attribute2, attribute3, attribute4, attribute5) {
        var customEvent = { control: control, newValue: newControlValue };
        ORBEON.xforms.Controls.beforeValueChange.fire(customEvent);
        ORBEON.xforms.Controls.valueChange.fire(customEvent);
        var isStaticReadonly = YAHOO.util.Dom.hasClass(control, "xforms-static");
        var formElement = ORBEON.xforms.Controls.getForm(control);
        if (YAHOO.util.Dom.hasClass(control, "xforms-output-appearance-xxforms-download")) {
            // XForms output with xxf:download appearance
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
                var image = YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, control)[0];
                image.src = newControlValue;
            } else {
                var output = YAHOO.util.Dom.getElementsByClassName("xforms-output-output", null, control)[0];
                if (YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
                    output.innerHTML = newControlValue;
                } else {
                    ORBEON.util.Dom.setStringValue(output, newControlValue);
                }
            }
        } else if (_.isNumber(ORBEON.xforms.Globals.changedIdsRequest[control.id])) {
            // User has modified the value of this control since we sent our request:
            // so don't try to update it
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // Triggers don't have a value: don't update them
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-time")) {
            // Time control
            if (! YAHOO.util.Dom.hasClass(document.body, "xforms-ios")) {
                var inputField = control.getElementsByTagName("input")[0];
                var jsDate = ORBEON.util.DateTime.magicTimeToJSDate(newControlValue);
                inputField.value = jsDate == null ? newControlValue : ORBEON.util.DateTime.jsDateToFormatDisplayTime(jsDate);
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-date")) {
            // Date control
            if (! YAHOO.util.Dom.hasClass(document.body, "xforms-ios")) {
                var jsDate = ORBEON.util.DateTime.magicDateToJSDate(newControlValue);
                var displayDate = jsDate == null ? newControlValue : ORBEON.util.DateTime.jsDateToFormatDisplayDate(jsDate);
                if (YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal")) {
                    var imgElement = control.getElementsByTagName("img")[0];
                    ORBEON.util.Dom.setAttribute(imgElement, "alt", displayDate);
                } else {
                    var inputField = control.getElementsByTagName("input")[0];
                    inputField.value = displayDate;
                }
            }
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
            if (attribute4 != null)
                ORBEON.util.Dom.setStringValue(sizeSpan, attribute4);
            // NOTE: Server can send a space-separated value but accept expects a comma-separated value
            if (attribute5 != null)
                $(control).find(".xforms-upload-select").attr("accept", attribute5.split(/\s+/).join(","));
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-type-dateTime")) {
            // Only update value if different from the one we have. This handle the case where the fields contain invalid
            // values with the T letter in them. E.g. aTb/cTd, aTbTcTd sent to server, which we don't know anymore how
            // to separate into 2 values.
            if (! YAHOO.util.Dom.hasClass(document.body, "xforms-ios")) {
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
            }
        } else if ((YAHOO.util.Dom.hasClass(control, "xforms-input") && !YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))
                || YAHOO.util.Dom.hasClass(control, "xforms-secret")) {
            // Regular XForms input (not boolean, date, time or dateTime) or secret
            var input = control.tagName.toLowerCase() == "input" ? control : control.getElementsByTagName("input")[0];
            if (control.value != newControlValue) {
                control.previousValue = newControlValue;
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
            var select = control.getElementsByTagName("select")[0];
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
        } else if (typeof(control.value) == "string") {
            // Textarea, password
            control.value = newControlValue;
            control.previousValue = newControlValue;
        }

        ORBEON.xforms.Controls.afterValueChange.fire(customEvent);
    },

    _setRadioCheckboxClasses: function(target) {
        // Update xforms-selected/xforms-deselected classes on the parent <span> element
        var checkboxInputs = target.getElementsByTagName("input");
        for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
            var checkboxInput = checkboxInputs[checkboxInputIndex];
            var parentSpan = checkboxInput.parentNode;                                              // Boolean checkboxes are directly inside a span
            if (parentSpan.tagName.toLowerCase() == 'label') parentSpan = parentSpan.parentNode;    // While xf:select checkboxes have a label in between
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
        "label":   XF_LHHAI_SEPARATOR + "l",
        "hint":    XF_LHHAI_SEPARATOR + "t",
        "help":    XF_LHHAI_SEPARATOR + "p",
        "alert":   XF_LHHAI_SEPARATOR + "a",
        "control": XF_LHHAI_SEPARATOR + "c"
    },

    /**
     * Look for an HTML element corresponding to an XForms LHHA element.
     * In the HTML generated by the server there is 1 element for each one and 2 for the help.
     */
    getControlLHHA: function(control, lhhaType) {

        // Search by id first
        // See https://github.com/orbeon/orbeon-forms/issues/793
        var lhhaElementId = ORBEON.util.Utils.appendToEffectiveId(control.id, ORBEON.xforms.Controls._classNameToId[lhhaType]);
        var byId = ORBEON.util.Dom.get(lhhaElementId);
        if (byId != null)
            return byId;

        // Search just under the control element, excluding elements with an LHHA id, as they might be for a nested
        // control if we are a grouping control. Test on XF_LHHAI_SEPARATOR as e.g. portals might add their own id.
        // See: https://github.com/orbeon/orbeon-forms/issues/1206
        var lhhaElements = $(control).children('.xforms-' + lhhaType).filter(function() { return this.id.indexOf(XF_LHHAI_SEPARATOR) == -1 });
        return (lhhaElements.length > 0) ? lhhaElements.get(0) : null;
    },

    /**
     * Return the control associated with a given LHHA element and its expected type.
     */
    getControlForLHHA: function(element, lhhaType) {
        var suffix = ORBEON.xforms.Controls._classNameToId[lhhaType];
        // NOTE: could probably do without llhaType parameter
        return element.id.indexOf(suffix) != -1
            ? ORBEON.util.Dom.get(element.id.replace(new RegExp(ORBEON.util.Utils.escapeRegex(ORBEON.xforms.Controls._classNameToId[lhhaType]), "g"), ''))
            : element.parentNode;
    },

    _setMessage: function(control, lhhaType, message) {
        var lhhaElement = ORBEON.xforms.Controls.getControlLHHA(control, lhhaType);
        if (lhhaElement != null) {
            lhhaElement.innerHTML = message;
            if (message == "") {
                if (lhhaType == "help" && !YAHOO.util.Dom.hasClass(lhhaElement, "xforms-disabled")) {
                    // Hide help with empty content
                    YAHOO.util.Dom.addClass(lhhaElement, "xforms-disabled-subsequent");
                    // If this is the help element, also disable help image
                    var help = ORBEON.xforms.Controls.getControlLHHA(control, "help");
                    YAHOO.util.Dom.addClass(help, "xforms-disabled-subsequent");
                }
            } else {
                // We show LHHA with non-empty content, but ONLY if the control is relevant
                if (ORBEON.xforms.Controls.isRelevant(control)) {
                    YAHOO.util.Dom.removeClass(lhhaElement, "xforms-disabled");
                    YAHOO.util.Dom.removeClass(lhhaElement, "xforms-disabled-subsequent");
                    // If this is the help element, also enable the help image
                    if (lhhaType == "help") {
                        var help = ORBEON.xforms.Controls.getControlLHHA(control, "help");
                        YAHOO.util.Dom.removeClass(help, "xforms-disabled");
                        YAHOO.util.Dom.removeClass(help, "xforms-disabled-subsequent");
                    }
                }
            }
        }
        ORBEON.xforms.Controls.lhhaChangeEvent.fire({ control: control, type: lhhaType, message: message });
    },

    lhhaChangeEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),

    getLabelMessage: function(control) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // Element is "label" and "control" at the same time so use "control"
            var labelElement = ORBEON.xforms.Controls.getControlLHHA(control, "control");
            return labelElement.innerHTML;
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
            var labelElement = ORBEON.xforms.Controls.getControlLHHA(control, "label");
            return labelElement == null ? "" : labelElement.innerHTML;
        }
    },

    setLabelMessage: function(control, message) {
        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger")
                || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // Element is "label" and "control" at the same time so use "control"
            ORBEON.xforms.Controls._setMessage(control, "control", message);
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
        var helpElement = ORBEON.xforms.Controls.getControlLHHA(control, "help");
        return helpElement == null ? "" : ORBEON.util.Dom.getStringValue(helpElement);
    },

    setHelpMessage: function(control, message) {
        // We escape the value because the help element is a little special, containing escaped HTML
        message = ORBEON.util.String.escapeForMarkup(message);
        ORBEON.xforms.Controls._setMessage(control, "help", message);
        ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.helpTooltipForControl);
    },

    setConstraintLevel: function(control, newLevel) {

        var alertActive = newLevel != "";

        function toggleCommonClasses(element) {
            $(element).toggleClass("xforms-invalid", newLevel == "error");
            $(element).toggleClass("xforms-warning", newLevel == "warning");
            $(element).toggleClass("xforms-info",    newLevel == "info");
        }

        // Classes on control
        toggleCommonClasses(control);

        // Classes on alert if any
        var alertElement = ORBEON.xforms.Controls.getControlLHHA(control, "alert");
        if (alertElement) {

            $(alertElement).toggleClass("xforms-active", alertActive);

            if (! _.isUndefined($(alertElement).attr("id")))
                toggleCommonClasses(alertElement);
        }

        // If the control is now valid and there is an alert tooltip for this control, get rid of it
        var alertTooltip = ORBEON.xforms.Globals.alertTooltipForControl[control.id];
        if (alertTooltip != null && alertTooltip != true) {
            if (! alertActive) {
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
                ORBEON.xforms.Controls.getControlLHHA(control, "label"),
                ORBEON.xforms.Controls.getControlLHHA(control, "alert")
            ];
            // Also show help if message is not empty
            if (!isRelevant || (isRelevant && ORBEON.xforms.Controls.getHelpMessage(control) != "")) {
                elementsToUpdate.push(ORBEON.xforms.Controls.getControlLHHA(control, "help"));
            }
            // Also show hint if message is not empty
            if (!isRelevant || (isRelevant && ORBEON.xforms.Controls.getHintMessage(control) != ""))
                elementsToUpdate.push(ORBEON.xforms.Controls.getControlLHHA(control, "hint"));

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
            var select = control.getElementsByTagName("select")[0];
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
                    YAHOO.util.Dom.getElementsByClassName("xforms-upload-select", null, control)[0], isReadonly);
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")) {
            // Textarea
            var textarea = control.getElementsByTagName("textarea")[0];
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
        var alertElement = ORBEON.xforms.Controls.getControlLHHA(control, "alert");
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
            var hintElement = ORBEON.xforms.Controls.getControlLHHA(control, "hint");
            return hintElement == null ? "" : hintElement.innerHTML;
        }
    },

    setHintMessage: function(control, message) {
        // Destroy existing tooltip if it was for a control which isn't anymore in the DOM
        var tooltips = ORBEON.xforms.Globals.hintTooltipForControl;
        if (tooltips[control.id] != null) {
            if (tooltips[control.id].cfg.getProperty('context')[0] != control)
                tooltips[control.id] = null
        }

        if (YAHOO.util.Dom.hasClass(control, "xforms-trigger") || YAHOO.util.Dom.hasClass(control, "xforms-submit")) {
            // For triggers, the value is stored in the title for the control


            if (tooltips[control.id] == null) {
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
        ORBEON.xforms.Controls._setTooltipMessage(control, message, tooltips);
    },

    _setTooltipMessage: function(control, message, tooltipForControl) {
        // If we have a YUI tooltip for this control, update the tooltip
        var currentTooltip = tooltipForControl[control.id];
        if (currentTooltip) {
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

    },

    /**
     * Sets focus to the specified control. This is called by the JavaScript code
     * generated by the server, which we invoke on page load.
     */
    setFocus: function(controlId) {

        // Don't bother focusing if the control is already focused. This also prevents issues with maskFocusEvents,
        // whereby maskFocusEvents could be set to true below, but then not cleared back to false if no focus event
        // is actually dispatched.
        if (ORBEON.xforms.Globals.currentFocusControlId == controlId)
            return;

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
            var htmlControl = $(control).find('input:visible, textarea:visible, select:visible, button:visible, a:visible');
            if (htmlControl.is('*'))
                ORBEON.util.Dom.focus(htmlControl.get(0));
            else
                // We haven't found anything to set the focus on, so don't mask the focus event, since we won't receive it
                ORBEON.xforms.Globals.maskFocusEvents = false;
        }

        // Save current value as server value. We usually do this on focus, but for control where we set the focus
        // with xf:setfocus, we still receive the focus event when the value changes, but after the change event
        // (which means we then don't send the new value to the server).
        if (ORBEON.xforms.ServerValueStore.get(controlId) == null) {
            var currentValue = ORBEON.xforms.Controls.getCurrentValue(control);
            ORBEON.xforms.ServerValueStore.set(controlId, currentValue);
        }
    },

    removeFocus: function(controlId) {

        // If not control has the focus, there is nothing to do
        if (ORBEON.xforms.Globals.currentFocusControlId == null)
            return;

        var control = ORBEON.util.Dom.get(controlId);

        if (YAHOO.util.Dom.hasClass(control, "xforms-select-appearance-full")
                || YAHOO.util.Dom.hasClass(control, "xforms-select1-appearance-full")
                || (YAHOO.util.Dom.hasClass(control, "xforms-input") && YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))) {
            // Radio button or checkbox
            var formInputs = ORBEON.util.Dom.getElementsByName(control, "input");
            if (formInputs.length > 0) {
                var itemIndex = 0;
                // Blur all of them (can we know which one has focus if any?)
                for (; itemIndex < formInputs.length; itemIndex++) {
                    var formInput = formInputs[itemIndex];
                    ORBEON.util.Dom.blur(formInput);
                }
            }
        } else if (YAHOO.util.Dom.hasClass(control, "xforms-textarea")
                && YAHOO.util.Dom.hasClass(control, "xforms-mediatype-text-html")) {
            // Special case for RTE
            ORBEON.xforms.Page.getControl(control).removeFocus();
        } else {
            // Generic code to find focusable descendant-or-self HTML element and focus on it
            var htmlControlNames = [ "input", "textarea", "select", "button", "a" ];
            var htmlControl = ORBEON.util.Dom.getElementByTagName(control, htmlControlNames);
            // If we found a control set the focus on it
            if (htmlControl != null) ORBEON.util.Dom.blur(htmlControl);
        }

        // Mark that no control has the focus
        ORBEON.xforms.Globals.currentFocusControlId = null;
        ORBEON.xforms.Globals.currentFocusControlElement = null;
    },

    /**
     * Update the visited state of a control, including its external alert if any.
     */
    updateVisited: function(control, newVisited) {

        // Classes on control
        $(control).toggleClass("xforms-visited", newVisited);

        // Classes on external alert if any
        // Q: Is this 100% reliable to determine if the alert is external?
        var alertElement = ORBEON.xforms.Controls.getControlLHHA(control, "alert");
        if (alertElement && ! _.isUndefined($(alertElement).attr("id")))
            $(alertElement).toggleClass("xforms-visited", newVisited);
    },

    /**
     * Update the xforms-empty/filled classes as necessary.
     */
    updateRequiredEmpty: function(control, emptyAttr) {
        var isRequired = $(control).hasClass("xforms-required");

        $(control).toggleClass("xforms-empty", isRequired  && emptyAttr == "true");
        $(control).toggleClass("xforms-filled", isRequired && emptyAttr == "false");
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
    },

    typeChangedEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),

    /**
     * Find the beginning of a case.
     */
    findCaseBegin: function(controlId) {
        var caseBeginId = "xforms-case-begin-" + controlId;
        return ORBEON.util.Dom.get(caseBeginId);
    },

    /**
     * Whether a case is selected or not.
     */
    isCaseSelected: function(controlId) {
        var caseBegin = ORBEON.xforms.Controls.findCaseBegin(controlId);
        return YAHOO.util.Dom.hasClass(caseBegin, "xforms-case-selected");
    },

    /**
     * Toggle a single case.
     *
     * [1] We disable the open/close animation on IE10 and under. The animation works on IE11, but we've seen problems
     *     with earlier versions, so, since the animation is only cosmetic, we determine that it isn't worth debugging
     *     those issues and that we're better off just disabling the animation for those old versions of IE.
     *     YAHOO.env.ua.ie returns 0 for IE11, as YUI doesn't detect IE11 as being IE.
     */
    toggleCase: function(controlId, visible) {
        var caseBegin = ORBEON.xforms.Controls.findCaseBegin(controlId);
        var caseBeginParent = caseBegin.parentNode;
        var foundCaseBegin = false;
        for (var childIndex = 0; caseBeginParent.childNodes.length; childIndex++) {
            var cursor = caseBeginParent.childNodes[childIndex];
            if (!foundCaseBegin) {
                if (cursor.id == caseBegin.id) foundCaseBegin = true;
                else continue;
            }
            if (cursor.nodeType == ELEMENT_TYPE) {
                // Change visibility by switching class
                if (cursor.id == "xforms-case-end-" + controlId) break;
                var doAnimate = cursor.id != "xforms-case-begin-" + controlId &&    // Don't animate case-begin/end
                        YAHOO.util.Dom.hasClass(cursor, "xxforms-animate") &&       // Only animate if class present
                        YAHOO.env.ua.ie == 0;                                       // Simply disable animation for IE<=10 [1]

                var updateClasses = _.partial(function(el) {
                    if (visible) {
                        YAHOO.util.Dom.addClass   (el, "xforms-case-selected");
                        YAHOO.util.Dom.removeClass(el, "xforms-case-deselected");
                        YAHOO.util.Dom.removeClass(el, "xforms-case-deselected-subsequent");
                    } else {
                        YAHOO.util.Dom.addClass   (el, "xforms-case-deselected-subsequent");
                        YAHOO.util.Dom.removeClass(el, "xforms-case-selected");
                    }
                }, cursor);

                if (doAnimate) {
                    if (visible) {
                        updateClasses();
                        $(cursor).css('display', 'none');  // So jQuery's toggle knows the block is hidden
                        $(cursor).animate({ height: 'toggle' }, { duration: 200 });
                    } else {
                        $(cursor).animate({ height: 'toggle' }, { duration: 200, complete: updateClasses });
                    }
                } else {
                    updateClasses();
                    ORBEON.util.Dom.nudgeAfterDelay(cursor);
                }
            }
        }
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
     *          <span class="xforms-repeat-begin-end" id="repeat-begin-inner-repeat⊙1">
     *          <span class="xforms-repeat-delimiter">
     *              ...
     *          <span class="xforms-repeat-begin-end" id="repeat-end-inner-repeat⊙1"></span>
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
                } else if ($(element).is('.xforms-control, .xbl-component')) {
                    return element;
                } else if (YAHOO.util.Dom.hasClass(element, "xforms-dialog")
                        || YAHOO.util.Dom.hasClass(element, "xforms-help")
                        || YAHOO.util.Dom.hasClass(element, "xforms-alert")) {
                    return element;
                }
            }
            // Go to parent and continue search
            element = element.parentNode;
        }
    },

    _findAncestorFocusableControl: function(eventTarget) {
        var ancestorControl = ORBEON.xforms.Events._findParentXFormsControl(eventTarget);

        var sendFocus =
            ancestorControl != null
            // We don't run this for dialogs, as there is not much sense doing this AND this causes issues with
            // FCKEditor embedded within dialogs with IE. In that case, the editor gets a blur, then the dialog, which
            // prevents detection of value changes in focus() above.
            && ! YAHOO.util.Dom.hasClass(ancestorControl, "xforms-dialog")
            // Don't send focus for XBL component that are not focusable
            && ! $(ancestorControl).is('.xbl-component:not(.xbl-focusable)');

        return sendFocus ? ancestorControl : null;
    },

    focus: function(event) {
        var eventTarget = YAHOO.util.Event.getTarget(event);
        // If the browser does not support capture, register listener for change on capture
        if (_.isUndefined(document.addEventListener)) {
            YAHOO.util.Dom.generateId(eventTarget);
            var changeListenerElement = ORBEON.xforms.Globals.changeListeners[eventTarget.id];
            var needToRegisterChangeListener = _.isUndefined(changeListenerElement) || changeListenerElement != eventTarget;
            if (needToRegisterChangeListener) {
                YAHOO.util.Event.addListener(eventTarget, "change", ORBEON.xforms.Events.change);
                ORBEON.xforms.Globals.changeListeners[eventTarget.id] = eventTarget;
            }
        }
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            // Control elements
            var newFocusControlElement = ORBEON.xforms.Events._findAncestorFocusableControl(eventTarget);
            var currentFocusControlElement = ORBEON.xforms.Globals.currentFocusControlId != null ? ORBEON.util.Dom.get(ORBEON.xforms.Globals.currentFocusControlId) : null;

            if (newFocusControlElement != null) {
                // Store initial value of control if we don't have a server value already, and if this is is not a list
                // Initial value for lists is set up initialization, as when we receive the focus event the new value is already set.
                if (ORBEON.xforms.ServerValueStore.get(newFocusControlElement.id) == null
                        && ! YAHOO.util.Dom.hasClass(newFocusControlElement, "xforms-select-appearance-compact")
                        && ! YAHOO.util.Dom.hasClass(newFocusControlElement, "xforms-select1-appearance-compact")) {
                    var controlCurrentValue = ORBEON.xforms.Controls.getCurrentValue(newFocusControlElement);
                    ORBEON.xforms.ServerValueStore.set(newFocusControlElement.id, controlCurrentValue);
                }
            }

            // The idea here is that we only register focus changes when focus moves between XForms controls. If focus
            // goes out to nothing, we don't handle it at this point but wait until focus comes back to a control.
            if (newFocusControlElement != null && currentFocusControlElement != newFocusControlElement) {

                // Send focus events
                var events = [];

                // Handle special value changes upon losing focus

                // HTML area does not throw value change event, so we send the value change to the server
                // when we get the focus on the next control
                var changeValue = false;
                if (currentFocusControlElement != null) { // Can be null on first focus
                    if (YAHOO.util.Dom.hasClass(currentFocusControlElement, "xforms-textarea")
                            && YAHOO.util.Dom.hasClass(currentFocusControlElement, "xforms-mediatype-text-html")) {
                        changeValue = true;
                    }
                    // Send value change if needed
                    if (changeValue) xformsValueChanged(currentFocusControlElement);
                }

                // Handle focus
                events.push(new ORBEON.xforms.server.AjaxServer.Event(null, newFocusControlElement.id, null, "xforms-focus"));

                // Keep track of the id of the last known control which has focus
                ORBEON.xforms.Globals.currentFocusControlId = newFocusControlElement.id;
                ORBEON.xforms.Globals.currentFocusControlElement = newFocusControlElement;

                // Fire events
                ORBEON.xforms.server.AjaxServer.fireEvents(events, true);
            }

        } else {
            ORBEON.xforms.Globals.maskFocusEvents = false;
        }
    },

    blurEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    blur: function(event) {
        if (!ORBEON.xforms.Globals.maskFocusEvents) {
            var target = YAHOO.util.Event.getTarget(event);
            var control = ORBEON.xforms.Events._findAncestorFocusableControl(target);
            if (control != null) {
                ORBEON.xforms.Events.blurEvent.fire({control: control, target: target});

                ORBEON.xforms.Globals.currentFocusControlId = control.id;
                ORBEON.xforms.Globals.currentFocusControlElement = control;

                // Dispatch xxforms-blur event if we're not going to another XForms control (see issue #619)
                var relatedTarget = event.relatedTarget || document.activeElement;
                var relatedControl = ORBEON.xforms.Events._findAncestorFocusableControl(relatedTarget);
                if (relatedControl == null) {
                    ORBEON.xforms.Globals.currentFocusControlId = null;
                    var events = [new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, "xxforms-blur")];
                    ORBEON.xforms.server.AjaxServer.fireEvents(events, false);
                }
            }
        }
    },

    change: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            if (YAHOO.util.Dom.hasClass(target, "xforms-upload")) {
                // Dispatch change event to upload control
                ORBEON.xforms.Page.getControl(target).change();
            } else {
                // When we move out from a field, we don't receive the keyup events corresponding to keydown
                // for that field (go figure!). Se we reset here the count for keypress without keyup for that field.
                if (_.isNumber(ORBEON.xforms.Globals.changedIdsRequest[target.id]))
                    ORBEON.xforms.Globals.changedIdsRequest[target.id] = 0;

                if (YAHOO.util.Dom.hasClass(target, "xforms-select1-appearance-compact")) {
                    // For select1 list, make sure we have exactly one value selected
                    var select = ORBEON.util.Dom.getElementByTagName(target, "select");
                    if (select.value == "") {
                        // Stop end-user from deselecting last selected value
                        select.options[0].selected = true;
                    } else {
                        // Deselect options other than the first one
                        var foundSelected = false;
                        for (var optionIndex = 0; optionIndex < select.options.length; optionIndex++) {
                            var option = select.options[optionIndex];
                            if (option.selected) {
                                if (foundSelected) option.selected = false;
                                else foundSelected = true;
                            }
                        }
                    }
                } else if (! $('body').is('.xforms-ios') &&
                            (
                                    YAHOO.util.Dom.hasClass(target, "xforms-type-time")
                                || (YAHOO.util.Dom.hasClass(target, "xforms-type-date") && !YAHOO.util.Dom.hasClass(target, "xforms-input-appearance-minimal"))
                                || YAHOO.util.Dom.hasClass(target, "xforms-type-dateTime")
                            )
                ) {
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
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, controlCurrentValue, "xxforms-value");
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
        return ! _.isUndefined(keyCode) &&
            keyCode != 9 && keyCode != 16 && keyCode != 17 && keyCode != 18 &&
            (YAHOO.util.Dom.hasClass(control, "xforms-input") || YAHOO.util.Dom.hasClass(control, "xforms-secret")
                    || YAHOO.util.Dom.hasClass(control, "xforms-textarea"));
    },

    keydownEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    keydown: function(event) {

        // On IE prevent default behavior when the esc key is pressed, which otherwise would reset all the form fields
        // See https://github.com/orbeon/orbeon-forms/issues/131
        if ($.browser.msie && event.keyCode == 27)
            YAHOO.util.Event.preventDefault(event);

        var target = YAHOO.util.Event.getTarget(event);
        var control = ORBEON.xforms.Events._findParentXFormsControl(target);
        if (control != null) {
            ORBEON.xforms.Events.keydownEvent.fire({control: control, target: target});
            if (ORBEON.xforms.Events._isChangingKey(control, event.keyCode)) {
                ORBEON.xforms.Globals.changedIdsRequest[control.id] =
                    (! _.isNumber(ORBEON.xforms.Globals.changedIdsRequest[control.id])) ? 1
                            : ORBEON.xforms.Globals.changedIdsRequest[control.id] + 1;
            }
        }
    },

    keypressEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    keypress: function(event) {
        var target = YAHOO.util.Event.getTarget(event);
        var control = ORBEON.xforms.Events._findParentXFormsControl(target);
        if (control != null) {
            ORBEON.xforms.Events.keypressEvent.fire({control: control, target: target, keyCode: event.keyCode});
            // Input field and auto-complete: trigger DOMActive when when enter is pressed
            if ((YAHOO.util.Dom.hasClass(control, "xforms-input") && !YAHOO.util.Dom.hasClass(control, "xforms-type-boolean"))
                    || YAHOO.util.Dom.hasClass(control, "xforms-secret")) {
                if (event.keyCode == 10 || event.keyCode == 13) {
                    // Send a value change and DOM activate
                    var events = [
                        new ORBEON.xforms.server.AjaxServer.Event(null, control.id, ORBEON.xforms.Controls.getCurrentValue(control), "xxforms-value"),
                        new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, "DOMActivate")
                    ];
                    ORBEON.xforms.server.AjaxServer.fireEvents(events, false);
                    // This prevents Chrome/Firefox from dispatching a 'change' event on event, making them more
                    // like IE, which in this case is more compliant to the spec.
                    YAHOO.util.Event.preventDefault(event);
                    // Force a change event if the value has changed, creating a new "change point", which the
                    // browser will use to dispatch a `change` event in the future. Also see issue #1207.
                    $(target).blur().focus();
                }
            }
        }
    },

    keyup: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {
            // Remember we have received the keyup for this element
            // NOTE: `changedIdsRequest` can be undefined in some cases. Test that it is a number before decrementing!
            // It is unclear why this can be the case, but see https://github.com/orbeon/orbeon-forms/issues/1732.
            if (ORBEON.xforms.Events._isChangingKey(target, event.keyCode) && _.isNumber(ORBEON.xforms.Globals.changedIdsRequest[target.id]))
                ORBEON.xforms.Globals.changedIdsRequest[target.id]--;
            // Incremental control: treat keypress as a value change event
            if (YAHOO.util.Dom.hasClass(target, "xforms-incremental")) {
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, ORBEON.xforms.Controls.getCurrentValue(target), "xxforms-value");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], true);
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
            if (message == "") {
                // Makes it easier for test to check that the mouseover did run
                tooltipForControl[control.id] = null;
            } else {
                // We have a hint, initialize YUI tooltip
                var yuiTooltip = new YAHOO.widget.Tooltip(control.id + toolTipSuffix, {
                    context: target,
                    text: message,
                    showDelay: delay,
                    effect: {effect: YAHOO.widget.ContainerEffect.FADE, duration: 0.2},
                    // We provide here a "high" zIndex value so the tooltip is "always" displayed on top over everything else.
                    // Otherwise, with dialogs, the tooltip might end up being below the dialog and be invisible.
                    zIndex: 10000
                });
                yuiTooltip.orbeonControl = control;
                // Send the mouse move event, because the tooltip gets positioned when receiving a mouse move.
                // Without this, sometimes the first time the tooltip is shows at the top left of the screen
                yuiTooltip.onContextMouseMove.call(target, event, yuiTooltip);
                // Send the mouse over event to the tooltip, since the YUI tooltip didn't receive it as it didn't
                // exist yet when the event was dispatched by the browser
                yuiTooltip.onContextMouseOver.call(target, event, yuiTooltip);
                // Save reference to YUI tooltip
                tooltipForControl[control.id] = yuiTooltip;
            }
        }
    },

    mouseover: function(event) {
        var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
        if (target != null) {

            // Hint tooltip
            if (! $(target).closest(".xforms-disable-hint-as-tooltip").is("*")) {
                var message = ORBEON.xforms.Controls.getHintMessage(target);
                if (YAHOO.util.Dom.hasClass(target, "xforms-trigger") || YAHOO.util.Dom.hasClass(target, "xforms-submit")) {
                    // Remove the title, to avoid having both the YUI tooltip and the browser tooltip based on the title showing up
                    var formElement = ORBEON.util.Dom.getElementByTagName(target, ["a", "button"]);
                    formElement.title = "";
                }
                ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.hintTooltipForControl, target, target, "-orbeon-hint-tooltip", message, 200, event);
            }

            // Alert tooltip
            if ($(target).is(".xforms-alert.xforms-active") && ! $(target).closest(".xforms-disable-alert-as-tooltip").is("*")) {
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
                        ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.alertTooltipForControl, control, target, "-orbeon-alert-tooltip", message, 10, event);
                    }
                }
            } else if (YAHOO.util.Dom.hasClass(target, "xforms-dialog-appearance-minimal")) {
                // Minimal dialog: record more is back inside the dialog
                ORBEON.xforms.Globals.dialogMinimalLastMouseOut[target.id] = -1;
            }

            // Help tooltip
            if (ORBEON.util.Properties.helpTooltip.get() && YAHOO.util.Dom.hasClass(target, "xforms-help")) {
                // Get control
                var control = ORBEON.xforms.Controls.getControlForLHHA(target, "help");
                if (control) {
                    // The xf:input is a unique case where the 'for' points to the input field, not the element representing the control
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
            if (YAHOO.lang.isObject(yuiTooltip) && ! $(target).closest(".xforms-disable-hint-as-tooltip").is("*")) {
                yuiTooltip.onContextMouseOut.call(target.id, event, yuiTooltip);
            }
        }
    },

    clickEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    click: function(event) {
        // Stop processing if the mouse button that was clicked is not the left button
        // See: http://www.quirksmode.org/js/events_properties.html#button
        if (event.button != 0 && event.button != 1) return;
        var originalTarget = YAHOO.util.Event.getTarget(event);
        var target = ORBEON.xforms.Events._findParentXFormsControl(originalTarget);
        // Listeners might be interested in click events even if they don't target an XForms control
        ORBEON.xforms.Events.clickEvent.fire({target: originalTarget, control: target});
        if (YAHOO.lang.isObject(originalTarget) && YAHOO.lang.isBoolean(originalTarget.disabled) && originalTarget.disabled) {
            // IE calls the click event handler on clicks on disabled controls, which Firefox doesn't.
            // To make processing more similar on all browsers, we stop going further here if we go a click on a disabled control.
            return;
        }

        if (target != null && (YAHOO.util.Dom.hasClass(target, "xforms-trigger") || YAHOO.util.Dom.hasClass(target, "xforms-submit"))) {
            // Click on trigger
            YAHOO.util.Event.preventDefault(event);
            if (!YAHOO.util.Dom.hasClass(target, "xforms-readonly")) {
                // If this is an anchor and we didn't get a chance to register the focus event,
                // send the focus event here. This is useful for anchors (we don't listen on the
                // focus event on those, and for buttons on Safari which does not dispatch the focus
                // event for buttons.
                ORBEON.xforms.Events.focus(event);
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, null, "DOMActivate");
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
        } else if (target != null && ! YAHOO.util.Dom.hasClass(target, "xforms-static") &&
                   (YAHOO.util.Dom.hasClass(target, "xforms-select1-appearance-full")
                || YAHOO.util.Dom.hasClass(target, "xforms-select-appearance-full")
                || (YAHOO.util.Dom.hasClass(target, "xforms-input") && YAHOO.util.Dom.hasClass(target, "xforms-type-boolean")))) {
            // Click on checkbox or radio button

            // Update classes right away to give user visual feedback
            ORBEON.xforms.Controls._setRadioCheckboxClasses(target);
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, ORBEON.xforms.Controls.getCurrentValue(target), "xxforms-value");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);

        } else if (target != null && YAHOO.util.Dom.hasClass(target, "xforms-upload") && YAHOO.util.Dom.hasClass(originalTarget, "xforms-upload-remove")) {
            // Click on remove icon in upload control
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, "", "xxforms-value");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        } else if (target != null && YAHOO.util.Dom.hasClass(target, "xforms-help")) {
            // Help image

            // Get control for this help image
            var control = ORBEON.xforms.Controls.getControlForLHHA(target, "help");
            if (ORBEON.util.Properties.helpHandler.get()) {
                // We are sending the xforms-help event to the server and the server will tell us what do to
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, "xforms-help");
                ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
            } else {
                // If the servers tells us there are no event handlers for xforms-help in the page,
                // we can avoid a round trip and show the help right away
                ORBEON.xforms.Controls.showHelp(control);
            }
        }

        // Click on something that is not an XForms element, but which might still be in an repeat iteration,
        // in which case we want to let the server know about where in the iteration the click was.

        var node = originalTarget;

        // Iterate on ancestors, stop when we don't find ancestors anymore or we arrive at the form element
        while (node != null && ! (ORBEON.util.Dom.isElement(node) && node.tagName.toLowerCase() == "form")) {

            // First check clickable group
            if (YAHOO.util.Dom.hasClass(node, "xforms-activable")) {
                var event = new ORBEON.xforms.server.AjaxServer.Event(form, node.id, null, "DOMActivate");
                ORBEON.xforms.server.AjaxServer.fireEvents([event]);
                break;
            }

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
                        targetId += targetId.indexOf(XF_REPEAT_SEPARATOR) == -1 ? XF_REPEAT_SEPARATOR : XF_REPEAT_INDEX_SEPARATOR;
                        targetId += delimiterCount;
                        var event = new ORBEON.xforms.server.AjaxServer.Event(form, targetId, null, "xxforms-repeat-activate");
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
                if (tooltip != null) {
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
        var rangeControl = ORBEON.util.Dom.get(this.id).parentNode;

        var value = offset / 200;
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, rangeControl.id, String(value), "xxforms-value");
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
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, dialog.id, null, "xxforms-dialog-close");
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    },

    /**
     * Event listener on dialogs called by YUI when the dialog is shown.
     */
	dialogShow: function(type, args, me) {
        var dialogId = me;

		if (ORBEON.xforms.Globals.isRenderingEngineTrident) {
            // On IE6, when the dialog is opened for the second time, part of the dialog are not visible.
            // Setting the class again on the dialog gives notch to IE and is hack to get around this issue.
            var dialogElement = ORBEON.util.Dom.get(dialogId);
			dialogElement.className = dialogElement.className;
		}

        // Set a max-height on the dialog body, so the dialog doesn't get larger than the viewport
        var yuiDialog = ORBEON.xforms.Globals.dialogs[dialogId];
        var maxHeight =
            YAHOO.util.Dom.getViewportHeight()
            - (yuiDialog.element.clientHeight - yuiDialog.body.clientHeight)
            // Don't use the whole height of the viewport, leaving some space at the top of the page,
            // which could be used by a navigation bar, as in Liferay
            - 80;
        var property = $(yuiDialog.innerElement).is('.xxforms-set-height') ? 'height' : 'max-height';
        $(yuiDialog.body).css(property, maxHeight + 'px');
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
     * Hide both show and hide details section in the error dialog.
     */
    errorHideAllDetails: function(errorBodyDiv) {
        var detailsHidden = ORBEON.util.Dom.getChildElementByClass(errorBodyDiv, "xforms-error-panel-details-hidden");
        var detailsShown = ORBEON.util.Dom.getChildElementByClass(errorBodyDiv, "xforms-error-panel-details-shown");

        if (detailsHidden != null)
            YAHOO.util.Dom.addClass(detailsHidden, "xforms-disabled-subsequent");

        if (detailsShown != null)
            YAHOO.util.Dom.addClass(detailsShown, "xforms-disabled-subsequent");
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
        if (yuiDialog.element.style.visibility != "hidden") {
            // Abort if one of the parents is drop-down dialog
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
                var event = new ORBEON.xforms.server.AjaxServer.Event(null, yuiDialog.id, null, "xxforms-dialog-close");
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
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, yuiDialog.element.id, null, "xxforms-dialog-close");
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
            var event = new ORBEON.xforms.server.AjaxServer.Event(null, heartBeatDiv.id, null, "xxforms-session-heartbeat");
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
                    return YUD.hasClass(node, "xforms-update-full") || YUD.hasClass(node, "xxforms-dynamic-control") ? (fullUpdate = node, true) : false;
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
            var hasInit = ! _.isUndefined(xblClass.prototype.init);

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
                    if (! _.isUndefined(originalDestroy))
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
                if (_.isUndefined(this._instances))
                    this._instances = {};
                // Get or create instance
                var instance = this._instances[container.id];
                if (_.isUndefined(instance) || YAHOO.lang.isNull(instance) || instance.container != container) {
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

    callValueChanged: function(prefix, component, target, property) {
        var partial = YAHOO.xbl;                                    if (partial == null) return;
        partial = partial[prefix];                                  if (partial == null) return;
        partial = partial[component];                               if (partial == null) return;
        partial = partial.instance(target);                         if (partial == null) return;
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

        ORBEON.xforms.Init._specialControlsInitFunctions = ORBEON.xforms.Init._specialControlsInitFunctions || {
            "select1": {
                "compact" : ORBEON.xforms.Init._list
            },
            "select": {
                "compact" : ORBEON.xforms.Init._list
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

        _.extend(ORBEON.xforms.Globals, {
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
            xformsServerURL: {},                 // XForms Server URL
            xformsServerUploadURL: {},           // XForms Server upload URL
            calendarImageURL: {},                // calendar.png image URL (should be ideally handled by a template)
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
            sliderYui: {},                       // Maps slider id to the YUI object for that slider
            isReloading: false,                  // Whether the form is being reloaded from the server
            lastDialogZIndex: 1050,              // zIndex of the last dialog displayed; gets incremented so the last dialog is always on top of everything else; initial value set to Bootstrap's @zindexModal
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
                ORBEON.xforms.Globals.topLevelListenerRegistered == null ? false : ORBEON.xforms.Globals.topLevelListenerRegistered,

            // Parse and store initial repeat hierarchy
            processRepeatHierarchy: function(repeatTreeString) {
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
            }
        });

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

        // Add the xforms-ios class on the body if we are on iOS
        if (YAHOO.env.ua.webkit && YAHOO.env.ua.mobile)
            YAHOO.util.Dom.addClass(document.body, "xforms-ios");

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

                _.each($(formElement).find('.xforms-error-dialogs > .xforms-error-panel'), function(errorPanelEl) {
                    // Create and store error panel
                    YAHOO.util.Dom.generateId(errorPanelEl);
                    YAHOO.util.Dom.removeClass(errorPanelEl, "xforms-initially-hidden");
                    var errorPanel = new YAHOO.widget.Panel(errorPanelEl.id, {
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
                    var titleDiv = ORBEON.util.Dom.getChildElementByClass(errorPanelEl, "hd");
                    var bodyDiv = ORBEON.util.Dom.getChildElementByClass(errorPanelEl, "bd");
                    var detailsHiddenDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-error-panel-details-hidden");
                    var showDetailsA = ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(detailsHiddenDiv, 0), 0);
                    YAHOO.util.Dom.generateId(showDetailsA);

                    // Find reference to elements in the details shown section
                    var detailsShownDiv = ORBEON.util.Dom.getChildElementByClass(bodyDiv, "xforms-error-panel-details-shown");
                    var hideDetailsA = ORBEON.util.Dom.getChildElementByIndex(ORBEON.util.Dom.getChildElementByIndex(detailsShownDiv, 0), 0);
                    YAHOO.util.Dom.generateId(hideDetailsA);
                    errorPanel.errorTitleDiv = titleDiv;
                    errorPanel.errorBodyDiv = bodyDiv;
                    errorPanel.errorDetailsDiv = ORBEON.util.Dom.getChildElementByClass(detailsShownDiv, "xforms-error-panel-details");

                    // Register listener that will show/hide the detail section
                    YAHOO.util.Event.addListener(showDetailsA.id, "click", ORBEON.xforms.Events.errorShowHideDetails);
                    YAHOO.util.Event.addListener(hideDetailsA.id, "click", ORBEON.xforms.Events.errorShowHideDetails);

                    // Handle listeners on error panel
                    var closeA = YAHOO.util.Dom.getElementsByClassName("xforms-error-panel-close", null, errorPanelEl);
                    if (closeA.length != 0) {
                        YAHOO.util.Dom.generateId(closeA[0]);
                        YAHOO.util.Event.addListener(closeA[0].id, "click", ORBEON.xforms.Events.errorCloseClicked, errorPanel);
                    }

                    var reloadA = YAHOO.util.Dom.getElementsByClassName("xforms-error-panel-reload", null, errorPanelEl);
                    if (reloadA.length != 0) {
                        YAHOO.util.Dom.generateId(reloadA[0]);
                        YAHOO.util.Event.addListener(reloadA[0].id, "click", ORBEON.xforms.Events.errorReloadClicked, errorPanel);
                    }
                });

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
                        } else {
                            // The user reloaded or navigated back to this page. Reset the value of the $uuid field to
                            // the value found in the client state, because the browser sometimes restores the value of
                            // hidden fields in an erratic way, for example from the value the hidden field had from
                            // the same URL loaded in another tab (e.g. Chrome, Firefox).
                            ORBEON.xforms.Globals.formUUID[formID].value = ORBEON.xforms.Document.getFromClientState(formID, "uuid");
                        }
                    } else if (element.name.indexOf("$repeat-tree") != -1) {
                        xformsRepeatTree = element;
                    } else if (element.name.indexOf("$repeat-indexes") != -1) {
                        xformsRepeatIndices = element;
                        // This is the last input field we are interested in
                        break;
                    }
                }

                ORBEON.xforms.Globals.processRepeatHierarchy(xformsRepeatTree.value);

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
                        var event = new ORBEON.xforms.server.AjaxServer.Event(formElement, null, null, "xxforms-all-events-required");
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
                                    if (! _.isUndefined(keyListener.modifier)) {
                                        additionalAttributes.push("modifiers");
                                        additionalAttributes.push(keyListener.modifier);
                                    }
                                    var event = new ORBEON.xforms.server.AjaxServer.Event(keyListener.form, targetId, null, "keypress",
                                        null, null, null, null, null, additionalAttributes);
                                    ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
                                }
                            });

                            // Register listener on dialog or enable
                            if (keyListener.isDialogListener) {
                                var yuiDialog = ORBEON.xforms.Globals.dialogs[keyListener.observer];
                                var dialogKeyListeners = yuiDialog.cfg.getProperty("keylisteners");
                                if (_.isUndefined(dialogKeyListeners)) dialogKeyListeners = [];
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
                            var discardable = ! _.isUndefined(serverEvent["discardable"]) && serverEvent["discardable"];
                            ORBEON.xforms.server.AjaxServer.createDelayedServerEvent(serverEvent["event"], serverEvent["delay"],
                                serverEvent["show-progress"], serverEvent["progress-message"], discardable, formElement.id);
                        }
                    }
                }
            }
        }

        // Special registration for focus, blur, and change events
        $(document).on('focusin', ORBEON.xforms.Events.focus);
        $(document).on('focusout', ORBEON.xforms.Events.blur);
        $(document).on('change', ORBEON.xforms.Events.change);

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
        try {
            if (window.parent.childWindowOrbeonReady) {
                window.parent.childWindowOrbeonReady();
                window.parent.childWindowOrbeonReady = null;
            }
        } catch (e) {
            // Silently ignore if we can't access parent window
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
    insertedElementEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
    insertedElement: function(element) {
        // TODO: Also need destructors for controls
        ORBEON.xforms.Init.insertedElementEvent.fire({element: element});
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
        var xformsServerURL       = null;
        var xformsServerUploadURL = null;
        var calendarImageURL      = null;

        if (!(window.orbeonInitData === undefined)) {
            // NOTE: We switched back and forth between trusting the client or the server on this. Starting 2010-08-27
            // the server provides the info. Starting 2011-10-05 we revert to using the server values instead of client
            // detection, as that works in portals. The concern with using the server values was proxying. But should
            // proxying be able to change the path itself? If so, wouldn't other things break anyway? So for now
            // server values it is.
            var formInitData = window.orbeonInitData[formID];
            if (formInitData && formInitData["paths"]) {
                xformsServerURL       = formInitData["paths"]["xforms-server"];
                xformsServerUploadURL = formInitData["paths"]["xforms-server-upload"];
                calendarImageURL      = formInitData["paths"]["calendar-image"];
            }
        }

        ORBEON.xforms.Globals.xformsServerURL[formID]       = xformsServerURL;
        ORBEON.xforms.Globals.xformsServerUploadURL[formID] = xformsServerUploadURL;
        ORBEON.xforms.Globals.calendarImageURL[formID]      = calendarImageURL;
    },

    _widetextArea: function(textarea) {
        ORBEON.xforms.Globals.autosizeTextareas.push(textarea);
        ORBEON.xforms.Controls.autosizeTextarea(textarea);
    },

    _range: function(range) {
        range.tabIndex = 0;
        ORBEON.xforms.ServerValueStore.set(range.id, 0);

        // In both cases the background <div> element must already have an id
        var backgroundDiv = YAHOO.util.Dom.getElementsByClassName("xforms-range-background", "div", range)[0];

        var thumbDiv = YAHOO.util.Dom.getElementsByClassName("xforms-range-thumb", "div", range)[0];
        thumbDiv.id = ORBEON.util.Utils.appendToEffectiveId(range.id, XF_LHHAI_SEPARATOR + "thumb");

        var slider = YAHOO.widget.Slider.getHorizSlider(backgroundDiv.id, thumbDiv.id, 0, 200);
        slider.subscribe("change", ORBEON.xforms.Events.sliderValueChange);
        ORBEON.xforms.Globals.sliderYui[range.id] = slider;
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
                close: hasClose,
                visible: false,
                draggable: false,
                fixedcenter: false,
                constraintoviewport: true,
                underlay: "none",
                usearia: ORBEON.util.Properties.useARIA.get(),
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
                usearia: ORBEON.util.Properties.useARIA.get(),
                role: "" // See bug 315634 http://goo.gl/54vzd
            });
        }
        yuiDialog.showEvent.subscribe(ORBEON.xforms.Events.dialogShow, dialog.id);
        // Register listener for when the dialog is closed by a click on the "x"
        yuiDialog.beforeHideEvent.subscribe(ORBEON.xforms.Events.dialogClose, dialog.id);

        // This is for JAWS to read the content of the dialog (otherwise it just reads the button)
        var dialogDiv = YAHOO.util.Dom.getElementsByClassName("xforms-dialog", "div", yuiDialog.element)[0];
        dialogDiv.setAttribute("aria-live", "polite");

        // If the dialog has a close "x" in the dialog toolbar, register a listener on the escape key that does the same as clicking on the "x"
        if (hasClose) {
            var escapeListener = new YAHOO.util.KeyListener(document, { keys:27 }, { fn: yuiDialog.hide, scope: yuiDialog, correctScope: true } );
            yuiDialog.cfg.queueProperty("keylisteners", escapeListener);
        }

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
        var repeatSeparatorPosition = element.id.indexOf(XF_REPEAT_SEPARATOR);
        if (repeatSeparatorPosition != -1) {
            var repeatIndexes =  element.id.substring(repeatSeparatorPosition + 1).split(XF_REPEAT_INDEX_SEPARATOR);
            repeatIndexes[repeatDepth] = newIndex;
            var newID = element.id.substring(0, repeatSeparatorPosition) + XF_REPEAT_SEPARATOR + repeatIndexes.join(XF_REPEAT_INDEX_SEPARATOR);
            element.id = newID;

        }
        // Do the same with all the children
        YAHOO.util.Dom.batch(YAHOO.util.Dom.getChildren(element), function(childElement) {
            this._renumberIDsWorker(childElement, repeatDepth, newIndex);
        }, this, true);
    },

    /**
     * Renumber the IDs for a given repeat ID, for all the elements between the begin and end marker for that repeat
     * @param repeatID      E.g. repeat-begin-todo⊙1 for the repeat on to-dos in the first to-do list.
     */
    _renumberIDs: function(repeatID) {

        // Figure at what depth this repeat is
        var repeatDepth = 0;
        var currentRepeat = repeatID;
        var repeatSeparatorPosition = currentRepeat.indexOf(XF_REPEAT_SEPARATOR);
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
            var event = new ORBEON.xforms.server.AjaxServer.Event(form, this.sourceControlID, null, "xxforms-dnd", null, null, null, null, null,
                    ["dnd-start", this._startPosition, "dnd-end", endPosition]);
            this._renumberIDs(this.sourceControlID);
            ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        }
    }

});

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
function xformsValueChanged(target) {
    var newValue = ORBEON.xforms.Controls.getCurrentValue(target);
    var valueChanged = newValue != target.previousValue;
    // We don't send value change events for the XForms upload control
    var isUploadControl = YAHOO.util.Dom.hasClass(target, "xforms-upload");
    if (valueChanged && !isUploadControl) {
        target.previousValue = newValue;
        var incremental = YAHOO.util.Dom.hasClass(target, "xforms-incremental");
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, target.id, newValue, "xxforms-value");
        ORBEON.xforms.server.AjaxServer.fireEvents([event], incremental);
    }
    return valueChanged;
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
