$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    # Keep track of grids positions, including the position their rows and columns
    gridsCache = []; do ->
        Builder.onUnderPointerChange ->
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

    # Position delete icon
    do ->
        deleteIcon = $ '.fb-delete-grid-trigger'
        Builder.currentContainerChanged gridsCache, (-> deleteIcon.hide()),
            (grid) ->
                deleteIcon.show()
                scrollContainer = f$.closest '.yui-layout-bd', $ '#fr-view'
                deleteIcon.offset
                    top:  grid.offset.top
                    left: grid.offset.left

    # Position icons do add/remove columns/rows
    do ->

        # Partially builds an icon object
        icon = (selector) -> _.tap {}, (icon) ->
            icon.el = $ selector
            icon.width =  _.memoize -> f$.width  icon.el
            icon.height = _.memoize -> f$.height icon.el

        # 3 icons to add/delete columns
        colIcons = do ->
            colIcon = (selector, colOffset) -> _.tap (icon selector), (icon) ->
                icon.offset = (col) -> top: col.grid.offset.top, left: col.offset.left + colOffset col, icon
            [
                colIcon '.fb-insert-column-left',  -> 0
                colIcon '.fb-delete-column',       (col, icon) -> (col.width - icon.width()) / 2
                colIcon '.fb-insert-column-right', (col, icon) -> col.width - icon.width()
            ]

        # 3 icons to add/delete rows
        rowIcons = do ->
            rowIcon = (selector, rowOffset) -> _.tap (icon selector), (icon) ->
                icon.offset = (row) -> top: row.offset.top + (rowOffset row, icon), left: row.grid.offset.left
            [
                rowIcon '.fb-insert-row-above', -> 0
                rowIcon '.fb-delete-row',       (row, icon) -> (row.height - icon.height()) / 2
                rowIcon '.fb-insert-row-below', (row, icon) -> row.height - icon.height()
            ]

        # When row/col isn't current anymore, hide icons
        wasCurrent = (icons) -> -> _.each (_.values icons), (icon) -> f$.hide icon.el
        becomesCurrent = (icons) -> (rowOrCol) ->
            _.each icons, (icon) ->
                f$.show icon.el
                f$.offset (icon.offset rowOrCol), icon.el

        Builder.currentRowColChanged gridsCache,
            wasCurrentRow: wasCurrent rowIcons
            becomesCurrentRow: becomesCurrent rowIcons
            wasCurrentCol: wasCurrent colIcons
            becomesCurrentCol: becomesCurrent colIcons
