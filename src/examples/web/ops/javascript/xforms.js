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
 * Constants
 */
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
var BASE_URL = null;
var XFORMS_SERVER_URL = null;
var PATH_TO_JAVASCRIPT = "/ops/javascript/xforms.js";
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var ATTRIBUTE_TYPE = document.createAttribute("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;
var XFORMS_DEBUG_WINDOW_HEIGHT = 600;
var XFORMS_DEBUG_WINDOW_WIDTH = 300;
var XFORMS_ONE_REQUEST_FOR_EVENTS_IN_MS = 100;
var REPEAT_HIERARCHY_SEPARATOR_1 = "\xB7";
var REPEAT_HIERARCHY_SEPARATOR_2 = "-";

/* * * * * * Utility functions * * * * * */

function xformsIsDefined(thing) {
    return typeof thing != "undefined";
}

/**
* Create an element with a namespace. Offers the same functionality
* as the DOM2 Document.createElementNS method.
*/
function xformsCreateElementNS(namespaceURI, qname) {
    var localName = qname.indexOf(":") == -1 ? "" : ":" + qname.substr(0, qname.indexOf(":"));
    var request = Sarissa.getDomDocument();
    request.loadXML("<" + qname + " xmlns" + localName + "='" + namespaceURI + "'/>");
    return request.documentElement;
}

function xformsAddEventListener(target, eventName, handler) {
    if (target.addEventListener) {
        target.addEventListener(eventName, handler, false);
    } else {
        target.attachEvent("on" + eventName, handler);
    }
}

function xformsRemoveEventListener(target, eventName, handler) {
    if (target.removeEventListener) {
        target.removeEventListener(eventName, handler, false);
    } else {
        target.detachEvent("on" + eventName, handler);
    }
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

function xformsArrayContains(array, element) {
    for (var i = 0; i < array.length; i++)
        if (array[i] == element)
            return true;
    return false;
}

function xformsRemoveClass(element, classToRemove) {
    var array = element.className.split(" ");
    var newClassName = "";
    for (var i in array) {
        var currentClass = array[i];
        if (currentClass != classToRemove) {
            if (newClassName != "") newClassName += " ";
            newClassName += currentClass;
        }
    }
    element.className = newClassName;
}

function xformsAddClass(element, classToAdd) {
    var array = element.className.split(" ");
    // Check if the class to add is already present
    var classAlreadyThere = false;
    for (var i in array) {
        var currentClass = array[i];
        if (currentClass == classToAdd) {
            classAlreadyThere = true;
            break;
        }
    }
    // Add new class if necessary
    if (!classAlreadyThere) {
        var newClassName = element.className;
        if (newClassName != "") newClassName += " ";
        newClassName += classToAdd;
        element.className = newClassName;
    }
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
    return text.replace(new RegExp("\\r", "g"), "");
}

function xformsAppendRepeatSuffix(id, suffix) {
    if (suffix == "")
        return id;
    if (suffix.charAt(0) == REPEAT_HIERARCHY_SEPARATOR_2)
        suffix = suffix.substring(1);
    if (id.indexOf(REPEAT_HIERARCHY_SEPARATOR_1) == -1)
        return id + REPEAT_HIERARCHY_SEPARATOR_1 + suffix;
    else
        return id + REPEAT_HIERARCHY_SEPARATOR_2 + suffix;
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
            parentRepeatIndexes = (grandParent == null ? REPEAT_HIERARCHY_SEPARATOR_1 : REPEAT_HIERARCHY_SEPARATOR_2)
                    + document.xformsRepeatIndexes[parent] + parentRepeatIndexes;
            currentId = parent;
        }
    }

    var beginElementId = "repeat-begin-" + repeatId + parentRepeatIndexes;
    var beginElement = document.getElementById(beginElementId);
    if (! beginElement) throw "Cannot find element with id '" + beginElementId + "'";
    var cursor = beginElement;
    var cursorPosition = 0;
    while (true) {
        while (cursor.nodeType != ELEMENT_TYPE || !xformsArrayContains(cursor.className.split(" "), "xforms-repeat-delimiter")) {
            cursor = cursor.nextSibling;
            if (!cursor)
                throw "Cannot find element #" + index + " with class 'xforms-repeat-delimiter' after element with id '"
                        + beginElementId + "'";
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
        xformsAddEventListener(clear, "click", function (event) {
            var target = getEventTarget(event);
            alert("click");
            while (target.nextSibling)
                target.parentNode.removeChild(target.nextSibling);
            return false;
        });

        // Make it so user can move the debug window
        xformsAddEventListener(debugDiv, "mousedown", function (event) {
            document.xformsDebugDiv = getEventTarget(event);
            return false;
        });
        xformsAddEventListener(document, "mouseup", function (event) {
            document.xformsDebugDiv = null;
            return false;
        });
        xformsAddEventListener(document, "mousemove", function (event) {
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

function xformsDisplayLoading(state) {
    switch (state) {
        case "loading" :
            if (document.xformsLoadingLoading != null)
                document.xformsLoadingLoading.style.display = "block";
            if (document.xformsLoadingError != null)
                document.xformsLoadingError.style.display = "none";
            if (document.xformsLoadingNone != null)
                document.xformsLoadingNone.style.display = "none";
            break;
        case "error":
            if (document.xformsLoadingLoading != null)
                document.xformsLoadingLoading.style.display = "none";
            if (document.xformsLoadingError != null)
                document.xformsLoadingError.style.display = "block";
            if (document.xformsLoadingNone != null)
                document.xformsLoadingNone.style.display = "none";
            break;
        case "none":
            if (document.xformsLoadingLoading != null)
                document.xformsLoadingLoading.style.display = "none";
            if (document.xformsLoadingError != null)
                document.xformsLoadingError.style.display = "none";
            if (document.xformsLoadingNone != null)
                document.xformsLoadingNone.style.display = "block";
            break;
    }
}

// Gets a value stored in the hidden client-state input field
function xformsGetFromClientState(key) {
    var keyValues = document.xformsClientState.value.split("&");
    for (var i = 0; i < keyValues.length; i = i + 2)
        if (keyValues[i] == key)
            return unescape(keyValues[i + 1]);
    return null;
}

// Returns a value stored in the hidden client-state input field
function xformsStoreInClientState(key, value) {
    var keyValues = document.xformsClientState.value == ""? new Array() : document.xformsClientState.value.split("&");
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
    document.xformsClientState.value = keyValues.join("&");
}

function xformsValueChanged(target, other) {
    var valueChanged = target.value != target.previousValue;
    if (valueChanged) {
        target.previousValue = target.value;
        document.xformsChangedIdsRequest.push(target.id);
        xformsFireEvents(new Array(xformsCreateEventArray
                (target, "xxforms-value-change-with-focus-change", target.value, other)));
    }
    return valueChanged;
}

// Function called by browser when value changes (either after changing focus or keyboard input)
function xformsHandleValueChange(event) {
    var target = getEventTarget(event);
    // If this is an input field, set value on parent and send event on parent element
    if (xformsArrayContains(target.parentNode.className.split(" "), "xforms-input")) {
        target.parentNode.valueSetByXForms++;
        target.parentNode.value = target.value;
        target = target.parentNode;
    }
    xformsValueChanged(target, null);
    return true;
}

// Handle click on trigger
function xformsHandleClick(event) {
    var target = getEventTarget(event);
    // Make sure the user really clicked on the trigger, instead of pressing enter in a nearby control
    var targetClasses = target.className.split(" ")
    if (xformsArrayContains(targetClasses, "xforms-trigger") && !xformsArrayContains(targetClasses, "xforms-readonly"))
        xformsFireEvents(new Array(xformsCreateEventArray(target, "DOMActivate", null)));
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
        select.selectValue)));
}

function xformsHandleRangeMouseDown(event) {
    document.xformsCurrentRangeControl = getEventTarget(event).parentNode;
    return false;
}

function xformsHandleRangeMouseUp(event) {
    document.xformsCurrentRangeControl = null;
    return false;
}

function xformsHandleRangeMouseMove(event) {
    var rangeControl = document.xformsCurrentRangeControl;
    if (rangeControl) {
        // Compute range boundaries
        var rangeStart = xformsGetElementPosition(rangeControl.track).left;
        var rangeLength = rangeControl.track.clientWidth - rangeControl.slider.clientWidth;

        // Compute value
        var value = (event.clientX - rangeStart) / rangeLength;
        if (value < 0) value = 0;
        if (value > 1) value = 1;

        // Compute slider position
        var sliderPosition = event.clientX - rangeStart;
        if (sliderPosition < 0) sliderPosition = 0;
        if (sliderPosition > rangeLength) sliderPosition = rangeLength;
        rangeControl.slider.style.left = sliderPosition;

        // Notify server that value changed
        rangeControl.value = value;
        xformsValueChanged(rangeControl, null);
    }

    return false;
}

function xformsHandleOutputClick(event) {
    var events = new Array();
    var target = getEventTarget(event);
    // In the case of appearance="xxforms:html, target can be an element inside
    // the xforms-output, not the xforms-output itself. So we are here looking for the
    // xforms-parent when necessary.
    while (!xformsArrayContains(target.className.split(" "), "xforms-output"))
        target = target.parentNode;
    events.push(xformsCreateEventArray(target, "DOMFocusIn", null));
    xformsFireEvents(events);
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
        xformsFireEvents(new Array(xformsCreateEventArray(span, "DOMActivate", null)));
        // Prevent default handling of enter, which might be equivalent as a click on some trigger in the form
        if (event.preventDefault) {
            // Firefox version
            event.preventDefault();
        } else {
            // IE version
            return false;
        }
    }
}

// Focus out events are only handled when we have receive a focus in event.
// Here we just save the event which will be handled when we receive a focus
// in from the browser.
function xformsHandleBlur(event) {
    if (!document.xformsMaskFocusEvents) {
        var target = getEventTarget(event);
        while (target && (target.className == null || !xformsArrayContains(target.className.split(" "), "xforms-control")))
            target = target.parentNode;
        if (target != null)
            document.xformsPreviousDOMFocusOut = target;
    }
}

function xformsHandleFocus(event) {
    if (!document.xformsMaskFocusEvents) {
        var target = getEventTarget(event);
        while (target && (target.className == null || !xformsArrayContains(target.className.split(" "), "xforms-control")))
            target = target.parentNode;
        var sendFocusEvents = target != null;

        // We have just received a change event: try to combine both
        if (sendFocusEvents && document.xformsPreviousValueChanged) {
            var eventSent = xformsValueChanged(document.xformsPreviousValueChanged, target);
            document.xformsPreviousValueChanged = null;
            if (eventSent)
                document.xformsPreviousDOMFocusOut = null;
            // If value changed didn't send anything, we still want to send the focus events
            sendFocusEvents = !eventSent;
        }

        // Send focus events
        if (sendFocusEvents) {
            if (document.xformsPreviousDOMFocusOut) {
                if (document.xformsPreviousDOMFocusOut != target) {
                    var events = new Array();
                    events.push(xformsCreateEventArray
                        (document.xformsPreviousDOMFocusOut, "DOMFocusOut", null));
                    events.push(xformsCreateEventArray(target, "DOMFocusIn", null));
                    xformsFireEvents(events);
                }
                document.xformsPreviousDOMFocusOut = null;
            } else {
                if (document.xformsPreviousDOMFocusIn != target) {
                    xformsFireEvents(new Array(xformsCreateEventArray(target, "DOMFocusIn", null)));
                }
            }
        }
        document.xformsPreviousDOMFocusIn = target;
    } else {
        document.xformsMaskFocusEvents = false;
    }
}

// Register listener on focus in and out events
function xformsRegisterForFocusBlurEvents(control) {
    if (!control.focusBlurEventListenerRegistered) {
        control.focusBlurEventListenerRegistered = true;
        xformsAddEventListener(control, "blur", xformsHandleBlur);
        xformsAddEventListener(control, "focus", xformsHandleFocus);
    }
}


// Parameter is an array of arrays with each array containing:
// new Array(target, eventName, value, incremental, other)
function xformsFireEvents(events) {

    // Store events to fire
    for (var eventIndex = 0; eventIndex < events.length; eventIndex++)
        document.xformsEvents.push(events[eventIndex]);

    // Fire them with a delay to give us a change to aggregate events together
    window.setTimeout(xformsExecuteNextRequest, XFORMS_ONE_REQUEST_FOR_EVENTS_IN_MS);
    
    return false;
}

function xformsCreateEventArray(target, eventName, value, other) {
    return new Array(target, eventName, value, other);
}

function getEventTarget(event) {
    event = event ? event : window.event;
    return event.srcElement ? event.srcElement : event.target;
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
            var spanClasses = span.className.split(" ");
            if (xformsArrayContains(spanClasses, "xforms-select-full")
                    || xformsArrayContains(spanClasses, "xforms-select1-full"))
                break;
            span = span.parentNode;
        }
        xformsInitCheckesRadiosComputeSpanValue(span);
        xformsFireEvents(new Array(xformsCreateEventArray
                (span, "xxforms-value-change-with-focus-change", span.value, null)));
    }

    // Register event listener on every checkbox
    var inputs = control.getElementsByTagName("input");
    for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++)
        xformsAddEventListener(inputs[inputIndex], "click", inputSelected);

    // Compute the checkes value for the first time
    xformsInitCheckesRadiosComputeSpanValue(control);
}

