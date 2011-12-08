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
Controls = ORBEON.xforms.Controls

# Show/hide placeholders for label and hint

Event.onDOMReady () ->

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

