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

/**
 * Parameters
 */
var XFORMS_DELAY_BEFORE_INCREMENTAL_REQUEST_IN_MS = 500;
var XFORMS_DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_IN_MS = 2000;
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

/* * * * * * Utility functions * * * * * */

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

function xformsGetElementPosition(element) {
    var offsetTrail = element;
    var offsetLeft = 0;
    var offsetTop = 0;
    while (offsetTrail) {
        offsetLeft += offsetTrail.offsetLeft;
        offsetTop += offsetTrail.offsetTop;
        offsetTrail = offsetTrail.offsetParent;
    }
    if (navigator.userAgent.indexOf("Mac") != -1 && xformsIsDefined(document.body.leftMargin)) {
        offsetLeft += document.body.leftMargin;
        offsetTop += document.body.topMargin;
    }
    return {left:offsetLeft, top:offsetTop};
}


/**
 * Retuns a string with contains all the concatenation of the child text node under the element.
 */
function xformsStringValue(element) {
    var result = "";
    for (var i = 0; i < element.childNodes.length; i++) {
        var child = element.childNodes[i];
        if (child.nodeType == TEXT_TYPE)
            result += child.nodeValue;
    }
    return result;
}

function xformsReplaceNodeText(node, text) {
    // Remove content
    while (node.childNodes.length > 0)
        node.removeChild(node.firstChild);
    // Add specified text
    var textNode = node.ownerDocument.createTextNode(text);
    node.appendChild(textNode);
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
            var parent = document.xformsRepeatTreeChildToParent[currentId];
            if (parent == null) break;
            var grandParent = document.xformsRepeatTreeChildToParent[parent];
            parentRepeatIndexes = (grandParent == null ? XFORMS_SEPARATOR_1 : XFORMS_SEPARATOR_2)
                    + document.xformsRepeatIndexes[parent] + parentRepeatIndexes;
            currentId = parent;
        }
    }

    var beginElementId = "repeat-begin-" + repeatId + parentRepeatIndexes;
    var beginElement = document.getElementById(beginElementId);
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
            document.xformsDebugDiv = getEventTarget(event);
            return false;
        });
        YAHOO.util.Event.addListener(document, "mouseup", function (event) {
            document.xformsDebugDiv = null;
            return false;
        });
        YAHOO.util.Event.addListener(document, "mousemove", function (event) {
            if (document.xformsDebugDiv) {
                document.xformsDebugDiv.style.left = event.clientX;
                document.xformsDebugDiv.style.top = event.clientY;
            }
            return false;
        });
    }
    debugDiv.innerHTML += text + " | ";
}

function xformsLogTime(text) {
    return;
    var oldTime = document.xformsTime;
    var currentTime = new Date().getTime();
    document.xformsTime = currentTime;
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
    var form = document.xformsRequestForm;
    switch (state) {
        case "loading" :
            if (form.xformsLoadingLoading != null)
                form.xformsLoadingLoading.style.display = "block";
            if (form.xformsLoadingError != null)
                form.xformsLoadingError.style.display = "none";
            if (form.xformsLoadingNone != null)
                form.xformsLoadingNone.style.display = "block";
            break;
        case "error":
            if (form.xformsLoadingLoading != null)
                form.xformsLoadingLoading.style.display = "none";
            if (form.xformsLoadingError != null)
                form.xformsLoadingError.style.display = "block";
            if (form.xformsLoadingNone != null)
                form.xformsLoadingNone.style.display = "none";
            break;
        case "none":
            if (form.xformsLoadingLoading != null)
                form.xformsLoadingLoading.style.display = "none";
            if (form.xformsLoadingError != null)
                form.xformsLoadingError.style.display = "none";
            if (form.xformsLoadingNone != null)
                form.xformsLoadingNone.style.display = "block";
            break;
    }
}

// Gets a value stored in the hidden client-state input field
function xformsGetFromClientState(form, key) {
    var keyValues = form.xformsClientState.value.split("&");
    for (var i = 0; i < keyValues.length; i = i + 2)
        if (keyValues[i] == key)
            return unescape(keyValues[i + 1]);
    return null;
}

// Returns a value stored in the hidden client-state input field
function xformsStoreInClientState(form, key, value) {
    var keyValues = form.xformsClientState.value == ""? new Array() : form.xformsClientState.value.split("&");
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
    form.xformsClientState.value = keyValues.join("&");
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
    var isUploadControl = YAHOO.util.Dom.hasClass(target, "xforms-upload");
    if (valueChanged && !isUploadControl) {
        target.previousValue = target.value;
        document.xformsChangedIdsRequest.push(target.id);
        var events = new Array(xformsCreateEventArray
                (target, "xxforms-value-change-with-focus-change", target.value, other));
        var incremental = other == null
                && YAHOO.util.Dom.hasClass(target, "xforms-incremental");
        xformsFireEvents(events, incremental);
    }
    return valueChanged;
}

/**
 * Function called by browser when value changes (either after changing focus or keyboard input).
 * This is an event handler we register with the browser on "change" and "keyup" (for incremental)
 * events.
 *
 * Here we intercept the user pressing cancel, we handle the case of the input which is in a span
 * and call xformsValueChanged().
 */
function xformsHandleValueChange(event) {
    var target = getEventTarget(event);
    if (event.keyCode == 27) {
        // We don't want to propagate the event and maybe cancel a request
        return xformsPreventDefault(event);
    } else {
        // If this is an input field, set value on parent and send event on parent element
        if (YAHOO.util.Dom.hasClass(target.parentNode, "xforms-input")
                || YAHOO.util.Dom.hasClass(target.parentNode, "xforms-select1-open")) {
            target.parentNode.valueSetByXForms++;
            target.parentNode.value = target.value;
            target = target.parentNode;
            target.lastKeyCode = event.keyCode;
        }
        xformsValueChanged(target, null);
        return true;
    }
}

// Handle click on trigger
function xformsHandleClick(event) {
    var target = getEventTarget(event);
    // Make sure the user really clicked on the trigger, instead of pressing enter in a nearby control
    if (YAHOO.util.Dom.hasClass(target, "xforms-trigger") && !YAHOO.util.Dom.hasClass(target, "xforms-readonly"))
        xformsFireEvents(new Array(xformsCreateEventArray(target, "DOMActivate", null)), false);
    return false;
}

function xformsComputeSelectValue(select) {
    var options = select.options;
    var selectValue = "";
    for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
        var option = options[optionIndex];
        if (option.selected) {
            if (selectValue != "") selectValue += " ";
            selectValue += option.value;
        }
    }
    select.selectValue = selectValue;
}

function xformsHandleSelectChanged(event) {
    var select = getEventTarget(event);
    xformsComputeSelectValue(select);
    xformsFireEvents(new Array(xformsCreateEventArray(select, "xxforms-value-change-with-focus-change",
        select.selectValue)), false);
}

function xformsSliderValueChange(offset) {
    // Notify server that value changed
    var rangeControl = document.getElementById(this.id);
    rangeControl.value = offset / 200;
    xformsValueChanged(rangeControl, null);
}

function xformsHandleOutputClick(event) {
    var events = new Array();
    var target = getEventTarget(event);
    // In the case of appearance="xxforms:html, target can be an element inside
    // the xforms-output, not the xforms-output itself. So we are here looking for the
    // xforms-parent when necessary.
    while (!YAHOO.util.Dom.hasClass(target, "xforms-output"))
        target = target.parentNode;
    events.push(xformsCreateEventArray(target, "DOMFocusIn", null));
    xformsFireEvents(events, false);
}

