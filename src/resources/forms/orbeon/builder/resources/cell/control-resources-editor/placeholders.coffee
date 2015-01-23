###
Copyright (C) 2014 Orbeon, Inc.

This program is free software; you can redistribute it and/or modify it under the terms of the
GNU Lesser General Public License as published by the Free Software Foundation; either version
2.1 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU Lesser General Public License for more details.

The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
###

###
Show placeholders, e.g. "Click here to…"
- Maintain on the <label class="xforms-label"> and <span class="xforms-hint"> an attribute placeholder.
- Value of placeholder attribute is the text shown on grid mouseover if the label/hint is empty.
- The value of the placeholder is shown with CSS :empty:before.
###

$ = ORBEON.jQuery
Builder = ORBEON.Builder
Events = ORBEON.xforms.Events

resourceNames = ['label', 'hint', 'text']
gridTdUpdated = []                  # Seq[gridTd: El] tds on which we set placeholders, to update on lang change
placeholderTextForResource = {}     # Map[resource name:String, placeholder: String]

# Set the placeholder attributes in the LHHA inside this gridTd
updateGridTd = (gridTd) ->
    gridTd = $(gridTd)
    _.each resourceNames, (lhha) ->
        elementInDiv = gridTd.find(".xforms-#{lhha}")
        # If elements is an xf:output, put placeholder attribute on child element
        if elementInDiv.is('.xforms-output') then elementInDiv = elementInDiv.children('.xforms-output-output')
        elementInDiv.attr('placeholder', placeholderTextForResource[lhha])

# Reads and stores the placeholder text
updatePlacehodlerText = ->
    foundDifference = false
    _.each resourceNames, (lhha) ->
        newText = $(".fb-message-enter-#{lhha}").children().text()
        if newText != placeholderTextForResource[lhha]
            placeholderTextForResource[lhha] = newText
            foundDifference = true
    if foundDifference
        # Only keep the gridTds still in the DOM
        isInDoc = (e) -> $.contains(document.body, e)
        gridTdUpdated = _.filter(gridTdUpdated, isInDoc)
        # Update the placeholders in the gridTds we have left
        _.each(gridTdUpdated, updateGridTd)

$ ->
    # Do initial update when the page is loaded
    updatePlacehodlerText()
    # Update on Ajax response in case the language changed
    Events.ajaxResponseProcessedEvent.subscribe updatePlacehodlerText

    # When label/hint empty, show placeholder hinting users can click to edit the label/hint
    Builder.mouseEntersGridTdEvent.subscribe ({gridTd}) ->
        if ! _.contains(gridTdUpdated, gridTd)
            gridTdUpdated.push(gridTd)
            updateGridTd(gridTd)
