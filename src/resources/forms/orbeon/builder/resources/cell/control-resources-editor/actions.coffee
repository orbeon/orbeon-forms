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

$ = ORBEON.jQuery
Builder = ORBEON.Builder
OD = ORBEON.xforms.Document

# Global state
Builder.resourceEditorCurrentControl = null
Builder.resourceEditorCurrentLabelHint = null

# Read/write class telling us if the label/hint is in HTML, set in grid.xml
lhha = -> if Builder.resourceEditorCurrentLabelHint.is('.xforms-label') then 'label' else 'hint'
htmlClass = -> 'fb-' + lhha() + '-is-html'
isLabelHintHtml = -> Builder.resourceEditorCurrentControl.is('.' + htmlClass())
setLabelHintHtml = (isHtml) -> Builder.resourceEditorCurrentControl.toggleClass(htmlClass(), isHtml)
annotateWithLhhaClass = (add) -> labelHintEditor().container.toggleClass('fb-label-editor-for-' + lhha(), add)

labelHintValue = (value) ->
    valueAccessor = if isLabelHintHtml() then Builder.resourceEditorCurrentLabelHint.html else Builder.resourceEditorCurrentLabelHint.text
    valueAccessor = _.bind(valueAccessor, Builder.resourceEditorCurrentLabelHint)
    # Don't pass value if undefined, as an undefined parameter is not the same to jQuery as no parameter
    if value? then valueAccessor(value) else valueAccessor()

# Returns a <div> which contains the text field and checkbox
labelHintEditor = _.memoize ->
    # Create elements and add to the DOM
    editor = {}
    editor.textfield = $('<input type="text">')
    editor.checkbox  = $('<input type="checkbox">')
    editor.container = $('<div class="fb-label-editor">').append(editor.textfield).append(editor.checkbox)
    editor.container.hide()
    $('.fb-main').append(editor.container)
    # Register event listeners
    editor.checkbox.on('click', -> labelHintEditor().textfield.focus())
    editor.textfield.on('keypress', (e) -> if e.which == 13 then Builder.resourceEditorEndEdit())
    editor

# Show editor on click on label
Builder.resourceEditorStartEdit = () ->
    # Remove `for` so browser doesn't set the focus to the control on click
    Builder.resourceEditorCurrentLabelHint.removeAttr('for')
    # Show, position, and populate editor
    # Get position before showing editor, so showing doesn't move things in the page
    labelHintOffset = Builder.resourceEditorCurrentLabelHint.offset()
    labelHintEditor().container.show()
    labelHintEditor().container.offset(labelHintOffset)
    labelHintEditor().container.width(Builder.resourceEditorCurrentLabelHint.outerWidth())
    labelHintEditor().textfield.val(labelHintValue()).focus()
    labelHintEditor().checkbox.prop('checked', isLabelHintHtml())
    # Set tooltip for checkbox and HTML5 placeholders (don't do this once for all, as the language can change)
    labelHintEditor().checkbox.tooltip(title: $('.fb-message-lhha-checkbox').text())
    labelHintEditor().textfield.attr('placeholder', $(".fb-message-type-#{lhha()}").text())
    # Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
    Builder.resourceEditorCurrentLabelHint.css('visibility', 'hidden')
    # Add class telling if this is a label or hint editor
    annotateWithLhhaClass(true)

# Called when users press enter or tab out
Builder.resourceEditorEndEdit = ->
    # If editor is hidden, editing has already been ended (endEdit can be called more than once)
    if labelHintEditor().container.is(':visible')
        # Send value to server, handled in FB's model.xml
        newValue = labelHintEditor().textfield.val()
        isChecked = labelHintEditor().checkbox.is(':checked')
        OD.dispatchEvent
            targetId: Builder.resourceEditorCurrentControl.attr('id')
            eventName: 'fb-update-control-lhha'
            properties: lhha: lhha(), value: newValue, isHtml: isChecked.toString()
        # Destroy tooltip, or it doesn't get recreated on startEdit()
        labelHintEditor().checkbox.tooltip('destroy')
        labelHintEditor().container.hide()
        annotateWithLhhaClass(false)
        Builder.resourceEditorCurrentLabelHint.css('visibility', '')
        # Update values in the DOM, without waiting for the server to send us the value
        setLabelHintHtml(isChecked)
        labelHintValue(newValue)
        # Clean state
        Builder.resourceEditorCurrentControl = null
        Builder.resourceEditorCurrentLabelHint = null

