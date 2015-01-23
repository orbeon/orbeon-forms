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
AjaxServer = ORBEON.xforms.server.AjaxServer
Builder = ORBEON.Builder
Controls = ORBEON.xforms.Controls
Event = YAHOO.util.Event
Events = ORBEON.xforms.Events
YD = YAHOO.util.Dom

# Rules defining when certain triggers are relevant
relevanceRules = do ->

    isNotEmpty = (gridTd) -> $(gridTd).find('.fr-grid-content').children().length > 0
    isUpload = (gridTd) -> isNotEmpty(gridTd) and $(gridTd).find('.fr-grid-content').hasClass('fb-upload')
    isEmptyUpload = (gridTd) ->
        # Check for presence of both spacer and photo placeholder, as both indicate an empty image and we are not sure which one will be shown
        # depending on whether the code replacing the spacer with the photo placeholder already ran or not
        spacer = '/ops/images/xforms/spacer.gif'
        photo = '/apps/fr/style/images/silk/photo.png'
        $(gridTd).find(".xforms-output-output[src $= '#{spacer}'], .xforms-output-output[src $= '#{photo}']").length > 0

    'fb-expand-trigger':            (gridTd) ->
                                        noDelimiter = ':not(.xforms-repeat-delimiter):not(.xforms-repeat-template):not(.xforms-repeat-begin-end):not(.xforms-group-begin-end)'
                                        grid = $(gridTd).closest('.fr-grid')
                                        if grid.hasClass('fr-norepeat')                                                                     # Regular table
                                            gridTd.rowSpan <= $(gridTd).parent().nextAll('tr' + noDelimiter).length                         #   Based on number of following non-internal rows
                                        else if grid.hasClass('fr-repeat-multiple-rows')                                                    # Repeat with multiple rows in each iteration
                                            gridTr =$(gridTd).parent()                                                                      #   Based on number of following row with the same parity
                                            oddEvenClass = if gridTr.hasClass('yui-dt-even') then 'yui-dt-even' else 'yui-dt-odd'           #   Rows with a different parity belong to a different iteration
                                            gridTd.rowSpan <= gridTr.next('.' + oddEvenClass).length
                                        else if grid.hasClass('fr-repeat-single-row') then false                                            # Repeat with single row: no expension possible
                                        else false                                                                                          # Catch all, which shouldn't happen
    'fb-shrink-trigger':            (gridTd) -> gridTd.rowSpan >= 2
    'fb-delete-trigger':            isNotEmpty
    'fb-edit-details-trigger':      isNotEmpty
    'fb-edit-validation-trigger':   isNotEmpty
    'fb-edit-items-trigger':        (gridTd) -> isNotEmpty(gridTd) and $(gridTd).find('.fr-grid-content').hasClass('fb-itemset')
    'fb-static-upload-empty':       (gridTd) -> isUpload(gridTd) and isEmptyUpload(gridTd)
    'fb-static-upload-non-empty':   (gridTd) -> isUpload(gridTd) and not isEmptyUpload(gridTd)

Builder.beforeAddingEditorCallbacks = $.Callbacks()

$ ->

    # Save reference to last operation that positioned the triggers
    lastPositionTriggers = null

    # Create a div.fb-hover inside the td, if there isn't one already
    # We do this because Firefox doesn't support position: relative on table cells (http://goo.gl/Atzi2)
    createHoverDiv = (gridThTd) ->
        hoverDiv = $(gridThTd).children('.fb-hover')
        # Create a div.fb-hover inside the td, if there isn't one already
        if hoverDiv.length == 0
            hoverDiv = $('<div>').addClass('fb-hover')
            hoverDiv.append($(gridThTd).children())
            $(gridThTd).append(hoverDiv)
        hoverDiv

    Builder.mouseEntersGridTdEvent.subscribe ({triggers, triggerGroups, gridTd}) ->
        gridTdId = gridTd.id
        lastPositionTriggers = ->
            # Move cell editor controls under this td
            gridTd = YD.get gridTdId
            if gridTd?
                hoverDiv = createHoverDiv gridTd
                hoverDiv.append(triggerGroups)
                $(triggerGroups).css('display', '')
                # Show/hide triggers depending on relevance rules
                for trigger in triggers
                    rule = relevanceRules[trigger.id]
                    triggerRelevant = if rule? then rule gridTd else true
                    trigger.style.display = if triggerRelevant then '' else 'none'
        lastPositionTriggers()

    # Normally, we add the div.fb-hover when the mouse first enters the td.fb-grid-td, but for the TinyMCE, if we do
    # this after the TinyMCE is initialized, the TinyMCE looses its state as we need to wrap the TinyMCE iframe,
    # which require us to detach the TinyMCE iframe from the DOM. So here we preempltively add the td.fb-grid-td around
    # the TinyMCE just before it is initialized.
    tinyMCE.onAddEditor.add (sender, editor) ->
        Builder.beforeAddingEditorCallbacks.fire $ document.getElementById editor.id

    Builder.beforeAddingEditorCallbacks.add (editor) ->
        gridThTd = f$.closest 'th.fb-grid-th, td.fb-grid-td', editor
        createHoverDiv gridThTd

    # We leave the div.fb-hover that was created on mouseEntersGridTdEvent, as removing it would dispatch a blur to the control
    # See: https://github.com/orbeon/orbeon-forms/issues/44
    Builder.mouseExitsGridTdEvent.subscribe ({triggerGroups, gridTd}) ->
        $(triggerGroups).hide()
        $('.fb-cell-editor').append(triggerGroups)
        lastPositionTriggers = null

    # Change current cell on click on trigger
    Builder.triggerClickEvent.subscribe ({trigger}) ->
        # Send a DOMActivate to the closest xforms-activable ancestor
        activable = YD.getAncestorByClassName trigger, "xforms-activable"
        form = Controls.getForm activable
        event = new AjaxServer.Event form, activable.id, null, "DOMActivate"
        AjaxServer.fireEvents [event]

    # Reposition triggers after an Ajax request
    Events.ajaxResponseProcessedEvent.subscribe ->
        lastPositionTriggers() if lastPositionTriggers?
