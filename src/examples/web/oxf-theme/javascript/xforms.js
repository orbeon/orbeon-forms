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

/* * * * * * Utility functions * * * * * */

/**
 * Create an element with a namespace. Offers the same functionality 
 * as the DOM2 Document.createElementNS method.
 */
function xformsCreateElementNS(namespaceURI, qname) {
    var localName = qname.indexOf(":") == -1 ? "" : ":" + qname.substr(0, qname.indexOf(":"));
    var request = Sarissa.getDomDocument();
    request.loadXML("<" + qname + " xmlns" + localName + "='" + namespaceURI + "'/>")
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

function xformsGetElementPosition(element) {
    var offsetTrail = element;
    var offsetLeft = 0;
    var offsetTop = 0;
    while (offsetTrail) {
        offsetLeft += offsetTrail.offsetLeft;
        offsetTop += offsetTrail.offsetTop;
        offsetTrail = offsetTrail.offsetParent;
    }
    if (navigator.userAgent.indexOf("Mac") != -1 && 
        typeof document.body.leftMargin != "undefined") {
        offsetLeft += document.body.leftMargin;
        offsetTop += document.body.topMargin;
    }
    return {left:offsetLeft, top:offsetTop};
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

function xformsValueChanged(target, incremental) {
    if (target.value != target.previousValue) {
        target.previousValue = target.value;
        xformsFireEvent(target, "xxforms-value-change-with-focus-change", target.value, incremental);
    }
}

function xformsFireEvent(target, eventName, value, incremental) {

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

/**
 * Initializes attributes of each form:
 *
 *     Div             xformsLoadingLoading
 *     Div             xformsLoadingError
 *     Div             xformsLoadingNone
 *     Input           xformsStaticState
 *     Input           xformsDynamicState
 *     Element         xformsCurrentDivs
 *
 * Initializes attributes on the document object:
 *
 *     Document[]      xformsNextRequests
 *     boolean         xformsLastRequestIsIncremental
 *     Form            xformsNextTargets
 *     boolean         xformsRequestInProgress
 *     Form            xformsTargetOfCurrentRequest
 *     XMLHttpRequest  xformsXMLHttpRequest
 *
 */
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
    
    // Gather all potential form controls
    var interestingTagNames = new Array("span", "button", "textarea", "input", "label", "select", "td", "table", "div");
    var formsControls = new Array();
    for (var tagIndex = 0; tagIndex < interestingTagNames.length; tagIndex++) {
        var elements = document.getElementsByTagName(interestingTagNames[tagIndex]);
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
                    computeSpanValue(checkbox.parentNode);
                    xformsFireEvent(checkbox.parentNode, "xxforms-value-change-with-focus-change",
                        checkbox.parentNode.value, false);
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

            } else if (isXFormsComboboxList) {

                function computeSelectValue(select) {
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

                function selectChanged(event) {
                    var select = getEventTarget(event);
                    computeSelectValue(select);
                    xformsFireEvent(select, "xxforms-value-change-with-focus-change",
                        select.selectValue, false);
                }

                // Register event listener on select
                xformsAddEventListener(control, "change", selectChanged);
                // Compute the checkes value for the first time
                computeSelectValue(control);

            } else if (isXFormsRange) {
            
                function rangeMouseDown(event) {
                    document.xformsCurrentRangeControl = getEventTarget(event).parentNode;
                    return false;
                }
                
                function rangeMouseUp(event) {
                    document.xformsCurrentRangeControl = null;
                    return false;
                }
                
                function rangeMouseMove(event) {
                    var control = document.xformsCurrentRangeControl;
                    if (control) {
                        // Compute range boundaries
                        var rangeStart = xformsGetElementPosition(control.track).left
                        var rangeLength = control.track.clientWidth - control.slider.clientWidth;

                        // Compute value
                        var value = (event.clientX - rangeStart) / rangeLength;
                        if (value < 0) value = 0;
                        if (value > 1) value = 1;
                        
                        // Compute slider position
                        var sliderPosition = event.clientX - rangeStart;
                        if (sliderPosition < 0) sliderPosition = 0;
                        if (sliderPosition > rangeLength) sliderPosition = rangeLength;
                        control.slider.style.left = sliderPosition;
                        
                        // Notify server that value changed
                        control.value = value;
                        xformsValueChanged(control, true);
                    }
                    
                    return false;
                }
                
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
                xformsAddEventListener(control, "change", function(event)
                    { xformsValueChanged(getEventTarget(event), false); });
                if (isIncremental)
                    xformsAddEventListener(control, "keyup", function(event)
                        { xformsValueChanged(getEventTarget(event), true); });
            }

            // If alert, store reference in control element to this alert element
            if (isXFormsAlert)
                document.getElementById(control.htmlFor).alertElement = control;

            // Add style to element
            xformsUpdateStyle(control);
        }
    }
    
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
                if (element.name.indexOf("$dynamic-state") != -1)
                    form.xformsDynamicState = element;
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
            for (var i = 0; i < responseRoot.childNodes.length; i++) {

                // Update instances
                if (xformsGetLocalName(responseRoot.childNodes[i]) == "dynamic-state")
                    form.xformsDynamicState.value = responseRoot.childNodes[i].firstChild.data;

                // Things to do are in the action element
                if (xformsGetLocalName(responseRoot.childNodes[i]) == "action") {
                    var actionElement = responseRoot.childNodes[i];
                    for (var actionIndex = 0; actionIndex < actionElement.childNodes.length; actionIndex++) {

                        // Update controls
                        if (xformsGetLocalName(actionElement.childNodes[actionIndex]) == "control-values") {
                            var controlValuesElement = actionElement.childNodes[actionIndex];
                            for (var j = 0; j < controlValuesElement.childNodes.length; j++) {
                                if (xformsGetLocalName(controlValuesElement.childNodes[j]) == "control") {
                                    var controlElement = controlValuesElement.childNodes[j];
                                    var controlValue = controlElement.firstChild ?
                                        controlElement.firstChild.data : "";
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

                                    // Update validity
                                    documentElement.isValid = controlElement.getAttribute("valid") != "false";

                                    // Update style
                                    xformsUpdateStyle(documentElement);
                                }
                            }
                        }

                        // Display or hide divs
                        if (xformsGetLocalName(actionElement.childNodes[actionIndex]) == "divs") {
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
                        }
                    }
                }
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
            var dynamicStateElement = request.getElementsByTagName("xxforms:dynamic-state")[0];
            dynamicStateElement.appendChild(dynamicStateElement.ownerDocument.createTextNode(target.form.xformsDynamicState.value));
            
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
