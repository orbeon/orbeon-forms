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
        YAHOO.util.Dom.addClass(element, "xforms-readonly");
    } else {
        element.removeAttribute("disabled");
        YAHOO.util.Dom.removeClass(element, "xforms-readonly");
    }
}

function xformsUpdateStyleRelevantReadonly(element, relevant, readonly, required, valid) {
    if (xformsIsDefined(relevant)) {
        if (relevant) YAHOO.util.Dom.removeClass(element, "xforms-disabled")
        else YAHOO.util.Dom.addClass(element, "xforms-disabled");
    }
    if (xformsIsDefined(readonly)) {
        if (YAHOO.util.Dom.hasClass(element, "xforms-input")) {
            // XForms input

            // Display value
            var displayValue = element.firstChild;
            while (displayValue.nodeType != ELEMENT_TYPE) displayValue = displayValue.nextSibling;
            if (readonly) YAHOO.util.Dom.addClass(displayValue, "xforms-readonly");
            else YAHOO.util.Dom.removeClass(displayValue, "xforms-readonly");

            // Text field
            var textField = displayValue.nextSibling;
            while (textField.nodeType != ELEMENT_TYPE) textField = textField.nextSibling;
            if (readonly) textField.setAttribute("disabled", "disabled");
            else textField.removeAttribute("disabled");

            // Calendar picker
            var showCalendar = textField.nextSibling;
            while (showCalendar.nodeType != ELEMENT_TYPE) showCalendar = showCalendar.nextSibling;
            if (readonly) YAHOO.util.Dom.addClass(showCalendar, "xforms-showcalendar-readonly");
            else YAHOO.util.Dom.removeClass(showCalendar, "xforms-showcalendar-readonly");
        } else if (YAHOO.util.Dom.hasClass(element, "xforms-output") || YAHOO.util.Dom.hasClass(element, "xforms-group")) {
            // XForms output and group
            if (readonly) YAHOO.util.Dom.addClass(element, "xforms-readonly");
            else YAHOO.util.Dom.removeClass(element, "xforms-readonly");
        } else if (YAHOO.util.Dom.hasClass(element, "xforms-select1-full") || YAHOO.util.Dom.hasClass(element, "xforms-select-full")) {
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
            YAHOO.util.Dom.addClass(element, "xforms-required");
            if (element.value == "") {
                YAHOO.util.Dom.addClass(element, "xforms-required-empty");
                YAHOO.util.Dom.removeClass(element, "xforms-required-filled");
            } else {
                YAHOO.util.Dom.addClass(element, "xforms-required-filled");
                YAHOO.util.Dom.removeClass(element, "xforms-required-empty");
            }
        } else {
            YAHOO.util.Dom.removeClass(element, "xforms-required");
            YAHOO.util.Dom.removeClass(element, "xforms-required-filled");
            YAHOO.util.Dom.removeClass(element, "xforms-required-empty");
        }
    }
    if (xformsIsDefined(valid)) {
        if (valid) YAHOO.util.Dom.removeClass(element, "xforms-invalid")
        else YAHOO.util.Dom.addClass(element, "xforms-invalid");
    }
}

function xformsSytleGetFocus(event) {
    var target = getEventTarget(event);
    if (!YAHOO.util.Dom.hasClass(target, "xforms-control"))
        target = target.parentNode;
    var hintLabel = target;
    while (true) {
        if (YAHOO.util.Dom.hasClass(hintLabel, "xforms-hint") && hintLabel.htmlFor == target.id) {
            YAHOO.util.Dom.removeClass(hintLabel, "xforms-hint");
            YAHOO.util.Dom.addClass(hintLabel, "xforms-hint-active");
            break;
        }
        hintLabel = hintLabel.nextSibling;
        if (hintLabel == null) break;
    }
}

