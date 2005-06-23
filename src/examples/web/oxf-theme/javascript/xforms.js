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
var PATH_TO_JAVASCRIPT = "/oxf-theme/javascript/xforms.js";
var ELEMENT_TYPE = document.createElement("dummy").nodeType;
var ATTRIBUTE_TYPE = document.createAttribute("dummy").nodeType;
var TEXT_TYPE = document.createTextNode("").nodeType;

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
        var event = document.createEvent("MouseEvent");
        event.initEvent("change", 0, 0);
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

function xformsArrayRemove(array, element) {
    // Look for position of element to remove
    var elementIndex = -1;
    for (var i = 0; i < array.length; i++)
        if (array[i] == element)
            elementIndex = i;
    // Remove element if found
    if (elementIndex != -1) {
        // If the element is not the last one, save the last one at the position of the element to remove
        if (elementIndex < array.length - 1)
            array[elementIndex] = array[array.length - 1];
        array.pop();
    }
    return array;
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

/**
 * Replace in a tree a placeholder by some other string in text nodes and attribute values
 */
function xformsStringReplace(node, placeholder, replacement) {

    var placeholderRegExp = new RegExp(placeholder.replace(new RegExp("\\$", "g"), "\\$"), "g");

    function xformsStringReplaceWorker(node) {

        function replace(text) {
            var stringText = new String(text);
            return stringText.replace(placeholderRegExp, replacement);
        }

        switch (node.nodeType) {
            case ELEMENT_TYPE:
                for (var i = 0; i < node.attributes.length; i++) {
                    var newValue = replace(node.attributes[i].value);
                    if (newValue != node.attributes[i].value)
                        node.setAttribute(node.attributes[i].name, newValue);
                }
                for (var i = 0; i < node.childNodes.length; i++)
                    xformsStringReplaceWorker(node.childNodes[i]);
                break;
            case TEXT_TYPE:
                var newValue = replace(node.nodeValue);
                if (newValue != node.nodeValue)
                    node.nodeValue = newValue;
                break;
        }
    }
    
    xformsStringReplaceWorker(node);
}

/**
 * Locate the delimiter at the given position starting from a repeat begin element.
 */
function xformsFindRepeatDelimiter(beginElement, index) {
    var cursor = beginElement;
    var cursorPosition = 0;
    while (true) {
        while (cursor.nodeType != ELEMENT_TYPE || cursor.className != "xforms-repeat-delimiter")
            cursor = cursor.nextSibling;
        cursorPosition++;
        if (cursorPosition == index) break;
        cursor = cursor.nextSibling;
    }
    return cursor;
}

function log(text) {
    document.getElementById("debug").innerHTML += text + " | ";
}

function xformsDisplayLoading(form, state) {
    switch (state) {
        case "loading" :
            form.xformsLoadingLoading.style.display = "block";
            form.xformsLoadingError.style.display = "none";
            form.xformsLoadingNone.style.display = "none";
            break;
        case "error":
            form.xformsLoadingLoading.style.display = "none";
            form.xformsLoadingError.style.display = "block";
            form.xformsLoadingNone.style.display = "none";
            break;
        case "none":
            form.xformsLoadingLoading.style.display = "none";
            form.xformsLoadingError.style.display = "none";
            form.xformsLoadingNone.style.display = "block";
            break;
    }
}

function xformsValueChanged(target, incremental, other) {
    if (target.value != target.previousValue) {
        target.previousValue = target.value;
        xformsFireEvent(target, "xxforms-value-change-with-focus-change", target.value, incremental, other);
    }
}

function xformsFireEvent(target, eventName, value, incremental, other) {

    // Build request
    var eventFiredElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event-request");
    
    // Add static state
    var staticStateElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:static-state");
    staticStateElement.appendChild(staticStateElement.ownerDocument.createTextNode(target.form.xformsStaticState.value));
    eventFiredElement.appendChild(staticStateElement);
    
    // Add dynamic state (element is just created and will be filled just before we send the request)
    var dynamicStateElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:dynamic-state");
    eventFiredElement.appendChild(dynamicStateElement);

    // Add action
    var actionElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:action");
    eventFiredElement.appendChild(actionElement);

    // Add event
    var eventElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event");
    actionElement.appendChild(eventElement);
    eventElement.setAttribute("name", eventName);
    eventElement.setAttribute("source-control-id", target.id);
    if (other != null)
        eventElement.setAttribute("other-control-id", other.id);
    if (value != null)
        eventElement.appendChild(eventElement.ownerDocument.createTextNode(value));

    // If last request added to queue is incremental, remove it    
    if (document.xformsLastRequestIsIncremental && document.xformsNextRequests.length > 0) {
        document.xformsNextRequests.pop();
        document.xformsNextTargets.pop();
    }

    // Set as next request to execute and trigger execution
    document.xformsLastRequestIsIncremental = incremental;
    document.xformsNextRequests.push(eventFiredElement.ownerDocument);
    document.xformsNextTargets.push(target);
    xformsExecuteNextRequest();
    
    return false;
}

function getEventTarget(event) {
    event = event ? event : window.event;
    return event.srcElement ? event.srcElement : event.target;
}

function xformsInitCheckesRadios(control) {
    function computeSpanValue(span) {
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
        computeSpanValue(span);
        xformsFireEvent(span, "xxforms-value-change-with-focus-change", span.value, false);
    }

    // Register event listener on every checkbox
    var inputs = control.getElementsByTagName("input");
    for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++)
        xformsAddEventListener(inputs[inputIndex], "click", inputSelected);

    // Find parent form and store this in span
    var parent = control;
    while (parent.tagName != "FORM")
       parent = parent.parentNode;
    control.form = parent;

    // Compute the checkes value for the first time
    computeSpanValue(control);
}

