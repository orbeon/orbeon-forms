# Copyright (C) 2015 Orbeon, Inc.
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
Builder = ORBEON.Builder

# Global state
Builder.resourceEditorCurrentControl = null
Builder.resourceEditorCurrentLabelHint = null

# Read/write class telling us if the label/hint is in HTML, set in grid.xml
lhha = ->
    if      Builder.resourceEditorCurrentLabelHint.is('.xforms-label')             then 'label'
    else if Builder.resourceEditorCurrentLabelHint.parents('.xforms-text').is('*') then 'text'
    else                                                                                'hint'
htmlClass = -> 'fb-' + lhha() + '-is-html'
isLabelHintHtml = -> Builder.resourceEditorCurrentControl.is('.' + htmlClass())
setLabelHintHtml = (isHtml) -> Builder.resourceEditorCurrentControl.toggleClass(htmlClass(), isHtml)
annotateWithLhhaClass = (add) -> resourceEditor().container.toggleClass('fb-label-editor-for-' + lhha(), add)

labelHintValue = (value) ->
    valueAccessor = if isLabelHintHtml() then Builder.resourceEditorCurrentLabelHint.html else Builder.resourceEditorCurrentLabelHint.text
    valueAccessor = _.bind(valueAccessor, Builder.resourceEditorCurrentLabelHint)
    # Don't pass value if undefined, as an undefined parameter is not the same to jQuery as no parameter
    if value? then valueAccessor(value) else valueAccessor()

# Returns a <div> which contains the text field and checkbox
resourceEditor = _.memoize ->

    # Create elements for editing
    container        = $('<div   style="display: none" class="fb-label-editor">')
    textfield        = $('<input style="display: none" type="text">')
    checkbox         = $('<input style="display: none" type="checkbox">')
    tinymceAnchor    = $('<div   style="display: none">')

    # Nest and add to the page
    container.append(textfield)
             .append(checkbox)
             .append(tinymceAnchor)
    $('.fb-main').append(container)

    # Event handlers
    textfield.on 'keypress', (e) ->
        # End edit when users press enter
        if e.which == 13 then Builder.resourceEditorEndEdit()
    checkbox.on 'click', ->
        # When checkbox clicked, set focus back on the textfield, where it was before
        resourceEditor().textfield.focus()

    # State
    tinymceObject = null

    afterTinyMCEInitialized = (f) ->
        if tinymceObject.initialized then f()
        else tinymceObject.onInit.add(f)

    makeSpaceForMCE = ->
        # Not using tinymceObject.container, as it is not initialized onInit, while editorContainer is
        mceContainer = document.getElementById(tinymceObject.editorContainer)
        mceHeight = $(mceContainer).height()
        Builder.resourceEditorCurrentLabelHint.height(mceHeight)

    # Function to initialize the TinyMCE, memoized so it runs at most once
    initTinyMCE = _.memoize ->
        tinymceAnchor.show()
        tinymceAnchor.attr('id', _.uniqueId())

        # Auto-size MCE height based on the content, with min height of 100px
        mceConfig = _.clone(YAHOO.xbl.fr.Tinymce.DefaultConfig)
        mceConfig.plugins += ',autoresize'
        mceConfig.autoresize_min_height = 100
        mceConfig.autoresize_bottom_margin = 16 # Default padding for autoresize adds too much empty space at the bottom

        tinymceObject = new window.tinymce.Editor(tinymceAnchor.attr('id'), mceConfig)
        tinymceObject.render()
        afterTinyMCEInitialized ->
            # We don't need the anchor anymore; just used to tell TinyMCE where to go in the DOM
            tinymceAnchor.detach()
            $(tinymceObject.getWin()).on('resize', makeSpaceForMCE)


    # Set width of TinyMCE to the width of the container
    # - If not yet initialized, set width on anchor, which is copied by TinyMCE to table
    # - If already initialized, set width directly on table created by TinyMCE
    # (Hacky, but didn't find a better way to do it)
    setTinyMCEWidth = ->
        tinymceTable = $(tinymceObject?.container).find('.mceLayout')
        widthSetOn = if tinymceTable.is('*') then tinymceTable else tinymceAnchor
        widthSetOn.width(container.outerWidth())

    # Create the object we return
    checkbox          : checkbox
    container         : container
    textfield         : textfield
    getValue          : ->
                            if lhha() == 'text'
                                content = tinymceObject.getContent()
                                # Workaround to TinyMCE issue, see
                                # https://twitter.com/avernet/status/579031182605750272
                                if content == '<div>\xa0</div>' then '' else content
                            else
                                textfield.val()
    setValue          : (newValue) ->
                            if lhha() == 'text'
                                afterTinyMCEInitialized ->
                                    tinymceObject.setContent(newValue)
                                    # Workaround for resize not happening with empty values, see
                                    # https://twitter.com/avernet/status/580798585291177984
                                    tinymceObject.execCommand('mceAutoResize')

                            else
                                textfield.val(newValue)
                                textfield.focus()
    isHTML            : ->
                            if lhha() == 'text' then true
                            else checkbox.is(':checked')
    startEdit         : ->
                            textfield.hide()
                            checkbox.hide()
                            tinymceObject?.hide()
                            if lhha() == 'text'
                                setTinyMCEWidth()
                                initTinyMCE()
                                afterTinyMCEInitialized ->
                                    makeSpaceForMCE()
                                    tinymceObject.show()
                                    tinymceObject.focus()
                            else
                                textfield.show()
                                checkbox.show()
    endEdit           : ->
                            if lhha() == 'text'
                                # Reset height we might have placed on the explanation element inside the cell
                                Builder.resourceEditorCurrentLabelHint.height('')


