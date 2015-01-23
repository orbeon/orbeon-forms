# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html

$ = ORBEON.jQuery
Controls = ORBEON.xforms.Controls
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
Init = ORBEON.xforms.Init
Properties = ORBEON.util.Properties
CalendarGroup = YAHOO.widget.CalendarGroup
Calendar = YAHOO.widget.Calendar
YD = YAHOO.util.Dom
DateTime = ORBEON.util.DateTime
CalendarResources = ORBEON.xforms.control.CalendarResources


appliesToControl = (control) ->
    (YD.hasClass control, "xforms-input") and \
        (_.find ["xforms-type-date", "xforms-type-time", "xforms-type-dateTime"], (c) -> YD.hasClass control, c)?

Event.onDOMReady ->

    setValue = (control, formattedDate) ->
        attributeName = if $(control).is(".xforms-input-appearance-minimal") then "alt" else "value"
        inputHolder = $(control).children('.xforms-input-input')
        $(inputHolder).attr(attributeName, formattedDate)
        value = Controls.getCurrentValue(control)
        changeEvent = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, value, "xxforms-value")
        ORBEON.xforms.server.AjaxServer.fireEvents([changeEvent], false)

    # Only enable date/time support on mobile WebKit for now. We don't detect if the browser supports the date/time types
    # as Safari does support them, but in such a minimal way that we wouldn't want to enable that on Safari.
    if YD.hasClass document.body, "xforms-ios"

        # On the iPad, use the native date/time picker
        initUnder = (node) ->
            forDate = ["input.xforms-type-date", DateTime.magicDateToJSDate, DateTime.jsDateToISODate, "date"]
            forTime = ["input.xforms-type-time", DateTime.magicTimeToJSDate, DateTime.jsDateToISOTime, "time"]
            for [cssClass, toJS, toISO, type] in [forDate, forTime]
                for input in node.querySelectorAll cssClass
                    inJS = toJS input.value
                    input.value = toISO inJS if inJS? # Can be null if ISO date/time can't be parsed
                    input.type = type

        # On page load, init everything under the document
        initUnder document.body

        # When an input becomes a date, initialize it just like we do when the page is loaded
        Controls.typeChangedEvent.subscribe (event) ->
            initUnder event.control if appliesToControl event.control

        # Fire change event on blur, as Mobile Safari doesn't fire the change event (http://goo.gl/jnMFj)
        Events.blurEvent.subscribe (event) ->
            dateOrTimeClasses = ["xforms-type-date", "xforms-type-time", "xforms-type-dateTime"]
            isDateOrTimeInput = true in (YD.hasClass event.control, c for c in dateOrTimeClasses)
            if isDateOrTimeInput
                value = Controls.getCurrentValue event.control
                changeEvent = new ORBEON.xforms.server.AjaxServer.Event null, event.control.id, value, "xxforms-value"
                ORBEON.xforms.server.AjaxServer.fireEvents [changeEvent], false

        # For iOS, store the ISO values as the input value
        Controls.valueChange.subscribe (event) ->
            if appliesToControl event.control
                if YD.hasClass event.control, "xforms-type-dateTime"
                    # Split the value and populate each input separately
                    values = event.newValue.split "T"
                    for [value, clazz] in (_.zip values, ["xforms-type-date", "xforms-type-time"])
                        input = (YD.getElementsByClassName clazz, "input", event.control)[0]
                        input.value = value
                else
                    # For date and time, just set value of the .xforms-input-input
                    setValue(event.control, event.newValue)

    else

        # For desktop browsers, use the YUI date picker

        # Set when the calendar is created the first time
        yuiCalendar = null
        yuiOverlay = null
        # Set when the calendar is opened for a given control
        control = null
        inputField = null
        # When calendar is in use, if the mouse in the calendar area
        mouseOverCalendar = false

        # Hide calendar div and do some cleanup of private variables
        closeCalendar = ->
            if yuiOverlay?
                control = null
                inputField = null
                mouseOverCalendar = false
                yuiOverlay.cfg.setProperty "visible", false

        # User selected a date in the picker
        calendarSelectEvent = ->
            jsDate = yuiCalendar.getSelectedDates()[0]
            formattedDate = DateTime.jsDateToFormatDisplayDate(jsDate)
            setValue(control, formattedDate)
            closeCalendar()

        # Open calendar on click
        Events.clickEvent.subscribe (event) ->

            # Show date picker on click on the input of type dates
            isDate = f$.is '.xforms-type-date', $ event.target
            canWrite = not f$.is '.xforms-readonly', $ event.control
            if isDate and canWrite

                control = event.control

                # Create or move overlay under this control
                calendarDiv = YD.get "orbeon-calendar-div"
                if not calendarDiv?
                    # Initialize calendarDiv and yuiOverlay
                    calendarDiv = document.createElement("div")
                    calendarDiv.id = "orbeon-calendar-div"
                    control.appendChild calendarDiv
                    yuiOverlay = new YAHOO.widget.Overlay calendarDiv, {constraintoviewport: true}
                    yuiOverlay.setBody ""
                    yuiOverlay.render()
                    # Create YUI calendar
                    hasTwoMonths = Properties.datePickerTwoMonths.get()
                    bd = (YD.getElementsByClassName "bd", null, calendarDiv)[0]
                    overlayBodyId = YD.generateId bd
                    yuiCalendar = if hasTwoMonths then new CalendarGroup overlayBodyId else new Calendar overlayBodyId

                    yuiCalendar.renderEvent.subscribe ->

                        # Keep track of whether the mouse pointer is inside or outside the calendar area
                        Event.addListener calendarDiv, "mouseover", -> mouseOverCalendar = true
                        Event.addListener calendarDiv, "mouseout", -> mouseOverCalendar = false

                        # Add our year navigator
                        monthLeft = (YD.getElementsByClassName "calnavleft", null, calendarDiv)[0]
                        yearLeft = document.createElement "a"
                        yearLeft.innerHTML = "Previous Year"
                        yearLeft.href = "#"
                        YD.addClass yearLeft, "calyearleft"
                        YD.insertBefore yearLeft, monthLeft
                        Event.addListener yearLeft, "click", (event) ->
                            Event.preventDefault event
                            # See comment in calendar.js doPreviousMonthNav() regarding the setTimeout()
                            setTimeout ->
                                    yuiCalendar.previousYear()
                                    newYearLeft = YD.getElementsByClassName "calyearleft", "a", calendarDiv
                                    newYearLeft[0].focus() if newYearLeft and newYearLeft[0]
                                , 0

                        # Add "following year" link
                        monthRight = (YD.getElementsByClassName "calnavright", null, calendarDiv)[0]
                        yearRight = document.createElement "a"
                        yearRight.innerHTML = "Next Year"
                        yearRight.href = "#"
                        YD.addClass yearRight, "calyearright"
                        YD.insertBefore yearRight, monthRight
                        Event.addListener yearRight, "click", (event) ->
                            Event.preventDefault event
                            # See comment in calendar.js doPreviousMonthNav() regarding the setTimeout()
                            setTimeout ->
                                    yuiCalendar.nextYear()
                                    newYearRight = YD.getElementsByClassName "calyearright", "a", calendarDiv
                                    newYearRight[0].focus() if newYearRight and newYearRight[0]
                                , 0

                    yuiCalendar.selectEvent.subscribe calendarSelectEvent
                else
                    # Move calendarDiv inside this date picker for scrolling
                    control.appendChild calendarDiv

                # Try to make sure the calendar appears in front of a dialog; doesn't work automatically as of 2008-12-10
                ORBEON.xforms.Globals.lastDialogZIndex += 2
                YD.setStyle calendarDiv, "z-index", ORBEON.xforms.Globals.lastDialogZIndex

                # Get language from html/@lang
                lang = ORBEON.util.Dom.getAttribute(document.documentElement, "lang")
                # Default to English if no language is specified
                lang = "en" if not lang? or lang is ""
                # Just keep first 2 letters (fr_FR becomes fr)
                lang = lang.substring 0, 2
                # Find resource for selected language
                resources = CalendarResources[lang]
                # Default to English if resources are not found
                resources = CalendarResources["en"] unless resources?
                # Pass resources to calendar
                yuiCalendar.cfg.setProperty key, resources.properties[key] for key of resources.properties

                if Properties.datePickerNavigator.get()
                    yuiCalendar.cfg.setProperty "navigator",
                        strings: resources.navigator
                        monthFormat: YAHOO.widget.Calendar.SHORT
                        initialFocus: "year"

                # Set date
                date = DateTime.magicDateToJSDate Controls.getCurrentValue control
                if not date?
                    yuiCalendar.cfg.setProperty "selected", "", false
                else
                    # Date must be the internal format expected by YUI
                    dateStringForYUI = (date.getMonth() + 1) + "/" + date.getDate() + "/" + date.getFullYear()
                    yuiCalendar.cfg.setProperty "selected", dateStringForYUI, false

                # Set min/max dates
                dateContainer = YD.getAncestorByClassName control, "xbl-fr-date"
                isDateContainer = dateContainer?
                mindateControl = (if isDateContainer then YD.getElementsByClassName("xbl-fr-date-mindate", null, dateContainer)[0] else null)
                yuiCalendar.cfg.setProperty "mindate", (if not mindateControl? then null else DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(mindateControl)))
                maxdateControl = (if isDateContainer then YD.getElementsByClassName("xbl-fr-date-maxdate", null, dateContainer)[0] else null)
                yuiCalendar.cfg.setProperty "maxdate", (if not maxdateControl? then null else DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(maxdateControl)))
                pagedateControl = (if isDateContainer then YD.getElementsByClassName("xbl-fr-date-pagedate", null, dateContainer)[0] else null)
                pagedateValue = (if not pagedateControl? then null else DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(pagedateControl)))
                yuiCalendar.cfg.setProperty "pagedate", (if not pagedateValue? then (if not date? then new Date() else date) else pagedateValue)

                # Show calendar
                ORBEON.xforms.Events.yuiCalendarCreated.fire { yuiCalendar: yuiCalendar, control: control }
                yuiCalendar.cfg.applyConfig()
                yuiCalendar.render()

                # Align overlay the best we can
                inputField = YD.getElementsByClassName("xforms-input-input", null, control)[0]
                # For minimal triggers, the element that gets the xforms-input-input is the image, and it doesn't have an id, so here we generate one
                YD.generateId inputField
                # 1. Show overlay below the input field
                yuiOverlay.cfg.setProperty "constraintoviewport", false
                yuiOverlay.cfg.setProperty "context", [ inputField.id, YAHOO.widget.Overlay.TOP_LEFT, YAHOO.widget.Overlay.BOTTOM_LEFT ]
                unless ORBEON.util.Utils.fitsInViewport(yuiOverlay.element)
                    # 2. If it was not entirely visible, show it above the input field
                    yuiOverlay.cfg.setProperty "context", [ inputField.id, YAHOO.widget.Overlay.BOTTOM_LEFT, YAHOO.widget.Overlay.TOP_LEFT ]
                    unless ORBEON.util.Utils.fitsInViewport(yuiOverlay.element)
                        # 3. If it was not entirely visible, do our best to make it visible
                        yuiOverlay.cfg.setProperty "constraintoviewport", true
                        yuiOverlay.cfg.setProperty "context", [ inputField.id, YAHOO.widget.Overlay.TOP_LEFT, YAHOO.widget.Overlay.TOP_RIGHT ]
                yuiOverlay.cfg.setProperty "visible", true
            else
                # Close date picker if the click is outside of the date picker area
                parentCalenderDiv = YD.getAncestorBy event.target, (e) -> e.id == "orbeon-calendar-div"
                closeCalendar() if not parentCalenderDiv?

        # Close calendar on blur
        Events.blurEvent.subscribe (event) ->
            if appliesToControl event.control
                closeCalendar() if not mouseOverCalendar

        # Close calendar when users start typing
        Events.keydownEvent.subscribe (event) ->
            if appliesToControl event.control
                closeCalendar() if event.target.className != "yui-cal-nav-yc"
