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

Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
YD = YAHOO.util.Dom

# Get the ids of all the inputs that are placeholders
placeHolderIds = do ->
    result = null
    ->
        if not result?
            result = []
            for own formId, formInfo of orbeonInitData
                controls = formInfo.controls
                continue if not controls?
                for labelHint in [controls.label, controls.hint]
                    continue if not labelHint?
                    placeholders = labelHint["{http://orbeon.org/oxf/xml/xforms}placeholder"]
                    continue if not placeholders?
                    result.push id for id in placeholders
        result

# Populate the value with the placeholder (when users aren't editing the field)
onFocusOut = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if input.value == ""
        YD.addClass control, "xforms-placeholder"
        input.value = input.placeholder

# Remove placeholder
onFocusIn = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if YD.hasClass control, "xforms-placeholder"
        YD.removeClass control, "xforms-placeholder"
        input.value = ""

# Call onFocusOut/onFocusIn when users focus in and out of input
addFocusListener = (name, f) ->
    Event.addListener document, name, (event) ->
        target = Event.getTarget event
        targetControl = YD.getAncestorByClassName target, "xforms-control"
        f targetControl if targetControl? and targetControl.id in placeHolderIds()
addFocusListener "focusin", onFocusIn
addFocusListener "focusout", onFocusOut

# Call onFocusOut/onFocusIn before/after the XForms engine changes the value
addChangeListener = (event, f) ->
    event.subscribe (type, args) ->
        target = args[0].target
        f(target) if target.id in placeHolderIds()
addChangeListener Events.beforeValueChange, onFocusIn
addChangeListener Events.afterValueChange, onFocusOut

# On DOM ready, populate value with placeholder
Event.onDOMReady ->
    onFocusOut (YD.get id) for id in placeHolderIds()
