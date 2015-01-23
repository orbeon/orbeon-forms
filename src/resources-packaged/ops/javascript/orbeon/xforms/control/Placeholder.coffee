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
YD = YAHOO.util.Dom

browserSupportsPlaceholder = do ->
    input = document.createElement "input"
    input.placeholder?

# Returns true if this control is a placeholder
isPlaceholderControl = (control) ->
    if YD.hasClass control, "xforms-input"
        input = (control.getElementsByTagName "input")[0]
        placeholder = YD.getAttribute input, "placeholder"
        placeholder != null
    else false

# Populate the value with the placeholder (when users aren't editing the field)
showPlaceholder = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if YD.hasClass control, "xforms-placeholder"
        # Already showing the placeholder, update it
        input.value = $(input).attr('placeholder')
    else if input.value == ""
        # Field is empty, then we can show the placeholder
        YD.addClass control, "xforms-placeholder"
        input.value = $(input).attr('placeholder')

# Remove placeholder
hidePlaceholder = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if YD.hasClass control, "xforms-placeholder"
        YD.removeClass control, "xforms-placeholder"
        input.value = ""

# On DOM ready, get initial placeholder inputs and populate value with placeholder
do ->
    if not browserSupportsPlaceholder
        Event.onDOMReady ->
            # Initial initialization of placeholders
            for own formId, formInfo of orbeonInitData
                controls = formInfo.controls
                continue if not controls?
                for labelHint in ["label", "hint"]
                    continue if not controls[labelHint]?
                    placeholders = controls[labelHint]["minimal"]
                    continue if not placeholders?
                    for id in placeholders
                        control = YD.get id
                        input = $(control).find('input')
                        # Don't show the placeholder if the focus is already on this input
                        if not input.is(document.activeElement)
                            showPlaceholder control

# Call showPlaceholder/hidePlaceholder when users focus in and out of input
do ->
    if not browserSupportsPlaceholder
        addFocusListener = (name, f) ->
            Event.addListener document, name, (event) ->
                target = Event.getTarget event
                targetControl = YD.getAncestorByClassName target, "xforms-control"
                f targetControl if targetControl? and isPlaceholderControl targetControl
        addFocusListener "focusin", hidePlaceholder
        addFocusListener "focusout", showPlaceholder

# Call showPlaceholder/hidePlaceholder before/after the XForms engine changes the value
do ->
    if not browserSupportsPlaceholder
        addChangeListener = (customEvent, f) ->
            customEvent.subscribe (event) ->
                f(event.control) if isPlaceholderControl event.control
        addChangeListener Controls.beforeValueChange, hidePlaceholder
        addChangeListener Controls.afterValueChange, showPlaceholder

# When the label/hint changes, set the value of the placeholder
do ->
    Controls.lhhaChangeEvent.subscribe (event) ->
        if isPlaceholderControl event.control
            labelHint = Controls.getControlLHHA event.control, event.type
            if not labelHint?
                # Update placeholder attribute and show it
                input = (event.control.getElementsByTagName "input")[0]
                $(input).attr('placeholder', event.message)
                showPlaceholder event.control if not browserSupportsPlaceholder

# When getting the value of a placeholder input, if the placeholder is shown, the current value is empty string
do ->
    if not browserSupportsPlaceholder
        Controls.getCurrentValueEvent.subscribe (event) ->
            if isPlaceholderControl event.control
                if YD.hasClass event.control, "xforms-placeholder"
                    event.result = ""
