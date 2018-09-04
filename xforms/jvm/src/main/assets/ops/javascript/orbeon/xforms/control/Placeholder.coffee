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

findInputOrTextarea = (control) ->
    input = (control.getElementsByTagName "input")[0]
    if input?
        input
    else
        (control.getElementsByTagName "textarea")[0]

# When the label/hint changes, set the value of the placeholder
do ->
    Controls.lhhaChangeEvent.subscribe (event) ->
        if $(event.control).is('.xforms-input, .xforms-textarea')
            labelHint = Controls.getControlLHHA event.control, event.type
            if not labelHint?
                # Update placeholder attribute and show it
                inputOrTextarea = findInputOrTextarea event.control
                if inputOrTextarea?
                    $(inputOrTextarea).attr('placeholder', event.message)
