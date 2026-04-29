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

var ELEMENT_TYPE = document.createElement("dummy").nodeType;

(function() {

    var $ = ORBEON.jQuery;
    var _ = ORBEON._;

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
            }
        },

        /**
         * Utility methods that don't in any other category
         */
        Utils: {

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

            // Escape a literal search string so it can be used in String.replace()
            escapeRegex: function(value) {
                return value.replace(/[\-\[\]{}()*+?.,\\\^$|#\s]/g, "\\$&");
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
                    var form = ORBEON.xforms.Page.getXFormsFormFromNamespacedIdOrThrow(formID);
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
            }
        }
    };
})();

(function() {

    var $ = ORBEON.jQuery;
    var _ = ORBEON._;

    // Define packages
    ORBEON.xforms = ORBEON.xforms || {};

    ORBEON.xforms.Controls = {

        /**
         * Updates the value of a control in the UI.
         *
         * @param control           HTML element for the control we want to update
         * @param newControlValue   New value
         */
        beforeValueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        valueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        afterValueChange: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
        lhhaChangeEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),



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
                    formInputs[foundSelected ? itemIndex : 0].focus();
                }
            } else if (ORBEON.xforms.XFormsXbl.isFocusable(control)) {
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
                    htmlControl.get(0).focus();
                else
                    // We haven't found anything to set the focus on, so don't mask the focus event, since we won't receive it
                    ORBEON.xforms.Globals.maskFocusEvents = false;
            }

            // 2023-01-12: Don't do this for static readonly controls.
            // Save current value as server value. We usually do this on focus, but for control where we set the focus
            // with `xf:setfocus`, we still receive the focus event when the value changes, but after the change event
            // (which means we then don't send the new value to the server).
            if (! control.classList.contains("xforms-static") && ORBEON.xforms.ServerValueStore.get(controlId) == null) {
                var currentValue = ORBEON.xforms.XFormsUi.getCurrentValue(control);
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
                        formInput.blur();
                    }
                }
            } else if (ORBEON.xforms.XFormsXbl.isFocusable(control)) {
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
                if (htmlControl != null)
                    htmlControl.blur();
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
            var alertElement = ORBEON.xforms.XFormsUi.getControlLHHA(control, "alert");
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
                            $(cursor).is('.xxforms-animate');                           // Only animate if class present

                    var updateClasses = _.partial(function (el) {
                        if (visible) {
                            el.classList.add("xforms-case-selected");
                            el.classList.remove("xforms-case-deselected");
                        } else {
                            el.classList.add("xforms-case-deselected");
                            el.classList.remove("xforms-case-selected");
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
                currentNode = currentNode.previousElementSibling;
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
                currentNode = currentNode.nextElementSibling;
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
            var OD = ORBEON.util.Dom;
            var OC = ORBEON.xforms.Controls;

            // Update class on group begin or delimiter
            if (isRelevant)
                node.classList.remove("xforms-disabled");
            else
                node.classList.add("xforms-disabled");

            // If this group/iteration becomes relevant, but has a parent that is non-relevant, we should not
            // remove xforms-disabled otherwise it will incorrectly show, so our job stops here
            if (isRelevant && FN.hasAncestor(node, function (node) {
                        return node.classList.contains("xforms-disabled");
                    })) return;

            FN.foldDescendants(node, null, function (node, value) {
                // Skip sub-tree if we are enabling and this sub-tree is disabled
                if (isRelevant && FN.isBegin(node) && node.classList.contains("xforms-disabled"))
                    return true;
                // Update disabled class on node
                if (isRelevant) {
                    node.classList.remove("xforms-disabled")
                } else {
                    node.classList.add("xforms-disabled")
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

        _showToolTip: function (tooltipForControl, control, target, toolTipSuffix, message, event) {

            // Cases where we don't want to reuse an existing tooltip for this control
            if (YAHOO.lang.isObject(tooltipForControl[control.id])) {
                const existingTooltip = tooltipForControl[control.id];
                if (existingTooltip.orbeonTarget != target) {
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
                    // We have a message, initialize YUI tooltip
                    var yuiTooltip = new YAHOO.widget.Tooltip(control.id + toolTipSuffix, {
                        context: target,
                        text: message,
                        showDelay: 0,
                        hideDelay: 0,
                        // We provide here a "high" zIndex value so the tooltip is "always" displayed on top over everything else.
                        // Otherwise, with dialogs, the tooltip might end up being below the dialog and be invisible.
                        zIndex: 10000
                    });
                    yuiTooltip.orbeonControl = control;
                    yuiTooltip.orbeonTarget  = target;
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

        orbeonLoadedEvent           : new YAHOO.util.CustomEvent("orbeonLoaded", window, false, YAHOO.util.CustomEvent.LIST, true),
        errorEvent                  : new YAHOO.util.CustomEvent("errorEvent"),
        componentChangedLayoutEvent : new YAHOO.util.CustomEvent("componentChangedLayout")
    };

    (function () {

        // 2023-01-09: This is only for backward compatibility with old code that doesn't use `javascript-lifecycle`.
        ORBEON.xforms.FullUpdate = {

            /** @private @type {Object.<string, boolean>} */                        _knownComponents: {},

            clinit: function () {
                ORBEON.xforms.XBL.componentInitialized.subscribe(this.onComponentInitialized, this, true);
            },

            /**
             * Called whenever a component is initialized (legacy components only).
             *
             * @param component
             * @return {void}
             */
            onComponentInitialized: function (containerAndConstructor) {
                if (! this._knownComponents[containerAndConstructor.container.id]) {
                    // Remember we looked at this one, so we don't have to do it again
                    this._knownComponents[containerAndConstructor.container.id] = true;
                }
            }
        };

        ORBEON.onJavaScriptLoaded.subscribe(ORBEON.xforms.FullUpdate.clinit, ORBEON.xforms.FullUpdate, true);
    })();

    ORBEON.xforms.XBL = {

        // Map the XBL CSS class to the JavaScript class
        _cssClassesToConstructors: {},

        // Get or create an instance of the JavaScript companion class for the given element, which must be
        // an XBL control element or a descendant of an XBL control element.
        instanceForControl: function(elem) {

            var xblControlElem = $(elem).closest('.xbl-component')[0];

            if (xblControlElem) {

                var identifyingCssClass =
                    _.find(xblControlElem.className.split(" "), function(clazz) {
                        // The "identifying class" should be the first after `xbl-component`, but filter
                        // known classes just in case
                        return clazz.indexOf("xbl-") == 0 &&
                               clazz != "xbl-component"   &&
                               clazz != "xbl-focusable"   &&
                               clazz != "xbl-javascript-lifecycle";
                    });

                if (identifyingCssClass) {
                    var factory = this._cssClassesToConstructors[identifyingCssClass];
                    if (factory) {
                        return factory(xblControlElem);
                    } else {
                        return null;
                    }
                }
                // TODO: This can return `undefined`!
            } else {
                return null;
            }
        },

        // Declare a companion JavaScript class. The class is defined by a prototype or a class.
        declareCompanion: function(bindingName, prototypeOrClass) {

            const isClass = typeof prototypeOrClass === "function";

            const parts = bindingName.split("|");
            const head = parts[0];
            const tail = bindingName.substring(head.length + 1);

            const cssClass = "xbl-" + head + "-" + tail;

            var xblClass;
            if (isClass) {
                xblClass = prototypeOrClass;
            } else {
                xblClass = function() {};
                Object.assign(xblClass.prototype, prototypeOrClass);
            }

            this.declareClass(xblClass, cssClass);
        },

        declareClass: function(xblClass, cssClass) {

            // 2023-01-09: TODO: Check cases where this is useful, and what kind of class we create below. Could we have
            //  simply an adapter class?
            var doNothingSingleton = null;

            this._cssClassesToConstructors[cssClass] = function(targetElem) {

                const subclass = ORBEON.xforms.XFormsXbl.createSubclass(xblClass);

                const containerElem =
                    targetElem == null || ! document.contains(targetElem)
                    ? null
                    : targetElem.closest("." + cssClass);

                if (containerElem == null) {
                    // If we get an event for a target which is not in the document, return a mock object
                    // that won't do anything when its methods are called
                    // Q: Under what circumstances can this happen?
                    if (doNothingSingleton == null) {
                        doNothingSingleton = {};
                        for (var methodName in xblClass.prototype) {
                            console.trace("instanceForControl: creating `doNothingSingleton` for `" + methodName + "`");
                            doNothingSingleton[methodName] = function () {};
                        }
                    }
                    console.debug("instanceForControl: returning mock `doNothingSingleton` for `" + cssClass + "`");
                    return doNothingSingleton;
                } else {
                    // Get or create instance
                    var instance = $(containerElem).data("xforms-xbl-object");
                    if (! _.isObject(instance) || instance.container != containerElem) {

                        // Q: Under what circumstances can this happen?
                        if (_.isObject(instance) && instance.container != containerElem) {
                            console.debug(
                                "instanceForControl: instance found in data but for different container: `" +
                                instance.container.id + "` and `" + containerElem.id + "`"
                            );
                            // Q: In this case, should we call `destroy()` on the class?
                        }

                        // Instantiate and initialize
                        instance = new subclass(containerElem);
                        instance.init();

                        // Keep track of the instance
                        // TODO: We remove those in `Form.destroy()`, but should we do it when the instance is destroyed
                        //  here too?
                        ORBEON.xforms.Page.getXFormsFormFromHtmlElemOrThrow(instance.container).xblInstances.push(instance);
                        $(containerElem).data('xforms-xbl-object', instance);
                    }
                    return instance;
                }
            };
        },

        componentInitialized: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT)
    };

    YAHOO.util.Event.throwErrors = true;
    ORBEON.onJavaScriptLoaded.fire();

})();
