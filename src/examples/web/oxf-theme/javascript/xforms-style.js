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

function xformsUpdateStyle(element) {

    /**
     * Updates an HTML label with a new message (used for XForms label, hint, and help).
     */
    function updateLabel(label, message) {
        if (xformsStringValue(label) != message) {
            // Remove content of label
            while (label.firstChild != null)
                label.removeChild(label.firstChild);
            // Add new message
            label.appendChild(document.createTextNode(message));
        }
    }

    if (element.className) {
        var classes = element.className.split(" ");
        for (var classIndex = 0; classIndex < classes.length; classIndex++) {
            var className = classes[classIndex];
            
            if (className == "xforms-xhtml") {
                element.innerHTML = element.firstChild.data;
                element.style.display = "inline";
            }

            if (className == "xforms-label") {

                // Initialize hint message on control if not set already
                var control = document.getElementById(element.htmlFor);
                if (!xformsIsDefined(control.labelMessage)) {
                    control.labelMessage = xformsStringValue(element);
                    control.labelElement = element;
                }

                // Update label if it changed
                if (control.labelMessage != xformsStringValue(element)) {
                    while (element.firstChild) element.removeChild(element.firstChild);
                    element.appendChild(document.createTextNode(control.labelMessage));
                }
            }

            if (className == "xforms-hint") {

                // Initialize hint message on control if not set already
                var control = document.getElementById(element.htmlFor);
                if (!xformsIsDefined(control.hintMessage)) {
                    control.hintMessage = xformsStringValue(element);
                    control.hintElement = element;
                }

                // Update hint if it changed
                if (control.hintMessage != xformsStringValue(element)) {
                    while (element.firstChild) element.removeChild(element.firstChild);
                    element.appendChild(document.createTextNode(control.hintMessage));
                }

                // Only add listener once
                if (!element.listenerRegistered) {
                    element.listenerRegistered = true;

                    // Compute class for active state
                    var activeClass = new Array();
                    activeClass.push("xforms-hint-active");
                    for (var i = 0; i < classes.length; i++)
                        if (classes[i] != "xforms-hint") activeClass.push(classes[i]);
                        
                    // What happens when control gets/looses focus
                    var controlGetsFocus = function() { element.className = activeClass.join(" "); };
                    var controlLoosesFocus = function() { element.className = classes.join(" "); };

                    // Add listeners on control
                    if (control.addEventListener) {
                        control.addEventListener("focus", controlGetsFocus, false);
                        control.addEventListener("blur", controlLoosesFocus, false);
                    } else {
                        control.attachEvent("onfocus", controlGetsFocus);
                        control.attachEvent("onblur", controlLoosesFocus);
            	    }
                }
            }
            
            if (className == "xforms-help") {

                // Initialize help message on control if not set already
                var control = document.getElementById(element.htmlFor);
                if (!xformsIsDefined(control.helpMessage)) {
                    control.helpMessage = xformsStringValue(element);
                    control.helpElement = element;
                }

                // Figure out if we need to create a div
                // When there is an existing div, check if it has the same message
                var needToCreateDiv;
                if (element.divId) {
                    var existingDiv = document.getElementById(element.divId);
                    if (existingDiv.helpMessage == control.helpMessage) {
                        needToCreateDiv = false;
                    } else {
                        needToCreateDiv = true;
                        existingDiv.parentNode.removeChild(existingDiv);
                    }
                } else {
                    needToCreateDiv = true;
                }

                // Create new div when necessary
                if (needToCreateDiv) {
                    element.divId = element.id + "-div";
                    var divHTML = tt_Htm(this, element.divId, control.helpMessage);
                    var container = element.cloneNode(true);
                    container.innerHTML = divHTML;
                    var newDiv = container.firstChild;
                    newDiv.helpMessage = control.helpMessage;
                    document.body.appendChild(newDiv);
                }

                // Only add listener once
                if (!element.listenerRegistered) {
                    element.listenerRegistered = true;
                    xformsAddEventListener(element, "mouseover", function(event) {
                        tt_Show(event, getEventTarget(event).divId, false, 0, false, false,
                            ttOffsetX, ttOffsetY, false, false, ttTemp);
                    });
                    xformsAddEventListener(element, "mouseout", function() {
                        tt_Hide();
                    });
                }
            }
            
            if (className == "xforms-date") {
                if (!element.setupDone) {
                    element.setupDone = true;
                    
                    var showCalendarId = element.id + "-showcalendar";
                    element.nextSibling.id = showCalendarId;
                        
                    // Setup calendar library
                    Calendar.setup({
                        inputField     :    element.id,
                        ifFormat       :    "%Y-%m-%d",
                        showsTime      :    false,
                        button         :    showCalendarId,
                        singleClick    :    false,
                        step           :    1,
                        onUpdate       :    function() {
                            xformsValueChanged(this.inputField, false);
                        }
                    });
                }
            }
            
            if (className == "xforms-select1-compact") {
                // Prevent end-user from selecting multiple values
                
                var select1CompactChanged = function (event) {
                    var target = getEventTarget(event);
                    target.selectedIndex = target.selectedIndex;
                };
                
                xformsAddEventListener(element, "change", select1CompactChanged);
            }
            
            if (className == "wide-textarea") {
                if (!element.changeHeightHandlerRegistered) {
                    var changeHeightHandler = function () {
                        var lineNumber = element.value.split("\n").length;
                        if (lineNumber < 5) lineNumber = 5;
                        element.style.height = 3 + lineNumber * 1.1 + "em";
                    };
                    element.changeHeightHandlerRegistered = true;
                    changeHeightHandler();
                    xformsAddEventListener(element, "keyup", changeHeightHandler);
                }
                
            }

            // Update validity
            if (element.alertElement && typeof(element.isValid) != "undefined") {
                element.alertElement.className = element.isValid ? "xforms-alert-inactive" 
                    : "xforms-alert-active";
            }

            // This is for widgets. Code for widgets should be modularized and moved out of this file
            
            if (className == "widget-tabs" || className == "widget-tab-inactive" 
                    || className == "widget-tab-active" || className == "widget-tab-spacer-side"
                    || className == "widget-tab-spacer-between") {
                // Once the size of the table is set, do not change it
                if (!element.width)
                    element.width = element.clientWidth;
            }
            
            if (className == "widget-tab-inactive" || className == "widget-tab-active") {
                if (!element.eventRegistered) {
                    element.eventRegistered = true;
                    var clickEventHandler = function (event) {
                        var td = getEventTarget(event);
                        var tr = td.parentNode;
                        
                        // Change class of all tds
                        for (var i = 0; i < tr.childNodes.length; i++) {
                            var child = tr.childNodes[i];
                            if (child.className == "widget-tab-inactive" || child.className == "widget-tab-active")
                                child.className = child == td ? "widget-tab-active" : "widget-tab-inactive";
                        }
                        
                        // Click the trigger contained in this td
                        for (var i = 0; i < td.childNodes.length; i++) {
                            var child = td.childNodes[i];
                            if (typeof(child.className) != "undefined"
                                    && xformsArrayContains(child.className.split(" "), "xforms-trigger")) {
                                xformsFireEvent(child, "DOMActivate", null, false);
                            }
                        }
                        
                        
                    };
                    xformsAddEventListener(element, "click", clickEventHandler);
                }
            }
            
        }
    }
}
