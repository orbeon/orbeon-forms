$ ->
    OD = ORBEON.xforms.Document

    currentControl = null
    currentLabel = null
    isLabelHtml = -> currentControl.is('.fb-label-is-html')
    setLabelHtml = (isHtml) -> currentControl.toggleClass('.fb-label-is-html', isHtml)
    labelValue = (value) ->
        valueAccessor = if isLabelHtml() then currentLabel.html else currentLabel.text
        valueAccessor = _.bind(valueAccessor, currentLabel)
        # Don't pass value if undefined, as an undefined parameter is not the same to jQuery as no parameter
        if value? then valueAccessor(value) else valueAccessor()

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
            # Destroy tooltip, or it doesn't get recreated on startEdit()
            labelEditor().checkbox.tooltip('destroy')
            labelEditor().container.hide()
            # Update values in the DOM, without waiting for the server to send us the value
            setLabelHtml(isChecked)
            labelValue(newLabel)

    # Heuristic to close the editor based on click and focus events
    clickOrFocus = ({target}) ->
        target = $(target)
        eventOnEditor = labelEditor().textfield.is(target) || labelEditor().checkbox.is(target)
        eventOnControlLabel =
            # Click on label or element inside label
            (target.is('.xforms-label') || target.parents('.xforms-label').is('*')) &&
            # Only interested in labels in the "editor" portion of FB
            target.parents('.fb-main').is('*')
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
    startEdit = ({currentTarget}) ->
        currentLabel = $(currentTarget)
        # Find control for this label
        th = currentLabel.parents('th')
        currentControl =
            if th.is('*')
                # Case of a repeat
                trWithControls = th.parents('table').find('tbody tr.fb-grid-tr').first()
                tdWithControl = trWithControls.children(':nth-child(' + (th.index() + 1) + ')')
                tdWithControl.find('.xforms-control, .xbl-component')
            else
                currentLabel.parents('.xforms-control, .xbl-component').first()
        # Remove `for` so browser doesn't set the focus to the control on click
        currentLabel.removeAttr('for')
        # Setup tooltip for editor (don't do this once for all, as the language can change)
        labelEditor().checkbox.tooltip(title: $('.fb-lhha-checkbox-message').text())
        # Position and populate editor
        labelEditor().container.show()
        labelEditor().container.offset(currentLabel.offset())
        labelEditor().textfield.outerWidth(currentLabel.outerWidth() - labelEditor().checkbox.outerWidth(true))
        labelEditor().textfield.val(labelValue()).focus()
        labelEditor().checkbox.prop('checked', isLabelHtml())

    $('.fb-main').on('click', '.xforms-label', startEdit)