/**
 * Initializes attributes of each form:
 *
 *     Div              xformsLoadingLoading
 *     Div              xformsLoadingError
 *     Div              xformsLoadingNone
 *     Input            xformsStaticState
 *     Input            xformsDynamicState
 *     Element          xformsCurrentDivs
 *     Array id->index  xformsRepeatIndices
 *
 * Initializes attributes on the document object:
 *
 *     Document[]      xformsNextRequests
 *     boolean         xformsLastRequestIsIncremental
 *     Form            xformsNextTargets
 *     boolean         xformsRequestInProgress
 *     Form            xformsTargetOfCurrentRequest
 *     XMLHttpRequest  xformsXMLHttpRequest
 *     Element         xformsPreviousValueChanged
 *
 */
function xformsInitializeControlsUnder(root) {

    // Gather all potential form controls
    var interestingTagNames = new Array("span", "button", "textarea", "input", "label", "select", "td", "table", "div");
    var formsControls = new Array();
    for (var tagIndex = 0; tagIndex < interestingTagNames.length; tagIndex++) {
        var elements = root.getElementsByTagName(interestingTagNames[tagIndex]);
        for (var elementIndex = 0; elementIndex < elements.length; elementIndex++)
            formsControls = formsControls.concat(elements[elementIndex]);
    }

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
            if (className.indexOf("widget-") != -1)
                isWidget = true;
        }
        
        if (isWidget && !isXFormsElement) {
            // For widget: just add style
            xformsUpdateStyle(control);
        }

        if (isXFormsElement) {

            if (control.tagName == "BUTTON") {
                // Handle click
                xformsAddEventListener(control, "click", function(event) {
                    xformsFireEvent(getEventTarget(event), "DOMActivate", null, false);
                });
            } else if (isXFormsCheckboxRadio) {

                xformsInitCheckesRadios(control);

            } else if (isXFormsComboboxList) {

                var computeSelectValue = function (select) {
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
                };

                var selectChanged = function (event) {
                    var select = getEventTarget(event);
                    computeSelectValue(select);
                    xformsFireEvent(select, "xxforms-value-change-with-focus-change",
                        select.selectValue, false);
                };

                // Register event listener on select
                xformsAddEventListener(control, "change", selectChanged);
                // Compute the checkes value for the first time
                computeSelectValue(control);

            } else if (isXFormsRange) {
            
                var rangeMouseDown = function (event) {
                    document.xformsCurrentRangeControl = getEventTarget(event).parentNode;
                    return false;
                };
                
                var rangeMouseUp = function (event) {
                    document.xformsCurrentRangeControl = null;
                    return false;
                };
                
                var rangeMouseMove = function (event) {
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
                        xformsValueChanged(rangeControl, true);
                    }
                    
                    return false;
                };
                
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
                    xformsAddEventListener(control.slider, "mousedown", rangeMouseDown);

                    // Find parent form and store this in span
                    var parent = control;
                    while (parent.tagName != "FORM")
                       parent = parent.parentNode;
                    control.form = parent;
                }
                
                if (!document.xformsRangeListenerRegistered) {
                    document.xformsRangeListenerRegistered = true;
                    xformsAddEventListener(document, "mousemove", rangeMouseMove);
                    xformsAddEventListener(document, "mouseup", rangeMouseUp);
                }
            
            } else if (control.tagName == "SPAN") {
                // Don't add listeners on spans
            } else {
                // Handle value change and incremental modification
                control.previousValue = null;
                control.userModications = false;
                xformsAddEventListener(control, "change", function(event) {
                    // If previous change is still not handled, handle it now
                    if (document.xformsPreviousValueChanged) {
                        xformsValueChanged(getEventTarget(event), false);
                        document.xformsPreviousValueChanged = null;
                    }
                    // Delay execution by 50 ms
                    document.xformsPreviousValueChanged = getEventTarget(event);
                    window.setTimeout(function() {
                        if (document.xformsPreviousValueChanged) {
                            xformsValueChanged(getEventTarget(event), false);
                            document.xformsPreviousValueChanged = null;
                        }
                    });
                });
                if (isIncremental)
                    xformsAddEventListener(control, "keyup", function(event) {
                        xformsValueChanged(getEventTarget(event), true);
                    });
            }

            if (!control.focusEventListenerRegistered) {
                control.focusEventListenerRegistered = true;
                xformsAddEventListener(control, "focus", function(event) {
                    var target = getEventTarget(event);
                    if (document.xformsPreviousValueChanged) {
                        // We have just received a change event: combine both
                        xformsValueChanged(document.xformsPreviousValueChanged, false, target);
                        document.xformsPreviousValueChanged = null;
                    } else {
                        // TODO: handle just focus change. Waiting for server to handle this.
                    }
                });
            }

            // If alert, store reference in control element to this alert element
            if (isXFormsAlert)
                document.getElementById(control.htmlFor).alertElement = control;

            // Add style to element
            xformsUpdateStyle(control);
        }
    }
}

