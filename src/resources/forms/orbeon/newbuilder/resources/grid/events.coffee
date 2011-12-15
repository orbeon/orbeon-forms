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

createCustomEvent = () -> new YAHOO.util.CustomEvent null, null, false, YAHOO.util.CustomEvent.FLAT

ORBEON.Builder =
    mouseEntersGridTdEvent:     createCustomEvent()
    mouseExitsGridTdEvent:      createCustomEvent()
    startLabelHintEditEvent:    createCustomEvent()
    endLabelHintEditEvent:      createCustomEvent()
    triggerClickEvent:          createCustomEvent()

YD = YAHOO.util.Dom
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
Builder = ORBEON.Builder

Event.onDOMReady () ->

    # Get a reference to the icons to the top left of the cell
    cellEditorsContainer = null
    cellEditorInputs = []
    cellEditorTriggerGroups = []
    cellEditorTriggers = []

    # Populate above variables
    do ->
        cellEditorsContainer = (YD.getElementsByClassName "fb-cell-editor", null, document)[0]
        classToCategory = [
            [['xforms-input'], cellEditorInputs]
            [['fb-grid-cell-icons', 'fb-grid-control-icons', 'fb-edit-items'], cellEditorTriggerGroups]
        ]
        for child in YD.getChildren cellEditorsContainer
            for mapping in classToCategory
                for cssClass in mapping[0]
                    mapping[1].push child if YD.hasClass child, cssClass
        gs = $(cellEditorTriggerGroups)
        # NOTE: I'm not happy with the following; see question http://goo.gl/Dn1DI
        cellEditorTriggers = $.merge(gs.find('.xforms-trigger'), gs.filter('.xforms-trigger'))

    # Fires events related to mouse entering or existing a grid cell
    do ->
        currentMouseOverGridTd = null

        buildGridTdEvent = (gridTd) ->
            triggerGroups: cellEditorTriggerGroups
            triggers: cellEditorTriggers
            inputs: cellEditorInputs
            gridTd: gridTd

        Event.addListener document, "mouseover", (event) ->
            target = Event.getTarget event
            gridTd =
                # Target is the grid td, we're good
                if YD.hasClass target, "fr-grid-td" then target
                # Try finding a grid td parent of the target
                else YD.getAncestorByClassName target, "fr-grid-td"
            if gridTd?
                if currentMouseOverGridTd?
                    if gridTd isnt currentMouseOverGridTd
                        # From one gridTd to another gridTd
                        Builder.mouseExitsGridTdEvent.fire buildGridTdEvent currentMouseOverGridTd
                        currentMouseOverGridTd = gridTd
                        Builder.mouseEntersGridTdEvent.fire buildGridTdEvent gridTd
                else
                    # First time in a gridTd
                    currentMouseOverGridTd = gridTd
                    Builder.mouseEntersGridTdEvent.fire buildGridTdEvent gridTd
            else
                if currentMouseOverGridTd?
                    # Exiting a gridTd
                    Builder.mouseExitsGridTdEvent.fire buildGridTdEvent currentMouseOverGridTd
                    currentMouseOverGridTd = null

    # Fire event on click on trigger
    do ->
        Events.clickEvent.subscribe ({control}) ->
            if control in cellEditorTriggers
                Builder.triggerClickEvent.fire {trigger: control}

    # Fire events related to users starting or being done with editing a label/hint
    do ->
        labelHint = null

        Events.clickEvent.subscribe ({target}) ->
            isLabel = YD.hasClass target, 'xforms-label'
            isHint = YD.hasClass target, 'xforms-hint'
            isInGridTd = (YD.getAncestorByClassName target, "fr-grid-td")?
            if (isLabel or isHint) and isInGridTd
              # Wait for Ajax response before showing editor to make sure the editor is bound to the current control
              Events.runOnNext Events.ajaxResponseProcessedEvent, ->
                  labelHint = target
                  Builder.startLabelHintEditEvent.fire {inputs: cellEditorInputs, labelHint}

        # Update labelHint before we call hide so not to call hide more than once
        fireEndHideLabelHintInput = ->
            currentLabelHint = labelHint
            labelHint = null
            Builder.endLabelHintEditEvent.fire {inputs: cellEditorInputs, labelHint: currentLabelHint}

        # On enter or escape, restore label/hint to its view mode
        Events.keypressEvent.subscribe ({control, keyCode}) ->
            if labelHint? and control.parentNode == labelHint and keyCode == 13
                fireEndHideLabelHintInput()
        # On blur of input, restore value
        Events.blurEvent.subscribe ({control}) ->
            if labelHint?
                editor = YD.getFirstChild labelHint
                fireEndHideLabelHintInput() if control == editor
