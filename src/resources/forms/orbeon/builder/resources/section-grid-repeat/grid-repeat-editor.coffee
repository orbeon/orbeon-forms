$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    deleteIcon = $ '.fb-delete-grid-trigger'
    colIcons = _.tap {}, (o) ->
        o.insertColLeft =
            el: $ '.fb-insert-column-left'
            offset: (c) -> top: c.grid.offset.top, left: c.offset.left
        o.insertColRight = _.tap {}, (o) ->
            o.el = $ '.fb-insert-column-right'
            o.width = f$.width o.el
            o.offset = (c) => top: c.grid.offset.top, left: c.offset.left + c.width - o.width

    gridsCache = []; do ->
        Builder.onPositionChange ->
            gridsCache.length = 0
            _.each ($ 'div.xbl-fr-grid:visible'), (grid) ->
                gridInfo =
                    element: $ grid
                    offset: f$.offset $ grid
                    height: f$.height $ grid
                gridInfo.rows = _.map (f$.find 'tr', $ grid), (tr) ->
                    grid: gridInfo
                    element: $ tr
                    offset: f$.offset $ tr
                    height: f$.height $ tr
                gridInfo.cols = _.map (f$.find 'tr:first td', $ grid), (td) ->
                    grid: gridInfo
                    element: $ td
                    offset: f$.offset $ td
                    width: f$.width $ td
                gridsCache.unshift gridInfo

    Builder.currentContainerChanged gridsCache, (-> deleteIcon.hide()),
        (grid) ->
            deleteIcon.show()
            scrollContainer = f$.closest '.yui-layout-bd', $ '#fr-view'
            deleteIcon.offset
                top:  grid.offset.top
                left: grid.offset.left

    Builder.currentRowColChanged gridsCache,
        wasCurrentRow: ->
        becomesCurrentRow: ->
        wasCurrentCol: -> _.each (_.values colIcons), (i) -> f$.hide i.el
        becomesCurrentCol: (c) -> _.each (_.values colIcons), (i) ->
            f$.show i.el
            f$.offset (i.offset c), i.el
