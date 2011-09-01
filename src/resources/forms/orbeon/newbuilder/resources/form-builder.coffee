Event = YAHOO.util.Event
YD = YAHOO.util.Dom
AjaxServer = ORBEON.xforms.server.AjaxServer
Controls = ORBEON.xforms.Controls

currentGridTd = null

Event.onDOMReady () ->

    # Get and hide the icons to the top left of the cell
    cellEditorChildren = do ->
        cellEditor = (YD.getElementsByClassName "fb-cell-editor", null, document)[0]
        YD.getChildren cellEditor

    # Keep track the grid id the mouse is over
    currentGridTd = null

    # Position cell editor controls on the right cell on the mouse over
    Event.addListener document, "mouseover", (event) ->
        target = Event.getTarget event
        gridTd =
            # Target is the grid td, we're good
            if YD.hasClass target, "fr-grid-td" then target
            # Try finding a grid td parent of the target
            else YD.getAncestorByClassName target, "fr-grid-td"
        if gridTd?
            # We're in a grid td, look for its content
            gridContent = (YD.getElementsByClassName "fr-grid-content", null, gridTd)[0]
            if gridTd isnt currentGridTd
                # We're in a new grid td: move cell editor controls under this td
                _.each cellEditorChildren, (child) -> YD.insertBefore child, gridContent
            currentGridTd = gridTd
            # Display or hide cell editor control depending on whether content is present
            emptyContent = (YD.getChildren gridContent).length is 0
            _.each cellEditorChildren, (child) -> child.style.display = if emptyContent then "none" else ""
        else if gridTd is null
            # We're outside of a grid td: hide cell editor controls
            _.each cellEditorChildren, (child) -> child.style.display = "none"
            currentGridTd = null

    # On click on any of the cell editor controls, make the cell the current one
    _.each cellEditorChildren, (child) ->
        Event.addListener child, "click", () ->
            # Send a DOMActivate to the closest xforms-activable ancestor
            activable = YD.getAncestorByClassName child, "xforms-activable"
            form = Controls.getForm activable
            event = new AjaxServer.Event form, activable.id, null, null, "DOMActivate"
            AjaxServer.fireEvents [event]
