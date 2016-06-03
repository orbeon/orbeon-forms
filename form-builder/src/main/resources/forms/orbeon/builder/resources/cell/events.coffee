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
createCustomEvent = () -> new YAHOO.util.CustomEvent null, null, false, YAHOO.util.CustomEvent.FLAT

ORBEON.Builder =
    mouseEntersGridTdEvent:     createCustomEvent()
    mouseExitsGridTdEvent:      createCustomEvent()
    triggerClickEvent:          createCustomEvent()
    controlAdded:               $.Callbacks()

YD = YAHOO.util.Dom
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
Builder = ORBEON.Builder

$ ->

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
            [['fb-grid-cell-icons', 'fb-grid-control-icons', 'fb-edit-items', 'fb-static-upload'], cellEditorTriggerGroups]
        ]
        for child in YD.getChildren cellEditorsContainer
            for mapping in classToCategory
                for cssClass in mapping[0]
                    mapping[1].push child if YD.hasClass child, cssClass
        gs = $(cellEditorTriggerGroups)
        # NOTE: I'm not happy with the following; see question http://goo.gl/Dn1DI
        triggerSelector = '.xforms-trigger, .xforms-upload'
        cellEditorTriggers = $.merge(gs.find(triggerSelector), gs.filter(triggerSelector))

    # Fires events related to mouse entering or existing a grid cell
    do ->
        currentMouseOverGridThTd = null

        buildGridTdEvent = (gridTd) ->
            triggerGroups: cellEditorTriggerGroups
            triggers: cellEditorTriggers
            inputs: cellEditorInputs
            gridTd: gridTd

        Event.addListener document, "mouseover", (event) ->
            target = Event.getTarget event
            gridThTd =
                # Give up we we're not in an editable grid, e.g. section template
                if not $(target).closest('.fr-grid').is('.fr-editable') then null
                # Target is the grid td, we're good
                else if $(target).is('.fb-grid-th, .fb-grid-td') then target
                # Try finding a grid td parent of the target
                else $(target).closest('.fb-grid-th, .fb-grid-td')[0]
            if gridThTd?
                if currentMouseOverGridThTd
                    if gridThTd isnt currentMouseOverGridThTd
                        # From one gridTd to another gridTd
                        Builder.mouseExitsGridTdEvent.fire buildGridTdEvent currentMouseOverGridThTd
                        currentMouseOverGridThTd = gridThTd
                        Builder.mouseEntersGridTdEvent.fire buildGridTdEvent gridThTd
                else
                    # First time in a gridTd
                    currentMouseOverGridThTd = gridThTd
                    Builder.mouseEntersGridTdEvent.fire buildGridTdEvent gridThTd
            else
                if currentMouseOverGridThTd
                    # Exiting a gridTd
                    Builder.mouseExitsGridTdEvent.fire buildGridTdEvent currentMouseOverGridThTd
                    currentMouseOverGridThTd = null

    # Fire event on click on trigger
    do ->
        Events.clickEvent.subscribe ({control}) ->
            if control in cellEditorTriggers
                Builder.triggerClickEvent.fire {trigger: control}
