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

function xformsFireEvent(target, eventName, value, incremental) {

    // Build request
    var eventFiredElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event-request");
    
    // Add event
    var eventElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event");
    eventFiredElement.appendChild(eventElement);
    eventElement.setAttribute("name", eventName);
    eventElement.setAttribute("source-control-id", target.id);
    if (value != null)
        eventElement.appendChild(eventElement.ownerDocument.createTextNode(value));

    // Add models
    var modelsElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:models");
    modelsElement.appendChild(modelsElement.ownerDocument.createTextNode(target.form.xformsModels.value));
    eventFiredElement.appendChild(modelsElement);
    
    // Add controls
    var controlsElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:controls");
    controlsElement.appendChild(controlsElement.ownerDocument.createTextNode(target.form.xformsControls.value));
    eventFiredElement.appendChild(controlsElement);    
    
    // Add instances
    var instancesElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:instances");
    instancesElement.appendChild(instancesElement.ownerDocument.createTextNode(target.form.xformsInstances.value));
    eventFiredElement.appendChild(instancesElement);    

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

// Function called when value changed
function xformsValueChanged(target, incremental) {
    if (target.value != target.previousValue) {
        target.previousValue = target.value;
        xformsFireEvent(target, "xxforms-value-change-with-focus-change", target.value, incremental);
    }
}
    
/**
 * Initializes attributes of each form:
 *
 *     Div             xformsLoadingLoading
 *     Div             xformsLoadingError
 *     Div             xformsLoadingNone
 *     Input           xformsModels
 *     Input           xformsControls
 *     Input           xformsInstances
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
    document.xformsChangeEventElements = new Array();
    var spans = document.getElementsByTagName("span");
    var buttons = document.getElementsByTagName("button");
    var inputs = document.getElementsByTagName("input");
    var textareas = document.getElementsByTagName("textarea");
    var labels = document.getElementsByTagName("label");
    var formsControls = new Array();
    for (var i = 0; i < spans.length; i++) formsControls = formsControls.concat(spans[i]);
    for (var i = 0; i < buttons.length; i++) formsControls = formsControls.concat(buttons[i]);
    for (var i = 0; i < inputs.length; i++) formsControls = formsControls.concat(inputs[i]);
    for (var i = 0; i < textareas.length; i++) formsControls = formsControls.concat(textareas[i]);
    for (var i = 0; i < labels.length; i++) formsControls = formsControls.concat(labels[i]);

    // Go through potential form controls, add style, and register listeners
    for (var controlIndex = 0; controlIndex < formsControls.length; controlIndex++) {
    
        var control = formsControls[controlIndex];

        // Check that this is an XForms control. Otherwise continue.
        var classes = control.className.split(" ");
        var isXFormsElement = false;
        var isXFormsAlert = false;
        var isIncremental = false;
        for (var classIndex = 0; classIndex < classes.length; classIndex++) {
            var className = classes[classIndex];
            if (className.indexOf("xforms-") == 0)
                isXFormsElement = true;
            if (className.indexOf("xforms-alert") == 0)
                isXFormsAlert = true;
            if (className.indexOf("xforms-incremental") == 0)
                isIncremental = true;
        }
        if (!isXFormsElement) continue;
    
        // Handle value change and incremental modification
        control.previousValue = null;
        control.userModications = false;
        xformsAddEventListener(control, "change", function(event) 
            { xformsValueChanged(getEventTarget(event), false); });
        if (isIncremental)
            xformsAddEventListener(control, "keyup", function(event) 
                { xformsValueChanged(getEventTarget(event), true); });
        
        // Handle click
        if (control.tagName == "BUTTON") {
            xformsAddEventListener(control, "click", function(event) {
                xformsFireEvent(getEventTarget(event), "DOMActivate", null, false);
            });
        }
            
        // If alert, store reference in control element to this alert element
        if (isXFormsAlert) 
            document.getElementById(control.htmlFor).alertElement = control;

        // Add style to element
        xformsUpdateStyle(control);
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
                if (element.name.indexOf("$models") != -1)
                    form.xformsModels = element;
                if (element.name.indexOf("$controls") != -1)
                    form.xformsControls = element;
                if (element.name.indexOf("$instances") != -1)
                    form.xformsInstances = element;
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
        var responseXML = document.xformsXMLHttpRequest.responseXML;
        if (responseXML && responseXML.documentElement 
                && responseXML.documentElement.tagName.indexOf("event-response") != -1) {
                
            // Good: we received an XML document from the server
            var responseRoot = responseXML.documentElement;
            for (var i = 0; i < responseRoot.childNodes.length; i++) {

                // Update controls
                if (xformsGetLocalName(responseRoot.childNodes[i]) == "control-values") {
                    var controlValuesElement = responseRoot.childNodes[i];
                    for (var j = 0; j < controlValuesElement.childNodes.length; j++) {
                        if (xformsGetLocalName(controlValuesElement.childNodes[j]) == "control") {
                            var controlElement = controlValuesElement.childNodes[j];
                            var controlId = controlElement.getAttribute("id");
                            var documentElement = document.getElementById(controlId);

                            // Update value
                            if (document.xformsTargetOfCurrentRequest.id != controlId
                                   && documentElement.className.indexOf("xforms-control") != -1) {
                                var controlValue = controlElement.firstChild ? 
                                    controlElement.firstChild.data : "";
                                if (typeof(documentElement.value) == "string") {
                                    if (documentElement.value != controlValue) {
                                        documentElement.value = controlValue;
                                    }
                                } else {
                                    while(documentElement.childNodes.length > 0)
                                        documentElement.removeChild(documentElement.firstChild);
                                    documentElement.appendChild
                                        (documentElement.ownerDocument.createTextNode(controlValue));
                                }
                            }

                            // Update validity
                            documentElement.valid = controlElement.getAttribute("valid") != "false";

                            // Update style
                            xformsUpdateStyle(documentElement);
                        }
                    }
                }

                // Update instances
                if (xformsGetLocalName(responseRoot.childNodes[i]) == "instances") {
                    document.xformsTargetOfCurrentRequest.form.xformsInstances.value =
                        responseRoot.childNodes[i].firstChild.data;
                }

                // Display or hide divs
                if (xformsGetLocalName(responseRoot.childNodes[i]) == "divs") {
                    var divsElement = responseRoot.childNodes[i];
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
            xformsDisplayLoading(document.xformsTargetOfCurrentRequest.form, "none");
            
        } else if (responseXML && responseXML.documentElement 
                && responseXML.documentElement.tagName.indexOf("exceptions") != -1) {
                
            // We received an error from the server
            var errorMessageNode = document.createTextNode
                (responseXML.getElementsByTagName("message")[0].firstChild.data);
            var errorContainer = document.xformsTargetOfCurrentRequest.form.xformsLoadingError;
            while (errorContainer.firstChild)
                errorContainer.removeChild(errorContainer.firstChild);
            errorContainer.appendChild(errorMessageNode);
            xformsDisplayLoading(document.xformsTargetOfCurrentRequest.form, "error");
            
        } else {
        
            // The server didn't send valid XML
            document.xformsTargetOfCurrentRequest.form.xformsLoadingError.innerHTML = 
                "Unexpected response received from server";
            xformsDisplayLoading(document.xformsTargetOfCurrentRequest.form, "error");
            
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
            var request = document.xformsNextRequests.shift();
            var target = document.xformsNextTargets.shift();
            document.xformsRequestInProgress = true;
            xformsDisplayLoading(target.form, "loading");
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