function xformsHandleFirefoxValueChange(property, oldvalue, newvalue) {
    var span = this;
    var textField = span.childNodes[1];
    if (span.valueSetByXForms == 0 && textField.value != newvalue) {
        span.value = newvalue;
        textField.value = newvalue;
        xformsDispatchEvent(textField, "change");
    }
    span.valueSetByXForms = 0;
    return newvalue;
}

function xformsHandleIEValueChange(event) {
    if (event.propertyName == "value") {
        var span = getEventTarget(event);
        var textField = span.childNodes[1];
        if (span.valueSetByXForms == 0 && textField.value != span.value) {
            textField.value = span.value;
            span.valueSetByXForms++;
            span.value = span.previousValue;
            xformsDispatchEvent(textField, "change");
        }
        span.valueSetByXForms = 0;
    }
}

function xformsHandleInputKeyPress(event) {
    if (event.keyCode == 10 || event.keyCode == 13) {
        var span = getEventTarget(event).parentNode;
        xformsHandleValueChange(event);
        xformsFireEvents(new Array(xformsCreateEventArray(span, "DOMActivate", null)), false);
        // Prevent default handling of enter, which might be equivalent as a click on some trigger in the form
        return xformsPreventDefault(event);
    }
}

function xformsHandleAutoCompleteMouseChange(input) {
    input.parentNode.lastKeyCode = -1;
    input.parentNode.value = input.value;
    xformsValueChanged(input.parentNode, null);
}

/**
 * Focus out events are only handled when we have receive a focus in event.
 * Here we just save the event which will be handled when we receive a focus
 * in from the browser.
 */
function xformsHandleBlur(event) {
    if (!document.xformsMaskFocusEvents) {
        var target = getEventTarget(event);

        // Look for first parent-or-self that is an XForms control
        while (true) {
            if (!target) break; // No more parent, stop search
            if (target.className != null) {
                if (YAHOO.util.Dom.hasClass(target, "xforms-control")) {
                    // We found our XForms element target
                    break;
                }
            }
            // Go to parent and continue search
            target = target.parentNode;
        }

        if (target != null) {
            // This is an event for an XForms control
            document.xformsPreviousDOMFocusOut = target;
            // HTML area does not throw value change event, so we throw it on blur
            if (YAHOO.util.Dom.hasClass(target, "xforms-textarea")
                    && YAHOO.util.Dom.hasClass(target, "xforms-mediatype-text-html"))
                xformsValueChanged(target, null);
        }
    }
}

function xformsHandleFocus(event) {
    if (!document.xformsMaskFocusEvents) {
        var target = getEventTarget(event);
        while (target && (target.className == null || !YAHOO.util.Dom.hasClass(target, "xforms-control")))
            target = target.parentNode;

        // Send focus events
        if (target != null) {
            if (document.xformsPreviousDOMFocusOut) {
                if (document.xformsPreviousDOMFocusOut != target) {
                    var events = new Array();
                    events.push(xformsCreateEventArray
                        (document.xformsPreviousDOMFocusOut, "DOMFocusOut", null));
                    events.push(xformsCreateEventArray(target, "DOMFocusIn", null));
                    xformsFireEvents(events, false);
                }
                document.xformsPreviousDOMFocusOut = null;
            } else {
                if (document.xformsPreviousDOMFocusIn != target) {
                    xformsFireEvents(new Array(xformsCreateEventArray(target, "DOMFocusIn", null)), false);
                }
            }
        }
        document.xformsPreviousDOMFocusIn = target;
    } else {
        document.xformsMaskFocusEvents = false;
    }
}

/**
 * Register listener on focus in and out events
 */
function xformsRegisterForFocusBlurEvents(control) {
    if (!control.focusBlurEventListenerRegistered) {
        control.focusBlurEventListenerRegistered = true;
        YAHOO.util.Event.addListener(control, "blur", xformsHandleBlur);
        YAHOO.util.Event.addListener(control, "focus", xformsHandleFocus);
    }
}

/**
 * Root function called by any widget that wants an event to be sent to the server.
 *
 * @param events       Array of arrays with each array containing:
 *                     new Array(target, eventName, value, other)
 * @param incremental  Are these incremental events
 */
function xformsFireEvents(events, incremental) {
    // Store the time of the first event to be sent in the queue
    var currentTime = new Date().getTime();
    if (document.xformsEvents.length == 0)
        document.xformsEventsFirstEventTime = currentTime;

    // Store events to fire
    for (var eventIndex = 0; eventIndex < events.length; eventIndex++)
        document.xformsEvents.push(events[eventIndex]);

    // Fire them with a delay to give us a change to aggregate events together
    document.xformsExecuteNextRequestInQueue++;
    if (incremental && !(currentTime - document.xformsEventsFirstEventTime >
            XFORMS_DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_IN_MS))
        window.setTimeout(xformsExecuteNextRequest,
            XFORMS_DELAY_BEFORE_INCREMENTAL_REQUEST_IN_MS);
    else xformsExecuteNextRequest(true);
    return false;
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
        var target = event.srcElement ? event.srcElement : event.currentTarget;
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

function xformsInitCheckesRadiosComputeSpanValue(span) {
    var inputs = span.getElementsByTagName("input");
    var spanValue = "";
    for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
        var input = inputs[inputIndex];
        if (input.checked) {
            if (spanValue != "") spanValue += " ";
            spanValue += input.value;
        }
    }
    span.value = spanValue;
}

function xformsInitCheckesRadios(control) {
    function inputSelected(event) {
        var checkbox = getEventTarget(event);
        var span = checkbox;
        while (true) {
            if (YAHOO.util.Dom.hasClass(span, "xforms-select-full")
                    || YAHOO.util.Dom.hasClass(span, "xforms-select1-full"))
                break;
            span = span.parentNode;
        }
        xformsInitCheckesRadiosComputeSpanValue(span);
        xformsFireEvents(new Array(xformsCreateEventArray
                (span, "xxforms-value-change-with-focus-change", span.value, null)), false);
    }

    // Register event listener on every checkbox
    var inputs = control.getElementsByTagName("input");
    for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++)
        YAHOO.util.Event.addListener(inputs[inputIndex], "click", inputSelected);

    // Compute the checkes value for the first time
    xformsInitCheckesRadiosComputeSpanValue(control);
}

function xformsHtmlEditorChange(editorInstance) {
    editorInstance.LinkedField.value = editorInstance.GetXHTML();
    // Throw value change event if the field is in incremental mode
    if (YAHOO.util.Dom.hasClass(editorInstance.LinkedField, "xforms-incremental"))
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
    var nodeIndex = 1;
    while (true) {
        var node = tree.getNodeByIndex(nodeIndex);
        if (node == null) break;
        if (node.checked) {
            if (control.value != "") control.value += " ";
            control.value += node.data.value;
        }
        nodeIndex++;
    }
    xformsValueChanged(control);
}

/**
 * Called by FCKeditor when an editor is fully loaded. This is our opportunity
 * to listen for events on this editor.
 */
function FCKeditor_OnComplete(editorInstance) {
    // Save reference to XForms element (textarea) in document for event handlers that receive the document
    editorInstance.EditorDocument.xformsElement = editorInstance.LinkedField;
    // Register value change handler
    editorInstance.Events.AttachEvent("OnSelectionChange", xformsHtmlEditorChange);
    // Register focus/blur events for Gecko
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "focus", xformsHandleFocus);
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "blur", xformsHandleBlur);
    // Register focus/blur events for IE
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "focusin", xformsHandleFocus);
    YAHOO.util.Event.addListener(editorInstance.EditorDocument, "focusout", xformsHandleBlur);
}

