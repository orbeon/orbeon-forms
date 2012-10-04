$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    # Keep track of grids positions, including the position their rows and columns
    gridsCache = []; do ->
        Builder.onUnderPointerChange ->
            gridsCache.length = 0
            _.each ($ 'div.xbl-fr-grid:visible'), (grid) ->
                gridInfo =
                    el: $ grid
                    offset: f$.offset $ grid
                    height: f$.height $ grid
                gridInfo.rows = _.map (f$.find 'tr', $ grid), (tr) ->
                    grid: gridInfo
                    el: $ tr
                    offset: f$.offset $ tr
                    height: f$.height $ tr
                gridInfo.cols = _.map (f$.find 'tr:first td', $ grid), (td) ->
                    grid: gridInfo
                    el: $ td
                    offset: f$.offset $ td
                    width: f$.width $ td
                gridsCache.unshift gridInfo

    # Position delete icon
    do ->
        deleteIcon = $ '.fb-delete-grid-trigger'
        Builder.currentContainerChanged gridsCache,
            wasCurrent: -> deleteIcon.hide()
            becomesCurrent: (grid) ->
                f$.show deleteIcon
                scrollContainer = f$.closest '.yui-layout-bd', $ '#fr-view'
                offset =
                    top:  grid.offset.top
                    left: grid.offset.left
                f$.offset offset, deleteIcon

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

        # Hide/show icons
        hideIcons = (icons) -> -> _.each (_.values icons), (icon) -> f$.hide icon.el
        showIcons = (icons) -> (rowOrCol) ->
            _.each icons, (icon) ->
                f$.show icon.el
                f$.offset (icon.offset rowOrCol), icon.el

        Builder.currentRowColChanged gridsCache,
            wasCurrentRow:      hideIcons rowIcons
            becomesCurrentRow:  showIcons rowIcons
            wasCurrentCol:      hideIcons colIcons
            becomesCurrentCol:  showIcons colIcons

    # On click on a trigger inside .fb-grid-repeat-editor, send section/row/column info along with the event
    do ->

        # Keep track of current grid/row/column so we can send this information to the server on click
        current =
            gridId: null
            colPos: -1
            rowPos: -1

        # Functions maintaining current row/col position
        resetPos = (pos) -> -> current[pos] = -1
        setPos = (pos) -> (rowCol) -> current[pos] = f$.length f$.prev rowCol.el[0].tagName, rowCol.el

        Builder.currentRowColChanged gridsCache,
            wasCurrentRow:      resetPos 'rowPos'
            becomesCurrentRow:  setPos   'rowPos'
            wasCurrentCol:      resetPos 'colPos'
            becomesCurrentCol:  setPos   'colPos'

        Builder.currentContainerChanged gridsCache,
            wasCurrent: -> current.gridId = null
            becomesCurrent: (grid) -> current.gridId = f$.attr 'id', grid.el

        # Provide event context properties on click
        AjaxServer.eventCreated.add (event) ->
            target = $ document.getElementById event.targetId
            inGridRepeatEditor = f$.is '*', f$.closest '.fb-grid-repeat-editor', target
            if event.eventName == 'DOMActivate' && inGridRepeatEditor
                classContains = (text) -> f$.is '*[class *= "' + text + '"]', target
                add = (name, value) -> event.properties[name] = value.toString()
                add 'grid-id', current.gridId
                add 'row-pos', current.rowPos if classContains 'row'
                add 'col-pos', current.colPos if classContains 'column'
