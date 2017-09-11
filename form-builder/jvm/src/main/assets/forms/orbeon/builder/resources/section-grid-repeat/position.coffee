$ = ORBEON.jQuery
$ ->
    Builder = ORBEON.Builder
    Position = ORBEON.builder.Position
    Events = ORBEON.xforms.Events

    # Container is either a section or grid; calls listeners passing old/new container
    Builder.currentContainerChanged = (containerCache, {wasCurrent, becomesCurrent}) ->
        notifyChange = Position.notifyOnChange(wasCurrent, becomesCurrent)
        Position.onUnderPointerChange ->
            top  = pointerPos.top  + Position.scrollTop()
            left = pointerPos.left + Position.scrollLeft()
            newContainer = Position.findInCache(containerCache, top, left)
            notifyChange(newContainer)

    # Calls listeners when, in a grid, the pointer moves out of or in a new row/cell
    Builder.currentRowColChanged = (gridsCache, {wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol}) ->
        currentGrid = null; do ->
            Builder.currentContainerChanged gridsCache,
                wasCurrent: -> currentGrid = null
                becomesCurrent: (g) ->
                    currentGrid = g
        notifyRowChange = Position.notifyOnChange(wasCurrentRow, becomesCurrentRow)
        notifyColChange = Position.notifyOnChange(wasCurrentCol, becomesCurrentCol)
        Position.onUnderPointerChange ->
            if currentGrid?
                newRow = _.find currentGrid.rows, (r) -> r.top  <= pointerPos.top + Position.scrollTop() <= r.top + r.height
                newCol = _.find currentGrid.cols, (c) -> c.left <= pointerPos.left <= c.left + c.width
            notifyRowChange newRow
            notifyColChange newCol

    # Keeps track of pointer position
    pointerPos = left: 0, top: 0; do ->
        ($ document).on 'mousemove', (event) ->
            pointerPos.left = event.pageX
            pointerPos.top = event.pageY