/**
 * Initializes attributes on the document object:
 *
 *     Array[]         xformsEvents
 *     Div             xformsLoadingLoading
 *     Div             xformsLoadingError
 *     Div             xformsLoadingNone
 *     Input           xformsStaticState
 *     Input           xformsDynamicState
 *     Form            xformsRequestForm
 *     boolean         xformsRequestInProgress
 *     XMLHttpRequest  xformsXMLHttpRequest
 *     Array           xformsChangedIdsRequest        Ids of controls changed while request is processed by server
 *     Array[id]->id   xformsRepeatTreeChildToParent  Returns the direct parent for each repeat id (or null if no parent)
 *     Array[id]->#    xformsRepeatIndexes            Current index for each repeat id
 */
function xformsInitializeControlsUnder(root) {

    // Gather all potential form controls
    var interestingTagNames = new Array("span", "button", "textarea", "input", "a", "select", "label", "td", "table", "div");
    var formsControls = new Array();
    for (var tagIndex = 0; tagIndex < interestingTagNames.length; tagIndex++) {
        var elements = root.getElementsByTagName(interestingTagNames[tagIndex]);
        for (var elementIndex = 0; elementIndex < elements.length; elementIndex++)
            formsControls.push(elements[elementIndex]);
        if (root.tagName.toLowerCase() == interestingTagNames[tagIndex])
            formsControls.push(root);
    }

    var xformsElementCount = 0;
    // Go through potential form controls, add style, and register listeners
    for (var controlIndex = 0; controlIndex < formsControls.length; controlIndex++) {

        var control = formsControls[controlIndex];

        // Check that this is an XForms control. Otherwise continue.
        var classes = control.className.split(" ");
        var isXFormsElement = false;
        var isXFormsAlert = false;
        var isIncremental = false;
        var isXFormsCheckboxRadio = false;
        var isXFormsComboboxList = false;
        var isXFormsAutoComplete = false;
        var isXFormsTrigger = false;
        var isXFormsOutput = false;
        var isXFormsInput = false;
        var isXFormsDate = false;
        var isWidget = false;
        var isXFormsRange = false;
        var isXFormsTree = false;
        var isXFormsMediatypeTextHTML = false;
        var isXFormsTextarea = false;
        var isXFormsNoInitElement = false;
        for (var classIndex = 0; classIndex < classes.length; classIndex++) {
            var className = classes[classIndex];
            if (className.indexOf("xforms-") == 0)
                isXFormsElement = true;
            if (className.indexOf("xforms-alert") == 0)
                isXFormsAlert = true;
            if (className == "xforms-incremental")
                isIncremental = true;
            if (className == "xforms-range-background")
                isXFormsRange = true;
            if (className == "xforms-select1-tree" || className == "xforms-select-tree")
                isXFormsTree = true;
            if (className == "xforms-select-full" || className == "xforms-select1-full")
                isXFormsCheckboxRadio = true;
            if (className == "xforms-select-compact" || className == "xforms-select1-minimal" || className == "xforms-select1-compact")
                isXFormsComboboxList = true;
            if (className == "xforms-trigger")
                isXFormsTrigger = true;
            if (className == "xforms-output")
                isXFormsOutput = true;
            if (className == "xforms-input")
                isXFormsInput = true;
            if (className == "xforms-select1-open")
                isXFormsAutoComplete = true;
            if (className == "xforms-select1-open-input")
                isXFormsNoInitElement = true;
            if (className == "xforms-select1-open-select")
                isXFormsNoInitElement = true;
            if (className == "xforms-mediatype-text-html")
                isXFormsMediatypeTextHTML = true;
            if (className == "xforms-textarea")
                isXFormsTextarea = true;
            if (className.indexOf("widget-") != -1)
                isWidget = true;
        }

        if (isWidget && !isXFormsElement) {
            // For widget: just add style
            xformsUpdateStyle(control);
        }

        if (isXFormsElement && !isXFormsNoInitElement) {
            xformsElementCount++;

            // For elements that correspond to XForms controls
            if (YAHOO.util.Dom.hasClass(control, "xforms-control")) {

                // Set initial values for disabled, readonly, required, and valid
                control.isRelevant = !YAHOO.util.Dom.hasClass(control, "xforms-disabled");
                control.isReadonly = YAHOO.util.Dom.hasClass(control, "xforms-readonly");
                control.isRequired = YAHOO.util.Dom.hasClass(control, "xforms-required");
                control.isValid = !YAHOO.util.Dom.hasClass(control, "xforms-invalid");

                // Initialize control.xformsForm
                if (typeof control.form == "undefined") {
                    // There is a span around the control go through parents until we find the form element
                    var candidateForm = control.parentNode;
                    while (candidateForm.tagName.toLowerCase() != "form")
                        candidateForm = candidateForm.parentNode;
                    control.xformsForm = candidateForm;
                } else {
                    // We have directly a form control
                    control.xformsForm = control.form;
                }
            }

            if (isXFormsTrigger) {
                // Handle click on trigger
                control.onclick = xformsHandleClick;
            } else if (isXFormsAutoComplete) {
                var textfield = control.childNodes[0];
                var select = control.childNodes[1];
                // Get list of possible values from the select
                var values = new Array();
                for (var optionIndex = 1; optionIndex < select.options.length; optionIndex++)
                    values.push(select.options[optionIndex].value);
                // Initialize auto-complete input
                var noFilter = YAHOO.util.Dom.hasClass(control, "xforms-select1-open-autocomplete-nofilter");
                actb(textfield, values, noFilter);
                // Initialize span
                control.value = textfield.value;
                control.previousValue = textfield.value;
                control.valueSetByXForms = 0;
                // Intercept end-user pressing enter in text field
                YAHOO.util.Event.addListener(textfield, "keypress", xformsHandleInputKeyPress);
                // Intercept incremental modifications
                if (isIncremental)
                    YAHOO.util.Event.addListener(textfield, "keyup", xformsHandleValueChange);
            } else if (isXFormsCheckboxRadio) {
                xformsInitCheckesRadios(control);
            } else if (isXFormsComboboxList) {
                // Register event listener on select
                YAHOO.util.Event.addListener(control, "change", xformsHandleSelectChanged);
                // Compute the checkes value for the first time
                xformsComputeSelectValue(control);
            } else if (isXFormsRange) {
                control.tabIndex = 0;
                control.previousValue = 0; // Will be modified once the initial value can be set
                var thumbDiv = control.firstChild;
                if (thumbDiv.nodeType != ELEMENT_TYPE) thumbDiv = thumbDiv.nextSibling;
                thumbDiv.id = control.id + XFORMS_SEPARATOR_1 + "thumb";
                var slider = YAHOO.widget.Slider.getHorizSlider(control.id, thumbDiv.id, 0, 200);
                slider.onChange = xformsSliderValueChange;
            } else if (isXFormsTree) {
                // This is a tree
                function addToTree(nameValueArray, treeNode, firstPosition) {
                    for (var arrayIndex = firstPosition; arrayIndex < nameValueArray.length; arrayIndex++) {
                        // Extract information from the first 3 position in the array
                        var childArray = nameValueArray[arrayIndex];
                        var name = childArray[0];
                        var value = childArray[1];
                        var selected = childArray[2];
                        // Create node and add to tree
                        var nodeInformation = { label: name, value: value };
                        var childNode;
                        if (control.xformsAllowMultipleSelection) {
                            childNode = new YAHOO.widget.TaskNode(nodeInformation, treeNode, false);
                            childNode.onCheckClick = xformsSelectTreeSelect;
                            if (selected) childNode.check();
                        } else {
                            nodeInformation.href = "javascript:xformsSelect1TreeSelect('"
                                    + control.id + "', '"
                                    + value.replace(XFORMS_REGEXP_SINGLE_QUOTE, "\\'")
                                    + "')";
                            childNode = new YAHOO.widget.TextNode(nodeInformation, treeNode, false);
                        }
                        addToTree(childArray, childNode, 3);
                        // Add this value to the list if selected
                        if (selected) {
                            if (control.value != "") control.value += " ";
                            control.value += value;
                        }
                    }
                }
                // Save in the control if it allows multiple selection
                control.xformsAllowMultipleSelection = YAHOO.util.Dom.hasClass(control, "xforms-select-tree");
                // Parse data put by the server in the div
                var treeArray = eval(control.firstChild.nodeValue);
                control.firstChild.nodeValue = "";
                // Create, populate, and show the tree
                var yahooTree = new YAHOO.widget.TreeView(control.id);
                var treeRoot = yahooTree.getRoot();
                control.value = "";
                addToTree(treeArray, treeRoot, 0);
                control.previousValue = control.value;
                yahooTree.draw();
            } else if (isXFormsOutput) {
                YAHOO.util.Event.addListener(control, "click", xformsHandleOutputClick);
            } else if (isXFormsInput) {
                control.value = control.childNodes[1].value;
                var textfield = control.childNodes[1];
                control.previousValue = textfield.value;
                control.valueSetByXForms = 0;
                // Intercept custom JavaScript code changing control value
                if (control.watch) {
                    // Firefox implements a watch() method
                    control.watch("value", xformsHandleFirefoxValueChange);
                } else {
                    // IE throws a propertychange event
                    YAHOO.util.Event.addListener(control, "propertychange", xformsHandleIEValueChange);
                }
                // Intercept end-user pressing enter in text field
                YAHOO.util.Event.addListener(textfield, "keypress", xformsHandleInputKeyPress);
                // Intercept incremental modifications
                if (isIncremental)
                    YAHOO.util.Event.addListener(textfield, "keyup", xformsHandleValueChange);
            } else if (control.tagName == "SPAN" || control.tagName == "DIV" || control.tagName == "LABEL") {
                // Don't add listeners on spans
            } else {
                // Handle value change and incremental modification
                control.previousValue = control.value;
                control.userModications = false;
                // Register listeners
                YAHOO.util.Event.addListener(control, "change", xformsHandleValueChange);
                if (isIncremental) {
                    YAHOO.util.Event.addListener(control, "keyup", xformsHandleValueChange);
                }
            }

            if (isXFormsCheckboxRadio) {
                var inputs = control.getElementsByTagName("input");
                for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
                    var input = inputs[inputIndex];
                    xformsRegisterForFocusBlurEvents(input);
                }
            } else {
                xformsRegisterForFocusBlurEvents(control);
            }

            // Alert label next to the control
            if (isXFormsAlert) {
                var isActive = YAHOO.util.Dom.hasClass(control, "xforms-alert-active");

                // Store reference in control element to this alert element
                var alertFor = document.getElementById(control.htmlFor);
                alertFor.alertElement = control;
                alertFor.isValid = !isActive;
                alertFor.alertMessage = xformsStringValue(control);

                // If active error, display messages and increment error counter
                if (isActive) {
                    var xformsMessages = document.getElementById("xforms-messages");
                    if (xformsMessages != null)
                        xformsMessages.invalidCount++;
                }
            }

            // Initialize HTML area
            if (isXFormsMediatypeTextHTML && isXFormsTextarea) {
                document.xformsHTMLAreaNames = new Array();
                var fckEditor = new FCKeditor(control.name);
                if (!xformsArrayContains(document.xformsHTMLAreaNames, control.name))
                    document.xformsHTMLAreaNames.push(control.name);
                fckEditor.BasePath = BASE_URL + "/ops/fckeditor/";
                fckEditor.ToolbarSet = "OPS";
                fckEditor.ReplaceTextarea() ;
            }

            // Add style to element
            xformsUpdateStyle(control);
        }
    }
}

