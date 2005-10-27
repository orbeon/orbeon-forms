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

    function updateRelevantReadonly(element, relevant, readonly) {
        if (xformsIsDefined(relevant)) {
            if (relevant) xformsRemoveClass(element, "xforms-disabled")
            else xformsAddClass(element, "xforms-disabled");
        }
        if (xformsIsDefined(readonly)) {
            var classes = element.className.split(" ");
            if (xformsArrayContains(classes, "xforms-input")) {
                // XForms input
                var displayValue = element.childNodes[0];
                if (readonly) xformsAddClass(displayValue, "xforms-readonly");
                else xformsRemoveClass(displayValue, "xforms-readonly");
                var textField = element.childNodes[1];
                if (readonly) textField.setAttribute("disabled", "disabled");
                else textField.removeAttribute("disabled");
                var showCalendar = element.childNodes[2];
                if (readonly) xformsAddClass(showCalendar, "xforms-showcalendar-readonly");
                else xformsRemoveClass(showCalendar, "xforms-showcalendar-readonly");
            } else if (xformsArrayContains(classes, "xforms-output") || xformsArrayContains(classes, "xforms-group")) {
                // XForms output and group
                if (readonly) xformsAddClass(element, "xforms-readonly");
                else xformsRemoveClass(element, "xforms-readonly");
            } else {
                // Other controls
                if (readonly) element.setAttribute("disabled", "disabled");
                else element.removeAttribute("disabled");
            }
        }
    }

    if (element.className) {
        var classes = element.className.split(" ");
        for (var classIndex = 0; classIndex < classes.length; classIndex++) {
            var className = classes[classIndex];
            
            if (className == "xforms-output-html") {
                if (element.firstChild != null)
                    element.innerHTML = element.firstChild.data;
                element.style.display = "inline";
            }

            if (className == "xforms-label") {

                // Initialize hint message on control if not set already
                var control = document.getElementById(element.htmlFor);
                if (!control.labelElement) {
                    control.labelMessage = xformsStringValue(element);
                    control.labelElement = element;
                }

                // Update label if it changed
                if (control.labelMessage != xformsStringValue(element)) {
                    while (element.firstChild) element.removeChild(element.firstChild);
                    element.appendChild(document.createTextNode(control.labelMessage));
                }

                // Disable or enable label depending if control is relevant
                updateRelevantReadonly(element, control.isRelevant, control.isReadonly);
            }

            if (className == "xforms-hint") {

                // Initialize hint message on control if not set already
                var control = document.getElementById(element.htmlFor);
                if (control.hintElement != element) {
                    control.hintElement = element;
                    control.hintMessage = xformsStringValue(element);
                }

                // Update hint if it changed
                if (control.hintMessage != xformsStringValue(element)) {
                    while (element.firstChild) element.removeChild(element.firstChild);
                    element.appendChild(document.createTextNode(control.hintMessage));
                }

                // Only add listener once
                if (!element.styleListenerRegistered) {
                    element.styleListenerRegistered = true;

                    // What happens when control gets/looses focus
                    function controlGetsFocus() {
                        xformsRemoveClass(element, "xforms-hint");
                        xformsAddClass(element, "xforms-hint-active");
                    }
                    function controlLoosesFocus() {
                        xformsRemoveClass(element, "xforms-hint-active");
                        xformsAddClass(element, "xforms-hint");
                    }

                    // Add listeners on control
                    if (control.addEventListener) {
                        control.addEventListener("focus", controlGetsFocus, false);
                        control.addEventListener("blur", controlLoosesFocus, false);
                    } else {
                        control.attachEvent("onfocus", controlGetsFocus);
                        control.attachEvent("onblur", controlLoosesFocus);
            	    }
                }

                // Disable or enable hint depending if control is relevant
                updateRelevantReadonly(element, control.isRelevant, control.isReadonly);
            }
            
            if (className == "xforms-help") {

                // Initialize help message on control if not set already
                var control = document.getElementById(element.htmlFor);
                if (control.helpElement != element) {
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
                    element.divId = control.id + "-div";
                    var divHTML = tt_Htm(this, element.divId, control.helpMessage);
                    var container = element.cloneNode(true);
                    container.innerHTML = divHTML;
                    var newDiv = container.firstChild;
                    newDiv.helpMessage = control.helpMessage;
                    document.body.appendChild(newDiv);
                }

                // Only add listener once
                if (!element.styleListenerRegistered) {
                    element.styleListenerRegistered = true;
                    xformsAddEventListener(element, "mouseover", function(event) {
                        tt_Show(event, getEventTarget(event).divId, false, 0, false, false,
                            ttOffsetX, ttOffsetY, false, false, ttTemp);
                    });
                    xformsAddEventListener(element, "mouseout", function() {
                        tt_Hide();
                    });
                }

                // Disable or enable help depending if control is relevant
                updateRelevantReadonly(element, control.isRelevant, control.isReadonly);
            }

            if (className == "xforms-alert-inactive" || className == "xforms-alert-active") {

                // Initialize alert control when necessary
                var control = document.getElementById(element.htmlFor);
                if (control.alertElement != element)
                    control.alertElement = element;

                // Change alert status when necessary
                if (xformsIsDefined(control.isValid))
                    element.className = control.isValid ? "xforms-alert-inactive" : "xforms-alert-active";

                // Change message if necessary
                if (control.alertMessage) {
                    if (control.xformsMessageLabel)
                        xformsReplaceNodeText(control.xformsMessageLabel, control.alertMessage);
                }

                // Disable or enable help depending if control is relevant
                updateRelevantReadonly(element, control.isRelevant, control.isReadonly);
            }

            if (className == "xforms-input") {

                var inputField = element.childNodes[1];
                var showCalendar = element.childNodes[2];

                if (!element.setupDone) {
                    element.setupDone = true;

                    // Assign ids to input field and icon for date picker
                    inputField.id = "input-" + element.id;
                    showCalendar.id = "showcalendar-" + element.id;

                    function calendarUpdate() {
                        // Send notification to XForms engine
                        element.value = inputField.value;
                        xformsValueChanged(element, false);
                    }

                    // Setup calendar library
                    Calendar.setup({
                        inputField     :    inputField.id,
                        ifFormat       :    "%Y-%m-%d",
                        showsTime      :    false,
                        button         :    element.id,
                        singleClick    :    true,
                        step           :    1,
                        onUpdate       :    calendarUpdate,
                        electric       :    true
                    });

                    var jscalendarOnClick = element.onclick;
                    element.onclick = function() {
                        // Call jscalendar handler only if this is date field
                        if (xformsArrayContains(inputField.className.split(" "), "xforms-type-date")
                                && !inputField.disabled)
                            jscalendarOnClick();
                    }
                }

                // Disable or enable input field depending if control is relevant
                // TODO: Should this be commented?
                //updateRelevantReadonly(inputField, control.isRelevant, control.isReadonly);
            }
            
            if (className == "xforms-select1-compact") {
                // Prevent end-user from selecting multiple values
                
                var select1CompactChanged = function (event) {
                    var target = getEventTarget(event);
                    target.selectedIndex = target.selectedIndex;
                };
                
                xformsAddEventListener(element, "change", select1CompactChanged);
            }

            if (className == "xforms-trigger") {
                // Update label on trigger
                if (element.labelMessage && element.labelMessage != xformsStringValue(element)) {
                    xformsReplaceNodeText(element, element.labelMessage);
                }
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

            // Update relevant
            updateRelevantReadonly(element, element.isRelevant, element.isReadonly);

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
                                xformsFireEvents(new Array(xformsCreateEventArray(child, "DOMActivate", null, false)));
                            }
                        }
                        
                        
                    };
                    xformsAddEventListener(element, "click", clickEventHandler);
                }
            }
            
        }
    }
}
