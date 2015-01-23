$ = ORBEON.jQuery
$ ->
    AjaxServer = ORBEON.xforms.server.AjaxServer
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    # Keep track of grids positions, including the position their rows and columns
    gridsCache = []; do ->
        Builder.onOffsetMayHaveChanged ->
            gridsCache.length = 0                                                                                       # Update gridsCache in-place, as references are kept by other functions
            _.each ($ '.fr-grid.fr-editable:visible'), (table) ->
                table = $(table)
                grid = table.closest('.xbl-fr-grid')                                                                    # There might be an intermediate group <span>
                gridInfo =
                    el: grid
                    offset: Builder.adjustedOffset grid
                    height: f$.outerHeight grid                                                                         # Include grid padding
                if f$.is '.fr-repeat', table
                    head = f$.find 'thead', table
                    gridInfo.head =
                        offset: Builder.adjustedOffset head
                        height: f$.height head
                gridInfo.rows = _.map (f$.find '.fb-grid-tr', grid), (tr) ->                                            # .fb-grid-tr leaves out the header row in the repeat
                    grid: gridInfo
                    el: $ tr
                    offset: Builder.adjustedOffset $ tr
                    height: f$.height $ tr
                gridInfo.cols = _.map (f$.find '.fb-grid-tr:first .fb-grid-td', grid), (td) ->
                    grid: gridInfo
                    el: $ td
                    offset: Builder.adjustedOffset $ td
                    width: f$.width $ td
                gridsCache.unshift gridInfo

    # Position delete and grid details icon
    do ->
        deleteIcon = $ '.fb-delete-grid-trigger'
        detailsIcon = $ '.fb-grid-details-trigger'
        detailsHeight = _.memoize -> f$.height detailsIcon
        Builder.currentContainerChanged gridsCache,
            wasCurrent: -> _.each [deleteIcon, detailsIcon], (i) -> f$.hide i
            becomesCurrent: (grid) ->
                table = f$.find '.fr-grid', grid.el
                if f$.is '.fb-can-delete-grid', table
                    f$.show deleteIcon
                    offset =
                        top:  grid.offset.top - Builder.scrollTop()
                        left: grid.offset.left
                    f$.offset offset, deleteIcon
                if grid.head?
                    f$.show detailsIcon
                    head = f$.find 'thead', table
                    offset =
                        top: grid.head.offset.top + (grid.head.height - detailsHeight()) / 2 - Builder.scrollTop()
                        left: grid.offset.left
                    f$.offset offset, detailsIcon

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
                    top: col.grid.offset.top - Builder.scrollTop()
                    left: col.offset.left + colOffset col, icon
            [
                colIcon '.fb-insert-col-left',  -> 0
                colIcon '.fb-delete-col',       (col, icon) -> (col.width - icon.width()) / 2
                colIcon '.fb-insert-col-right', (col, icon) -> col.width - icon.width()
            ]

        # 3 icons to add/delete rows
        rowIcons = do ->
            rowIcon = (selector, rowOffset) -> _.tap (icon selector), (icon) ->
                icon.offset = (row) ->
                    top: row.offset.top + (rowOffset row, icon) - Builder.scrollTop()
                    left: row.grid.offset.left
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

        Builder.currentRowColChanged gridsCache,
            wasCurrentRow:      hideIcons rowIcons
            becomesCurrentRow:  showIcons rowIcons
            wasCurrentCol:      hideIcons colIcons
            becomesCurrentCol:  showIcons colIcons

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
            selector = '.fb-grid-' + rowCol.el[0].nodeName.toLowerCase()
            current[pos] = f$.length f$.prevAll selector, rowCol.el

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
                add 'col-pos', current.colPos if classContains 'col'
