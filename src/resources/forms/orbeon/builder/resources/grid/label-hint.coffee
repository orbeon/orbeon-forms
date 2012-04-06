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

YD = YAHOO.util.Dom
OD = ORBEON.util.Dom
Builder = ORBEON.Builder
Event = YAHOO.util.Event
Controls = ORBEON.xforms.Controls

# Show/hide input for label and hint

Event.onDOMReady () ->

    labelSavedHTMLFor = null

    # Show input to edit label or hint, which is called on click on label or hint
    Builder.startLabelHintEditEvent.subscribe ({inputs, labelHint}) ->
        # We use CSS to override the xforms-disabled on hints so the placeholder shows, but that CSS isn't
        # enough to un-hide the input, so here we just remove that xforms-disabled class
        YD.removeClass labelHint, 'xforms-disabled'
        # Get the input we want to show
        outputInputClassTuples = [["xforms-label", "fb-control-label"], ["xforms-hint", "fb-control-hint"]]
        inputClass = (inputClass for [targetClass, inputClass] in outputInputClassTuples when YD.hasClass labelHint, targetClass)[0]
        editor = (e for e in inputs when YD.hasClass e, inputClass)[0]
        # Added editor as a children of the label
        labelHint.removeChild labelHint.firstChild while labelHint.firstChild
        labelHint.appendChild editor
        # Show and focus on editor
        editor.style.display = ""
        (editor.getElementsByTagName "input")[0].focus()

    # Hide input and restore value
    Builder.endLabelHintEditEvent.subscribe ({labelHint}) ->
        # Move input out of label/hint back under container
        editor = YD.getFirstChild labelHint
        cellEditorsContainer = (YD.getElementsByClassName "fb-cell-editor", null, document)[0]
        cellEditorsContainer.appendChild editor
        # Restore text under label/hint
        editorValue = Controls.getCurrentValue editor
        OD.setStringValue labelHint, editorValue

    # Remove 'for' so focus isn't set on the control on click
    Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) ->
        label = (YD.getElementsByClassName 'xforms-label', null, gridTd)[0]
        labelSavedHTMLFor = label?.htmlFor
        label?.htmlFor = ''

    # Restore htmlFor on existing the gridTd
    Builder.mouseExitsGridTdEvent.subscribe ({gridTd}) ->
        label = (YD.getElementsByClassName 'xforms-label', null, gridTd)[0]
        label?.htmlFor = labelSavedHTMLFor
        labelSavedHTMLFor = null