# Show editor on click on label
Builder.resourceEditorStartEdit = () ->
    # Remove `for` so browser doesn't set the focus to the control on click
    Builder.resourceEditorCurrentLabelHint.removeAttr('for')
    # Show, position, and populate editor
    # Get position before showing editor, so showing doesn't move things in the page
    resourceEditor().container.width(Builder.resourceEditorCurrentLabelHint.outerWidth())
    resourceEditor().container.show()
    resourceEditor().startEdit()
    labelHintOffset = Builder.resourceEditorCurrentLabelHint.offset()
    resourceEditor().container.offset(labelHintOffset)
    resourceEditor().setValue(labelHintValue())
    resourceEditor().checkbox.prop('checked', isLabelHintHtml())
    # Set tooltip for checkbox and HTML5 placeholders (don't do this once for all, as the language can change)
    resourceEditor().checkbox.tooltip(title: $('.fb-message-lhha-checkbox').text())
    resourceEditor().textfield.attr('placeholder', $(".fb-message-type-#{lhha()}").text())
    # Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
    Builder.resourceEditorCurrentLabelHint.css('visibility', 'hidden')
    # Add class telling if this is a label or hint editor
    annotateWithLhhaClass(true)

# Called when users press enter or tab out
Builder.resourceEditorEndEdit = ->
    # If editor is hidden, editing has already been ended (endEdit can be called more than once)
    if resourceEditor().container.is(':visible')
        # Send value to server, handled in FB's model.xml
        newValue = resourceEditor().getValue()
        isHTML = resourceEditor().isHTML()
        ORBEON.xforms.Document.dispatchEvent
            targetId: Builder.resourceEditorCurrentControl.attr('id')
            eventName: 'fb-update-control-lhha'
            properties: lhha: lhha(), value: newValue, isHtml: isHTML.toString()
        # Destroy tooltip, or it doesn't get recreated on startEdit()
        resourceEditor().checkbox.tooltip('destroy')
        resourceEditor().container.hide()
        resourceEditor().endEdit()
        annotateWithLhhaClass(false)
        Builder.resourceEditorCurrentLabelHint.css('visibility', '')
        # Update values in the DOM, without waiting for the server to send us the value
        setLabelHintHtml(isHTML)
        labelHintValue(newValue)
        # Clean state
        Builder.resourceEditorCurrentControl = null
        Builder.resourceEditorCurrentLabelHint = null
