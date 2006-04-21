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

function xformsUpdateStyleLabel(label, message) {
    if (xformsStringValue(label) != message) {
        // Remove content of label
        while (label.firstChild != null)
            label.removeChild(label.firstChild);
        // Add new message
        label.appendChild(document.createTextNode(message));
    }
}

function xformsUpdateReadonlyFormElement(element, readonly) {
    if (readonly) {
        element.setAttribute("disabled", "disabled");
        xformsAddClass(element, "xforms-readonly");
    } else {
        element.removeAttribute("disabled");
        xformsRemoveClass(element, "xforms-readonly");
    }
}

function xformsUpdateStyleRelevantReadonly(element, relevant, readonly, required, valid) {
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
        } else if (xformsArrayContains(classes, "xforms-select1-full") || xformsArrayContains(classes, "xforms-select-full")) {
            // XForms radio buttons
            for (var spanIndex = 0; spanIndex < element.childNodes.length; spanIndex++) {
                var span = element.childNodes[spanIndex];
                var input = span.firstChild;
                xformsUpdateReadonlyFormElement(input, readonly);
            }
        } else {
            // Other controls
            xformsUpdateReadonlyFormElement(element, readonly);
        }
    }
    if (xformsIsDefined(required)) {
        var classes = element.className.split(" ");
        if (required) {
            xformsAddClass(element, "xforms-required");
            if (element.value == "") {
                xformsAddClass(element, "xforms-required-empty");
                xformsRemoveClass(element, "xforms-required-filled");
            } else {
                xformsAddClass(element, "xforms-required-filled");
                xformsRemoveClass(element, "xforms-required-empty");
            }
        } else {
            xformsRemoveClass(element, "xforms-required");
            xformsRemoveClass(element, "xforms-required-filled");
            xformsRemoveClass(element, "xforms-required-empty");
        }
    }
    if (xformsIsDefined(valid)) {
        if (valid) xformsRemoveClass(element, "xforms-invalid")
        else xformsAddClass(element, "xforms-invalid");
    }
}

function xformsSytleGetFocus(event) {
    var target = getEventTarget(event);
    if (!xformsArrayContains(target.className.split(" "), "xforms-control"))
        target = target.parentNode;
    var hintLabel = target;
    while (true) {
        if (xformsArrayContains(hintLabel.className.split(" "), "xforms-hint") && hintLabel.htmlFor == target.id) {
            xformsRemoveClass(hintLabel, "xforms-hint");
            xformsAddClass(hintLabel, "xforms-hint-active");
            break;
        }
        hintLabel = hintLabel.nextSibling;
        if (hintLabel == null) break;
    }
}

function xformsStyleLoosesFocus(event) {
    var target = getEventTarget(event);
    if (!xformsArrayContains(target.className.split(" "), "xforms-control"))
        target = target.parentNode;
    var hintLabel = target;
    while (true) {
        if (xformsArrayContains(hintLabel.className.split(" "), "xforms-hint-active") && hintLabel.htmlFor == target.id) {
            xformsRemoveClass(hintLabel, "xforms-hint-active");
            xformsAddClass(hintLabel, "xforms-hint");
            break;
        }
        hintLabel = hintLabel.nextSibling;
        if (hintLabel == null) break;
    }
}

function xformsHelpMouseOver(event) {
    tt_Show(event, getEventTarget(event).divId, false, 0, false, false,
        ttOffsetX, ttOffsetY, false, false, ttTemp);
}

function xformsCalendarUpdate(calendar) {
    // Send notification to XForms engine
    var inputField = calendar.params.inputField;
    var element = inputField.parentNode;
    element.value = inputField.value;
    xformsValueChanged(element, false);
}

function xformsCalendarClick(event) {
    // Call jscalendar handler only if this is date field
    var target = getEventTarget(event);
    // Event can be received on calendar picker span, or on the containing span
    var span = target.childNodes.length != 3 ? target.parentNode : target
    var inputField = span.childNodes[1];
    if (xformsArrayContains(inputField.className.split(" "), "xforms-type-date")
            && !inputField.disabled)
        span.xformsJscalendarOnClick();
}

function xformsSelect1CompactChanged(event) {
    var target = getEventTarget(event);
    target.selectedIndex = target.selectedIndex;
}

function xformsChangeHeightHandler(variant) {
    window.v = variant;
    var element = variant.tagName ? variant : getEventTarget(variant);
    var lineNumber = element.value.split("\n").length;
    if (lineNumber < 5) lineNumber = 5;
    element.style.height = 3 + lineNumber * 1.1 + "em";
}

