$ ->
    OD = ORBEON.xforms.Document

    currentControl = null

    # Called when users press enter or tab out
    endEdit = ->
        # If editor is hidden, editing has already been ended (endEdit can be called more than once)
        if labelEditor().container.is(':visible')
            # Send value to server
            newLabel = labelEditor().textfield.val()
            isHtml = labelEditor().checkbox.is(':checked')
            OD.dispatchEvent
                targetId: currentControl.attr('id')
                eventName: 'fb-update-control-label'
                properties: label: newLabel, isHtml: isHtml.toString()
            labelEditor().container.hide()
            # Update label in the DOM, without waiting for the server to send us the value
            currentControl.find('.xforms-label').text(newLabel)

    # Heuristic to close the editor based on click and focus events
    clickOrFocus = ({target}) ->
        target = $(target)
        eventOnEditor = labelEditor().textfield.is(target) or labelEditor().checkbox.is(target)
        eventOnControlLabel = target.is('.xforms-label') && target.parents('.fb-main').is('*')
        endEdit() unless eventOnEditor or eventOnControlLabel

    # Returns a <div> which contains the text field and checkbox
    labelEditor = do ->
        editor = null
        ->
            unless editor?
                # Create elements and add to the DOM
                editor = {}
                editor.textfield = $('<input type="text">')
                editor.checkbox  = $('<input type="checkbox">')
                editor.container = $('<div class="fb-label-editor">').append(editor.textfield).append(editor.checkbox)
                $('.fb-main').append(editor.container)
                # Register event listeners
                editor.checkbox.on('click', -> labelEditor().textfield.focus())
                editor.textfield.on('keypress', (e) -> if e.which == 13 then endEdit())
                $(document).on('click', clickOrFocus)
                $(document).on('focusin', clickOrFocus)
            editor

    # Show editor on click on label
    startEdit = ({target}) ->
        label = $(target)
        # We don't want the browser to set the focus on the input
        label.removeAttr('for')
        # Position and populate editor
        labelEditor().container.show()
        labelEditor().container.offset(label.offset())
        labelEditor().textfield.val(label.text()).focus()
        isHTML = label.parents('.fr-grid-content').children('.fb-label-is-html').text() == 'true'
        labelEditor().checkbox.prop('checked', isHTML)
        # Remember which control we're editing
        currentControl = label.parent('.xforms-control')

    $('.fb-main').on('click', '.xforms-label', startEdit)
