/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
ORBEON.widgets.YUICalendar = function() {

    /**
     * State shared amongst all calendars
     */

    // Set when the calendar is created the first time
    var yuiCalendar = null;
    var calendarDiv = null;
    var yuiOverlay = null;
    // Set when the calendar is opened for a given control
    var control = null;
    var inputField = null;
    // When calendar is in use, if the mouse in the calendar area
    var mouseOverCalendar = false;

    /**
     * Private listeners
     */

    // Keep track of whether the mouse pointer is inside or outside the calendar area
    function mouseover() {
        mouseOverCalendar = true;
    }

    function mouseout() {
        mouseOverCalendar = false;
    }

    // After the calendar is rendered, setup listeners on mouseover/mouseout
    function setupListeners() {
        YAHOO.util.Event.addListener(calendarDiv, "mouseover", mouseover);
        YAHOO.util.Event.addListener(calendarDiv, "mouseout", mouseout);
    }

    // User selected a date in the picker
    function dateSelected() {
        var jsDate = yuiCalendar.getSelectedDates()[0];
        inputField.value = ORBEON.util.DateTime.jsDateToFormatDisplayDate(jsDate);
		if(YAHOO.util.Dom.hasClass(control, "xforms-input-appearance-minimal"))
			inputField.alt = inputField.value;
        var event = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, null, ORBEON.xforms.Controls.getCurrentValue(control), "xxforms-value-change-with-focus-change");
        ORBEON.xforms.server.AjaxServer.fireEvents([event], false);
        closeCalendar();
    }

    // Hide calendar div and do some cleanup of private variables
    function closeCalendar() {
        // Nothing to do if the calendar was never opened
        if (yuiOverlay != null) {
            // Reset state
            control = null;
            inputField = null;
            mouseOverCalendar = false;
            // Hide calendar
            yuiOverlay.cfg.setProperty("visible", false);
            // Unsubscribe to global click
            ORBEON.xforms.Events.clickEvent.unsubscribe(clickAnywhere);
        }
    }

    // Listener on a click anywhere on the page, so we can close the calendar when we get a click on the background
    function clickAnywhere(type, arguments) {
        var event = arguments[0];
        var originalTarget = YAHOO.util.Event.getTarget(event);
        // Check if click was inside the date picker div
        var calendarContainer = YAHOO.util.Dom.getAncestorByClassName(originalTarget, "yui-calcontainer");
        // Close calendar if click was outside
        if (YAHOO.lang.isNull(calendarContainer))
            closeCalendar();
    }

    return {
        extending: ORBEON.widgets.Base,

        appliesToControl: function(control) {
            return YAHOO.util.Dom.hasClass(control, "xforms-type-date") || YAHOO.util.Dom.hasClass(control, "xforms-type-dateTime");
        },

        click: function(event, target) {
            if (calendarDiv == null && (calendarDiv = YAHOO.util.Dom.get("orbeon-calendar-div") == null)) {
                // Initialize calendarDiv and yuiOverlay
                calendarDiv = document.createElement("div");
                calendarDiv.id = "orbeon-calendar-div";
                target.appendChild(calendarDiv);
                yuiOverlay = new YAHOO.widget.Overlay(calendarDiv, { constraintoviewport: true });
                yuiOverlay.setBody(""); // Get overlay to create a body
                yuiOverlay.render();
            } else {
                // Move calendarDiv inside this date picker for scrolling
                target.appendChild(calendarDiv);
            }

            // Try to make sure the calendar appears in front of a dialog; doesn't work automatically as of 2008-12-10
            ORBEON.xforms.Globals.lastDialogZIndex += 2;
            YAHOO.util.Dom.setStyle(calendarDiv, "z-index", ORBEON.xforms.Globals.lastDialogZIndex);

            if (yuiCalendar == null) {
                // Create YUI calendar
                var hasTwoMonths = ORBEON.util.Properties.datePickerTwoMonths.get();
                var overlayBodyId = YAHOO.util.Dom.generateId(YAHOO.util.Dom.getElementsByClassName("bd", null, calendarDiv)[0]);
                yuiCalendar = hasTwoMonths
                    ? new YAHOO.widget.CalendarGroup(overlayBodyId)
                    : new YAHOO.widget.Calendar(overlayBodyId);

                // Listeners on calendar events
                yuiCalendar.renderEvent.subscribe(setupListeners, yuiCalendar, true);
                yuiCalendar.selectEvent.subscribe(dateSelected, yuiCalendar, true);

                // Listener on render event to add our year navigator
                window.yuiCalendar = yuiCalendar;
                yuiCalendar.renderEvent.subscribe(function() {
                    // Add "previous year" link
                    var monthLeft = YAHOO.util.Dom.getElementsByClassName("calnavleft", null, calendarDiv)[0];
                    var yearLeft = document.createElement("a");
                    yearLeft.innerHTML = "Previous Year";
                    yearLeft.href = "#";
                    YAHOO.util.Dom.addClass(yearLeft, "calyearleft");
                    YAHOO.util.Dom.insertBefore(yearLeft, monthLeft);
                    YAHOO.util.Event.addListener(yearLeft, "click", function(event) {
                        YAHOO.util.Event.preventDefault(event);
                        // See comment in calendar.js doPreviousMonthNav() regarding the setTimeout()
                        setTimeout(function() {
                            yuiCalendar.previousYear();
                            var newYearLeft = YAHOO.util.Dom.getElementsByClassName("calyearleft", "a", calendarDiv);
                            if (newYearLeft && newYearLeft[0]) newYearLeft[0].focus();
                        }, 0);
                    });
                    // Add "following year" link
                    var monthRight = YAHOO.util.Dom.getElementsByClassName("calnavright", null, calendarDiv)[0];
                    var yearRight = document.createElement("a");
                    yearRight.innerHTML = "Next Year";
                    yearRight.href = "#";
                    YAHOO.util.Dom.addClass(yearRight, "calyearright");
                    YAHOO.util.Dom.insertBefore(yearRight, monthRight);
                    YAHOO.util.Event.addListener(yearRight, "click", function(event) {
                        YAHOO.util.Event.preventDefault(event);
                        // See comment in calendar.js doPreviousMonthNav() regarding the setTimeout()
                        setTimeout(function() {
                            yuiCalendar.nextYear();
                            var newYearRight = YAHOO.util.Dom.getElementsByClassName("calyearright", "a", calendarDiv);
                            if (newYearRight && newYearRight[0]) newYearRight[0].focus();
                        }, 0);
                    });
                });
            }

            // Get language from html/@lang
            var lang = ORBEON.util.Dom.getAttribute(document.documentElement, "lang");
            // Default to English if no language is specified
            if (lang == null || lang == "")
                lang = "en";
            // Just keep first 2 letters (fr_FR becomes fr)
            lang = lang.substring(0, 2);
            // Find resource for selected language
            var resources = ORBEON.xforms.control.CalendarResources[lang];
            // Default to English if resources are not found
            if (resources == null)
                resources = ORBEON.xforms.control.CalendarResources["en"];
            for (var key in resources.properties)
                yuiCalendar.cfg.setProperty(key, resources.properties[key]);
            var hasNavigator = ORBEON.util.Properties.datePickerNavigator.get();
            if (hasNavigator) {
                yuiCalendar.cfg.setProperty("navigator", {
                    strings : resources.navigator,
                     monthFormat: YAHOO.widget.Calendar.SHORT,
                     initialFocus: "year"
                });
            }
            // Listen on clicks on the page, so we can close the dialog
            ORBEON.xforms.Events.clickEvent.subscribe(clickAnywhere);

            // Set date
            control = target;
            var date = ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(control));
            if (date == null) {
                yuiCalendar.cfg.setProperty("selected", "", false);
            } else {
                // Date must be the internal format expected by YUI
                var dateStringForYUI = (date.getMonth() + 1)
                   + "/" + date.getDate()
                   + "/" + date.getFullYear();
                yuiCalendar.cfg.setProperty("selected", dateStringForYUI, false);
            }
            // Set min/max dates
            var dateContainer = YAHOO.util.Dom.getAncestorByClassName(control, "xbl-fr-date");
            var isDateContainer = dateContainer != null;
            var mindateControl = isDateContainer ? YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-mindate", null, dateContainer)[0] : null;
            yuiCalendar.cfg.setProperty("mindate", mindateControl == null ? null :
                ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(mindateControl)));
            var maxdateControl = isDateContainer ? YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-maxdate", null, dateContainer)[0] : null;
            yuiCalendar.cfg.setProperty("maxdate", maxdateControl == null ? null :
                ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(maxdateControl)));
            var pagedateControl = isDateContainer ? YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-pagedate", null, dateContainer)[0] : null;
            var pagedateValue = pagedateControl == null ? null : ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(pagedateControl));
            yuiCalendar.cfg.setProperty("pagedate", pagedateValue == null ? (date == null ? new Date() : date) : pagedateValue);

            // Show calendar
            ORBEON.xforms.Events.yuiCalendarCreated.fire({ yuiCalendar: yuiCalendar, control: control });
            yuiCalendar.cfg.applyConfig();
            yuiCalendar.render();

            // Align overlay the best we can
            inputField = YAHOO.util.Dom.getElementsByClassName("xforms-input-input", null, target)[0];
            // For minimal triggers, the element that gets the xforms-input-input is the image, and it doesn't have an id, so here we generate one
            YAHOO.util.Dom.generateId(inputField);
            // 1. Show overlay below the input field
            yuiOverlay.cfg.setProperty("constraintoviewport", false);
            yuiOverlay.cfg.setProperty("context", [inputField.id, YAHOO.widget.Overlay.TOP_LEFT, YAHOO.widget.Overlay.BOTTOM_LEFT]);
            if (! ORBEON.util.Utils.fitsInViewport(yuiOverlay.element)) {
                // 2. If it was not entirely visible, show it above the input field
                yuiOverlay.cfg.setProperty("context", [inputField.id, YAHOO.widget.Overlay.BOTTOM_LEFT, YAHOO.widget.Overlay.TOP_LEFT]);
                if (! ORBEON.util.Utils.fitsInViewport(yuiOverlay.element)) {
                    // 3. If it was not entirely visible, do our best to make it visible
                    yuiOverlay.cfg.setProperty("constraintoviewport", true);
                    yuiOverlay.cfg.setProperty("context", [inputField.id, YAHOO.widget.Overlay.TOP_LEFT, YAHOO.widget.Overlay.TOP_RIGHT]);
                }
            }
            yuiOverlay.cfg.setProperty("visible", true);
        },

        blur: function(event, target) {
            // Close the calendar when the input is loosing the focus, and the mouse is not inside the calendar (i.e.
            // when users click somewhere else on the page, or on another field).
            if (! mouseOverCalendar) {
                closeCalendar();
            }
        },

        keydown: function(event, target) {
            // Close calendar when users start typing, except if they type in the year field
            var eventTarget = YAHOO.util.Event.getTarget(event);
            if (eventTarget.className != "yui-cal-nav-yc") closeCalendar();
        }
    };
}();

(function() {

    /**
     * Corresponds to <xforms:input> bound to node of type xs:date (xforms-type-date) or xs:dateTime (xforms-type-dateTime)
     */
    ORBEON.xforms.control.Calendar = function() {};

    var Control = ORBEON.xforms.control.Control;
    var Calendar = ORBEON.xforms.control.Calendar;
    var Globals = ORBEON.xforms.Globals;

    Calendar.prototype = new Control();

    Calendar.prototype.yuiCalendar = null;
    Calendar.prototype.calendarDiv = null;
    Calendar.prototype.yuiOverlay = null;
    Calendar.prototype.inputField = null;
    Calendar.prototype.mouseOverCalendar = false;

    Calendar.prototype.click = function(event, target) {

    };
})();