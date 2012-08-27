$ ->
    Builder = ORBEON.Builder
    Events = ORBEON.xforms.Events

    deleteIcon = $ '.fb-delete-grid-trigger'

    gridsCache = []; do ->
        updateGridsCache = ->
            gridsCache.length = 0
            _.each ($ 'div.xbl-fr-grid'), (grid) ->
                grid = $ grid
                gridsCache.unshift
                    element: grid
                    offset: f$.offset grid
                    height: f$.height grid
        ($ document).on 'mousemove', updateGridsCache if gridsCache.length == 0
        Events.ajaxResponseProcessedEvent.subscribe updateGridsCache

    Builder.currentContainerChanged gridsCache,
        (grid) ->
            deleteIcon.hide()
        (grid) ->
            deleteIcon.show()
            scrollContainer = f$.closest '.yui-layout-bd', $ '#fr-view'
            deleteIcon.offset
                top:  grid.offset.top
                left: grid.offset.left