function xformsPageLoaded() {

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

    // Initialize controls
    xformsInitializeControlsUnder(document);

    // Initialize attributes on form
    var forms = document.getElementsByTagName("form");
    for (var formIndex = 0; formIndex < forms.length; formIndex++) {
        var form = forms[formIndex];
        var spans = form.getElementsByTagName("span");
        for (var spanIndex = 0; spanIndex < spans.length; spanIndex++) {
            if (spans[spanIndex].className == "xforms-loading-loading")
                forms[formIndex].xformsLoadingLoading = spans[spanIndex];
            if (spans[spanIndex].className == "xforms-loading-error")
                forms[formIndex].xformsLoadingError = spans[spanIndex];
            if (spans[spanIndex].className == "xforms-loading-none")
                forms[formIndex].xformsLoadingNone = spans[spanIndex];
        }
        var elements = form.elements;
        for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
            var element = elements[elementIndex];
            if (element.name) {
                if (element.name.indexOf("$static-state") != -1)
                    form.xformsStaticState = element;
                if (element.name.indexOf("$dynamic-state") != -1) {
                    form.xformsDynamicState = element;
                }
                if (element.name.indexOf("$temp-dynamic-state") != -1) {
                    form.xformsTempDynamicState = element;
                    if (element.value == "")
                        element.value = form.xformsDynamicState.value;
                }
            }
        }
    }

    // Initialize attributes on document
    document.xformsNextRequests = new Array();
    document.xformsLastRequestIsIncremental = false;
    document.xformsNextTargets = new Array();
    document.xformsRequestInProgress = false;
    document.xformsTargetOfCurrentRequest = null;
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

