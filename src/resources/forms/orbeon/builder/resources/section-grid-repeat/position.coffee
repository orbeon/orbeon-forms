$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    viewPos = left: 0, right: 0; do ->
        updateViewPos = ->
            view = $ '.fr-view'
            viewPos.left = (f$.offset view).left
            viewPos.right = viewPos.left + (f$.width view)
        f$.on 'load resize', updateViewPos, $ window

    mousePos = left: 0, top: 0; do ->
        ($ document).on 'mousemove', (event) ->
            mousePos.left = event.pageX
            mousePos.top = event.pageY

    Builder.findInCache = (containerCache, top) ->
        _.find containerCache, (container) ->
            container.offset.top <= top <= container.offset.top + container.height

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

    Builder.onPositionChange = (f) ->
        ($ document).on 'mousemove', f
        Events.ajaxResponseProcessedEvent.subscribe f

    Builder.currentContainerChanged = (containerCache, wasCurrentContainer, becomesCurrentContainer) ->

        notifyChange = notifyOnChange wasCurrentContainer, becomesCurrentContainer

        Builder.onPositionChange ->
            if viewPos.left <= mousePos.left <= viewPos.right
                newContainer = Builder.findInCache containerCache, mousePos.top
            notifyChange newContainer

    Builder.currentRowColChanged = (gridsCache, {wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol}) ->

        currentGrid = null; do ->
            Builder.currentContainerChanged gridsCache, (-> currentGrid = null), ((g) -> currentGrid = g)

        notifyRowChange = notifyOnChange wasCurrentRow, becomesCurrentRow
        notifyColChange = notifyOnChange wasCurrentCol, becomesCurrentCol

        Builder.onPositionChange ->
            if currentGrid?
                newRow    = _.find currentGrid.rows, (r) -> r.offset.top  <= mousePos.top  <= r.offset.top + r.height
                newColumn = _.find currentGrid.cols, (c) -> c.offset.left <= mousePos.left <= c.offset.left + c.width
            notifyRowChange    newRow
            notifyColChange newColumn
