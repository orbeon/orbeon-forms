$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    # Calls listener when what is under the pointer has potentially changed
    Builder.onUnderPointerChange = (f) ->
        ($ document).on 'mousemove', f
        Events.ajaxResponseProcessedEvent.subscribe f

    # Finds the container, if any, based on a vertical position
    Builder.findInCache = (containerCache, top) ->
        _.find containerCache, (container) ->
            container.offset.top <= top <= container.offset.top + container.height

    # Container is either a section or grid; calls listeners passing old/new container
    Builder.currentContainerChanged = (containerCache, {wasCurrent, becomesCurrent}) ->
        notifyChange = notifyOnChange wasCurrent, becomesCurrent
        Builder.onUnderPointerChange ->
            if viewPos.left <= pointerPos.left <= viewPos.right
                newContainer = Builder.findInCache containerCache, pointerPos.top
            notifyChange newContainer

    # Calls listeners when, in a grid, the pointer moves out of or in a new row/cell
    Builder.currentRowColChanged = (gridsCache, {wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol}) ->
        currentGrid = null; do ->
            Builder.currentContainerChanged gridsCache,
                wasCurrent: -> currentGrid = null
                becomesCurrent: (g) -> currentGrid = g
        notifyRowChange = notifyOnChange wasCurrentRow, becomesCurrentRow
        notifyColChange = notifyOnChange wasCurrentCol, becomesCurrentCol
        Builder.onUnderPointerChange ->
            if currentGrid?
                newRow = _.find currentGrid.rows, (r) -> r.offset.top  <= pointerPos.top  <= r.offset.top + r.height
                newCol = _.find currentGrid.cols, (c) -> c.offset.left <= pointerPos.left <= c.offset.left + c.width
            notifyRowChange newRow
            notifyColChange newCol

    # Keep track of FB's main area left/right, as we don't want to show icons when pointer is over the toolbar
    viewPos = left: 0, right: 0; do ->
        updateViewPos = ->
            view = $ '.fr-view'
            viewPos.left = (f$.offset view).left
            viewPos.right = viewPos.left + (f$.width view)
        f$.on 'load resize', updateViewPos, $ window

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
                if currentValue is null or newValue.element  != currentValue.element
                    was currentValue if currentValue?
                    currentValue = newValue
                    becomes newValue
            else
                was currentValue if currentValue?
                currentValue = null
