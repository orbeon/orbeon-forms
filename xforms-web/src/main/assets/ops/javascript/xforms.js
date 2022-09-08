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

var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;

(function() {

    var $ = ORBEON.jQuery;

    var YD = YAHOO.util.Dom;

    /**
     * Functions we add to the awesome Underscore.js
     *
     * 2020-10-05: Probably no longer relevant!
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

    this.ORBEON.onJavaScriptLoaded = new YAHOO.util.CustomEvent("javascript-loaded");

    this.ORBEON.util = {

        /**
         *  Utilities to deal with the DOM that supplement what is provided by YAHOO.util.Dom.
         */
        Dom: {

            ELEMENT_TYPE: 1,

            isElement: function(node) {
                return node.nodeType == this.ELEMENT_TYPE;
            },

            getElementsByName: function(element, localName, namespace) {
                return element.getElementsByTagName(namespace == null ? localName : namespace + ":" + localName);
            },

            /**
             * Return null when the attribute is not there.
             */
            getAttribute: function(element, name) {

                // https://developer.mozilla.org/en-US/docs/Web/API/Element/getAttribute#Notes
                //
                // "Essentially all web browsers (Firefox, Internet Explorer, recent versions of Opera, Safari, Konqueror,
                // and iCab, as a non-exhaustive list) return null when the specified attribute does not exist on the specified
                // element; this is what the current DOM specification draft specifies. The old DOM 3 Core specification, on
                // the other hand, says that the correct return value in this case is actually the empty string, and some DOM
                // implementations implement this behavior. The implementation of getAttribute() in XUL (Gecko) actually
                // follows the DOM 3 Core specification and returns an empty string. Consequently, you should use
                // element.hasAttribute() to check for an attribute's existence prior to calling getAttribute() if it is
                // possible that the requested attribute does not exist on the specified element."

                if (element.hasAttribute(name)) {
                    return element.getAttribute(name);
                } else {
                    return null;
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

                        if (! nameChangeSuccessful) {
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
            focus: function(element) { // 2020-04-23: 2 usages.
                try { element.focus(); }
                catch (e) { /* NOP */ }
            },

            blur: function(element) { // 2020-04-23: 2 usages.
                try { element.blur(); }
                catch (e) { /* NOP */ }
            },

            /**
             * Use W3C DOM API to get the content of an element.
             */
            getStringValue: function(element) {
                return $(element).text();
            },

            /**
             * Use W3C DOM API to set the content of an element.
             */
            setStringValue: function(element, text) {
                $(element).text(text);
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
         * Utility methods that don't in any other category
         */
        Utils: {
            logMessage: function(message) {
                if (typeof console != "undefined") {
                    console.log(message); // Normal use; do not remove
                }
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

            /**
             * For example: appendToEffectiveId("foo⊙1", "bar") returns "foobar⊙1"
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
                    return [];
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

            getClassForRepeatId: function(formID, repeatId) {
                var depth = 1;
                var currentRepeatId = repeatId;
                while (true) {
                    currentRepeatId = ORBEON.xforms.Page.getForm(formID).repeatTreeChildToParent[currentRepeatId];
                    if (currentRepeatId == null) break;
                    depth = (depth == 4) ? 1 : depth + 1;
                }
                return "xforms-repeat-selected-item-" + depth;
            },

            /**
             * In a string `stack`, finds all occurrences of `needle`, replaces them by `replacement`,
             * applies `builder` to the text between the needles, and returns the resulting sequence.
             *
             * @param  stack        : String
             * @param  needle       : String
             * @param  replacements : [T]
             * @param  builder      : String -> T
             * @return                [T]
             */
            replaceInText: function(stack, needle, replacements, builder) {
                var parts = stack.split(needle);
                var firstPart = _.first(parts);
                var replaced = _.flatten(_.map(_.rest(parts), function (part) {
                    return (part === "") ?
                            replacements :
                            _.flatten([replacements, [builder(part)]]);
                }), true);
                return _.flatten([[builder(firstPart)], replaced], true);
            },


            /**
             * Replaces in a tree (DOM) a placeholder (needle) by some other content (a string or sequence of nodes),
             * this in both text nodes and attribute values.
             *
             * @param  element      : Element
             * @param  needle       : String
             * @param  replacements : String | [Node]
             * @param  isHTML       : Boolean
             */
            replaceInDOM: function(element, needle, replacements, isHTML) {

                var createTextNode = _.bind(document.createTextNode, document);
                var replacementNodes =
                    isHTML ?
                    (replacements == null ? [] : replacements) :
                    [createTextNode(replacements)];
                var replaceInText  = ORBEON.util.Utils.replaceInText;

                function worker(node) {
                    switch (node.nodeType) {

                        case ELEMENT_TYPE:

                            // Do replacements in attributes if we're doing a text replacement
                            if (! isHTML) {
                                _.each(node.attributes, function (attribute) {
                                    var newValue = replaceInText(attribute.value, needle, replacements, _.identity).join("");
                                    if (newValue != attribute.value)
                                        $(node).attr(attribute.name, newValue);
                                });
                            }
                            // Recurse on children
                            _.each(node.childNodes, worker);

                            break;

                        case TEXT_TYPE:

                            var newNodes = replaceInText(String(node.nodeValue),
                                    needle,
                                    replacementNodes,
                                    createTextNode);
                            var changed = newNodes.length > 1 ||
                                    newNodes[0].nodeType != TEXT_TYPE ||
                                    newNodes[0].nodeValue != node.nodeValue;

                            if (changed) {
                                // Clone, as if multiple replacements occurred, the sequence
                                // will have multiple copies of the same object
                                $(newNodes).clone().insertBefore(node);
                                $(node).detach();
                            }
                            break;
                    }
                }

                worker(element);
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
            findRepeatDelimiter: function(formID, repeatId, index) {

                // Find id of repeat begin for the current repeatId
                var parentRepeatIndexes = "";
                {
                    var currentId = repeatId;
                    var form = ORBEON.xforms.Page.getForm(formID);
                    var repeatTreeChildToParent = form.repeatTreeChildToParent;
                    var repeatIndexes           = form.repeatIndexes;
                    while (true) {
                        var parent = repeatTreeChildToParent[currentId];
                        if (parent == null) break;
                        var grandParent = repeatTreeChildToParent[parent];
                        parentRepeatIndexes =
                                (grandParent == null ? XF_REPEAT_SEPARATOR : XF_REPEAT_INDEX_SEPARATOR)
                                + repeatIndexes[parent]
                                + parentRepeatIndexes;
                        currentId = parent;
                    }
                }

                var beginElementId = "repeat-begin-" + repeatId + parentRepeatIndexes;
                var beginElement = document.getElementById(beginElementId);
                if (! beginElement) return null;
                var cursor = beginElement;
                var cursorPosition = 0;
                while (true) {
                    while (cursor.nodeType != ELEMENT_TYPE || ! $(cursor).is('.xforms-repeat-delimiter')) {
                        cursor = cursor.nextSibling;
                        if (! cursor) return null;
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
            },

            isIOS: function() {
                return $(document.body).hasClass("xforms-ios");
            },

            getZoomLevel: function() {
                return document.documentElement.clientWidth / window.innerWidth;
            },

            resetIOSZoom: function() {
                var viewPortMeta = document.querySelector('meta[name="viewport"]');
                if (viewPortMeta) {
                    var contentAttribute = viewPortMeta.getAttribute('content');
                    if (contentAttribute) {
                        var parts = contentAttribute.split(/\s*[,;]\s*/);

                        var pairs =
                            _.map(parts, function(part) {
                                return part.split(/\s*=\s*/);
                            });

                        var filteredWithoutMaximumScale =
                            _.filter(pairs, function(pair) {
                                return pair.length == 2 && pair[0] != "maximum-scale";
                            });

                        var newParametersWithoutMaximumScale =
                            _.map(filteredWithoutMaximumScale, function(pair) {
                                return pair.join('=');
                            });

                        var newParametersWithMaximumScale =
                            newParametersWithoutMaximumScale.slice(0).concat('maximum-scale=1.0');

                        viewPortMeta.setAttribute('content', newParametersWithMaximumScale.join(','));
                        viewPortMeta.setAttribute('content', newParametersWithoutMaximumScale.join(','));
                    }
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
                    if (ORBEON.xforms.AjaxClient.hasEventsToProcess()) {
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
                var element = ORBEON.util.Dom.get(id);
                var button = element.tagName.toLowerCase() == "button" ? element : ORBEON.util.Dom.getElementByTagName(element, "button");
                button.click();
            }
        }
    };
})();

(function() {

    var $ = ORBEON.jQuery;

    // Define packages
    ORBEON.xforms = ORBEON.xforms || {};

    ORBEON.xforms.Controls = {

        // Returns MIP for a given control
        isRelevant: function (control) {
            return ! $(control).is('.xforms-disabled');
        },
        isReadonly: function (control) {
            return $(control).is('.xforms-readonly');
        },
        isRequired: function (control) {
            return $(control).is('.xforms-required');
        },
        isValid: function (control) {
            return ! $(control).is('.xforms-invalid');
        },

        getForm: function (control) {
            // If the control is not an HTML form control look for an ancestor which is a form
            if (typeof control.form == "undefined") {
                return $(control).closest('form')[0];
            } else {
                // We have directly a form control
                return control.form;
            }
        },

        getCurrentValue: function (control) {
            var event = {control: control};
            var jControl = $(control);

            if (! _.isUndefined(event.result)) {
                return event.result;
            } else if ((jControl.is('.xforms-input') && ! jControl.is('.xforms-type-boolean, .xforms-static'))
                    || jControl.is('.xforms-secret')) {
                // Simple input
                var input = control.tagName.toLowerCase() == "input" ? control : control.getElementsByTagName("input")[0];
                return input.value;
            } else if (jControl.is('.xforms-select-appearance-full, .xforms-select1-appearance-full, .xforms-input.xforms-type-boolean')) {
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
                if (spanValue == "" && jControl.is('.xforms-input') && jControl.is('.xforms-type-boolean'))
                    spanValue = "false";
                return spanValue;
            } else if (jControl.is('.xforms-select-appearance-compact, .xforms-select1-appearance-minimal, .xforms-select1-appearance-compact, .xforms-input-appearance-minimal, .xforms-input-appearance-compact')) {
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
            } else if (jControl.is('.xforms-textarea')) {
                // Text area
                var textarea = control.tagName.toLowerCase() == "textarea" ? control : control.getElementsByTagName("textarea")[0];
                return textarea.value
            } else if (jControl.is('.xforms-output, .xforms-input.xforms-static')) {
                // Output and static input
                var output = jControl.children(".xforms-output-output, .xforms-field").first();
                if (output.length > 0) {
                    if (jControl.is(".xforms-mediatype-image")) {
                        return output[0].src;
                    } else if (jControl.is(".xforms-output-appearance-xxforms-download")) {
                        return null;
                    } else if (jControl.is(".xforms-mediatype-text-html")) {
                        return output[0].innerHTML;// NOTE: Used to be control.innerHTML, which seems wrong.
                    } else {
                        return ORBEON.util.Dom.getStringValue(output[0]);
                    }
                }
            } else if (jControl.is('.xforms-range')) {
                var value = ORBEON.xforms.Globals.sliderYui[control.id].previousVal / 200;
                return value.toString();
            } else if (ORBEON.xforms.XBL.isComponent(control)) {
                var instance = ORBEON.xforms.XBL.instanceForControl(control);
                if (_.isObject(instance) && _.isFunction(instance.xformsGetValue))
                    return instance.xformsGetValue();
            }
        },

        /**
         * Updates the value of a control in the UI.
         *
         * @param control           HTML element for the control we want to update
         * @param newControlValue   New value
         */
        beforeValueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        valueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        afterValueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),

        setCurrentValue: function (control, newControlValue, force) {
            var customEvent = {control: control, newValue: newControlValue};
            ORBEON.xforms.Controls.beforeValueChange.fire(customEvent);
            ORBEON.xforms.Controls.valueChange.fire(customEvent);

            var jControl         = $(control);
            var isStaticReadonly = jControl.is('.xforms-static');

            // Can be set below by an XBL component's `xformsUpdateValue()` result
            var result = undefined;

            if (jControl.is('.xforms-output-appearance-xxforms-download')) {
                // XForms output with xxf:download appearance
                var anchor = ORBEON.util.Dom.getElementsByName(control, "a")[0];
                if (newControlValue == "") {
                    anchor.setAttribute("href", "#");
                    YAHOO.util.Dom.addClass(anchor, "xforms-readonly");
                } else {
                    anchor.setAttribute("href", newControlValue);
                    YAHOO.util.Dom.removeClass(anchor, "xforms-readonly");
                }
            } else if (isStaticReadonly && jControl.is('.xforms-textarea')) {
                // textarea in "static readonly" mode
                jControl.children('pre').first().text(newControlValue);
            } else if (isStaticReadonly && jControl.is('.xforms-select1-appearance-full')) {
                // Radio buttons in "static readonly" mode
                var items = jControl.find('.xforms-selected, .xforms-deselected');

                _.each(items, function(item) {
                    var jItem    = $(item);
                    var selected = jItem.find('.radio > span').text() == newControlValue;

                    jItem.toggleClass('xforms-selected',     selected);
                    jItem.toggleClass('xforms-deselected', ! selected);
                });
            } else if (jControl.is('.xforms-label, .xforms-hint, .xforms-help')) {
                // External LHH
                if (jControl.is(".xforms-mediatype-text-html")) {
                    jControl[0].innerHTML = newControlValue;
                } else {
                    ORBEON.util.Dom.setStringValue(jControl[0], newControlValue);
                }
            } else if (jControl.is('.xforms-output') || isStaticReadonly) {
                // XForms output or other field in "static readonly" mode
                var output = jControl.children(".xforms-output-output, .xforms-field").first();
                if (output.length > 0) {
                    if (jControl.is(".xforms-mediatype-image")) {
                        output[0].src = newControlValue;
                    } else if (jControl.is(".xforms-mediatype-text-html")) {
                        output[0].innerHTML = newControlValue;
                    } else {
                        ORBEON.util.Dom.setStringValue(output[0], newControlValue);
                    }
                }
            } else if ((_.isUndefined(force) || force == false) && ORBEON.xforms.AjaxFieldChangeTracker.hasChangedIdsRequest(control.id)) {
                // User has modified the value of this control since we sent our request so don't try to update it
                // 2017-03-29: Added `force` attribute to handle https://github.com/orbeon/orbeon-forms/issues/3130 as we
                // weren't sure we wanted to fully disable the test on `changedIdsRequest`.
            } else if (jControl.is('.xforms-trigger, .xforms-submit, .xforms-upload')) {
                // No value
            } else if ((jControl.is('.xforms-input') && ! jControl.is('.xforms-type-boolean')) || jControl.is('.xforms-secret')) {
                // Regular XForms input (not boolean, date, time or dateTime) or secret
                var input = control.tagName.toLowerCase() == "input" ? control : control.getElementsByTagName("input")[0];
                if (control.value != newControlValue) {
                    control.previousValue = newControlValue;
                    control.value = newControlValue;
                }
                if (input.value != newControlValue)
                    input.value = newControlValue;
            } else if (jControl.is('.xforms-select-appearance-full, .xforms-select1-appearance-full, .xforms-input.xforms-type-boolean')) {
                // Handle checkboxes and radio buttons
                var selectedValues = jControl.is('.xforms-select-appearance-full')
                        ? newControlValue.split(" ") : new Array(newControlValue);
                var checkboxInputs = control.getElementsByTagName("input");
                for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                    var checkboxInput = checkboxInputs[checkboxInputIndex];
                    checkboxInput.checked = _.contains(selectedValues, checkboxInput.value);
                }

                // Update classes on control
                ORBEON.xforms.XFormsUi.setRadioCheckboxClasses(control);
            } else if (jControl.is('.xforms-select-appearance-compact, .xforms-select1-appearance-compact, .xforms-select1-appearance-minimal, .xforms-input-appearance-compact, .xforms-input-appearance-minimal')) {
                // Handle lists and comboboxes
                var selectedValues = jControl.is('.xforms-select-appearance-compact') ? newControlValue.split(" ") : new Array(newControlValue);
                var select = control.getElementsByTagName("select")[0];
                var options = select.options;
                if (options != null) {
                    for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                        var option = options[optionIndex];
                        try {
                            option.selected = _.contains(selectedValues, option.value);
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
            } else if (jControl.is('.xforms-textarea')) {
                // Text area
                var textarea = control.getElementsByTagName("textarea")[0];
                textarea.value = newControlValue;
            } else if (ORBEON.xforms.XBL.isComponent(control)) {
                var instance = ORBEON.xforms.XBL.instanceForControl(control);
                if (_.isObject(instance) && _.isFunction(instance.xformsUpdateValue)) {
                    // Return `undefined` or a JavaScript or jQuery `Promise` once the value is actually set
                    result = instance.xformsUpdateValue(newControlValue);
                }
            } else if (typeof(control.value) == "string") {
                // Textarea, password
                control.value = newControlValue;
                control.previousValue = newControlValue;
            }

            ORBEON.xforms.Controls.afterValueChange.fire(customEvent);

            return result;
        },

        // Mapping between className (parameter of this method and added after "xforms-") and id of elements
        // in the case where they are outside of the control element.
        _classNameToId: {
            "label": XF_LHHAI_SEPARATOR + "l",
            "hint": XF_LHHAI_SEPARATOR + "t",
            "help": XF_LHHAI_SEPARATOR + "p",
            "alert": XF_LHHAI_SEPARATOR + "a",
            "control": XF_LHHAI_SEPARATOR + "c"
        },

        /**
         * Look for an HTML element corresponding to an XForms LHHA element.
         * In the HTML generated by the server there is 1 element for each one and 2 for the help.
         */
        getControlLHHA: function (control, lhhaType) {

            // Search by id first
            // See https://github.com/orbeon/orbeon-forms/issues/793
            var lhhaElementId = ORBEON.util.Utils.appendToEffectiveId(control.id, ORBEON.xforms.Controls._classNameToId[lhhaType]);
            var byId = document.getElementById(lhhaElementId);
            if (byId != null)
                return byId;

            // Search just under the control element, excluding elements with an LHHA id, as they might be for a nested
            // control if we are a grouping control. Test on XF_LHHAI_SEPARATOR as e.g. portals might add their own id.
            // See: https://github.com/orbeon/orbeon-forms/issues/1206
            var lhhaElements = $(control).children('.xforms-' + lhhaType).filter(function () {
                return this.id.indexOf(XF_LHHAI_SEPARATOR) == -1
            });
            if (lhhaElements.length > 0)
                return lhhaElements.get(0);

            // If the control an LHHA, which happens for LHHA outside of the control, when the control is in a repeat
            if (control.classList.contains("xforms-" + lhhaType))
                return control;

            // Nothing found
            return null;
        },

        /**
         * Return the control associated with a given LHHA element and its expected type.
         */
        getControlForLHHA: function (element, lhhaType) {
            var suffix = ORBEON.xforms.Controls._classNameToId[lhhaType];
            // NOTE: could probably do without lhhaType parameter
            if (element.classList.contains("xforms-control"))
                // LHHA element is itself a control, which happens for stand-alone LHHA outside of a repeat
                return element
            else if (element.id.indexOf(suffix) != -1)
                return document.getElementById(element.id.replace(new RegExp(ORBEON.util.Utils.escapeRegex(ORBEON.xforms.Controls._classNameToId[lhhaType]), "g"), ''))
            else
                return element.parentNode;
        },

        _setMessage: function (control, lhhaType, message) {
            var lhhaElement = ORBEON.xforms.Controls.getControlLHHA(control, lhhaType);
            if (lhhaElement != null) {
                lhhaElement.innerHTML = message;

                // https://github.com/orbeon/orbeon-forms/issues/4062
                if (lhhaType == "help")
                    $(lhhaElement).toggleClass('xforms-disabled', message.trim() == "");
            }
            ORBEON.xforms.Controls.lhhaChangeEvent.fire({control: control, type: lhhaType, message: message});
        },

        lhhaChangeEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),

        getLabelMessage: function (control) {
            if ($(control).is('.xforms-trigger, .xforms-submit')) {
                // Element is "label" and "control" at the same time so use "control"
                var labelElement = ORBEON.xforms.Controls.getControlLHHA(control, "control");
                return labelElement.innerHTML;
            } else if ($(control).is('.xforms-dialog')) {
                // Dialog
                var labelDiv = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                return labelDiv.innerHTML;
            } else if ($(control).is('.xforms-group-appearance-xxforms-fieldset')) {
                // Group with fieldset/legend
                var legend = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                if (legend != null)
                    return legend.innerHTML;
            } else {
                var labelElement = ORBEON.xforms.Controls.getControlLHHA(control, "label");
                return labelElement == null ? "" : labelElement.innerHTML;
            }
        },

        setLabelMessage: function (control, message) {
            if ($(control).is('.xforms-trigger, .xforms-submit')) {
                // Element is "label" and "control" at the same time so use "control"
                ORBEON.xforms.Controls._setMessage(control, "control", message);
            } else if ($(control).is('.xforms-dialog')) {
                // Dialog
                var labelDiv = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                labelDiv.innerHTML = message;
            } else if ($(control).is('.xforms-group-appearance-xxforms-fieldset')) {
                // Group with fieldset/legend
                var legend = ORBEON.util.Dom.getChildElementByIndex(control, 0);
                if (legend != null)
                    legend.innerHTML = message;
            } else if ($(control).is('.xforms-output-appearance-xxforms-download')) {
                // Download link
                var anchor = YAHOO.util.Dom.getChildren(control)[0];
                anchor.innerHTML = message;
            } else {
                ORBEON.xforms.Controls._setMessage(control, "label", message);
            }
        },

        getHelpMessage: function (control) {
            var helpElement = ORBEON.xforms.Controls.getControlLHHA(control, "help");
            var helpMessage =
                helpElement == null ? "" :
                // When the help is a control (`xf:help` outside of its control), the content is markup, not escaped HTML
                helpElement.classList.contains("xforms-control") ? helpElement.innerHTML :
                ORBEON.util.Dom.getStringValue(helpElement);
            return helpMessage;
        },

        setHelpMessage: function (control, message) {
            // We escape the value because the help element is a little special, containing escaped HTML
            message = ORBEON.common.MarkupUtils.escapeXmlMinimal(message);
            ORBEON.xforms.Controls._setMessage(control, "help", message); // uses `innerHTML`
            ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.helpTooltipForControl);
        },

        setConstraintLevel: function (control, newLevel) {

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

        setDisabledOnFormElement: function (element, disabled) {
            if (disabled) {
                element.setAttribute("disabled", "disabled");
            } else {
                element.removeAttribute("disabled");
            }
        },

        setReadonlyOnFormElement: function (element, disabled) {
            if (disabled) {
                element.setAttribute("readonly", "readonly");
            } else {
                element.removeAttribute("readonly");
            }
        },

        setRelevant: function (control, isRelevant) {
            var FN = ORBEON.xforms.FlatNesting;

            var jControl = $(control);

            if (jControl.is('.xforms-group-begin-end')) {
                // Case of group delimiters
                FN.setRelevant(control, isRelevant);
            } else {
                var elementsToUpdate = [
                    control,
                    ORBEON.xforms.Controls.getControlLHHA(control, "label"),
                    ORBEON.xforms.Controls.getControlLHHA(control, "help"),
                    ORBEON.xforms.Controls.getControlLHHA(control, "hint"),
                    ORBEON.xforms.Controls.getControlLHHA(control, "alert")
                ];
                // Also show help if message is not empty
                if (! isRelevant || (isRelevant && ORBEON.xforms.Controls.getHelpMessage(control) != "")) {
                    elementsToUpdate.push(ORBEON.xforms.Controls.getControlLHHA(control, "help"));
                }
                // Also show hint if message is not empty
                if (! isRelevant || (isRelevant && ORBEON.xforms.Controls.getHintMessage(control) != ""))
                    elementsToUpdate.push(ORBEON.xforms.Controls.getControlLHHA(control, "hint"));

                // Go through elements to update, and update classes
                _.each(elementsToUpdate, function(element) {
                    if (element != null) {
                        if (isRelevant) {
                            YAHOO.util.Dom.removeClass(element, "xforms-disabled");
                        } else {
                            YAHOO.util.Dom.addClass(element, "xforms-disabled");
                        }
                    }
                });
            }
        },

        setRepeatIterationRelevance: function(formID, repeatID, iteration, relevant) {
            var OU = ORBEON.util.Utils;
            var FN = ORBEON.xforms.FlatNesting;

            var delimiter = OU.findRepeatDelimiter(formID, repeatID, iteration);
            FN.setRelevant(delimiter, relevant);
        },

        setReadonly: function (control, isReadonly) {

            var jControl = $(control);

            // Update class
            if (isReadonly) {
                YAHOO.util.Dom.addClass(control, "xforms-readonly");
            } else {
                YAHOO.util.Dom.removeClass(control, "xforms-readonly");
            }

            if (jControl.is('.xforms-group-begin-end')) {
                // Case of group delimiters
                // Readonlyness is no inherited by controls inside the group, so we are just updating the class on the begin-marker
                // to be consistent with the markup generated by the server.
                if (isReadonly) {
                    YAHOO.util.Dom.addClass(control, "xforms-readonly");
                } else {
                    YAHOO.util.Dom.removeClass(control, "xforms-readonly");
                }
            } else if (jControl.is('.xforms-input, .xforms-secret')) {
                // Input fields

                // Add/remove xforms-readonly on span
                if (isReadonly) YAHOO.util.Dom.addClass(control, "xforms-readonly");
                else YAHOO.util.Dom.removeClass(control, "xforms-readonly");

                // Update disabled on input fields
                var inputs = control.getElementsByTagName("input");
                for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
                    var input = inputs[inputIndex];
                    ORBEON.xforms.Controls.setReadonlyOnFormElement(input, isReadonly);
                }
                if (control.tagName.toLowerCase() == "input")
                    ORBEON.xforms.Controls.setReadonlyOnFormElement(control, isReadonly);
            } else if (jControl.is('.xforms-select1-appearance-full, .xforms-select-appearance-full')) {
                // Radio buttons, or checkboxes

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
            } else if (jControl.is('.xforms-select-appearance-compact, .xforms-select1-appearance-minimal, .xforms-select1-appearance-compact, .xforms-input-appearance-minimal, .xforms-input-appearance-compact')) {
                // Lists
                var select = control.getElementsByTagName("select")[0];
                ORBEON.xforms.Controls.setDisabledOnFormElement(select, isReadonly);
            } else if (jControl.is('.xforms-output, .xforms-group')) {
                // XForms output and group
                if (isReadonly) YAHOO.util.Dom.addClass(control, "xforms-readonly");
                else YAHOO.util.Dom.removeClass(control, "xforms-readonly");
            } else if (jControl.is('.xforms-upload')) {
                // Upload control
                ORBEON.xforms.Controls.setDisabledOnFormElement(
                        YAHOO.util.Dom.getElementsByClassName("xforms-upload-select", null, control)[0], isReadonly);
            } else if (jControl.is('.xforms-textarea')) {
                // Textarea
                var textarea = control.getElementsByTagName("textarea")[0];
                ORBEON.xforms.Controls.setReadonlyOnFormElement(textarea, isReadonly);
            } else if (jControl.is('.xforms-trigger')
                    || jControl.is('.xforms-submit' )) {
                // Button
                var button = ORBEON.util.Dom.getElementByTagName(control, "button");
                ORBEON.xforms.Controls.setDisabledOnFormElement(button, isReadonly);
            }
        },

        getAlertMessage: function (control) {
            var alertElement = ORBEON.xforms.Controls.getControlLHHA(control, "alert");
            return alertElement.innerHTML;
        },

        setAlertMessage: function (control, message) {
            ORBEON.xforms.Controls._setMessage(control, "alert", message);
            ORBEON.xforms.Controls._setTooltipMessage(control, message, ORBEON.xforms.Globals.alertTooltipForControl);
        },

        getHintMessage: function (control) {
            if ($(control).is('.xforms-trigger, .xforms-submit')) {
                var formElement = ORBEON.util.Dom.getElementByTagName(control, ["a", "button"]);
                return formElement.title;
            } else {
                // Element for hint
                var hintElement = ORBEON.xforms.Controls.getControlLHHA(control, "hint");
                return hintElement == null ? "" : hintElement.innerHTML;
            }
        },

        setHintMessage: function (control, message) {
            // Destroy existing tooltip if it was for a control which isn't anymore in the DOM
            var tooltips = ORBEON.xforms.Globals.hintTooltipForControl;
            if (tooltips[control.id] != null) {
                if (tooltips[control.id].cfg.getProperty('context')[0] != control)
                    tooltips[control.id] = null
            }

            if ($(control).is('.xforms-trigger, .xforms-submit')) {
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

        _setTooltipMessage: function (control, message, tooltipForControl) {
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

        _gotLoad: function() {
            return document.readyState == "complete";
        },

        _delayingUntilLoad: function (f) {
            var delaying = ! ORBEON.xforms.Controls._gotLoad();
            if (delaying) window.addEventListener("load", f);
            return delaying;
        },

        /**
         * Sets focus to the specified control. This is called by the JavaScript code
         * generated by the server, which we invoke on page load.
         */
        setFocus: function setFocus(controlId) {

            // Wait until `load` event to set focus, as dialog in control the control is present might not be visible until then
            // And on mobile, don't set the focus on page load, as a heuristic to avoid showing the soft keyboard on page load
            if (! ORBEON.xforms.Controls._gotLoad()) {
                if (! bowser.mobile)
                    ORBEON.xforms.Controls._delayingUntilLoad(_.partial(setFocus, controlId));
                return;
            }

            // Don't bother focusing if the control is already focused. This also prevents issues with maskFocusEvents,
            // whereby maskFocusEvents could be set to true below, but then not cleared back to false if no focus event
            // is actually dispatched.
            if (ORBEON.xforms.Globals.currentFocusControlId == controlId)
                return;

            var control = document.getElementById(controlId);

            // Keep track of the id of the last known control which has focus
            ORBEON.xforms.Globals.currentFocusControlId = controlId;
            ORBEON.xforms.Globals.currentFocusControlElement = control;
            ORBEON.xforms.Globals.maskFocusEvents = true;
            if ($(control).is('.xforms-select-appearance-full, .xforms-select1-appearance-full, .xforms-input.xforms-type-boolean')) {
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
            } else if (ORBEON.xforms.XBL.isFocusable(control)) {
                var instance = ORBEON.xforms.XBL.instanceForControl(control);
                if (_.isObject(instance)) {
                    if (_.isFunction(instance.xformsFocus))
                        instance.xformsFocus();
                    else if (_.isFunction(instance.setFocus))
                        instance.setFocus();
                }
            } else {

                // Generic code, trying to find:
                // P1. A HTML form field
                var htmlControl = $(control).find("input:visible, textarea:visible, select:visible, button:visible:not(.xforms-help), a:visible");
                // P2. A focusable element
                if (! htmlControl.is('*'))
                    htmlControl = $(control).find("[tabindex]:not([tabindex = '-1']):visible");

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

        removeFocus: function (controlId) {

            // If not control has the focus, there is nothing to do
            if (ORBEON.xforms.Globals.currentFocusControlId == null)
                return;

            var control = document.getElementById(controlId);

            if ($(control).is('.xforms-select-appearance-full, .xforms-select1-appearance-full, .xforms-input.xforms-type-boolean')) {
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
            } else if (ORBEON.xforms.XBL.isFocusable(control)) {
                var instance = ORBEON.xforms.XBL.instanceForControl(control);
                if (_.isObject(instance)) {
                    if (_.isFunction(instance.xformsBlur))
                        instance.xformsBlur();
                }
            } else {
                // Generic code to find focusable descendant-or-self HTML element and focus on it
                var htmlControlNames = ["input", "textarea", "select", "button", "a"];
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
        updateVisited: function (control, newVisited) {

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
        updateRequiredEmpty: function (control, emptyAttr) {
            var isRequired = $(control).hasClass("xforms-required");

            $(control).toggleClass("xforms-empty", isRequired && emptyAttr == "true");
            $(control).toggleClass("xforms-filled", isRequired && emptyAttr == "false");
        },

        showDialog: function showDialog(controlId, neighbor) {

            // Wait until all the CSS and images are loaded, as they can influence the positioning of the dialog
            if (ORBEON.xforms.Controls._delayingUntilLoad(_.partial(showDialog, controlId, neighbor)))
                return;

            var divElement = document.getElementById(controlId);
            var initializedDialog = function() { return ORBEON.xforms.Globals.dialogs[controlId]; }
            var yuiDialog = initializedDialog();

            // Initialize dialog now, if it hasn't been done already
            if (! _.isObject(yuiDialog)) {
                ORBEON.xforms.Init._dialog(divElement);
                yuiDialog = initializedDialog();
            }

            // Take out the focus from the current control. This is particularly important with non-modal dialogs
            // opened with a minimal trigger, otherwise we have a dotted line around the link after it opens.
            if (ORBEON.xforms.Globals.currentFocusControlId != null) {
                var focusedElement = document.getElementById(ORBEON.xforms.Globals.currentFocusControlId);
                if (focusedElement != null) focusedElement.blur();
            }

            // Render the dialog if needed
            if ($(divElement).is('.xforms-initially-hidden')) {
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
                // Center dialog in page after the whole Ajax request has been processed,
                // giving a chance to the content of the dialog to show itself
                ORBEON.xforms.AjaxClient.currentAjaxResponseProcessedOrImmediatelyP().then(
                    function() { yuiDialog.center(); }
                );
            } else {
                // Align dialog relative to neighbor
                yuiDialog.cfg.setProperty("context", [neighbor, "tl", "bl"]);
                yuiDialog.align();
            }
        },

        typeChangedEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        fullUpdateEvent:  new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),

        /**
         * Find the beginning of a case.
         */
        findCaseBegin: function (controlId) {
            var caseBeginId = "xforms-case-begin-" + controlId;
            return document.getElementById(caseBeginId);
        },

        /**
         * Toggle a single case.
         *
         * [1] We disable the open/close animation on IE10 and under. The animation works on IE11, but we've seen problems
         *     with earlier versions, so, since the animation is only cosmetic, we determine that it isn't worth debugging
         *     those issues and that we're better off just disabling the animation for those old versions of IE.
         *     YAHOO.env.ua.ie returns 0 for IE11, as YUI doesn't detect IE11 as being IE.
         */
        toggleCase: function (controlId, visible) {
            var caseBegin = ORBEON.xforms.Controls.findCaseBegin(controlId);
            var caseBeginParent = caseBegin.parentNode;
            var foundCaseBegin = false;
            for (var childIndex = 0; caseBeginParent.childNodes.length; childIndex++) {
                var cursor = caseBeginParent.childNodes[childIndex];
                if (! foundCaseBegin) {
                    if (cursor.id == caseBegin.id) foundCaseBegin = true;
                    else continue;
                }
                if (cursor.nodeType == ELEMENT_TYPE) {
                    // Change visibility by switching class
                    if (cursor.id == "xforms-case-end-" + controlId) break;
                    var doAnimate = cursor.id != "xforms-case-begin-" + controlId &&    // Don't animate case-begin/end
                            $(cursor).is('.xxforms-animate') &&                         // Only animate if class present
                            YAHOO.env.ua.ie == 0;                                       // Simply disable animation for IE<=10 [1]

                    var updateClasses = _.partial(function (el) {
                        if (visible) {
                            YAHOO.util.Dom.addClass(el, "xforms-case-selected");
                            YAHOO.util.Dom.removeClass(el, "xforms-case-deselected");
                        } else {
                            YAHOO.util.Dom.addClass(el, "xforms-case-deselected");
                            YAHOO.util.Dom.removeClass(el, "xforms-case-selected");
                        }
                    }, cursor);

                    if (doAnimate) {
                        if (visible) {
                            updateClasses();
                            $(cursor).animate({height: 'show'}, {duration: 200});
                        } else {
                            $(cursor).animate({height: 'hide'}, {duration: 200, complete: updateClasses});
                        }
                    } else {
                        updateClasses();
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

        isGroupBeginEnd: function (node) {
            return node.nodeType == ELEMENT_TYPE && $(node).is('.xforms-group-begin-end');
        },
        isGroupBegin: function (node) {
            return this.isGroupBeginEnd(node) && node.id.indexOf("group-begin-") == 0;
        },
        isGroupEnd: function (node) {
            return this.isGroupBeginEnd(node) && node.id.indexOf("group-end-") == 0;
        },
        isRepeatBeginEnd: function (node) {
            return node.nodeType == ELEMENT_TYPE && $(node).is('.xforms-repeat-begin-end');
        },
        isRepeatBegin: function (node) {
            return this.isRepeatBeginEnd(node) && node.id.indexOf("repeat-begin-") == 0;
        },
        isRepeatEnd: function (node) {
            return this.isRepeatBeginEnd(node) && node.id.indexOf("repeat-end-") == 0;
        },
        isRepeatDelimiter: function (node) {
            return node.nodeType == ELEMENT_TYPE && $(node).is('.xforms-repeat-delimiter');
        },
        isBegin: function (node) {
            return this.isGroupBegin(node) || this.isRepeatBegin(node);
        },
        isEnd: function (node) {
            return this.isGroupEnd(node) || this.isRepeatEnd(node);
        },

        /**
         * Start applying foldFunction to all the ancestors of startNode, and stops if foldFunction returns stopValue.
         *
         * @param startNode     Node we start with: group begin or repeat delimiter
         * @param startValue    Start value for folding
         * @param foldFunction  function(beginNode, value) -> value
         * @param stopValue     Stop folding if fold function returns this value
         */
        foldAncestors: function (startNode, startValue, foldFunction, stopValue) {
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
        foldDescendants: function (startNode, startValue, foldFunction, stopValue) {
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
        hasAncestor: function (startNode, conditionFunction) {
            var FN = ORBEON.xforms.FlatNesting;

            return FN.foldAncestors(startNode, false, function (value, node) {
                return conditionFunction(node);
            }, true);
        },

        setRelevant: function (node, isRelevant) {
            var FN = ORBEON.xforms.FlatNesting;
            var YD = YAHOO.util.Dom;
            var OD = ORBEON.util.Dom;
            var OC = ORBEON.xforms.Controls;

            // Update class on group begin or delimiter
            if (isRelevant) YAHOO.util.Dom.removeClass(node, "xforms-disabled");
            else YAHOO.util.Dom.addClass(node, "xforms-disabled");

            // If this group/iteration becomes relevant, but has a parent that is non-relevant, we should not
            // remove xforms-disabled otherwise it will incorrectly show, so our job stops here
            if (isRelevant && FN.hasAncestor(node, function (node) {
                        return $(node).is('.xforms-disabled');
                    })) return;

            FN.foldDescendants(node, null, function (node, value) {
                // Skip sub-tree if we are enabling and this sub-tree is disabled
                if (isRelevant && FN.isBegin(node) && YD.hasClass(node, "xforms-disabled")) return true;
                // Update disabled class on node
                if (isRelevant) {
                    YD.removeClass(node, "xforms-disabled");
                } else {
                    YD.addClass(node, "xforms-disabled");
                }
                return false;
            }, true);
        }
    };

    ORBEON.xforms.Events = {

        /**
         * Look for the first parent control which is an XForms control
         */
        _findParentXFormsControl: function (element) {
            while (true) {
                if (! element) {
                    // No more parent, stop search
                    return null;
                } else if (element.tagName != null
                        && element.tagName.toLowerCase() == "iframe") {
                    // This might be the iframe that corresponds to a dialog on IE6
                    for (var dialogId in ORBEON.xforms.Globals.dialogs) {
                        var dialog = ORBEON.xforms.Globals.dialogs[dialogId];
                        if (dialog.iframe == element)
                            return dialog.element;
                    }
                } else if (element.className != null) {
                    if ($(element).is('.xforms-control, .xbl-component, .xforms-dialog')) {
                        return element;
                    }
                }
                // Go to parent and continue search
                element = element.parentNode;
            }
        },

        _findAncestorFocusableControl: function (eventTarget) {
            var ancestorControl = ORBEON.xforms.Events._findParentXFormsControl(eventTarget);

            var sendFocus =
                    ancestorControl != null
                        // We don't run this for dialogs, as there is not much sense doing this AND this causes issues with
                        // FCKEditor embedded within dialogs with IE. In that case, the editor gets a blur, then the dialog, which
                        // prevents detection of value changes in focus() above.
                    && ! $(ancestorControl).is('.xforms-dialog')
                        // Don't send focus for XBL component that are not focusable
                    && ! $(ancestorControl).is('.xbl-component:not(.xbl-focusable)');

            return sendFocus ? ancestorControl : null;
        },

        focus: function (event) {

            if (ORBEON.xforms.XFormsUi.modalProgressPanelShown) {
                event.preventDefault();
                return;
            }

            var eventTarget = YAHOO.util.Event.getTarget(event);
            if (! ORBEON.xforms.Globals.maskFocusEvents) {
                // Control elements
                var newFocusControlElement = ORBEON.xforms.Events._findAncestorFocusableControl(eventTarget);
                var currentFocusControlElement = ORBEON.xforms.Globals.currentFocusControlId != null ? document.getElementById(ORBEON.xforms.Globals.currentFocusControlId) : null;

                if (newFocusControlElement != null) {
                    // Store initial value of control if we don't have a server value already, and if this is is not a list
                    // Initial value for lists is set up initialization, as when we receive the focus event the new value is already set.
                    if (ORBEON.xforms.ServerValueStore.get(newFocusControlElement.id) == null
                            && ! $(newFocusControlElement).is('.xforms-select-appearance-compact, .xforms-select1-appearance-compact')) {
                        var controlCurrentValue = ORBEON.xforms.Controls.getCurrentValue(newFocusControlElement);
                        ORBEON.xforms.ServerValueStore.set(newFocusControlElement.id, controlCurrentValue);
                    }
                }

                // The idea here is that we only register focus changes when focus moves between XForms controls. If focus
                // goes out to nothing, we don't handle it at this point but wait until focus comes back to a control.
                if (newFocusControlElement != null && currentFocusControlElement != newFocusControlElement) {

                    // Keep track of the id of the last known control which has focus
                    ORBEON.xforms.Globals.currentFocusControlId = newFocusControlElement.id;
                    ORBEON.xforms.Globals.currentFocusControlElement = newFocusControlElement;

                    // Fire events
                    ORBEON.xforms.AjaxClient.fireEvent(
                        new ORBEON.xforms.AjaxEvent(
                            {
                                "targetId"   : newFocusControlElement.id,
                                "eventName"  : "xforms-focus",
                                "incremental": true
                            }
                        )
                    );
                }

            } else {
                ORBEON.xforms.Globals.maskFocusEvents = false;
            }
        },

        blurEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        blur: function (event) {

            if (ORBEON.xforms.XFormsUi.modalProgressPanelShown) {
                event.preventDefault();
                return;
            }

            if (! ORBEON.xforms.Globals.maskFocusEvents) {
                var target = YAHOO.util.Event.getTarget(event);
                var control = ORBEON.xforms.Events._findAncestorFocusableControl(target);
                if (control != null) {
                    ORBEON.xforms.Events.blurEvent.fire({control: control, target: target});

                    ORBEON.xforms.Globals.currentFocusControlId = control.id;
                    ORBEON.xforms.Globals.currentFocusControlElement = control;

                    // Dispatch `xxforms-blur` event if we're not going to another XForms control (see issue #619)
                    var relatedTarget = event.relatedTarget || document.activeElement;
                    var relatedControl = ORBEON.xforms.Events._findAncestorFocusableControl(relatedTarget);
                    if (relatedControl == null) {
                        ORBEON.xforms.Globals.currentFocusControlId      = null;
                        ORBEON.xforms.Globals.currentFocusControlElement = null;
                        var event = new ORBEON.xforms.AjaxEvent(null, control.id, null, "xxforms-blur");
                        ORBEON.xforms.AjaxClient.fireEvent(event);
                    }
                }
            }
        },

        change: function (event) {
            var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (target != null) {
                if ($(target).is('.xbl-component.xbl-javascript-lifecycle')) {
                    // We might exclude *all* changes under `.xbl-component` but for now, to be conservative, we
                    // exclude those that support the JavaScript lifecycle.
                    // https://github.com/orbeon/orbeon-forms/issues/4169
                } else if ($(target).is('.xforms-upload')) {
                    // Dispatch change event to upload control
                    ORBEON.xforms.Page.getUploadControl(target).change();
                } else {

                    if ($(target).is('.xforms-select1-appearance-compact')) {
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
                    }

                    // Fire change event if the control has a value
                    var controlCurrentValue = ORBEON.xforms.Controls.getCurrentValue(target);
                    if (! _.isUndefined(controlCurrentValue)) {
                        var event = new ORBEON.xforms.AjaxEvent(null, target.id, controlCurrentValue, "xxforms-value");
                        ORBEON.xforms.AjaxClient.fireEvent(event);
                    }
                }
            }
        },

        // TODO: 2020-06-04: Only used by the IE 11 `preventDefault()` below.
        //   2022-06-14: Unclear, check again!
        keydownEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        keydown: function (event) {

            if (ORBEON.xforms.XFormsUi.modalProgressPanelShown) {
                event.preventDefault();
                return;
            }

            // Prevent default behavior when the esc key is pressed, which would otherwise reset all the form fields on IE,
            // up to IE11 included. See https://github.com/orbeon/orbeon-forms/issues/131
            if (event.keyCode == 27)
                event.preventDefault();

            var target = YAHOO.util.Event.getTarget(event);
            var control = ORBEON.xforms.Events._findParentXFormsControl(target);
            if (control != null) {
                ORBEON.xforms.Events.keydownEvent.fire({control: control, target: target});
            }
        },

        //TODO: MDN: "Since this event has been deprecated, you should look to use beforeinput or keydown instead."
        keypressEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        keypress: function (event) {

            if (ORBEON.xforms.XFormsUi.modalProgressPanelShown) {
                event.preventDefault();
                return;
            }

            var target = YAHOO.util.Event.getTarget(event);
            var control = ORBEON.xforms.Events._findParentXFormsControl(target);
            if (control != null) {
                ORBEON.xforms.Events.keypressEvent.fire({control: control, target: target, keyCode: event.keyCode});
                // Input field and auto-complete: trigger DOMActive when when enter is pressed
                var isXFormsInputField = ($(control).is('.xforms-input') && ! $(control).is('.xforms-type-boolean')) ||
                                          $(control).is('.xforms-secret');
                var isFocusInOnInput   = $(target).is("input");
                if (isXFormsInputField && isFocusInOnInput) {
                    if (event.keyCode == 10 || event.keyCode == 13) {
                        // Force a change event if the value has changed, creating a new "change point", which the
                        // browser will use to dispatch a `change` event in the future. Also see issue #1207.
                        $(target).blur().focus();
                        // Send a value change and DOM activate
                        var valueEvent    = new ORBEON.xforms.AjaxEvent(null, control.id, ORBEON.xforms.Controls.getCurrentValue(control), "xxforms-value");
                        var activateEvent = new ORBEON.xforms.AjaxEvent(null, control.id, null, "DOMActivate")

                        ORBEON.xforms.AjaxClient.fireEvent(valueEvent);
                        ORBEON.xforms.AjaxClient.fireEvent(activateEvent);

                        // This prevents Chrome/Firefox from dispatching a 'change' event on event, making them more
                        // like IE, which in this case is more compliant to the spec.
                        event.preventDefault();
                    }
                }
            }
        },

        input: function (event) {

            if (ORBEON.xforms.XFormsUi.modalProgressPanelShown) {
               event.preventDefault();
               return;
            }

            var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (target != null) {
                // Incremental control: treat keypress as a value change event
                if ($(target).is('.xforms-incremental')) {
                    ORBEON.xforms.AjaxClient.fireEvent(
                        new ORBEON.xforms.AjaxEvent(
                            {
                                "targetId"   : target.id,
                                "eventName"  : "xxforms-value",
                                "value"      : ORBEON.xforms.Controls.getCurrentValue(target),
                                "incremental": true
                            }
                        )
                    );
                }
            }
        },

        _showToolTip: function (tooltipForControl, control, target, toolTipSuffix, message, delay, event) {

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

        mouseover: function (event) {
            var target = ORBEON.xforms.Events._findParentXFormsControl(YAHOO.util.Event.getTarget(event));
            if (target != null) {

                // Hint tooltip
                if (! $(target).closest(".xforms-disable-hint-as-tooltip").is("*")) {
                    var message = ORBEON.xforms.Controls.getHintMessage(target);
                    if ($(target).is('.xforms-trigger, .xforms-submit')) {
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
                        if (! ($(control).is('.xforms-control, .xforms-group')))
                            control = YAHOO.util.Dom.getAncestorByClassName(control, "xforms-control");
                        if (control) {
                            var message = ORBEON.xforms.Controls.getAlertMessage(control);
                            ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.alertTooltipForControl, control, target, "-orbeon-alert-tooltip", message, 10, event);
                        }
                    }
                }

                // Help tooltip
                if (ORBEON.xforms.Page.getForm(ORBEON.xforms.Controls.getForm(target).id).helpTooltip() && $(target).is('.xforms-help')) {
                    // Get control
                    var control = ORBEON.xforms.Controls.getControlForLHHA(target, "help");
                    if (control) {
                        // The xf:input is a unique case where the 'for' points to the input field, not the element representing the control
                        if ($(control).is('.xforms-input-input'))
                            control = YAHOO.util.Dom.getAncestorByClassName(control, "xforms-control");
                        var message = ORBEON.xforms.Controls.getHelpMessage(control);
                        YAHOO.util.Dom.generateId(target);
                        ORBEON.xforms.Events._showToolTip(ORBEON.xforms.Globals.helpTooltipForControl, control, target, "-orbeon-help-tooltip", message, 0, event);
                    }
                }
            }
        },

        mouseout: function (event) {
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
        click: function (event) {

            if (ORBEON.xforms.XFormsUi.modalProgressPanelShown) {
                event.preventDefault();
                return;
            }

            // Stop processing if the mouse button that was clicked is not the left button
            // See: http://www.quirksmode.org/js/events_properties.html#button
            if (event.button != 0 && event.button != 1) return;
            var originalTarget = YAHOO.util.Event.getTarget(event);
            var controlTarget = ORBEON.xforms.Events._findParentXFormsControl(originalTarget);
            // Listeners might be interested in click events even if they don't target an XForms control
            ORBEON.xforms.Events.clickEvent.fire({target: originalTarget, control: controlTarget});
            if (YAHOO.lang.isObject(originalTarget) && YAHOO.lang.isBoolean(originalTarget.disabled) && originalTarget.disabled) {
                // IE calls the click event handler on clicks on disabled controls, which Firefox doesn't.
                // To make processing more similar on all browsers, we stop going further here if we go a click on a disabled control.
                return;
            }

            var handled = false;
            if (originalTarget != null && $(originalTarget).is('.xforms-help')) {
                // Help image
                // We test on `originalTarget` being on the help icon first, so in case the help icon is inside a
                // trigger, we don't "mistake" the click on the help for a click on the trigger, rendering the help
                // inaccessible through a click.
                if (ORBEON.xforms.Page.getForm(ORBEON.xforms.Controls.getForm(controlTarget).id).helpHandler()) {
                    // We are sending the xforms-help event to the server and the server will tell us what do to
                    var event = new ORBEON.xforms.AjaxEvent(null, controlTarget.id, null, "xforms-help");
                    ORBEON.xforms.AjaxClient.fireEvent(event);
                } else {
                    // If the servers tells us there are no event handlers for xforms-help in the page,
                    // we can avoid a round trip and show the help right away
                    ORBEON.xforms.Help.showHelp(controlTarget);
                }
                handled = true;
            } else if (controlTarget != null && ($(controlTarget).is('.xforms-trigger, .xforms-submit'))) {
                // Click on trigger
                event.preventDefault();
                if (! $(controlTarget).is('.xforms-readonly')) {
                    // If this is an anchor and we didn't get a chance to register the focus event,
                    // send the focus event here. This is useful for anchors (we don't listen on the
                    // focus event on those, and for buttons on Safari which does not dispatch the focus
                    // event for buttons.
                    ORBEON.xforms.Events.focus(event);
                    var event = new ORBEON.xforms.AjaxEvent(null, controlTarget.id, null, "DOMActivate");
                    ORBEON.xforms.AjaxClient.fireEvent(event);

                    if ($(controlTarget).is('.xforms-trigger-appearance-modal, .xforms-submit-appearance-modal')) {
                        ORBEON.xforms.XFormsUi.displayModalProgressPanel();
                    }
                    handled = true;
                }
            } else if (controlTarget != null && ! $(controlTarget).is('.xforms-static') &&
                    $(controlTarget).is('.xforms-select1-appearance-full, .xforms-select-appearance-full, .xforms-input.xforms-type-boolean')) {
                // Click on checkbox or radio button

                // Update classes right away to give user visual feedback
                ORBEON.xforms.XFormsUi.setRadioCheckboxClasses(controlTarget);
                ORBEON.xforms.XFormsUi.handleShiftSelection(event, controlTarget);
                var event = new ORBEON.xforms.AjaxEvent(null, controlTarget.id, ORBEON.xforms.Controls.getCurrentValue(controlTarget), "xxforms-value");

                ORBEON.xforms.AjaxClient.fireEvent(event);
                handled = true;
            } else if (controlTarget != null && $(controlTarget).is('.xforms-upload') && $(originalTarget).is('.xforms-upload-remove')) {
                // Click on remove icon in upload control
                var event = new ORBEON.xforms.AjaxEvent(null, controlTarget.id, "", "xxforms-value");
                ORBEON.xforms.AjaxClient.fireEvent(event);
                handled = true;
            }

            if (handled)
                return;

            // Click on something that is not an XForms element, but which might still be in an repeat iteration,
            // in which case we want to let the server know about where in the iteration the click was.

            var node = originalTarget;

            // Iterate on ancestors, stop when we don't find ancestors anymore or we arrive at the form element
            while (node != null && ! (ORBEON.util.Dom.isElement(node) && node.tagName.toLowerCase() == "form")) {

                // First check clickable group
                if ($(node).is('.xforms-activable')) {
                    var form = ORBEON.xforms.Controls.getForm(node);
                    var event = new ORBEON.xforms.AjaxEvent(form, node.id, null, "DOMActivate");
                    ORBEON.xforms.AjaxClient.fireEvent(event);
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
                            var event = new ORBEON.xforms.AjaxEvent(form, targetId, null, "xxforms-repeat-activate");
                            ORBEON.xforms.AjaxClient.fireEvent(event);
                            foundRepeatBegin = true;
                            break;
                        } else if ($(sibling).is('.xforms-repeat-delimiter')) {
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
        _resize: function () {
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
        scrollOrResize: function () {
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

        sliderValueChange: function (offset) {
            // Notify server that value changed
            var rangeControl = document.getElementById(this.id).parentNode;

            var value = offset / 200;
            var event = new ORBEON.xforms.AjaxEvent(null, rangeControl.id, String(value), "xxforms-value");
            ORBEON.xforms.AjaxClient.fireEvent(event);
        },

        /**
         * Event listener on dialogs called by YUI when the dialog is closed. If the dialog was closed by the user (not
         * because the server told use to close the dialog), then we want to notify the server that this happened.
         */
        dialogClose: function (type, args, me) {
            if (! ORBEON.xforms.Globals.maskDialogCloseEvents) {
                var dialogId = me;
                var dialog = document.getElementById(dialogId);
                var event = new ORBEON.xforms.AjaxEvent(null, dialog.id, null, "xxforms-dialog-close");
                ORBEON.xforms.AjaxClient.fireEvent(event);
            }
        },

        /**
         * Event listener on dialogs called by YUI when the dialog is shown.
         */
        dialogShow: function (type, args, me) {
            var dialogId = me;

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
         * Called for each minimal dialog when there is a click on the document.
         * We have one listener per dialog, which listens to those events all the time,
         * not just when the dialog is open.
         */
        dialogMinimalBodyClick: function (event, yuiDialog) {
            // If this dialog is visible
            if (yuiDialog.element.style.visibility != "hidden") {
                // Abort if one of the parents is drop-down dialog
                var current = YAHOO.util.Event.getTarget(event);
                var foundDropDownParent = false;
                while (current != null && current != document) {
                    if ($(current).is('.xforms-dialog-appearance-minimal')) {
                        foundDropDownParent = true;
                        break;
                    }
                    current = current.parentNode;
                }
                if (! foundDropDownParent) {
                    var event = new ORBEON.xforms.AjaxEvent(null, yuiDialog.id, null, "xxforms-dialog-close");
                    ORBEON.xforms.AjaxClient.fireEvent(event);
                }
            }
        },

        orbeonLoadedEvent           : new YAHOO.util.CustomEvent("orbeonLoaded", window, false, YAHOO.util.CustomEvent.LIST, true),
        errorEvent                  : new YAHOO.util.CustomEvent("errorEvent"),
        yuiCalendarCreated          : new YAHOO.util.CustomEvent("yuiCalendarCreated"),
        componentChangedLayoutEvent : new YAHOO.util.CustomEvent("componentChangedLayout")
    };

    (function () {

        ORBEON.xforms.FullUpdate = {

            /** @private @type {Object.<string, Array.<string>>} */                 _fullUpdateToComponents: {},
            /** @private @type {Object.<string, boolean>} */                        _knownComponents: {},
            /** @private @type {Object.<string, function(HTMLElement): Object>} */  _componentsXblClass: {},

            clinit: function () {
                ORBEON.xforms.XBL.componentInitialized.subscribe(this.onComponentInitialized, this, true);
            },

            /**
             * Called whenever a component is initialized (legacy components only).
             *
             * @param component
             * @return {void}
             */
            onComponentInitialized: function (component) {
                if (! ORBEON.xforms.XBL.isJavaScriptLifecycle(component.container)) {
                    if (! this._knownComponents[component.container.id]) {

                        // Find if this instance is in a full update container
                        /** @type {HTMLElement} */ var fullUpdate = null;
                        ORBEON.util.Dom.existsAncestorOrSelf(component.container, function (node) {
                            if ($(node).is('.xforms-update-full, .xxforms-dynamic-control')) {
                                fullUpdate = node;
                                return true;
                            } else {
                                return false;
                            }
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
                }
            },

            /**
             * Called when a full update is performed.
             *
             * @param {!string} fullUpdateId    Id of the control that contains the section that was updated.
             */
            onFullUpdateDone: function (fullUpdateId) {

                // Re-initialize all the existing XBL components inside the this container
                var me = this;
                var componentIds = this._fullUpdateToComponents[fullUpdateId];
                if (componentIds) {
                    _.each(componentIds, function (componentId) {
                        /** @type {HTMLElement} */ var componentContainer = document.getElementById(componentId);
                        if (componentContainer != null) {
                            // Call instance which will call init if necessary
                            var component = me._componentsXblClass[componentId].instance(componentContainer);

                            // Legacy
                            if (_.isFunction(component.enabled))
                                component.enabled();
                        }
                    });
                }
            }
        };

        ORBEON.onJavaScriptLoaded.subscribe(ORBEON.xforms.FullUpdate.clinit, ORBEON.xforms.FullUpdate, true);
    })();

    ORBEON.xforms.XBL = {

        /**
         * Base class for classes implementing an XBL component.
         */
        _BaseClass: function () {
            var BaseClass = function () {
            };
            BaseClass.prototype = {

                /**
                 * The HTML element that contains the component on the page.
                 */
                container: null
            }
        }(),

        isComponent: function(control) {
            return $(control).is('.xbl-component');
        },

        isJavaScriptLifecycle: function(control) {
            return $(control).is('.xbl-component.xbl-javascript-lifecycle');
        },

        isFocusable: function(control) {
            return $(control).is('.xbl-component.xbl-focusable');
        },

        // Map the XBL CSS class to the JavaScript class
        _cssClassesToConstructors: {},

        // Get or create an instance of the JavaScript companion class for the given element, which must be
        // an XBL control element or a descendant of an XBL control element.
        instanceForControl: function(elem) {

            var xblControlElem = $(elem).closest('.xbl-component')[0];

            if (xblControlElem) {

                var identifyingCssClass =
                    _.find(xblControlElem.className.split(" "), function(clazz) {
                        // The "identifying class" should be the the first after `xbl-component`, but filter
                        // known classes just in case
                        return clazz.indexOf("xbl-") == 0 &&
                               clazz != "xbl-component"   &&
                               clazz != "xbl-focusable"   &&
                               clazz != "xbl-javascript-lifecycle";
                    });

                if (identifyingCssClass) {
                    var factory = this._cssClassesToConstructors[identifyingCssClass];
                    if (factory) {
                        return factory.instance(xblControlElem);
                    } else {
                        return null;
                    }
                }
            } else {
                return null;
            }
        },

        // Declare a companion JavaScript class. The class is defined by a simple prototype and will map
        // to elements with the CSS class inferred from the binding name.
        declareCompanion: function(bindingName, prototype) {

            var parts = bindingName.split("|");
            var head = parts[0];
            var tail = bindingName.substring(head.length + 1);

            var cssClass = "xbl-" + head + "-" + tail;

            var xblClass = function() {};
            this.declareClass(xblClass, cssClass);
            xblClass.prototype = prototype;
            return xblClass;
        },

        /**
         *  Called by JavaScript companion code upon load.
         *
         *  This adds an `instance(target)` function to `xblClass`.
         */
        declareClass: function(xblClass, cssClass) {
            var doNothingSingleton = null;
            var instanceAlreadyCalled = false;

            this._cssClassesToConstructors[cssClass] = xblClass;

            // Define factory function for this class
            xblClass.instance = function (target) {
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
                        this.prototype.init = function () {
                            if (! this.initialized) {
                                originalInit.call(this);
                                this.initialized = true;
                                ORBEON.xforms.XBL.componentInitialized.fire(this);
                            }
                        }
                    }
                    // Inject destroy
                    var originalDestroy = this.prototype.destroy;
                    this.prototype.destroy = function () {
                        if (! _.isUndefined(originalDestroy))
                            originalDestroy.call(this);
                        $(this.container).data('xforms-xbl-object', null);
                    }
                }

                if (container == null) {
                    // If we get an event for a target which is not in the document, return a mock object
                    // that won't do anything when its methods are called
                    if (doNothingSingleton == null) {
                        doNothingSingleton = {};
                        for (var methodName in xblClass.prototype)
                            doNothingSingleton[methodName] = function () {};
                    }
                    return doNothingSingleton;
                } else {
                    // Get or create instance
                    var instance = $(container).data('xforms-xbl-object');
                    if (! _.isObject(instance) || instance.container != container) {
                        instance = new xblClass(container);
                        instance.xblClass = xblClass;
                        instance.container = container;
                        var formId = ORBEON.xforms.Controls.getForm(instance.container).id;
                        var form   = ORBEON.xforms.Page.getForm(formId);
                        form.xblInstances.push(instance);
                        if (hasInit) {
                            instance.initialized = false;
                            instance.init();
                        }
                        $(container).data('xforms-xbl-object', instance);
                    }
                    return instance;
                }
            };
        },

        // 2016-03-20: Called from xbl.xsl
        callValueChanged: function (prefix, component, target, property) {
            var partial = YAHOO.xbl;
            if (partial == null) return;
            partial = partial[prefix];
            if (partial == null) return;
            partial = partial[component];
            if (partial == null) return;
            partial = partial.instance(target);
            if (partial == null) return;
            var method = partial["parameter" + property + "Changed"];
            if (method == null) return;
            method.call(partial);
        },

        componentInitialized: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT)
    };

    ORBEON.xforms.Init = {

        // Should move to XBL component, see: https://github.com/orbeon/orbeon-forms/issues/2658.
        _range: function (range) {
            range.tabIndex = 0;
            ORBEON.xforms.ServerValueStore.set(range.id, "0");

            // In both cases the background <div> element must already have an id
            var backgroundDiv = YAHOO.util.Dom.getElementsByClassName("xforms-range-background", "div", range)[0];

            var thumbDiv = YAHOO.util.Dom.getElementsByClassName("xforms-range-thumb", "div", range)[0];
            thumbDiv.id = ORBEON.util.Utils.appendToEffectiveId(range.id, XF_LHHAI_SEPARATOR + "thumb");

            var slider = YAHOO.widget.Slider.getHorizSlider(backgroundDiv.id, thumbDiv.id, 0, 200);
            slider.subscribe("change", ORBEON.xforms.Events.sliderValueChange);
            ORBEON.xforms.Globals.sliderYui[range.id] = slider;
        },

        /**
         * For all the controls except list, we figure out the initial value of the control when
         * receiving the first focus event. For the lists on Firefox, the value has already changed
         * when we receive the focus event. So here we save the value for lists when the page loads.
         *
         * Should move to XBL component, see: https://github.com/orbeon/orbeon-forms/issues/2657.
         */
        _compactSelect: function (list) {
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
        _dialog: function (dialog) {

            var isModal     = $(dialog).is('.xforms-dialog-modal');
            var hasClose    = $(dialog).is('.xforms-dialog-close-true');
            var isDraggable = $(dialog).is('.xforms-dialog-draggable-true');
            var isVisible   = $(dialog).is('.xforms-dialog-visible-true');
            var isMinimal   = $(dialog).is('.xforms-dialog-appearance-minimal');

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
                    usearia: ORBEON.xforms.Page.getForm(ORBEON.xforms.Controls.getForm(dialog).id).useARIA(),
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
                    usearia: ORBEON.xforms.Page.getForm(ORBEON.xforms.Controls.getForm(dialog).id).useARIA(),
                    role: "" // See bug 315634 http://goo.gl/54vzd
                });
            }
            yuiDialog.showEvent.subscribe(ORBEON.xforms.Events.dialogShow, dialog.id);
            // Register listener for when the dialog is closed by a click on the "x"
            yuiDialog.beforeHideEvent.subscribe(ORBEON.xforms.Events.dialogClose, dialog.id);

            // This is for JAWS to read the content of the dialog (otherwise it just reads the button)
            var dialogDiv = YAHOO.util.Dom.getElementsByClassName("xforms-dialog", "div", yuiDialog.element)[0];
            dialogDiv.setAttribute("aria-live",  "polite");
            dialogDiv.setAttribute("aria-modal", "true");
            dialogDiv.setAttribute("role",       "dialog");
            var dialogHeadDiv = dialogDiv.querySelector(".xxforms-dialog-head");
            if (dialogHeadDiv != null)
                dialogDiv.setAttribute("aria-labelledby", dialogDiv.id + "_h");

            // If the dialog has a close "x" in the dialog toolbar, register a listener on the escape key that does the same as clicking on the "x"
            if (hasClose) {
                var escapeListener = new YAHOO.util.KeyListener(document, {keys: 27}, {
                    fn: yuiDialog.hide,
                    scope: yuiDialog,
                    correctScope: true
                });
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

    YAHOO.util.Event.throwErrors = true;
    ORBEON.onJavaScriptLoaded.fire();

})();
