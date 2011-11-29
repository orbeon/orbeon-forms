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
Events = ORBEON.builder.Events
Event = YAHOO.util.Event
AjaxServer = ORBEON.xforms.server.AjaxServer
Controls = ORBEON.xforms.Controls

Event.onDOMReady () ->

    # Deal with mouse entering/existing gridTd
    do ->

        Events.mouseEntersGridTdEvent.subscribe ({triggers, gridTd}) ->
          # We're in a grid td, look for its content
          gridContent = (YD.getElementsByClassName "fr-grid-content", null, gridTd)[0]
          # Move cell editor controls under this td
          _.each triggers, (child) -> YD.insertBefore child, gridContent
          # Display or hide cell editor control depending on whether content is present
          emptyContent = (YD.getChildren gridContent).length is 0
          for trigger in triggers
              trigger.style.display =
                  # Only hide/show the icons, skip the in-place editors
                  if (_.any ["fb-grid-cell-icons", "fb-grid-control-icons"], (c) -> YD.hasClass trigger, c)
                      if emptyContent then "none" else ""

        Events.mouseExitsGridTdEvent.subscribe ({triggers}) ->
          # We're outside of a grid td: hide cell editor controls
          trigger.style.display = "none" for trigger in triggers when not YD.hasClass trigger, "xforms-input"


    # A click on a trigger changes the current cell
    do ->
        Events.triggerClickEvent.subscribe ({trigger}) ->
            # Send a DOMActivate to the closest xforms-activable ancestor
            activable = YD.getAncestorByClassName trigger, "xforms-activable"
            form = Controls.getForm activable
            event = new AjaxServer.Event form, activable.id, null, null, "DOMActivate"
            AjaxServer.fireEvents [event]

    # Show/hide input for label and hint
    do ->

        labelHintSavedHTMLFor = ''

        # Show input to edit label or hint, which is called on click on label or hint
        Events.startLabelHintEditEvent.subscribe ({inputs, labelHint}) ->
            # Save information about this label/hint
            labelHintSavedHTMLFor = labelHint.htmlFor
            # Remove 'for' so focus isn't set on the control on click
            labelHint.htmlFor = ''
            # Get the input we want to show
            outputInputClassTuples = [["xforms-label", "fb-control-label"], ["xforms-hint", "fb-control-hint"]]
            inputClass = (inputClass for [targetClass, inputClass] in outputInputClassTuples when YD.hasClass labelHint, targetClass)[0]
            editor = (e for e in inputs when YD.hasClass e, inputClass)[0]
            # Added editor as a children of the label
            labelHint.removeChild labelHint.childNodes[0] while labelHint.childNodes.length > 0
            labelHint.appendChild editor
            # Show and focus on editor
            editor.style.display = ""
            (editor.getElementsByTagName "input")[0].focus()

        # Hide input and restore value
        Events.endLabelHintEditEvent.subscribe ({labelHint}) ->
            # Move input out of label/hint back under container
            editor = YD.getFirstChild labelHint
            cellEditorsContainer = (YD.getElementsByClassName "fb-cell-editor", null, document)[0]
            cellEditorsContainer.appendChild editor
            # Restore text under label/hint
            labelHint.htmlFor = labelHintSavedHTMLFor
            editorValue = Controls.getCurrentValue editor
            OD.setStringValue labelHint, editorValue
            # Reset state
            labelHintSavedHTMLFor = ''
