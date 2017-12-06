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

# Returns true if this control has a placeholder
isPlaceholderControl = (control) ->
    if $(control).is('.xforms-input, .xforms-textarea')
        inputOrTextarea = findInputOrTextarea control
        placeholder = YD.getAttribute inputOrTextarea, "placeholder"
        placeholder != null
    else
        false

findInputOrTextarea = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if input?
        input
    else
        (control.getElementsByTagName "textarea")[0]

# Populate the value with the placeholder (when users aren't editing the field)
showPlaceholder = (control) ->
    inputOrTextarea = findInputOrTextarea control
    if $(control).is('.xforms-placeholder')
        # Already showing the placeholder, update it
        inputOrTextarea.value = $(inputOrTextarea).attr('placeholder')
    else if inputOrTextarea.value == ""
        # Field is empty, then we can show the placeholder
        YD.addClass control, "xforms-placeholder"
        inputOrTextarea.value = $(inputOrTextarea).attr('placeholder')

# Remove placeholder
hidePlaceholder = (control) ->
    inputOrTextarea = findInputOrTextarea control
    if $(control).is('.xforms-placeholder')
        YD.removeClass control, "xforms-placeholder"
        inputOrTextarea.value = ""

# When the label/hint changes, set the value of the placeholder
do ->
    Controls.lhhaChangeEvent.subscribe (event) ->
        if isPlaceholderControl event.control
            labelHint = Controls.getControlLHHA event.control, event.type
            if not labelHint?
                # Update placeholder attribute and show it
                inputOrTextarea = findInputOrTextarea event.control
                if inputOrTextarea?
                    $(inputOrTextarea).attr('placeholder', event.message)