function xformsPageLoaded() {

    xformsLogTime("Start xformsPageLoaded");

    // See if there is an XForms form
    var allForms = document.forms;
    var foundXFormsForm = false;
    for (var formIndex = 0; formIndex < allForms.length; formIndex++) {
        if (YAHOO.util.Dom.hasClass(allForms[formIndex], "xforms-form")) {
            foundXFormsForm = true;
            break;
        }
    }
    if (foundXFormsForm) {

        // Initialize tooltip library
        tt_init();

        // Initialize XForms server URL
        var scripts = document.getElementsByTagName("script");
        for (var scriptIndex = 0; scriptIndex < scripts.length; scriptIndex++) {
            var script = scripts[scriptIndex];
            var scriptSrc = script.getAttribute("src");
            if (scriptSrc != null) {
                var startPathToJavaScript = scriptSrc.indexOf(PATH_TO_JAVASCRIPT);
                if (startPathToJavaScript != -1) {
                    BASE_URL = script.getAttribute("src").substr(0, startPathToJavaScript);
                    XFORMS_SERVER_URL = BASE_URL + "/xforms-server";
                    break;
                }
            }
        }

        // Initialize attributes on document
        document.xformsRequestInProgress = false;
        document.xformsEvents = new Array();
        document.xformsExecuteNextRequestInQueue = 0;
        document.xformsChangedIdsRequest = new Array();
        document.xformsHTMLAreaNames = new Array();

        // Initialize summary section that displays alert messages
        var xformsMessages = document.getElementById("xforms-messages");
        if (xformsMessages) {
            xformsMessages.invalidCount = 0;
            var labels = xformsMessages.getElementsByTagName("LABEL");
            for (var labelIndex = 0; labelIndex < labels.length; labelIndex++) {
                var label = labels[labelIndex];
                var xformsControl = document.getElementById(label.htmlFor);
                xformsControl.xformsMessageLabel = label;
            }
        }

        // Initialize controls
        xformsInitializeControlsUnder(document.body);

        // Initialize attributes on form
        var forms = document.getElementsByTagName("form");
        for (var formIndex = 0; formIndex < forms.length; formIndex++) {
            var form = forms[formIndex];
            // Store in the form if this is an XForms form for faster lookup when iterating on form later on
            form.xformsIsXFormsForm = YAHOO.util.Dom.hasClass(form, "xforms-form");
            // If this is an XForms form, procede with initialization
            if (form.xformsIsXFormsForm) {

                // Initialize loading and error indicator
                form.xformsLoadingLoading = null;
                form.xformsLoadingError = null;
                form.xformsLoadingNone = null;
                var spans = form.getElementsByTagName("span");
                for (var spanIndex = 0; spanIndex < spans.length; spanIndex++) {
                    if (spans[spanIndex].className == "xforms-loading-loading")
                        form.xformsLoadingLoading = spans[spanIndex];
                    if (spans[spanIndex].className == "xforms-loading-error")
                        form.xformsLoadingError = spans[spanIndex];
                    if (spans[spanIndex].className == "xforms-loading-none")
                        form.xformsLoadingNone = spans[spanIndex];
                }

                var elements = form.elements;
                var xformsRepeatTree;
                var xformsRepeatIndices;
                for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
                    var element = elements[elementIndex];
                    if (element.name) {
                        // Store pointer to elements with state
                        if (element.name.indexOf("$static-state") != -1) {
                            form.xformsStaticState = element;
                        } else if (element.name.indexOf("$dynamic-state") != -1) {
                            form.xformsDynamicState = element;
                        } else if (element.name.indexOf("$client-state") != -1) {
                            form.xformsClientState = element;
                            if (element.value == "")
                                xformsStoreInClientState(form, "ajax-dynamic-state", form.xformsDynamicState.value);
                        }
                        // Store points to elements with repeat hierarchy
                        else if (element.name.indexOf("$repeat-tree") != -1) {
                            xformsRepeatTree = element;
                        } else if (element.name.indexOf("$repeat-indexes") != -1) {
                            xformsRepeatIndices = element;
                        }
                    }
                }

                // Parse and store initial repeat hierarchy
                document.xformsRepeatTreeChildToParent = new Array();
                var repeatTreeString = xformsRepeatTree.value;
                var repeatTree = repeatTreeString.split(",");
                for (var repeatIndex = 0; repeatIndex < repeatTree.length; repeatIndex++) {
                    var repeatInfo = repeatTree[repeatIndex].split(" ");
                    var id = repeatInfo[0];
                    var parent = repeatInfo.length > 1 ? repeatInfo[repeatInfo.length - 1] : null;
                    document.xformsRepeatTreeChildToParent[id] = parent;
                }
                document.xformsRepeatTreeParentToAllChildren = new Array();
                for (var child in document.xformsRepeatTreeChildToParent) {
                    var parent = document.xformsRepeatTreeChildToParent[child];
                    while (parent != null) {
                        if (!document.xformsRepeatTreeParentToAllChildren[parent])
                            document.xformsRepeatTreeParentToAllChildren[parent] = new Array();
                        document.xformsRepeatTreeParentToAllChildren[parent].push(child);
                        parent = document.xformsRepeatTreeChildToParent[parent];
                    }
                }

                // Parse and store initial repeat indexes
                document.xformsRepeatIndexes = new Array();
                var repeatIndexesString = xformsRepeatIndices.value;
                var repeatIndexes = repeatIndexesString.split(",");
                for (var repeatIndex = 0; repeatIndex < repeatIndexes.length; repeatIndex++) {
                    var repeatInfo = repeatIndexes[repeatIndex].split(" ");
                    var id = repeatInfo[0];
                    var index = repeatInfo[repeatInfo.length - 1];
                    document.xformsRepeatIndexes[id] = index;
                }

                // Ask server to resend events if this is not the first time load is called
                if (xformsGetFromClientState(form, "load-did-run") == null) {
                    xformsStoreInClientState(form, "load-did-run", "true");
                } else {
                    xformsFireEvents(new Array(xformsCreateEventArray(form, "xxforms-all-events-required", null, null)), false);
                }
            }
        }
    }

    xformsLogTime("End xformsPageLoaded");
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
    if (element.name)
        element.name= xformsAppendRepeatSuffix(element.name, idSuffixWithDepth);
    // Remove references to hint, help, alert, label as they might have changed
    if (xformsIsDefined(element.labelElement)) element.labelElement = null;
    if (xformsIsDefined(element.hintElement)) element.hintElement = null;
    if (xformsIsDefined(element.helpElement)) element.helpElement = null;
    if (xformsIsDefined(element.alertElement)) element.alertElement = null;
    if (xformsIsDefined(element.divId)) element.divId = null;
    element.styleListenerRegistered = false;
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
        if (node.tagName == "INPUT") {
            return node;
        } else {
            for (var childIndex in node.childNodes) {
                var result = xformsGetInputUnderNode(node.childNodes[childIndex]);
                if (result != null) return result;
            }
        }
    } else {
        return null;
    }
}

