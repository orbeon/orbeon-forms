Event = YAHOO.util.Event
YD = YAHOO.util.Dom
AjaxServer = ORBEON.xforms.server.AjaxServer
Controls = ORBEON.xforms.Controls

currentGridTd = null

Event.onDOMReady () ->

    # Get a reference to the icons to the top left of the cell
    cellEditorChildren = do ->
        cellEditor = (YD.getElementsByClassName "fb-cell-editor", null, document)[0]
        YD.getChildren cellEditor

    # Keep track of the grid id the mouse is over
    currentGridTd = null

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

    # Position cell editor controls on the right cell on the mouse over
    onMouseOver \
        (gridTd) ->
            # We're in a grid td, look for its content
            gridContent = (YD.getElementsByClassName "fr-grid-content", null, gridTd)[0]
            if gridTd isnt currentGridTd
                # We're in a new grid td: move cell editor controls under this td
                _.each cellEditorChildren, (child) -> YD.insertBefore child, gridContent
            currentGridTd = gridTd
            # Display or hide cell editor control depending on whether content is present
            emptyContent = (YD.getChildren gridContent).length is 0
            _.each cellEditorChildren, (child) -> child.style.display =
                # Only hide/show the icons, skip the in-place editors
                if (_.any ["fb-grid-cell-icons", "fb-grid-control-icons"], (c) -> YD.hasClass child, c)
                    if emptyContent then "none" else ""
        , () ->
            # We're outside of a grid td: hide cell editor controls
            _.each cellEditorChildren, (child) -> child.style.display = "none"
            currentGridTd = null

    # Click on a label or hint shows and positions the input
    onClickLabelHint = (event) ->
        target = Event.getTarget event
        inputToShow = do ->
            targetToInput = "xforms-label": "fb-control-label", "xforms-hint": "fb-control-hint"
            targetClass = _.detect (_.keys targetToInput), (c) -> YD.hasClass target, c
            inputClass = targetToInput[targetClass]
            _.detect cellEditorChildren, (e) -> YD.hasClass e, inputClass
        inputToShow.style.display = ""
        #
        console.log input

    # Register listener for click on label and hint
    do ->
        labelHintClickRegistered = []
        onMouseOver (gridTd) ->
            # Get label and hint elements in this grid td
            elements = _.flatten _.map ["xforms-label", "xforms-hint"], (c) -> YD.getElementsByClassName c, null, gridTd
            # Remove those we already know about
            elements = _.difference elements, labelHintClickRegistered
            # Register our listener
            _.each elements, (e) ->
                Event.addListener e, "click", onClickLabelHint
            # Remember we already registered a listener
            labelHintClickRegistered = _.union labelHintClickRegistered, elements

    # On click on any of the cell editor controls, make the cell the current one
    _.each cellEditorChildren, (child) ->
        Event.addListener child, "click", () ->
            # Send a DOMActivate to the closest xforms-activable ancestor
            activable = YD.getAncestorByClassName child, "xforms-activable"
            form = Controls.getForm activable
            event = new AjaxServer.Event form, activable.id, null, null, "DOMActivate"
            AjaxServer.fireEvents [event]

