$ = ORBEON.jQuery
$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Position = ORBEON.builder.Position
    Events = ORBEON.xforms.Events

    # Keep track of grids positions, including the position their rows and columns
    gridsCache = []; do ->
        Position.onOffsetMayHaveChanged ->
            gridsCache.length = 0                                                                                       # Update gridsCache in-place, as references are kept by other functions
            _.each ($ '.fr-grid.fr-editable:visible'), (table) ->
                table = $(table)
                grid = table.closest('.xbl-fr-grid')                                                                    # There might be an intermediate group <span>
                gridOffset = Position.adjustedOffset(grid)
                gridInfo =
                    el: grid
                    top: gridOffset.top
                    left: gridOffset.left
                    height: f$.outerHeight grid                                                                         # Include grid padding
                    width : f$.outerWidth  grid
                if f$.is '.fr-repeat', table
                    head = f$.find '.fr-grid-head', table
                    gridInfo.head =
                        offset: Position.adjustedOffset head
                        height: f$.height head
                gridInfo.rows = _.map (f$.find '.fr-grid-tr', grid), (tr) ->                                            # .fr-grid-tr leaves out the header row in the repeat
                    offset = Position.adjustedOffset $ tr
                    grid: gridInfo
                    el: $ tr
                    left: offset.left
                    top: offset.top
                    height: f$.height $ tr
                gridInfo.cols = _.map (f$.find '.fr-grid-tr:first .fr-grid-td', grid), (td) ->
                    offset = Position.adjustedOffset $ td
                    grid: gridInfo
                    el: $ td
                    left: offset.left
                    top: offset.top
                    width: f$.width $ td
                gridsCache.unshift gridInfo

    # Position delete and grid details icon
    do ->
        deleteIcon = $ '.fb-delete-grid-trigger'
        detailsIcon = $ '.fb-grid-details-trigger'
        detailsHeight = _.memoize -> f$.height detailsIcon
        wasCurrent = -> _.each [deleteIcon, detailsIcon], (i) -> f$.hide i
        becomesCurrent = (grid) ->
            table = f$.find '.fr-grid', grid.el
            if f$.is '.fb-can-delete-grid', table
                f$.show deleteIcon
                offset =
                    top:  grid.top - Position.scrollTop()
                    left: grid.left
                f$.offset offset, deleteIcon
            if grid.head?
                f$.show detailsIcon
                head = f$.find '.fr-grid-head', table
                offset =
                    top: grid.head.offset.top + (grid.head.height - detailsHeight()) / 2 - Position.scrollTop()
                    left: grid.left
                f$.offset offset, detailsIcon
        Position.currentContainerChanged gridsCache, wasCurrent, becomesCurrent

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
                icon.offset = (col) ->
                    top: col.grid.top - Position.scrollTop()
                    left: col.left + colOffset col, icon
            [
                colIcon '.fb-insert-col-left',  -> 0
                colIcon '.fb-delete-col',       (col, icon) -> (col.width - icon.width()) / 2
                colIcon '.fb-insert-col-right', (col, icon) -> col.width - icon.width()
            ]

        # 3 icons to add/delete rows
        rowIcons = do ->
            rowIcon = (selector, rowOffset) -> _.tap (icon selector), (icon) ->
                icon.offset = (row) ->
                    top: row.top + (rowOffset row, icon) - Position.scrollTop()
                    left: row.grid.left
            [
                rowIcon '.fb-insert-row-above', -> 0
                rowIcon '.fb-delete-row',       (row, icon) -> (row.height - icon.height()) / 2
                rowIcon '.fb-insert-row-below', (row, icon) -> row.height - icon.height()
            ]

        # Hide/show icons
        hideIcons = (icons) -> -> _.each (_.values icons), (icon) -> f$.hide icon.el
        showIcons = (icons) -> (rowOrCol) ->
            _.each icons, (icon) ->
                canDo = (operation) ->
                    gridDiv = rowOrCol.grid.el
                    gridTable = f$.find '.fr-grid', gridDiv
                    f$.is '.fb-can-' + operation, gridTable
                operationRequires =
                    'delete-row': 'delete-row'
                    'delete-col': 'delete-col'
                    'insert-col-left': 'add-col'
                    'insert-col-right': 'add-col'
                dontShow = _.any (_.keys operationRequires), (operation) ->
                    (f$.is '.fb-' + operation, icon.el) and         # Is this an icon for the current operation
                        (not canDo operationRequires[operation])    # We can't perform the operation
                unless dontShow
                    f$.show icon.el
                    f$.offset (icon.offset rowOrCol), icon.el

        wasCurrentRow     = hideIcons rowIcons
        becomesCurrentRow = showIcons rowIcons
        wasCurrentCol     = hideIcons colIcons
        becomesCurrentCol = showIcons colIcons
        Position.currentRowColChanged(gridsCache, wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol)

    # On click on a trigger inside .fb-grid-repeat-editor, send grid/row/column info along with the event
    do ->

        # Keep track of current grid/row/column so we can send this information to the server on click
        current =
            gridId: null
            colPos: -1
            rowPos: -1

        # Functions maintaining current row/col position
        resetPos = (pos) -> -> current[pos] = -1
        setPos = (pos) -> (rowCol) ->
            selector = '.fr-grid-' + (if pos == 'rowPos' then 'tr' else 'td')
            current[pos] = f$.length f$.prevAll selector, rowCol.el

        wasCurrentRow     = resetPos 'rowPos'
        becomesCurrentRow = setPos   'rowPos'
        wasCurrentCol     = resetPos 'colPos'
        becomesCurrentCol = setPos   'colPos'
        Position.currentRowColChanged(gridsCache, wasCurrentRow, becomesCurrentRow, wasCurrentCol, becomesCurrentCol)

        wasCurrent = -> current.gridId = null
        becomesCurrent =  (grid) -> current.gridId = f$.attr 'id', grid.el
        Position.currentContainerChanged gridsCache, wasCurrent, becomesCurrent

        # Provide event context properties on click
        AjaxServer.eventCreated.add (event) ->
            target = $ document.getElementById event.targetId
            inGridRepeatEditor = f$.is '*', f$.closest '.fb-grid-repeat-editor', target
            if event.eventName == 'DOMActivate' && inGridRepeatEditor
                classContains = (text) -> f$.is '*[class *= "' + text + '"]', target
                add = (name, value) -> event.properties[name] = value.toString()
                add 'grid-id', current.gridId
                add 'row-pos', current.rowPos if classContains 'row'
                add 'col-pos', current.colPos if classContains 'col'
