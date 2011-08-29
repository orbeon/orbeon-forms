Event = YAHOO.util.Event
YD = YAHOO.util.Dom

currentGridTd = null

Event.onDOMReady () ->

    # Get and hide the icons to the top left of the cell
    gridCellIcons = (YD.getElementsByClassName "fb-grid-cell-icons", null, document)[0]
    gridCellIcons.style.display = "none"

    # Calls function on event with given name on a grid td
    onGridTd = (eventName, callback) ->
        Event.addListener document, eventName, (event) ->
            target = Event.getTarget event
            if YD.hasClass target, "fr-grid-td"
                # Target is the grid td, we're good
                callback target
            else
                # See if the grid td is a parent of the target
                parentGridTd = YD.getAncestorByClassName target, "fr-grid-td"
                callback parentGridTd if parentGridTd?

    onGridTd "mouseover", (td) ->
        console.log "mouseover", td
        position = YD.getXY td
        gridCellIcons.style.display = ""
        YD.setXY gridCellIcons, position

    onGridTd "mouseout", (td) ->
        console.log "mouseout", td
        gridCellIcons.style.display = "none"


