/**
 * Constants
 */
var XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
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

function xformsFireEvent(target, eventName, value) {

    // Build request
    var eventFiredElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event-request");
    
    // Add event
    var eventElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:event");
    eventFiredElement.appendChild(eventElement);
    eventElement.setAttribute("name", eventName);
    eventElement.setAttribute("source-control-id", target.id);
    
    // Add models
    var modelsElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:models");
    modelsElement.appendChild(modelsElement.ownerDocument.createTextNode(target.form.xformsModels));
    eventFiredElement.appendChild(modelsElement);
    
    // Add controls
    var controlsElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:controls");
    controlsElement.appendChild(controlsElement.ownerDocument.createTextNode(target.form.xformsControls));
    eventFiredElement.appendChild(controlsElement);    
    
    // Add instances
    var instancesElement = xformsCreateElementNS(XXFORMS_NAMESPACE_URI, "xxforms:instances");
    instancesElement.appendChild(instancesElement.ownerDocument.createTextNode(target.form.xformsInstances));
    eventFiredElement.appendChild(instancesElement);    
    
    // Set as next request to execute and trigger execution
    document.xformsNextRequests.push(eventFiredElement.ownerDocument);
    document.xformsNextForms.push(target.form);
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
 *     Document        xformsModels
 *     Document        xformsControls
 *     Document        xformsInstances
 *     Div             xformsLoading
 *
 * Initializes attributes on the document object:
 *
 *     Document        xformsNextRequests
 *     Form            xformsNextForms
 *     boolean         xformsRequestInProgress
 *     Form            xformsFormOfCurrentRequest
 *     XMLHttpRequest  xformsXMLHttpRequest
 *
 */
function xformsPageLoaded() {

    // Initialize XForms server URL
    var scripts = document.getElementsByTagName("script");
    for (var scriptIndex = 0; scriptIndex < scripts.length; scriptIndex++) {
        var script = scripts[scriptIndex];
        var startPathToJavaScript = script.getAttribute("src").indexOf(PATH_TO_JAVASCRIPT);
        if (startPathToJavaScript != -1) {
            XFORMS_SERVER_URL = script.getAttribute("src").substr(0, startPathToJavaScript) + "/xforms-server";
            break;
        }
    }

    // 
    var forms = document.getElementsByTagName("form");
    for (var formIndex = 0; formIndex < forms.length; formIndex++) {
    
        // Register listeners on controls
        var elements = forms[formIndex].elements;
        for (var elementIndex = 0; elementIndex < elements.length; elementIndex++) {
            var element = elements[elementIndex];
            
            // Handle value change
            var handleChange = function(event) {
                xformsFireEvent(getEventTarget(event), "DOMActivate", null);
            };
            if (element.addEventListener) {
                element.addEventListener("change", handleChange, false);
            } else {
                element.attachEvent("onchange", handleChange);
            }
            
            // Handle click 
            var handleClick = function(event) {
                xformsFireEvent(getEventTarget(event), "DOMActivate", null);
            }
            if (element.addEventListener) {
                element.addEventListener("click", handleClick, false);
            } else {
                element.attachEvent("onclick", handleClick);
            }
        }
    
        // Initialize attributes on form
        var divs = forms[formIndex].getElementsByTagName("div");
        for (var divIndex = 0; divIndex < divs.length; divIndex++) {
            if (divs[divIndex].getAttribute("title") == "xforms-private") {
                var xformsDiv = divs[divIndex];
                var privateDivs = xformsDiv.getElementsByTagName("div");
                for (var privateDivIndex = 0; privateDivIndex < privateDivs.length; privateDivIndex++) {
                    if (privateDivs[privateDivIndex].getAttribute("title") == "models") 
                        forms[formIndex].xformsModels = privateDivs[privateDivIndex].firstChild.data;
                    if (privateDivs[privateDivIndex].getAttribute("title") == "controls") 
                        forms[formIndex].xformsControls = privateDivs[privateDivIndex].firstChild.data;
                    if (privateDivs[privateDivIndex].getAttribute("title") == "instances") 
                        forms[formIndex].xformsInstances = privateDivs[privateDivIndex].firstChild.data;
                }
            }
            if (divs[divIndex].getAttribute("title") == "xforms-loading") {
                forms[formIndex].xformsLoading = divs[divIndex];
            }
        }
    }
    document.xformsNextRequests = new Array();
    document.xformsNextForms = new Array();
    document.xformsRequestInProgress = false;
    document.xformsFormOfCurrentRequest = null;
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
        //alert(document.xformsXMLHttpRequest.responseText);
        //alert(Sarissa.serialize(document.xformsXMLHttpRequest.responseXML));
        var responseRoot = document.xformsXMLHttpRequest.responseXML.documentElement;
        for (var i = 0; i < responseRoot.childNodes.length; i++) {
        
            // Update control values
            if (xformsGetLocalName(responseRoot.childNodes[i]) == "control-values") {
                var controlValuesElement = responseRoot.childNodes[i];
                for (var j = 0; j < controlValuesElement.childNodes.length; j++) {
                    if (xformsGetLocalName(controlValuesElement.childNodes[j]) == "control") {
                        var controlElement = controlValuesElement.childNodes[j];
                        var controlId = controlElement.getAttribute("id");
                        var controlValue = controlElement.getAttribute("value");
                        var documentElement = document.getElementById(controlId);
                        if (typeof(documentElement.value) == "string") {
                            documentElement.value = controlValue;
                        } else {
                            while(documentElement.childNodes.length > 0)
                                documentElement.removeChild(documentElement.firstChild);
                            documentElement.appendChild
                                (documentElement.ownerDocument.createTextNode(controlValue));
                        }
                    }
                }
            }
            
            // Update instances
            if (xformsGetLocalName(responseRoot.childNodes[i]) == "instances") {
                document.xformsFormOfCurrentRequest.xformsInstances =
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

        // End this request
        document.xformsRequestInProgress = false;
        document.xformsFormOfCurrentRequest.xformsLoading.style.visibility = "hidden";
        
        // Go ahead with next request, if any
        xformsExecuteNextRequest();
    }
}

function xformsExecuteNextRequest() {
    if (! document.xformsRequestInProgress) {
        if (document.xformsNextRequests.length > 0) {
            var request = document.xformsNextRequests.shift();
            var form = document.xformsNextForms.shift();
            document.xformsRequestInProgress = true;
            form.xformsLoading.style.visibility = "visible";
            document.xformsFormOfCurrentRequest = form;
            document.xformsXMLHttpRequest = new XMLHttpRequest();
            document.xformsXMLHttpRequest.open("POST", XFORMS_SERVER_URL, true);
            document.xformsXMLHttpRequest.onreadystatechange = xformsHandleResponse;
            //alert(Sarissa.serialize(request));
            document.xformsXMLHttpRequest.send(request);
            foundRequest = true;
        }
    }
}

// Run xformsPageLoaded when the browser has finished loading the page
if (window.addEventListener) {
    window.addEventListener("load", xformsPageLoaded, false);
} else {
    window.attachEvent("onload", xformsPageLoaded);
}
