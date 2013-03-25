$ ->
    OD = ORBEON.xforms.Document

    currentControl = null
    currentLabel = null
    isLabelHtml = -> currentControl.is('.fb-label-is-html')
    setLabelHtml = (isHtml) -> currentControl.toggleClass('.fb-label-is-html', isHtml)

    # Called when users press enter or tab out
    endEdit = ->
        # If editor is hidden, editing has already been ended (endEdit can be called more than once)
        if labelEditor().container.is(':visible')
            # Send value to server
            newLabel = labelEditor().textfield.val()
            isChecked = labelEditor().checkbox.is(':checked')
            OD.dispatchEvent
                targetId: currentControl.attr('id')
                eventName: 'fb-update-control-label'
                properties: label: newLabel, isHtml: isChecked.toString()
            labelEditor().container.hide()
            # Update values in the DOM, without waiting for the server to send us the value
            currentLabel.text(newLabel)
            setLabelHtml(isChecked)

    # Heuristic to close the editor based on click and focus events
    clickOrFocus = ({target}) ->
        target = $(target)
        eventOnEditor = labelEditor().textfield.is(target) or labelEditor().checkbox.is(target)
        eventOnControlLabel = target.is('.xforms-label') && target.parents('.fb-main').is('*')
        endEdit() unless eventOnEditor or eventOnControlLabel

    # Returns a <div> which contains the text field and checkbox
    labelEditor = _.memoize ->
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
        currentLabel = $(target)
        # Find control for this label
        th = currentLabel.parents('th')
        currentControl =
            if th.is('*')
                # Case of a repeat
                trWithControls = th.parents('table').find('tbody tr.fb-grid-tr').first()
                tdWithControl = trWithControls.children(':nth-child(' + (th.index() + 1) + ')')
                tdWithControl.find('.xforms-control, .xbl-component').first()
            else
                currentLabel.parent('.xforms-control, .xbl-component')
        # Remove `for` so browser doesn't set the focus to the control on click
        currentLabel.removeAttr('for')
        # Position and populate editor
        labelEditor().container.show()
        labelEditor().container.offset(currentLabel.offset())
        labelEditor().textfield.outerWidth(currentLabel.outerWidth() - labelEditor().checkbox.outerWidth(true))
        labelEditor().textfield.val(currentLabel.text()).focus()
        labelEditor().checkbox.prop('checked', isLabelHtml())

    $('.fb-main').on('click', '.xforms-label', startEdit)