function xformsGetClassForReapeatId(repeatId) {
    var depth = 1;
    var currentRepeatId = repeatId;
    while (true) {
        currentRepeatId = document.xformsRepeatTreeChildToParent[currentRepeatId];
        if (currentRepeatId == null) break;
        depth = depth == 1 ? 2 : 1;
    }
    return "xforms-repeat-selected-item-" + depth;
}

function xformsHandleResponse(o) {
    var responseXML = o.responseXML;
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
                newDynamicState = xformsStringValue(responseRoot.childNodes[i]);

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
                                        var repeatId = copyRepeatTemplateElement.getAttribute("id");
                                        var parentIndexes = copyRepeatTemplateElement.getAttribute("parent-indexes");
                                        var idSuffix = copyRepeatTemplateElement.getAttribute("id-suffix");
                                        // Put nodes of the template in an array
                                        var templateNodes = new Array();
                                        {
                                            // Locate end of the repeat
                                            var delimiterTagName = null;
                                            var templateRepeatEnd = document.getElementById("repeat-end-" + repeatId);
                                            var templateNode = templateRepeatEnd.previousSibling;
                                            var nestedRepeatLevel = 0;
                                            while (!(nestedRepeatLevel == 0 && templateNode.nodeType == ELEMENT_TYPE
                                                     && YAHOO.util.Dom.hasClass(templateNode, "xforms-repeat-delimiter"))) {
                                                var nodeCopy = templateNode.cloneNode(true);
                                                if (templateNode.nodeType == ELEMENT_TYPE) {
                                                    // Save tag name to be used for delimiter
                                                    delimiterTagName = templateNode.tagName;
                                                    // Decrement nestedRepeatLevel when we we exit a nested repeat
                                                    if (YAHOO.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") &&
                                                            templateNode.id.indexOf("repeat-begin-") == 0)
                                                        nestedRepeatLevel--;
                                                    // Add suffix to all the ids
                                                    xformsAddSuffixToIds(nodeCopy, parentIndexes == "" ? idSuffix : parentIndexes + XFORMS_SEPARATOR_2 + idSuffix, nestedRepeatLevel);
                                                    // Increment nestedRepeatLevel when we enter a nested repeat
                                                    if (YAHOO.util.Dom.hasClass(templateNode, "xforms-repeat-begin-end") &&
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
                                                        && YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                        && !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-template"))) {
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
                                        // Initialize style on copied node
                                        for (var templateNodeIndex in templateNodes) {
                                            templateNode = templateNodes[templateNodeIndex];
                                            if (templateNode.nodeType == ELEMENT_TYPE)
                                                xformsInitializeControlsUnder(templateNode);
                                        }

                                        break;
                                    }


                                    // Delete element in repeat
                                    case "delete-repeat-elements": {
                                        // Extract data from server response
                                        var deleteElementElement = controlValuesElement.childNodes[j];
                                        var deleteId = deleteElementElement.getAttribute("id");
                                        var parentIndexes = deleteElementElement.getAttribute("parent-indexes");
                                        var count = deleteElementElement.getAttribute("count");
                                        // Find end of the repeat
                                        var repeatEnd = document.getElementById("repeat-end-" + xformsAppendRepeatSuffix(deleteId, parentIndexes));
                                        // Find last element to delete
                                        var lastElementToDelete;
                                        {
                                            lastElementToDelete = repeatEnd.previousSibling;
                                            if (parentIndexes == "") {
                                                // Top-level repeat: need to go over template
                                                while (lastElementToDelete.nodeType != ELEMENT_TYPE
                                                        || !YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter"))
                                                    lastElementToDelete = lastElementToDelete.previousSibling;
                                                lastElementToDelete = lastElementToDelete.previousSibling;
                                            }
                                        }
                                        // Perform delete
                                        for (var countIndex = 0; countIndex < count; countIndex++) {
                                            while (true) {
                                                var wasDelimiter = lastElementToDelete.nodeType == ELEMENT_TYPE
                                                    && YAHOO.util.Dom.hasClass(lastElementToDelete, "xforms-repeat-delimiter");
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
                                var controlId = itemsetElement.getAttribute("id");
                                var documentElement = document.getElementById(controlId);
                                var documentElementClasses = documentElement.className.split(" ");

                                if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-open")) {
                                    // Build list with new values
                                    var newValues = new Array();
                                    for (var k = 0; k < itemsetElement.childNodes.length; k++) {
                                        var itemElement = itemsetElement.childNodes[k];
                                        if (itemElement.nodeType == ELEMENT_TYPE)
                                            newValues.push(itemElement.getAttribute("value"));
                                    }

                                    // Case of the auto-complete control
                                    var textfield = documentElement.childNodes[0];
                                    textfield.actb_keywords = newValues;
                                    if (xformsIsDefined(documentElement.lastKeyCode) && documentElement.lastKeyCode != -1)
                                        textfield.actb_tocomplete(documentElement.lastKeyCode);
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
                                                documentElement.options.add(newOption);
                                                newOption.text = itemElement.getAttribute("label");
                                                newOption.value = itemElement.getAttribute("value");
                                                newOption.selected = xformsArrayContains(selectedValues, newOption.value);
                                            } else {
                                                // Replace current label/value if necessary
                                                var option = options[itemCount];
                                                if (option.text != itemElement.getAttribute("label")) {
                                                    option.text = itemElement.getAttribute("label");
                                                }
                                                if (option.value != itemElement.getAttribute("value")) {
                                                    option.value = itemElement.getAttribute("value");
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
                                    //     <span id="id" class="xforms-control xforms-select[1]-full">
                                    //         <span><input type="checkbox" checked="..." value="v"/> Vanilla</span>
                                    //         ... other checkboxes / radio buttons
                                    //     </span>
                                    //
                                    // Template follows:
                                    //     <span id="xforms-select-template-id" class="xforms-select-template">
                                    //         <span><input type="checkbox" checked="..." value="$xforms-template-value$"/> $xforms-template-label$</span>
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
                                    for (var k = 0; k < itemsetElement.childNodes.length; k++) {
                                        var itemElement = itemsetElement.childNodes[k];
                                        if (itemElement.nodeType == ELEMENT_TYPE) {
                                            var templateClone = template.cloneNode(true);
                                            xformsStringReplace(templateClone, "$xforms-template-label$",
                                                itemElement.getAttribute("label"));
                                            xformsStringReplace(templateClone, "$xforms-template-value$",
                                                itemElement.getAttribute("value"));
                                            documentElement.appendChild(templateClone);
                                            // Restore checked state after copy
                                            if (valueToChecked[itemElement.getAttribute("value")] == true)
                                                xformsGetInputUnderNode(templateClone).checked = true;
                                        }
                                    }

                                    // Compute value, of checkboxes/radio buttons and register listeners
                                    xformsInitCheckesRadios(documentElement);
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
                                        var newControlValue = xformsStringValue(controlElement);
                                        var controlId = controlElement.getAttribute("id");
                                        var relevant = controlElement.getAttribute("relevant");
                                        var readonly = controlElement.getAttribute("readonly");
                                        var required = controlElement.getAttribute("required");
                                        var displayValue = controlElement.getAttribute("display-value");
                                        var type = controlElement.getAttribute("type");
                                        var documentElement = document.getElementById(controlId);
                                        if (documentElement == null)
                                            throw "Can not find element with id '" + controlId + "' received in <control>";
                                        var documentElementClasses = documentElement.className.split(" ");

                                        // Check if this control was modified and we haven't even received the key event yet
                                        // This can happen as the the keyup event is dispatched after the control.value is modified,
                                        // and it is possible to receive a response from the server after the value is modified but
                                        // before the keyup event is dispatched.
                                        var foundControlModified = false;
                                        if (YAHOO.util.Dom.hasClass(documentElement, "xforms-input")) {
                                            if (documentElement.childNodes[1].value != documentElement.previousValue)
                                                foundControlModified = true;
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-open")) {
                                            if (documentElement.childNodes[0].value != documentElement.previousValue)
                                                foundControlModified = true;
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-textarea")
                                                && YAHOO.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                            // For HTML area, compare previous value to value of the HTML area widget
                                            var htmlEditor = FCKeditorAPI.GetInstance(documentElement.name);
                                            if (documentElement.previousValue != htmlEditor.GetXHTML())
                                                foundControlModified = true;
                                        } else if (xformsIsDefined(documentElement.previousValue)
                                                && documentElement.previousValue != documentElement.value) {
                                            foundControlModified = true;
                                        }
                                        // Check if this control has been modified while the event was processed
                                        if (!foundControlModified) {
                                            for (var indexId = 0; indexId < document.xformsChangedIdsRequest.length; indexId++) {
                                                if (document.xformsChangedIdsRequest[indexId] == controlId) {
                                                    foundControlModified = true;
                                                    break;
                                                }
                                            }
                                        }

                                        // Update value
                                        if (foundControlModified) {
                                            // User has modified the value of this control since we sent our request:
                                            // so don't try to update it
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-trigger")) {
                                            // Triggers don't have a value: don't update them
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-open")) {
                                            // Auto-complete
                                            var textfield = documentElement.childNodes[0];
                                            var select = documentElement.childNodes[1];

                                            // Populate values
                                            if (textfield.value != newControlValue)
                                                textfield.value = newControlValue;
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select-full")
                                                || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-full")) {
                                            // Handle checkboxes and radio buttons
                                            var selectedValues = YAHOO.util.Dom.hasClass(documentElement, "xforms-select-full")
                                                ? newControlValue.split(" ") : new Array(newControlValue);
                                            var checkboxInputs = documentElement.getElementsByTagName("input");
                                            for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                                                var checkboxInput = checkboxInputs[checkboxInputIndex];
                                                checkboxInput.checked = xformsArrayContains(selectedValues, checkboxInput.value);
                                            }
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-select-compact")
                                                || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-compact")
                                                || YAHOO.util.Dom.hasClass(documentElement, "xforms-select1-minimal")) {
                                            // Handle lists and comboboxes
                                            var selectedValues = YAHOO.util.Dom.hasClass(documentElement, "xforms-select-compact")
                                                ? newControlValue.split(" ") : new Array(newControlValue);
                                            var options = documentElement.options;
                                            for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                                                var option = options[optionIndex];
                                                option.selected = xformsArrayContains(selectedValues, option.value);
                                            }
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-output")) {
                                            // XForms output
                                            var newOutputControlValue = displayValue != null ? displayValue : newControlValue;
                                            if (YAHOO.util.Dom.hasClass(documentElement, "xforms-mediatype-image")) {
                                                documentElement.firstChild.src = newOutputControlValue;
                                            } else {
                                                xformsReplaceNodeText(documentElement, newOutputControlValue);
                                            }
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-input")) {
                                            // XForms input
                                            var displayField = documentElement.childNodes[0];
                                            var inputField = documentElement.childNodes[1];
                                            var datePicker = documentElement.childNodes[2];

                                            // Change classes on control and date pick based on type
                                            if (type == "{http://www.w3.org/2001/XMLSchema}date") {
                                                for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
                                                    var child = documentElement.childNodes[childIndex];
                                                    YAHOO.util.Dom.addClass(child, "xforms-type-date");
                                                    YAHOO.util.Dom.removeClass(child, "xforms-type-string");
                                                }
                                            } else if (type != null && type != "{http://www.w3.org/2001/XMLSchema}date") {
                                                for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
                                                    var child = documentElement.childNodes[childIndex];
                                                    YAHOO.util.Dom.addClass(child, "xforms-type-string");
                                                    YAHOO.util.Dom.removeClass(child, "xforms-type-date");
                                                }
                                            }

                                            // Populate values
                                            if (YAHOO.util.Dom.hasClass(inputField, "xforms-type-date"))
                                                xformsReplaceNodeText(displayField, displayValue == null ? "" : displayValue);
                                            if (documentElement.value != newControlValue) {
                                                documentElement.previousValue = newControlValue;
                                                documentElement.valueSetByXForms++;
                                                documentElement.value = newControlValue;
                                            }
                                            if (inputField.value != newControlValue)
                                                inputField.value = newControlValue;
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-textarea")
                                                && YAHOO.util.Dom.hasClass(documentElement, "xforms-mediatype-text-html")) {
                                            // HTML area
                                            var htmlEditor = FCKeditorAPI.GetInstance(documentElement.name);
                                            if (xformsNormalizeEndlines(htmlEditor.GetXHTML()) != xformsNormalizeEndlines(newControlValue)) {
                                                htmlEditor.SetHTML(newControlValue);
                                                documentElement.value = newControlValue;
                                                documentElement.previousValue = newControlValue;
                                            }
                                        } else if (YAHOO.util.Dom.hasClass(documentElement, "xforms-control")
                                                && typeof(documentElement.value) == "string") {
                                            // Textarea, password
                                            if (xformsNormalizeEndlines(documentElement.value) != xformsNormalizeEndlines(newControlValue)) {
                                                documentElement.value = newControlValue;
                                                documentElement.previousValue = newControlValue;
                                            }
                                        }

                                        // Store new label message in control attribute
                                        var newLabel = controlElement.getAttribute("label");
                                        if (newLabel != null && newLabel != documentElement.labelMessage)
                                            documentElement.labelMessage = newLabel;
                                        // Store new hint message in control attribute
                                        var newHint = controlElement.getAttribute("hint");
                                        if (newHint != null && newHint != documentElement.hintMessage)
                                            documentElement.hintMessage = newHint;
                                        // Store new help message in control attribute
                                        var newHelp = controlElement.getAttribute("help");
                                        if (newHelp != null && newHelp != documentElement.helpMessage)
                                            documentElement.helpMessage = newHelp;
                                        // Store new alert message in control attribute
                                        var newAlert = controlElement.getAttribute("alert");
                                        if (newAlert != null && newAlert != documentElement.alertMessage)
                                            documentElement.alertMessage = newAlert;
                                        // Store validity, label, hint, help in element
                                        var newValid = controlElement.getAttribute("valid");
                                        if (newValid != null) {
                                            var newIsValid = newValid != "false";
                                            if (newIsValid != documentElement.isValid) {
                                                // Show or hide messages section
                                                var xformsMessages = document.getElementById("xforms-messages");
                                                if (xformsMessages) {
                                                    xformsMessages.invalidCount += (newIsValid ? -1 : +1);
                                                    xformsMessages.style.display = xformsMessages.invalidCount == 0 ? "none" : "";
                                                }
                                                // Show or hide specific message for this control
                                                if (documentElement.xformsMessageLabel) {
                                                    if (newIsValid) {
                                                        documentElement.xformsMessageLabel.style.display = "none";
                                                    } else {
                                                        if (documentElement.alertMessage != "")
                                                            documentElement.xformsMessageLabel.style.display = "";
                                                    }
                                                }
                                            }
                                            documentElement.isValid = newIsValid;
                                        }
                                        // Update relevant and readonly
                                        if (relevant)
                                            documentElement.isRelevant = relevant == "true";
                                        if (readonly)
                                            documentElement.isReadonly = readonly == "true";
                                        if (required)
                                            documentElement.isRequired = required == "true";

                                        // Update style
                                        xformsUpdateStyle(documentElement);
                                        if (documentElement.labelElement)
                                            xformsUpdateStyle(documentElement.labelElement);
                                        if (documentElement.hintElement)
                                            xformsUpdateStyle(documentElement.hintElement);
                                        if (documentElement.helpElement)
                                            xformsUpdateStyle(documentElement.helpElement);
                                        if (documentElement.alertElement)
                                            xformsUpdateStyle(documentElement.alertElement);

                                        break;
                                    }

                                    // Model item properties on a repeat item
                                    case "repeat-iteration": {
                                        // Extract data from server response
                                        var repeatIterationElement = controlValuesElement.childNodes[j];
                                        var repeatId = repeatIterationElement.getAttribute("id");
                                        var iteration = repeatIterationElement.getAttribute("iteration");
                                        var relevant = repeatIterationElement.getAttribute("relevant");
                                        // Remove or add xforms-disabled on elements after this delimiter
                                        var cursor = xformsFindRepeatDelimiter(repeatId, iteration).nextSibling;
                                        while (!(cursor.nodeType == ELEMENT_TYPE &&
                                                 (YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                                    || YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-begin-end")))) {
                                            if (cursor.nodeType == ELEMENT_TYPE) {
                                                if (relevant) {
                                                    if (relevant == "true") YAHOO.util.Dom.removeClass(cursor, "xforms-disabled");
                                                    else YAHOO.util.Dom.addClass(cursor, "xforms-disabled");
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
                                    var controlId = divElement.getAttribute("id");
                                    var visibile = divElement.getAttribute("visibility") == "visible";

                                    var caseBeginId = "xforms-case-begin-" + controlId;
                                    var caseBegin = document.getElementById(caseBeginId);
                                    if (caseBegin == null) throw "Can not find element with id '" + caseBeginId + "'";
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
                                            YAHOO.util.Dom.addClass(cursor, visibile ? "xforms-case-selected" : "xforms-case-deselected");
                                            YAHOO.util.Dom.removeClass(cursor, visibile ? "xforms-case-deselected" : "xforms-case-selected");
                                        }
                                    }
                                }
                            }

                            // After we display divs, we must reenable the HTML editors.
                            // This is a workaround for a Gecko bug documented at:
                            // http://wiki.fckeditor.net/Troubleshooting#gecko_hidden_div
                            if (XFORMS_IS_GECKO && document.xformsHTMLAreaNames.length > 0) {
                                for (var htmlAreaIndex = 0; htmlAreaIndex < document.xformsHTMLAreaNames.length; htmlAreaIndex++) {
                                    var name = document.xformsHTMLAreaNames[htmlAreaIndex];
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
                                    var repeatId = repeatIndexElement.getAttribute("id");
                                    var newIndex = repeatIndexElement.getAttribute("new-index");
                                    newRepeatIndexes[repeatId] = newIndex;
                                }
                            }
                            // For each repeat id that changes, see if all the children are also included in
                            // newRepeatIndexes. If they are not, add an entry with the index unchanged.
                            for (var repeatId in newRepeatIndexes) {
                                var children = document.xformsRepeatTreeParentToAllChildren[repeatId];
                                for (var childIndex in children) {
                                    var child = children[childIndex];
                                    if (!newRepeatIndexes[child])
                                        newRepeatIndexes[child] = document.xformsRepeatIndexes[child];
                                }
                            }
                            // Unhighlight items at old indexes
                            for (var repeatId in newRepeatIndexes) {
                                var oldIndex = document.xformsRepeatIndexes[repeatId];
                                if (oldIndex != 0) {
                                    var oldItemDelimiter = xformsFindRepeatDelimiter(repeatId, oldIndex);
                                    if (oldItemDelimiter != null) {
                                        cursor = oldItemDelimiter.nextSibling;
                                        while (cursor.nodeType != ELEMENT_TYPE ||
                                               (!YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                               && !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-begin-end"))) {
                                            if (cursor.nodeType == ELEMENT_TYPE)
                                                YAHOO.util.Dom.removeClass(cursor, xformsGetClassForReapeatId(repeatId));
                                            cursor = cursor.nextSibling;
                                        }
                                    }
                                }
                            }
                            // Store new indexes
                            for (var repeatId in newRepeatIndexes) {
                                var newIndex = newRepeatIndexes[repeatId];
                                document.xformsRepeatIndexes[repeatId] = newIndex;
                            }
                            // Highlight item a new index
                            for (var repeatId in newRepeatIndexes) {
                                var newIndex = newRepeatIndexes[repeatId];
                                if (newIndex != 0) {
                                    var newItemDelimiter = xformsFindRepeatDelimiter(repeatId, newIndex);
                                    if (newItemDelimiter == null)
                                        throw "Can not find delimiter for repeatId '" + repeatId + "' index '" + newIndex + "'";
                                    cursor = newItemDelimiter.nextSibling;
                                    while (cursor.nodeType != ELEMENT_TYPE ||
                                           (!YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-delimiter")
                                           && !YAHOO.util.Dom.hasClass(cursor, "xforms-repeat-begin-end"))) {
                                        if (cursor.nodeType == ELEMENT_TYPE) {
                                            var classNameArray = cursor.className.split(" ");
                                            classNameArray.push(xformsGetClassForReapeatId(repeatId));
                                            cursor.className = classNameArray.join(" ");
                                        }
                                        cursor = cursor.nextSibling;
                                    }
                                }
                            }
                            break;
                        }

                        // Submit form
                        case "submission": {
                            if (xformsGetLocalName(actionElement.childNodes[actionIndex]) == "submission") {
                                newDynamicStateTriggersPost = true;
                                document.xformsRequestForm.xformsDynamicState.value = newDynamicState;
                                document.xformsRequestForm.submit();
                            }
                            break;
                        }

                        // Display modal message
                        case "message": {
                            var messageElement = actionElement.childNodes[actionIndex];
                            var message = xformsStringValue(messageElement);
                            if (messageElement.getAttribute("level") == "modal")
                                alert(message);
                            break;
                        }

                        // Load another page
                        case "load": {
                            var loadElement = actionElement.childNodes[actionIndex];
                            var resource = loadElement.getAttribute("resource");
                            var show = loadElement.getAttribute("show");
                            var target = loadElement.getAttribute("target");
                            if (show == "replace") {
                                if (target == null) {
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
                            var controlId = setfocusElement.getAttribute("control-id");
                            var control = document.getElementById(controlId);
                            if (YAHOO.util.Dom.hasClass(control, "xforms-input"))
                                control = control.childNodes[1];
                            document.xformsMaskFocusEvents = true;
                            control.focus();
                            break;
                        }
                    }
                }
            }
        }

        // Store new dynamic state if that state did not trigger a post
        if (!newDynamicStateTriggersPost) {
            xformsStoreInClientState(document.xformsRequestForm, "ajax-dynamic-state", newDynamicState);
        }

        if (newDynamicStateTriggersReplace || newDynamicStateTriggersPost) {
            // Display loading indicator when we go to another page.
            // Display it even if it was not displayed before as loading the page could take time.
            xformsDisplayIndicator("loading");
        } else if (document.xformsEvents.length == 0) {
            // Hide loading indicator if there are no other request in the queue
            xformsDisplayIndicator("none");
        }

    } else if (responseXML && responseXML.documentElement
            && responseXML.documentElement.tagName.indexOf("exceptions") != -1) {
        // We received an error from the server

        // Find an error message starting from the inner-most exception
        var errorMessage = "XForms error";
        var messageElements = responseXML.getElementsByTagName("message");
        for (var messageIndex = messageElements.length - 1; messageIndex >= 0; messageIndex--) {
            if (messageElements[messageIndex].firstChild != null) {
                errorMessage += ": " + xformsStringValue(messageElements[messageIndex]);
                break;
            }
        }
        // Display error
        var errorContainer = document.xformsRequestForm.xformsLoadingError;
        xformsReplaceNodeText(errorContainer, errorMessage);
        xformsDisplayIndicator("error");
    } else {
        // The server didn't send valid XML
        document.xformsRequestForm.xformsLoadingError.innerHTML = "Unexpected response received from server";
        xformsDisplayIndicator("error");

    }

    // Go ahead with next request, if any
    document.xformsRequestInProgress = false;
    document.xformsExecuteNextRequestInQueue++;
    xformsExecuteNextRequest(false);
}

function xformsDisplayLoading() {
    if (document.xformsRequestInProgress == true)
        xformsDisplayIndicator("loading");
}

function xformsExecuteNextRequest(bypassRequestQueue) {
    bypassRequestQueue = typeof(bypassRequestQueue) == "boolean"  && bypassRequestQueue == true;
    document.xformsExecuteNextRequestInQueue--;
    if (!document.xformsRequestInProgress
            && document.xformsEvents.length > 0
            && (bypassRequestQueue || document.xformsExecuteNextRequestInQueue == 0)) {

        // Save the form for this request
        document.xformsRequestForm = document.xformsEvents[0][0].xformsForm;

        // Mark this as loading
        document.xformsRequestInProgress = true;
        if (XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS == 0) xformsDisplayLoading();
        else window.setTimeout(xformsDisplayLoading, XFORMS_DELAY_BEFORE_DISPLAY_LOADING_IN_MS);

        // Build request
        var requestDocumentString = "";
        var indent = "    ";
        {
            // Start request
            requestDocumentString += '<xxforms:event-request xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">\n';

            // Add static state
            requestDocumentString += indent;
            requestDocumentString += '<xxforms:static-state>';
            requestDocumentString += document.xformsRequestForm.xformsStaticState.value;
            requestDocumentString += '</xxforms:static-state>\n';

            // Add dynamic state (element is just created and will be filled just before we send the request)
            requestDocumentString += indent;
            requestDocumentString += '<xxforms:dynamic-state>';
            requestDocumentString += xformsGetFromClientState(document.xformsRequestForm, "ajax-dynamic-state");
            requestDocumentString += '</xxforms:dynamic-state>\n';

            // Start action
            requestDocumentString += indent;
            requestDocumentString += '<xxforms:action>\n';

            // Add events
            var handledEvents = [];
            for (var i = 0; i < document.xformsEvents.length; i++) {
                // Extract information from event array
                var event = document.xformsEvents[i];
                var target = event[0];
                var eventName = event[1];
                var value = event[2];
                var other = event[3];

                // Only handle this event if it is for the form we chose
                if (target.xformsForm == document.xformsRequestForm) {
                    // Create <xxforms:event> element
                    requestDocumentString += indent + indent;
                    requestDocumentString += '<xxforms:event';
                    requestDocumentString += ' name="' + eventName + '"';
                    if (target != null)
                        requestDocumentString += ' source-control-id="' + target.id + '"';
                    if (other != null)
                        requestDocumentString += ' other-control-id="' + other.id + '"';
                    requestDocumentString += '>';
                    if (value != null) {
                        // When the range is used we get an int here when the page is first loaded
                        if (typeof value == "string")
                            value = value.replace(XFORMS_REGEXP_OPEN_ANGLE, "&lt;");
                        requestDocumentString += value;
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
                document.xformsEvents.splice(handledEvents[i], 1);

            // Reset changes, as changes are included in this bach of events
            document.xformsChangedIdsRequest = new Array();
        }

        var aysncRequestCallback = { success: xformsHandleResponse };

        // Send request
        YAHOO.util.Connect.initHeader("Content-Type", "application/xml");
        YAHOO.util.Connect.asyncRequest("POST", XFORMS_SERVER_URL, aysncRequestCallback, requestDocumentString);
    }
}

// Run xformsPageLoaded when the browser has finished loading the page
YAHOO.util.Event.addListener(window, "load", xformsPageLoaded);
document.xformsTime = new Date().getTime();