/**
 * Initializes attributes on the document object:
 *
 *     Form            xformsForm
 *     Array[]         xformsEvents
 *     Div             xformsLoadingLoading
 *     Div             xformsLoadingError
 *     Div             xformsLoadingNone
 *     Input           xformsStaticState
 *     Input           xformsDynamicState
 *     boolean         xformsRequestInProgress
 *     XMLHttpRequest  xformsXMLHttpRequest
 *     Element         xformsPreviousValueChanged
 *     Array           xformsChangedIdsRequest        Ids of controls changed while request is processed by server
 *     Array[id]->id   xformsRepeatTreeChildToParent  Returns the direct parent for each repeat id (or null if no parent)
 *     Array[id]->#    xformsRepeatIndexes            Current index for each repeat id
 */
function xformsInitializeControlsUnder(root) {

    // Gather all potential form controls
    var interestingTagNames = new Array("span", "button", "textarea", "input", "a", "label", "select", "td", "table", "div");
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
        var isXFormsTrigger = false;
        var isXFormsOutput = false;
        var isXFormsInput = false;
        var isXFormsDate = false;
        var isWidget = false;
        var isXFormsRange = false;
        for (var classIndex = 0; classIndex < classes.length; classIndex++) {
            var className = classes[classIndex];
            if (className.indexOf("xforms-") == 0)
                isXFormsElement = true;
            if (className.indexOf("xforms-alert") == 0)
                isXFormsAlert = true;
            if (className == "xforms-incremental")
                isIncremental = true;
            if (className == "xforms-range-casing")
                isXFormsRange = true;
            if (className == "xforms-select-full" || className == "xforms-select1-full")
                isXFormsCheckboxRadio = true;
            if (className == "xforms-select-compact" || className == "xforms-select1-minimal")
                isXFormsComboboxList = true;
            if (className == "xforms-trigger")
                isXFormsTrigger = true;
            if (className == "xforms-output")
                isXFormsOutput = true;
            if (className == "xforms-input")
                isXFormsInput = true;
            if (className.indexOf("widget-") != -1)
                isWidget = true;
        }

        if (isWidget && !isXFormsElement) {
            // For widget: just add style
            xformsUpdateStyle(control);
        }

        if (isXFormsElement) {
            xformsElementCount++;
            if (isXFormsTrigger) {
                // Handle click on trigger
                control.onclick = xformsHandleClick;
            } else if (isXFormsCheckboxRadio) {
                xformsInitCheckesRadios(control);
            } else if (isXFormsComboboxList) {
                // Register event listener on select
                xformsAddEventListener(control, "change", xformsHandleSelectChanged);
                // Compute the checkes value for the first time
                xformsComputeSelectValue(control);
            } else if (isXFormsRange) {
            
                if (!control.listenersRegistered) {
                    control.listenersRegistered = true;
                    control.mouseDown = false;
                    for (var childIndex = 0; childIndex < control.childNodes.length; childIndex++) {
                        var child = control.childNodes[childIndex];
                        if (child.className 
                                && xformsArrayContains(child.className.split(" "), "xforms-range-track"))
                            control.track = child;
                        if (child.className 
                                && xformsArrayContains(child.className.split(" "), "xforms-range-slider"))
                            control.slider = child;
                    }
                    xformsAddEventListener(control.slider, "mousedown", xformsHandleRangeMouseDown);
                }
                
                if (!document.xformsRangeListenerRegistered) {
                    document.xformsRangeListenerRegistered = true;
                    xformsAddEventListener(document, "mousemove", xformsHandleRangeMouseMove);
                    xformsAddEventListener(document, "mouseup", xformsHandleRangeMouseUp);
                }
            } else if (isXFormsOutput) {
                xformsAddEventListener(control, "click", xformsHandleOutputClick);
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
                    xformsAddEventListener(control, "propertychange", xformsHandleIEValueChange);
                }
                // Intercept end-user pressing enter in text field
                xformsAddEventListener(textfield, "keypress", xformsHandleInputKeyPress);
                // Intercept incremental modifications
                if (isIncremental)
                    xformsAddEventListener(textfield, "keyup", xformsHandleValueChange);
            } else if (control.tagName == "SPAN" || control.tagName == "DIV" || control.tagName == "LABEL") {
                // Don't add listeners on spans
            } else {
                // Handle value change and incremental modification
                control.previousValue = control.value;
                control.userModications = false;
                // Register listeners
                xformsAddEventListener(control, "change", xformsHandleValueChange);
                if (isIncremental) {
                    xformsAddEventListener(control, "keyup", xformsHandleValueChange);
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
                var isActive = xformsArrayContains(control.className.split(" "), "xforms-alert-active");

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

            // Set initial values for disabled, readonly, and required
            if (xformsArrayContains(classes, "xforms-control")) {
                control.isRelevant = !xformsArrayContains(classes, "xforms-disabled");
                control.isReadonly = xformsArrayContains(classes, "xforms-readonly");
                control.isRequired = xformsArrayContains(classes, "xforms-required");
            }

            // Add style to element
            xformsUpdateStyle(control);
        }
    }
}

