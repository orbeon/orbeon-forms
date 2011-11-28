Event = YAHOO.util.Event
YD = YAHOO.util.Dom
OD = ORBEON.util.Dom
Events = ORBEON.xforms.Events
AjaxServer = ORBEON.xforms.server.AjaxServer
Controls = ORBEON.xforms.Controls
pipeline = (v, fs...) -> v = f.call(v) for f in fs; v

Event.onDOMReady () ->

    # Get a reference to the icons to the top left of the cell
    cellEditorsContainer = (YD.getElementsByClassName "fb-cell-editor", null, document)[0]
    cellEditorInputs = []
    cellEditorTriggers = []
    for child in YD.getChildren cellEditorsContainer
        type = if YD.hasClass child, "xforms-input" then cellEditorInputs else cellEditorTriggers
        type.push child

    # Calls a different listener depending on whether to mouse is over agreed a grid td or not
    onMouseOver = (gridTdCallback, otherCallback) ->
        Event.addListener document, "mouseover", (event) ->
            target = Event.getTarget event
            gridTd =
                # Target is the grid td, we're good
                if YD.hasClass target, "fr-grid-td" then target
                # Try finding a grid td parent of the target
                else YD.getAncestorByClassName target, "fr-grid-td"
            if gridTd? then gridTdCallback gridTd if gridTdCallback?
            else otherCallback() if otherCallback?

    # Position cell editor triggers on the right cell on the mouse over
    do ->
        # Keep track of the grid id the mouse is over
        currentMouseOverGridTd = null
        onMouseOver \
            (gridTd) ->
                # We're in a grid td, look for its content
                gridContent = (YD.getElementsByClassName "fr-grid-content", null, gridTd)[0]
                if gridTd isnt currentMouseOverGridTd
                    # We're in a new grid td: move cell editor controls under this td
                    _.each cellEditorTriggers, (child) -> YD.insertBefore child, gridContent
                currentMouseOverGridTd = gridTd
                # Display or hide cell editor control depending on whether content is present
                emptyContent = (YD.getChildren gridContent).length is 0
                for child in cellEditorTriggers
                    child.style.display =
                        # Only hide/show the icons, skip the in-place editors
                        if (_.any ["fb-grid-cell-icons", "fb-grid-control-icons"], (c) -> YD.hasClass child, c)
                            if emptyContent then "none" else ""
            , () ->
                # We're outside of a grid td: hide cell editor controls
                child.style.display = "none" for child in cellEditorTriggers when not YD.hasClass child, "xforms-input"
                currentMouseOverGridTd = null

    # Show/hide input for label and hint
    do ->

        labelHintClickRegistered = []
        labelHint = null
        labelHintSavedHTMLFor = ''

        # Show input to edit label or hint, which is called on click on label or hint
        showLabelHintInput = (gridTd, target) ->
            # Don't bother if we already have an input inside the label or hint
            if (YD.getElementsByClassName "xforms-input", null, target).length == 0
                # Save information about this label/hint
                labelHint = target
                labelHintSavedHTMLFor = labelHint.htmlFor
                # Remove 'for' so focus isn't set on the control on click
                labelHint.htmlFor = ''
                # Get the input we want to show
                editor = pipeline ([["xforms-label", "fb-control-label"], ["xforms-hint", "fb-control-hint"]]),
                    -> return inputClass for [targetClass, inputClass] in @ when YD.hasClass labelHint, targetClass
                    -> return e for e in cellEditorInputs when YD.hasClass e, @
                # Added editor as a children of the label
                labelHint.removeChild labelHint.childNodes[0] while labelHint.childNodes.length > 0
                labelHint.appendChild editor
                # Show and focus on editor
                editor.style.display = ""
                (editor.getElementsByTagName "input")[0].focus()

        # Hide input and restore value
        hideLabelHintInput = ->
            console.trace()
            # Move input out of label/hint back under container
            editor = YD.getFirstChild labelHint
            cellEditorsContainer.appendChild editor
            # Restore text under label/hint
            labelHint.htmlFor = labelHintSavedHTMLFor
            editorValue = Controls.getCurrentValue editor
            OD.setStringValue labelHint, editorValue
            # Reset state
            labelHint = null
            labelHintSavedHTMLFor = ''

        # Register listener for click on label and hint
        onMouseOver (gridTd) ->
            # Get label and hint elements in this grid td
            elements = _.flatten _.map ["xforms-label", "xforms-hint"], (c) -> YD.getElementsByClassName c, null, gridTd
            # Remove those we already know about
            elements = _.difference elements, labelHintClickRegistered
            # Register our listener
            _.each elements, (e) ->
                Event.addListener e, "click", ->
                    # Wait for Ajax response before showing editor to make sure the editor is bound to the current control
                    Events.runOnNext Events.ajaxResponseProcessedEvent, ->
                        showLabelHintInput gridTd, e
            # Remember we already registered a listener
            labelHintClickRegistered = _.union labelHintClickRegistered, elements

        # On enter or escape, restore label/hint to its view mode
        Events.keypressEvent.subscribe (event) ->
            hideLabelHintInput() if labelHint? and event.control.parentNode == labelHint and event.keyCode == 13
        # On blur of input, restore value
        Events.blurEvent.subscribe (event) ->
            if labelHint?
                editor = YD.getFirstChild labelHint
                hideLabelHintInput() if event.control == editor

    # On click on any of the cell editor controls, make the cell the current one
    _.each cellEditorTriggers, (child) ->
        Event.addListener child, "click", () ->
            # Send a DOMActivate to the closest xforms-activable ancestor
            activable = YD.getAncestorByClassName child, "xforms-activable"
            form = Controls.getForm activable
            event = new AjaxServer.Event form, activable.id, null, null, "DOMActivate"
            AjaxServer.fireEvents [event]
