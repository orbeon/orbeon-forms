$ ->
    Builder = ORBEON.Builder
    OD = ORBEON.xforms.Document

    LabelHintSelector = '.fr-editable .xforms-label, .fr-editable .xforms-hint'
    ControlSelector = '.xforms-control, .xbl-component'

    # Global state
    currentControl = null
    currentLabelHint = null

    # Read/write class telling us if the label/hint is in HTML, set in grid.xml
    lhha = -> if currentLabelHint.is('.xforms-label') then 'label' else 'hint'
    htmlClass = -> 'fb-' + lhha() + '-is-html'
    isLabelHintHtml = -> currentControl.is('.' + htmlClass())
    setLabelHintHtml = (isHtml) -> currentControl.toggleClass(htmlClass(), isHtml)

    labelHintValue = (value) ->
        valueAccessor = if isLabelHintHtml() then currentLabelHint.html else currentLabelHint.text
        valueAccessor = _.bind(valueAccessor, currentLabelHint)
        # Don't pass value if undefined, as an undefined parameter is not the same to jQuery as no parameter
        if value? then valueAccessor(value) else valueAccessor()

    # Called when users press enter or tab out
    endEdit = ->
        # If editor is hidden, editing has already been ended (endEdit can be called more than once)
        if labelHintEditor().container.is(':visible')
            # Send value to server, handled in FB's model.xml
            newValue = labelHintEditor().textfield.val()
            isChecked = labelHintEditor().checkbox.is(':checked')
            OD.dispatchEvent
                targetId: currentControl.attr('id')
                eventName: 'fb-update-control-lhha'
                properties: lhha: lhha(), value: newValue, isHtml: isChecked.toString()
            # Destroy tooltip, or it doesn't get recreated on startEdit()
            labelHintEditor().checkbox.tooltip('destroy')
            labelHintEditor().container.hide()
            currentLabelHint.css('visibility', '')
            # Update values in the DOM, without waiting for the server to send us the value
            setLabelHintHtml(isChecked)
            labelHintValue(newValue)

    # Heuristic to close the editor based on click and focus events
    clickOrFocus = ({target}) ->
        target = $(target)
        eventOnEditor = labelHintEditor().textfield.is(target) || labelHintEditor().checkbox.is(target)
        eventOnControlLabel =
            # Click on label or element inside label
            (target.is(LabelHintSelector) || target.parents(LabelHintSelector).is('*')) &&
            # Only interested in labels in the "editor" portion of FB
            target.parents('.fb-main').is('*')
        endEdit() unless eventOnEditor or eventOnControlLabel

    # Returns a <div> which contains the text field and checkbox
    labelHintEditor = _.memoize ->
        # Create elements and add to the DOM
        editor = {}
        editor.textfield = $('<input type="text">')
        editor.checkbox  = $('<input type="checkbox">')
        editor.container = $('<div class="fb-label-editor">').append(editor.textfield).append(editor.checkbox)
        $('.fb-main').append(editor.container)
        # Register event listeners
        editor.checkbox.on('click', -> labelHintEditor().textfield.focus())
        editor.textfield.on('keypress', (e) -> if e.which == 13 then endEdit())
        $(document).on('click', clickOrFocus)
        $(document).on('focusin', clickOrFocus)
        editor

    # Show editor on click on label
    startEdit = () ->
        # Remove `for` so browser doesn't set the focus to the control on click
        currentLabelHint.removeAttr('for')
        # Setup tooltip for editor (don't do this once for all, as the language can change)
        labelHintEditor().checkbox.tooltip(title: $('.fb-lhha-checkbox-message').text())
        # Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
        currentLabelHint.css('visibility', 'hidden')
        # Show, position, and populate editor
        labelHintEditor().container.show()
        labelHintEditor().container.offset(currentLabelHint.offset())
        labelHintEditor().textfield.outerWidth(currentLabelHint.outerWidth() - labelHintEditor().checkbox.outerWidth(true))
        labelHintEditor().textfield.val(labelHintValue()).focus()
        labelHintEditor().checkbox.prop('checked', isLabelHintHtml())

    # Click on label/hint
    $('.fb-main').on 'click', LabelHintSelector, ({currentTarget}) ->
        currentLabelHint = $(currentTarget)
        # Find control for this label
        th = currentLabelHint.parents('th')
        currentControl =
            if th.is('*')
                # Case of a repeat
                trWithControls = th.parents('table').find('tbody tr.fb-grid-tr').first()
                tdWithControl = trWithControls.children(':nth-child(' + (th.index() + 1) + ')')
                tdWithControl.find(ControlSelector)
            else
                currentLabelHint.parents(ControlSelector).first()
        startEdit()

    # New control added
    Builder.controlAdded.add (containerId) ->
        container = $(document.getElementById(containerId))
        currentControl = container.find(ControlSelector)
        repeat = container.parents('.fr-repeat').first()
        currentLabelHint =
            if   repeat.is('*') \
            then repeat.find('thead tr th:nth-child(' + (container.index() + 1) + ') .xforms-label')
            else container.find('.xforms-label')
        startEdit()