function xformsHandleResponse() {
    if (document.xformsXMLHttpRequest.readyState == 4) {
        var form = document.xformsTargetOfCurrentRequest.form;
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

                // First handle the <xxforms:itemsets> actions
                if (xformsGetLocalName(responseRoot.childNodes[i]) == "action") {
                    var actionElement = responseRoot.childNodes[i];
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
                                            
                                        // Get its child element and clone
                                        template = template.firstChild;
                                        while (template.nodeType != ELEMENT_TYPE)
                                            template = template.nextSibling;
                                            
                                        // Remove content
                                        while (documentElement.childNodes.length > 0)
                                            documentElement.removeChild(documentElement.firstChild);
                                            
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
                                            var controlValue = xformsStringValue(controlElement);
                                            var controlId = controlElement.getAttribute("id");
                                            var documentElement = document.getElementById(controlId);
                                            var documentElementClasses = documentElement.className.split(" ");

                                            // Update value
                                            if (document.xformsTargetOfCurrentRequest.id == controlId) {
                                                // Don't update the control that we just modified
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-trigger")) {
                                                // Triggers don't have a value: don't update them
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-select-full")
                                                    || xformsArrayContains(documentElementClasses, "xforms-select1-full")) {
                                                // Handle checkboxes and radio buttons
                                                var selectedValues = xformsArrayContains(documentElementClasses, "xforms-select-full")
                                                    ? controlValue.split(" ") : new Array(controlValue);
                                                var checkboxInputs = documentElement.getElementsByTagName("input");
                                                for (var checkboxInputIndex = 0; checkboxInputIndex < checkboxInputs.length; checkboxInputIndex++) {
                                                    var checkboxInput = checkboxInputs[checkboxInputIndex];
                                                    checkboxInput.checked = xformsArrayContains(selectedValues, checkboxInput.value);
                                                }
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-select-compact")
                                                    || xformsArrayContains(documentElementClasses, "xforms-select1-minimal")) {
                                                // Handle lists and comboboxes
                                                var selectedValues = xformsArrayContains(documentElementClasses, "xforms-select-compact")
                                                    ? controlValue.split(" ") : new Array(controlValue);
                                                var options = documentElement.options;
                                                for (var optionIndex = 0; optionIndex < options.length; optionIndex++) {
                                                    var option = options[optionIndex];
                                                    option.selected = xformsArrayContains(selectedValues, option.value);
                                                }
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-output")) {
                                                // XForms output
                                                while(documentElement.childNodes.length > 0)
                                                    documentElement.removeChild(documentElement.firstChild);
                                                documentElement.appendChild
                                                    (documentElement.ownerDocument.createTextNode(controlValue));
                                            } else if (xformsArrayContains(documentElementClasses, "xforms-control")
                                                    && typeof(documentElement.value) == "string") {
                                                // Other controls that have a value (textfield, etc)
                                                if (documentElement.value != controlValue) {
                                                    documentElement.value = controlValue;
                                                }
                                            }

                                            // Store validity, label, hint, help in element
                                            var newValid = controlElement.getAttribute("valid");
                                            if (newValid != null) documentElement.isValid = newValid != "false";
                                            // Store new hint message in control attribute
                                            var newLabel = controlElement.getAttribute("label");
                                            if (newLabel && newLabel != documentElement.labelMessage) {
                                                documentElement.labelMessage = newLabel;
                                                xformsUpdateStyle(documentElement.labelElement);
                                            }
                                            // Store new hint message in control attribute
                                            var newHint = controlElement.getAttribute("hint");
                                            if (newHint && newHint != documentElement.hintMessage) {
                                                documentElement.hintMessage = newHint;
                                                xformsUpdateStyle(documentElement.hintElement);
                                            }
                                            // Store new help message in control attribute
                                            var newHelp = controlElement.getAttribute("help");
                                            if (newHelp && newHelp != documentElement.helpMessage) {
                                                documentElement.helpMessage = newHelp;
                                                xformsUpdateStyle(documentElement.helpElement);
                                            }

                                            // Update style
                                            xformsUpdateStyle(documentElement);
                                            break;
                                        }

                                        // Copy repeat template
                                        case "copy-repeat-template": {
                                            var copyRepeatTemplateElement = controlValuesElement.childNodes[j];
                                            var repeatId = copyRepeatTemplateElement.getAttribute("id");
                                            var idSuffix = copyRepeatTemplateElement.getAttribute("id-suffix");
                                            // Locate end of the repeat
                                            var repeatEnd = document.getElementById(repeatId + "-repeat-end");
                                            // Put nodes of the template in an array in reverse order
                                            var templateNodes = new Array();
                                            var templateNode = repeatEnd.previousSibling;
                                            while (templateNode.className != "xforms-repeat-delimiter") {
                                                if (templateNode.nodeType == ELEMENT_TYPE) {
                                                    var nodeCopy = templateNode.cloneNode(true);
                                                    // Add suffix to all the ids
                                                    var addSuffixToIds = function(element, idSuffix) {
                                                        if (element.id) element.id += idSuffix;
                                                        if (element.htmlFor) element.htmlFor += idSuffix;
                                                        for (var childIndex = 0; childIndex < element.childNodes.length; childIndex++) {
                                                            var childNode = element.childNodes[childIndex];
                                                            if (childNode.nodeType == ELEMENT_TYPE)
                                                                addSuffixToIds(childNode, idSuffix);
                                                        }
                                                    };
                                                    addSuffixToIds(nodeCopy, idSuffix);
                                                    // Remove "xforms-repeat-template" from classes on copy of element
                                                    var nodeCopyClasses = nodeCopy.className.split(" ");
                                                    var nodeCopyNewClasses = new Array();
                                                    for (var nodeCopyClassIndex = 0; nodeCopyClassIndex < nodeCopyClasses.length; nodeCopyClassIndex++) {
                                                        var currentClass = nodeCopyClasses[nodeCopyClassIndex];
                                                        if (currentClass != "xforms-repeat-template")
                                                            nodeCopyNewClasses.push(currentClass);
                                                    }
                                                    nodeCopy.className = nodeCopyNewClasses.join(" ");
                                                    templateNodes.push(nodeCopy);
                                                }
                                                templateNode = templateNode.previousSibling;
                                            }
                                            // Figure out tag name for delimiter element
                                            var tagName = templateNodes[0].tagName;
                                            // Insert copy of template nodes
                                            var afterTemplateCopy = templateNode.nextSibling;
                                            for (var templateNodeIndex = 0; templateNodeIndex < templateNodes.length; templateNodeIndex++) {
                                                templateNode = templateNodes[templateNodeIndex];
                                                afterTemplateCopy.parentNode.insertBefore(templateNode, afterTemplateCopy);
                                                xformsInitializeControlsUnder(templateNode);
                                            }
                                            // Insert delimiter
                                            var newDelimiter = document.createElement(templateNodes[0].tagName);
                                            newDelimiter.className = "xforms-repeat-delimiter";
                                            afterTemplateCopy.parentNode.insertBefore(newDelimiter, afterTemplateCopy);
                                            break;
                                        }

                                        // Delete element in repeat
                                        case "delete-element": {
                                            var deleteElementElement = controlValuesElement.childNodes[j];
                                            var deleteId = deleteElementElement.getAttribute("id");
                                            var repeatId = deleteId.substring(0, deleteId.lastIndexOf("-"));
                                            // Locate end of repeat
                                            var cursor = document.getElementById(repeatId + "-repeat-end");
                                            // Move to last delimiter
                                            while (cursor.className != "xforms-repeat-delimiter")
                                                cursor = cursor.previousSibling;
                                            // Eat everything until next delimiter
                                            do {
                                                var nextCursor = cursor.previousSibling;
                                                cursor.parentNode.removeChild(cursor);
                                                cursor = nextCursor;
                                            } while (cursor.className != "xforms-repeat-delimiter");

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
                                        var documentElement = document.getElementById(controlId);
                                        documentElement.style.display = visibile ? "block" : "none";
                                    }
                                }
                                break;
                            }

                            // Change highlighted section in repeat
                            case "repeat-indexes": {
                                var repeatIndexesElement = actionElement.childNodes[actionIndex];
                                for (var j = 0; j < repeatIndexesElement.childNodes.length; j++) {
                                    if (xformsGetLocalName(repeatIndexesElement.childNodes[j]) == "repeat-index") {
                                        // Extract data from server response
                                        var repeatIndexElement = repeatIndexesElement.childNodes[j];
                                        var repeatId = repeatIndexElement.getAttribute("id");
                                        var oldIndex = repeatIndexElement.getAttribute("old-index");
                                        var newIndex = repeatIndexElement.getAttribute("new-index");
                                        // Unhighlight item at old index
                                        var repeatBegin = document.getElementById(repeatId + "-repeat-begin");
                                        var oldItemDelimiter = xformsFindRepeatDelimiter(repeatBegin, oldIndex);
                                        cursor = oldItemDelimiter.nextSibling;
                                        while (cursor.nodeType != ELEMENT_TYPE ||
                                               (cursor.className != "xforms-repeat-delimiter"
                                               && cursor.className != "xforms-repeat-begin-end")) {
                                            if (cursor.nodeType == ELEMENT_TYPE)
                                                cursor.className = xformsArrayRemove(cursor.className.split(" "),
                                                    "xforms-repeat-selected-item").join(" ");
                                            cursor = cursor.nextSibling;
                                        }
                                        // Highlight item a new index
                                        var newItemDelimiter = xformsFindRepeatDelimiter(repeatBegin, newIndex);
                                        cursor = newItemDelimiter.nextSibling;
                                        while (cursor.nodeType != ELEMENT_TYPE ||
                                               (cursor.className != "xforms-repeat-delimiter"
                                               && cursor.className != "xforms-repeat-begin-end")) {
                                            if (cursor.nodeType == ELEMENT_TYPE) {
                                                var classNameArray = cursor.className.split(" ");
                                                classNameArray.push("xforms-repeat-selected-item");
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
                                    form.xformsDynamicState.value = newDynamicState;
                                    form.submit();
                                }
                                break;
                            }
                        }
                    }
                }
            }

            // Store new dynamic state if that state did not trigger a post
            if (!newDynamicStateTriggersPost) {
                form.xformsTempDynamicState.value = newDynamicState;
            }

            xformsDisplayLoading(form, "none");
            
        } else if (responseXML && responseXML.documentElement 
                && responseXML.documentElement.tagName.indexOf("exceptions") != -1) {
                
            // We received an error from the server
            var errorMessageNode = document.createTextNode
                (responseXML.getElementsByTagName("message")[0].firstChild.data);
            var errorContainer = form.xformsLoadingError;
            while (errorContainer.firstChild)
                errorContainer.removeChild(errorContainer.firstChild);
            errorContainer.appendChild(errorMessageNode);
            xformsDisplayLoading(form, "error");
            
        } else {

            // The server didn't send valid XML
            form.xformsLoadingError.innerHTML = "Unexpected response received from server";
            xformsDisplayLoading(form, "error");
            
        }

        // End this request
        
        // Go ahead with next request, if any
        document.xformsRequestInProgress = false;
        xformsExecuteNextRequest();
    }
}

