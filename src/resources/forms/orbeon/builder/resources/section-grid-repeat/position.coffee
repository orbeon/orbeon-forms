$ = ORBEON.jQuery
$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    # Calls listener when what is under the pointer has potentially changed
    onUnderPointerChange = (f) ->
        $(document).on('mousemove', f)
        # Resizing the window might change what is under the pointer the last time we saw it in the window
        $(window).on('resize', f)
        Events.ajaxResponseProcessedEvent.subscribe f

    # Call listener when anything on the page that could change element positions happened
    Builder.onOffsetMayHaveChanged = (f) ->
        # After the form is first shown
        Events.orbeonLoadedEvent.subscribe f
        # After an Ajax response, as it might have changed the DOM
        Events.ajaxResponseProcessedEvent.subscribe f
        $(window).on('resize', f)

    # Finds the container, if any, based on a vertical position
    Builder.findInCache = (containerCache, top) ->
        _.find containerCache, (container) ->
            container.offset.top <= top <= container.offset.top + container.height

    # How much we need to add to offset to account for the form having been scrolled
    Builder.scrollTop = -> f$.scrollTop $ '.fb-main'

    # Gets an element offset, normalizing for scrolling, so the offset can be stored in a cache
    Builder.adjustedOffset = (jQueryObject) ->
        _.tap (f$.offset jQueryObject), (offset) -> offset.top += Builder.scrollTop()

    # Container is either a section or grid; calls listeners passing old/new container
    Builder.currentContainerChanged = (containerCache, {wasCurrent, becomesCurrent}) ->
        notifyChange = notifyOnChange wasCurrent, becomesCurrent
        onUnderPointerChange ->
            if viewPos.left <= pointerPos.left <= viewPos.right
                top = pointerPos.top + Builder.scrollTop()
                newContainer = Builder.findInCache containerCache, top
            notifyChange newContainer

    # Calls listeners when, in a grid, the pointer moves out of or in a new row/cell
    Builder.currentRowColChanged = (gridsCache, {wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol}) ->
        currentGrid = null; do ->
            Builder.currentContainerChanged gridsCache,
                wasCurrent: -> currentGrid = null
                becomesCurrent: (g) -> currentGrid = g
        notifyRowChange = notifyOnChange wasCurrentRow, becomesCurrentRow
        notifyColChange = notifyOnChange wasCurrentCol, becomesCurrentCol
        onUnderPointerChange ->
            if currentGrid?
                newRow = _.find currentGrid.rows, (r) -> r.offset.top  <= pointerPos.top + Builder.scrollTop() <= r.offset.top + r.height
                newCol = _.find currentGrid.cols, (c) -> c.offset.left <= pointerPos.left <= c.offset.left + c.width
            notifyRowChange newRow
            notifyColChange newCol

    # Keep track of FB's main area left/right, as we don't want to show icons when pointer is over the toolbar
    viewPos = left: 0, right: 0; do ->
        updateViewPos = ->
            fbMain = $ '.fb-main'
            viewPos.left = (f$.offset fbMain).left
            viewPos.right = viewPos.left + (f$.width fbMain)
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
                    was currentValue if currentValue?
                    currentValue = newValue
                    becomes newValue
            else
                was currentValue if currentValue?
                currentValue = null
