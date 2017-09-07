$ = ORBEON.jQuery
$ ->
    Builder = ORBEON.Builder
    Position = ORBEON.builder.Position
    Events = ORBEON.xforms.Events

    # How much we need to add to offset to account for the form having been scrolled
    Builder.scrollTop  = -> f$.scrollTop  $ '.fb-main'
    Builder.scrollLeft = -> f$.scrollLeft $ '.fb-main'

    # Gets an element offset, normalizing for scrolling, so the offset can be stored in a cache
    Builder.adjustedOffset = (jQueryObject) ->
        _.tap (f$.offset jQueryObject), (offset) -> offset.top += Builder.scrollTop()

    # Container is either a section or grid; calls listeners passing old/new container
    Builder.currentContainerChanged = (containerCache, {wasCurrent, becomesCurrent}) ->
        notifyChange = notifyOnChange(wasCurrent, becomesCurrent)
        Position.onUnderPointerChange ->
            top  = pointerPos.top  + Builder.scrollTop()
            left = pointerPos.left + Builder.scrollLeft()
            newContainer = Position.findInCache(containerCache, top, left)
            notifyChange(newContainer)

    # Calls listeners when, in a grid, the pointer moves out of or in a new row/cell
    Builder.currentRowColChanged = (gridsCache, {wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol}) ->
        currentGrid = null; do ->
            Builder.currentContainerChanged gridsCache,
                wasCurrent: -> currentGrid = null
                becomesCurrent: (g) ->
                    currentGrid = g
        notifyRowChange = notifyOnChange(wasCurrentRow, becomesCurrentRow)
        notifyColChange = notifyOnChange(wasCurrentCol, becomesCurrentCol)
        Position.onUnderPointerChange ->
            if currentGrid?
                newRow = _.find currentGrid.rows, (r) -> r.offset.top  <= pointerPos.top + Builder.scrollTop() <= r.offset.top + r.height
                newCol = _.find currentGrid.cols, (c) -> c.offset.left <= pointerPos.left <= c.offset.left + c.width
            notifyRowChange newRow
            notifyColChange newCol

    # Keeps track of pointer position
    pointerPos = left: 0, top: 0; do ->
        ($ document).on 'mousemove', (event) ->
            pointerPos.left = event.pageX
            pointerPos.top = event.pageY

    # Returns a function, which is expected to be called every time the value changes passing the new value, and which
    # will when appropriate notify the listeners `was` and `becomes` of the old and new value
    notifyOnChange = (was, becomes) ->
        currentValue = null
        (newValue) ->
            if newValue?
                firstTime = -> _.isNull(currentValue)
                domElementChanged = ->
                    # Typically after an Ajax request, maybe a column/row was added/removed, so we might consequently
                    # need to update the icon position
                    not(newValue.el.is(currentValue.el))
                elementPositionChanged = ->
                    # The elements could be the same, but their position could have changed, in which case want to
                    # reposition relative icons, so we don't consider the value to be the "same"
                    not(_.isEqual(newValue.offset, currentValue.offset))
                if firstTime() or domElementChanged() or elementPositionChanged()
                    was(currentValue) if currentValue?
                    currentValue = newValue
                    becomes(newValue)
            else
                was(currentValue) if currentValue?
                currentValue = null