function xformsExecuteNextRequest() {
    if (! document.xformsRequestInProgress) {
        if (document.xformsNextRequests.length > 0) {
            // Get next request
            var request = document.xformsNextRequests.shift();
            var target = document.xformsNextTargets.shift();
            
            // Mark this as loading
            document.xformsRequestInProgress = true;
            xformsDisplayLoading(target.form, "loading");
            
            // Set the value of the dynamic-state in the request
            var requestElements = request.documentElement.childNodes;
            for (var i = 0; i < requestElements.length; i++) {
                if (requestElements[i].tagName.indexOf("dynamic-state") != -1) {
                    var dynamicStateElement = requestElements[i];
                    dynamicStateElement.appendChild(dynamicStateElement.ownerDocument.createTextNode
                        (target.form.xformsTempDynamicState.value));
                }
            }
            
            // Send request
            document.xformsTargetOfCurrentRequest = target;
            document.xformsXMLHttpRequest = new XMLHttpRequest();
            document.xformsXMLHttpRequest.open("POST", XFORMS_SERVER_URL, true);
            document.xformsXMLHttpRequest.onreadystatechange = xformsHandleResponse;
            document.xformsXMLHttpRequest.send(request);
            foundRequest = true;
        }
    }
}

// Run xformsPageLoaded when the browser has finished loading the page
xformsAddEventListener(window, "load", xformsPageLoaded);
