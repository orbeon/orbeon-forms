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
Events = ORBEON.xforms.Events
AjaxServer = ORBEON.xforms.server.AjaxServer
Controls = ORBEON.xforms.Controls

Event.onDOMReady () ->

    # Deal with the placeholders for label and hint
    do ->
        gridTdWithMouse = null

        showPlaceholder = (labelHint) ->
            if labelHint.childNodes.length == 0
                YD.addClass labelHint, 'fb-label-hint-placeholder'
                classesToIds = [['xforms-label', 'fb-enter-label'], ['xforms-hint', 'fb-enter-hint']]
                outputId = (ci[1] for ci in classesToIds when YD.hasClass labelHint, ci[0])[0]
                outputControl = YD.get outputId
                placeholderText = Controls.getCurrentValue outputControl
                OD.setStringValue labelHint, placeholderText
        hidePlaceholder = (labelHint) ->
            if YD.hasClass labelHint, 'fb-label-hint-placeholder'
                YD.removeClass labelHint, 'fb-label-hint-placeholder'
                OD.setStringValue labelHint, ''
        doOnLabelHint = (gridTd, f) ->
            for labelHintClass in ['xforms-label', 'xforms-hint']
                labelHint = (YD.getElementsByClassName labelHintClass, null, gridTd)[0]
                f labelHint if labelHint? # There could be no label or hint if the cell is empty

        # Show placeholder on mouse entering, and remove them on mouse exiting
        Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) ->
            gridTdWithMouse = gridTd
            doOnLabelHint gridTd, showPlaceholder
        Builder.mouseExitsGridTdEvent.subscribe ({gridTd}) ->
            gridTdWithMouse = null
            doOnLabelHint gridTd, hidePlaceholder

        # Remove placeholder class when start editing and show it when done editing
        Builder.startLabelHintEditEvent.subscribe ({labelHint}) -> hidePlaceholder labelHint
        Builder.endLabelHintEditEvent.subscribe ({labelHint}) ->
            Events.runOnNext Events.ajaxResponseProcessedEvent, ->
                isLabelHintInCurrentGridTd =
                    if gridTdWithMouse? false
                    else
                        parentGridTd = YD.getAncestorByClassName labelHint, 'fr-grid-td'
                        parentGridTd? and parentGridTd == gridTdWithMouse
                showPlaceholder labelHint if isLabelHintInCurrentGridTd

    # Deal with mouse entering/existing gridTd
    do ->

        Builder.mouseEntersGridTdEvent.subscribe ({triggers, gridTd}) ->
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

        Builder.mouseExitsGridTdEvent.subscribe ({triggers}) ->
          # We're outside of a grid td: hide cell editor controls
          trigger.style.display = "none" for trigger in triggers when not YD.hasClass trigger, "xforms-input"


    # A click on a trigger changes the current cell
    do ->
        Builder.triggerClickEvent.subscribe ({trigger}) ->
            # Send a DOMActivate to the closest xforms-activable ancestor
            activable = YD.getAncestorByClassName trigger, "xforms-activable"
            form = Controls.getForm activable
            event = new AjaxServer.Event form, activable.id, null, null, "DOMActivate"
            AjaxServer.fireEvents [event]

    # Show/hide input for label and hint
    do ->

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
        Builder.mouseEntersGridTdEvent.subscribe ({triggers, gridTd}) ->
            label = (YD.getElementsByClassName 'xforms-label', null, gridTd)[0]
            labelSavedHTMLFor = label?.htmlFor
            label?.htmlFor = ''

        # Restore htmlFor on existing the gridTd
        Builder.mouseExitsGridTdEvent.subscribe ({triggers, gridTd}) ->
            label = (YD.getElementsByClassName 'xforms-label', null, gridTd)[0]
            label?.htmlFor = labelSavedHTMLFor
            labelSavedHTMLFor = null