function xformsPageLoaded() {

    xformsLogTime("Start xformsPageLoaded");
    window.start = new Date().getTime();
    if (document.getElementById("xforms-form")) {

        // Initialize tooltip library
        tt_init();

        // Initialize XForms server URL
        var scripts = document.getElementsByTagName("script");
        for (var scriptIndex = 0; scriptIndex < scripts.length; scriptIndex++) {
            var script = scripts[scriptIndex];
            var startPathToJavaScript = script.getAttribute("src").indexOf(PATH_TO_JAVASCRIPT);
            if (startPathToJavaScript != -1) {
                BASE_URL = script.getAttribute("src").substr(0, startPathToJavaScript);
                XFORMS_SERVER_URL = BASE_URL + "/xforms-server";
                break;
            }
        }

        // Initialize attributes on document
        document.xformsRequestInProgress = false;
        document.xformsEvents = new Array();
        document.xformsChangedIdsRequest = new Array();

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
            if (xformsArrayContains(form.className.split(" "), "xforms-form")) {
                // This is a XForms form
                document.xformsForm = form;
                document.xformsLoadingLoading = null;
                document.xformsLoadingError = null;
                document.xformsLoadingNone = null;
                var spans = form.getElementsByTagName("span");
                for (var spanIndex = 0; spanIndex < spans.length; spanIndex++) {
                    if (spans[spanIndex].className == "xforms-loading-loading")
                        document.xformsLoadingLoading = spans[spanIndex];
                    if (spans[spanIndex].className == "xforms-loading-error")
                        document.xformsLoadingError = spans[spanIndex];
                    if (spans[spanIndex].className == "xforms-loading-none")
                        document.xformsLoadingNone = spans[spanIndex];
                }
                var elements = form.elements;
                for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
                    var element = elements[elementIndex];
                    if (element.name) {
                        if (element.name.indexOf("$static-state") != -1)
                            document.xformsStaticState = element;
                        if (element.name.indexOf("$dynamic-state") != -1) {
                            document.xformsDynamicState = element;
                        }
                        if (element.name.indexOf("$client-state") != -1) {
                            document.xformsClientState = element;
                            if (element.value == "")
                                xformsStoreInClientState("ajax-dynamic-state", document.xformsDynamicState.value);
                        }
                    }
                }
            }
        }

        // Parse and store initial repeat hierarchy
        document.xformsRepeatTreeChildToParent = new Array();
        var repeatTreeString = xformsStringValue(document.getElementById("xforms-repeat-tree"));
        var repeatTree = repeatTreeString.split(",");
        for (var repeatIndex = 0; repeatIndex < repeatTree.length; repeatIndex++) {
            var repeatInfo = repeatTree[repeatIndex].split(" ");
            var id = repeatInfo[0];
            var parent = repeatInfo.length > 1 ? repeatInfo[1] : null;
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
        var repeatIndexesString = xformsStringValue(document.getElementById("xforms-repeat-indexes"));
        repeatIndexesString = repeatIndexesString.split("\n").join(""); // Remove \n added by serializer

        var repeatIndexes = repeatIndexesString.split(",");
        for (var repeatIndex = 0; repeatIndex < repeatIndexes.length; repeatIndex++) {
            var repeatInfo = repeatIndexes[repeatIndex].split(" ");
            var id = repeatInfo[0];
            var index = repeatInfo[repeatInfo.length - 1];
            document.xformsRepeatIndexes[id] = index;
        }

        // Ask server to resend events if this is not the first time load is called
        if (xformsGetFromClientState("load-did-run") == null) {
            xformsStoreInClientState("load-did-run", "true");
        } else {
            xformsFireEvents(new Array(xformsCreateEventArray(null, "xxforms-all-events-required", null, null)));
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
         idSuffixWithDepth += REPEAT_HIERARCHY_SEPARATOR_2 + "1";
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

function xformsHandleResponse() {
    if (document.xformsXMLHttpRequest.readyState == 4) {
        var responseXML = document.xformsXMLHttpRequest.responseXML;
        if (responseXML && responseXML.documentElement
                && responseXML.documentElement.tagName.indexOf("event-response") != -1) {

            // Good: we received an XML document from the server
            var responseRoot = responseXML.documentElement;
            var newDynamicState = null;
            var newDynamicStateTriggersPost = false;
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
                                                         && xformsArrayContains(templateNode.className.split(" "), "xforms-repeat-delimiter"))) {
                                                    var nodeCopy = templateNode.cloneNode(true);
                                                    if (templateNode.nodeType == ELEMENT_TYPE) {
                                                        // Save tag name to be used for delimiter
                                                        delimiterTagName = templateNode.tagName;
                                                        // Decrement nestedRepeatLevel when we we exit a nested repeat
                                                        if (xformsArrayContains(templateNode.className.split(" "), "xforms-repeat-begin-end") &&
                                                                templateNode.id.indexOf("repeat-begin-") == 0)
                                                            nestedRepeatLevel--;
                                                        // Add suffix to all the ids
                                                        xformsAddSuffixToIds(nodeCopy, parentIndexes == "" ? idSuffix : parentIndexes + REPEAT_HIERARCHY_SEPARATOR_2 + idSuffix, nestedRepeatLevel);
                                                        // Increment nestedRepeatLevel when we enter a nested repeat
                                                        if (xformsArrayContains(templateNode.className.split(" "), "xforms-repeat-begin-end") &&
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
                                                            && xformsArrayContains(cursor.className.split(" "), "xforms-repeat-delimiter")
                                                            && !xformsArrayContains(cursor.className.split(" "), "xforms-repeat-template"))) {
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
                                                            || !xformsArrayContains(lastElementToDelete.className.split(" "), "xforms-repeat-delimiter"))
                                                        lastElementToDelete = lastElementToDelete.previousSibling;
                                                    lastElementToDelete = lastElementToDelete.previousSibling;
                                                }
                                            }
                                            // Perform delete
                                            for (var countIndex = 0; countIndex < count; countIndex++) {
                                                while (true) {
                                                    var wasDelimiter = lastElementToDelete.nodeType == ELEMENT_TYPE
                                                        && xformsArrayContains(lastElementToDelete.className.split(" "), "xforms-repeat-delimiter");
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

                                    if (documentElement.tagName == "SELECT") {

                                        // Case of list / combobox
                                        var options = documentElement.options;

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
                                                } else {
                                                    // Replace current label/value if necessary
                                                    var option = options[itemCount];
                                                    if (option.text != itemElement.getAttribute("label"))
                                                        option.text = itemElement.getAttribute("label");
                                                    if (option.value != itemElement.getAttribute("value"))
                                                        option.value = itemElement.getAttribute("value");
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
                                                xformsLog(controlId);
                                            var documentElementClasses = documentElement.className.split(" ");

                                            // Check if this control was modified and we haven't even received the key event yet
                                            // This can happen as the the keyup event is dispatched after the control.value is modified,
                                            // and it is possible to receive a response from the server after the value is modified but
                                            // before the keyup event is dispatched.
                                            var foundControlModified = false;
                                            if (xformsArrayContains(documentElement.className.split(" "), "xforms-input")) {
                                                if (documentElement.childNodes[1].value != documentElement.previousValue)
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
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-trigger")) {
                                                // Triggers don't have a value: don't update them
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-select-full")
                                                    || xformsArrayContains(documentElementClasses, "xforms-select1-full")) {
                                                // Handle checkboxes and radio buttons
                                                var selectedValues = xformsArrayContains(documentElementClasses, "xforms-select-full")
                                                    ? newControlValue.split(" ") : new Array(newControlValue);
                                                var checkboxInputs = documentElement.getElementsByTagName("input");
                                                for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                                                    var checkboxInput = checkboxInputs[checkboxInputIndex];
                                                    checkboxInput.checked = xformsArrayContains(selectedValues, checkboxInput.value);
                                                }
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-select-compact")
                                                    || xformsArrayContains(documentElementClasses, "xforms-select1-minimal")) {
                                                // Handle lists and comboboxes
                                                var selectedValues = xformsArrayContains(documentElementClasses, "xforms-select-compact")
                                                    ? newControlValue.split(" ") : new Array(newControlValue);
                                                var options = documentElement.options;
                                                for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                                                    var option = options[optionIndex];
                                                    option.selected = xformsArrayContains(selectedValues, option.value);
                                                }
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-output")) {
                                                // XForms output
                                                var newOutputControlValue = displayValue != null ? displayValue : newControlValue;
                                                if (xformsArrayContains(documentElementClasses, "xforms-output-image")) {
                                                    documentElement.firstChild.src = newOutputControlValue;
                                                } else {
                                                    xformsReplaceNodeText(documentElement, newOutputControlValue);
                                                }
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-input")) {
                                                var displayField = documentElement.childNodes[0];
                                                var inputField = documentElement.childNodes[1];
                                                var datePicker = documentElement.childNodes[2];

                                                // Populate values
                                                if (xformsArrayContains(inputField.className.split(" "), "xforms-type-date")) 
                                                    xformsReplaceNodeText(displayField, displayValue == null ? "" : displayValue);
                                                if (documentElement.value != newControlValue) {
                                                    documentElement.previousValue = newControlValue;
                                                    documentElement.valueSetByXForms++;
                                                    documentElement.value = newControlValue;
                                                }
                                                if (inputField.value != newControlValue)
                                                    inputField.value = newControlValue;

                                                // Change classes on control and date pick based on type
                                                if (type == "{http://www.w3.org/2001/XMLSchema}date") {
                                                    for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
                                                        var child = documentElement.childNodes[childIndex];
                                                        xformsAddClass(child, "xforms-type-date");
                                                        xformsRemoveClass(child, "xforms-type-string");
                                                    }
                                                } else if (type != null && type != "{http://www.w3.org/2001/XMLSchema}date") {
                                                    for (var childIndex = 0; childIndex < documentElement.childNodes.length; childIndex++) {
                                                        var child = documentElement.childNodes[childIndex];
                                                        xformsAddClass(child, "xforms-type-string");
                                                        xformsRemoveClass(child, "xforms-type-date");
                                                    }
                                                }
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-control")
                                                    && typeof(documentElement.value) == "string") {
                                                // Textarea, password
                                                if (xformsNormalizeEndlines(documentElement.value) != xformsNormalizeEndlines(newControlValue)) {
                                                    documentElement.value = newControlValue;
                                                    documentElement.previousValue = newControlValue;
                                                }
                                            }

                                            // Store new label message in control attribute
                                            var newLabel = controlElement.getAttribute("label");
                                            if (newLabel && newLabel != documentElement.labelMessage)
                                                documentElement.labelMessage = newLabel;
                                            // Store new hint message in control attribute
                                            var newHint = controlElement.getAttribute("hint");
                                            if (newHint && newHint != documentElement.hintMessage)
                                                documentElement.hintMessage = newHint;
                                            // Store new help message in control attribute
                                            var newHelp = controlElement.getAttribute("help");
                                            if (newHelp && newHelp != documentElement.helpMessage)
                                                documentElement.helpMessage = newHelp;
                                            // Store new alert message in control attribute
                                            var newAlert = controlElement.getAttribute("alert");
                                            if (newAlert && newAlert != documentElement.alertMessage)
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
                                            var relevant = repeatIterationElement.getAttribute("relevant") == "true";
                                            // Remove or add xforms-disabled on elements after this delimiter
                                            var cursor = xformsFindRepeatDelimiter(repeatId, iteration).nextSibling;
                                            while (!(cursor.nodeType == ELEMENT_TYPE &&
                                                     (xformsArrayContains(cursor.className.split(" "), "xforms-repeat-delimiter")
                                                        || xformsArrayContains(cursor.className.split(" "), "xforms-repeat-begin-end")))) {
                                                if (cursor.nodeType == ELEMENT_TYPE) {
                                                    if (relevant) xformsRemoveClass(cursor, "xforms-disabled");
                                                    else xformsAddClass(cursor, "xforms-disabled");
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
                                                xformsAddClass(cursor, visibile ? "xforms-case-selected" : "xforms-case-deselected");
                                                xformsRemoveClass(cursor, visibile ? "xforms-case-deselected" : "xforms-case-selected");
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
                                        var repeatId = repeatIndexElement.getAttribute("id");
                                        var newIndex = repeatIndexElement.getAttribute("new-index");
                                        newRepeatIndexes[repeatId] = newIndex;
                                    }
                                }
                                // For each repeat id that changes, see if all the children are also included in
                                // newRepeatIndexes. If they are not, add an entry with the index unchanged.
                                var newRepeatIndexesIgnoreHack = new Array(); // HACK
                                for (var repeatId in newRepeatIndexes) {
                                    var children = document.xformsRepeatTreeParentToAllChildren[repeatId];
                                    var parentIndex = document.xformsRepeatIndexes[repeatId]; // HACK
                                    for (var childIndex in children) {
                                        var child = children[childIndex];
                                        if (!newRepeatIndexes[child]) {
                                            newRepeatIndexes[child] = document.xformsRepeatIndexes[child];
                                            if (parentIndex == 0) // HACK
                                                newRepeatIndexesIgnoreHack[child] = true; // HACK
                                        }
                                    }
                                }
                                // Unhighlight items at old indexes
                                for (var repeatId in newRepeatIndexes) {
                                    var oldIndex = document.xformsRepeatIndexes[repeatId];
                                    if (oldIndex != 0 
                                            && !newRepeatIndexesIgnoreHack[repeatId]) {  // HACK
                                        var oldItemDelimiter = xformsFindRepeatDelimiter(repeatId, oldIndex);
                                        if (oldItemDelimiter != null) {
                                            cursor = oldItemDelimiter.nextSibling;
                                            while (cursor.nodeType != ELEMENT_TYPE ||
                                                   (!xformsArrayContains(cursor.className.split(" "), "xforms-repeat-delimiter")
                                                   && !xformsArrayContains(cursor.className.split(" "), "xforms-repeat-begin-end"))) {
                                                if (cursor.nodeType == ELEMENT_TYPE)
                                                    xformsRemoveClass(cursor, xformsGetClassForReapeatId(repeatId));
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
                                        cursor = newItemDelimiter.nextSibling;
                                        while (cursor.nodeType != ELEMENT_TYPE ||
                                               (!xformsArrayContains(cursor.className.split(" "), "xforms-repeat-delimiter")
                                               && !xformsArrayContains(cursor.className.split(" "), "xforms-repeat-begin-end"))) {
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
                                    document.xformsDynamicState.value = newDynamicState;
                                    document.xformsForm.submit();
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
                                    if (target == null)  window.location.href = resource;
                                    else window.open(resource, target);
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
                                if (xformsArrayContains(control.className.split(" "), "xforms-input"))
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
                xformsStoreInClientState("ajax-dynamic-state", newDynamicState);
            }

            xformsDisplayLoading("none");

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
            var errorContainer = document.xformsLoadingError;
            xformsReplaceNodeText(errorContainer, errorMessage);
            xformsDisplayLoading("error");
        } else {
            // The server didn't send valid XML
            document.xformsLoadingError.innerHTML = "Unexpected response received from server";
            xformsDisplayLoading("error");
            
        }

        // Go ahead with next request, if any
        document.xformsRequestInProgress = false;
        xformsExecuteNextRequest();
    }
}

function xformsExecuteNextRequest() {
    if (! document.xformsRequestInProgress && document.xformsEvents.length > 0) {

        // Mark this as loading
        document.xformsRequestInProgress = true;
        xformsDisplayLoading("loading");

        // Build request
        var requestDocument;
        {
            var eventFiredElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event-request");

            // Add static state
            var staticStateElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:static-state");
            staticStateElement.appendChild(staticStateElement.ownerDocument.createTextNode(document.xformsStaticState.value));
            eventFiredElement.appendChild(staticStateElement);

            // Add dynamic state (element is just created and will be filled just before we send the request)
            var dynamicStateElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:dynamic-state");
            dynamicStateElement.appendChild(dynamicStateElement.ownerDocument.createTextNode(xformsGetFromClientState("ajax-dynamic-state")));
            eventFiredElement.appendChild(dynamicStateElement);

            // Add action
            var actionElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:action");
            eventFiredElement.appendChild(actionElement);

            // Add event
            for (var i = 0; i < document.xformsEvents.length; i++) {
                // Extract information from event array
                var event = document.xformsEvents[i];
                var target = event[0];
                var eventName = event[1];
                var value = event[2];
                var other = event[3];
                // Create <xxforms:event> element
                var eventElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event");
                actionElement.appendChild(eventElement);
                eventElement.setAttribute("name", eventName);
                if (target != null)
                    eventElement.setAttribute("source-control-id", target.id);
                if (other != null)
                    eventElement.setAttribute("other-control-id", other.id);
                if (value != null)
                    eventElement.appendChild(eventElement.ownerDocument.createTextNode(value));
            }
            document.xformsEvents = new Array();

            // Reset changes, as changes are included in this bach of events
            document.xformsChangedIdsRequest = new Array();

            requestDocument = eventFiredElement.ownerDocument;
        }

        // Send request
        document.xformsXMLHttpRequest = new XMLHttpRequest();
        document.xformsXMLHttpRequest.open("POST", XFORMS_SERVER_URL, true);
        document.xformsXMLHttpRequest.onreadystatechange = xformsHandleResponse;
        document.xformsXMLHttpRequest.send(requestDocument);
    }
}

// Run xformsPageLoaded when the browser has finished loading the page
xformsAddEventListener(window, "load", xformsPageLoaded);
document.xformsTime = new Date().getTime();