function xformsStyleLoosesFocus(event) {
    var target = getEventTarget(event);
    if (!YAHOO.util.Dom.hasClass(target, "xforms-control"))
        target = target.parentNode;
    var hintLabel = target;
    while (true) {
        if (YAHOO.util.Dom.hasClass(hintLabel, "xforms-hint-active") && hintLabel.htmlFor == target.id) {
            YAHOO.util.Dom.removeClass(hintLabel, "xforms-hint-active");
            YAHOO.util.Dom.addClass(hintLabel, "xforms-hint");
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
    if (YAHOO.util.Dom.hasClass(inputField, "xforms-type-date")
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

        if (YAHOO.util.Dom.hasClass(element, "xforms-mediatype-text-html")
                && YAHOO.util.Dom.hasClass(element, "xforms-output")) {
            if (element.firstChild != null)
                element.innerHTML = xformsStringValue(element);
        }

        if (YAHOO.util.Dom.hasClass(element, "xforms-initially-hidden")) {
            // Make the content of the element visible
            YAHOO.util.Dom.removeClass(element, "xforms-initially-hidden");
        }

        if (YAHOO.util.Dom.hasClass(element, "xforms-label")) {

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

        if (YAHOO.util.Dom.hasClass(element, "xforms-hint")) {

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
                var controlGeneratingEvent = YAHOO.util.Dom.hasClass(control, "xforms-input")
                    ? control.childNodes[1] : control;
                YAHOO.util.Event.addListener(controlGeneratingEvent, "focus", xformsSytleGetFocus);
                YAHOO.util.Event.addListener(controlGeneratingEvent, "blur", xformsStyleLoosesFocus);
            }

            // Disable or enable hint depending if control is relevant
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (YAHOO.util.Dom.hasClass(element, "xforms-help")) {

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
                YAHOO.util.Event.addListener(element, "mouseover", xformsHelpMouseOver);
                YAHOO.util.Event.addListener(element, "mouseout", tt_Hide);
            }

            // Disable or enable help depending if control is relevant
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (YAHOO.util.Dom.hasClass(element, "xforms-alert-inactive") || YAHOO.util.Dom.hasClass(element, "xforms-alert-active")) {

            // Initialize alert control when necessary
            var control = document.getElementById(element.htmlFor);
            if (control.alertElement != element)
                control.alertElement = element;

            // Change alert status when necessary
            if (xformsIsDefined(control.isValid)) {
                YAHOO.util.Dom.addClass(element, control.isValid ? "xforms-alert-inactive" : "xforms-alert-active");
                YAHOO.util.Dom.removeClass(element, control.isValid ? "xforms-alert-active" : "xforms-alert-inactive");
            }

            // Change message if necessary
            if (control.alertMessage)
                xformsReplaceNodeText(element, control.alertMessage);

            // Set title of label with content of label
            element.title = xformsStringValue(element);

            // Disable or enable help depending if control is relevant
            xformsUpdateStyleRelevantReadonly(element, control.isRelevant, control.isReadonly, control.isRequired, control.isValid);
        }

        if (YAHOO.util.Dom.hasClass(element, "xforms-input")) {

            var inputField = element.childNodes[1];
            var showCalendar = element.childNodes[2];

            if (YAHOO.util.Dom.hasClass(inputField, "xforms-type-date")
                    && !element.styleListenerRegistered) {
                element.styleListenerRegistered = true;

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

        if (YAHOO.util.Dom.hasClass(element, "xforms-select1-compact")) {
            // Prevent end-user from selecting multiple values
            YAHOO.util.Event.addListener(element, "change", xformsSelect1CompactChanged);
        }

        if (YAHOO.util.Dom.hasClass(element, "xforms-trigger")) {
            // Update label on trigger
            if (typeof element.labelMessage != "undefined" && element.labelMessage != xformsStringValue(element)) {
                if (element.tagName.toLowerCase() == "input")
                    element.alt = element.labelMessage;
                else
                    xformsReplaceNodeText(element, element.labelMessage);
            }
        }

        if (YAHOO.util.Dom.hasClass(element, "wide-textarea")) {
            if (!element.changeHeightHandlerRegistered) {
                element.changeHeightHandlerRegistered = true;
                xformsChangeHeightHandler(element);
                YAHOO.util.Event.addListener(element, "keyup", xformsChangeHeightHandler);
            }
        }

        // Update class on element based on its attributes
        xformsUpdateStyleRelevantReadonly(element, element.isRelevant, element.isReadonly, element.isRequired, element.isValid);
    }
}