function xformsUpdateStyle(element) {
    if (element.className) {
        var classes = element.className.split(" ");

        if (xformsArrayContains(classes, "xforms-mediatype-text-html") && xformsArrayContains(classes, "xforms-output")) {
            if (element.firstChild != null)
                element.innerHTML = xformsStringValue(element);
        }

        if (xformsArrayContains(classes, "xforms-output-html-initial")) {
            // Make the content of the element visible
            xformsRemoveClass(element, "xforms-output-html-initial");
        }

        if (xformsArrayContains(classes, "xforms-label")) {

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
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (xformsArrayContains(classes, "xforms-hint")) {

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
                // Add listeners on control
                var controlGeneratingEvent = xformsArrayContains(control.className.split(" "), "xforms-input")
                    ? control.childNodes[1] : control;
                xformsAddEventListener(controlGeneratingEvent, "focus", xformsSytleGetFocus);
                xformsAddEventListener(controlGeneratingEvent, "blur", xformsStyleLoosesFocus);
            }

            // Disable or enable hint depending if control is relevant
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (xformsArrayContains(classes, "xforms-help")) {

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
                var container = document.createElement("DIV");
                container.innerHTML = divHTML;
                var newDiv = container.firstChild;
                newDiv.helpMessage = control.helpMessage;
                document.body.appendChild(newDiv);
            }

            // Only add listener once
            if (!element.styleListenerRegistered) {
                element.styleListenerRegistered = true;
                xformsAddEventListener(element, "mouseover", xformsHelpMouseOver);
                xformsAddEventListener(element, "mouseout", tt_Hide);
            }

            // Disable or enable help depending if control is relevant
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (xformsArrayContains(classes, "xforms-alert-inactive") || xformsArrayContains(classes, "xforms-alert-active")) {

            // Initialize alert control when necessary
            var control = document.getElementById(element.htmlFor);
            if (control.alertElement != element)
                control.alertElement = element;

            // Change alert status when necessary
            if (xformsIsDefined(control.isValid)) {
                xformsAddClass(element, control.isValid ? "xforms-alert-inactive" : "xforms-alert-active");
                xformsRemoveClass(element, control.isValid ? "xforms-alert-active" : "xforms-alert-inactive");
            }

            // Change message if necessary
            if (control.alertMessage && control.xformsMessageLabel)
                xformsReplaceNodeText(control.xformsMessageLabel, control.alertMessage);

            // Set title of label with content of label
            element.title = xformsStringValue(element);

            // Disable or enable help depending if control is relevant
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (xformsArrayContains(classes, "xforms-input")) {

            var inputField = element.childNodes[1];
            var showCalendar = element.childNodes[2];

            if (xformsArrayContains(inputField.className.split(" "), "xforms-type-date")
                    && !element.styleListenerRegistered) {
                element.styleListenerRegistered = true;

                // Assign ids to input field and icon for date picker
                inputField.id = "input-" + element.id;
                showCalendar.id = "showcalendar-" + element.id;

                // Setup calendar library
                Calendar.setup({
                    inputField     :    inputField.id,
                    ifFormat       :    "%Y-%m-%d",
                    showsTime      :    false,
                    button         :    element.id,
                    singleClick    :    true,
                    step           :    1,
                    onUpdate       :    xformsCalendarUpdate,
                    electric       :    true
                });

                element.xformsJscalendarOnClick = element.onclick;
                element.onclick = xformsCalendarClick;
            }
        }

        if (xformsArrayContains(classes, "xforms-select1-compact")) {
            // Prevent end-user from selecting multiple values
            xformsAddEventListener(element, "change", xformsSelect1CompactChanged);
        }

        if (xformsArrayContains(classes, "xforms-trigger")) {
            // Update label on trigger
            if (typeof element.labelMessage != "undefined" && element.labelMessage != xformsStringValue(element)) {
                if (element.tagName.toLowerCase() == "input")
                    element.alt = element.labelMessage;
                else
                    xformsReplaceNodeText(element, element.labelMessage);
            }
        }

        if (xformsArrayContains(classes, "wide-textarea")) {
            if (!element.changeHeightHandlerRegistered) {
                element.changeHeightHandlerRegistered = true;
                xformsChangeHeightHandler(element);
                xformsAddEventListener(element, "keyup", xformsChangeHeightHandler);
            }
        }

        // This is for widgets. Code for widgets should be modularized and moved out of this file
        if (xformsArrayContains(classes, "widget-tabs") || xformsArrayContains(classes, "widget-tab-inactive")
                || xformsArrayContains(classes, "widget-tab-active") || xformsArrayContains(classes, "widget-tab-spacer-side")
                || xformsArrayContains(classes, "widget-tab-spacer-between")) {
            // Once the size of the table is set, do not change it
            if (!element.width)
                element.width = element.clientWidth;
        }

        // Update class on element based on its attributes
        xformsUpdateStyleRelevantReadonly(element, element.isRelevant, element.isReadonly, element.isRequired, element.isValid);
    }
}
