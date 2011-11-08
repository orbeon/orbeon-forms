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

Controls = ORBEON.xforms.Controls
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
YD = YAHOO.util.Dom

# Local state keeping track of the input where the label/hint is a placeholder
inputWithPlaceholder =
    label: []
    hint: []

# All the ids of placeholder inputs
inputWithPlaceholderIds = () ->
    result = []
    result.push id for id in ids for own labelHint, ids of inputWithPlaceholder
    result

# Populate the value with the placeholder (when users aren't editing the field)
showPlaceholder = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if input.value == ""
        YD.addClass control, "xforms-placeholder"
        input.value = input.placeholder

# Remove placeholder
hidePlaceholder = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if YD.hasClass control, "xforms-placeholder"
        YD.removeClass control, "xforms-placeholder"
        input.value = ""

# On DOM ready, get initial placeholder inputs and populate value with placeholder
do ->
    Event.onDOMReady ->
        # Store pointers to list of input with label/hint placeholder
        for own formId, formInfo of orbeonInitData
            controls = formInfo.controls
            continue if not controls?
            for labelHint in ["label", "hint"]
                continue if not controls[labelHint]?
                placeholders = controls[labelHint]["{http://orbeon.org/oxf/xml/xforms}placeholder"]
                continue if not placeholders?
                inputWithPlaceholder[labelHint] = placeholders
        # Populate value with placeholder
        showPlaceholder (YD.get id) for id in ids for own labelHint, ids of inputWithPlaceholder

# Call showPlaceholder/hidePlaceholder when users focus in and out of input
do ->
    addFocusListener = (name, f) ->
        Event.addListener document, name, (event) ->
            target = Event.getTarget event
            targetControl = YD.getAncestorByClassName target, "xforms-control"
            f targetControl if targetControl? and targetControl.id in inputWithPlaceholderIds()
    addFocusListener "focusin", hidePlaceholder
    addFocusListener "focusout", showPlaceholder

# Call showPlaceholder/hidePlaceholder before/after the XForms engine changes the value
do ->
    addChangeListener = (event, f) ->
        event.subscribe (type, args) ->
            target = args[0].target
            f(target) if target.id in inputWithPlaceholderIds()
    addChangeListener Events.beforeValueChange, hidePlaceholder
    addChangeListener Events.afterValueChange, showPlaceholder

# When the label/hint changes, set the value of the placeholder
do ->
    Controls.lhhaChangeEvent.subscribe (event) ->
        if event.control.id in inputWithPlaceholder[event.type]
            # Update placeholder attribute
            input = (event.control.getElementsByTagName "input")[0]
            input.placeholder = event.message
            # Update value if showing placeholder
            input.value = event.message if YD.hasClass event.control, "xforms-placeholder"

# When getting the value of a placeholder input, if the placeholder is shown, the current value is empty string
do ->
    Controls.getCurrentValueEvent.subscribe (event) ->
        if event.control.id in inputWithPlaceholderIds()
            if YD.hasClass event.control, "xforms-placeholder"
                event.result = ""