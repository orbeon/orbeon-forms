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

AjaxServer = ORBEON.xforms.server.AjaxServer
Builder = ORBEON.Builder
Controls = ORBEON.xforms.Controls
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
YD = YAHOO.util.Dom

# Rules defining when certain triggers are relevant
isNotEmpty = (gridTd) -> $(gridTd).children('.fr-grid-content').children().length > 0
relevanceRules =
    'fb-expand-trigger':            (gridTd) -> gridTd.rowSpan <= $(gridTd).parent().nextAll('tr:not(.xforms-repeat-delimiter):not(.xforms-repeat-template):not(.xforms-repeat-begin-end):not(.xforms-group-begin-end)').length
    'fb-shrink-trigger':            (gridTd) -> gridTd.rowSpan >= 2
    'fb-delete-trigger':            isNotEmpty
    'fb-edit-details-trigger':      isNotEmpty
    'fb-edit-validation-trigger':   isNotEmpty
    'fb-edit-items-trigger':        (gridTd) -> isNotEmpty(gridTd) and $(gridTd).children('.fr-grid-content').hasClass('fb-itemset')

$ ->

    # Save reference to last operation that positioned the
    lastPositionTriggers = null

    Builder.mouseEntersGridTdEvent.subscribe ({triggers, triggerGroups, gridTd}) ->
        gridTdId = gridTd.id
        lastPositionTriggers = ->
            # Move cell editor controls under this td
            gridTd = YD.get gridTdId
            if gridTd?
                $(gridTd).children('.fr-grid-content').before(triggerGroups)
                $(triggerGroups).css('display', '')
                # Show/hide triggers depending on relevance rules
                for trigger in triggers
                    rule = relevanceRules[trigger.id]
                    triggerRelevant = if rule? then rule gridTd else true
                    trigger.style.display = if triggerRelevant then '' else 'none'
        lastPositionTriggers()

    Builder.mouseExitsGridTdEvent.subscribe ({triggerGroups}) ->
      # We're outside of a grid td: hide cell editor controls
      triggerGroup.style.display = "none" for triggerGroup in triggerGroups
      lastPositionTriggers = null

    # Change current cell on click on trigger
    Builder.triggerClickEvent.subscribe ({trigger}) ->
        # Send a DOMActivate to the closest xforms-activable ancestor
        activable = YD.getAncestorByClassName trigger, "xforms-activable"
        form = Controls.getForm activable
        event = new AjaxServer.Event form, activable.id, null, null, "DOMActivate"
        AjaxServer.fireEvents [event]

    # Reposition triggers after an Ajax request
    Events.ajaxResponseProcessedEvent.subscribe ->
        lastPositionTriggers() if lastPositionTriggers?
